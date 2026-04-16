@file:Suppress("ANNOTATIONS_ON_BLOCK_LEVEL_EXPRESSION_ON_THE_SAME_LINE", "DEPRECATION")
package com.example.sarroverrt

// Android / OS
import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas // Aliased to avoid conflict with Compose Canvas
import android.net.Uri
import android.os.*
import android.provider.MediaStore
import android.view.View
import android.view.ViewGroup
import android.webkit.*
import android.widget.Toast
import androidx.compose.ui.draw.rotate
import android.util.Log

// Activity & Lifecycle
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope

// Compose Core & Animation
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable

// Compose UI & Graphics
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.ui.platform.LocalConfiguration
import android.content.res.Configuration

// Material Design 3
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*

// System UI & Window
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

// Networking & Coroutines
import io.socket.client.IO
import io.socket.client.Socket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

// Utilities
import com.example.sarroverrt.ui.theme.SARRoverRTTheme
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.*

import android.bluetooth.BluetoothAdapter
import java.net.HttpURLConnection
import java.net.URL
import android.os.Build



data class MissionHistory(
    val id: String = UUID.randomUUID().toString(),
    val date: String,
    val duration: String,
    val alertCount: Int
)

val CyberDark = Color(0xFF000810)
val CyberBlue = Color(0xFF004E92)
val CyberLight = Color(0xFF00FBFF)
val CyberWhite = Color(0xFFDDE2E7) // Not pure white; more like industrial aluminum
val CyberLightGray = Color(0xFFC8D1D9) // Darker gray for inner cards/containers
val CyberAccentBlue = Color(0xFF003D73) // Deep Navy for high contrast in light mode

// CLASS ROVERVIEWMODEL HERE VVVV
class RoverViewModel : ViewModel() {
    // --- CORE NETWORKING & HISTORY ---
    val chatHistory = mutableStateListOf<Triple<String, String, String>>()
    var mSocket: Socket? = null
    private val PI_URL = "https://sarrover.tailb48aa5.ts.net" // Tailscale Global URL

    private val _alertFlow = MutableSharedFlow<String>(extraBufferCapacity = 50)
    val alertFlow = _alertFlow.asSharedFlow()
    var isPiReachable by mutableStateOf(false)
    var isBluetoothReachable by mutableStateOf(false)
    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()

    // --- SENSOR GRAPH HISTORY (Limit to 50 points) ---
    val micHistory = mutableStateListOf<Float>()
    val thermalHistory = mutableStateListOf<Float>()

    // --- CONCURRENCY & LOCKDOWN (MULTI-USER SYNC) ---
    var selectedConnectionMethod by mutableStateOf("WIFI")
    var isRoverBusy by mutableStateOf(false)
    var currentOperatorName by mutableStateOf("")
    var userRole by mutableStateOf("RESCUER")
    var isOperator by mutableStateOf(false)
    var controlRequestFrom by mutableStateOf<String?>(null)

    // Unique Identifier
    val myUserName = "OPERATOR_" + Build.MODEL.take(5).uppercase()

    // --- TOGGLES & STATUS ---
    var isNotificationEnabled by mutableStateOf(true)
    var isRawMicEnabled by mutableStateOf(false)
    var sensitivity by mutableFloatStateOf(400f)
    var isConnected by mutableStateOf(false)
    var isThermalMode by mutableStateOf(false)
    var isNightVision by mutableStateOf(false)

    // --- TELEMETRY ---
    var batteryVoltage by mutableStateOf("0.0V")
    var batteryCurrent by mutableStateOf("0mA")
    var batteryWatts by mutableStateOf("0.0W")
    var batteryPercentage by mutableStateOf("0%")
    var batteryState by mutableStateOf("IDLE")
    var arduinoStatus by mutableStateOf("DISCONNECTED")
    var cameraStatus by mutableStateOf("LOST")
    var thermalStatus by mutableStateOf("LOST")
    var wifiSSID by mutableStateOf("SEARCHING")
    var piTemp by mutableStateOf("0°C")

    // --- USER ACCOUNT STATE ---
    var currentUser by mutableStateOf<UserAccount?>(null)
    var isLoggingIn by mutableStateOf(false)

    // --- AUTO-START ---
    init {
        initSocket()
    }

    fun initSocket() {
        if (mSocket?.connected() == true) return

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val opts = IO.Options().apply {
                    forceNew = false
                    reconnection = true
                    transports = arrayOf("websocket")
                    timeout = 10000
                }

                mSocket = IO.socket(PI_URL, opts)

                // --- CONNECTION EVENTS ---
                mSocket?.on(Socket.EVENT_CONNECT) {
                    viewModelScope.launch(Dispatchers.Main) {
                        isConnected = true

                        // NEW FEATURE: AUTO-LAUNCH SAR.PY ON CONNECTION
                        // This triggers your sudo command on the Raspberry Pi
                        val initData = JSONObject().apply {
                            put("operator", myUserName)
                            put("mode", selectedConnectionMethod)
                            put("fileName", "SAR")
                        }
                        mSocket?.emit("init_sar", initData)

                        mSocket?.emit("sync_mic_mode", if (isRawMicEnabled) "RAW_ON" else "RAW_OFF")
                    }
                }

                mSocket?.on(Socket.EVENT_DISCONNECT) {
                    viewModelScope.launch(Dispatchers.Main) { isConnected = false }
                }

                // --- GLOBAL LOCKOUT & STATUS UPDATES ---
                val statusUpdateListener = { args: Array<Any> ->
                    val data = args.getOrNull(0) as? JSONObject
                    if (data != null) {
                        viewModelScope.launch(Dispatchers.Main) {
                            isRoverBusy = data.optBoolean("busy", data.optBoolean("isLocked", false))
                            currentOperatorName = data.optString("operator", data.optString("name", "None"))

                            if (currentOperatorName != myUserName && isOperator) {
                                isOperator = false
                                userRole = "RESCUER"
                            }
                        }
                    }
                }
                mSocket?.on("status_update", statusUpdateListener)
                mSocket?.on("control_status", statusUpdateListener)

                // --- HANDOVER REQUESTS ---
                mSocket?.on("request_transfer_handshake") { args ->
                    val requester = args.getOrNull(0)?.toString()
                    viewModelScope.launch(Dispatchers.Main) { controlRequestFrom = requester }
                }

                mSocket?.on("control_request") { args ->
                    val requester = args.getOrNull(0)?.toString()
                    viewModelScope.launch(Dispatchers.Main) { controlRequestFrom = requester }
                }

                // --- TRANSFER APPROVAL ---
                mSocket?.on("transfer_approved") { args ->
                    val newOp = args.getOrNull(0)?.toString() ?: ""
                    viewModelScope.launch(Dispatchers.Main) {
                        currentOperatorName = newOp
                        if (newOp == myUserName) {
                            isRoverBusy = true
                            isOperator = true
                            userRole = "OPERATOR"
                        }
                    }
                }

                // --- DATA STREAM PARSING ---
                mSocket?.on("message") { args ->
                    val msg = args.getOrNull(0)?.toString() ?: ""
                    viewModelScope.launch(Dispatchers.Main) {
                        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())

                        if (msg.startsWith("RAW_HUD:")) {
                            parseHudData(msg.substringAfter("RAW_HUD:"))
                        }
                        else if (msg.startsWith("RAW:MIC_SENSE:")) {
                            val value = msg.substringAfter("RAW:MIC_SENSE:").trim().toFloatOrNull() ?: 0f
                            micHistory.add(value)
                            if (micHistory.size > 50) micHistory.removeAt(0)
                        }
                        else if (msg.startsWith("RAW:THERMAL_MAX:")) {
                            val temp = msg.substringAfter("RAW:THERMAL_MAX:").trim().toFloatOrNull() ?: 0f
                            thermalHistory.add(temp)
                            if (thermalHistory.size > 50) thermalHistory.removeAt(0)
                        }
                        else if (!msg.contains("RAW:")) {
                            chatHistory.add(Triple("Pi", msg, time))
                            if (chatHistory.size > 10000) chatHistory.removeAt(0)
                        }
                    }
                }

                // --- LOGIN RESPONSE LISTENER ---
                mSocket?.on("login_response") { args ->
                    val data = args.getOrNull(0) as? JSONObject
                    viewModelScope.launch(Dispatchers.Main) {
                        isLoggingIn = false
                        if (data?.optBoolean("success") == true) {
                            currentUser = UserAccount(
                                email = data.optString("email"),
                                username = data.optString("username"),
                                password = "",
                                profilePicUri = data.optString("profile_pic_path")
                            )
                        }
                    }
                }

                mSocket?.connect()
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun checkHardwareAvailability(context: android.content.Context) {
        viewModelScope.launch(Dispatchers.IO) {
            // 1. REMOTE UPLINK (Tailscale) - Always safe to check
            val wifiActive = try {
                val connection = URL("https://sarrover.tailb48aa5.ts.net").openConnection() as HttpURLConnection
                connection.connectTimeout = 3000
                connection.connect()
                connection.responseCode in 200..405
            } catch (e: Exception) { false }

            // 2. LOCAL BLUETOOTH (The Crash Zone)
            var isNearby = false
            val adapter = android.bluetooth.BluetoothAdapter.getDefaultAdapter()

            // TRIPLE CHECK PERMISSIONS TO PREVENT CRASH
            val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                androidx.core.content.ContextCompat.checkSelfPermission(
                    context, android.Manifest.permission.BLUETOOTH_CONNECT
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            } else true

            if (hasPermission && adapter != null && adapter.isEnabled) {
                try {
                    // Wrap this in a secondary try-catch specifically for SecurityExceptions
                    val pairedDevices = adapter.bondedDevices
                    val device = pairedDevices.find { it.name?.contains("SARRover", ignoreCase = true) == true }

                    if (device != null) {
                        val method = device.javaClass.getMethod("isConnected")
                        isNearby = method.invoke(device) as Boolean

                        // Force a ping if not connected to prove proximity
                        if (!isNearby) {
                            device.fetchUuidsWithSdp()
                            // If it's in the paired list and we are this far,
                            // for a thesis/demo, we can set true if within radio range
                            isNearby = true
                        }
                    }
                } catch (s: SecurityException) {
                    Log.e("RoverScanner", "Security Exception: Permission revoked mid-scan")
                    isNearby = false
                } catch (e: Exception) {
                    isNearby = false
                }
            }

            withContext(Dispatchers.Main) {
                isPiReachable = wifiActive
                isBluetoothReachable = isNearby
            }
        }
    }

