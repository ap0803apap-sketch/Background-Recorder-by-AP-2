package com.ap.background.recorder.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ap.background.recorder.data.RecorderPreferences
import com.ap.background.recorder.services.RecordingService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class TimeTriggerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return

        val isStart = intent.getBooleanExtra("is_start", true)
        val prefs = RecorderPreferences(context)
        val scope = CoroutineScope(Dispatchers.IO)

        scope.launch {
            if (!prefs.isTimeTriggerEnabled()) return@launch

            if (isStart) {
                val action = when (prefs.getRecordingMode()) {
                    "VIDEO" -> RecordingService.ACTION_START_VIDEO
                    "PHOTO" -> RecordingService.ACTION_START_PHOTO
                    "AUDIO" -> RecordingService.ACTION_START_AUDIO
                    else -> RecordingService.ACTION_START_VIDEO
                }

                val serviceIntent = Intent(context, RecordingService::class.java).apply {
                    this.action = action
                    putExtra("allow_toggle", false)
                }
                context.startForegroundService(serviceIntent)
            } else {
                val serviceIntent = Intent(context, RecordingService::class.java).apply {
                    this.action = RecordingService.ACTION_STOP
                }
                context.startService(serviceIntent)
            }
        }
    }
}
