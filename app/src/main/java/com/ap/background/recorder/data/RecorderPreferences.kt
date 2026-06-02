package com.ap.background.recorder.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "recorder_prefs")

class RecorderPreferences(private val context: Context) {

    companion object {
        val THEME_MODE = stringPreferencesKey("theme_mode") // "SYSTEM", "LIGHT", "DARK"
        val IS_DYNAMIC_COLOR = booleanPreferencesKey("is_dynamic_color")
        val IS_AMOLED_MODE = booleanPreferencesKey("is_amoled_mode")
        val IS_BIOMETRIC_ENABLED = booleanPreferencesKey("is_biometric_enabled")
        val IS_TERMS_ACCEPTED = booleanPreferencesKey("is_terms_accepted")
        val RECORDING_MODE = stringPreferencesKey("recording_mode")
        val VIDEO_RESOLUTION = stringPreferencesKey("video_resolution")
        val VIDEO_FPS = intPreferencesKey("video_fps")
        val CAMERA_SELECTION = stringPreferencesKey("camera_selection")
        val FOCUS_MODE = stringPreferencesKey("focus_mode")
        val ZOOM_LEVEL = floatPreferencesKey("zoom_level")
        val PHOTO_MEGAPIXEL = intPreferencesKey("photo_megapixel")
        val PHOTO_INTERVAL = intPreferencesKey("photo_interval")
        val AUDIO_BITRATE = intPreferencesKey("audio_bitrate")

        // Trigger Settings
        val IS_SHAKE_TRIGGER_ENABLED = booleanPreferencesKey("is_shake_trigger_enabled")
        val IS_SHAKE_TOGGLE_OFF_ENABLED = booleanPreferencesKey("is_shake_toggle_off_enabled")
        val IS_SMS_TRIGGER_ENABLED = booleanPreferencesKey("is_sms_trigger_enabled")
        val IS_SMS_TOGGLE_OFF_ENABLED = booleanPreferencesKey("is_sms_toggle_off_enabled")
        val SMS_TRIGGER_TYPE = stringPreferencesKey("sms_trigger_type") // "CUSTOM", "CODE"
        val SMS_TRIGGER_TEXT = stringPreferencesKey("sms_trigger_text")
        val IS_TIME_TRIGGER_ENABLED = booleanPreferencesKey("is_time_trigger_enabled")
        val TIME_TRIGGERS_JSON = stringPreferencesKey("time_triggers_json")
        val SHAKE_INTENSITY = floatPreferencesKey("shake_intensity")
        
        // Front Camera Specific Settings
        val FRONT_VIDEO_RESOLUTION = stringPreferencesKey("front_video_resolution")
        val FRONT_VIDEO_FPS = intPreferencesKey("front_video_fps")
        val FRONT_PHOTO_QUALITY = intPreferencesKey("front_photo_quality")

        // Overlay Settings
        val SHOW_TIMESTAMP = booleanPreferencesKey("show_timestamp")
        val SHOW_GPS = booleanPreferencesKey("show_gps")
        val SHOW_APP_NAME = booleanPreferencesKey("show_app_name")
        val SHOW_DEVICE_INFO = booleanPreferencesKey("show_device_info")
        val SHOW_LENS_INFO = booleanPreferencesKey("show_lens_info")
        val IS_RECORDING_ACTIVE = booleanPreferencesKey("is_recording_active")
    }

    val audioBitrateFlow: Flow<Int> = context.dataStore.data.map { it[AUDIO_BITRATE] ?: 128 }
    
