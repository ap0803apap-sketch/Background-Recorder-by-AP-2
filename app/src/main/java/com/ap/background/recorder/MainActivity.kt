package com.ap.background.recorder

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.ap.background.recorder.data.RecorderPreferences
import com.ap.background.recorder.services.RecordingService
import com.ap.background.recorder.services.ShakeTriggerService
import com.ap.background.recorder.services.SmsTriggerService
import com.ap.background.recorder.ui.screens.HomeScreen
import com.ap.background.recorder.ui.screens.SettingsScreen
import com.ap.background.recorder.ui.screens.SplashBiometricScreen
import com.ap.background.recorder.ui.screens.TermsScreen
import com.ap.background.recorder.ui.theme.BackgroundRecorderTheme
import com.ap.background.recorder.utils.PermissionManager
import com.ap.background.recorder.utils.RecordingStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {
    private lateinit var prefs: RecorderPreferences
    private lateinit var permissionManager: PermissionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        prefs = RecorderPreferences(this)
        permissionManager = PermissionManager(this)

        if (intent?.action == Intent.ACTION_ASSIST) {
            handleAssistIntent()
            return
        }

        enableEdgeToEdge()
        
        // Request permissions on start
        permissionManager.requestRecordingPermissions(this) {
            // Permissions granted
        }

        // Start Trigger services if enabled
        lifecycleScope.launch {
            RecordingStatus.setRecording(prefs.isRecordingActiveFlow.first())

            if (prefs.shakeTriggerEnabledFlow.first()) {
                startForegroundService(Intent(this@MainActivity, ShakeTriggerService::class.java))
            }
            if (prefs.smsTriggerEnabledFlow.first()) {
                startForegroundService(Intent(this@MainActivity, SmsTriggerService::class.java))
            }
        }

        setContent {
            RecorderApp(prefs, this)
        }
    }

    private fun handleAssistIntent() {
        lifecycleScope.launch {
            val mode = prefs.getRecordingMode()
            
            val action = when (mode) {
                "VIDEO" -> RecordingService.ACTION_START_VIDEO
                "PHOTO" -> RecordingService.ACTION_START_PHOTO
                "AUDIO" -> RecordingService.ACTION_START_AUDIO
                else -> RecordingService.ACTION_START_VIDEO
            }
            
            val serviceIntent = Intent(this@MainActivity, RecordingService::class.java).apply {
                this.action = action
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
            finish()
        }
    }
}

@Composable
private fun RecorderApp(prefs: RecorderPreferences, activity: MainActivity) {
    val themeMode by prefs.themeModeFlow.collectAsStateWithLifecycle(initialValue = "SYSTEM")
    val isDynamicColor by prefs.dynamicColorFlow.collectAsStateWithLifecycle(initialValue = true)
    val isAmoledMode by prefs.amoledModeFlow.collectAsStateWithLifecycle(initialValue = false)
    val biometricAuthRequired by prefs.biometricEnabledFlow.collectAsStateWithLifecycle(initialValue = false)
    val isTermsAccepted by prefs.termsAcceptedFlow.collectAsStateWithLifecycle(initialValue = true)
    
    var isAuthenticated by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    BackgroundRecorderTheme(
        themeMode = themeMode,
        dynamicColor = isDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
        amoledMode = isAmoledMode
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = androidx.compose.material3.MaterialTheme.colorScheme.background
        ) {
            when {
                !isTermsAccepted -> {
                    TermsScreen(
                        onAccept = {
                            scope.launch {
                                prefs.setTermsAccepted(true)
                            }
                        }
                    )
                }
                biometricAuthRequired && !isAuthenticated -> {
                    SplashBiometricScreen(
                        onAuthenticationSuccess = { isAuthenticated = true },
                        activity = activity
                    )
                }
                else -> {
                    AppNavigation(prefs)
                }
            }
        }
    }
}

@Composable
private fun AppNavigation(prefs: RecorderPreferences) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            HomeScreen(
                prefs = prefs,
                onNavigateToSettings = { navController.navigate("settings") }
            )
        }
        composable("settings") {
            SettingsScreen(
                prefs = prefs,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}