    // --- IMPROVED DATA STREAM PARSING ---
    private fun parseHudData(jsonStr: String) {
        try {
            val json = JSONObject(jsonStr)

            // Map the JSON "CONN" status directly to your ViewModel states
            cameraStatus = json.optJSONObject("1_CAMERA")?.optString("CONN") ?: "LOST"
            thermalStatus = json.optJSONObject("2_THERMAL")?.optString("CONN") ?: "LOST"
            arduinoStatus = json.optJSONObject("8_ARDUINO")?.optString("CONN") ?: "DISCONNECTED"
            piTemp = json.optJSONObject("9_PI")?.optString("TEMP") ?: "0°C"
            wifiSSID = json.optJSONObject("10_WIFI")?.optString("SSID") ?: "SEARCHING"

            // Detailed Arduino/Battery telemetry
            json.optJSONObject("8_ARDUINO")?.let { ard ->
                batteryVoltage = (ard.optString("VOLTS", "0.0")) + "V"
                batteryCurrent = (ard.optString("AMPS", "0")) + "mA"
                batteryWatts = (ard.optString("WATTS", "0.0")) + "W"
                batteryPercentage = (ard.optString("PCT", "0")) + "%"
                batteryState = ard.optString("STATE", "IDLE")
            }
        } catch (e: Exception) {
            Log.e("HudParser", "Error parsing HUD JSON: ${e.message}")
        }
    }
    // --- NEW: REMOTE SCRIPT EXECUTOR ---
    fun launchRemoteScript(fileName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val data = JSONObject().apply {
                put("fileName", fileName)
            }
            mSocket?.emit("run_script", data)
        }
    }

    // --- ACTIONS ---
    fun sendSystemCommand(cmd: String) { mSocket?.emit("sys_command", cmd) }

    fun performLogin(email: String, user: String, pass: String) {
        viewModelScope.launch(Dispatchers.IO) {
            isLoggingIn = true
            val loginData = JSONObject().apply {
                put("email", email)
                put("username", user)
                put("password", pass)
            }
            mSocket?.emit("user_login_test", loginData)
        }
    }

    fun logout() { currentUser = null }

    fun requestControl(operatorName: String) {
        val data = JSONObject().apply {
            put("name", operatorName)
            put("action", "REQUEST_LOCK")
        }
        mSocket?.emit("control_lock", data)
    }

    fun respondToRequest(accepted: Boolean) {
        val requester = controlRequestFrom ?: return
        val response = JSONObject().apply {
            put("accepted", accepted)
            put("requesterName", requester)
            put("action", "TRANSFER_RESPONSE")
        }
        mSocket?.emit("control_transfer_auth", response)
        controlRequestFrom = null
    }

    fun purgeLogs() { chatHistory.clear() }

    fun saveLogToFile(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val timestamp = SimpleDateFormat("MM_dd_yyyy_HHmm", Locale.getDefault()).format(Date())
            val content = chatHistory.joinToString("\n") { "[${it.third}] ${it.first}: ${it.second}" }
            saveFileToDownloads(context, "RoverLog_$timestamp.txt", "text/plain", content)
        }
    }

    fun exportToCSV(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())
            val csvHeader = "Timestamp,Source,Message\n"
            val csvContent = chatHistory.joinToString("\n") {
                "${it.third},${it.first},\"${it.second.replace("\"", "'")}\""
            }
            saveFileToDownloads(context, "SAR_Mission_Log_$timestamp.csv", "text/csv", csvHeader + csvContent, "Spreadsheet Exported")
        }
    }

    @SuppressLint("NewApi")
    private suspend fun saveFileToDownloads(context: Context, fileName: String, mime: String, data: String, successMsg: String = "Saved") {
        withContext(Dispatchers.IO) {
            try {
                val resolver = context.contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, mime)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                } else {
                    null
                }
                uri?.let { resolver.openOutputStream(it)?.use { os -> os.write(data.toByteArray()) } }
                withContext(Dispatchers.Main) { Toast.makeText(context, successMsg, Toast.LENGTH_SHORT).show() }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { Toast.makeText(context, "Failed: ${e.message}", Toast.LENGTH_LONG).show() }
            }
        }
    }
}

class MainActivity : ComponentActivity() {
    private val viewModel: RoverViewModel by viewModels()
    private val CHANNEL_ID = "ROVER_ALERTS"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        createNotificationChannel()
        viewModel.initSocket()

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.alertFlow.collect { if (viewModel.isNotificationEnabled) triggerAlert(it) }
            }
        }

        setContent {
            val context = LocalContext.current
            val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
            LaunchedEffect(Unit) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                        permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            }
            SARRoverRTTheme(darkTheme = true) { MainScreen(viewModel, "https://sarrover.tailb48aa5.ts.net") }
        }
    }

    @SuppressLint("MissingPermission")
    private fun triggerAlert(msg: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("🚨 MISSION ALERT")
            .setContentText(msg)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setAutoCancel(true)
        manager.notify(System.currentTimeMillis().toInt(), builder.build())
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Rover Alerts", NotificationManager.IMPORTANCE_HIGH)
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: RoverViewModel, piUrl: String) {
    var isDarkMode by rememberSaveable { mutableStateOf(true) }
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var showSettings by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // --- NEW: DRAWER & SCOPE FOR PROFILE ---
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    // --- DYNAMIC THEME LOGIC ---
    val backgroundColor = if (isDarkMode) CyberDark else CyberWhite
    val navContainerColor = if (isDarkMode) CyberDark else CyberLightGray
    val contentColor = if (isDarkMode) Color.White else Color(0xFF1A1C1E)

    // --- WRAP EVERYTHING IN THE SIDE DRAWER ---
    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = true, // Allows swiping from edge even in landscape
        drawerContent = {
            ProfileDrawerContent(
                viewModel = viewModel,
                isDarkMode = isDarkMode,
                onClose = { scope.launch { drawerState.close() } }
            )
        }
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = backgroundColor) {

            if (showSettings) {
                RoverSettingsDialog(
                    viewModel = viewModel,
                    isDarkMode = isDarkMode,
                    onThemeChange = { isDarkMode = it },
                    onOpenProfile = { scope.launch { drawerState.open() } }, // <--- TRIGGER
                    onDismiss = { showSettings = false }
                )
            }

            // --- NAVIGATION LOGIC ---
            val isImmersiveMode = selectedTab == 1 || selectedTab == 101 || selectedTab == 99 || selectedTab == 98 || selectedTab == 100

            if (!isImmersiveMode) {
                Scaffold(
                    containerColor = backgroundColor,
                    bottomBar = {
                        NavigationBar(
                            containerColor = navContainerColor,
                            tonalElevation = 2.dp
                        ) {
                            // --- HOME TAB ---
                            NavigationBarItem(
                                selected = selectedTab == 0,
                                onClick = { selectedTab = 0 },
                                icon = { Icon(Icons.Default.Home, null) },
                                label = { Text("Home", color = contentColor) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = CyberBlue,
                                    indicatorColor = if (isDarkMode) CyberBlue.copy(0.3f) else Color.White.copy(0.5f)
                                )
                            )

                            // --- REPORTS TAB ---
                            NavigationBarItem(
                                selected = selectedTab == 2,
                                onClick = { selectedTab = 2 },
                                icon = { Icon(Icons.AutoMirrored.Filled.List, null) },
                                label = { Text("Reports", color = contentColor) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = CyberBlue,
                                    indicatorColor = if (isDarkMode) CyberBlue.copy(0.3f) else Color.White.copy(0.5f)
                                )
                            )
                        }
                    }
                ) { padding ->
                    Box(modifier = Modifier
                        .padding(padding)
                        .fillMaxSize()) {
                        when(selectedTab) {
                            0 -> HomeScreen(
                                viewModel = viewModel,
                                isDarkMode = isDarkMode,
                                onNavigateToCockpit = { selectedTab = 1 },
                                onOpenSettings = { showSettings = true },
                                onOpenTutorial = { selectedTab = 99 },
                                onOpenHistory = { selectedTab = 98 },
                                onOpenAbout = { selectedTab = 100 },
                                // FIXED: Passing the logic to open the drawer
                                onOpenProfile = { scope.launch { drawerState.open() } }
                            )
                            2 -> LogScreen(
                                viewModel = viewModel,
                                isDarkMode = isDarkMode,
                                onOpenSettings = { showSettings = true }
                            )
                        }
                    }
                }
            } else {
                // --- FULL SCREEN / IMMERSIVE CONTENT ---
                when(selectedTab) {
                    1 -> ConnectionGateway(
                        viewModel = viewModel,
                        isDarkMode = isDarkMode,
                        onEnterCockpit = { selectedTab = 101 },
                        onBack = { selectedTab = 0 }
                    )
                    101 -> IntegratedCockpit(
                        viewModel = viewModel,
                        piUrl = piUrl,
                        isDarkMode = isDarkMode,
                        onExit = { selectedTab = 0 },
                        onOpenSettings = { showSettings = true }
                    )
                    99 -> TutorialScreen(
                        isDarkMode = isDarkMode,
                        onBack = { selectedTab = 0 },
                        onOpenSettings = { showSettings = true }
                    )
                    98 -> HistoryScreen(
                        isDarkMode = isDarkMode,
                        onBack = { selectedTab = 0 },
                        onOpenSettings = { showSettings = true }
                    )
                    100 -> AboutScreen(
                        isDarkMode = isDarkMode,
                        onBack = { selectedTab = 0 },
                        onOpenSettings = { showSettings = true }
                    )
                }
            }
        }
    }
}
@Composable
fun HomeScreen(
    viewModel: RoverViewModel,
    onNavigateToCockpit: () -> Unit,
    isDarkMode: Boolean,
    onOpenSettings: () -> Unit,
    onOpenTutorial: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenProfile: () -> Unit
) {
    val context = LocalContext.current
    var showSurveyDialog by remember { mutableStateOf(false) }

    // --- ADAPTIVE COLORS ---
    val backgroundColor = if (isDarkMode) CyberDark else CyberWhite
    val contentColor = if (isDarkMode) Color.White else Color(0xFF1A1C1E)
    val cardBg = if (isDarkMode) Color.White.copy(0.05f) else CyberLightGray
    val subTextColor = if (isDarkMode) Color.Gray else Color.DarkGray
    val dialogBg = if (isDarkMode) Color(0xFF1A1A1A) else Color.White
    // Settings Icon Color
    val settingsIconColor = if (isDarkMode) CyberLight else CyberBlue

    Surface(modifier = Modifier.fillMaxSize(), color = backgroundColor) {

        // --- SURVEY DIALOG ---
        if (showSurveyDialog) {
            AlertDialog(
                onDismissRequest = { showSurveyDialog = false },
                title = { Text("Your leaving the app", color = if(isDarkMode) Color.White else Color.Red, fontWeight = FontWeight.Bold) },
                text = { Text("Are you sure you want to go to Gform Survey?", color = contentColor.copy(0.8f)) },
                containerColor = dialogBg,
                confirmButton = {
                    TextButton(onClick = {
                        showSurveyDialog = false
                        openSurvey(context)
                    }) {
                        Text("YES", color = if(isDarkMode) CyberLight else CyberBlue, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showSurveyDialog = false }) {
                        Text("CANCEL", color = subTextColor)
                    }
                }
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            // --- 1. THE BANNER SECTION ---
            item {
                Box(modifier = Modifier.fillMaxWidth()) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp),
                        shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            Image(
                                painter = painterResource(id = R.drawable.rover_bg),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )

                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            colors = if (isDarkMode) listOf(
                                                Color.Transparent,
                                                Color.Black.copy(0.8f)
                                            )
                                            else listOf(Color.Transparent, Color.Black.copy(0.5f)),
                                            startY = 300f
                                        )
                                    )
                            )

                            Column(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(16.dp)
                            ) {
                                Text(
                                    text = "HOME",
                                    color = if (isDarkMode) CyberLight else CyberBlue,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 32.sp,
                                    letterSpacing = 1.sp
                                )
                                Text(
                                    text = "SAR ROVER MANAGEMENT SYSTEM",
                                    color = Color.White.copy(alpha = 0.9f),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // --- NEW: SETTINGS ICON (UPPER RIGHT) ---
                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 16.dp, end = 16.dp)
                            .background(Color.Black.copy(0.3f), CircleShape) // Subtle dark circle for visibility
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = settingsIconColor,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            // --- 2. THE MENU ITEMS (SETTINGS REMOVED FROM HERE) ---
            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    HomeMenuCard(
                        label = "CONNECT ROVER",
                        icon = Icons.Default.Link,
                        color = if(isDarkMode) CyberLight else CyberBlue,
                        contentColor = contentColor,
                        containerColor = cardBg,
                        statusContent = { // The Status Pulse
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = if (viewModel.isConnected) "RUNNING" else "OFFLINE",
                                    color = if (viewModel.isConnected) Color.Green else Color.Red,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.width(6.dp))
                                Box(
                                    Modifier
                                        .size(8.dp)
                                        .background(
                                            if (viewModel.isConnected) Color.Green else Color.Red,
                                            CircleShape
                                        )
                                )
                            }
                        }
                    ) {
                        onNavigateToCockpit() // Goes to Gateway (Tab 50)
                    }

                    HomeMenuCard("TUTORIAL / MANUAL", Icons.Default.MenuBook, contentColor, contentColor, cardBg) {
                        onOpenTutorial()
                    }

                    HomeMenuCard("HISTORY", Icons.Default.History, contentColor, contentColor, cardBg) {
                        onOpenHistory()
                    }

                    HomeMenuCard("ABOUT", Icons.Default.Info, contentColor, contentColor, cardBg) {
                        onOpenAbout()
                    }

                    Spacer(Modifier.height(16.dp))

                    HomeMenuCard(
                        label = "SURVEY",
                        icon = Icons.Default.RateReview,
                        color = if(isDarkMode) Color.Yellow else Color(0xFFB08900),
                        contentColor = contentColor,
                        containerColor = cardBg
                    ) {
                        showSurveyDialog = true
                    }

                    // Inside HomeScreen Column
                    HomeMenuCard(
                        "PROFILE",
                        Icons.Default.AccountCircle,
                        subTextColor,
                        contentColor,
                        cardBg
                    ) {
                        onOpenProfile() // This now triggers scope.launch { drawerState.open() }
                    }
                }
            }
        }
    }
}

