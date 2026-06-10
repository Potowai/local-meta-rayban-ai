package com.smartview.glassai.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meta.wearable.dat.core.Wearables
import com.meta.wearable.dat.core.types.Permission
import com.meta.wearable.dat.core.types.PermissionStatus
import com.smartview.glassai.R
import com.smartview.glassai.managers.APIProviderManager
import com.smartview.glassai.managers.ProviderStatus
import com.smartview.glassai.managers.ProviderStatusChecker
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
            providerManager = APIProviderManager.getInstance(context),
            apiKeyManager = apiKeyManager
        )
    }

    // AI provider status check
    var aiStatus by remember { mutableStateOf<ProviderStatus?>(null) }
    var isCheckingAi by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!isCheckingAi) {
            isCheckingAi = true
            try {
                val pm = APIProviderManager.getInstance(context)
                aiStatus = ProviderStatusChecker.checkPrimary(pm, apiKeyManager)
            } catch (_: Exception) {
                aiStatus = null
            } finally {
                isCheckingAi = false
            }
        }
    }

    // Dialog states
    var showApiKeyDialog by remember { mutableStateOf(false) }
    var showDeviceNotConnectedDialog by remember { mutableStateOf(false) }
    var showCameraPermissionDeniedDialog by remember { mutableStateOf(false) }
    var isCheckingPermission by remember { mutableStateOf(false) }
    var showJarvisDialog by remember { mutableStateOf(false) }

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

    // ── Dialogs ──
    if (showApiKeyDialog) {
        AlertDialog(
            onDismissRequest = { showApiKeyDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Key,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
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
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text(stringResource(R.string.apikey_get_key))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showApiKeyDialog = false
                    onNavigateToSettings()
                }) {
                    Text(stringResource(R.string.go_to_settings))
                }
            }
        )
    }

    if (showDeviceNotConnectedDialog) {
        AlertDialog(
            onDismissRequest = { showDeviceNotConnectedDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Bluetooth,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
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
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
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

    if (showCameraPermissionDeniedDialog) {
        AlertDialog(
            onDismissRequest = { showCameraPermissionDeniedDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Videocam,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
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
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text(stringResource(R.string.ok))
                }
            }
        )
    }

    if (showJarvisDialog) {
        JarvisVoiceDialog(
            viewModel = assistantViewModel,
            onDismiss = { showJarvisDialog = false }
        )
    }

    // ── Main Layout ──
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
    ) {
        // ── Header ──
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 28.dp, bottom = AppSpacing.large)
                .padding(horizontal = AppSpacing.large),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = stringResource(R.string.app_name),
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.home_subtitle),
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 22.sp
            )
        }

        // ── Device Status Pill ──
        DeviceStatusPill(
            connectionState = connectionState,
            onConnect = { wearablesViewModel.startDeviceSearch() },
            onDisconnect = { wearablesViewModel.disconnect() },
            modifier = Modifier.padding(horizontal = AppSpacing.large)
        )

        Spacer(modifier = Modifier.height(AppSpacing.small))

        // ── AI Provider Compact Status ──
        AiProviderStatus(
            status = aiStatus,
            onRefresh = {
                scope.launch {
                    if (!isCheckingAi) {
                        isCheckingAi = true
                        try {
                            val pm = APIProviderManager.getInstance(context)
                            aiStatus = ProviderStatusChecker.checkPrimary(pm, apiKeyManager)
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

        // ── Feature Section Label ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppSpacing.large),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Apps,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(AppSpacing.small))
            Text(
                text = "Features",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Spacer(modifier = Modifier.height(AppSpacing.small))

        // ── Feature Bento Grid ──
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppSpacing.large),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.medium)
        ) {
            // Row 1: LiveAI + QuickVision
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.medium)
            ) {
                ModernFeatureCard(
                    modifier = Modifier.weight(1f),
                    title = stringResource(R.string.feature_liveai_title),
                    subtitle = stringResource(R.string.feature_liveai_subtitle),
                    icon = Icons.Default.Psychology,
                    featureColor = LiveAIColor,
                    onClick = {
                        if (!hasActiveDevice) { showDeviceNotConnectedDialog = true; return@ModernFeatureCard }
                        val apiKey = apiKeyManager.getAPIKey()
                        if (apiKey.isNullOrBlank()) { showApiKeyDialog = true; return@ModernFeatureCard }
                        checkCameraPermissionAndNavigate { onNavigateToLiveAI() }
                    }
                )
                ModernFeatureCard(
                    modifier = Modifier.weight(1f),
                    title = stringResource(R.string.feature_quickvision_title),
                    subtitle = stringResource(R.string.feature_quickvision_subtitle),
                    icon = Icons.Default.Visibility,
                    featureColor = QuickVisionColor,
                    onClick = {
                        if (!hasActiveDevice) { showDeviceNotConnectedDialog = true; return@ModernFeatureCard }
                        val apiKey = apiKeyManager.getAPIKey()
                        if (apiKey.isNullOrBlank()) { showApiKeyDialog = true; return@ModernFeatureCard }
                        checkCameraPermissionAndNavigate { onNavigateToVision() }
                    }
                )
            }

            // Row 2: LeanEat + WordLearn
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.medium)
            ) {
                ModernFeatureCard(
                    modifier = Modifier.weight(1f),
                    title = stringResource(R.string.lean_eat),
                    subtitle = stringResource(R.string.lean_eat_subtitle),
                    icon = Icons.Default.Restaurant,
                    featureColor = LeanEatColor,
                    onClick = onNavigateToLeanEat
                )
                ModernFeatureCard(
                    modifier = Modifier.weight(1f),
                    title = stringResource(R.string.feature_wordlearn_title),
                    subtitle = stringResource(R.string.feature_wordlearn_subtitle),
                    icon = Icons.AutoMirrored.Filled.MenuBook,
                    featureColor = WordLearnColor,
                    isPlaceholder = true,
                    onClick = {}
                )
            }

            // Wide card: LiveStream
            WideFeatureCard(
                title = stringResource(R.string.feature_livestream_title),
                subtitle = stringResource(R.string.feature_livestream_subtitle),
                icon = Icons.Default.Videocam,
                featureColor = LiveStreamColor,
                onClick = {
                    if (!hasActiveDevice) { showDeviceNotConnectedDialog = true; return@WideFeatureCard }
                    checkCameraPermissionAndNavigate { onNavigateToLiveStream() }
                }
            )

            // Wide card: RTMP
            WideFeatureCard(
                title = stringResource(R.string.feature_rtmp_title),
                subtitle = stringResource(R.string.feature_rtmp_subtitle),
                icon = Icons.Default.Stream,
                featureColor = RTMPColor,
                onClick = {
                    if (!hasActiveDevice) { showDeviceNotConnectedDialog = true; return@WideFeatureCard }
                    checkCameraPermissionAndNavigate { onNavigateToRTMPStream() }
                }
            )
        }

        Spacer(modifier = Modifier.height(AppSpacing.large))

        // ── Jarvis Section ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppSpacing.large),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.SmartToy,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(AppSpacing.small))
            Text(
                text = "Jarvis Voice Assistant",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.small)
            ) {
                JarvisActionCard(
                    modifier = Modifier.weight(1f),
                    title = "Jarvis",
                    subtitle = "Open voice assistant",
                    icon = Icons.Default.Mic,
                    gradientColor = MaterialTheme.colorScheme.primary,
                    onClick = { showJarvisDialog = true }
                )
                JarvisActionCard(
                    modifier = Modifier.weight(1f),
                    title = "Music",
                    subtitle = "Gemma 4 asks Spotify",
                    icon = Icons.Default.MusicNote,
                    gradientColor = LiveStreamColor,
                    onClick = { showJarvisDialog = true }
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.small)
            ) {
                JarvisActionCard(
                    modifier = Modifier.weight(1f),
                    title = "Timer",
                    subtitle = "Set a timer or alarm",
                    icon = Icons.Default.Alarm,
                    gradientColor = Warning,
                    onClick = { showJarvisDialog = true }
                )
                JarvisActionCard(
                    modifier = Modifier.weight(1f),
                    title = "Shortcuts",
                    subtitle = "Trigger a MacroDroid",
                    icon = Icons.Default.FlashOn,
                    gradientColor = Success,
                    onClick = { showJarvisDialog = true }
                )
            }
        }

        Spacer(modifier = Modifier.height(AppSpacing.extraLarge))
    }
}

