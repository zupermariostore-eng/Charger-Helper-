package com.example

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import com.example.data.database.CachedVoiceCommand
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import androidx.compose.ui.layout.ContentScale
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.MapLandmark
import com.example.viewmodel.VoiceIntelligenceViewModel
import com.example.viewmodel.VoiceState
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import java.net.URLEncoder
import java.util.*

class MainActivity : ComponentActivity() {

    private val viewModel: VoiceIntelligenceViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = Color(0xFF0C101B)
                ) { innerPadding ->
                    DeepalCockpitScreen(
                        viewModel = viewModel,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DeepalCockpitScreen(
    viewModel: VoiceIntelligenceViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    // Voice & System States
    val voiceState by viewModel.voiceState.collectAsState()
    val isRecording by viewModel.isRecording.collectAsState()
    val isDemoMode by viewModel.isDemoMode.collectAsState()
    val isKhmerTtsSupported by viewModel.isKhmerTtsSupported.collectAsState()
    val voiceLanguagePref by viewModel.voiceLanguagePref.collectAsState()
    val ttsConfirmationMode by viewModel.ttsConfirmationMode.collectAsState()
    val cachedCommands by viewModel.cachedCommands.collectAsState()

    // Vehicle Telemetry
    val speed by viewModel.speed.collectAsState()
    val batteryRef by viewModel.batteryPercentage.collectAsState()
    val rangeRef by viewModel.estimatedRangeKm.collectAsState()
    val isSimulatingDrive by viewModel.isSimulatingDrive.collectAsState()

    // Map & Nav States
    val selectedLandmark by viewModel.selectedLandmark.collectAsState()
    val isNavigating by viewModel.isNavigating.collectAsState()

    // Handle audio authorization
    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val requestPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasMicPermission = isGranted
        if (isGranted) {
            viewModel.startMicRecording()
        } else {
            Toast.makeText(context, "Microphone permission is required to record real voice commands", Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0C101B))
            .padding(16.dp)
    ) {
        // --- 1. SYSTEM TOP STATUS HEADER ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "DEEPAL S05",
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.SansSerif
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Box(
                        modifier = Modifier
                            .background(Color(0xFF00B0FF), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "🇨🇳 Chinese Spec • GB/T Only",
                            color = Color.Black,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
                Text(
                    text = "Phnom Penh, Cambodia",
                    color = Color(0xFF8E95A5),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Gear Position Simulation
            Row(
                modifier = Modifier
                    .background(Color(0xFF161C2C), RoundedCornerShape(16.dp))
                    .border(1.dp, Color(0xFF2E3B5E), RoundedCornerShape(16.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                listOf("P", "R", "N", "D").forEach { gear ->
                    val isActive = if (isSimulatingDrive) gear == "D" else gear == "P"
                    Text(
                        text = gear,
                        color = if (isActive) Color(0xFF00B0FF) else Color(0x55FFFFFF),
                        fontWeight = if (isActive) FontWeight.Black else FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            }
        }

        // --- AMBIENT VOICE SENSOR AUDIO INTELLIGENCE FEEDBACK ---
        DeepalVoiceFeedbackComponent(
            voiceState = voiceState,
            isRecording = isRecording,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
        )

        // --- QUICK ACTIONS BAR ---
        DriverQuickActionsRow(
            viewModel = viewModel,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 14.dp)
        )

        // --- 2. MULTI-COLUMN CONSOLE PANELS (Highly Responsive Layout) ---
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            val isWidescreenLandscape = maxWidth >= 950.dp && maxHeight >= 480.dp
            val isMediumTablet = maxWidth >= 650.dp && maxWidth < 950.dp && maxHeight >= 480.dp

            if (isWidescreenLandscape) {
                // Large Cockpit: Widescreen Landscape EV Dashboard (3 columns)
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Column 1 (Left): Driving instrumentation & command inputs (closest to driver's view)
                    Column(
                        modifier = Modifier
                            .weight(1.1f)
                            .fillMaxHeight()
                    ) {
                        TelemetryPanelCard(
                            speed = speed,
                            batteryRef = batteryRef,
                            rangeRef = rangeRef,
                            isSimulatingDrive = isSimulatingDrive,
                            onCheckedChange = { viewModel.setDriveSimulationActive(it) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1.1f)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        PresetCommandsPanelCard(
                            voiceState = voiceState,
                            onPresetClick = { type, query ->
                                viewModel.sendPresetCommand(type, query)
                            },
                            cachedCommands = cachedCommands,
                            onCachedCommandClick = { command ->
                                viewModel.executeCachedCommand(command)
                            },
                            onDeleteCachedCommand = { q ->
                                viewModel.deleteCachedCommand(q)
                            },
                            onClearCache = {
                                viewModel.clearVoiceCache()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        )
                    }

                    // Column 2 (Center): Primary dynamic navigation map (takes visual center stage)
                    Column(
                        modifier = Modifier
                            .weight(1.5f)
                            .fillMaxHeight()
                    ) {
                        PhnomPenhVirtualMapCard(
                            landmarks = viewModel.landmarks,
                            selectedLandmark = selectedLandmark,
                            isNavigating = isNavigating,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    // Column 3 (Right): Voice Intelligent System & Advanced Session JSON Logs
                    Column(
                        modifier = Modifier
                            .weight(1.2f)
                            .fillMaxHeight()
                    ) {
                        VoiceIntelligenceCard(
                            voiceState = voiceState,
                            isRecording = isRecording,
                            isDemoMode = isDemoMode,
                            hasMicPermission = hasMicPermission,
                            isKhmerTtsSupported = isKhmerTtsSupported,
                            voiceLanguagePref = voiceLanguagePref,
                            onVoiceLanguagePrefChange = { viewModel.setVoiceLanguagePref(it) },
                            ttsConfirmationMode = ttsConfirmationMode,
                            onTtsConfirmationModeChange = { viewModel.setTtsConfirmationMode(it) },
                            onReplayVoice = {
                                val successResult = (voiceState as? VoiceState.Success)?.result
                                if (successResult != null) {
                                    viewModel.speak(successResult)
                                }
                            },
                            onStartRecording = {
                                if (hasMicPermission) {
                                    viewModel.startMicRecording()
                                } else {
                                    requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            },
                            onStopRecording = {
                                viewModel.stopMicRecording()
                            },
                            onDemoModeToggle = { viewModel.setDemoModeActive(!isDemoMode) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1.4f)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        CognitiveConsoleCard(
                            voiceState = voiceState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(0.9f)
                        )
                    }
                }
            } else if (isMediumTablet) {
                // Medium Landscape Tablet / Large Portait Grid (2 columns + bottom console)
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        ) {
                            TelemetryPanelCard(
                                speed = speed,
                                batteryRef = batteryRef,
                                rangeRef = rangeRef,
                                isSimulatingDrive = isSimulatingDrive,
                                onCheckedChange = { viewModel.setDriveSimulationActive(it) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            VoiceIntelligenceCard(
                                voiceState = voiceState,
                                isRecording = isRecording,
                                isDemoMode = isDemoMode,
                                hasMicPermission = hasMicPermission,
                                isKhmerTtsSupported = isKhmerTtsSupported,
                                voiceLanguagePref = voiceLanguagePref,
                                onVoiceLanguagePrefChange = { viewModel.setVoiceLanguagePref(it) },
                                ttsConfirmationMode = ttsConfirmationMode,
                                onTtsConfirmationModeChange = { viewModel.setTtsConfirmationMode(it) },
                                onReplayVoice = {
                                    val successResult = (voiceState as? VoiceState.Success)?.result
                                    if (successResult != null) {
                                        viewModel.speak(successResult)
                                    }
                                },
                                onStartRecording = {
                                    if (hasMicPermission) {
                                        viewModel.startMicRecording()
                                    } else {
                                        requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                },
                                onStopRecording = {
                                    viewModel.stopMicRecording()
                                },
                                onDemoModeToggle = { viewModel.setDemoModeActive(!isDemoMode) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1.8f)
                            )
                        }

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        ) {
                            PhnomPenhVirtualMapCard(
                                landmarks = viewModel.landmarks,
                                selectedLandmark = selectedLandmark,
                                isNavigating = isNavigating,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1.4f)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            PresetCommandsPanelCard(
                                voiceState = voiceState,
                                onPresetClick = { type, query ->
                                    viewModel.sendPresetCommand(type, query)
                                },
                                cachedCommands = cachedCommands,
                                onCachedCommandClick = { command ->
                                    viewModel.executeCachedCommand(command)
                                },
                                onDeleteCachedCommand = { q ->
                                    viewModel.deleteCachedCommand(q)
                                },
                                onClearCache = {
                                    viewModel.clearVoiceCache()
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    CognitiveConsoleCard(
                        voiceState = voiceState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp)
                    )
                }
            } else {
                // Compact Screen Layout / Phones (Clean single vertical scrollable Column to avoid clipping on smaller ports)
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    TelemetryPanelCard(
                        speed = speed,
                        batteryRef = batteryRef,
                        rangeRef = rangeRef,
                        isSimulatingDrive = isSimulatingDrive,
                        onCheckedChange = { viewModel.setDriveSimulationActive(it) },
                        modifier = Modifier.fillMaxWidth()
                    )

                    VoiceIntelligenceCard(
                        voiceState = voiceState,
                        isRecording = isRecording,
                        isDemoMode = isDemoMode,
                        hasMicPermission = hasMicPermission,
                        isKhmerTtsSupported = isKhmerTtsSupported,
                        voiceLanguagePref = voiceLanguagePref,
                        onVoiceLanguagePrefChange = { viewModel.setVoiceLanguagePref(it) },
                        ttsConfirmationMode = ttsConfirmationMode,
                        onTtsConfirmationModeChange = { viewModel.setTtsConfirmationMode(it) },
                        onReplayVoice = {
                            val successResult = (voiceState as? VoiceState.Success)?.result
                            if (successResult != null) {
                                viewModel.speak(successResult)
                            }
                        },
                        onStartRecording = {
                            if (hasMicPermission) {
                                viewModel.startMicRecording()
                            } else {
                                requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        },
                        onStopRecording = {
                            viewModel.stopMicRecording()
                        },
                        onDemoModeToggle = { viewModel.setDemoModeActive(!isDemoMode) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(420.dp)
                    )

                    PhnomPenhVirtualMapCard(
                        landmarks = viewModel.landmarks,
                        selectedLandmark = selectedLandmark,
                        isNavigating = isNavigating,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(350.dp)
                    )

                    PresetCommandsPanelCard(
                        voiceState = voiceState,
                        onPresetClick = { type, query ->
                            viewModel.sendPresetCommand(type, query)
                        },
                        cachedCommands = cachedCommands,
                        onCachedCommandClick = { command ->
                            viewModel.executeCachedCommand(command)
                        },
                        onDeleteCachedCommand = { q ->
                            viewModel.deleteCachedCommand(q)
                        },
                        onClearCache = {
                            viewModel.clearVoiceCache()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp)
                    )

                    CognitiveConsoleCard(
                        voiceState = voiceState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun CognitiveConsoleCard(
    voiceState: VoiceState,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color(0xFF161C2C)),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.5.dp, Color(0xFF2E3B5E))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "VEHICLE INTELLIGENCE / COGNITIVE LOGS",
                    color = Color(0xFF00B0FF),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp
                )

                // Actions
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .background(Color(0xFF1F263E), RoundedCornerShape(6.dp))
                            .clickable {
                                val currentResult = (voiceState as? VoiceState.Success)?.result
                                if (currentResult != null) {
                                    val mockJson = """
                                    {
                                      "intent": "${currentResult.intent}",
                                      "charger_type_required": ${if (currentResult.chargerTypeRequired != null) "\"${currentResult.chargerTypeRequired}\"" else "null"},
                                      "destination_name": ${if (currentResult.destinationName != null) "\"${currentResult.destinationName}\"" else "null"},
                                      "spoken_response_khmer": "${currentResult.spokenResponseKhmer}",
                                      "transcribed_khmer_text": "${currentResult.transcribedKhmerText}"
                                    }
                                    """.trimIndent()
                                    clipboardManager.setText(AnnotatedString(mockJson))
                                    Toast.makeText(context, "Telemetry JSON copied!", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "No successful voice result logged yet", Toast.LENGTH_SHORT).show()
                                }
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "COPY JSON",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF0C101B), RoundedCornerShape(10.dp))
                    .border(1.dp, Color(0xFF2E3B5E), RoundedCornerShape(10.dp))
                    .padding(8.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                val activeResult = (voiceState as? VoiceState.Success)?.result
                if (activeResult != null) {
                    Text(
                        text = """
                        {
                          "intent": "${activeResult.intent}",
                          "charger_type_required": ${if (activeResult.chargerTypeRequired != null) "\"${activeResult.chargerTypeRequired}\"" else "null"},
                          "destination_name": ${if (activeResult.destinationName != null) "\"${activeResult.destinationName}\"" else "null"},
                          "spoken_response_khmer": "${activeResult.spokenResponseKhmer}",
                          "transcribed_khmer_text": "${activeResult.transcribedKhmerText}"
                        }
                        """.trimIndent(),
                        color = Color(0xFF00FF87),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        modifier = Modifier.testTag("copied_json_console")
                    )
                } else {
                    Text(
                        text = ">> Cockpit State: STANDBY. Tap the Mic orb or shift gears to trigger Khmer voice intelligence parameters...",
                        color = Color(0xFF8E95A5),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp
                    )
                }
            }
        }
    }
}

@Composable
fun TelemetryPanelCard(
    speed: Int,
    batteryRef: Int,
    rangeRef: Int,
    isSimulatingDrive: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color(0xFF161C2C)),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.5.dp, Color(0xFF2E3B5E))
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Live Instrument Panel",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )

                // Large Driver Cockpit Gear Selector Button (replaces standard small Switch for standard Car UI look)
                Button(
                    onClick = { onCheckedChange(!isSimulatingDrive) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isSimulatingDrive) Color(0xFFBA1A1A) else Color(0xFF0061A4)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                    modifier = Modifier
                        .height(44.dp)
                        .testTag("driver_simulation_switch")
                ) {
                    Icon(
                        imageVector = if (isSimulatingDrive) Icons.Default.Close else Icons.Default.PlayArrow,
                        contentDescription = "Simulate Drive Control Trigger",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isSimulatingDrive) "SHIFT TO PARK [P]" else "SHIFT TO DRIVE [D]",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Speed Info Block
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .background(Color(0xFF1F263E), RoundedCornerShape(16.dp))
                        .padding(vertical = 12.dp, horizontal = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("CURRENT SPEED", color = Color(0xFF80A2FF), fontSize = 10.sp, fontWeight = FontWeight.Black)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = "$speed",
                            color = Color.White,
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.SansSerif
                        )
                        Text(
                            " km/h",
                            color = Color(0xFF8E95A5),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                    }
                }

                // Battery Info Block
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .background(Color(0xFF1F263E), RoundedCornerShape(16.dp))
                        .padding(vertical = 12.dp, horizontal = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("BATTERY STATUS", color = Color(0xFF80A2FF), fontSize = 10.sp, fontWeight = FontWeight.Black)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "Battery charge icon",
                            tint = if (batteryRef < 20) Color(0xFFFF5252) else Color(0xFF00FF87),
                            modifier = Modifier.size(26.dp).padding(bottom = 2.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "$batteryRef",
                            color = if (batteryRef < 20) Color(0xFFFF5252) else Color.White,
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.SansSerif
                        )
                        Text(
                            " %",
                            color = Color(0xFF8E95A5),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                    }
                }

                // Range Info Block
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .background(Color(0xFF1F263E), RoundedCornerShape(16.dp))
                        .padding(vertical = 12.dp, horizontal = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("ESTIMATED RANGE", color = Color(0xFF80A2FF), fontSize = 10.sp, fontWeight = FontWeight.Black)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = "$rangeRef",
                            color = Color(0xFF00B0FF),
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.SansSerif
                        )
                        Text(
                            " km",
                            color = Color(0xFF8E95A5),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                    }
                }
            }

            if (batteryRef < 30) {
                Spacer(modifier = Modifier.height(14.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF5C1D1D), RoundedCornerShape(12.dp))
                        .border(1.5.dp, Color(0xFFFF5252), RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Warning Low Battery icon",
                            tint = Color(0xFFFF5252),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "ថ្មឡានជិតអស់ហើយ! ស្នើសុំសាកថ្មជាមួយប្រភេទ GB/T។",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun VoiceIntelligenceCard(
    voiceState: VoiceState,
    isRecording: Boolean,
    isDemoMode: Boolean,
    hasMicPermission: Boolean,
    isKhmerTtsSupported: Boolean,
    voiceLanguagePref: String,
    onVoiceLanguagePrefChange: (String) -> Unit,
    ttsConfirmationMode: String,
    onTtsConfirmationModeChange: (String) -> Unit,
    onReplayVoice: () -> Unit,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onDemoModeToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isHolding by remember { mutableStateOf(false) }
    var pressStartTime by remember { mutableStateOf(0L) }

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color(0xFF161C2C)),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.5.dp, Color(0xFF2E3B5E))
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Deepal Voice Assistant",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )

                // Large Demo Mode toggle
                Box(
                    modifier = Modifier
                        .background(
                            if (isDemoMode) Color(0xFF1F263E) else Color(0x3300B0FF),
                            RoundedCornerShape(8.dp)
                        )
                        .border(
                            1.dp,
                            if (isDemoMode) Color(0xFF2E3B5E) else Color(0xFF00B0FF),
                            RoundedCornerShape(8.dp)
                        )
                        .clickable { onDemoModeToggle() }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = if (isDemoMode) "Local Demo Engine" else "Gemini AI Live 🌐",
                        color = if (isDemoMode) Color(0xFF8E95A5) else Color(0xFF00B0FF),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // MASSIVE Driver-Optimized Pulsing Mic Orb Frame
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(120.dp)
            ) {
                // Infinite Pulsing Ripple Effects using InfiniteTransition
                val infiniteTransition = rememberInfiniteTransition(label = "speech_ripple")
                val rippleScale1 by infiniteTransition.animateFloat(
                    initialValue = 1f,
                    targetValue = 1.7f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1200, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "scale1"
                )
                val rippleAlpha1 by infiniteTransition.animateFloat(
                    initialValue = 0.6f,
                    targetValue = 0f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1200, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "alpha1"
                )

                val rippleScale2 by infiniteTransition.animateFloat(
                    initialValue = 1f,
                    targetValue = 2.1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1800, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "scale2"
                )
                val rippleAlpha2 by infiniteTransition.animateFloat(
                    initialValue = 0.4f,
                    targetValue = 0f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1800, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "alpha2"
                )

                if (isRecording || voiceState is VoiceState.Processing) {
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .scale(rippleScale1)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(Color(0x5600B0FF), Color.Transparent)
                                ),
                                shape = CircleShape
                            )
                            .scale(rippleAlpha1)
                    )
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .scale(rippleScale2)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(Color(0x3800B0FF), Color.Transparent)
                                ),
                                shape = CircleShape
                            )
                            .scale(rippleAlpha2)
                    )
                }

                // GIGANTIC SLAP-TO-CLICK MICROPHONE KEY (Huge 92dp circle with PTT gestures!)
                Box(
                    modifier = Modifier
                        .size(92.dp)
                        .testTag("microphone_button")
                        .pointerInput(hasMicPermission, isRecording, voiceState) {
                            detectTapGestures(
                                onPress = {
                                    val wasRecordingBeforePress = isRecording
                                    pressStartTime = System.currentTimeMillis()
                                    isHolding = true

                                    if (voiceState !is VoiceState.Processing) {
                                        if (!wasRecordingBeforePress) {
                                            onStartRecording()
                                        }
                                    }

                                    val isReleased = try {
                                        awaitRelease()
                                        true
                                    } catch (e: Exception) {
                                        false
                                    }

                                    isHolding = false
                                    val duration = System.currentTimeMillis() - pressStartTime
                                    if (isReleased && duration > 400) {
                                        // PTT Held release -> stop recording immediately on let go
                                        if (isRecording || !wasRecordingBeforePress) {
                                            onStopRecording()
                                        }
                                    }
                                },
                                onTap = {
                                    if (isRecording) {
                                        onStopRecording()
                                    }
                                }
                            )
                        }
                        .clip(CircleShape)
                        .background(
                            brush = Brush.radialGradient(
                                colors = when {
                                    isRecording -> listOf(Color(0xFFFF3B30), Color(0xFFC30010))
                                    voiceState is VoiceState.Processing -> listOf(Color(0xFF00B0FF), Color(0xFF0061A4))
                                    else -> listOf(Color(0xFF1F263E), Color(0xFF121824))
                                }
                            )
                        )
                        .border(
                            BorderStroke(
                                if (isRecording) 3.dp else 2.dp,
                                if (isRecording) Color(0xFFFF5252) else Color(0xFF00B0FF)
                            ),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isRecording) Icons.Default.Close else Icons.Default.Mic,
                        contentDescription = "Toggle Recording Microphone",
                        tint = Color.White,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Highly Visible Status HUD Subtitle
            Text(
                text = when (voiceState) {
                    is VoiceState.Idle -> {
                        if (isHolding) "🎙️ PTT LISTENING..." else "TAP TO TOGGLE • HOLD TO SPEAK"
                    }
                    is VoiceState.Recording -> {
                        if (isHolding) "🎙️ HOLDING... RELEASE TO SEND" else "🎙️ RECORDING... TAP TO TRANSMIT"
                    }
                    is VoiceState.Processing -> "🤖 DECODING DRIVER COMMANDS..."
                    is VoiceState.Success -> "⚡ COGNITIVE MATCH SUCCESSFUL"
                    is VoiceState.Error -> "🚨 CRITICAL DIALECT MISMATCH"
                },
                color = when (voiceState) {
                    is VoiceState.Recording -> Color(0xFFFF5252)
                    is VoiceState.Processing -> Color(0xFF00B0FF)
                    is VoiceState.Success -> Color(0xFF00FF87)
                    is VoiceState.Error -> Color(0xFFFF5252)
                    else -> Color(0xFF8E95A5)
                },
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier.testTag("microphone_status")
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Huge In-Car Language Selector segment selector spanning full width
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "AI SPEECH READOUT FALLBACK",
                        color = Color(0xFF8E95A5),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val opts = listOf("AUTO", "KHMER", "ENGLISH")
                    opts.forEach { opt ->
                        val isSel = voiceLanguagePref == opt
                        val label = when (opt) {
                            "AUTO" -> "Auto 🌐"
                            "KHMER" -> "Khmer 🇰🇭"
                            else -> "Eng 🇬🇧"
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .background(
                                    if (isSel) Color(0xFF0061A4) else Color(0xFF1F263E),
                                    RoundedCornerShape(10.dp)
                                )
                                .border(
                                    1.dp,
                                    if (isSel) Color(0xFF00B0FF) else Color(0xFF2E3B5E),
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable { onVoiceLanguagePrefChange(opt) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            if (!isKhmerTtsSupported && voiceLanguagePref == "AUTO") {
                Text(
                    text = "System lacks Khmer speech engine. Auto-falling back to clear English voice readout.",
                    color = Color(0xFF00B0FF),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // TTS Feedback Mode Selector
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "TTS VOICE FEEDBACK ALERTS",
                        color = Color(0xFF8E95A5),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val modes = listOf("FULL_VOICE", "CHIME_ONLY", "DISABLED")
                    modes.forEach { mode ->
                        val isSel = ttsConfirmationMode == mode
                        val label = when (mode) {
                            "FULL_VOICE" -> "Voice + Chime 🔊"
                            "CHIME_ONLY" -> "Chime Only 🔔"
                            else -> "Mute 🔇"
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .background(
                                    if (isSel) Color(0xFF0061A4) else Color(0xFF1F263E),
                                    RoundedCornerShape(10.dp)
                                )
                                .border(
                                    1.dp,
                                    if (isSel) Color(0xFF00B0FF) else Color(0xFF2E3B5E),
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable { onTtsConfirmationModeChange(mode) }
                                .testTag("tts_mode_$mode"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Premium Slate Cockpit HUD transcripts display
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color(0xFF121824), RoundedCornerShape(20.dp))
                    .border(1.5.dp, Color(0xFF2E3B5E), RoundedCornerShape(20.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                when (val state = voiceState) {
                    is VoiceState.Success -> {
                        Text(
                            text = "“ ${state.result.transcribedKhmerText} ”",
                            color = Color(0xFF00B0FF),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(bottom = 6.dp).testTag("transcription_result")
                        )
                        HorizontalDivider(color = Color(0xFF2E3B5E), thickness = 1.dp)
                        
                        Text(
                            text = state.result.spokenResponseKhmer,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 8.dp).testTag("spoken_text_result")
                        )

                        state.result.spokenResponseEnglish?.let { eng ->
                            Text(
                                text = "EN: $eng",
                                color = Color(0xFF8E95A5),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Large Replay Response Capsule Button
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .background(Color(0xFF1F263E), RoundedCornerShape(12.dp))
                                .border(1.dp, Color(0xFF00B0FF), RoundedCornerShape(12.dp))
                                .clickable { onReplayVoice() }
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Replay Voice Command Response",
                                tint = Color(0xFF00B0FF),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "REPLAY AUDIO READOUT",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                    is VoiceState.Error -> {
                        Text(
                            text = state.message,
                            color = Color(0xFFFF5252),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.testTag("error_voice_text")
                        )
                    }
                    is VoiceState.Processing -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(32.dp),
                            color = Color(0xFF00B0FF),
                            strokeWidth = 3.dp
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            "COGNITIVE INTENT MATCHING...",
                            color = Color(0xFF8E95A5),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }
                    else -> {
                        Text(
                            text = "Driver Command Prompts:\n\"រកកន្លែងសាកថ្មឡាន\" (Find GB/T Charger)\n\"ទៅផ្សារទំនើបអ៊ីអន\" (Navigate to AEON Mall)",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center,
                            lineHeight = 20.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun GoogleStaticMapPreviewCard(
    landmark: MapLandmark,
    modifier: Modifier = Modifier
) {
    val mapsApiKey = remember {
        try {
            BuildConfig.MAPS_API_KEY
        } catch (e: Exception) {
            ""
        }
    }
    val hasValidApiKey = remember(mapsApiKey) {
        mapsApiKey.isNotEmpty() && mapsApiKey != "YOUR_GOOGLE_MAPS_API_KEY"
    }

    var isSatelliteMode by remember { mutableStateOf(true) }
    var useFallback by remember { mutableStateOf(!hasValidApiKey) }

    Card(
        modifier = modifier
            .width(145.dp)
            .height(115.dp)
            .clip(RoundedCornerShape(16.dp))
            .border(1.5.dp, Color(0xFF00B0FF), RoundedCornerShape(16.dp))
            .clickable {
                // Interactive tap allows drivers to toggle satellite vs standard map modes
                if (!useFallback) {
                    isSatelliteMode = !isSatelliteMode
                }
            }
            .testTag("static_map_preview_card"),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF121824))
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (!useFallback) {
                val mapTypeStr = if (isSatelliteMode) "hybrid" else "roadmap"
                val staticMapUrl = remember(landmark, mapsApiKey, isSatelliteMode) {
                    "https://maps.googleapis.com/maps/api/staticmap?center=${landmark.latitude},${landmark.longitude}&zoom=16&size=300x230&maptype=$mapTypeStr&markers=color:0x00B0FF%7Clabel:D%7C${landmark.latitude},${landmark.longitude}&key=$mapsApiKey"
                }

                AsyncImage(
                    model = staticMapUrl,
                    contentDescription = "Static Map destination preview",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    onState = { state ->
                        if (state is AsyncImagePainter.State.Error) {
                            println("StaticMapPreview: Failed to load static map, falling back. Error: " + state.result.throwable)
                            useFallback = true
                        }
                    }
                )
            }

            if (useFallback) {
                // Futuristic visual mesh/radar vector fallback when key is not configured or fails
                val infiniteTransition = rememberInfiniteTransition(label = "hud_pulse")
                val radarRadius by infiniteTransition.animateFloat(
                    initialValue = 15f,
                    targetValue = 45f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1500, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "pulse_radius"
                )
                val radarAlpha by infiniteTransition.animateFloat(
                    initialValue = 0.8f,
                    targetValue = 0.0f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1500, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "pulse_alpha"
                )

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Color(0xFF121A2E), Color(0xFF0B101D))
                            )
                        )
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val strokePx = 1.dp.toPx()
                        // Tracking Grid Rings
                        drawCircle(
                            color = Color(0xFF00B0FF).copy(alpha = 0.15f),
                            radius = size.minDimension / 3.5f,
                            center = center
                        )
                        drawCircle(
                            color = Color(0xFF00B0FF).copy(alpha = 0.08f),
                            radius = size.minDimension / 2.2f,
                            center = center
                        )

                        // Glowing Radar Pulse
                        drawCircle(
                            color = Color(0xFF00B0FF).copy(alpha = radarAlpha),
                            radius = radarRadius.dp.toPx(),
                            center = center,
                            style = Stroke(width = strokePx)
                        )

                        // Reticle lines
                        drawLine(
                            color = Color(0xFF2E3B5E),
                            start = androidx.compose.ui.geometry.Offset(0f, center.y),
                            end = androidx.compose.ui.geometry.Offset(size.width, center.y),
                            strokeWidth = 0.8f.dp.toPx()
                        )
                        drawLine(
                            color = Color(0xFF2E3B5E),
                            start = androidx.compose.ui.geometry.Offset(center.x, 0f),
                            end = androidx.compose.ui.geometry.Offset(center.x, size.height),
                            strokeWidth = 0.8f.dp.toPx()
                        )

                        // Marker Core Dot
                        drawCircle(
                            color = Color(0xFFFF5252),
                            radius = 5.dp.toPx(),
                            center = center
                        )
                    }

                    Text(
                        text = "GPS LOCK SIM",
                        color = Color(0xFF00B0FF),
                        fontSize = 7.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 6.dp)
                    )
                }
            }

            // Status Tag Overlay
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .background(Color(0xFF080C14).copy(alpha = 0.85f), RoundedCornerShape(6.dp))
                    .border(1.dp, Color(0xFF2E3B5E), RoundedCornerShape(6.dp))
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .background(if (useFallback) Color(0xFFFFA726) else Color(0xFF00E676), CircleShape)
                    )
                    Text(
                        text = if (useFallback) "SIM GPS" else if (isSatelliteMode) "HYBRID" else "ROAD",
                        color = Color.White,
                        fontSize = 7.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // Destination Name Label Overlay
            Text(
                text = landmark.name.uppercase(),
                color = Color.White,
                fontSize = 7.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.SansSerif,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .background(Color(0xFF121824).copy(alpha = 0.85f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            )
        }
    }
}

@Composable
fun PhnomPenhGoogleMapView(
    landmarks: List<MapLandmark>,
    selectedLandmark: MapLandmark?,
    isNavigating: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Initialize MapView with robust lifecycle connection
    val mapView = remember {
        MapView(context)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_CREATE -> mapView.onCreate(Bundle())
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier.testTag("google_map_android_view")
    ) { view ->
        view.getMapAsync { googleMap ->
            googleMap.clear()
            googleMap.uiSettings.isZoomControlsEnabled = true
            googleMap.uiSettings.isCompassEnabled = true

            val phnomPenhCenter = LatLng(11.5564, 104.9282)

            // EV Chargers & POI markers configuration
            landmarks.forEach { landmark ->
                val pos = LatLng(landmark.latitude, landmark.longitude)
                val isSelected = selectedLandmark?.id == landmark.id

                val title = if (landmark.isCharger) {
                    "⚡ [${landmark.chargerType}] ${landmark.name}"
                } else {
                    "📍 ${landmark.name}"
                }

                val markerOpts = MarkerOptions()
                    .position(pos)
                    .title(title)
                    .snippet(if (landmark.isCharger) "Deepal S05 Charging station" else "Destination Landmark")

                val hue = when {
                    landmark.isCharger && landmark.chargerType == "GB/T" -> BitmapDescriptorFactory.HUE_GREEN
                    landmark.isCharger && landmark.chargerType == "CCS2" -> BitmapDescriptorFactory.HUE_RED
                    else -> BitmapDescriptorFactory.HUE_AZURE
                }
                markerOpts.icon(BitmapDescriptorFactory.defaultMarker(hue))

                val marker = googleMap.addMarker(markerOpts)
                if (isSelected) {
                    marker?.showInfoWindow()
                }
            }

            // Draw current active vehicle location
            val vehiclePos = LatLng(11.5350, 104.9210)
            googleMap.addMarker(
                MarkerOptions()
                    .position(vehiclePos)
                    .title("🚘 MY DEEPAL S05 [ACTIVE]")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE))
            )

            // Move camera
            if (selectedLandmark != null) {
                googleMap.animateCamera(
                    CameraUpdateFactory.newLatLngZoom(
                        LatLng(selectedLandmark.latitude, selectedLandmark.longitude),
                        14.5f
                    )
                )
            } else {
                googleMap.moveCamera(
                    CameraUpdateFactory.newLatLngZoom(phnomPenhCenter, 13.0f)
                )
            }
        }
    }
}

@Composable
fun PhnomPenhVirtualMapCard(
    landmarks: List<MapLandmark>,
    selectedLandmark: MapLandmark?,
    isNavigating: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showGoogleMap by remember { mutableStateOf(false) }

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color(0xFF161C2C)),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.5.dp, Color(0xFF2E3B5E))
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (showGoogleMap) {
                PhnomPenhGoogleMapView(
                    landmarks = landmarks,
                    selectedLandmark = selectedLandmark,
                    isNavigating = isNavigating,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                PhnomPenhVirtualMap(
                    landmarks = landmarks,
                    selectedLandmark = selectedLandmark,
                    isNavigating = isNavigating,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Dynamic Live Google Static Map Mini-Preview on Navigation Entry (Only in Vector HUD Mode)
            androidx.compose.animation.AnimatedVisibility(
                visible = !showGoogleMap && isNavigating && selectedLandmark != null,
                enter = fadeIn() + expandIn(expandFrom = Alignment.TopEnd),
                exit = fadeOut() + shrinkOut(shrinkTowards = Alignment.TopEnd),
                modifier = Modifier
                    .padding(12.dp)
                    .align(Alignment.TopEnd)
            ) {
                if (selectedLandmark != null) {
                    GoogleStaticMapPreviewCard(
                        landmark = selectedLandmark,
                        modifier = Modifier
                    )
                }
            }

            // System Control & Toggle Panel Overlay
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
                    .align(Alignment.TopStart),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Float Compass overlay
                Box(
                    modifier = Modifier
                        .background(Color(0xFF121824).copy(alpha = 0.9f), RoundedCornerShape(10.dp))
                        .border(1.dp, Color(0xFF2E3B5E), RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = "Compass icon",
                            tint = Color(0xFF00B0FF),
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "COMPASS: PP-AUTO-NET",
                            color = Color.White,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                // Cockpit Map Selector Switch (HUD vs Google Map)
                Row(
                    modifier = Modifier
                        .background(Color(0xFF0C101B).copy(alpha = 0.95f), RoundedCornerShape(10.dp))
                        .border(1.dp, Color(0xFF2E3B5E), RoundedCornerShape(10.dp))
                        .padding(2.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (!showGoogleMap) Color(0xFF0061A4) else Color.Transparent)
                            .clickable { showGoogleMap = false }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .testTag("mode_hud_button")
                    ) {
                        Text(
                            text = "HUD GRID",
                            color = if (!showGoogleMap) Color.White else Color(0xFF8E95A5),
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (showGoogleMap) Color(0xFF00FF87).copy(alpha = 0.25f) else Color.Transparent)
                            .border(
                                1.dp,
                                if (showGoogleMap) Color(0xFF00FF87) else Color.Transparent,
                                RoundedCornerShape(8.dp)
                            )
                            .clickable { showGoogleMap = true }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .testTag("mode_google_button")
                    ) {
                        Text(
                            text = "GOOGLE MAP",
                            color = if (showGoogleMap) Color(0xFF00FF87) else Color(0xFF8E95A5),
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            // Low HUD details overlay (increased height and buttons touch size for driving comfort)
            if (isNavigating && selectedLandmark != null) {
                Box(
                    modifier = Modifier
                        .padding(10.dp)
                        .fillMaxWidth(0.94f)
                        .align(Alignment.BottomCenter)
                        .background(Color(0xFF121824).copy(alpha = 0.95f), RoundedCornerShape(16.dp))
                        .border(1.5.dp, Color(0xFF00B0FF), RoundedCornerShape(16.dp))
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Navigating to: ${selectedLandmark.name}",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                overflow = TextOverflow.Ellipsis,
                                maxLines = 1
                            )
                            Text(
                                text = if (selectedLandmark.isCharger) {
                                    "⚡ Charger Type Required: ${selectedLandmark.chargerType}"
                                } else {
                                    "General Route Planned"
                                },
                                color = Color(0xFF00B0FF),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        // Large Action buttons for Google Maps & Waze (driving comfort target sizes)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    val labelEnc = URLEncoder.encode(selectedLandmark.name, "UTF-8")
                                    val uri = Uri.parse("geo:0,0?q=${selectedLandmark.latitude},${selectedLandmark.longitude}($labelEnc)")
                                    val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                                        setPackage("com.google.android.apps.maps")
                                    }
                                    try {
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        val fallbackIntent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:${selectedLandmark.latitude},${selectedLandmark.longitude}?q=${selectedLandmark.latitude},${selectedLandmark.longitude}($labelEnc)"))
                                        context.startActivity(fallbackIntent)
                                    }
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0061A4)),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.height(34.dp).testTag("google_maps_intent_button")
                            ) {
                                Text("Maps", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Black)
                            }

                            Button(
                                onClick = {
                                    val uri = Uri.parse("waze://?ll=${selectedLandmark.latitude},${selectedLandmark.longitude}&navigate=yes")
                                    val intent = Intent(Intent.ACTION_VIEW, uri)
                                    try {
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        val fallbackUri = Uri.parse("https://waze.com/ul?ll=${selectedLandmark.latitude},${selectedLandmark.longitude}&navigate=yes")
                                        val fallbackIntent = Intent(Intent.ACTION_VIEW, fallbackUri)
                                        context.startActivity(fallbackIntent)
                                    }
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1F263E)),
                                border = BorderStroke(1.dp, Color(0xFF2E3B5E)),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.height(34.dp).testTag("waze_intent_button")
                            ) {
                                Text("Waze", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Black)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PresetCommandsPanelCard(
    voiceState: VoiceState,
    onPresetClick: (String, String) -> Unit,
    cachedCommands: List<CachedVoiceCommand>,
    onCachedCommandClick: (CachedVoiceCommand) -> Unit,
    onDeleteCachedCommand: (String) -> Unit,
    onClearCache: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableStateOf("PROMPTS") } // "PROMPTS" or "OFFLINE_CACHE"

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color(0xFF161C2C)),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.5.dp, Color(0xFF2E3B5E))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header Row: Tab Selectors & Action Buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Tab Buttons
                Row(
                    modifier = Modifier
                        .background(Color(0xFF0C101B), RoundedCornerShape(12.dp))
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (selectedTab == "PROMPTS") Color(0xFF1F263E) else Color.Transparent)
                            .clickable { selectedTab = "PROMPTS" }
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                            .testTag("tab_presets")
                    ) {
                        Text(
                            "PRESETS",
                            color = if (selectedTab == "PROMPTS") Color(0xFF00B0FF) else Color(0x99FFFFFF),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (selectedTab == "OFFLINE_CACHE") Color(0xFF1F263E) else Color.Transparent)
                            .clickable { selectedTab = "OFFLINE_CACHE" }
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                            .testTag("tab_caching")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                "LOCAL CACHE",
                                color = if (selectedTab == "OFFLINE_CACHE") Color(0xFF00B0FF) else Color(0x99FFFFFF),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black
                            )
                            if (cachedCommands.isNotEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .background(Color(0xFF00E676), CircleShape)
                                        .padding(horizontal = 5.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = cachedCommands.size.toString(),
                                        color = Color.Black,
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                }
                            }
                        }
                    }
                }

                // Header auxiliary button (e.g., Clear button for cache)
                if (selectedTab == "OFFLINE_CACHE" && cachedCommands.isNotEmpty()) {
                    Text(
                        text = "CLEAR ALL",
                        color = Color(0xFFFF8A80),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier
                            .clickable { onClearCache() }
                            .padding(4.dp)
                            .testTag("clear_all_cache")
                    )
                }
            }

            if (selectedTab == "PROMPTS") {
                val speechPresets = listOf(
                    Triple("EV_CHARGER", "រកកន្លែងសាកថ្មឡាន", "Find EV Charger • ⚡ GB/T"),
                    Triple("LOW_BATTERY", "ថ្មឡានជិតអស់ហើយ", "Simulate Low Battery ⚠️"),
                    Triple("AEON_MALL", "ទៅផ្សារទំនើបអ៊ីអនមានជ័យ", "Route to AEON Mall 📍"),
                    Triple("ERROR_OFFLINE", "បណ្តាញអុីនធឺណិតខ្សោយ", "Simulate Network Loss 🌐"),
                    Triple("ERROR_UNRECOGNIZED", "ពាក្យបញ្ជាមិនត្រឹមត្រូវ", "Simulate Unrecognized Phrase ❌"),
                    Triple("UNSUPPORTED", "អាកាសធាតុថ្ងៃនេះយ៉ាងម៉េចដែរ", "System Help Guide ❓")
                )

                // Adaptive 2x2 grid for standard landscape/tablet centers
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    speechPresets.chunked(2).forEach { pair ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            pair.forEach { preset ->
                                val isSelected = when (val state = voiceState) {
                                    is VoiceState.Success -> state.result.transcribedKhmerText == preset.second
                                    else -> false
                                }

                                // Large Tactile driving click block (Huge 78dp target height!)
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(78.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(if (isSelected) Color(0xFF1F2E52) else Color(0xFF1F263E))
                                        .border(
                                            1.5.dp,
                                            if (isSelected) Color(0xFF00B0FF) else Color(0xFF2E3B5E),
                                            RoundedCornerShape(14.dp)
                                        )
                                        .clickable {
                                            onPresetClick(preset.first, preset.second)
                                        }
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        // Huge Icon for fast category scan
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .background(
                                                    if (isSelected) Color(0xFF0061A4) else Color(0xFF161C2C),
                                                    CircleShape
                                                )
                                                .border(1.dp, Color(0xFF2E3B5E), CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = when (preset.first) {
                                                    "EV_CHARGER" -> Icons.Default.LocationOn
                                                    "LOW_BATTERY" -> Icons.Default.Warning
                                                    "AEON_MALL" -> Icons.Default.LocationOn
                                                    "ERROR_OFFLINE" -> Icons.Default.Close
                                                    "ERROR_UNRECOGNIZED" -> Icons.Default.Warning
                                                    else -> Icons.Default.Info
                                                },
                                                contentDescription = null,
                                                tint = if (isSelected) Color.White else Color(0xFF00B0FF),
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }

                                        Column(
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text(
                                                text = preset.second,
                                                color = Color.White,
                                                fontWeight = FontWeight.Black,
                                                fontSize = 12.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = preset.third,
                                                color = Color(0xFF8E95A5),
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // Offline Local Cache View
                if (cachedCommands.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF0C101B), RoundedCornerShape(14.dp))
                            .border(1.dp, Color(0xFF2E3B5E), RoundedCornerShape(14.dp))
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Cache Status",
                                tint = Color(0xFFFFA726),
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = ">> STANDBY & OFFLINE-READY",
                                color = Color(0xFFFFA726),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Voice command responses auto-persist locally in the Room database so you can re-run them with split-second speeds even when traveling off-grid.",
                                color = Color(0xFF8E95A5),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 8.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        cachedCommands.forEachIndexed { index, cached ->
                            val isSelected = when (val state = voiceState) {
                                is VoiceState.Success -> state.result.transcribedKhmerText == cached.query
                                else -> false
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isSelected) Color(0xFF1F2E52) else Color(0xFF1F263E))
                                    .border(
                                        1.dp,
                                        if (isSelected) Color(0xFF00B0FF) else Color(0xFF232B44),
                                        RoundedCornerShape(12.dp)
                                    )
                                    .clickable { onCachedCommandClick(cached) }
                                    .padding(start = 10.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Hit/Frequency Pill Indicator
                                    Box(
                                        modifier = Modifier
                                            .background(Color(0xFF00E676).copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                                            .border(1.dp, Color(0xFF00E676).copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "⚡ ${cached.frequency}x",
                                            color = Color(0xFF00E676),
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }

                                    Column {
                                        Text(
                                            text = cached.query,
                                            color = Color.White,
                                            fontWeight = FontWeight.Black,
                                            fontSize = 11.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "Intent: ${cached.intent.replace("navigate_", "nav_").uppercase()}",
                                            color = Color(0xFF8E95A5),
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                // Delete Button
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    IconButton(
                                        onClick = { onDeleteCachedCommand(cached.query) },
                                        modifier = Modifier
                                            .size(28.dp)
                                            .testTag("delete_cache_$index")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Purge Cache Item",
                                            tint = Color(0xFFFF8A80),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PhnomPenhVirtualMap(
    landmarks: List<MapLandmark>,
    selectedLandmark: MapLandmark?,
    isNavigating: Boolean,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current.density
    // Coordinate Bounds for Phnom Penh Map Draw
    val minLat = 11.4900
    val maxLat = 11.5900
    val minLng = 104.8800
    val maxLng = 104.9650

    // Source Vehicle Location: Hun Sen Blvd junction
    val vehicleLat = 11.5350
    val vehicleLng = 104.9210

    BoxWithConstraints(modifier = modifier.background(Color(0xFFF0F4FC))) {
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()

        // Projection mapping from Lat/Long to screen pixels in Canvas
        fun getProjectedX(lng: Double): Float {
            return (((lng - minLng) / (maxLng - minLng)) * widthPx).toFloat()
        }

        fun getProjectedY(lat: Double): Float {
            return ((1.0 - (lat - minLat) / (maxLat - minLat)) * heightPx).toFloat()
        }

        // Animated float for path drawing loop
        val transition = rememberInfiniteTransition(label = "mesh")
        val pathDashState by transition.animateFloat(
            initialValue = 0f,
            targetValue = 60f,
            animationSpec = infiniteRepeatable(
                animation = tween(1200, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "dash"
        )

        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 1.dp.toPx()

            // Main Boulevards draw (mock road grid) - clean light blue road layout
            val monivongLngs = listOf(104.9250, 104.9120, 104.9350)
            monivongLngs.forEach { lng ->
                drawLine(
                    color = Color(0xFFD1E4FF),
                    start = androidx.compose.ui.geometry.Offset(getProjectedX(lng), 0f),
                    end = androidx.compose.ui.geometry.Offset(getProjectedX(lng), heightPx),
                    strokeWidth = 3.dp.toPx()
                )
            }

            val boulevardLats = listOf(11.5600, 11.5300, 11.5150)
            boulevardLats.forEach { lat ->
                drawLine(
                    color = Color(0xFFD1E4FF),
                    start = androidx.compose.ui.geometry.Offset(0f, getProjectedY(lat)),
                    end = androidx.compose.ui.geometry.Offset(widthPx, getProjectedY(lat)),
                    strokeWidth = 3.dp.toPx()
                )
            }

            // Draw circular Phnom Penh compass rings in accent blue
            drawCircle(
                color = Color(0x330061A4),
                center = androidx.compose.ui.geometry.Offset(getProjectedX(104.9282), getProjectedY(11.5564)),
                radius = 120.dp.toPx(),
                style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 15f)))
            )

            // IF Navigating, draw direct glowing laser navigation path from vehicle to recipient
            if (isNavigating && selectedLandmark != null) {
                val startX = getProjectedX(vehicleLng)
                val startY = getProjectedY(vehicleLat)
                val endX = getProjectedX(selectedLandmark.longitude)
                val endY = getProjectedY(selectedLandmark.latitude)

                // Navigation trace glow
                drawLine(
                    color = Color(0x3B0061A4),
                    start = androidx.compose.ui.geometry.Offset(startX, startY),
                    end = androidx.compose.ui.geometry.Offset(endX, endY),
                    strokeWidth = 8.dp.toPx()
                )

                // Precise target navigation line
                drawLine(
                    color = Color(0xFF0061A4),
                    start = androidx.compose.ui.geometry.Offset(startX, startY),
                    end = androidx.compose.ui.geometry.Offset(endX, endY),
                    strokeWidth = 4.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(20f, 10f), pathDashState)
                )
            }

            // Draw Source Vehicle Location
            val carX = getProjectedX(vehicleLng)
            val carY = getProjectedY(vehicleLat)
            drawCircle(
                color = Color(0x330061A4),
                center = androidx.compose.ui.geometry.Offset(carX, carY),
                radius = 18.dp.toPx()
            )
            drawCircle(
                color = Color(0xFF0061A4),
                center = androidx.compose.ui.geometry.Offset(carX, carY),
                radius = 7.dp.toPx()
            )
        }

        // Overlay Interactive Compose Nodes on the projected map for maximum UX polish!
        landmarks.forEach { landmark ->
            val landX = getProjectedX(landmark.longitude) / density
            val landY = getProjectedY(landmark.latitude) / density

            val isTarget = selectedLandmark?.id == landmark.id

            Box(
                modifier = Modifier
                    .offset(x = (landX - 16).dp, y = (landY - 40).dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        when {
                            isTarget -> Color(0xFF0061A4)
                            landmark.isCharger && landmark.chargerType == "GB/T" -> Color(0xFFD1E4FF)
                            landmark.isCharger && landmark.chargerType == "CCS2" -> Color(0xFFFFF0F1)
                            else -> Color.White
                        }
                    )
                    .border(
                        1.dp,
                        if (isTarget) Color.White else Color(0xFFE1E2EC),
                        RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Icon(
                            imageVector = when {
                                landmark.isCharger -> Icons.Default.Star
                                else -> Icons.Default.LocationOn
                            },
                            contentDescription = "Map Node type description indicator icon",
                            tint = if (isTarget) Color.White else (if (landmark.isCharger && landmark.chargerType == "CCS2") Color(0xFFBA1A1A) else Color(0xFF0061A4)),
                            modifier = Modifier.size(10.dp)
                        )
                        Text(
                            text = if (landmark.isCharger) landmark.chargerType ?: "⚡" else "POI",
                            color = if (isTarget) Color.White else Color(0xFF1B1B1F),
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = landmark.name.take(11),
                        color = if (isTarget) Color.White else Color(0xFF44474E),
                        fontSize = 7.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun DriverQuickActionsRow(
    viewModel: VoiceIntelligenceViewModel,
    modifier: Modifier = Modifier
) {
    val cabinTemp by viewModel.cabinTemp.collectAsState()
    val isPlayingMusic by viewModel.isPlayingMusic.collectAsState()
    val selectedLandmark by viewModel.selectedLandmark.collectAsState()
    val isNavigating by viewModel.isNavigating.collectAsState()

    Row(
        modifier = modifier
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 1. Navigate Home Button (Large & Tactile)
        Box(
            modifier = Modifier
                .weight(1f)
                .height(64.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF161C2C))
                .border(
                    width = 1.5.dp,
                    color = if (isNavigating && selectedLandmark?.id == "dest_home_royal") Color(0xFF00B0FF) else Color(0xFF2E3B5E),
                    shape = RoundedCornerShape(16.dp)
                )
                .clickable {
                    viewModel.quickActionNavigateHome()
                }
                .testTag("quick_action_navigate_home")
                .padding(horizontal = 10.dp, vertical = 8.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(
                            color = if (isNavigating && selectedLandmark?.id == "dest_home_royal") Color(0xFF0061A4) else Color(0xFF1F263E),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Home,
                        contentDescription = "Navigate Home Quick Action",
                        tint = if (isNavigating && selectedLandmark?.id == "dest_home_royal") Color.White else Color(0xFF00B0FF),
                        modifier = Modifier.size(18.dp)
                    )
                }

                Column {
                    Text(
                        text = "Navigate Home",
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (isNavigating && selectedLandmark?.id == "dest_home_royal") "ACTIVE GPS NAV" else "Home Base",
                        color = if (isNavigating && selectedLandmark?.id == "dest_home_royal") Color(0xFF00B0FF) else Color(0xFF8E95A5),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // 2. Set Climate Button (Large & Tactile)
        Box(
            modifier = Modifier
                .weight(1f)
                .height(64.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF161C2C))
                .border(1.5.dp, Color(0xFF2E3B5E), RoundedCornerShape(16.dp))
                .clickable {
                    viewModel.quickActionSetClimate()
                }
                .testTag("quick_action_set_climate")
                .padding(horizontal = 10.dp, vertical = 8.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color(0xFF1F263E), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = "Climate adjustment",
                        tint = Color(0xFF00B0FF),
                        modifier = Modifier.size(18.dp)
                    )
                }

                Column {
                    Text(
                        text = "Set Climate",
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Temp: ${String.format(Locale.US, "%.1f", cabinTemp)}°C",
                        color = Color(0xFF00B0FF),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // 3. Play Music Button (Large & Tactile)
        Box(
            modifier = Modifier
                .weight(1f)
                .height(64.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF161C2C))
                .border(
                    width = 1.5.dp,
                    color = if (isPlayingMusic) Color(0xFF00B0FF) else Color(0xFF2E3B5E),
                    shape = RoundedCornerShape(16.dp)
                )
                .clickable {
                    viewModel.quickActionPlayMusic()
                }
                .testTag("quick_action_play_music")
                .padding(horizontal = 10.dp, vertical = 8.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(
                            color = if (isPlayingMusic) Color(0xFF0061A4) else Color(0xFF1F263E),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPlayingMusic) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = "Toggle Playlist Playback",
                        tint = if (isPlayingMusic) Color.White else Color(0xFF00B0FF),
                        modifier = Modifier.size(18.dp)
                    )
                }

                Column {
                    Text(
                        text = "Play Music",
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (isPlayingMusic) "Jazz Chill 🎵" else "PAUSED",
                        color = if (isPlayingMusic) Color(0xFF00B0FF) else Color(0xFF8E95A5),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun CyberVoiceWaveVisualizer(
    voiceState: VoiceState,
    isRecording: Boolean,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "wave_anim")
    
    // Wave phase shifts
    val phaseShift1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2 * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(if (isRecording) 1400 else 4500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase1"
    )
    val phaseShift2 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -2 * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(if (isRecording) 1800 else 6500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase2"
    )
    val phaseShift3 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2 * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(if (isRecording) 1000 else 3200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase3"
    )

    // Breathing scale for active glow
    val breatheScale by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isRecording) 700 else 2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathe"
    )

    val baseColor = when (voiceState) {
        is VoiceState.Recording -> Color(0xFF00FF87)
        is VoiceState.Processing -> Color(0xFFFF8A00)
        is VoiceState.Success -> Color(0xFF00FF87)
        is VoiceState.Error -> Color(0xFFFF5252)
        else -> Color(0xFF00B0FF)
    }

    val secColor = when (voiceState) {
        is VoiceState.Recording -> Color(0xFF00B0FF)
        is VoiceState.Processing -> Color(0xFF9C27B0)
        is VoiceState.Success -> Color(0xFF00D2FF)
        is VoiceState.Error -> Color(0xFFFF1744)
        else -> Color(0x3300B0FF)
    }

    val waveCount = 3
    val waveAmplitudes = when (voiceState) {
        is VoiceState.Recording -> listOf(14.dp, 10.dp, 5.dp)
        is VoiceState.Processing -> listOf(5.dp, 11.dp, 7.dp)
        is VoiceState.Success -> listOf(8.dp, 4.dp, 1.dp)
        is VoiceState.Error -> listOf(3.dp, 2.dp, 1.dp)
        else -> listOf(3.dp, 1.dp, 0.5.dp)
    }

    val waveFrequencies = when (voiceState) {
        is VoiceState.Recording -> listOf(1.5f, 2.8f, 4.2f)
        is VoiceState.Processing -> listOf(4.5f, 6.0f, 9.5f)
        is VoiceState.Success -> listOf(1.2f, 2.0f, 3.0f)
        is VoiceState.Error -> listOf(8.0f, 10.0f, 12.0f)
        else -> listOf(1.0f, 1.8f, 2.5f)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .background(Color(0xFF0C101B).copy(alpha = 0.35f), RoundedCornerShape(10.dp))
            .border(1.dp, Color(0xFF1F263E).copy(alpha = 0.5f), RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .scale(if (isRecording) breatheScale else 1.0f)
                .testTag("cyber_voice_wave_canvas")
        ) {
            val width = size.width
            val height = size.height
            val centerY = height / 2f

            for (i in 0 until waveCount) {
                val path = Path()
                val amp = waveAmplitudes.getOrElse(i) { 6.dp }.toPx()
                val freq = waveFrequencies.getOrElse(i) { 2.0f }
                val phase = when (i) {
                    0 -> phaseShift1
                    1 -> phaseShift2
                    else -> phaseShift3
                }
                val opacity = when (i) {
                    0 -> 0.8f
                    1 -> 0.5f
                    else -> 0.25f
                }

                path.moveTo(0f, centerY)
                for (x in 0..width.toInt() step 4) {
                    val xNormalized = x.toFloat() / width
                    val window = Math.sin(xNormalized * Math.PI).toFloat()
                    val angle = (xNormalized * freq * 2f * Math.PI.toFloat()) + phase
                    val y = centerY + (Math.sin(angle.toDouble()).toFloat() * amp * window)
                    path.lineTo(x.toFloat(), y)
                }

                drawPath(
                    path = path,
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            baseColor.copy(alpha = opacity),
                            secColor.copy(alpha = opacity * 0.7f),
                            baseColor.copy(alpha = opacity)
                        )
                    ),
                    style = Stroke(
                        width = (2f - (i * 0.5f)).dp.toPx(),
                        cap = StrokeCap.Round
                    )
                )
            }
        }
    }
}

@Composable
fun DeepalVoiceFeedbackComponent(
    voiceState: VoiceState,
    isRecording: Boolean,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "ambient_glow")
    
    // Smooth pulsating glow multiplier for active listener
    val glowPulse by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )

    val isActiveMode = isRecording || voiceState is VoiceState.Recording

    // Smooth transition animations for Card styling and borders
    val cardBorderWidth by animateDpAsState(
        targetValue = if (isActiveMode || voiceState is VoiceState.Processing) 2.dp else 1.dp,
        animationSpec = tween(400, easing = FastOutSlowInEasing),
        label = "card_border_width"
    )

    val cardBorderColor by animateColorAsState(
        targetValue = if (isActiveMode) {
            Color(0xFF00E5FF)
        } else if (voiceState is VoiceState.Processing) {
            Color(0xFF00B0FF)
        } else if (voiceState is VoiceState.Success) {
            Color(0xFF00FF87)
        } else if (voiceState is VoiceState.Error) {
            Color(0xFFFF5252)
        } else {
            Color(0xFF2E3B5E)
        },
        animationSpec = tween(400, easing = FastOutSlowInEasing),
        label = "card_border_color"
    )

    val cardBgColor by animateColorAsState(
        targetValue = if (isActiveMode) {
            Color(0xFF1B2338) // Deep pulsing active cyber dashboard blue
        } else if (voiceState is VoiceState.Processing) {
            Color(0xFF132035) // Deep thinking blue
        } else if (voiceState is VoiceState.Success) {
            Color(0xFF102521) // Subtle confirmation green
        } else if (voiceState is VoiceState.Error) {
            Color(0xFF26151B) // Dialect warning red tint
        } else {
            Color(0xFF161C2C) // Sleek standby navy
        },
        animationSpec = tween(400, easing = FastOutSlowInEasing),
        label = "card_bg_color"
    )

    val radarBorderColor by animateColorAsState(
        targetValue = if (isActiveMode) Color(0xFF00FF87) else Color(0xFF2A344E),
        animationSpec = tween(400, easing = FastOutSlowInEasing),
        label = "radar_border_color"
    )

    val iconColor by animateColorAsState(
        targetValue = when (voiceState) {
            is VoiceState.Recording -> Color(0xFF00FF87)
            is VoiceState.Processing -> Color(0xFF00B0FF)
            is VoiceState.Success -> Color(0xFF00FF87)
            is VoiceState.Error -> Color(0xFFFF5252)
            else -> if (isRecording) Color(0xFF00FF87) else Color(0xFF8E95A5)
        },
        animationSpec = tween(400, easing = FastOutSlowInEasing),
        label = "icon_color"
    )

    val glowColor = when (voiceState) {
        is VoiceState.Recording -> Color(0xFF00FF87).copy(alpha = glowPulse)
        is VoiceState.Processing -> Color(0xFF00B0FF).copy(alpha = glowPulse * 0.7f)
        is VoiceState.Success -> Color(0xFF00E676).copy(alpha = 0.8f)
        is VoiceState.Error -> Color(0xFFFF5252).copy(alpha = 0.8f)
        else -> Color(0xFF00B0FF).copy(alpha = 0.08f)
    }

    val stateText = when (voiceState) {
        is VoiceState.Idle -> if (isRecording) "🎙️ S05 SPEECH SENSOR ACTIVE [KHMER / ENGLISH]" else "🎧 DEEPAL VOICE SYSTEM STANDBY"
        is VoiceState.Recording -> "🎙️ S05 SPEECH SENSOR ACTIVE [KHMER / ENGLISH]"
        is VoiceState.Processing -> "🤖 DECODING NATURAL COGNITIVE REQUESTS..."
        is VoiceState.Success -> "⚡ COGNITIVE ACTION EXPEDITED!"
        is VoiceState.Error -> {
            val msg = voiceState.message
            if (msg.contains("Connection") || msg.contains("network") || msg.contains("internet") || msg.contains("បណ្តាញ") || msg.contains("offline") || msg.contains("failure")) {
                "🌐 COGNITIVE SYSTEM OFFLINE"
            } else {
                "🎙️ UNRECOGNIZED VOICE COMMAND"
            }
        }
    }

    val stateSubtitle = when (voiceState) {
        is VoiceState.Idle -> if (isRecording) "System is capturing live driver audio cues..." else "Wait or tap / hold key to speak commands."
        is VoiceState.Recording -> "System is capturing live driver audio cues..."
        is VoiceState.Processing -> "Invoking state synthesis and context analysis..."
        is VoiceState.Success -> "Executing targeted smart vehicle commands..."
        is VoiceState.Error -> {
            val msg = voiceState.message
            if (msg.contains("Connection") || msg.contains("network") || msg.contains("internet") || msg.contains("បណ្តាញ") || msg.contains("offline") || msg.contains("failure")) {
                "Internet connection lost or API server timeout. Tip: Check signal/use presets."
            } else {
                "Dialect mismatch or command not recognized. Tip: Speak clearly or tap presets."
            }
        }
    }

    Card(
        modifier = modifier
            .testTag("voice_feedback_card")
            .background(Color.Transparent),
        colors = CardDefaults.cardColors(containerColor = cardBgColor),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(cardBorderWidth, cardBorderColor)
    ) {
        // Overlay radial background glow on active
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Interactive Radar Indicator
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .background(Color(0xFF0C101B), CircleShape)
                        .border(1.dp, radarBorderColor, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .background(glowColor, CircleShape)
                    )
                    Icon(
                        imageVector = when (voiceState) {
                            is VoiceState.Recording -> Icons.Default.Hearing
                            is VoiceState.Processing -> Icons.Default.Refresh
                            is VoiceState.Success -> Icons.Default.CheckCircle
                            is VoiceState.Error -> Icons.Default.Warning
                            else -> if (isRecording) Icons.Default.Hearing else Icons.Default.Mic
                        },
                        contentDescription = "Voice State Icon",
                        tint = iconColor,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Description Layout with smooth slider and fading transition for textual details
                Column(
                    modifier = Modifier.weight(1.2f),
                    verticalArrangement = Arrangement.Center
                ) {
                    AnimatedContent(
                        targetState = stateText,
                        transitionSpec = {
                            (fadeIn(animationSpec = tween(320, easing = FastOutSlowInEasing)) + 
                             slideInVertically(animationSpec = tween(320, easing = FastOutSlowInEasing)) { it / 2 }) togetherWith
                            (fadeOut(animationSpec = tween(200, easing = FastOutSlowInEasing)) + 
                             slideOutVertically(animationSpec = tween(200, easing = FastOutSlowInEasing)) { -it / 2 })
                        },
                        label = "state_text_transition"
                    ) { targetText ->
                        Text(
                            text = targetText,
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    AnimatedContent(
                        targetState = stateSubtitle,
                        transitionSpec = {
                            (fadeIn(animationSpec = tween(320, easing = FastOutSlowInEasing)) + 
                             slideInVertically(animationSpec = tween(320, easing = FastOutSlowInEasing)) { it / 2 }) togetherWith
                            (fadeOut(animationSpec = tween(200, easing = FastOutSlowInEasing)) + 
                             slideOutVertically(animationSpec = tween(200, easing = FastOutSlowInEasing)) { -it / 2 })
                        },
                        label = "state_subtitle_transition"
                    ) { targetSub ->
                        Text(
                            text = targetSub,
                            color = Color(0xFF8E95A5),
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    androidx.compose.animation.AnimatedVisibility(
                        visible = voiceState is VoiceState.Error,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Column {
                            Spacer(modifier = Modifier.height(4.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF301518), RoundedCornerShape(6.dp))
                                    .border(1.dp, Color(0xFFFF5252).copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 6.dp, vertical = 4.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = "Error detail icon",
                                        tint = Color(0xFFFF5252),
                                        modifier = Modifier.size(10.dp)
                                    )
                                    Text(
                                        text = if (voiceState is VoiceState.Error) voiceState.message else "",
                                        color = Color(0xFFFF8A80),
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Black,
                                        fontFamily = FontFamily.Monospace,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }

                // Interactive Sine Wave
                CyberVoiceWaveVisualizer(
                    voiceState = voiceState,
                    isRecording = isActiveMode,
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                )
            }
        }
    }
}