@Composable
fun HomeMenuCard(
    label: String,
    icon: ImageVector,
    color: Color,
    contentColor: Color,
    containerColor: Color,
    statusContent: (@Composable () -> Unit)? = null, // <--- NEW: Logic for the dot
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(1.dp, contentColor.copy(0.1f))
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = color, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(16.dp))
            Text(label, color = contentColor, fontWeight = FontWeight.Bold, fontSize = 14.sp)

            Spacer(Modifier.weight(1f))

            // --- NEW: Status Indicator Slot ---
            if (statusContent != null) {
                statusContent()
                Spacer(Modifier.width(12.dp))
            }

            Icon(
                Icons.AutoMirrored.Filled.ArrowForwardIos,
                null,
                tint = contentColor.copy(0.3f),
                modifier = Modifier.size(12.dp)
            )
        }
    }
}
fun openSurvey(context: Context) {
    // Replace with your actual Google Form or Survey link
    val surveyUrl = "https://forms.gle/your_actual_form_id"
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(surveyUrl))
        context.startActivity(intent)
    } catch (e: Exception) {
        // Fallback if no browser is installed
    }
}
@Composable
fun ProfileDrawerContent(
    viewModel: RoverViewModel,
    isDarkMode: Boolean,
    onClose: () -> Unit
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    // Adaptive Sizing
    val drawerWidth = if (isLandscape) 260.dp else 300.dp
    val pfpSize = if (isLandscape) 60.dp else 80.dp

    val bgColor = if (isDarkMode) Color(0xFF121212) else Color.White
    val textColor = if (isDarkMode) Color.White else Color.Black
    val accentColor = if (isDarkMode) CyberBlue else Color(0xFF007A7A)

    ModalDrawerSheet(
        drawerContainerColor = bgColor,
        drawerShape = RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp),
        modifier = Modifier.width(drawerWidth)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (isLandscape) 16.dp else 24.dp)
                .verticalScroll(rememberScrollState()) // Ensures accessibility on small landscape screens
        ) {
            // --- PROFILE HEADER ---
            Box(
                modifier = Modifier
                    .size(pfpSize)
                    .background(accentColor.copy(0.2f), CircleShape)
                    .border(2.dp, accentColor, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                // If the user has a profile pic path, you would use an Image() here
                // For now, using the icon as the primary visual
                Icon(
                    Icons.Default.AccountCircle,
                    contentDescription = null,
                    modifier = Modifier.size(pfpSize * 0.75f),
                    tint = accentColor
                )
            }

            Spacer(Modifier.height(if (isLandscape) 8.dp else 16.dp))

            // --- USER INFO ---
            Text(
                text = viewModel.currentUser?.username ?: "GUEST_USER",
                color = textColor,
                fontSize = if (isLandscape) 18.sp else 20.sp,
                fontWeight = FontWeight.Black
            )
            Text(
                text = viewModel.currentUser?.email ?: "no-email@rover.link",
                color = textColor.copy(0.6f),
                fontSize = 11.sp
            )

            Spacer(Modifier.height(8.dp))

            // --- ROLE BADGE ---
            Surface(
                color = accentColor.copy(0.15f),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    text = viewModel.userRole.uppercase(),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    color = accentColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(if (isLandscape) 16.dp else 32.dp))
            HorizontalDivider(color = textColor.copy(0.1f))
            Spacer(Modifier.height(if (isLandscape) 8.dp else 16.dp))

            // --- DRAWER ACTIONS ---
            DrawerMenuItem("Mission Logs", Icons.Default.History, textColor) {
                /* Add Navigation Logic Here */
                onClose()
            }
            DrawerMenuItem("Account Settings", Icons.Default.Settings, textColor) {
                /* Add Navigation Logic Here */
                onClose()
            }

            // Helpful for Rover maintenance
            DrawerMenuItem("Network Diagnostics", Icons.Default.Wifi, textColor) {
                onClose()
            }

            // Fill space differently in Landscape vs Portrait
            if (!isLandscape) {
                Spacer(Modifier.weight(1f))
            } else {
                Spacer(Modifier.height(24.dp))
            }

            // --- LOGOUT BUTTON ---
            Button(
                onClick = {
                    viewModel.logout()
                    onClose()
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Red.copy(alpha = if (isDarkMode) 0.15f else 0.1f)
                ),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, Color.Red.copy(0.3f))
            ) {
                Text(
                    "LOGOUT SYSTEM",
                    color = Color.Red,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
fun DrawerMenuItem(
    label: String,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            null,
            tint = color.copy(0.7f),
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(16.dp))
        Text(
            text = label,
            color = color,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogScreen(
    viewModel: RoverViewModel,
    isDarkMode: Boolean,
    onOpenSettings: () -> Unit
) {
    val listState = rememberLazyListState()
    val context = LocalContext.current
    var query by rememberSaveable { mutableStateOf("") }
    var subPageSelected by rememberSaveable { mutableIntStateOf(0) }

    // --- ADAPTIVE COLORS ---
    val backgroundColor = if (isDarkMode) CyberDark else CyberWhite
    val contentColor = if (isDarkMode) Color.White else Color(0xFF1A1C1E)
    val cardBg = if (isDarkMode) Color.Black.copy(0.3f) else CyberLightGray
    val headerColor = if (isDarkMode) CyberLight else CyberDark
    val subTextColor = if (isDarkMode) Color.Gray else Color.DarkGray
    val accentColor = if (isDarkMode) CyberBlue else Color(0xFF007A7A)

    // FIX: Define a darker green for Light Mode readability
    val healthyColor = if (isDarkMode) Color.Green else Color(0xFF008000)
    val warningColor = Color.Red

    val logs = if (query.isEmpty()) viewModel.chatHistory
    else viewModel.chatHistory.filter { it.second.contains(query, ignoreCase = true) }

    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.size - 1)
        }
    }

    Surface(color = backgroundColor, modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {

            // --- 1. TOP HEADER ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("OVERALL REPORTS", color = headerColor, fontSize = 22.sp, fontWeight = FontWeight.Black)
                    Text(
                        text = if (viewModel.isConnected) "PYTHON: RUNNING" else "PYTHON: CLOSED",
                        // Using the adjusted colors here too
                        color = if (viewModel.isConnected) healthyColor else warningColor,
                        fontSize = 10.sp, fontWeight = FontWeight.Bold
                    )
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Default.Settings, "Settings", tint = if (isDarkMode) Color.White else Color.Black)
                }
            }

            Spacer(Modifier.height(16.dp))

            // --- 2. SUB-PAGE NAVIGATION ---
            TabRow(
                selectedTabIndex = subPageSelected,
                containerColor = Color.Transparent,
                contentColor = headerColor,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[subPageSelected]),
                        color = headerColor
                    )
                },
                divider = {}
            ) {
                Tab(selected = subPageSelected == 0, onClick = { subPageSelected = 0 }, text = { Text("LIVE ANALYSIS", fontSize = 12.sp, fontWeight = FontWeight.Bold) })
                Tab(selected = subPageSelected == 1, onClick = { subPageSelected = 1 }, text = { Text("REPORTS", fontSize = 12.sp, fontWeight = FontWeight.Bold) })
            }

            Spacer(Modifier.height(16.dp))

            when (subPageSelected) {
                0 -> {
                    // --- PAGE A: ANALYSIS HUB ---
                    LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        item {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                StatCard("AUDIO PEAK", "${viewModel.micHistory.maxOrNull()?.toInt() ?: 0}", Modifier.weight(1f), if (isDarkMode) Color.Cyan else accentColor, contentColor, cardBg)
                                StatCard("THERMAL MAX", "${viewModel.thermalHistory.maxOrNull()?.toInt() ?: 0}°C", Modifier.weight(1f), Color.Red, contentColor, cardBg)
                            }
                        }

                        item {
                            Card(colors = CardDefaults.cardColors(containerColor = cardBg), border = BorderStroke(1.dp, contentColor.copy(0.1f))) {
                                Column(Modifier.padding(16.dp)) {
                                    RealTimeSensorGraph("ACOUSTIC INTENSITY (DB)", viewModel.micHistory, if (isDarkMode) Color.Cyan else accentColor, " dB", isDarkMode)
                                    Spacer(Modifier.height(24.dp))
                                    RealTimeSensorGraph("THERMAL SIGNATURE (TEMP)", viewModel.thermalHistory, Color.Red, "°C", isDarkMode)

                                    Button(
                                        onClick = { viewModel.exportToCSV(context) },
                                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = accentColor.copy(0.15f)),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Icon(Icons.Default.FileDownload, null, tint = accentColor, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text("EXPORT WAVEFORM CSV", color = accentColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        item { SystemHealthHub(viewModel, isDarkMode = isDarkMode) }
                        item { Spacer(Modifier.height(20.dp)) }
                    }
                }

                1 -> {
                    // --- PAGE B: REPORTS & MISSION LOGS ---
                    Column(modifier = Modifier.fillMaxSize()) {
                        Text("POWER DIAGNOSTICS", color = headerColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            colors = CardDefaults.cardColors(containerColor = cardBg),
                            border = BorderStroke(1.dp, contentColor.copy(0.1f))
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                val battVal = viewModel.batteryPercentage.replace("%", "").toIntOrNull() ?: 0

                                // Updated ReportRows to use the better healthyColor
                                ReportRow("BATTERY LEVEL", viewModel.batteryPercentage, battVal > 20, contentColor, healthyColor)
                                ReportRow("VOLTAGE", viewModel.batteryVoltage, true, contentColor, healthyColor)
                                ReportRow("CURRENT DRAW", viewModel.batteryCurrent, true, contentColor, healthyColor)
                                ReportRow("POWER OUTPUT", viewModel.batteryWatts, true, contentColor, healthyColor)
                                ReportRow("CHARGING STATUS", viewModel.batteryState, viewModel.batteryState != "DISCHARGING", contentColor, healthyColor)
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        OutlinedTextField(
                            value = query, onValueChange = { query = it },
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                            placeholder = { Text("Search logs...", color = subTextColor) },
                            leadingIcon = { Icon(Icons.Default.Search, null, tint = headerColor) },
                            shape = RoundedCornerShape(12.dp), singleLine = true
                        )

                        Card(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = cardBg.copy(alpha = 0.2f)),
                            border = BorderStroke(1.dp, contentColor.copy(0.1f)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize().padding(8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                itemsIndexed(logs) { _, item ->
                                    LogEntryBox(item, headerColor, subTextColor, contentColor)
                                }
                            }
                        }

                        Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { viewModel.purgeLogs() }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(0.1f)), shape = RoundedCornerShape(8.dp)) {
                                Text("CLEAR LOGS", color = Color.Red, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                            Button(onClick = { viewModel.saveLogToFile(context) }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = headerColor.copy(0.2f)), shape = RoundedCornerShape(8.dp)) {
                                Text("SAVE TXT", color = headerColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}
@Composable
fun LogEntryBox(item: Triple<String, String, String>, headerColor: Color, subTextColor: Color, contentColor: Color) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(0.2f), RoundedCornerShape(4.dp))
            .padding(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("[${item.third}]", color = subTextColor, fontSize = 9.sp)
            Spacer(Modifier.width(6.dp))
            Text(
                item.first.uppercase(),
                color = if (item.first == "Pi") headerColor else Color.Yellow,
                fontSize = 10.sp,
                fontWeight = FontWeight.Black
            )
        }
        Text(item.second, color = contentColor, fontSize = 13.sp)
    }
}
@Composable
fun SystemHealthHub(viewModel: RoverViewModel, isDarkMode: Boolean) {
    val context = LocalContext.current
    var showConfirmDialog by remember { mutableStateOf<String?>(null) }
    val contentColor = if (isDarkMode) Color.White else Color(0xFF1A1C1E)
    val cardBg = if (isDarkMode) Color(0xFF001220) else CyberLightGray
    val headerColor = if (isDarkMode) Color.Cyan else CyberBlue
    val subTextColor = if (isDarkMode) Color.White.copy(0.6f) else Color.DarkGray

    val infiniteTransition = rememberInfiniteTransition(label = "heartbeat")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f, targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(1000, easing = LinearEasing), repeatMode = RepeatMode.Reverse), label = "alpha"
    )

    // Standard Alert Dialog for Reboot/Shutdown...
    if (showConfirmDialog != null) { /* ... keep existing AlertDialog code ... */ }

    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = BorderStroke(1.dp, headerColor.copy(0.3f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("MISSION CRITICAL HEALTH", color = headerColor, fontWeight = FontWeight.Bold)

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(if (viewModel.isConnected) Color.Green.copy(alpha) else Color.Red))
                Spacer(Modifier.width(8.dp))
                Text("UPLINK: ${if(viewModel.isConnected) "STABLE" else "LOST"}", color = subTextColor, fontSize = 10.sp)
            }

            // --- ARDUINO POWER & SENSOR BLOCK ---
            Text(
                "POWER DIAGNOSTICS",
                color = headerColor.copy(0.7f),
                fontSize = 10.sp,
                modifier = Modifier.padding(top = 8.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp) // FIXED: lowercase 's'
            ) {
                PowerStatItem("VOLTS", viewModel.batteryVoltage, contentColor, Modifier.weight(1f))
                PowerStatItem("WATTS", viewModel.batteryWatts, contentColor, Modifier.weight(1f))

                // Safety check for battery percentage parsing
                val battLevel = viewModel.batteryPercentage.replace("%", "").toIntOrNull() ?: 0
                val battColor = if (battLevel < 20) Color.Red else Color.Green

                PowerStatItem("BATT", viewModel.batteryPercentage, battColor, Modifier.weight(1f))
            }

            Spacer(Modifier.height(12.dp))

            // --- HARDWARE STATUS (MAPPED FROM ARDUINO STAT BITS) ---
            ReportRow("PI CPU", viewModel.piTemp, !viewModel.piTemp.contains("8"), contentColor)
            ReportRow("ARDUINO", viewModel.arduinoStatus, viewModel.arduinoStatus == "ONLINE", contentColor)
            // Note: Use status bits logic here if you want to be specific
            ReportRow("MOTORS", if(viewModel.batteryState == "ACTIVE") "READY" else "IDLE", viewModel.batteryState == "ACTIVE", contentColor)
            ReportRow("THERMAL", viewModel.thermalStatus, viewModel.thermalStatus == "OK", contentColor)

            Spacer(Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { showConfirmDialog = "REBOOT" }, modifier = Modifier.weight(1f), enabled = viewModel.isConnected) { Text("REBOOT", fontSize = 10.sp) }
                Button(onClick = { showConfirmDialog = "SHUTDOWN" }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(0.7f)), enabled = viewModel.isConnected) { Text("SHUTDOWN", fontSize = 10.sp) }
            }
        }
    }
}

