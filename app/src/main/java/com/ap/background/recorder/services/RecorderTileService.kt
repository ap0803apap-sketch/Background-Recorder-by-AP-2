package com.ap.background.recorder.services

import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import com.ap.background.recorder.data.RecorderPreferences
import com.ap.background.recorder.utils.RecordingStatus
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

@RequiresApi(Build.VERSION_CODES.N)
class RecorderTileService : TileService() {

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)
    private var statusJob: Job? = null

    override fun onClick() {
        super.onClick()

        serviceScope.launch {
            val prefs = RecorderPreferences(this@RecorderTileService)
            val isRecording = RecordingStatus.isRecording.value
            
            val serviceIntent = Intent(this@RecorderTileService, RecordingService::class.java)

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

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
        }
    }

    override fun onStartListening() {
        super.onStartListening()
        statusJob?.cancel()
        statusJob = serviceScope.launch {
            RecordingStatus.isRecording.collect { isRecording ->
                updateTileState(isRecording)
            }
        }
    }

    override fun onStopListening() {
        super.onStopListening()
        statusJob?.cancel()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
    }

    private fun updateTileState(isRecording: Boolean) {
        val tile = qsTile ?: return
        if (isRecording) {
            tile.state = Tile.STATE_ACTIVE
            tile.label = "Stop Recording"
        } else {
            tile.state = Tile.STATE_INACTIVE
            tile.label = "Start Recording"
        }
        tile.updateTile()
    }

    companion object {
        fun updateTile(context: android.content.Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                requestListeningState(context, ComponentName(context, RecorderTileService::class.java))
            }
        }
    }
}