    // Trigger Flows
    val shakeTriggerEnabledFlow: Flow<Boolean> = context.dataStore.data.map { it[IS_SHAKE_TRIGGER_ENABLED] ?: false }
    val shakeToggleOffFlow: Flow<Boolean> = context.dataStore.data.map { it[IS_SHAKE_TOGGLE_OFF_ENABLED] ?: false }
    val smsTriggerEnabledFlow: Flow<Boolean> = context.dataStore.data.map { it[IS_SMS_TRIGGER_ENABLED] ?: false }
    val smsToggleOffFlow: Flow<Boolean> = context.dataStore.data.map { it[IS_SMS_TOGGLE_OFF_ENABLED] ?: false }
    val smsTriggerTypeFlow: Flow<String> = context.dataStore.data.map { it[SMS_TRIGGER_TYPE] ?: "CUSTOM" }
    val smsTriggerTextFlow: Flow<String> = context.dataStore.data.map { it[SMS_TRIGGER_TEXT] ?: "" }
    val timeTriggerEnabledFlow: Flow<Boolean> = context.dataStore.data.map { it[IS_TIME_TRIGGER_ENABLED] ?: false }
    val timeTriggersJsonFlow: Flow<String> = context.dataStore.data.map { it[TIME_TRIGGERS_JSON] ?: "[]" }
    val shakeIntensityFlow: Flow<Float> = context.dataStore.data.map { it[SHAKE_INTENSITY] ?: 50f }

    val themeModeFlow: Flow<String> = context.dataStore.data.map { it[THEME_MODE] ?: "SYSTEM" }
    val dynamicColorFlow: Flow<Boolean> = context.dataStore.data.map { it[IS_DYNAMIC_COLOR] ?: true }
    val amoledModeFlow: Flow<Boolean> = context.dataStore.data.map { it[IS_AMOLED_MODE] ?: false }
    val biometricEnabledFlow: Flow<Boolean> = context.dataStore.data.map { it[IS_BIOMETRIC_ENABLED] ?: false }
    val termsAcceptedFlow: Flow<Boolean> = context.dataStore.data.map { it[IS_TERMS_ACCEPTED] ?: false }
    val recordingModeFlow: Flow<String> = context.dataStore.data.map { it[RECORDING_MODE] ?: "VIDEO" }
    val cameraSelectionFlow: Flow<String> = context.dataStore.data.map { it[CAMERA_SELECTION] ?: "PRIMARY" } 
    val focusModeFlow: Flow<String> = context.dataStore.data.map { it[FOCUS_MODE] ?: "AUTO" }
    val zoomLevelFlow: Flow<Float> = context.dataStore.data.map { it[ZOOM_LEVEL] ?: 1f }
    val photoIntervalFlow: Flow<Int> = context.dataStore.data.map { it[PHOTO_INTERVAL] ?: 5 }

    // Overlay Flows
    val showTimestampFlow: Flow<Boolean> = context.dataStore.data.map { it[SHOW_TIMESTAMP] ?: false }
    val showGpsFlow: Flow<Boolean> = context.dataStore.data.map { it[SHOW_GPS] ?: false }
    val showAppNameFlow: Flow<Boolean> = context.dataStore.data.map { it[SHOW_APP_NAME] ?: false }
    val showDeviceInfoFlow: Flow<Boolean> = context.dataStore.data.map { it[SHOW_DEVICE_INFO] ?: false }
    val showLensInfoFlow: Flow<Boolean> = context.dataStore.data.map { it[SHOW_LENS_INFO] ?: false }
    val isRecordingActiveFlow: Flow<Boolean> = context.dataStore.data.map { it[IS_RECORDING_ACTIVE] ?: false }

    suspend fun getThemeMode(): String = themeModeFlow.first()
    suspend fun setThemeMode(mode: String) {
        context.dataStore.edit { preferences -> preferences[THEME_MODE] = mode }
    }

    suspend fun isDynamicColor(): Boolean = dynamicColorFlow.first()
    suspend fun setDynamicColor(isEnabled: Boolean) {
        context.dataStore.edit { preferences -> preferences[IS_DYNAMIC_COLOR] = isEnabled }
    }

    suspend fun isAmoledMode(): Boolean = amoledModeFlow.first()
    suspend fun setAmoledMode(isEnabled: Boolean) {
        context.dataStore.edit { preferences -> preferences[IS_AMOLED_MODE] = isEnabled }
    }

