package com.ap.background.recorder.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ap.background.recorder.data.RecorderPreferences
import com.ap.background.recorder.services.TimeTriggerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED || context == null) return

        val prefs = RecorderPreferences(context)
        val scope = CoroutineScope(Dispatchers.IO)

        scope.launch {
            if (prefs.isTimeTriggerEnabled()) {
                val serviceIntent = Intent(context, TimeTriggerService::class.java)
                context.startForegroundService(serviceIntent)
            }
        }
    }
}
