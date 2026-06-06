package com.ap.background.recorder.services

import android.app.*
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.ap.background.recorder.MainActivity
import com.ap.background.recorder.R
import com.ap.background.recorder.data.RecorderPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.sqrt

class ShakeTriggerService : Service(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var lastUpdate: Long = 0
    private var lastX: Float = 0f
    private var lastY: Float = 0f
    private var lastZ: Float = 0f
    private var shakeThreshold = 800f

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)
    private lateinit var prefs: RecorderPreferences

    private val NOTIFICATION_ID = 1002
    private val CHANNEL_ID = "shake_trigger_channel"

    override fun onCreate() {
        super.onCreate()
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        prefs = RecorderPreferences(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, createNotification())
        
        serviceScope.launch {
            prefs.shakeIntensityFlow.collect { intensity ->
                // Map 0-100 to threshold 2000-200 (lower threshold = more sensitive)
                shakeThreshold = 2000f - (intensity * 18f)
            }
        }

        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        return START_STICKY
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "Shake Trigger", NotificationManager.IMPORTANCE_LOW).apply {
            setSound(null, null)
            enableLights(false)
            enableVibration(false)
            setShowBadge(false)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Shake Trigger Active")
            .setContentText("Listening for shake to record")
            .setSmallIcon(R.drawable.ic_recorder)
            .setContentIntent(pendingIntent)
            .build()
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_ACCELEROMETER) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]

            val curTime = System.currentTimeMillis()
            if ((curTime - lastUpdate) > 100) {
                val diffTime = curTime - lastUpdate
                lastUpdate = curTime

                val speed = sqrt(((x - lastX) * (x - lastX) + (y - lastY) * (y - lastY) + (z - lastZ) * (z - lastZ)).toDouble()) / diffTime * 10000

                if (speed > shakeThreshold) {
                    onShakeDetected()
                }

                lastX = x
                lastY = y
                lastZ = z
            }
        }
    }

    private fun onShakeDetected() {
        serviceScope.launch {
            if (prefs.shakeTriggerEnabledFlow.first()) {
                val mode = prefs.getRecordingMode()
                val action = when (mode) {
                    "VIDEO" -> RecordingService.ACTION_START_VIDEO
                    "PHOTO" -> RecordingService.ACTION_START_PHOTO
                    "AUDIO" -> RecordingService.ACTION_START_AUDIO
                    else -> RecordingService.ACTION_START_VIDEO
                }

                val serviceIntent = Intent(this@ShakeTriggerService, RecordingService::class.java).apply {
                    this.action = action
                    this.putExtra("allow_toggle", prefs.isShakeToggleOffEnabled())
                }
                startForegroundService(serviceIntent)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onDestroy() {
        super.onDestroy()
        sensorManager.unregisterListener(this)
        serviceJob.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