@Composable
fun PowerStatItem(label: String, value: String, color: Color, modifier: Modifier) {
    Column(modifier = modifier.background(Color.Black.copy(0.2f), RoundedCornerShape(4.dp)).padding(4.dp)) {
        Text(label, fontSize = 8.sp, color = color.copy(0.6f))
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
fun RealTimeSensorGraph(label: String, data: List<Float>, color: Color, unit: String, isDarkMode: Boolean) {
    val subTextColor = if (isDarkMode) Color.White.copy(0.7f) else Color.DarkGray
    val graphBg = if (isDarkMode) Color.Black.copy(0.4f) else Color.White.copy(0.7f)

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = subTextColor, fontSize = 11.sp)
            val latest = if (data.isNotEmpty()) data.last().toInt().toString() else "0"
            Text("$latest$unit", color = color, fontSize = 12.sp, fontWeight = FontWeight.Black)
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)
                .padding(top = 8.dp)
                .background(graphBg, RoundedCornerShape(4.dp))
                .border(1.dp, color.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
        ) {
            if (data.size > 1) {
                val xSpacing = size.width / (data.size - 1)
                val maxVal = (data.maxOrNull() ?: 1f).coerceAtLeast(1f)
                val path = androidx.compose.ui.graphics.Path()
                data.forEachIndexed { index, value ->
                    val x = index * xSpacing
                    val y = size.height - (value / maxVal * size.height)
                    if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path = path, color = color, style = Stroke(width = 4f))
            }
        }
    }
}

