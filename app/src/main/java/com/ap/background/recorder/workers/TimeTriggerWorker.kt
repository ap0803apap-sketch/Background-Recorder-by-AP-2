package com.ap.background.recorder.workers

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ap.background.recorder.data.RecorderPreferences
import com.ap.background.recorder.services.RecordingService
import kotlinx.coroutines.flow.first

class TimeTriggerWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val isStart = inputData.getBoolean("is_start", true)
        val prefs = RecorderPreferences(applicationContext)

        if (!prefs.isTimeTriggerEnabled()) return Result.success()

        if (isStart) {
            val action = when (prefs.getRecordingMode()) {
                "VIDEO" -> RecordingService.ACTION_START_VIDEO
                "PHOTO" -> RecordingService.ACTION_START_PHOTO
                "AUDIO" -> RecordingService.ACTION_START_AUDIO
                else -> RecordingService.ACTION_START_VIDEO
            }

            val serviceIntent = Intent(applicationContext, RecordingService::class.java).apply {
                this.action = action
                putExtra("allow_toggle", false)
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                applicationContext.startForegroundService(serviceIntent)
            } else {
                applicationContext.startService(serviceIntent)
            }
        } else {
            val serviceIntent = Intent(applicationContext, RecordingService::class.java).apply {
                action = RecordingService.ACTION_STOP
            }
            applicationContext.startService(serviceIntent)
        }

        return Result.success()
    }
}
