package com.ap.background.recorder.ui.screens

import android.content.Intent
import android.os.Build
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.scale
import androidx.compose.ui.viewinterop.AndroidView
import androidx.camera.view.PreviewView
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import android.util.Size
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.core.content.FileProvider
import com.ap.background.recorder.R
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ap.background.recorder.data.RecorderPreferences
import com.ap.background.recorder.services.RecordingService
import com.ap.background.recorder.services.FileExportService
import com.ap.background.recorder.utils.FileManager
import com.ap.background.recorder.utils.PermissionManager
import com.ap.background.recorder.utils.RecordingStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    prefs: RecorderPreferences,
    onNavigateToSettings: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val fileManager = remember { FileManager(context) }
    val permissionManager = remember { PermissionManager(context) }
    
    val isRecording by RecordingStatus.isRecording.collectAsStateWithLifecycle()
    var isRefreshing by remember { mutableStateOf(false) }

    val recordingMode by prefs.recordingModeFlow.collectAsStateWithLifecycle(initialValue = "VIDEO")
    val cameraSelection by prefs.cameraSelectionFlow.collectAsStateWithLifecycle(initialValue = "PRIMARY")
    val zoomLevel by prefs.zoomLevelFlow.collectAsStateWithLifecycle(initialValue = 1f)
    val focusMode by prefs.focusModeFlow.collectAsStateWithLifecycle(initialValue = "AUTO")
    val photoInterval by prefs.photoIntervalFlow.collectAsStateWithLifecycle(initialValue = 5)
    val showPreview by prefs.showPreviewFlow.collectAsStateWithLifecycle(initialValue = false)
    
    var recordings by remember { mutableStateOf(listOf<File>()) }
    var fileToDelete by remember { mutableStateOf<File?>(null) }
    var showHelpDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        // Query current recording status from service
        val intent = Intent(context, RecordingService::class.java).apply {
            action = RecordingStatus.ACTION_RECORDING_STATUS_QUERY
        }
        context.startService(intent)
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        recordings = fileManager.getAllRecordings()
    }

    if (showHelpDialog) {
        AlertDialog(
            onDismissRequest = { showHelpDialog = false },
            title = { Text("How to Use Background Recorder") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    HelpItem("🚀 Getting Started", "Use the 'Start' and 'Stop' buttons in the Quick Controls card to manage recordings directly from the app.")
                    
                    HelpItem("🔇 Stealth Recording", "Go to Device Settings > Apps > Default Apps > Digital Assistant and choose this app. You can then trigger recording silently by long-pressing the power or home button.")
                    
                    HelpItem("⚡ Quick Access", "Pull down your notification panel and add the 'Background Recorder' tile. This allows you to start/stop recording instantly from any screen.")
                    
                    HelpItem("📳 Shake Trigger", "Enable 'Shake to Trigger' in Settings. You can adjust the intensity and even set it to stop recording on a second shake.")
                    
                    HelpItem("📩 SMS Trigger", "Control the recorder remotely by sending an SMS with your custom text or generated 64-digit code.")
                    
                    HelpItem("⏰ Time-Based Trigger", "Schedule one-time or duration-based recordings. Set multiple triggers to automate your capturing needs.")
                    
                    HelpItem("🔋 Battery Optimization", "To ensure triggers work reliably in the background, click 'Disable Optimization' below to give the app unrestricted battery access.")
                    
                    Button(
                        onClick = { 
                            (context as? androidx.fragment.app.FragmentActivity)?.let { 
                                permissionManager.requestIgnoreBatteryOptimizations(it) 
                            }
                        },
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        enabled = !permissionManager.isBatteryOptimizationIgnored()
                    ) {
                        Text(if (permissionManager.isBatteryOptimizationIgnored()) "Battery Optimized" else "Disable Battery Optimization")
                    }

                    HelpItem("🚀 Background Start", "Android 14+ requires 'Display over other apps' permission to start recording from SMS or Shake while the app is in the background.")

                    Button(
                        onClick = { 
                            (context as? androidx.fragment.app.FragmentActivity)?.let { 
                                permissionManager.requestOverlayPermission(it) 
                            }
                        },
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        enabled = !permissionManager.canDrawOverlays()
                    ) {
                        Text(if (permissionManager.canDrawOverlays()) "Background Start Ready" else "Enable Background Start")
                    }
                    
                    HelpItem("📁 Private Storage", "Recordings are saved in private app storage. Use the 'Save to Downloads' option in the file menu to export them to your public gallery.")
                }
            },
            confirmButton = { TextButton(onClick = { showHelpDialog = false }) { Text("Got it") } }
        )
    }

    if (fileToDelete != null) {
        AlertDialog(
            onDismissRequest = { fileToDelete = null },
            title = { Text("Confirm Delete") },
            text = { Text("Are you sure you want to delete ${fileToDelete?.name}?") },
            confirmButton = {
                TextButton(onClick = {
                    fileToDelete?.delete()
                    recordings = fileManager.getAllRecordings()
                    fileToDelete = null
                }) { Text("Delete", color = Color.Red) }
            },
            dismissButton = {
                TextButton(onClick = { fileToDelete = null }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { 
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painter = painterResource(id = R.mipmap.ic_launcher_foreground),
                            contentDescription = null,
                            modifier = Modifier.size(32.dp),
                            tint = Color.Unspecified
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Background Recorder")
                    }
                },
                actions = {
                    IconButton(onClick = { showHelpDialog = true }) {
                        Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = "Help")
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { paddingValues ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                scope.launch {
                    isRefreshing = true
                    recordings = fileManager.getAllRecordings()
                    delay(800)
                    isRefreshing = false
                }
            },
            modifier = Modifier.padding(paddingValues).fillMaxSize()
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    // Control Panel
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateContentSize(),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Quick Controls", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                ControlChip("Mode: $recordingMode", Icons.Default.Videocam) {
                                     val next = when(recordingMode) {
                                         "VIDEO" -> "PHOTO"
                                         "PHOTO" -> "AUDIO"
                                         else -> "VIDEO"
                                     }
                                     scope.launch { prefs.setRecordingMode(next) }
                                }
                                
                                ControlChip("Cam: $cameraSelection", Icons.Default.Camera) {
                                    val next = when(cameraSelection) {
                                        "PRIMARY" -> "SECONDARY"
                                        "SECONDARY" -> "FRONT"
                                        else -> "PRIMARY"
                                    }
                                    scope.launch { prefs.setCameraSelection(next) }
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                ControlChip("Zoom: ${String.format(Locale.getDefault(), "%.1f", zoomLevel)}x", Icons.Default.ZoomIn) {
                                    val next = if (zoomLevel >= 10f) 1f else zoomLevel + 1.0f
                                    scope.launch { prefs.setZoomLevel(next) }
                                }
                                ControlChip("Focus: $focusMode", Icons.Default.FilterCenterFocus) {
                                    val next = if (focusMode == "AUTO") "MANUAL" else "AUTO"
                                    scope.launch { prefs.setFocusMode(next) }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Show Preview", style = MaterialTheme.typography.bodyMedium)
                                }
                                Switch(
                                    checked = showPreview,
                                    onCheckedChange = { scope.launch { prefs.setShowPreview(it) } },
                                    modifier = Modifier.scale(0.8f)
                                )
                            }

                            AnimatedVisibility(visible = recordingMode == "PHOTO") {
                                Column {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        "Photo Interval: ${photoInterval}s", 
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.padding(start = 8.dp)
                                    )
                                    Slider(
                                        value = photoInterval.toFloat(),
                                        onValueChange = { scope.launch { prefs.setPhotoInterval(it.toInt()) } },
                                        valueRange = 1f..60f,
                                        steps = 59,
                                        modifier = Modifier.padding(horizontal = 8.dp)
                                    )
                                }
                            }
                            
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        val action = when (recordingMode) {
                                            "VIDEO" -> RecordingService.ACTION_START_VIDEO
                                            "PHOTO" -> RecordingService.ACTION_START_PHOTO
                                            "AUDIO" -> RecordingService.ACTION_START_AUDIO
                                            else -> RecordingService.ACTION_START_VIDEO
                                        }
                                        val intent = Intent(context, RecordingService::class.java).apply {
                                            this.action = action
                                        }
                                        context.startForegroundService(intent)
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                    enabled = !isRecording
                                ) {
                                    Icon(Icons.Default.RadioButtonChecked, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Start")
                                }

                                Button(
                                    onClick = {
                                        val intent = Intent(context, RecordingService::class.java).apply {
                                            action = RecordingService.ACTION_STOP
                                        }
                                        context.startService(intent)
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                    enabled = isRecording
                                ) {
                                    Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Stop")
                                }
                            }
                        }
                    }
                }

                if (showPreview) {
                    item {
                        CameraPreviewSection(prefs)
                    }
                }

                item {
                    Text(
                        "Recent Recordings",
                        modifier = Modifier.padding(vertical = 8.dp),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (recordings.isEmpty()) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                            Text("No recordings found", color = Color.Gray)
                        }
                    }
                } else {
                    items(recordings, key = { it.absolutePath }) { file ->
                        Box(modifier = Modifier.animateItem()) {
                            RecordingItem(
                                file = file, 
                                onDelete = { fileToDelete = it },
                                onSave = {
                                    val feature = when {
                                        file.name.contains("VID") -> "Video"
                                        file.name.contains("AUD") -> "Audio"
                                        else -> "Photo"
                                    }
                                    val serviceIntent = Intent(context, FileExportService::class.java).apply {
                                        putExtra("file_path", file.absolutePath)
                                        putExtra("file_type", feature)
                                    }
                                    context.startForegroundService(serviceIntent)
                                    scope.launch {
                                        snackbarHostState.showSnackbar("Export started in background...")
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CameraPreviewSection(prefs: RecorderPreferences) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraSelection by prefs.cameraSelectionFlow.collectAsStateWithLifecycle(initialValue = "PRIMARY")
    val aspectRatio by prefs.aspectRatioFlow.collectAsStateWithLifecycle(initialValue = "16:9")
    val frontAspectRatio by prefs.frontAspectRatioFlow.collectAsStateWithLifecycle(initialValue = "16:9")
    val orientation by prefs.orientationFlow.collectAsStateWithLifecycle(initialValue = "AUTO")
    val frontOrientation by prefs.frontOrientationFlow.collectAsStateWithLifecycle(initialValue = "AUTO")
    val zoomLevel by prefs.zoomLevelFlow.collectAsStateWithLifecycle(initialValue = 1f)

    val currentAspectRatio = if (cameraSelection == "FRONT") frontAspectRatio else aspectRatio
    val currentOrientation = if (cameraSelection == "FRONT") frontOrientation else orientation

    val targetAspectRatio = when (currentAspectRatio) {
        "4:3" -> androidx.camera.core.AspectRatio.RATIO_4_3
        "16:9" -> androidx.camera.core.AspectRatio.RATIO_16_9
        "1:1" -> androidx.camera.core.AspectRatio.RATIO_4_3 
        else -> androidx.camera.core.AspectRatio.RATIO_16_9
    }

    val boxModifier = if (currentOrientation == "LANDSCAPE") {
        Modifier
            .fillMaxWidth()
            .aspectRatio(if (targetAspectRatio == androidx.camera.core.AspectRatio.RATIO_4_3) 4/3f else 16/9f)
    } else {
        Modifier
            .fillMaxWidth()
            .aspectRatio(if (targetAspectRatio == androidx.camera.core.AspectRatio.RATIO_4_3) 3/4f else 9/16f)
            .heightIn(max = 400.dp)
    }

    Card(
        modifier = boxModifier
            .clip(RoundedCornerShape(12.dp)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }
            },
            modifier = Modifier.fillMaxSize(),
            update = { previewView ->
                val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    
                    val targetRotation = when (currentOrientation) {
                        "PORTRAIT" -> android.view.Surface.ROTATION_0
                        "LANDSCAPE" -> android.view.Surface.ROTATION_90
                        else -> android.view.Surface.ROTATION_0
                    }

                    val preview = Preview.Builder()
                        .setTargetAspectRatio(targetAspectRatio)
                        .setTargetRotation(targetRotation)
                        .build()
                    
                    val cameraSelector = when (cameraSelection) {
                        "FRONT" -> CameraSelector.DEFAULT_FRONT_CAMERA
                        "SECONDARY" -> {
                            CameraSelector.Builder()
                                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                                .build()
                        }
                        else -> CameraSelector.DEFAULT_BACK_CAMERA
                    }

                    try {
                        cameraProvider.unbindAll()
                        val camera = cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            preview
                        )
                        camera.cameraControl.setZoomRatio(zoomLevel)
                        preview.setSurfaceProvider(previewView.surfaceProvider)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }, ContextCompat.getMainExecutor(context))
            }
        )
    }
}

@Composable
fun HelpItem(title: String, description: String) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
        Text(description, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun ControlChip(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    FilterChip(
        selected = true,
        onClick = onClick,
        label = { Text(label, fontSize = 12.sp) },
        leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp)) }
    )
}