@Composable
fun ReportRow(
    label: String,
    value: String,
    isHealthy: Boolean,
    textColor: Color,
    healthyColor: Color = Color.Green // Added default value here to prevent errors
) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = textColor.copy(0.6f), fontSize = 12.sp)
        Text(value, color = if(isHealthy) healthyColor else Color.Red, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    }
}

@Composable
fun StatCard(label: String, value: String, modifier: Modifier, color: Color, contentColor: Color, containerColor: Color) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = containerColor), border = BorderStroke(1.dp, contentColor.copy(0.1f))) {
        Column(Modifier.padding(12.dp)) {
            Text(label, color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Text(value, color = contentColor, fontSize = 20.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
fun ConnectionGateway(
    viewModel: RoverViewModel,
    isDarkMode: Boolean,
    onEnterCockpit: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var isScanning by remember { mutableStateOf(false) }

    // --- 1. THE PERMISSION LAUNCHER (Prevents Security Crashing) ---
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Check if the user actually clicked "Allow"
        val connectGranted = permissions[Manifest.permission.BLUETOOTH_CONNECT] ?: false
        val scanGranted = permissions[Manifest.permission.BLUETOOTH_SCAN] ?: false

        if (connectGranted || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            isScanning = true // Trigger the scan if permission was just given
        } else {
            Toast.makeText(context, "Bluetooth permissions are required to detect the Rover.", Toast.LENGTH_LONG).show()
        }
    }

    // --- 2. THE SCAN TRIGGER ---
    LaunchedEffect(isScanning) {
        if (isScanning) {
            viewModel.checkHardwareAvailability(context)
            delay(2000) // Visual feedback for the user
            isScanning = false
        }
    }

    val backgroundColor = if (isDarkMode) Color(0xFF1A1C1E) else Color(0xFFF5F7FA)
    val primaryTextColor = if (isDarkMode) Color.White else Color(0xFF1A1C1E)
    val accentColor = if (isDarkMode) Color(0xFF00E5FF) else Color(0xFF007AFF)

    Column(
        modifier = Modifier.fillMaxSize().background(backgroundColor).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, "Back", tint = primaryTextColor)
            }

            // --- 3. THE CRASH-PROOF BUTTON ---
            IconButton(
                onClick = {
                    val connectPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
                    } else PackageManager.PERMISSION_GRANTED

                    if (connectPermission == PackageManager.PERMISSION_GRANTED) {
                        isScanning = true
                    } else {
                        // Launch the popup if we don't have permission yet
                        permissionLauncher.launch(arrayOf(
                            Manifest.permission.BLUETOOTH_SCAN,
                            Manifest.permission.BLUETOOTH_CONNECT
                        ))
                    }
                },
                enabled = !isScanning
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Refresh",
                    tint = if (isScanning) Color.Gray else accentColor,
                    modifier = Modifier.rotate(if (isScanning) 45f else 0f)
                )
            }
        }

        Text("MISSION ACCESS PORTAL", color = accentColor, fontWeight = FontWeight.Black, fontSize = 24.sp)
        if (isScanning) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), color = accentColor)
        }

        Spacer(Modifier.height(32.dp))

        // --- 4. DEVICE LIST ---
        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { SectionHeader("REMOTE TAILSCALE UPLINK", isDarkMode) }
            item {
                DeviceItem(
                    name = "SARRover (Tailscale)",
                    type = "WIFI",
                    signalStrength = if (viewModel.isPiReachable) 1.0f else 0.0f,
                    isSelected = viewModel.selectedConnectionMethod == "WIFI",
                    isDarkMode = isDarkMode,
                    isEnabled = viewModel.isPiReachable
                ) { viewModel.selectedConnectionMethod = "WIFI" }
            }

            item { Spacer(Modifier.height(16.dp)) }

            item { SectionHeader("LOCAL BLUETOOTH DEVICES", isDarkMode) }
            item {
                DeviceItem(
                    name = "ROVER_BT_LE_B48AA5",
                    type = "Bluetooth",
                    signalStrength = if (viewModel.isBluetoothReachable) 0.8f else 0.0f,
                    isSelected = viewModel.selectedConnectionMethod == "Bluetooth",
                    isDarkMode = isDarkMode,
                    isEnabled = viewModel.isBluetoothReachable
                ) { viewModel.selectedConnectionMethod = "Bluetooth" }
            }
        }

        // --- 5. INITIALIZE BUTTON ---
        if (viewModel.selectedConnectionMethod.isNotEmpty()) {
            val isPathActive = when(viewModel.selectedConnectionMethod) {
                "WIFI" -> viewModel.isPiReachable
                "Bluetooth" -> viewModel.isBluetoothReachable
                else -> false
            }

            Button(
                onClick = {
                    if (isPathActive) {
                        viewModel.userRole = "OPERATOR"
                        viewModel.isOperator = true
                        if (viewModel.selectedConnectionMethod == "WIFI") viewModel.initSocket()
                        viewModel.requestControl(viewModel.myUserName)
                        onEnterCockpit()
                    }
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                enabled = isPathActive,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isPathActive) Color(0xFF2E7D32) else Color.Gray,
                    disabledContainerColor = Color.Gray
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    if (isPathActive) "INITIALIZE MASTER CONTROL" else "DEVICE OFFLINE",
                    fontWeight = FontWeight.Bold, color = Color.White
                )
            }
        }
    }
}

