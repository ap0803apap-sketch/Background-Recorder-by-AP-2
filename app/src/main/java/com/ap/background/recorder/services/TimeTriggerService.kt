package com.ap.background.recorder.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.ap.background.recorder.MainActivity
import com.ap.background.recorder.R
import com.ap.background.recorder.data.RecorderPreferences
import com.ap.background.recorder.utils.TriggerUtils
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import org.json.JSONArray

class TimeTriggerService : Service() {

    private val NOTIFICATION_ID = 1005
    private val CHANNEL_ID = "time_trigger_channel"
    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, createNotification())
        
        serviceScope.launch {
            val prefs = RecorderPreferences(this@TimeTriggerService)
            prefs.timeTriggersJsonFlow.collectLatest { json ->
                refreshAlarms(json)
            }
        }
        
        return START_STICKY
    }

    private fun refreshAlarms(triggersJson: String) {
        serviceScope.launch {
            val context = this@TimeTriggerService
            try {
                val array = JSONArray(triggersJson)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val id = obj.getString("id")
                    val startTime = obj.getLong("startTime")
                    val endTime = if (obj.has("endTime")) obj.getLong("endTime") else null
                    val isEnabled = obj.getBoolean("isEnabled")

                    if (isEnabled) {
                        TriggerUtils.scheduleAlarm(context, id, startTime, true)
                        endTime?.let { TriggerUtils.scheduleAlarm(context, id, it, false) }
                    } else {
                        TriggerUtils.cancelAlarm(context, id, true)
                        TriggerUtils.cancelAlarm(context, id, false)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Time-Based Monitoring",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps scheduled recordings active"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Time Trigger Active")
            .setContentText("Monitoring for scheduled recordings")
            .setSmallIcon(R.drawable.ic_recorder)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}