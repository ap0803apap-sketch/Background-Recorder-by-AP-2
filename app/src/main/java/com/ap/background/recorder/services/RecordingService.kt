package com.ap.background.recorder.services

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.*
import android.media.MediaRecorder
import android.net.Uri
import android.os.*
import android.util.Log
import android.view.Surface
import androidx.camera.core.*
import androidx.camera.effects.OverlayEffect
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.camera.video.VideoCapture
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import com.ap.background.recorder.MainActivity
import com.ap.background.recorder.R
import com.ap.background.recorder.data.RecorderPreferences
import com.ap.background.recorder.utils.FileManager
import com.ap.background.recorder.utils.LocationHelper
import com.ap.background.recorder.utils.RecordingStatus
import com.ap.background.recorder.utils.TelephonyHelper
import com.ap.background.recorder.widgets.RecorderWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class RecordingService : LifecycleService() {

    private val NOTIFICATION_ID = 1001
    private val NOTIFICATION_CHANNEL_ID = "recording_channel_v3"
    private val TAG = "RecordingService"

    private lateinit var cameraExecutor: ExecutorService
    private lateinit var fileManager: FileManager
    private lateinit var prefs: RecorderPreferences
    private lateinit var locationHelper: LocationHelper
    private lateinit var telephonyHelper: TelephonyHelper
    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null
    private var imageCapture: ImageCapture? = null
    private var camera: androidx.camera.core.Camera? = null
    private var mediaRecorder: MediaRecorder? = null

    private var currentAction: String? = null
    private var photoHandler: Handler? = null
    private var photoRunnable: Runnable? = null

    override fun onCreate() {
        super.onCreate()
        cameraExecutor = Executors.newSingleThreadExecutor()
        fileManager = FileManager(this)
        prefs = RecorderPreferences(this)
        locationHelper = LocationHelper(this)
        telephonyHelper = TelephonyHelper(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        val action = intent?.action ?: return START_NOT_STICKY

        if (action == ACTION_STOP) {
            stopRecording()
            return START_NOT_STICKY
        }

        val allowToggle = intent.getBooleanExtra("allow_toggle", true)

        if (currentAction == action) {
            if (allowToggle) {
                stopRecording()
                return START_NOT_STICKY
            } else {
                return START_STICKY
            }
        }

        if (currentAction != null) {
            stopRecording()
        }

        currentAction = action

        val requiredType = when (action) {
            ACTION_START_VIDEO -> ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            ACTION_START_AUDIO -> ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            ACTION_START_PHOTO -> ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
            else -> 0
        }

        try {
            val notification = createNotification("Initializing...")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && requiredType != 0) {
                startForeground(NOTIFICATION_ID, notification, requiredType)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            setRecordingState(true)
        } catch (e: Exception) {
            setRecordingState(false)
            stopSelf()
            return START_NOT_STICKY
        }

        serviceScope.launch {
            when (action) {
                ACTION_START_VIDEO -> {
                    startVideoRecording()
                }
                ACTION_START_PHOTO -> {
                    startPhotoCaptureInterval()
                }
                ACTION_START_AUDIO -> {
                    startAudioRecording()
                }
            }
        }

        return START_STICKY
    }

    private suspend fun startAudioRecording() {
        updateNotification("Audio recording in progress...")
        try {
            val audioFile = fileManager.createAudioFile()
            
            @Suppress("DEPRECATION")
            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(this)
            } else {
                MediaRecorder()
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioChannels(2)
                setAudioSamplingRate(44100)
                setAudioEncodingBitRate(prefs.getAudioBitrate() * 1000)
                setOutputFile(audioFile.absolutePath)
                prepare()
                start()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Audio recording failed: ${e.message}")
            stopRecording()
        }
    }

    private suspend fun startVideoRecording() {
        updateNotification("Video recording in progress...")
        val cameraSelection = prefs.getCameraSelection()
        val zoomLevel = prefs.getZoomLevel()
        
        val showTimestamp = prefs.showTimestampFlow.first()
        val showGps = prefs.showGpsFlow.first()
        val showAppName = prefs.showAppNameFlow.first()
        val showDeviceInfo = prefs.showDeviceInfoFlow.first()
        val showLensInfo = prefs.showLensInfoFlow.first()

        val orientation = if (cameraSelection == "FRONT") prefs.getFrontOrientation() else prefs.getOrientation()

        val resolution = if (cameraSelection == "FRONT") prefs.getFrontVideoResolution() else prefs.getVideoResolution()
        val quality = when (resolution) {
            "4K" -> Quality.UHD
            "1080p" -> Quality.FHD
            "720p" -> Quality.HD
            else -> Quality.HIGHEST
        }

        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            
            val recorder = Recorder.Builder()
                .setQualitySelector(QualitySelector.from(quality))
                .build()
            videoCapture = VideoCapture.withOutput(recorder)
            
            val cameraSelector = getCameraSelector(cameraProvider, cameraSelection)

            val targetRotation = when (orientation) {
                "PORTRAIT" -> Surface.ROTATION_0
                "LANDSCAPE" -> Surface.ROTATION_90
                else -> Surface.ROTATION_0
            }

            try {
                cameraProvider.unbindAll()

                videoCapture?.targetRotation = targetRotation

                val useCaseGroupBuilder = UseCaseGroup.Builder()
                    .addUseCase(videoCapture!!)

                if (showTimestamp || showGps || showAppName || showDeviceInfo || showLensInfo) {
                    val overlayEffect = createOverlayEffect(showTimestamp, showGps, showAppName, showDeviceInfo, showLensInfo, cameraSelection)
                    useCaseGroupBuilder.addEffect(overlayEffect)
                }

                camera = cameraProvider.bindToLifecycle(this, cameraSelector, useCaseGroupBuilder.build())
                camera?.cameraControl?.setZoomRatio(zoomLevel)

                val outputOptions = FileOutputOptions.Builder(fileManager.createVideoFile()).build()
                recording = videoCapture?.output
                    ?.prepareRecording(this, outputOptions)
                    ?.apply { 
                        if (ContextCompat.checkSelfPermission(this@RecordingService, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                            withAudioEnabled()
                        }
                    }
                    ?.start(ContextCompat.getMainExecutor(this)) { recordEvent ->
                        if (recordEvent is VideoRecordEvent.Finalize && recordEvent.hasError()) {
                            stopRecording()
                        }
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Video start failed: ${e.message}")
                stopRecording()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun createOverlayEffect(
        showTimestamp: Boolean,
        showGps: Boolean,
        showAppName: Boolean,
        showDeviceInfo: Boolean,
        showLensInfo: Boolean,
        cameraSelection: String
    ): OverlayEffect {
        val overlayEffect = OverlayEffect(
            CameraEffect.VIDEO_CAPTURE,
            1,
            Handler(Looper.getMainLooper()),
            { /* error listener */ }
        )
        
        overlayEffect.setOnDrawListener { frame ->
            val canvas = frame.overlayCanvas
            // Clear the canvas to ensure transparency works
            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
            
            val width = canvas.width
            val height = canvas.height

            val paint = Paint().apply {
                color = Color.WHITE
                textSize = height / 40f
                isAntiAlias = true
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }

            val bgPaint = Paint().apply {
                color = Color.argb(128, 0, 0, 0)
                style = Paint.Style.FILL
                isAntiAlias = true
            }

            val overlayLines = mutableListOf<String>()
            if (showAppName) overlayLines.add("Background Recorder by AP")
            if (showTimestamp) {
                val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                overlayLines.add("Time: $dateStr")
            }
            if (showGps) {
                locationHelper.getLastLocationString()?.let { overlayLines.add(it) }
            }
            if (showDeviceInfo) {
                overlayLines.add("Device: ${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE}, API ${Build.VERSION.SDK_INT})")
                overlayLines.add(telephonyHelper.getSimInfo())
                overlayLines.add(telephonyHelper.getDeviceIdentifier())
            }
            if (showLensInfo) {
                val lens = if (cameraSelection == "FRONT") "Front Camera" else "Back Camera"
                overlayLines.add("Lens: $lens")
            }

            if (overlayLines.isNotEmpty()) {
                var maxWidth = 0f
                overlayLines.forEach {
                    val w = paint.measureText(it)
                    if (w > maxWidth) maxWidth = w
                }

                val padding = 20f
                val lineHeight = paint.fontSpacing
                val rectHeight = overlayLines.size * lineHeight + padding * 2
                val rectWidth = maxWidth + padding * 2

                val x = width - rectWidth - 20f
                val y = height - rectHeight - 20f

                val rect = RectF(x, y, x + rectWidth, y + rectHeight)
                val cornerRadius = height / 100f
                canvas.drawRoundRect(rect, cornerRadius, cornerRadius, bgPaint)
                
                overlayLines.forEachIndexed { index, line ->
                    canvas.drawText(line, x + padding, y + padding + (index + 1) * lineHeight - paint.descent(), paint)
                }
            }
            true // frame updated
        }
        return overlayEffect
    }

    private suspend fun startPhotoCaptureInterval() {
        val intervalSeconds = prefs.getPhotoInterval()
        val cameraSelection = prefs.getCameraSelection()
        val zoomLevel = prefs.getZoomLevel()
        
        val aspectRatioStr = if (cameraSelection == "FRONT") prefs.getFrontAspectRatio() else prefs.getAspectRatio()
        val orientationStr = if (cameraSelection == "FRONT") prefs.getFrontOrientation() else prefs.getOrientation()

        val aspectRatio = when (aspectRatioStr) {
            "4:3" -> AspectRatio.RATIO_4_3
            "16:9" -> AspectRatio.RATIO_16_9
            else -> AspectRatio.RATIO_16_9
        }

        val targetRotation = when (orientationStr) {
            "PORTRAIT" -> Surface.ROTATION_0
            "LANDSCAPE" -> Surface.ROTATION_90
            else -> Surface.ROTATION_0
        }

        updateNotification("Taking photos every $intervalSeconds seconds")

        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            imageCapture = ImageCapture.Builder()
                .setTargetAspectRatio(aspectRatio)
                .setTargetRotation(targetRotation)
                .build()
            val cameraSelector = getCameraSelector(cameraProvider, cameraSelection)

            try {
                cameraProvider.unbindAll()
                
                camera = cameraProvider.bindToLifecycle(this, cameraSelector, imageCapture!!)
                camera?.cameraControl?.setZoomRatio(zoomLevel)

                photoHandler = Handler(Looper.getMainLooper())
                photoRunnable = object : Runnable {
                    override fun run() {
                        takeSinglePhoto()
                        photoHandler?.postDelayed(this, intervalSeconds * 1000L)
                    }
                }
                photoHandler?.post(photoRunnable!!)
                
            } catch (e: Exception) {
                Log.e(TAG, "Photo start failed: ${e.message}")
                stopRecording()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun takeSinglePhoto() {
        val outputOptions = ImageCapture.OutputFileOptions.Builder(fileManager.createPhotoFile()).build()
        imageCapture?.takePicture(
            outputOptions,
            cameraExecutor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    output.savedUri?.let { uri ->
                        processCapturedPhoto(uri)
                    }
                    Log.d(TAG, "Photo saved: ${output.savedUri}")
                }
                override fun onError(exc: ImageCaptureException) {
                    Log.e(TAG, "Photo failed: ${exc.message}")
                }
            }
        )
    }

    private fun processCapturedPhoto(uri: Uri) {
        serviceScope.launch(Dispatchers.IO) {
            try {
                val showTimestamp = prefs.showTimestampFlow.first()
                val showGps = prefs.showGpsFlow.first()
                val showAppName = prefs.showAppNameFlow.first()
                val showDeviceInfo = prefs.showDeviceInfoFlow.first()
                val showLensInfo = prefs.showLensInfoFlow.first()

                if (!showTimestamp && !showGps && !showAppName && !showDeviceInfo && !showLensInfo) return@launch

                val context = this@RecordingService
                val inputStream = context.contentResolver.openInputStream(uri) ?: return@launch
                val bitmap = BitmapFactory.decodeStream(inputStream).copy(Bitmap.Config.ARGB_8888, true)
                inputStream.close()

                val canvas = Canvas(bitmap)
                val paint = Paint().apply {
                    color = Color.WHITE
                    textSize = bitmap.height / 40f
                    isAntiAlias = true
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                }

                val bgPaint = Paint().apply {
                    color = Color.argb(128, 0, 0, 0)
                    style = Paint.Style.FILL
                    isAntiAlias = true
                }

                val overlayLines = mutableListOf<String>()
                if (showAppName) overlayLines.add("Background Recorder by AP")
                if (showTimestamp) {
                    val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                    overlayLines.add("Time: $dateStr")
                }
                if (showGps) {
                    locationHelper.getLastLocationString()?.let { overlayLines.add(it) }
                }
                if (showDeviceInfo) {
                    overlayLines.add("Device: ${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE}, API ${Build.VERSION.SDK_INT})")
                    overlayLines.add(telephonyHelper.getSimInfo())
                    overlayLines.add(telephonyHelper.getDeviceIdentifier())
                }
                if (showLensInfo) {
                    val cameraSelection = prefs.getCameraSelection()
                    val lens = if (cameraSelection == "FRONT") "Front Camera" else "Back Camera"
                    overlayLines.add("Lens: $lens")
                }

                if (overlayLines.isNotEmpty()) {
                    var maxWidth = 0f
                    overlayLines.forEach {
                        val width = paint.measureText(it)
                        if (width > maxWidth) maxWidth = width
                    }

                    val padding = 20f
                    val lineHeight = paint.fontSpacing
                    val rectHeight = overlayLines.size * lineHeight + padding * 2
                    val rectWidth = maxWidth + padding * 2

                    val x = bitmap.width - rectWidth - 20f
                    val y = bitmap.height - rectHeight - 20f

                    val rect = RectF(x, y, x + rectWidth, y + rectHeight)
                    val cornerRadius = bitmap.height / 100f
                    canvas.drawRoundRect(rect, cornerRadius, cornerRadius, bgPaint)
                    
                    overlayLines.forEachIndexed { index, line ->
                        canvas.drawText(line, x + padding, y + padding + (index + 1) * lineHeight - paint.descent(), paint)
                    }
                }

                val outputStream = context.contentResolver.openOutputStream(uri) ?: return@launch
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, outputStream)
                outputStream.close()
                bitmap.recycle()
            } catch (e: Exception) {
                Log.e(TAG, "Error processing photo overlay: ${e.message}")
            }
        }
    }

    private fun getCameraSelector(cameraProvider: ProcessCameraProvider, selection: String): CameraSelector {
        val backCameras = cameraProvider.availableCameraInfos.filter { it.lensFacing == CameraSelector.LENS_FACING_BACK }
        val frontCameras = cameraProvider.availableCameraInfos.filter { it.lensFacing == CameraSelector.LENS_FACING_FRONT }

        return when (selection) {
            "PRIMARY" -> {
                if (backCameras.isNotEmpty()) {
                    CameraSelector.Builder().addCameraFilter { it.filter { info -> info == backCameras[0] } }.build()
                } else CameraSelector.DEFAULT_BACK_CAMERA
            }
            "SECONDARY" -> {
                if (backCameras.size > 1) {
                    CameraSelector.Builder().addCameraFilter { it.filter { info -> info == backCameras[1] } }.build()
                } else {
                    if (backCameras.isNotEmpty()) {
                        CameraSelector.Builder().addCameraFilter { it.filter { info -> info == backCameras[0] } }.build()
                    } else CameraSelector.DEFAULT_BACK_CAMERA
                }
            }
            "FRONT" -> {
                if (frontCameras.isNotEmpty()) {
                    CameraSelector.Builder().addCameraFilter { it.filter { info -> info == frontCameras[0] } }.build()
                } else CameraSelector.DEFAULT_FRONT_CAMERA
            }
            else -> CameraSelector.DEFAULT_BACK_CAMERA
        }
    }

    private fun stopRecording() {
        setRecordingState(false)
        photoHandler?.removeCallbacks(photoRunnable ?: Runnable {})
        recording?.stop()
        recording = null
        imageCapture = null
        currentAction = null
        
        mediaRecorder?.apply {
            try {
                stop()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            release()
        }
        mediaRecorder = null

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(NOTIFICATION_CHANNEL_ID, "Recorder", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(content: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Background Recorder")
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_recorder)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun updateNotification(content: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, createNotification(content))
    }

    private fun setRecordingState(active: Boolean) {
        RecordingStatus.setRecording(active)
        serviceScope.launch {
            prefs.setRecordingActive(active)
        }
        RecorderTileService.updateTile(this)
        RecorderWidgetProvider.updateAllWidgets(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        setRecordingState(false)
        cameraExecutor.shutdown()
        serviceJob.cancel()
    }

    override fun onBind(intent: Intent): IBinder? {
        return super.onBind(intent)
    }

    companion object {
        const val ACTION_START_VIDEO = "com.ap.background.recorder.START_VIDEO"
        const val ACTION_START_AUDIO = "com.ap.background.recorder.START_AUDIO"
        const val ACTION_START_PHOTO = "com.ap.background.recorder.START_PHOTO"
        const val ACTION_STOP = "com.ap.background.recorder.STOP"
    }
}