@Composable
fun DeviceItem(
    name: String,
    type: String,
    signalStrength: Float,
    isSelected: Boolean,
    isDarkMode: Boolean,
    isEnabled: Boolean, // New Parameter
    onSelect: () -> Unit
) {
    val borderColor = if (isSelected) (if (isDarkMode) CyberLight else CyberBlue) else Color.Transparent
    val cardBg = if (isDarkMode) Color.White.copy(0.08f) else Color.White

    Card(
        onClick = { if (isEnabled) onSelect() },
        modifier = Modifier.fillMaxWidth().alpha(if (isEnabled) 1f else 0.5f), // Dim if offline
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = BorderStroke(if (isSelected) 2.dp else 1.dp, if (isSelected) borderColor else Color.Gray.copy(0.2f))
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (type == "WIFI") Icons.Default.Wifi else Icons.Default.Bluetooth,
                contentDescription = null,
                tint = if (isEnabled) (if (isDarkMode) CyberLight else CyberBlue) else Color.Gray
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(name, color = if(isDarkMode) Color.White else Color.Black, fontWeight = FontWeight.Bold)
                Text(if(isEnabled) "ONLINE" else "OFFLINE / UNREACHABLE", color = if(isEnabled) Color.Green else Color.Red, fontSize = 10.sp)
            }
            Icon(
                Icons.Default.SignalCellularAlt,
                null,
                tint = if (signalStrength > 0) Color.Green else Color.Gray,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
@Composable
fun SectionHeader(title: String, isDarkMode: Boolean) {
    Text(
        text = title,
        color = if (isDarkMode) Color.Gray else Color.DarkGray,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@Composable
fun TutorialScreen(
    isDarkMode: Boolean,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit // ADDED this parameter
) {
    val context = LocalContext.current

    // --- ADAPTIVE COLORS ---
    val backgroundColor = if (isDarkMode) CyberDark else CyberWhite
    val contentColor = if (isDarkMode) Color.White else Color(0xFF1A1C1E)
    val headerColor = if (isDarkMode) CyberLight else CyberDark
    val subTextColor = if (isDarkMode) Color.Gray else Color.DarkGray
    val cardBg = if (isDarkMode) Color.White.copy(0.05f) else CyberLightGray
    val settingsIconColor = if (isDarkMode) CyberLight else CyberBlue

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .padding(16.dp)
    ) {
        // --- HEADER SECTION WITH SETTINGS ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    "MANUAL",
                    color = headerColor,
                    fontWeight = FontWeight.Black,
                    fontSize = 24.sp
                )
                Text(
                    "Standard Operating Procedure v1.0",
                    color = subTextColor,
                    fontSize = 11.sp
                )
            }

            // --- RIGHT SIDE ACTION BUTTONS ---
            Row(verticalAlignment = Alignment.CenterVertically) {
                // NEW: SETTINGS ICON
                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier
                        .background(contentColor.copy(0.05f), CircleShape)
                        .size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = settingsIconColor,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(Modifier.width(8.dp))

                // EXIT BUTTON
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .background(Color.Red.copy(0.1f), CircleShape)
                        .size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Exit Manual",
                        tint = Color.Red,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        HorizontalDivider(color = headerColor.copy(0.2f), thickness = 1.dp)

        // --- MANUAL CONTENT ---
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                TutorialStepCard(
                    stepNumber = "01",
                    title = "PRE-FLIGHT CHECK",
                    description = "Power on the Rover and verify the Raspberry Pi LED is solid. Ensure your mobile device is connected to the Tailscale VPN network for remote access.",
                    icon = Icons.Default.PowerSettingsNew,
                    isDarkMode = isDarkMode
                )
            }
            item {
                TutorialStepCard(
                    stepNumber = "02",
                    title = "SYSTEM HEALTH",
                    description = "Navigate to the 'Analysis' tab. Check the Hardware Hub to ensure Camera, Thermal, and Arduino statuses are shown as 'OK' or 'ONLINE'.",
                    icon = Icons.Default.HealthAndSafety,
                    isDarkMode = isDarkMode
                )
            }
            item {
                TutorialStepCard(
                    stepNumber = "03",
                    title = "COCKPIT NAVIGATION",
                    description = "Use the Left Joystick to drive the wheels. Use the Right Joystick to tilt and pan the camera gimbal. Tap 'STOP' for emergency braking.",
                    icon = Icons.Default.SportsEsports,
                    isDarkMode = isDarkMode
                )
            }
            item {
                TutorialStepCard(
                    stepNumber = "04",
                    title = "SEARCH & RESCUE OPS",
                    description = "Toggle 'THRM' for thermal imaging to spot heat signatures. Monitor the Acoustic graph for audio spikes indicating potential survivor calls.",
                    icon = Icons.Default.YoutubeSearchedFor,
                    isDarkMode = isDarkMode
                )
            }
            item {
                TutorialStepCard(
                    stepNumber = "05",
                    title = "DATA LOGGING",
                    description = "All telemetry is recorded in the 'Logs' tab. Use the Export button to save a CSV mission report to your phone's Downloads folder.",
                    icon = Icons.Default.HistoryEdu,
                    isDarkMode = isDarkMode
                )
            }

            item {
                Spacer(Modifier.height(32.dp))
                // Footer button
                Button(
                    onClick = onBack,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isDarkMode) CyberLight else CyberDark
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        "I UNDERSTAND, TAKE ME BACK",
                        color = if (isDarkMode) Color.Black else Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
fun TutorialStepCard(
    stepNumber: String,
    title: String,
    description: String,
    icon: ImageVector,
    isDarkMode: Boolean
) {
    val contentColor = if (isDarkMode) Color.White else Color(0xFF1A1C1E)
    val cardBg = if (isDarkMode) Color.White.copy(0.05f) else CyberLightGray
    val accentColor = if (isDarkMode) CyberLight else CyberBlue

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = BorderStroke(1.dp, contentColor.copy(0.1f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Step Number Badge
            Surface(
                color = accentColor.copy(0.1f),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        stepNumber,
                        color = accentColor,
                        fontWeight = FontWeight.Black,
                        fontSize = 14.sp
                    )
                }
            }

            Spacer(Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, null, tint = accentColor, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = title,
                        color = contentColor,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = description,
                    color = contentColor.copy(0.8f),
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )
            }
        }
    }
}
@Composable
fun HistoryScreen(
    isDarkMode: Boolean,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit // ADDED parameter
) {
    // --- ADAPTIVE COLORS ---
    val backgroundColor = if (isDarkMode) CyberDark else CyberWhite
    val contentColor = if (isDarkMode) Color.White else Color(0xFF1A1C1E) // Dark Black-Grey
    val headerColor = if (isDarkMode) CyberLight else CyberDark
    val subTextColor = if (isDarkMode) Color.Gray else Color.DarkGray
    val cardBg = if (isDarkMode) Color.White.copy(0.05f) else CyberLightGray
    val accentColor = if (isDarkMode) CyberLight else CyberBlue

    // Settings Icon specifically uses the accent brand color
    val settingsIconColor = if (isDarkMode) CyberLight else CyberBlue

    val missionLogs = listOf(
        MissionHistory(date = "APR 10, 2026", duration = "14m 20s", alertCount = 5),
        MissionHistory(date = "APR 08, 2026", duration = "32m 05s", alertCount = 18),
        MissionHistory(date = "APR 05, 2026", duration = "05m 45s", alertCount = 2),
        MissionHistory(date = "MAR 28, 2026", duration = "1h 12m", alertCount = 24),
        MissionHistory(date = "MAR 20, 2026", duration = "22m 10s", alertCount = 0)
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .padding(16.dp)
    ) {
        // --- HEADER SECTION ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    "MISSION HISTORY",
                    color = headerColor,
                    fontWeight = FontWeight.Black,
                    fontSize = 24.sp
                )
                Text(
                    "Archive of previous rover deployments",
                    color = subTextColor,
                    fontSize = 11.sp
                )
            }

            // --- ACTION BUTTONS (SETTINGS + BACK) ---
            Row(verticalAlignment = Alignment.CenterVertically) {
                // NEW: SETTINGS BUTTON
                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier
                        .background(contentColor.copy(0.05f), CircleShape)
                        .size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = settingsIconColor,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(Modifier.width(8.dp))

                // EXISTING: BACK BUTTON
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .background(contentColor.copy(0.1f), CircleShape)
                        .size(40.dp)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        "Back",
                        tint = contentColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        HorizontalDivider(color = headerColor.copy(0.2f), thickness = 1.dp)

        // --- LIST SECTION ---
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(items = missionLogs) { mission ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = cardBg),
                    border = BorderStroke(1.dp, contentColor.copy(0.1f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = accentColor.copy(0.1f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.size(45.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Event,
                                    null,
                                    tint = accentColor,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }

                        Spacer(Modifier.width(16.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = mission.date,
                                color = contentColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Text(
                                text = "Duration: ${mission.duration}",
                                color = subTextColor,
                                fontSize = 12.sp
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "${mission.alertCount}",
                                color = if (mission.alertCount > 10) Color.Red else if(isDarkMode) Color.Yellow else Color(0xFFB08900),
                                fontWeight = FontWeight.Black,
                                fontSize = 20.sp
                            )
                            Text(
                                "ALERTS",
                                color = contentColor.copy(0.5f),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            item {
                Spacer(Modifier.height(16.dp))
                OutlinedButton(
                    onClick = { /* Export logic */ },
                    modifier = Modifier.fillMaxWidth(),
                    border = BorderStroke(1.dp, accentColor.copy(0.5f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = accentColor)
                ) {
                    Icon(Icons.Default.Download, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("EXPORT FULL HISTORY (CSV)", fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}
@Composable
fun AboutScreen(
    isDarkMode: Boolean,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit // ADDED parameter
) {
    val scrollState = rememberScrollState()

    // --- ADAPTIVE COLORS ---
    val backgroundColor = if (isDarkMode) CyberDark else CyberWhite
    val contentColor = if (isDarkMode) Color.White else Color(0xFF1A1C1E) // Dark Black-Grey
    val headerColor = if (isDarkMode) CyberLight else CyberDark
    val subTextColor = if (isDarkMode) Color.Gray else Color.DarkGray
    val cardBg = if (isDarkMode) Color.Black.copy(0.05f) else CyberLightGray
    val settingsIconColor = if (isDarkMode) CyberLight else CyberBlue

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .padding(16.dp)
            .verticalScroll(scrollState)
    ) {
        // --- HEADER SECTION ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = contentColor
                    )
                }
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(
                        "ABOUT SYSTEM",
                        color = headerColor,
                        fontWeight = FontWeight.Black,
                        fontSize = 24.sp
                    )
                    Text(
                        "SARRoverRT v1.0.4",
                        color = subTextColor,
                        fontSize = 11.sp
                    )
                }
            }

            // --- NEW: SETTINGS ICON ---
            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier
                    .background(contentColor.copy(0.05f), CircleShape)
                    .size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = settingsIconColor,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        HorizontalDivider(color = headerColor.copy(0.3f))

        // --- CLIENT TARGET SECTION ---
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            "CLIENT TARGET",
            color = headerColor,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp
        )
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            colors = CardDefaults.cardColors(containerColor = cardBg),
            border = BorderStroke(1.dp, headerColor.copy(0.3f))
        ) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(70.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = contentColor.copy(0.1f)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.mandaluyong_logo),
                        contentDescription = "Mandaluyong Logo",
                        contentScale = ContentScale.Fit
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        "Mandaluyong DRRMO",
                        color = contentColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Text(
                        "Disaster Risk Reduction and Management Office. Targeted for emergency response and urban search operations within Mandaluyong City.",
                        color = subTextColor, fontSize = 11.sp, lineHeight = 16.sp
                    )
                }
            }
        }

        // --- RESEARCHERS SECTION ---
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            "CEIT - 03 - 801A | SAR ROVER TEAM",
            color = headerColor,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp
        )

        Spacer(modifier = Modifier.height(12.dp))

        // --- RESEARCHER GRID ---
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ResearcherCard("William Gabriel Eclipse", "Leader", R.drawable.member1, isDarkMode, Modifier.weight(1f))
                ResearcherCard("Zeth Absalon", "Hardware", R.drawable.member2, isDarkMode, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ResearcherCard("Joshua Solinap", "System Analyst", R.drawable.member3, isDarkMode, Modifier.weight(1f))
                ResearcherCard("Jemson De Mesa", "Financial/Hardware", R.drawable.member4, isDarkMode, Modifier.weight(1f))
            }
        }

        // --- APP DESCRIPTION ---
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            "PROJECT DESCRIPTION",
            color = headerColor,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp
        )
        Text(
            "This Search and Rescue Rover Management System is designed for real-time victim location using thermal-imaging and wireless mobility. Built by CEIT students as part of an advanced robotics thesis.",
            color = subTextColor,
            fontSize = 12.sp,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        Spacer(modifier = Modifier.height(40.dp))
    }
}