@Composable
fun RecordingItem(file: File, onDelete: (File) -> Unit, onSave: () -> Unit) {
    val context = LocalContext.current
    val dateFormat = remember { SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()) }
    var showMenu by remember { mutableStateOf(false) }
    
    ListItem(
        headlineContent = { Text(file.name) },
        supportingContent = { 
            val size = if (file.length() >= 1024 * 1024) String.format("%.1f MB", file.length() / (1024.0 * 1024.0)) else String.format("%.1f KB", file.length() / 1024.0)
            Text("$size • ${dateFormat.format(java.util.Date(file.lastModified()))}") 
        },
        leadingContent = {
            val icon = when {
                file.name.contains("VID") -> Icons.Default.Movie
                file.name.contains("AUD") -> Icons.Default.Mic
                else -> Icons.Default.Image
            }
            Icon(icon, contentDescription = null)
        },
        trailingContent = {
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "More")
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("Play/Open") },
                        onClick = {
                            showMenu = false
                            openFile(context, file)
                        },
                        leadingIcon = { Icon(Icons.Default.PlayArrow, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text("Save to Downloads") },
                        onClick = {
                            showMenu = false
                            onSave()
                        },
                        leadingIcon = { Icon(Icons.Default.Save, contentDescription = null) }
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("Delete", color = Color.Red) },
                        onClick = {
                            showMenu = false
                            onDelete(file)
                        },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Red) }
                    )
                }
            }
        },
        modifier = Modifier.clickable { openFile(context, file) }
    )
}

private fun openFile(context: android.content.Context, file: File) {
    try {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, getMimeType(file))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        // Handle error
    }
}

private fun getMimeType(file: File): String {
    return when (file.extension.lowercase()) {
        "mp4" -> "video/mp4"
        "m4a" -> "audio/mp4"
        "jpg", "jpeg" -> "image/jpeg"
        else -> "*/*"
    }
}