    suspend fun isBiometricEnabled(): Boolean = biometricEnabledFlow.first()
    suspend fun setBiometricEnabled(isEnabled: Boolean) {
        context.dataStore.edit { preferences -> preferences[IS_BIOMETRIC_ENABLED] = isEnabled }
    }

    suspend fun isTermsAccepted(): Boolean = termsAcceptedFlow.first()
    suspend fun setTermsAccepted(accepted: Boolean) {
        context.dataStore.edit { preferences -> preferences[IS_TERMS_ACCEPTED] = accepted }
    }

    suspend fun getRecordingMode(): String = recordingModeFlow.first()
    suspend fun setRecordingMode(mode: String) {
        context.dataStore.edit { preferences -> preferences[RECORDING_MODE] = mode }
    }

    suspend fun getVideoResolution(): String = context.dataStore.data.map { it[VIDEO_RESOLUTION] ?: "1080p" }.first()
    suspend fun setVideoResolution(resolution: String) {
        context.dataStore.edit { preferences -> preferences[VIDEO_RESOLUTION] = resolution }
    }

    suspend fun getVideoFps(): Int = context.dataStore.data.map { it[VIDEO_FPS] ?: 30 }.first()
    suspend fun setVideoFps(fps: Int) {
        context.dataStore.edit { preferences -> preferences[VIDEO_FPS] = fps }
    }

    suspend fun getCameraSelection(): String = cameraSelectionFlow.first()
    suspend fun setCameraSelection(camera: String) {
        context.dataStore.edit { preferences -> preferences[CAMERA_SELECTION] = camera }
    }

    suspend fun getFocusMode(): String = focusModeFlow.first()
    suspend fun setFocusMode(mode: String) {
        context.dataStore.edit { preferences -> preferences[FOCUS_MODE] = mode }
    }

    suspend fun getZoomLevel(): Float = zoomLevelFlow.first()
    suspend fun setZoomLevel(zoom: Float) {
        context.dataStore.edit { preferences -> preferences[ZOOM_LEVEL] = zoom }
    }

    suspend fun getPhotoMegapixel(): Int = context.dataStore.data.map { it[PHOTO_MEGAPIXEL] ?: 48 }.first()
    suspend fun setPhotoMegapixel(megapixel: Int) {
        context.dataStore.edit { preferences -> preferences[PHOTO_MEGAPIXEL] = megapixel }
    }

    suspend fun getPhotoInterval(): Int = photoIntervalFlow.first()
    suspend fun setPhotoInterval(interval: Int) {
        context.dataStore.edit { preferences -> preferences[PHOTO_INTERVAL] = interval }
    }

    suspend fun getAudioBitrate(): Int = context.dataStore.data.map { it[AUDIO_BITRATE] ?: 128 }.first()
    suspend fun setAudioBitrate(bitrate: Int) {
        context.dataStore.edit { preferences -> preferences[AUDIO_BITRATE] = bitrate }
    }

    // Trigger Methods
    suspend fun isShakeTriggerEnabled(): Boolean = shakeTriggerEnabledFlow.first()
    suspend fun setShakeTriggerEnabled(enabled: Boolean) {
        context.dataStore.edit { it[IS_SHAKE_TRIGGER_ENABLED] = enabled }
    }
    
    suspend fun isShakeToggleOffEnabled(): Boolean = shakeToggleOffFlow.first()
    suspend fun setShakeToggleOffEnabled(enabled: Boolean) {
        context.dataStore.edit { it[IS_SHAKE_TOGGLE_OFF_ENABLED] = enabled }
    }

    suspend fun isSmsTriggerEnabled(): Boolean = smsTriggerEnabledFlow.first()
    suspend fun setSmsTriggerEnabled(enabled: Boolean) {
        context.dataStore.edit { it[IS_SMS_TRIGGER_ENABLED] = enabled }
    }
    
    suspend fun isSmsToggleOffEnabled(): Boolean = smsToggleOffFlow.first()
    suspend fun setSmsToggleOffEnabled(enabled: Boolean) {
        context.dataStore.edit { it[IS_SMS_TOGGLE_OFF_ENABLED] = enabled }
    }

