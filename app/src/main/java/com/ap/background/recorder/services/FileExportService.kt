package com.ap.background.recorder.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.ap.background.recorder.R
import com.ap.background.recorder.utils.FileManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File

class FileExportService : Service() {

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private val NOTIFICATION_ID = 1003
    private val CHANNEL_ID = "export_channel"

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val filePath = intent?.getStringExtra("file_path")
        val fileType = intent?.getStringExtra("file_type") ?: "Media"

        if (filePath != null) {
            val file = File(filePath)
            startForeground(NOTIFICATION_ID, createNotification("Exporting ${file.name}..."))
            
            serviceScope.launch {
                val fileManager = FileManager(this@FileExportService)
                fileManager.saveToDownloads(file, fileType)
                
                // Show completion notification or just stop
                updateNotification("Export completed: ${file.name}")
                stopForeground(STOP_FOREGROUND_DETACH)
                stopSelf()
            }
        } else {
            stopSelf()
        }

        return START_NOT_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "File Export", NotificationManager.IMPORTANCE_LOW).apply {
                setSound(null, null)
                enableLights(false)
                enableVibration(false)
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(content: String) = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("Background Recorder")
        .setContentText(content)
        .setSmallIcon(R.drawable.ic_recorder)
        .build()

    private fun updateNotification(content: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, createNotification(content))
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
