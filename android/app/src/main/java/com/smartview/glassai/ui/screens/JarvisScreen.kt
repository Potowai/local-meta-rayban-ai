package com.smartview.glassai.ui.screens

import android.speech.SpeechRecognizer
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.smartview.glassai.R
import com.smartview.glassai.ui.theme.*
import com.smartview.glassai.viewmodels.AssistantViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * A fullscreen dialog that hosts the "Jarvis" voice assistant.
 *
 * Shows a pulsing mic button, the transcribed text, the AI response,
 * and action confirmation buttons.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JarvisVoiceDialog(
    viewModel: AssistantViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isListening by viewModel.isListening.collectAsState()
    val isThinking by viewModel.isThinking.collectAsState()
    val transcript by viewModel.transcript.collectAsState()
    val responseText by viewModel.response.collectAsState()
    val pendingAction by viewModel.pendingAction.collectAsState()

    // Pulsing animation for the mic
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Dialog(
        onDismissRequest = {
            viewModel.stopListening()
            onDismiss()
        },
        content = {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    MaterialTheme.colorScheme.surface,
                    RoundedCornerShape(28.dp)
                )
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Title
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.SmartToy,
                    contentDescription = null,
                    tint = Secondary,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(AppSpacing.small))
                Text(
                    text = "Jarvis",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Mic button area
            Box(
                modifier = Modifier.size(120.dp),
                contentAlignment = Alignment.Center
            ) {
                // Outer glow ring when listening
                if (isListening) {
                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .scale(pulseScale)
                            .clip(CircleShape)
                            .background(Primary.copy(alpha = 0.15f))
                    )
                }

                // Mic button
                FilledIconButton(
                    onClick = {
                        if (isListening) {
                            viewModel.stopListening()
                        } else {
                            if (SpeechRecognizer.isRecognitionAvailable(context)) {
                                viewModel.startListening(context)
                            }
                        }
                    },
                    modifier = Modifier.size(72.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = if (isListening) Primary else MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Icon(
                        imageVector = if (isListening) Icons.Default.Mic else Icons.Default.MicNone,
                        contentDescription = "Voice",
                        modifier = Modifier.size(32.dp),
                        tint = if (isListening) Color.White else MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Status text
            Text(
                text = when {
                    isListening -> "Listening…"
                    isThinking -> "Thinking…"
                    else -> "Tap to speak"
                },
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Transcript (what you said)
            if (transcript.isNotEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = transcript,
                        modifier = Modifier.padding(16.dp),
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // AI response
            val responseTextVal = responseText
            if (responseTextVal != null) {
                Surface(
                    color = Primary.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = responseTextVal,
                        modifier = Modifier.padding(16.dp),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // Action confirmation
            val pendingActionVal = pendingAction
            if (pendingActionVal != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    color = Secondary.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.FlashOn,
                            contentDescription = null,
                            tint = Secondary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = pendingActionVal.label,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    viewModel.executeAction(context, pendingActionVal)
                                    viewModel.clearPendingAction()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Secondary)
                            ) { Text("Execute") }
                            OutlinedButton(
                                onClick = {
                                    viewModel.clearPendingAction()
                                }
                            ) { Text("Cancel") }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Close button
            TextButton(onClick = {
                viewModel.stopListening()
                onDismiss()
            }) {
                Text("Close", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
)

}

/**
 * A compact Jarvis action card used on the home screen.
 * Each card triggers the voice dialog with a pre-filled context.
 */
@Composable
fun JarvisActionCard(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String,
    icon: ImageVector,
    gradientColor: Color,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        label = "jarvisScale"
    )

    Box(
        modifier = modifier
            .height(110.dp)
            .scale(scale)
            .clip(RoundedCornerShape(16.dp))
            .background(gradientColor.copy(alpha = 0.15f))
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(gradientColor.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = gradientColor,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
