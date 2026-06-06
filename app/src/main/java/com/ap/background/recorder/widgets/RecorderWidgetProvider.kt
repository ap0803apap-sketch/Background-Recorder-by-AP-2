package com.ap.background.recorder.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.ap.background.recorder.R
import com.ap.background.recorder.data.RecorderPreferences
import com.ap.background.recorder.services.RecordingService
import com.ap.background.recorder.utils.RecordingStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class RecorderWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_WIDGET_CLICK) {
            handleWidgetClick(context)
        } else if (intent.action == RecordingStatus.ACTION_RECORDING_STATUS_CHANGED) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, RecorderWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
            for (appWidgetId in appWidgetIds) {
                updateAppWidget(context, appWidgetManager, appWidgetId)
            }
        }
    }

    private fun handleWidgetClick(context: Context) {
        CoroutineScope(Dispatchers.Main).launch {
            val prefs = RecorderPreferences(context)
            if (!prefs.isWidgetTriggerEnabled()) return@launch

            val isRecording = RecordingStatus.isRecording.value
            val serviceIntent = Intent(context, RecordingService::class.java)

            if (isRecording) {
                serviceIntent.action = RecordingService.ACTION_STOP
            } else {
                val mode = prefs.getRecordingMode()
                serviceIntent.action = when (mode) {
                    "VIDEO" -> RecordingService.ACTION_START_VIDEO
                    "PHOTO" -> RecordingService.ACTION_START_PHOTO
                    "AUDIO" -> RecordingService.ACTION_START_AUDIO
                    else -> RecordingService.ACTION_START_VIDEO
                }
            }
            context.startForegroundService(serviceIntent)
        }
    }

    companion object {
        const val ACTION_WIDGET_CLICK = "com.ap.background.recorder.WIDGET_CLICK"

        fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.recorder_widget)
            val isRecording = RecordingStatus.isRecording.value

            if (isRecording) {
                views.setImageViewResource(R.id.widget_button, R.drawable.ic_stop)
                views.setInt(R.id.widget_container, "setBackgroundResource", R.drawable.widget_background_active)
            } else {
                views.setImageViewResource(R.id.widget_button, R.drawable.ic_play)
                views.setInt(R.id.widget_container, "setBackgroundResource", R.drawable.widget_background)
            }

            val intent = Intent(context, RecorderWidgetProvider::class.java).apply {
                action = ACTION_WIDGET_CLICK
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_container, pendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        fun updateAllWidgets(context: Context) {
            val intent = Intent(context, RecorderWidgetProvider::class.java).apply {
                action = RecordingStatus.ACTION_RECORDING_STATUS_CHANGED
            }
            context.sendBroadcast(intent)
        }
    }
}