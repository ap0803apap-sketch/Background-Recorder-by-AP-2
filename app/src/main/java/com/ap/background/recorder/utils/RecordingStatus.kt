package com.ap.background.recorder.utils

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object RecordingStatus {
    const val ACTION_RECORDING_STATUS_CHANGED = "com.ap.background.recorder.RECORDING_STATUS_CHANGED"

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    fun setRecording(recording: Boolean) {
        _isRecording.value = recording
    }
}
