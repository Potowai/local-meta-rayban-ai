package com.smartview.glassai.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.sp
import com.meta.wearable.dat.core.Wearables
import com.meta.wearable.dat.core.types.Permission
import com.meta.wearable.dat.core.types.PermissionStatus
import com.smartview.glassai.R
import com.smartview.glassai.ui.theme.*
import com.smartview.glassai.utils.APIKeyManager
import com.smartview.glassai.viewmodels.AssistantViewModel
import com.smartview.glassai.viewmodels.WearablesViewModel
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    wearablesViewModel: WearablesViewModel,
    onRequestWearablesPermission: suspend (Permission) -> PermissionStatus,
    onNavigateToLiveAI: () -> Unit,
    onNavigateToLeanEat: () -> Unit,
    onNavigateToVision: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToLiveStream: () -> Unit = {},
    onNavigateToRTMPStream: () -> Unit = {}
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    val apiKeyManager = remember { APIKeyManager.getInstance(context) }
    val connectionState by wearablesViewModel.connectionState.collectAsState()
    val hasActiveDevice by wearablesViewModel.hasActiveDevice.collectAsState()
    val assistantViewModel = remember {
        AssistantViewModel(
            providerManager = com.smartview.glassai.managers.APIProviderManager.getInstance(context),
            apiKeyManager = apiKeyManager
        )
    }

    // AI provider status check
    var aiStatus by remember { mutableStateOf<com.smartview.glassai.managers.ProviderStatus?>(null) }
    var isCheckingAi by remember { mutableStateOf(false) }

    // Check AI status on launch and every time the screen recomposes
    LaunchedEffect(Unit) {
        if (!isCheckingAi) {
            isCheckingAi = true
            try {
                val pm = com.smartview.glassai.managers.APIProviderManager.getInstance(context)
                aiStatus = com.smartview.glassai.managers.ProviderStatusChecker.checkPrimary(pm, apiKeyManager)
            } catch (_: Exception) {
                aiStatus = null
            } finally {
                isCheckingAi = false
            }
        }
    }

    // API Key dialog state
    var showApiKeyDialog by remember { mutableStateOf(false) }
    // Device not connected dialog state
    var showDeviceNotConnectedDialog by remember { mutableStateOf(false) }
    // Camera permission denied dialog state
    var showCameraPermissionDeniedDialog by remember { mutableStateOf(false) }
    // Loading state for permission check
    var isCheckingPermission by remember { mutableStateOf(false) }
    // Jarvis assistant dialog
    var showJarvisDialog by remember { mutableStateOf(false) }

    // Function to check camera permission and navigate
    fun checkCameraPermissionAndNavigate(onSuccess: () -> Unit) {
        scope.launch {
            isCheckingPermission = true
            try {
                val permission = Permission.CAMERA
                val result = Wearables.checkPermissionStatus(permission)

                val permissionStatus = result.getOrNull()
                if (permissionStatus == PermissionStatus.Granted) {
                    isCheckingPermission = false
                    onSuccess()
                    return@launch
                }

                // Request permission
                val requestedStatus = onRequestWearablesPermission(permission)
                isCheckingPermission = false

                when (requestedStatus) {
                    PermissionStatus.Granted -> onSuccess()
                    PermissionStatus.Denied -> showCameraPermissionDeniedDialog = true
                }
            } catch (e: Exception) {
                isCheckingPermission = false
                wearablesViewModel.setError("Permission check failed: ${e.message}")
            }
        }
    }

    // API Key configuration dialog
    if (showApiKeyDialog) {
        AlertDialog(
            onDismissRequest = { showApiKeyDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Key,
                    contentDescription = null,
                    tint = Primary
                )
            },
            title = {
                Text(
                    text = stringResource(R.string.api_key_required),
                    fontWeight = FontWeight.SemiBold
                )
            },
            text = {
                Text(stringResource(R.string.api_key_required_desc))
            },
            confirmButton = {
                Button(
                    onClick = {
                        showApiKeyDialog = false
                        uriHandler.openUri("https://bailian.console.aliyun.com/?apiKey=1")
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Primary)
                ) {
                    Text(stringResource(R.string.apikey_get_key))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showApiKeyDialog = false
                        onNavigateToSettings()
                    }
                ) {
                    Text(stringResource(R.string.go_to_settings))
                }
            }
        )
    }

    // Device not connected dialog
    if (showDeviceNotConnectedDialog) {
        AlertDialog(
            onDismissRequest = { showDeviceNotConnectedDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Bluetooth,
                    contentDescription = null,
                    tint = Primary
                )
            },
            title = {
                Text(
                    text = stringResource(R.string.device_required),
                    fontWeight = FontWeight.SemiBold
                )
            },
            text = {
                Text(stringResource(R.string.device_required_desc))
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeviceNotConnectedDialog = false
                        wearablesViewModel.startDeviceSearch()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Primary)
                ) {
                    Text(stringResource(R.string.connect_device))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeviceNotConnectedDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // Camera permission denied dialog
    if (showCameraPermissionDeniedDialog) {
        AlertDialog(
            onDismissRequest = { showCameraPermissionDeniedDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Videocam,
                    contentDescription = null,
                    tint = Error
                )
            },
            title = {
                Text(
                    text = stringResource(R.string.permission_required),
                    fontWeight = FontWeight.SemiBold
                )
            },
            text = {
                Text(stringResource(R.string.camera_permission_denied))
            },
            confirmButton = {
                Button(
                    onClick = { showCameraPermissionDeniedDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = Primary)
                ) {
                    Text(stringResource(R.string.ok))
                }
            }
        )
    }

    // Jarvis voice assistant dialog
    if (showJarvisDialog) {
        JarvisVoiceDialog(
            viewModel = assistantViewModel,
            onDismiss = { showJarvisDialog = false }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Primary.copy(alpha = 0.1f),
                        Secondary.copy(alpha = 0.1f)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
        ) {
            // Header
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = AppSpacing.extraLarge, bottom = AppSpacing.large),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.app_name),
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(AppSpacing.small))
                Text(
                    text = stringResource(R.string.home_subtitle),
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Device Connection Card
            DeviceStatusCard(
                connectionState = connectionState,
                onConnect = { wearablesViewModel.startDeviceSearch() },
                onDisconnect = { wearablesViewModel.disconnect() },
                modifier = Modifier.padding(horizontal = AppSpacing.large)
            )

            Spacer(modifier = Modifier.height(AppSpacing.medium))

            // AI Provider Status Card
            AiStatusCard(
                status = aiStatus,
                onRefresh = {
                    scope.launch {
                        if (!isCheckingAi) {
                            isCheckingAi = true
                            try {
                                val pm = com.smartview.glassai.managers.APIProviderManager.getInstance(context)
                                aiStatus = com.smartview.glassai.managers.ProviderStatusChecker.checkPrimary(pm, apiKeyManager)
                            } catch (_: Exception) {
                                aiStatus = null
                            } finally {
                                isCheckingAi = false
                            }
                        }
                    }
                },
                modifier = Modifier.padding(horizontal = AppSpacing.large)
            )

            Spacer(modifier = Modifier.height(AppSpacing.medium))

            // Feature Grid
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppSpacing.large),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.medium)
            ) {
                // Row 1: LiveAI + Quick Vision
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.medium)
                ) {
                    FeatureCard(
                        modifier = Modifier.weight(1f),
                        title = stringResource(R.string.feature_liveai_title),
                        subtitle = stringResource(R.string.feature_liveai_subtitle),
                        icon = Icons.Default.Psychology,
                        gradientColors = listOf(LiveAIColor, LiveAIColor.copy(alpha = 0.7f)),
                        isLoading = isCheckingPermission,
                        onClick = {
                            // First check device connection
                            if (!hasActiveDevice) {
                                showDeviceNotConnectedDialog = true
                                return@FeatureCard
                            }
                            // Then check API key
                            val apiKey = apiKeyManager.getAPIKey()
                            if (apiKey.isNullOrBlank()) {
                                showApiKeyDialog = true
                                return@FeatureCard
                            }
                            // Finally check camera permission and navigate
                            checkCameraPermissionAndNavigate { onNavigateToLiveAI() }
                        }
                    )

                    FeatureCard(
                        modifier = Modifier.weight(1f),
                        title = stringResource(R.string.feature_quickvision_title),
                        subtitle = stringResource(R.string.feature_quickvision_subtitle),
                        icon = Icons.Default.Visibility,
                        gradientColors = listOf(QuickVisionColor, QuickVisionColor.copy(alpha = 0.7f)),
                        isLoading = isCheckingPermission,
                        onClick = {
                            // Check device connection first
                            if (!hasActiveDevice) {
                                showDeviceNotConnectedDialog = true
                                return@FeatureCard
                            }
                            // Check API key
                            val apiKey = apiKeyManager.getAPIKey()
                            if (apiKey.isNullOrBlank()) {
                                showApiKeyDialog = true
                                return@FeatureCard
                            }
                            // Navigate to Vision (Quick Vision)
                            checkCameraPermissionAndNavigate { onNavigateToVision() }
                        }
                    )
                }

                // Row 2: LeanEat + WordLearn
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.medium)
                ) {
                    FeatureCard(
                        modifier = Modifier.weight(1f),
                        title = stringResource(R.string.lean_eat),
                        subtitle = stringResource(R.string.lean_eat_subtitle),
                        icon = Icons.Default.Restaurant,
                        gradientColors = listOf(LeanEatColor, LeanEatColor.copy(alpha = 0.7f)),
                        onClick = onNavigateToLeanEat
                    )

                    FeatureCard(
                        modifier = Modifier.weight(1f),
                        title = stringResource(R.string.feature_wordlearn_title),
                        subtitle = stringResource(R.string.feature_wordlearn_subtitle),
                        icon = Icons.AutoMirrored.Filled.MenuBook,
                        gradientColors = listOf(WordLearnColor, WordLearnColor.copy(alpha = 0.7f)),
                        isPlaceholder = true,
                        onClick = {}
                    )
                }

                // Row 3: LiveStream (wide card)
                FeatureCardWide(
                    title = stringResource(R.string.feature_livestream_title),
                    subtitle = stringResource(R.string.feature_livestream_subtitle),
                    icon = Icons.Default.Videocam,
                    gradientColors = listOf(LiveStreamColor, LiveStreamColor.copy(alpha = 0.7f)),
                    isLoading = isCheckingPermission,
                    onClick = {
                        // Check device connection first
                        if (!hasActiveDevice) {
                            showDeviceNotConnectedDialog = true
                            return@FeatureCardWide
                        }
                        // Check camera permission and navigate
                        checkCameraPermissionAndNavigate { onNavigateToLiveStream() }
                    }
                )

                // Row 4: RTMP Streaming (wide card) - Experimental
                FeatureCardWide(
                    title = stringResource(R.string.feature_rtmp_title),
                    subtitle = stringResource(R.string.feature_rtmp_subtitle),
                    icon = Icons.Default.Stream,
                    gradientColors = listOf(Color(0xFF9C27B0), Color(0xFF9C27B0).copy(alpha = 0.7f)),
                    isLoading = isCheckingPermission,
                    onClick = {
                        // Check device connection first
                        if (!hasActiveDevice) {
                            showDeviceNotConnectedDialog = true
                            return@FeatureCardWide
                        }
                        // Check camera permission and navigate
                        checkCameraPermissionAndNavigate { onNavigateToRTMPStream() }
                    }
                )
            }

            // Jarvis Assistant — voice AI action cards
            Spacer(modifier = Modifier.height(AppSpacing.medium))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = AppSpacing.large, end = AppSpacing.large, bottom = AppSpacing.small),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.SmartToy,
                    contentDescription = null,
                    tint = Secondary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(AppSpacing.small))
                Text(
                    text = "Jarvis Voice Assistant",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = "Ask Gemma 4 for anything — music, timer, answers, macros",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = AppSpacing.large, bottom = AppSpacing.medium)
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppSpacing.large),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.small)
            ) {
                // Row 1: General assistant + Music
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.small)
                ) {
                    JarvisActionCard(
                        modifier = Modifier.weight(1f),
                        title = "Jarvis",
                        subtitle = "Open voice assistant",
                        icon = Icons.Default.Mic,
                        gradientColor = Secondary,
                        onClick = { showJarvisDialog = true }
                    )
                    JarvisActionCard(
                        modifier = Modifier.weight(1f),
                        title = "Music",
                        subtitle = "Gemma 4 asks Spotify",
                        icon = Icons.Default.MusicNote,
                        gradientColor = Color(0xFFE91E63),
                        onClick = { showJarvisDialog = true }
                    )
                }
                // Row 2: Timer + Search
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.small)
                ) {
                    JarvisActionCard(
                        modifier = Modifier.weight(1f),
                        title = "Timer",
                        subtitle = "Set a timer or alarm",
                        icon = Icons.Default.Alarm,
                        gradientColor = Color(0xFFFF9800),
                        onClick = { showJarvisDialog = true }
                    )
                    JarvisActionCard(
                        modifier = Modifier.weight(1f),
                        title = "Shortcuts",
                        subtitle = "Trigger a MacroDroid",
                        icon = Icons.Default.FlashOn,
                        gradientColor = Color(0xFF4CAF50),
                        onClick = { showJarvisDialog = true }
                    )
                }
            }

            Spacer(modifier = Modifier.height(AppSpacing.extraLarge))
        }
    }
}