    suspend fun getSmsTriggerType(): String = smsTriggerTypeFlow.first()
    suspend fun setSmsTriggerType(type: String) {
        context.dataStore.edit { it[SMS_TRIGGER_TYPE] = type }
    }

    suspend fun getSmsTriggerText(): String = smsTriggerTextFlow.first()
    suspend fun setSmsTriggerText(text: String) {
        context.dataStore.edit { it[SMS_TRIGGER_TEXT] = text }
    }

    suspend fun isTimeTriggerEnabled(): Boolean = timeTriggerEnabledFlow.first()
    suspend fun setTimeTriggerEnabled(enabled: Boolean) {
        context.dataStore.edit { it[IS_TIME_TRIGGER_ENABLED] = enabled }
    }

    suspend fun getTimeTriggersJson(): String = timeTriggersJsonFlow.first()
    suspend fun setTimeTriggersJson(json: String) {
        context.dataStore.edit { it[TIME_TRIGGERS_JSON] = json }
    }

    suspend fun getShakeIntensity(): Float = shakeIntensityFlow.first()
    suspend fun setShakeIntensity(intensity: Float) {
        context.dataStore.edit { it[SHAKE_INTENSITY] = intensity }
    }
    
    // Front Camera Methods
    suspend fun getFrontVideoResolution(): String = context.dataStore.data.map { it[FRONT_VIDEO_RESOLUTION] ?: "1080p" }.first()
    suspend fun setFrontVideoResolution(resolution: String) {
        context.dataStore.edit { preferences -> preferences[FRONT_VIDEO_RESOLUTION] = resolution }
    }

    suspend fun getFrontVideoFps(): Int = context.dataStore.data.map { it[FRONT_VIDEO_FPS] ?: 30 }.first()
    suspend fun setFrontVideoFps(fps: Int) {
        context.dataStore.edit { preferences -> preferences[FRONT_VIDEO_FPS] = fps }
    }

    suspend fun getFrontPhotoQuality(): Int = context.dataStore.data.map { it[FRONT_PHOTO_QUALITY] ?: 32 }.first()
    suspend fun setFrontPhotoQuality(quality: Int) {
        context.dataStore.edit { preferences -> preferences[FRONT_PHOTO_QUALITY] = quality }
    }

    // Overlay Methods
    suspend fun isShowTimestamp(): Boolean = showTimestampFlow.first()
    suspend fun setShowTimestamp(show: Boolean) {
        context.dataStore.edit { it[SHOW_TIMESTAMP] = show }
    }

    suspend fun isShowGps(): Boolean = showGpsFlow.first()
    suspend fun setShowGps(show: Boolean) {
        context.dataStore.edit { it[SHOW_GPS] = show }
    }

    suspend fun isShowAppName(): Boolean = showAppNameFlow.first()
    suspend fun setShowAppName(show: Boolean) {
        context.dataStore.edit { it[SHOW_APP_NAME] = show }
    }

    suspend fun isShowDeviceInfo(): Boolean = showDeviceInfoFlow.first()
    suspend fun setShowDeviceInfo(show: Boolean) {
        context.dataStore.edit { it[SHOW_DEVICE_INFO] = show }
    }

    suspend fun isShowLensInfo(): Boolean = showLensInfoFlow.first()
    suspend fun setShowLensInfo(show: Boolean) {
        context.dataStore.edit { it[SHOW_LENS_INFO] = show }
    }

    suspend fun setRecordingActive(active: Boolean) {
        context.dataStore.edit { it[IS_RECORDING_ACTIVE] = active }
    }

    // Deprecated but kept for compatibility for now if used elsewhere
    suspend fun isDarkMode(): Boolean = getThemeMode() == "DARK"
    suspend fun setDarkMode(isDark: Boolean) {
        setThemeMode(if (isDark) "DARK" else "LIGHT")
    }
}