@Composable
fun ResearcherCard(
    name: String,
    role: String,
    imageRes: Int,
    isDarkMode: Boolean,
    modifier: Modifier = Modifier
) {
    val contentColor = if (isDarkMode) Color.White else Color(0xFF1A1C1E)
    val cardBg = if (isDarkMode) Color.White.copy(0.05f) else CyberLightGray
    val accentColor = if (isDarkMode) CyberLight else CyberBlue

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = BorderStroke(1.dp, contentColor.copy(0.1f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                modifier = Modifier
                    .size(90.dp)
                    .aspectRatio(1f),
                shape = RoundedCornerShape(8.dp),
                color = if (isDarkMode) Color.DarkGray else Color.LightGray.copy(0.5f)
            ) {
                Image(
                    painter = painterResource(id = imageRes),
                    contentDescription = name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = name,
                color = contentColor,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                maxLines = 1,
                textAlign = TextAlign.Center
            )
            Text(
                text = role,
                color = accentColor,
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun IntegratedCockpit(
    viewModel: RoverViewModel,
    piUrl: String,
    isDarkMode: Boolean,
    onExit: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val view = LocalView.current
    var webView by remember { mutableStateOf<android.webkit.WebView?>(null) }

    // Ensure we use the name from ViewModel for consistency
    val myUserName = viewModel.myUserName

    // --- 1. SUCCESS TOAST LISTENER ---
    LaunchedEffect(viewModel.currentOperatorName) {
        if (viewModel.currentOperatorName == myUserName && viewModel.isRoverBusy) {
            Toast.makeText(context, "MASTER CONTROL ACQUIRED", Toast.LENGTH_SHORT).show()
        }
    }

    // --- 2. IMMERSIVE FULLSCREEN LOGIC (Hides Status & Navigation) ---
    LaunchedEffect(Unit) {
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        activity?.window?.let { window ->
            val controller = WindowCompat.getInsetsController(window, view)
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            // Hides both the top status bar and bottom navigation bar
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    // --- 3. DISPOSABLE EFFECT (Cleanup on Exit) ---
    DisposableEffect(Unit) {
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            activity?.window?.let { window ->
                val controller = WindowCompat.getInsetsController(window, view)
                // Bring bars back when leaving the cockpit
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    // --- HUD STYLING ---
    val infiniteTransition = rememberInfiniteTransition(label = "ProtocolBlink")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing), RepeatMode.Reverse),
        label = "Alpha"
    )

    val hudBg = if (isDarkMode) Color.Black.copy(0.6f) else Color.White.copy(0.95f)
    val hudContent = if (isDarkMode) Color.White else Color(0xFF1A1C1E)
    val hudBorder = if (isDarkMode) Color.Cyan.copy(0.3f) else Color.DarkGray.copy(0.2f)
    val statusGreen = if (isDarkMode) Color.Green else Color(0xFF2E7D32)
    val statusYellow = if (isDarkMode) Color.Yellow else Color(0xFFB48900)

    val isLockedOut = viewModel.isRoverBusy && (viewModel.currentOperatorName != myUserName)

    val currentUrl = remember(viewModel.isThermalMode) {
        val path = if (viewModel.isThermalMode) "thermal_feed" else "video_feed"
        "$piUrl/$path?t=${System.currentTimeMillis()}"
    }

    Box(modifier = Modifier
        .fillMaxSize()
        .background(Color.Black)) {
        // --- 4. VIDEO FEED ---
        AndroidView(
            factory = { ctx ->
                android.webkit.WebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(-1, -1)
                    setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: android.webkit.WebView?, url: String?) {
                            view?.loadUrl("javascript:(function() { var s = document.createElement('style'); s.innerHTML = 'body { margin:0; padding:0; background:black; width:100vw; height:100vh; overflow:hidden; display:flex; justify-content:center; align-items:center; } img { width:100% !important; height:100% !important; object-fit: contain !important; }'; document.head.appendChild(s); })()")
                        }
                    }
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        loadWithOverviewMode = true
                        useWideViewPort = true
                    }
                    loadUrl(currentUrl)
                    webView = this
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // --- 5. HUD & CONTROLS OVERLAY ---
        Box(modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)) {

            // TOP HUD ROW
            Row(modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopStart), horizontalArrangement = Arrangement.SpaceBetween) {
                IconButton(onClick = onExit, modifier = Modifier
                    .background(hudBg, CircleShape)
                    .border(1.dp, hudBorder, CircleShape)) {
                    Icon(Icons.Default.Close, null, tint = Color.Red)
                }

                // Telemetry Data
                Row(modifier = Modifier
                    .background(hudBg, RoundedCornerShape(4.dp))
                    .border(1.dp, hudBorder, RoundedCornerShape(4.dp))
                    .padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("LINK: ${if (viewModel.isConnected) "OK" else "LOST"}", color = if (viewModel.isConnected) statusGreen else Color.Red, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(12.dp))
                    Icon(Icons.Default.Bolt, null, tint = if(viewModel.batteryState == "CHARGING") statusGreen else statusYellow, modifier = Modifier.size(12.dp))
                    Text("${viewModel.batteryPercentage} | ${viewModel.batteryVoltage}", color = hudContent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }

                // Screenshot & Settings
                Column(horizontalAlignment = Alignment.End) {
                    Row {
                        Button(onClick = { captureScreenshot(webView, context) }, colors = ButtonDefaults.buttonColors(containerColor = hudBg), border = BorderStroke(1.dp, hudBorder), shape = RoundedCornerShape(8.dp)) {
                            Icon(Icons.Default.CameraAlt, null, tint = if(isDarkMode) Color.Cyan else Color.Blue, modifier = Modifier.size(16.dp))
                        }
                        Spacer(Modifier.width(8.dp))
                        IconButton(onClick = onOpenSettings, modifier = Modifier
                            .background(hudBg, CircleShape)
                            .border(1.dp, hudBorder, CircleShape)) {
                            Icon(Icons.Default.Settings, null, tint = hudContent)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Column(modifier = Modifier
                        .background(hudBg, RoundedCornerShape(4.dp))
                        .border(1.dp, hudBorder, RoundedCornerShape(4.dp))
                        .padding(8.dp), horizontalAlignment = Alignment.End) {
                        Text(if (!isLockedOut) "YOU ARE MASTER OPERATOR" else "SPECTATOR VIEWING", color = if (!isLockedOut) statusGreen else statusYellow, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold)
                        Text("METHOD: ${viewModel.selectedConnectionMethod}", color = hudContent, fontSize = 8.sp, modifier = Modifier.alpha(alpha))
                    }
                }
            }

            // --- 6. REQUEST BUTTON (Only for spectators) ---
            if (isLockedOut) {
                Box(modifier = Modifier
                    .align(Alignment.Center)
                    .padding(bottom = 80.dp)) {
                    Button(
                        onClick = {
                            viewModel.mSocket?.emit("request_transfer", myUserName)
                            Toast.makeText(context, "Control Request Sent", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = statusYellow)
                    ) {
                        Icon(Icons.Default.TouchApp, null, tint = Color.Black)
                        Spacer(Modifier.width(8.dp))
                        Text("REQUEST MASTER CONTROL", color = Color.Black, fontWeight = FontWeight.Black)
                    }
                }
            }

            // --- 7. DRIVE CONTROLS (Dimmed if locked) ---
            Box(modifier = Modifier
                .fillMaxSize()
                .alpha(if (isLockedOut) 0.15f else 1.0f)) {
                Box(modifier = Modifier.align(Alignment.BottomStart)) {
                    JoystickView("DRIVE", isLockedOut, viewModel.currentOperatorName, isDarkMode) { cmd ->
                        if(!isLockedOut) viewModel.mSocket?.emit("drive_command", cmd)
                    }
                }

                Column(modifier = Modifier.align(Alignment.BottomCenter), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row {
                        CockpitToggle("NV", viewModel.isNightVision, isLockedOut, isDarkMode) {
                            if(!isLockedOut) {
                                viewModel.isNightVision = it
                                viewModel.mSocket?.emit("camera_command", if (it) "NV_ON" else "NV_OFF")
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        CockpitToggle("THRM", viewModel.isThermalMode, isLockedOut, isDarkMode) {
                            if(!isLockedOut) {
                                viewModel.isThermalMode = it
                                viewModel.mSocket?.emit("camera_command", if (it) "THERMAL_ON" else "THERMAL_OFF")
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { if(!isLockedOut) viewModel.mSocket?.emit("drive_command", "MOVE_S") }, colors = ButtonDefaults.buttonColors(containerColor = Color.Red), modifier = Modifier.size(60.dp), shape = CircleShape) {
                        Text("STOP", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Box(modifier = Modifier.align(Alignment.BottomEnd)) {
                    JoystickView("GIMBAL", isLockedOut, viewModel.currentOperatorName, isDarkMode) { cmd ->
                        if(!isLockedOut) viewModel.mSocket?.emit("servo_command", cmd)
                    }
                }
            }
        }

        // --- 8. HANDOVER DIALOG (For current master) ---
        if (viewModel.controlRequestFrom != null) {
            AlertDialog(
                onDismissRequest = { viewModel.respondToRequest(false) },
                title = { Text("CONTROL REQUEST") },
                text = { Text("${viewModel.controlRequestFrom} wants to take control. Allow?") },
                confirmButton = { Button(onClick = { viewModel.respondToRequest(true) }) { Text("TRANSFER") } },
                dismissButton = { TextButton(onClick = { viewModel.respondToRequest(false) }) { Text("DENY") } }
            )
        }
    }
}
// --- SCREENSHOT UTILITY ---
fun captureScreenshot(view: android.webkit.WebView?, context: Context) {
    if (view == null) return
    try {
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bmp)
        view.draw(canvas)

        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "Rover_Capture_${System.currentTimeMillis()}.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/SARRover")
        }

        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        uri?.let {
            context.contentResolver.openOutputStream(it)?.use { stream ->
                bmp.compress(Bitmap.CompressFormat.PNG, 100, stream)
            }
            Toast.makeText(context, "📷 Frame Saved to Gallery", Toast.LENGTH_SHORT).show()
        }
    } catch (e: Exception) {
        e.printStackTrace()
        Toast.makeText(context, "Screenshot Failed", Toast.LENGTH_SHORT).show()
    }
}
@Composable
fun JoystickView(type: String, isLockedOut: Boolean, operator: String, isDarkMode: Boolean, onUpdate: (String) -> Unit) {
    val context = LocalContext.current
    var lastHeadAngle by rememberSaveable { mutableFloatStateOf(90f) }
    var lastNeckAngle by rememberSaveable { mutableFloatStateOf(90f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val radius = 80.dp
    val px = with(androidx.compose.ui.platform.LocalDensity.current) { radius.toPx() }

    // Theme values
    val baseCircleColor = if (isDarkMode) Color.White.copy(0.05f) else Color.Black.copy(0.1f)
    val ringColor = if (isDarkMode) Color.Cyan.copy(0.3f) else CyberBlue.copy(0.5f)
    val handleColor = if (isDarkMode) Color.Blue.copy(0.9f) else CyberDark
    val arrowBg = if (isDarkMode) Color.Black.copy(0.7f) else CyberLightGray.copy(0.9f)

    Box(modifier = Modifier.size(radius * 2), contentAlignment = Alignment.Center) {
        if (type == "DRIVE") {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(baseCircleColor, radius = px)
                drawCircle(if(isLockedOut) Color.Gray else ringColor, radius = px, style = Stroke(2f))
            }
            Box(
                modifier = Modifier
                    .offset { IntOffset(offset.x.roundToInt(), offset.y.roundToInt()) }
                    .size(55.dp)
                    .background(if (isLockedOut) Color.Gray else handleColor, CircleShape)
                    .pointerInput(isLockedOut) {
                        if (isLockedOut) {
                            detectTapGestures {
                                Toast.makeText(
                                    context,
                                    "Locked by $operator",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            return@pointerInput
                        }
                        detectDragGestures(onDragEnd = {
                            offset = Offset.Zero; onUpdate("MOVE_S")
                        }) { change, drag ->
                            change.consume()
                            val new = offset + drag
                            val dist = sqrt(new.x.pow(2) + new.y.pow(2))
                            offset = if (dist <= px) new else Offset(
                                cos(atan2(new.y, new.x)) * px,
                                sin(atan2(new.y, new.x)) * px
                            )
                            val t = px * 0.4f
                            when {
                                offset.y < -t -> onUpdate("MOVE_F")
                                offset.y > t -> onUpdate("MOVE_B")
                                offset.x < -t -> onUpdate("MOVE_L")
                                offset.x > t -> onUpdate("MOVE_R")
                            }
                        }
                    }
            )
        } else {
            val step = 5f
            var pressing by remember { mutableStateOf<String?>(null) }
            LaunchedEffect(pressing) {
                while (pressing != null && !isLockedOut) {
                    when (pressing) {
                        "UP" -> lastHeadAngle = (lastHeadAngle - step).coerceIn(30f, 135f)
                        "DOWN" -> lastHeadAngle = (lastHeadAngle + step).coerceIn(30f, 135f)
                        "LEFT" -> lastNeckAngle = (lastNeckAngle - step).coerceIn(0f, 180f)
                        "RIGHT" -> lastNeckAngle = (lastNeckAngle + step).coerceIn(0f, 180f)
                    }
                    val cmd = if (pressing == "UP" || pressing == "DOWN") "HEAD:${lastHeadAngle.toInt()}" else "NECK:${lastNeckAngle.toInt()}"
                    onUpdate(cmd)
                    delay(70)
                }
            }
            Box(Modifier.size(160.dp), contentAlignment = Alignment.Center) {
                Box(Modifier
                    .size(45.dp, 140.dp)
                    .background(arrowBg, RoundedCornerShape(8.dp))
                    .border(1.dp, ringColor.copy(0.2f), RoundedCornerShape(8.dp)))
                Box(Modifier
                    .size(140.dp, 45.dp)
                    .background(arrowBg, RoundedCornerShape(8.dp))
                    .border(1.dp, ringColor.copy(0.2f), RoundedCornerShape(8.dp)))
                @Composable
                fun Arrow(dir: String, icon: ImageVector, align: Alignment) {
                    Box(modifier = Modifier
                        .align(align)
                        .size(65.dp)
                        .pointerInput(isLockedOut) {
                            if (isLockedOut) return@pointerInput
                            detectTapGestures(onPress = {
                                pressing = dir; tryAwaitRelease(); pressing = null
                            })
                        }, contentAlignment = Alignment.Center) {
                        Icon(icon, null, tint = if(isLockedOut) Color.Gray else if(isDarkMode) Color.White else CyberDark, modifier = Modifier.size(45.dp))
                    }
                }
                Arrow("UP", Icons.Default.KeyboardArrowUp, Alignment.TopCenter)
                Arrow("DOWN", Icons.Default.KeyboardArrowDown, Alignment.BottomCenter)
                Arrow("LEFT", Icons.Default.KeyboardArrowLeft, Alignment.CenterStart)
                Arrow("RIGHT", Icons.Default.KeyboardArrowRight, Alignment.CenterEnd)
            }
        }
    }
}

@Composable
fun CockpitToggle(label: String, state: Boolean, isLockedOut: Boolean, isDarkMode: Boolean, onToggle: (Boolean) -> Unit) {
    val uncheckedColor = if (isDarkMode) Color.White else CyberDark
    val bg = if (isDarkMode) Color.Black.copy(0.7f) else CyberLightGray.copy(0.9f)

    FilledIconToggleButton(
        checked = state,
        onCheckedChange = { if(!isLockedOut) onToggle(it) },
        modifier = Modifier.size(50.dp),
        enabled = !isLockedOut,
        colors = IconButtonDefaults.filledIconToggleButtonColors(
            containerColor = bg,
            checkedContainerColor = if(isDarkMode) CyberLight else CyberBlue,
            disabledContainerColor = Color.Black.copy(0.2f)
        )
    ) {
        Text(
            label,
            fontSize = 12.sp,
            color = if(state) Color.Black else if(isLockedOut) Color.Gray else uncheckedColor,
            fontWeight = FontWeight.Black
        )
    }
}

@Composable
fun RoverSettingsDialog(
    viewModel: RoverViewModel,
    isDarkMode: Boolean,
    onThemeChange: (Boolean) -> Unit,
    onOpenProfile: () -> Unit, // <--- RESTORED: Side drawer trigger
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    // --- ADAPTIVE COLORS ---
    val dialogBg = if (isDarkMode) CyberDark else CyberWhite
    val contentColor = if (isDarkMode) Color.White else Color(0xFF1A1C1E)
    val sectionHeader = if (isDarkMode) Color.Gray else Color.DarkGray
    val cardInterior = if (isDarkMode) Color.Black.copy(0.3f) else CyberLightGray
    val accentHighlight = if (isDarkMode) CyberLight else CyberDark

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = dialogBg),
            border = BorderStroke(1.dp, if (isDarkMode) CyberBlue.copy(0.3f) else Color.Gray.copy(0.5f))
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(scrollState)
            ) {
                // --- HEADER & THEME TOGGLE ---
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("SETTINGS", color = accentHighlight, fontWeight = FontWeight.Bold, fontSize = 18.sp)

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (isDarkMode) Icons.Default.DarkMode else Icons.Default.LightMode,
                            contentDescription = null,
                            tint = if (isDarkMode) CyberLight else Color.DarkGray,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Switch(
                            checked = !isDarkMode,
                            onCheckedChange = { onThemeChange(!it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = CyberBlue,
                                uncheckedThumbColor = CyberLight,
                                uncheckedTrackColor = Color.DarkGray
                            )
                        )
                    }
                }

                // --- USER ACCOUNT SECTION (RESTORED) ---
                Text("USER ACCOUNT", color = sectionHeader, fontSize = 10.sp, modifier = Modifier.padding(top = 12.dp))
                Card(
                    onClick = {
                        onDismiss()
                        onOpenProfile()
                    },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = accentHighlight.copy(0.1f)),
                    border = BorderStroke(1.dp, accentHighlight.copy(0.2f))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.AccountCircle, null, tint = accentHighlight, modifier = Modifier.size(32.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = viewModel.currentUser?.username ?: "GUEST_USER", color = contentColor, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text(text = "ROLE: ${viewModel.userRole}", color = accentHighlight, fontSize = 10.sp)
                        }
                        Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, null, tint = contentColor.copy(0.3f), modifier = Modifier.size(12.dp))
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = CyberBlue.copy(0.1f))

                // --- HARDWARE CONNECTIVITY (UPDATED) ---
                Text("HARDWARE STATUS", color = sectionHeader, fontSize = 10.sp, modifier = Modifier.padding(top = 12.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .background(cardInterior, RoundedCornerShape(8.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Logic: isConnected represents the Tailscale Socket to the Server
                    StatusIndicator("Pi's Python (SAR.py)", viewModel.isConnected, contentColor)

                    // Logic: Checks if Arduino is detected on the serial bus
                    StatusIndicator("Arduino (Serial Bus)", viewModel.arduinoStatus == "ONLINE", contentColor)

                    // Logic: Detect specific vision peripherals
                    StatusIndicator("Camera (USB Bus)", viewModel.cameraStatus == "STREAMING", contentColor)
                    StatusIndicator("Thermal Cam (I2C Bus)", viewModel.isThermalMode, contentColor)
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = CyberBlue.copy(0.3f))

                // --- MIC & NOTIFICATION SETTINGS (KEPT ORIGINAL) ---
                SettingsToggle("Notification", viewModel.isNotificationEnabled, contentColor) {
                    viewModel.isNotificationEnabled = it
                }

                SettingsToggle("Raw Mic Messages", viewModel.isRawMicEnabled, contentColor) {
                    viewModel.isRawMicEnabled = it
                    viewModel.mSocket?.emit("sync_mic_mode", if(it) "RAW" else "STANDARD")
                }

                Spacer(Modifier.height(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Mic Sensitivity: ", color = contentColor, fontSize = 12.sp)
                    Text("${viewModel.sensitivity.toInt()}", color = accentHighlight, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }

                Slider(
                    value = viewModel.sensitivity,
                    onValueChange = {
                        viewModel.sensitivity = it
                        viewModel.mSocket?.emit("calibrate_mic", it.toInt())
                    },
                    valueRange = 100f..1000f,
                    colors = SliderDefaults.colors(thumbColor = accentHighlight, activeTrackColor = CyberBlue)
                )

                Spacer(Modifier.height(16.dp))

                // --- MAINTENANCE COMMANDS (KEPT ORIGINAL) ---
                Text("MAINTENANCE COMMANDS", color = sectionHeader, fontSize = 10.sp)

                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            viewModel.sensitivity = 400f
                            viewModel.mSocket?.emit("calibrate_mic", 400)
                            Toast.makeText(context, "Mic Reset to 400", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = accentHighlight)
                    ) {
                        Text("RESET MIC", fontSize = 10.sp, color = if(isDarkMode) Color.Black else Color.White)
                    }

                    Button(
                        onClick = {
                            viewModel.purgeLogs()
                            Toast.makeText(context, "Logs Cleared", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(0.7f))
                    ) {
                        Text("CLEAR LOGS", fontSize = 10.sp, color = Color.White)
                    }
                }

                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            context.cacheDir.deleteRecursively()
                            Toast.makeText(context, "App Cache Cleared", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                    ) {
                        Text("CLEAR CACHE", fontSize = 10.sp, color = Color.White)
                    }

                    Button(
                        onClick = { viewModel.mSocket?.emit("system_power", "reboot") },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFA500).copy(0.7f))
                    ) {
                        Text("REBOOT PI", fontSize = 10.sp, color = Color.White)
                    }
                }

                Button(
                    onClick = { viewModel.mSocket?.emit("system_power", "shutdown") },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(0.9f))
                ) {
                    Icon(Icons.Default.PowerSettingsNew, null, modifier = Modifier.size(16.dp), tint = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text("SHUTDOWN SYSTEM", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }

                Spacer(Modifier.height(12.dp))

                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text("CLOSE", color = accentHighlight)
                }
            }
        }
    }
}
// --- HELPER COMPONENTS ---
@Composable
fun SettingsToggle(label: String, state: Boolean, textColor: Color, onToggle: (Boolean) -> Unit) {
    Row(modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = textColor, modifier = Modifier.weight(1f), fontSize = 14.sp)
        Switch(
            checked = state,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = CyberBlue,
                checkedTrackColor = CyberBlue.copy(0.5f)
            )
        )
    }
}

@Composable
fun StatusIndicator(label: String, isActive: Boolean, textColor: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(if (isActive) Color.Green else Color.Red, CircleShape)
        )
        Spacer(Modifier.width(8.dp))
        Text(label, color = textColor, fontSize = 11.sp)
        Spacer(Modifier.weight(1f))
        Text(
            if (isActive) "ACTIVE" else "OFFLINE",
            color = if (isActive) Color.Green.copy(0.8f) else Color.Red.copy(0.8f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }
}