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
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.MapLandmark
import com.example.viewmodel.VoiceIntelligenceViewModel
import com.example.viewmodel.VoiceState
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
fun PhnomPenhVirtualMapCard(
    landmarks: List<MapLandmark>,
    selectedLandmark: MapLandmark?,
    isNavigating: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color(0xFF161C2C)),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.5.dp, Color(0xFF2E3B5E))
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            PhnomPenhVirtualMap(
                landmarks = landmarks,
                selectedLandmark = selectedLandmark,
                isNavigating = isNavigating,
                modifier = Modifier.fillMaxSize()
            )

            // Float Compass overlay
            Box(
                modifier = Modifier
                    .padding(12.dp)
                    .align(Alignment.TopStart)
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
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color(0xFF161C2C)),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.5.dp, Color(0xFF2E3B5E))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                "Driver Fast Command Shorthands",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            val speechPresets = listOf(
                Triple("EV_CHARGER", "រកកន្លែងសាកថ្មឡាន", "Find EV Charger • ⚡ GB/T"),
                Triple("LOW_BATTERY", "ថ្មឡានជិតអស់ហើយ", "Simulate Low Battery ⚠️"),
                Triple("AEON_MALL", "ទៅផ្សារទំនើបអ៊ីអនមានជ័យ", "Route to AEON Mall 📍"),
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