@Composable
private fun FeatureCard(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String,
    icon: ImageVector,
    gradientColors: List<Color>,
    isPlaceholder: Boolean = false,
    isLoading: Boolean = false,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        label = "scale"
    )

    // Use Box with explicit gradient background - no inner containers with backgrounds
    Box(
        modifier = modifier
            .height(180.dp)
            .scale(scale)
            .clip(RoundedCornerShape(AppRadius.large))
            .background(
                brush = Brush.linearGradient(
                    colors = gradientColors,
                    start = androidx.compose.ui.geometry.Offset(0f, 0f),
                    end = androidx.compose.ui.geometry.Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
                )
            )
            .then(
                if (!isPlaceholder) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onClick
                    )
                } else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Icon with circle background
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(modifier = Modifier.height(AppSpacing.medium))

            // Title
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(AppSpacing.extraSmall))

            // Subtitle
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.8f),
                textAlign = TextAlign.Center
            )

            // Coming Soon badge for placeholders
            if (isPlaceholder) {
                Spacer(modifier = Modifier.height(AppSpacing.small))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(AppRadius.small))
                        .background(Color.White.copy(alpha = 0.2f))
                        .padding(horizontal = AppSpacing.medium, vertical = AppSpacing.extraSmall)
                ) {
                    Text(
                        text = "Coming Soon",
                        fontSize = 10.sp,
                        color = Color.White.copy(alpha = 0.9f)
                    )
                }
            }
        }
    }
}