// ─────────────────────────────────────────────────────────────
// MODERN FEATURE CARD
// ─────────────────────────────────────────────────────────────

@Composable
private fun ModernFeatureCard(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String,
    icon: ImageVector,
    featureColor: Color,
    isPlaceholder: Boolean = false,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        label = "featureScale"
    )

    ElevatedCard(
        modifier = modifier
            .scale(scale)
            .then(
                if (!isPlaceholder) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onClick
                    )
                } else Modifier
            ),
        shape = RoundedCornerShape(AppRadius.large),
        elevation = CardDefaults.elevatedCardElevation(
            defaultElevation = if (isPlaceholder) 0.dp else 2.dp
        ),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (isPlaceholder)
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            else
                MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppSpacing.medium),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Icon in colored circle
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(
                        if (isPlaceholder) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                        else featureColor.copy(alpha = 0.12f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isPlaceholder) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    else featureColor,
                    modifier = Modifier.size(26.dp)
                )
            }

            Spacer(modifier = Modifier.height(AppSpacing.small))

            // Title
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isPlaceholder) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                else MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 1
            )

            Spacer(modifier = Modifier.height(2.dp))

            // Subtitle
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = if (isPlaceholder) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 2,
                lineHeight = 16.sp
            )

            // Placeholder badge
            if (isPlaceholder) {
                Spacer(modifier = Modifier.height(AppSpacing.small))
                Surface(
                    shape = RoundedCornerShape(AppRadius.small),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                ) {
                    Text(
                        text = "Coming Soon",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// WIDE FEATURE CARD
// ─────────────────────────────────────────────────────────────

@Composable
private fun WideFeatureCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    featureColor: Color,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        label = "wideScale"
    )

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        shape = RoundedCornerShape(AppRadius.large),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.elevatedCardColors(
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
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(featureColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = featureColor,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(AppSpacing.medium))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────
// DEVICE STATUS PILL
// ─────────────────────────────────────────────────────────────

@Composable
private fun DeviceStatusPill(
    connectionState: WearablesViewModel.ConnectionState,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isConnected = connectionState is WearablesViewModel.ConnectionState.Connected
    val isRegistered = connectionState is WearablesViewModel.ConnectionState.Registered
    val isSearching = connectionState is WearablesViewModel.ConnectionState.Searching
    val isConnecting = connectionState is WearablesViewModel.ConnectionState.Connecting
    val hasDevice = isConnected || isRegistered
    val showAsConnected = hasDevice

    var showDisconnectDialog by remember { mutableStateOf(false) }

    if (showDisconnectDialog) {
        AlertDialog(
            onDismissRequest = { showDisconnectDialog = false },
            title = { Text(stringResource(R.string.settings_disconnect)) },
            text = { Text(stringResource(R.string.disconnect_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    onDisconnect()
                    showDisconnectDialog = false
                }) {
                    Text(stringResource(R.string.disconnect), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDisconnectDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // Animated connection dot
    val dotColor by animateColorAsState(
        targetValue = if (showAsConnected) Success else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
        label = "dotColor"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val dotAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dotAlpha"
    )

    // Compact pill-shaped card
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(AppRadius.extraLarge),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppSpacing.medium, vertical = AppSpacing.small)
                .then(
                    if (hasDevice) Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { showDisconnectDialog = true }
                    ) else Modifier
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Animated connection dot
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(dotColor.copy(alpha = if (showAsConnected) dotAlpha else 1f))
            )

            Spacer(modifier = Modifier.width(10.dp))

            Icon(
                imageVector = Icons.Default.Bluetooth,
                contentDescription = null,
                tint = if (showAsConnected) Success else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                modifier = Modifier.size(18.dp)
            )

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = stringResource(R.string.rayban_glasses),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )

            Text(
                text = when {
                    showAsConnected -> stringResource(R.string.connected)
                    isSearching -> stringResource(R.string.searching)
                    isConnecting -> stringResource(R.string.connecting)
                    connectionState is WearablesViewModel.ConnectionState.Error -> connectionState.message
                    else -> stringResource(R.string.disconnected)
                },
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = if (showAsConnected) Success else MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Connect button for disconnected state
            if (!showAsConnected && !isSearching && !isConnecting &&
                connectionState !is WearablesViewModel.ConnectionState.Error
            ) {
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(
                    onClick = onConnect,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(
                        stringResource(R.string.connect_glasses),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// AI PROVIDER STATUS COMPACT
// ─────────────────────────────────────────────────────────────

@Composable
private fun AiProviderStatus(
    status: ProviderStatus?,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    val statusColor = when (status) {
        is ProviderStatus.Online -> Success
        is ProviderStatus.Offline -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
    }

    val statusText = when (status) {
        is ProviderStatus.Online -> "AI Online • ${status.model} • ${status.latencyMs}ms"
        is ProviderStatus.Offline -> "${status.providerName} — ${status.reason}"
        is ProviderStatus.NotConfigured -> "No provider configured"
        null -> "Checking AI…"
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(AppRadius.extraLarge),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppSpacing.medium, vertical = AppSpacing.small),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(statusColor)
            )
            Spacer(modifier = Modifier.width(AppSpacing.small))
            Icon(
                imageVector = Icons.Default.Psychology,
                contentDescription = null,
                tint = statusColor,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(AppSpacing.small))
            Text(
                text = statusText,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = onRefresh,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Refresh",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
