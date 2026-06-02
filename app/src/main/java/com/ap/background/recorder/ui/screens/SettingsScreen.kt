package com.ap.background.recorder.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ap.background.recorder.R
import com.ap.background.recorder.data.RecorderPreferences
import com.ap.background.recorder.services.ShakeTriggerService
import com.ap.background.recorder.services.SmsTriggerService
import com.ap.background.recorder.utils.PermissionManager
import com.ap.background.recorder.utils.TriggerUtils
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

data class TimeTrigger(
    val id: String = UUID.randomUUID().toString(),
    val startTime: Long,
    val endTime: Long? = null,
    val isEnabled: Boolean = true
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    prefs: RecorderPreferences,
    onNavigateBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val permissionManager = remember { PermissionManager(context) }
    val clipboardManager = LocalClipboardManager.current

    val themeMode by prefs.themeModeFlow.collectAsStateWithLifecycle(initialValue = "SYSTEM")
    val isDynamicColor by prefs.dynamicColorFlow.collectAsStateWithLifecycle(initialValue = true)
    val isAmoledMode by prefs.amoledModeFlow.collectAsStateWithLifecycle(initialValue = false)
    val isBiometricEnabled by prefs.biometricEnabledFlow.collectAsStateWithLifecycle(initialValue = false)

    // Overlay Settings
    val showTimestamp by prefs.showTimestampFlow.collectAsStateWithLifecycle(initialValue = false)
    val showGps by prefs.showGpsFlow.collectAsStateWithLifecycle(initialValue = false)
    val showAppName by prefs.showAppNameFlow.collectAsStateWithLifecycle(initialValue = false)
    val showDeviceInfo by prefs.showDeviceInfoFlow.collectAsStateWithLifecycle(initialValue = false)
    val showLensInfo by prefs.showLensInfoFlow.collectAsStateWithLifecycle(initialValue = false)

    // Trigger Settings
    val isShakeEnabled by prefs.shakeTriggerEnabledFlow.collectAsStateWithLifecycle(initialValue = false)
    val shakeToggleOff by prefs.shakeToggleOffFlow.collectAsStateWithLifecycle(initialValue = false)
    val isSmsEnabled by prefs.smsTriggerEnabledFlow.collectAsStateWithLifecycle(initialValue = false)
    val smsToggleOff by prefs.smsToggleOffFlow.collectAsStateWithLifecycle(initialValue = false)
    val smsType by prefs.smsTriggerTypeFlow.collectAsStateWithLifecycle(initialValue = "CUSTOM")
    val smsText by prefs.smsTriggerTextFlow.collectAsStateWithLifecycle(initialValue = "")
    val isTimeEnabled by prefs.timeTriggerEnabledFlow.collectAsStateWithLifecycle(initialValue = false)
    val timeTriggersJson by prefs.timeTriggersJsonFlow.collectAsStateWithLifecycle(initialValue = "[]")
    val shakeIntensity by prefs.shakeIntensityFlow.collectAsStateWithLifecycle(initialValue = 50f)

    // UI States for Dialogs
    var showSmsCustomEditDialog by remember { mutableStateOf(false) }
    var showSmsRefreshConfirmDialog by remember { mutableStateOf(false) }
    var showSmsResetConfirmDialog by remember { mutableStateOf(false) }
    var showAddTimeTriggerDialog by remember { mutableStateOf(false) }
    var triggerToDelete by remember { mutableStateOf<TimeTrigger?>(null) }
    
    var tempSmsText by remember { mutableStateOf("") }
    var isLicenseExpanded by remember { mutableStateOf(false) }

    val timeTriggers = remember(timeTriggersJson) {
        val list = mutableListOf<TimeTrigger>()
        try {
            val array = JSONArray(timeTriggersJson)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    TimeTrigger(
                        id = obj.getString("id"),
                        startTime = obj.getLong("startTime"),
                        endTime = if (obj.has("endTime")) obj.getLong("endTime") else null,
                        isEnabled = obj.getBoolean("isEnabled")
                    )
                )
            }
        } catch (e: Exception) {}
        list
    }

    fun saveTimeTriggers(list: List<TimeTrigger>) {
        val array = JSONArray()
        list.forEach { trigger ->
            val obj = JSONObject().apply {
                put("id", trigger.id)
                put("startTime", trigger.startTime)
                trigger.endTime?.let { put("endTime", it) }
                put("isEnabled", trigger.isEnabled)
            }
            array.put(obj)
        }
        scope.launch { 
            prefs.setTimeTriggersJson(array.toString()) 
            if (prefs.isTimeTriggerEnabled()) {
                list.forEach { 
                    if (it.isEnabled) {
                        TriggerUtils.scheduleAlarm(context, it.id, it.startTime, true)
                        it.endTime?.let { end -> TriggerUtils.scheduleAlarm(context, it.id, end, false) }
                    } else {
                        TriggerUtils.cancelAlarm(context, it.id, true)
                        TriggerUtils.cancelAlarm(context, it.id, false)
                    }
                }
            }
        }
    }

    // Camera Settings State
    var videoResolution by remember { mutableStateOf("1080p") }
    var videoFps by remember { mutableIntStateOf(30) }
    var photoMegapixel by remember { mutableIntStateOf(48) }
    var frontVideoResolution by remember { mutableStateOf("1080p") }
    var frontVideoFps by remember { mutableIntStateOf(30) }
    var frontPhotoQuality by remember { mutableIntStateOf(32) }

    LaunchedEffect(Unit) {
        videoResolution = prefs.getVideoResolution()
        videoFps = prefs.getVideoFps()
        photoMegapixel = prefs.getPhotoMegapixel()
        frontVideoResolution = prefs.getFrontVideoResolution()
        frontVideoFps = prefs.getFrontVideoFps()
        frontPhotoQuality = prefs.getFrontPhotoQuality()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Card 1: App Theme
            Card(modifier = Modifier.animateContentSize()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(painterResource(R.drawable.theme), null, modifier = Modifier.size(24.dp).padding(end = 8.dp), tint = MaterialTheme.colorScheme.primary)
                        Text("App Theme", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    ThemeRadioGroup(
                        selectedOption = themeMode,
                        onOptionSelected = { scope.launch { prefs.setThemeMode(it) } }
                    )
                    SettingSwitch(
                        label = "Dynamic Color (Material You)",
                        checked = isDynamicColor,
                        onCheckedChange = { scope.launch { prefs.setDynamicColor(it) } }
                    )
                    SettingSwitch(
                        label = "AMOLED Dark Mode",
                        checked = isAmoledMode,
                        onCheckedChange = { scope.launch { prefs.setAmoledMode(it) } },
                        enabled = themeMode == "DARK" || (themeMode == "SYSTEM" && isSystemInDark())
                    )
                }
            }

            // Card 2: Camera Settings
            Card(modifier = Modifier.animateContentSize()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(painterResource(R.drawable.camsettings), null, modifier = Modifier.size(24.dp).padding(end = 8.dp), tint = MaterialTheme.colorScheme.primary)
                        Text("Camera Settings", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    }
                    
                    Text("Back Camera", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                    SettingOption("Video Resolution", videoResolution) {
                        val next = if (videoResolution == "1080p") "4K" else "1080p"
                        videoResolution = next
                        scope.launch { prefs.setVideoResolution(next) }
                    }
                    SettingOption("Video FPS", "$videoFps FPS") {
                        val next = if (videoFps == 30) 60 else 30
                        videoFps = next
                        scope.launch { prefs.setVideoFps(next) }
                    }
                    SettingOption("Photo Quality", "$photoMegapixel MP") {
                        val next = if (photoMegapixel == 48) 12 else 48
                        photoMegapixel = next
                        scope.launch { prefs.setPhotoMegapixel(next) }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    
                    Text("Front Camera", style = MaterialTheme.typography.labelLarge)
                    SettingOption("Video Resolution", frontVideoResolution) {
                        val next = if (frontVideoResolution == "1080p") "720p" else "1080p"
                        frontVideoResolution = next
                        scope.launch { prefs.setFrontVideoResolution(next) }
                    }
                    SettingOption("Video FPS", "$frontVideoFps FPS") {
                        val next = if (frontVideoFps == 30) 24 else 30
                        frontVideoFps = next
                        scope.launch { prefs.setFrontVideoFps(next) }
                    }
                    SettingOption("Photo Quality", "$frontPhotoQuality MP") {
                        val next = if (frontPhotoQuality == 32) 8 else 32
                        frontPhotoQuality = next
                        scope.launch { prefs.setFrontPhotoQuality(next) }
                    }
                }
            }

            // Card 3: Trigger Methods
            Card(modifier = Modifier.animateContentSize()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(painterResource(R.drawable.trigger), null, modifier = Modifier.size(24.dp).padding(end = 8.dp), tint = MaterialTheme.colorScheme.primary)
                        Text("Triggering Methods", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    }
                    
                    SettingSwitch("Side Key (Power/Assistant)", true, {}, false, icon = painterResource(R.drawable.side))
                    SettingSwitch("Quick Settings Panel", true, {}, false, icon = painterResource(R.drawable.panel))

                    // Shake Trigger
                    SettingSwitch(
                        label = "Shake Device to Trigger",
                        checked = isShakeEnabled,
                        onCheckedChange = { 
                            scope.launch { 
                                prefs.setShakeTriggerEnabled(it)
                                val intent = Intent(context, ShakeTriggerService::class.java)
                                if (it) context.startForegroundService(intent) else context.stopService(intent)
                            } 
                        },
                        icon = painterResource(R.drawable.shake)
                    )
                    AnimatedVisibility(
                        visible = isShakeEnabled,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Column(modifier = Modifier.padding(start = 16.dp)) {
                            SettingSwitch("Turn off on second shake", shakeToggleOff, { scope.launch { prefs.setShakeToggleOffEnabled(it) } })
                            Text("Shake Intensity: ${shakeIntensity.toInt()}%", style = MaterialTheme.typography.bodySmall)
                            Slider(
                                value = shakeIntensity,
                                onValueChange = { scope.launch { prefs.setShakeIntensity(it) } },
                                valueRange = 0f..100f,
                                steps = 100
                            )
                        }
                    }

                    // SMS Trigger
                    SettingSwitch(
                        label = "SMS Received Trigger",
                        checked = isSmsEnabled,
                        onCheckedChange = { checked ->
                            if (checked) {
                                activity?.let { permissionManager.requestSmsPermission(it) { granted ->
                                    if (granted) {
                                        scope.launch { 
                                            prefs.setSmsTriggerEnabled(true)
                                            val intent = Intent(context, SmsTriggerService::class.java)
                                            context.startForegroundService(intent)
                                        }
                                    }
                                } }
                            } else {
                                scope.launch { 
                                    prefs.setSmsTriggerEnabled(false)
                                    val intent = Intent(context, SmsTriggerService::class.java)
                                    context.stopService(intent)
                                }
                            }
                        },
                        icon = painterResource(R.drawable.sms)
                    )
                    AnimatedVisibility(
                        visible = isSmsEnabled,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Column(modifier = Modifier.padding(start = 16.dp)) {
                            SettingSwitch("Turn off on second SMS", smsToggleOff, { scope.launch { prefs.setSmsToggleOffEnabled(it) } })
                            Row(Modifier.fillMaxWidth().selectableGroup()) {
                                Row(
                                    modifier = Modifier.selectable(
                                        selected = smsType == "CUSTOM",
                                        onClick = { scope.launch { prefs.setSmsTriggerType("CUSTOM") } },
                                        role = Role.RadioButton
                                    ).padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(smsType == "CUSTOM", null)
                                    Text("Custom", Modifier.padding(start = 4.dp))
                                }
                                Row(
                                    modifier = Modifier.selectable(
                                        selected = smsType == "CODE",
                                        onClick = { 
                                            scope.launch { 
                                                prefs.setSmsTriggerType("CODE")
                                                if (smsText.isEmpty() || smsText.length != 64) prefs.setSmsTriggerText(generate64DigitCode())
                                            } 
                                        },
                                        role = Role.RadioButton
                                    ).padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(smsType == "CODE", null)
                                    Text("Generated Code", Modifier.padding(start = 4.dp))
                                }
                            }
                            
                            if (smsType == "CUSTOM") {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(if (smsText.isEmpty()) "Not set" else smsText, modifier = Modifier.weight(1f))
                                    Row {
                                        IconButton(onClick = { tempSmsText = smsText; showSmsCustomEditDialog = true }) { Icon(Icons.Default.Edit, "Edit") }
                                        IconButton(onClick = { clipboardManager.setText(AnnotatedString(smsText)) }) { Icon(Icons.Default.ContentCopy, "Copy") }
                                    }
                                }
                            } else {
                                Column {
                                    Text(smsText, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(8.dp))
                                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceEvenly) {
                                        IconButton(onClick = { showSmsRefreshConfirmDialog = true }) { Icon(Icons.Default.Refresh, "Refresh") }
                                        IconButton(onClick = { clipboardManager.setText(AnnotatedString(smsText)) }) { Icon(Icons.Default.ContentCopy, "Copy") }
                                        IconButton(onClick = { showSmsResetConfirmDialog = true }) { Icon(Icons.Default.RestartAlt, "Reset") }
                                    }
                                }
                            }
                        }
                    }

                    // Time Trigger
                    SettingSwitch(
                        label = "Time-Based Trigger",
                        checked = isTimeEnabled,
                        onCheckedChange = { 
                            scope.launch { 
                                prefs.setTimeTriggerEnabled(it)
                                if (!it) timeTriggers.forEach { t -> TriggerUtils.cancelAlarm(context, t.id, true); TriggerUtils.cancelAlarm(context, t.id, false) }
                                else timeTriggers.forEach { t -> if (t.isEnabled) { TriggerUtils.scheduleAlarm(context, t.id, t.startTime, true); t.endTime?.let { e -> TriggerUtils.scheduleAlarm(context, t.id, e, false) } } }
                            } 
                        },
                        icon = painterResource(R.drawable.time)
                    )
                    AnimatedVisibility(
                        visible = isTimeEnabled,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Column {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), Arrangement.End) {
                                Button(onClick = { showAddTimeTriggerDialog = true }) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("Add Trigger") }
                            }
                            if (timeTriggers.isEmpty()) Text("No triggers added", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            timeTriggers.forEach { trigger ->
                                val startStr = SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault()).format(Date(trigger.startTime))
                                val endStr = trigger.endTime?.let { " - " + SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(it)) } ?: ""
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column { Text(startStr + endStr, style = MaterialTheme.typography.bodyLarge); Text(if (trigger.endTime == null) "One-time start" else "Duration", style = MaterialTheme.typography.bodySmall) }
                                    Row {
                                        Checkbox(trigger.isEnabled, { checked -> saveTimeTriggers(timeTriggers.map { if (it.id == trigger.id) it.copy(isEnabled = checked) else it }) })
                                        IconButton(onClick = { triggerToDelete = trigger }) { Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.error) }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Card 4: Overlay Settings
            Card(modifier = Modifier.animateContentSize()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Layers, null, modifier = Modifier.size(24.dp).padding(end = 8.dp), tint = MaterialTheme.colorScheme.primary)
                        Text("Overlay Settings", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    }
                    Text("Burn info directly onto video and photos", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    SettingSwitch(
                        label = "Show Timestamp (Date/Time)",
                        checked = showTimestamp,
                        onCheckedChange = { scope.launch { prefs.setShowTimestamp(it) } }
                    )
                    SettingSwitch(
                        label = "Show GPS Coordinates",
                        checked = showGps,
                        onCheckedChange = { checked ->
                            if (checked) {
                                activity?.let { permissionManager.requestLocationPermission(it) { granted ->
                                    if (granted) scope.launch { prefs.setShowGps(true) }
                                } }
                            } else {
                                scope.launch { prefs.setShowGps(false) }
                            }
                        }
                    )
                    SettingSwitch(
                        label = "Show App Name",
                        checked = showAppName,
                        onCheckedChange = { scope.launch { prefs.setShowAppName(it) } }
                    )
                    SettingSwitch(
                        label = "Show Device Information",
                        checked = showDeviceInfo,
                        onCheckedChange = { scope.launch { prefs.setShowDeviceInfo(it) } }
                    )
                    SettingSwitch(
                        label = "Show Lens Information",
                        checked = showLensInfo,
                        onCheckedChange = { scope.launch { prefs.setShowLensInfo(it) } }
                    )
                }
            }

            // Card 5: Security
            Card(modifier = Modifier.animateContentSize()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(painterResource(R.drawable.security), null, modifier = Modifier.size(24.dp).padding(end = 8.dp), tint = MaterialTheme.colorScheme.primary)
                        Text("Security", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    }
                    SettingSwitch("Biometric Lock", isBiometricEnabled, { scope.launch { prefs.setBiometricEnabled(it) } }, icon = painterResource(R.drawable.fingerprint))
                }
            }

            // Card 5: About
            Card(modifier = Modifier.animateContentSize()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(painterResource(R.drawable.about), null, modifier = Modifier.size(24.dp).padding(end = 8.dp), tint = MaterialTheme.colorScheme.primary)
                        Text("About", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    }
                    
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                        Icon(painterResource(R.drawable.dev), null, modifier = Modifier.size(20.dp).padding(end = 8.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Developer: AP")
                    }
                    
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(painterResource(R.drawable.mail), null, modifier = Modifier.size(20.dp).padding(end = 8.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(onClick = {
                            val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:ap0803apap@gmail.com")).apply { putExtra(Intent.EXTRA_SUBJECT, "Background Recorder Feedback") }
                            try { context.startActivity(intent) } catch (e: Exception) {}
                        }, contentPadding = PaddingValues(0.dp)) { Text("Email: ap0803apap@gmail.com", textDecoration = TextDecoration.Underline) }
                    }
                    
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(painterResource(R.drawable.sourcecode), null, modifier = Modifier.size(20.dp).padding(end = 8.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/ap0803apap-sketch"))) }, contentPadding = PaddingValues(0.dp)) { Text("GitHub: View Source Code", textDecoration = TextDecoration.Underline) }
                    }
                    
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { isLicenseExpanded = !isLicenseExpanded }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(painterResource(R.drawable.licence), null, modifier = Modifier.size(20.dp).padding(end = 8.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("License Info", fontWeight = FontWeight.SemiBold)
                        }
                        Icon(if (isLicenseExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
                    }
                    AnimatedVisibility(visible = isLicenseExpanded) {
                        Text(LICENSE_TEXT, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 8.dp))
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Version 1.0.0", style = MaterialTheme.typography.bodySmall, modifier = Modifier.align(Alignment.CenterHorizontally))
                }
            }
        }
    }

    // Confirmation Dialogs
    if (triggerToDelete != null) {
        AlertDialog(
            onDismissRequest = { triggerToDelete = null },
            title = { Text("Delete Trigger") },
            text = { Text("Are you sure you want to delete this scheduled trigger?") },
            confirmButton = { TextButton(onClick = {
                val t = triggerToDelete!!
                TriggerUtils.cancelAlarm(context, t.id, true); TriggerUtils.cancelAlarm(context, t.id, false)
                saveTimeTriggers(timeTriggers.filter { it.id != t.id })
                triggerToDelete = null
            }) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { triggerToDelete = null }) { Text("Cancel") } }
        )
    }

    if (showSmsCustomEditDialog) {
        AlertDialog(onDismissRequest = { showSmsCustomEditDialog = false }, title = { Text("Edit Trigger Text") },
            text = { Column { Text("Enter text to trigger recording."); OutlinedTextField(tempSmsText, { tempSmsText = it }, label = { Text("Trigger Text") }, singleLine = true) } },
            confirmButton = { TextButton(onClick = { scope.launch { prefs.setSmsTriggerText(tempSmsText) }; showSmsCustomEditDialog = false }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { showSmsCustomEditDialog = false }) { Text("Cancel") } }
        )
    }
    if (showSmsRefreshConfirmDialog) {
        AlertDialog(onDismissRequest = { showSmsRefreshConfirmDialog = false }, title = { Text("Refresh Code") }, text = { Text("Generate new 64-digit code?") },
            confirmButton = { TextButton(onClick = { scope.launch { prefs.setSmsTriggerText(generate64DigitCode()) }; showSmsRefreshConfirmDialog = false }) { Text("Refresh") } },
            dismissButton = { TextButton(onClick = { showSmsRefreshConfirmDialog = false }) { Text("Cancel") } }
        )
    }
    if (showSmsResetConfirmDialog) {
        AlertDialog(onDismissRequest = { showSmsResetConfirmDialog = false }, title = { Text("Reset SMS Trigger") }, text = { Text("Reset to Custom mode?") },
            confirmButton = { TextButton(onClick = { scope.launch { prefs.setSmsTriggerText(""); prefs.setSmsTriggerType("CUSTOM") }; showSmsResetConfirmDialog = false }) { Text("Reset") } },
            dismissButton = { TextButton(onClick = { showSmsResetConfirmDialog = false }) { Text("Cancel") } }
        )
    }

    if (showAddTimeTriggerDialog) {
        AddTriggerDialog(
            onDismiss = { showAddTimeTriggerDialog = false },
            onConfirm = { start, end ->
                val nEnd = end ?: start
                if (timeTriggers.any { t -> (t.endTime ?: t.startTime).let { e -> start <= e && nEnd >= t.startTime } }) false
                else { saveTimeTriggers(timeTriggers + TimeTrigger(startTime = start, endTime = end)); showAddTimeTriggerDialog = false; true }
            }
        )
    }
}