@Composable
private fun FeatureCardWide(
    title: String,
    subtitle: String,
    icon: ImageVector,
    gradientColors: List<Color>,
    isLoading: Boolean = false,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        label = "scale"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(RoundedCornerShape(AppRadius.large))
            .background(
                Brush.horizontalGradient(
                    colors = gradientColors
                )
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppSpacing.large),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon with circle background
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(modifier = Modifier.width(AppSpacing.large))

            // Text
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(AppSpacing.extraSmall))
                Text(
                    text = subtitle,
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.8f)
                )
            }

            // Arrow
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
private fun DeviceStatusCard(
    connectionState: WearablesViewModel.ConnectionState,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier
) {
    // iOS doesn't distinguish between Registered and Connected on home screen
    // Both states mean the device is available - show as "Connected"
    val isConnected = connectionState is WearablesViewModel.ConnectionState.Connected
    val isRegistered = connectionState is WearablesViewModel.ConnectionState.Registered
    val isSearching = connectionState is WearablesViewModel.ConnectionState.Searching
    val isConnecting = connectionState is WearablesViewModel.ConnectionState.Connecting
    val hasDevice = isConnected || isRegistered

    // Treat both Registered and Connected as "connected" for UI purposes (matching iOS)
    val showAsConnected = hasDevice

    var showDisconnectDialog by remember { mutableStateOf(false) }

    // Disconnect confirmation dialog
    if (showDisconnectDialog) {
        AlertDialog(
            onDismissRequest = { showDisconnectDialog = false },
            title = { Text(stringResource(R.string.settings_disconnect)) },
            text = { Text(stringResource(R.string.disconnect_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDisconnect()
                        showDisconnectDialog = false
                    }
                ) {
                    Text(stringResource(R.string.disconnect), color = Error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDisconnectDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(AppRadius.large),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        onClick = if (hasDevice) { { showDisconnectDialog = true } } else { {} }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppSpacing.medium),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        if (showAsConnected) Success.copy(alpha = 0.1f)
                        else Primary.copy(alpha = 0.1f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Bluetooth,
                    contentDescription = null,
                    tint = if (showAsConnected) Success else Primary,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(AppSpacing.medium))

            // Text
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.rayban_glasses),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = when {
                        showAsConnected -> stringResource(R.string.connected)
                        isSearching -> stringResource(R.string.searching)
                        isConnecting -> stringResource(R.string.connecting)
                        connectionState is WearablesViewModel.ConnectionState.Error -> connectionState.message
                        else -> stringResource(R.string.disconnected)
                    },
                    fontSize = 14.sp,
                    color = if (showAsConnected) Success else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Connect Button or Status
            when {
                showAsConnected -> {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(AppRadius.small))
                            .background(Success.copy(alpha = 0.1f))
                            .padding(horizontal = AppSpacing.medium, vertical = AppSpacing.small)
                    ) {
                        Text(
                            text = stringResource(R.string.connected),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = Success
                        )
                    }
                }
                isSearching || isConnecting -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = Primary
                    )
                }
                else -> {
                    Button(
                        onClick = onConnect,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Primary
                        ),
                        shape = RoundedCornerShape(AppRadius.small)
                    ) {
                        Text(stringResource(R.string.connect_glasses))
                    }
                }
            }
        }
    }
}

@Composable
private fun AiStatusCard(
    status: com.smartview.glassai.managers.ProviderStatus?,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(AppRadius.large),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppSpacing.medium),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(
                        when (status) {
                            is com.smartview.glassai.managers.ProviderStatus.Online -> Success
                            is com.smartview.glassai.managers.ProviderStatus.Offline -> Error
                            else -> MaterialTheme.colorScheme.outline
                        }
                    )
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "AI Provider",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                when (status) {
                    is com.smartview.glassai.managers.ProviderStatus.Online -> {
                        Text(
                            text = "${status.providerName} • ${status.model} • ${status.latencyMs}ms",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                    is com.smartview.glassai.managers.ProviderStatus.Offline -> {
                        Text(
                            text = "${status.providerName} — ${status.reason}",
                            fontSize = 12.sp,
                            color = Error,
                            maxLines = 1
                        )
                    }
                    null -> {
                        Text(
                            text = "Checking…",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    is com.smartview.glassai.managers.ProviderStatus.NotConfigured -> {
                        Text(
                            text = "No provider configured",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            IconButton(
                onClick = onRefresh,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Refresh",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
