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
import androidx.compose.foundation.layout.*
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
                    containerColor = Color(0xFFF8F9FF)
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
            .background(Color(0xFFF8F9FF))
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
                        color = Color(0xFF1B1B1F),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.SansSerif
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .background(Color(0xFF0061A4), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "🇨🇳 Chinese Spec • GB/T Only",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Text(
                    text = "Phnom Penh, Cambodia",
                    color = Color(0xFF44474E),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            // Gear Position Simulation
            Row(
                modifier = Modifier
                    .background(Color(0xFFE1E2EC), RoundedCornerShape(14.dp))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                listOf("P", "R", "N", "D").forEach { gear ->
                    val isActive = if (isSimulatingDrive) gear == "D" else gear == "P"
                    Text(
                        text = gear,
                        color = if (isActive) Color(0xFF0061A4) else Color(0x731B1B1F),
                        fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.Normal,
                        fontSize = 14.sp
                    )
                }
            }
        }
        // --- 2. MULTI-COLUMN CONSOLE PANELS (Responsive Layout) ---
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            val isWide = maxWidth >= 760.dp

            if (isWide) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // LEFT COLUMN: Vehicle Controls & Voice Console
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
                                .padding(bottom = 12.dp)
                        )

                        VoiceIntelligenceCard(
                            voiceState = voiceState,
                            isRecording = isRecording,
                            isDemoMode = isDemoMode,
                            hasMicPermission = hasMicPermission,
                            onMicToggle = {
                                if (isRecording) {
                                    viewModel.stopMicRecording()
                                } else {
                                    if (hasMicPermission) {
                                        viewModel.startMicRecording()
                                    } else {
                                        requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                }
                            },
                            onDemoModeToggle = { viewModel.setDemoModeActive(!isDemoMode) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        )
                    }

                    // RIGHT COLUMN: Clean Map & Preset Commands Panel
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
                                .weight(1.3f)
                                .padding(bottom = 12.dp)
                        )

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
            } else {
                // Compact Screen Layout
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
                        onMicToggle = {
                            if (isRecording) {
                                viewModel.stopMicRecording()
                            } else {
                                if (hasMicPermission) {
                                    viewModel.startMicRecording()
                                } else {
                                    requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            }
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
                            .height(300.dp)
                    )
                }
            }
        }

        // --- 3. DEVELOPMENT LOGS CONSOLE ---
        Spacer(modifier = Modifier.height(12.dp))
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(130.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, Color(0xFFE1E2EC))
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "APPLICATION CONSOLE / COGNITIVE JSON LOGS",
                        color = Color(0xFF0061A4),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    )

                    // Actions
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Copy JSON",
                            color = Color(0xFF0061A4),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier
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
                                        Toast.makeText(context, "Telemetry JSON copied to Clipboard!", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "No successful voice result logged yet", Toast.LENGTH_SHORT).show()
                                    }
                                }
                                .padding(2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFFF8F9FF), RoundedCornerShape(10.dp))
                        .border(1.dp, Color(0xFFE1E2EC), RoundedCornerShape(10.dp))
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
                            color = Color(0xFF1B1B1F),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            modifier = Modifier.testTag("copied_json_console")
                        )
                    } else {
                        Text(
                            text = ">> Standby. Start driving or trigger a Khmer voice input above to parse vehicle response metrics...",
                            color = Color(0xFF44474E),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp
                        )
                    }
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
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, Color(0xFFE1E2EC))
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Live Instrument Panel",
                    color = Color(0xFF1B1B1F),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                // Drive Simulator switch
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (isSimulatingDrive) "Driving" else "Parked",
                        color = if (isSimulatingDrive) Color(0xFF0061A4) else Color(0xFF44474E),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(end = 6.dp)
                    )
                    Switch(
                        checked = isSimulatingDrive,
                        onCheckedChange = onCheckedChange,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFF0061A4),
                            uncheckedThumbColor = Color(0xFF44474E),
                            uncheckedTrackColor = Color(0xFFE1E2EC)
                        ),
                        modifier = Modifier.testTag("driver_simulation_switch")
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Speed Info
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("CURRENT SPEED", color = Color(0xFF44474E), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = "$speed",
                            color = Color(0xFF1B1B1F),
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.SansSerif
                        )
                        Text(
                            " km/h",
                            color = Color(0xFF44474E),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                    }
                }

                // Battery Info
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("BATTERY STATUS", color = Color(0xFF44474E), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.Bottom) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "Battery charge icon",
                            tint = if (batteryRef < 20) Color(0xFFBA1A1A) else Color(0xFF0061A4),
                            modifier = Modifier.size(24.dp).padding(bottom = 2.dp)
                        )
                        Text(
                            text = "$batteryRef",
                            color = if (batteryRef < 20) Color(0xFFBA1A1A) else Color(0xFF1B1B1F),
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.SansSerif
                        )
                        Text(
                            " %",
                            color = Color(0xFF44474E),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                    }
                }

                // Range Info
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("ESTIMATED RANGE", color = Color(0xFF44474E), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = "$rangeRef",
                            color = Color(0xFF0061A4),
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.SansSerif
                        )
                        Text(
                            " km",
                            color = Color(0xFF44474E),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                    }
                }
            }

            if (batteryRef < 30) {
                Spacer(modifier = Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFFFF0F1), RoundedCornerShape(12.dp))
                        .border(1.dp, Color(0xFFFFCCD0), RoundedCornerShape(12.dp))
                        .padding(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Warning Low Battery icon",
                            tint = Color(0xFFBA1A1A),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "ថ្មឡានជិតអស់ហើយ! ស្នើសុំសាកថ្មជាមួយប្រភេទ GB/T។",
                            color = Color(0xFFBA1A1A),
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
    onMicToggle: () -> Unit,
    onDemoModeToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, Color(0xFFE1E2EC))
    ) {
        Column(
            modifier = Modifier
                .padding(18.dp)
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
                    color = Color(0xFF1B1B1F),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )

                // Demo / Live API Selector Badge
                Box(
                    modifier = Modifier
                        .background(
                            if (isDemoMode) Color(0xFFE1E2EC) else Color(0xFFD1E4FF),
                            RoundedCornerShape(8.dp)
                        )
                        .border(
                            1.dp,
                            if (isDemoMode) Color(0xFFBCBFCD) else Color(0xFF0061A4),
                            RoundedCornerShape(8.dp)
                        )
                        .clickable { onDemoModeToggle() }
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (isDemoMode) "Demo Engine Active" else "Live Gemini API Beta",
                        color = if (isDemoMode) Color(0xFF1B1B1F) else Color(0xFF0061A4),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Pulsing Minimalist Blue Speech Orb Panel
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(110.dp)
            ) {
                // Infinite Pulsing Ripple Effects using InfiniteTransition
                val infiniteTransition = rememberInfiniteTransition(label = "speech_ripple")
                val rippleScale1 by infiniteTransition.animateFloat(
                    initialValue = 1f,
                    targetValue = 1.6f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1400, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "scale1"
                )
                val rippleAlpha1 by infiniteTransition.animateFloat(
                    initialValue = 0.6f,
                    targetValue = 0f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1400, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "alpha1"
                )

                val rippleScale2 by infiniteTransition.animateFloat(
                    initialValue = 1f,
                    targetValue = 2.0f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(2000, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "scale2"
                )
                val rippleAlpha2 by infiniteTransition.animateFloat(
                    initialValue = 0.4f,
                    targetValue = 0f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(2000, easing = FastOutSlowInEasing),
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
                                    colors = listOf(Color(0x3D0061A4), Color.Transparent)
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
                                    colors = listOf(Color(0x240061A4), Color.Transparent)
                                ),
                                shape = CircleShape
                            )
                            .scale(rippleAlpha2)
                    )
                }

                // Clean Mic Trigger button
                Button(
                    onClick = onMicToggle,
                    modifier = Modifier
                        .size(82.dp)
                        .testTag("microphone_button"),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = when {
                            isRecording -> Color(0xFFBA1A1A)
                            voiceState is VoiceState.Processing -> Color(0xFF0061A4)
                            else -> Color(0xFFE1E2EC)
                        }
                    ),
                    contentPadding = PaddingValues(0.dp),
                    border = BorderStroke(2.dp, Color(0xFF0061A4))
                ) {
                    Icon(
                        imageVector = if (isRecording) Icons.Default.Close else Icons.Default.Call,
                        contentDescription = "Toggle Recording Microphone",
                        tint = if (isRecording || voiceState is VoiceState.Processing) Color.White else Color(0xFF0061A4),
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Mic Status Subtitles
            Text(
                text = when (voiceState) {
                    is VoiceState.Idle -> "Tap Microphone to Speak Khmer"
                    is VoiceState.Recording -> "LISTENING... Speak Khmer"
                    is VoiceState.Processing -> "EXTRACTING INTENT..."
                    is VoiceState.Success -> "CMD SUCCESSFUL"
                    is VoiceState.Error -> "SYSTEM OVERLOAD"
                },
                color = when (voiceState) {
                    is VoiceState.Recording -> Color(0xFFBA1A1A)
                    is VoiceState.Processing -> Color(0xFF0061A4)
                    is VoiceState.Success -> Color(0xFF0061A4)
                    is VoiceState.Error -> Color(0xFFBA1A1A)
                    else -> Color(0xFF44474E)
                },
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.testTag("microphone_status")
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Transcripts HUD Panel - Baby Blue background (#D1E4FF)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color(0xFFD1E4FF), RoundedCornerShape(20.dp))
                    .border(1.dp, Color(0xFF0061A4).copy(alpha = 0.1f), RoundedCornerShape(20.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                when (val state = voiceState) {
                    is VoiceState.Success -> {
                        Text(
                            text = "“ ${state.result.transcribedKhmerText} ”",
                            color = Color(0xFF0061A4),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(bottom = 6.dp).testTag("transcription_result")
                        )
                        HorizontalDivider(color = Color(0x330061A4), thickness = 1.dp)
                        Text(
                            text = state.result.spokenResponseKhmer,
                            color = Color(0xFF1B1B1F),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 10.dp).testTag("spoken_text_result")
                        )
                    }
                    is VoiceState.Error -> {
                        Text(
                            text = state.message,
                            color = Color(0xFFBA1A1A),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.testTag("error_voice_text")
                        )
                    }
                    is VoiceState.Processing -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = Color(0xFF0061A4),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "MAPPING COGNITIVE TELEMETRY...",
                            color = Color(0xFF1B1B1F),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }
                    else -> {
                        Text(
                            text = "Try speaking: \"រកកន្លែងសាកថ្មឡាន\" (Find a GB/T Charger) or \"ទៅផ្សារទំនើបអ៊ីអន\" (Navigate to AEON Mall)",
                            color = Color(0xFF1B1B1F),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp
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
        colors = CardDefaults.cardColors(containerColor = Color(0xFFEDF2FA)),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, Color(0xFFE1E2EC))
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
                    .background(Color.White.copy(alpha = 0.9f), RoundedCornerShape(10.dp))
                    .border(1.dp, Color(0xFFE1E2EC), RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "Compass icon",
                        tint = Color(0xFF0061A4),
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "COMPASS: PP-AUTO-NET",
                        color = Color(0xFF1B1B1F),
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // Low HUD details overlay
            if (isNavigating && selectedLandmark != null) {
                Box(
                    modifier = Modifier
                        .padding(10.dp)
                        .fillMaxWidth(0.92f)
                        .align(Alignment.BottomCenter)
                        .background(Color.White.copy(alpha = 0.95f), RoundedCornerShape(16.dp))
                        .border(1.dp, Color(0xFF0061A4), RoundedCornerShape(16.dp))
                        .padding(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Navigating to: ${selectedLandmark.name}",
                                color = Color(0xFF1B1B1F),
                                fontSize = 11.sp,
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
                                color = Color(0xFF0061A4),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        // Action buttons for Google Maps & Waze
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(28.dp).testTag("google_maps_intent_button")
                            ) {
                                Text("Maps", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
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
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE1E2EC)),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(28.dp).testTag("waze_intent_button")
                            ) {
                                Text("Waze", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
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
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, Color(0xFFE1E2EC))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                "Simulate Khmer Spoken Inputs",
                color = Color(0xFF1B1B1F),
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            val speechPresets = listOf(
                Triple("EV_CHARGER", "រកកន្លែងសាកថ្មឡាន", "Find EV Charger"),
                Triple("LOW_BATTERY", "ថ្មឡានជិតអស់ហើយ", "Car Battery Low"),
                Triple("AEON_MALL", "ទៅផ្សារទំនើបអ៊ីអនមានជ័យ", "Navigate: AEON Mall"),
                Triple("UNSUPPORTED", "អាកាសធាតុថ្ងៃនេះយ៉ាងម៉េចដែរ", "Query Weather (unsupported)")
            )

            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                speechPresets.forEach { preset ->
                    val isSelected = when (val state = voiceState) {
                        is VoiceState.Success -> state.result.transcribedKhmerText == preset.second
                        else -> false
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) Color(0xFFD1E4FF) else Color(0xFFF8F9FF))
                            .border(
                                1.dp,
                                if (isSelected) Color(0xFF0061A4) else Color(0xFFE1E2EC),
                                RoundedCornerShape(12.dp)
                            )
                            .clickable {
                                onPresetClick(preset.first, preset.second)
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = preset.second,
                                color = Color(0xFF1B1B1F),
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                            Text(
                                text = preset.third,
                                color = Color(0xFF44474E),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = "Trigger Speech preset simulation",
                            tint = if (isSelected) Color(0xFF0061A4) else Color(0xFF1B1B1F),
                            modifier = Modifier.size(16.dp)
                        )
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