@Composable
fun ThemeRadioGroup(selectedOption: String, onOptionSelected: (String) -> Unit) {
    val options = listOf("SYSTEM", "LIGHT", "DARK")
    Column(Modifier.selectableGroup()) {
        options.forEach { text ->
            Row(
                modifier = Modifier.fillMaxWidth().height(40.dp).selectable(
                    selected = text == selectedOption,
                    onClick = { onOptionSelected(text) },
                    role = Role.RadioButton
                ).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(text == selectedOption, null)
                Text(text.lowercase().replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 12.dp))
            }
        }
    }
}

@Composable
private fun SettingSwitch(
    label: String, 
    checked: Boolean, 
    onCheckedChange: (Boolean) -> Unit, 
    enabled: Boolean = true,
    icon: androidx.compose.ui.graphics.painter.Painter? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, null, modifier = Modifier.size(24.dp).padding(end = 12.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Text(label, Modifier.weight(1f), color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
        Switch(checked, onCheckedChange, enabled = enabled)
    }
}

@Composable
private fun SettingOption(label: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Text(value, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTriggerDialog(onDismiss: () -> Unit, onConfirm: (Long, Long?) -> Boolean) {
    var triggerType by remember { mutableStateOf("SPECIFIC") }
    var startTime by remember { mutableLongStateOf(0L) }
    var endTime by remember { mutableLongStateOf(0L) }
    var showStartDatePicker by remember { mutableStateOf(false) }
    var showStartTimePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }
    var showEndTimePicker by remember { mutableStateOf(false) }
    val startDatePickerState = rememberDatePickerState()
    val startTimePickerState = rememberTimePickerState(initialHour = 0, initialMinute = 0, is24Hour = false)
    val endDatePickerState = rememberDatePickerState()
    val endTimePickerState = rememberTimePickerState(initialHour = 0, initialMinute = 0, is24Hour = false)
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(onDismissRequest = onDismiss, title = { Text("Add Time Trigger") },
        text = {
            Column {
                Row(Modifier.selectableGroup()) {
                    Row(
                        modifier = Modifier.selectable(
                            selected = triggerType == "SPECIFIC",
                            onClick = { triggerType = "SPECIFIC" },
                            role = Role.RadioButton
                        ).padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) { RadioButton(triggerType == "SPECIFIC", null); Text("Time") }
                    Row(
                        modifier = Modifier.selectable(
                            selected = triggerType == "DURATION",
                            onClick = { triggerType = "DURATION" },
                            role = Role.RadioButton
                        ).padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) { RadioButton(triggerType == "DURATION", null); Text("Duration") }
                }
                TextButton(onClick = { showStartDatePicker = true }) { Text(if (startTime == 0L) "Select Start" else "Start: " + SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault()).format(Date(startTime))) }
                if (triggerType == "DURATION") { TextButton(onClick = { showEndDatePicker = true }) { Text(if (endTime == 0L) "Select End" else "End: " + SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault()).format(Date(endTime))) } }
                errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = { TextButton(onClick = {
            if (startTime == 0L) errorMessage = "Select start time"
            else if (triggerType == "DURATION" && (endTime == 0L || endTime <= startTime)) errorMessage = "End must be after start"
            else if (!onConfirm(startTime, if (triggerType == "DURATION") endTime else null)) errorMessage = "Overlap error"
        }) { Text("Add") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
    if (showStartDatePicker) { DatePickerDialog(onDismissRequest = { showStartDatePicker = false }, confirmButton = { TextButton(onClick = { showStartDatePicker = false; showStartTimePicker = true }) { Text("Next") } }) { DatePicker(startDatePickerState) } }
    if (showStartTimePicker) { AlertDialog(onDismissRequest = { showStartTimePicker = false }, confirmButton = { TextButton(onClick = {
        val cal = Calendar.getInstance(); startDatePickerState.selectedDateMillis?.let { cal.timeInMillis = it }; cal.set(Calendar.HOUR_OF_DAY, startTimePickerState.hour); cal.set(Calendar.MINUTE, startTimePickerState.minute); startTime = cal.timeInMillis; showStartTimePicker = false
    }) { Text("OK") } }, text = { TimePicker(startTimePickerState) }) }
    if (showEndDatePicker) { DatePickerDialog(onDismissRequest = { showEndDatePicker = false }, confirmButton = { TextButton(onClick = { showEndDatePicker = false; showEndTimePicker = true }) { Text("Next") } }) { DatePicker(endDatePickerState) } }
    if (showEndTimePicker) { AlertDialog(onDismissRequest = { showEndTimePicker = false }, confirmButton = { TextButton(onClick = {
        val cal = Calendar.getInstance(); endDatePickerState.selectedDateMillis?.let { cal.timeInMillis = it }; cal.set(Calendar.HOUR_OF_DAY, endTimePickerState.hour); cal.set(Calendar.MINUTE, endTimePickerState.minute); endTime = cal.timeInMillis; showEndTimePicker = false
    }) { Text("OK") } }, text = { TimePicker(endTimePickerState) }) }
}

@Composable
fun isSystemInDark(): Boolean = androidx.compose.foundation.isSystemInDarkTheme()

fun generate64DigitCode(): String = (1..64).map { "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789".random() }.joinToString("")

private const val LICENSE_TEXT = """Attribution-NonCommercial 4.0 International

=======================================================================

Creative Commons Corporation ("Creative Commons") is not a law firm and
does not provide legal services or legal advice. Distribution of
Creative Commons public licenses does not create a lawyer-client or
other relationship. Creative Commons makes its licenses and related
information available on an "as-is" basis. Creative Commons gives no
warranties regarding its licenses, any material licensed under their
terms and conditions, or any related information. Creative Commons
disclaims all liability for damages resulting from their use to the
fullest extent possible.

Using Creative Commons Public Licenses

Creative Commons public licenses provide a standard set of terms and
conditions that creators and other rights holders may use to share
original works of authorship and other material subject to copyright
and certain other rights specified in the public license below. The
following considerations are for informational purposes only, are not
exhaustive, and do not form part of our licenses.

     Considerations for licensors: Our public licenses are
     intended for use by those authorized to give the public
     permission to use material in ways otherwise restricted by
     copyright and certain other rights. Our licenses are
     irrevocable. Licensors should read and understand the terms
     and conditions of the license they choose before applying it.
     Licensors should also secure all rights necessary before
     applying our licenses so that the public can reuse the
     material as expected. Licensors should clearly mark any
     material not subject to the license. This includes other CC-
     licensed material, or material used under an exception or
     limitation to copyright. More considerations for licensors:
    wiki.creativecommons.org/Considerations_for_licensors

     Considerations for the public: By using one of our public
     licenses, a licensor grants the public permission to use the
     licensed material under specified terms and conditions. If
     the licensor's permission is not necessary for any reason--for
     example, because of any applicable exception or limitation to
     copyright--then that use is not regulated by the license. Our
     licenses grant only permissions under copyright and certain
     other rights that a licensor has authority to grant. Use of
     the licensed material may still be restricted for other
     reasons, including because others have copyright or other
     rights in the material. A licensor may make special requests,
     such as asking that all changes be marked or described.
     Although not required by our licenses, you are encouraged to
     respect those requests where reasonable. More considerations
     for the public:
    wiki.creativecommons.org/Considerations_for_licensees

=======================================================================

Creative Commons Attribution-NonCommercial 4.0 International Public
License

By exercising the Licensed Rights (defined below), You accept and agree
to be bound by the terms and conditions of this Creative Commons
Attribution-NonCommercial 4.0 International Public License ("Public
License"). To the extent this Public License may be interpreted as a
contract, You are granted the Licensed Rights in consideration of Your
acceptance of these terms and conditions, and the Licensor grants You
such rights in consideration of benefits the Licensor receives from
making the Licensed Material available under these terms and
conditions.


Section 1 -- Definitions.

  a. Adapted Material means material subject to Copyright and Similar
     Rights that is derived from or based upon the Licensed Material
     and in which the Licensed Material is translated, altered,
     arranged, transformed, or otherwise modified in a manner requiring
     permission under the Copyright and Similar Rights held by the
     Licensor. For purposes of this Public License, where the Licensed
     Material is a musical work, performance, or sound recording,
     Adapted Material is always produced where the Licensed Material is
     synched in timed relation with a moving image.

  b. Adapter's License means the license You apply to Your Copyright
     and Similar Rights in Your contributions to Adapted Material in
     accordance with the terms and conditions of this Public License.

  c. Copyright and Similar Rights means copyright and/or similar rights
     closely related to copyright including, without limitation,
     performance, broadcast, sound recording, and Sui Generis Database
     Rights, without regard to how the rights are labeled or
     categorized. For purposes of this Public License, the rights
     specified in Section 2(b)(1)-(2) are not Copyright and Similar
     Rights.
  d. Effective Technological Measures means those measures that, in the
     absence of proper authority, may not be circumvented under laws
     fulfilling obligations under Article 11 of the WIPO Copyright
     Treaty adopted on December 20, 1996, and/or similar international
     agreements.

  e. Exceptions and Limitations means fair use, fair dealing, and/or
     any other exception or limitation to Copyright and Similar Rights
     that applies to Your use of the Licensed Material.

  f. Licensed Material means the artistic or literary work, database,
     or other material to which the Licensor applied this Public
     License.

  g. Licensed Rights means the rights granted to You subject to the
     terms and conditions of this Public License, which are limited to
     all Copyright and Similar Rights that apply to Your use of the
     Licensed Material and that the Licensor has authority to license.

  h. Licensor means the individual(s) or entity(ies) granting rights
     under this Public License.

  i. NonCommercial means not primarily intended for or directed towards
     commercial advantage or monetary compensation. For purposes of
     this Public License, the exchange of the Licensed Material for
     other material subject to Copyright and Similar Rights by digital
     file-sharing or similar means is NonCommercial provided there is
     no payment of monetary compensation in connection with the
     exchange.

  j. Share means to provide material to the public by any means or
     process that requires permission under the Licensed Rights, such
     as reproduction, public display, public performance, distribution,
     dissemination, communication, or importation, and to make material
     available to the public including in ways that members of the
     public may access the material from a place and at a time
     individually chosen by them.

  k. Sui Generis Database Rights means rights other than copyright
     resulting from Directive 96/9/EC of the European Parliament and of
     the Council of 11 March 1996 on the legal protection of databases,
     as amended and/or succeeded, as well as other essentially
     equivalent rights anywhere in the world.

  l. You means the individual or entity exercising the Licensed Rights
     under this Public License. Your has a corresponding meaning.


Section 2 -- Scope.

  a. License grant.

       1. Subject to the terms and conditions of this Public License,
          the Licensor hereby grants You a worldwide, royalty-free,
          non-sublicensable, non-exclusive, irrevocable license to
          exercise the Licensed Rights in the Licensed Material to:

            a. reproduce and Share the Licensed Material, in whole or
               in part, for NonCommercial purposes only; and

            b. produce, reproduce, and Share Adapted Material for
               NonCommercial purposes only.

       2. Exceptions and Limitations. For the avoidance of doubt, where
          Exceptions and Limitations apply to Your use, this Public
          License does not apply, and You do not need to comply with
          its terms and conditions.

       3. Term. The term of this Public License is specified in Section
          6(a).

       4. Media and formats; technical modifications allowed. The
          Licensor authorizes You to exercise the Licensed Rights in
          all media and formats whether now known or hereafter created,
          and to make technical modifications necessary to do so. The
          Licensor waives and/or agrees not to assert any right or
          authority to forbid You from making technical modifications
          necessary to exercise the Licensed Rights, including
          technical modifications necessary to circumvent Effective
          Technological Measures. For purposes of this Public License,
          simply making modifications authorized by this Section 2(a)
          (4) never produces Adapted Material.

       5. Downstream recipients.

            a. Offer from the Licensor -- Licensed Material. Every
               recipient of the Licensed Material automatically
               receives an offer from the Licensor to exercise the
               Licensed Rights under the terms and conditions of this
               Public License.

            b. No downstream restrictions. You may not offer or impose
               any additional or different terms or conditions on, or
               apply any Effective Technological Measures to, the
               Licensed Material if doing so restricts exercise of the
               Licensed Rights by any recipient of the Licensed
               Material.

       6. No endorsement. Nothing in this Public License constitutes or
          may be construed as permission to assert or imply that You
          are, or that Your use of the Licensed Material is, connected
          with, or sponsored, endorsed, or granted official status by,
          the Licensor or others designated to receive attribution as
          provided in Section 3(a)(1)(A)(i).

  b. Other rights.

       1. Moral rights, such as the right of integrity, are not
          licensed under this Public License, nor are publicity,
          privacy, and/or other similar personality rights; however, to
          the extent possible, the Licensor waives and/or agrees not to
          assert any such rights held by the Licensor to the limited
          extent necessary to allow You to exercise the Licensed
          Rights, but not otherwise.

       2. Patent and trademark rights are not licensed under this
          Public License.

       3. To the extent possible, the Licensor waives any right to
          collect royalties from You for the exercise of the Licensed
          Rights, whether directly or through a collecting society
          under any voluntary or waivable statutory or compulsory
          licensing scheme. In all other cases the Licensor expressly
          reserves any right to collect such royalties, including when
          the Licensed Material is used other than for NonCommercial
          purposes.


Section 3 -- License Conditions.

Your exercise of the Licensed Rights is expressly made subject to the
following conditions.

  a. Attribution.

       1. If You Share the Licensed Material (including in modified
          form), You must:

            a. retain the following if it is supplied by the Licensor
               with the Licensed Material:

                 i. identification of the creator(s) of the Licensed
                    Material and any others designated to receive
                    attribution, in any reasonable manner requested by
                    the Licensor (including by pseudonym if
                    designated);

                ii. a copyright notice;

               iii. a notice that refers to this Public License;

                iv. a notice that refers to the disclaimer of
                    warranties;

                 v. a URI or hyperlink to the Licensed Material to the
                    extent reasonably practicable;

            b. indicate if You modified the Licensed Material and
               retain an indication of any previous modifications; and

            c. indicate the Licensed Material is licensed under this
               Public License, and include the text of, or the URI or
               hyperlink to, this Public License.

       2. You may satisfy the conditions in Section 3(a)(1) in any
          reasonable manner based on the medium, means, and context in
          which You Share the Licensed Material. For example, it may be
          reasonable to satisfy the conditions by providing a URI or
          hyperlink to a resource that includes the required
          information.

       3. If requested by the Licensor, You must remove any of the
          information required by Section 3(a)(1)(A) to the extent
          reasonably practicable.

       4. If You Share Adapted Material You produce, the Adapter's
          License You apply must not prevent recipients of the Adapted
          Material from complying with this Public License.


Section 4 -- Sui Generis Database Rights.

Where the Licensed Rights include Sui Generis Database Rights that
apply to Your use of the Licensed Material:

  a. for the avoidance of doubt, Section 2(a)(1) grants You the right
     to extract, reuse, reproduce, and Share all or a substantial
     portion of the contents of the database for NonCommercial purposes
     only;

  b. if You include all or a substantial portion of the database
     contents in a database in which You have Sui Generis Database
     Rights, then the database in which You have Sui Generis Database
     Rights (but not its individual contents) is Adapted Material; and

  c. You must comply with the conditions in Section 3(a) if You Share
     all or a substantial portion of the contents of the database.

For the avoidance of doubt, this Section 4 supplements and does not
replace Your obligations under this Public License where the Licensed
Rights include other Copyright and Similar Rights.


Section 5 -- Disclaimer of Warranties and Limitation of Liability.

  a. UNLESS OTHERWISE SEPARATELY UNDERTAKEN BY THE LICENSOR, TO THE
     EXTENT POSSIBLE, THE LICENSOR OFFERS THE LICENSED MATERIAL AS-IS
     AND AS-AVAILABLE, AND MAKES NO REPRESENTATIONS OR WARRANTIES OF
     ANY KIND CONCERNING THE LICENSED MATERIAL, WHETHER EXPRESS,
     IMPLIED, STATUTORY, OR OTHER. THIS INCLUDES, WITHOUT LIMITATION,
     WARRANTIES OF TITLE, MERCHANTABILITY, FITNESS FOR A PARTICULAR
     PURPOSE, NON-INFRINGEMENT, ABSENCE OF LATENT OR OTHER DEFECTS,
     ACCURACY, OR THE PRESENCE OR ABSENCE OF ERRORS, WHETHER OR NOT
     KNOWN OR DISCOVERABLE. WHERE DISCLAIMERS OF WARRANTIES ARE NOT
     ALLOWED IN FULL OR IN PART, THIS DISCLAIMER MAY NOT APPLY TO YOU.

  b. TO THE EXTENT POSSIBLE, IN NO EVENT WILL THE LICENSOR BE LIABLE
     TO YOU ON ANY LEGAL THEORY (INCLUDING, WITHOUT LIMITATION,
     NEGLIGENCE) OR OTHERWISE FOR ANY DIRECT, SPECIAL, INDIRECT,
     INCIDENTAL, CONSEQUENTIAL, PUNITIVE, EXEMPLARY, OR OTHER LOSSES,
     COSTS, EXPENSES, OR DAMAGES ARISING OUT OF THIS PUBLIC LICENSE OR
     USE OF THE LICENSED MATERIAL, EVEN IF THE LICENSOR HAS BEEN
     ADVISED OF THE POSSIBILITY OF SUCH LOSSES, COSTS, EXPENSES, OR
     DAMAGES. WHERE A LIMITATION OF LIABILITY IS NOT ALLOWED IN FULL OR
     IN PART, THIS LIMITATION MAY NOT APPLY TO YOU.

  c. The disclaimer of warranties and limitation of liability provided
     above shall be interpreted in a manner that, to the extent
     possible, most closely approximates an absolute disclaimer and
     waiver of all liability.


Section 6 -- Term and Termination.

  a. This Public License applies for the term of the Copyright and
     Similar Rights licensed here. However, if You fail to comply with
     this Public License, then Your rights under this Public License
     terminate automatically.

  b. Where Your right to use the Licensed Material has terminated under
     Section 6(a), it reinstates:

       1. automatically as of the date the violation is cured, provided
          it is cured within 30 days of Your discovery of the
          violation; or

       2. upon express reinstatement by the Licensor.

     For the avoidance of doubt, this Section 6(b) does not affect any
     right the Licensor may have to seek remedies for Your violations
     of this Public License.

  c. For the avoidance of doubt, the Licensor may also offer the
     Licensed Material under separate terms or conditions or stop
     distributing the Licensed Material at any time; however, doing so
     will not terminate this Public License.

  d. Sections 1, 5, 6, 7, and 8 survive termination of this Public
     License.


Section 7 -- Other Terms and Conditions.

  a. The Licensor shall not be bound by any additional or different
     terms or conditions communicated by You unless expressly agreed.

  b. Any arrangements, understandings, or agreements regarding the
     Licensed Material not stated herein are separate from and
     independent of the terms and conditions of this Public License.


Section 8 -- Interpretation.

  a. For the avoidance of doubt, this Public License does not, and
     shall not be interpreted to, reduce, limit, restrict, or impose
     conditions on any use of the Licensed Material that could lawfully
     be made without permission under this Public License.

  b. To the extent possible, if any provision of this Public License is
     deemed unenforceable, it shall be automatically reformed to the
     minimum extent necessary to make it enforceable. If the provision
     cannot be reformed, it shall be severed from this Public License
     without affecting the enforceability of the remaining terms and
     conditions.

  c. No term or condition of this Public License will be waived and no
     failure to comply consented to unless expressly agreed to by the
     Licensor.

  d. Nothing in this Public License constitutes or may be interpreted
     as a limitation upon, or waiver of, any privileges and immunities
     that apply to the Licensor or You, including from the legal
     processes of any jurisdiction or authority.

=======================================================================

Creative Commons is not a party to its public
licenses. Notwithstanding, Creative Commons may elect to apply one of
its public licenses to material it publishes and in those instances
will be considered the “Licensor.” The text of the Creative Commons
public licenses is dedicated to the public domain under the CC0 Public
Domain Dedication. Except for the limited purpose of indicating that
material is shared under a Creative Commons public license or as
otherwise permitted by the Creative Commons policies published at
creativecommons.org/policies, Creative Commons does not authorize the
use of the trademark "Creative Commons" or any other trademark or logo
of Creative Commons without its prior written consent including,
without limitation, in connection with any unauthorized modifications
to any of its public licenses or any other arrangements,
understandings, or agreements concerning use of licensed material. For
the avoidance of doubt, this paragraph does not form part of the
public licenses.

Creative Commons may be contacted at creativecommons.org."""
