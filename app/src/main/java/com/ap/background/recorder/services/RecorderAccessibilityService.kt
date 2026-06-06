package com.ap.background.recorder.services

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import com.ap.background.recorder.data.RecorderPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class RecorderAccessibilityService : AccessibilityService() {

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    override fun onServiceConnected() {
        super.onServiceConnected()
        // When accessibility service connects, ensure our trigger services are running
        serviceScope.launch {
            val prefs = RecorderPreferences(this@RecorderAccessibilityService)
            
            if (prefs.shakeTriggerEnabledFlow.first()) {
                startForegroundService(Intent(this@RecorderAccessibilityService, ShakeTriggerService::class.java))
            }
            if (prefs.smsTriggerEnabledFlow.first()) {
                startForegroundService(Intent(this@RecorderAccessibilityService, SmsTriggerService::class.java))
            }
            if (prefs.timeTriggerEnabledFlow.first()) {
                startForegroundService(Intent(this@RecorderAccessibilityService, TimeTriggerService::class.java))
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Not used for logic, but required to override
    }

    override fun onInterrupt() {
        // Required to override
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
    }
}
