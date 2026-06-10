package com.smartview.glassai.ui.screens

import android.graphics.Bitmap
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.smartview.glassai.R
import com.smartview.glassai.models.FoodEntry
import com.smartview.glassai.models.FoodNutritionResponse
import com.smartview.glassai.ui.components.*
import com.smartview.glassai.ui.theme.*
import com.smartview.glassai.viewmodels.LeanEatViewModel

/**
 * LeanEat screen — capture food photos with glasses, analyze via AI,
 * display nutrition info, and browse history.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeanEatScreen(
    viewModel: LeanEatViewModel = viewModel(),
    capturedPhoto: Bitmap? = null,
    onBackClick: () -> Unit,
    onTakePhoto: () -> Unit,
    onPhotoConsumed: () -> Unit = {}
) {
    val viewState by viewModel.viewState.collectAsState()
    val capturedImage by viewModel.capturedImage.collectAsState()
    val nutritionResult by viewModel.nutritionResult.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val isAnalyzing by viewModel.isAnalyzing.collectAsState()
    val foodHistory by viewModel.foodHistory.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current

    // When the glasses capture a photo, feed it into the ViewModel
    LaunchedEffect(capturedPhoto) {
        capturedPhoto?.let { photo ->
            if (viewState is LeanEatViewModel.ViewState.Idle ||
                viewState is LeanEatViewModel.ViewState.Result ||
                viewState is LeanEatViewModel.ViewState.Error
            ) {
                viewModel.setCapturedImage(photo)
                onPhotoConsumed()
            }
        }
    }

    // Fallback toast
    LaunchedEffect(viewModel) {
        viewModel.fallbackNotice.collect { notice ->
            val msg = if (notice.primaryName.contains("Local", ignoreCase = true)) {
                context.getString(R.string.fallback_used_toast_local_cloud)
            } else {
                context.getString(R.string.fallback_used_toast_cloud_local)
            }
            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show()
        }
    }

    // Save confirmation snackbar
    LaunchedEffect(viewModel) {
        viewModel.saveConfirmation.collect { msg ->
            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.lean_eat),
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = LeanEatColor.copy(alpha = 0.1f)
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Error banner
            errorMessage?.let { error ->
                ErrorMessage(
                    message = error,
                    onDismiss = { viewModel.clearError() },
                    modifier = Modifier.padding(horizontal = AppSpacing.medium, vertical = AppSpacing.small)
                )
            }

            // ── Main content area ──
            when (viewState) {
                is LeanEatViewModel.ViewState.Idle -> {
                    IdleContent(
                        onTakePhoto = onTakePhoto,
                        lastImage = null
                    )
                }
                is LeanEatViewModel.ViewState.Capturing -> {
                    CapturedImageContent(
                        image = capturedImage,
                        onAnalyze = { viewModel.analyzeFood() },
                        onRetake = onTakePhoto
                    )
                }
                is LeanEatViewModel.ViewState.Analyzing -> {
                    AnalyzingContent(image = capturedImage)
                }
                is LeanEatViewModel.ViewState.Result -> {
                    nutritionResult?.let { result ->
                        ResultContent(
                            image = capturedImage,
                            result = result,
                            onSave = { viewModel.saveImageToGallery() },
                            onRetake = { viewModel.retakePhoto() },
                            onTakePhoto = onTakePhoto
                        )
                    }
                }
                is LeanEatViewModel.ViewState.Error -> {
                    ErrorContent(
                        message = (viewState as LeanEatViewModel.ViewState.Error).message,
                        onRetry = { viewModel.analyzeFood() },
                        onRetake = { viewModel.retakePhoto() },
                        onTakePhoto = onTakePhoto
                    )
                }
            }

            // ── Food History Section ──
            if (foodHistory.isNotEmpty()) {
                FoodHistorySection(
                    entries = foodHistory,
                    onDeleteEntry = { viewModel.deleteFoodEntry(it) }
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// IDLE
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun IdleContent(
    onTakePhoto: () -> Unit,
    lastImage: Bitmap?
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(AppSpacing.large),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(AppSpacing.extraLarge))

        // Icon
        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape)
                .background(LeanEatColor.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Restaurant,
                contentDescription = null,
                modifier = Modifier.size(52.dp),
                tint = LeanEatColor
            )
        }

        Spacer(modifier = Modifier.height(AppSpacing.large))

        Text(
            text = stringResource(R.string.capture_food),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(AppSpacing.small))
        Text(
            text = stringResource(R.string.capture_food_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )

        Spacer(modifier = Modifier.height(AppSpacing.extraLarge))

        // Prominent "Take Photo with Glasses" button
        GradientButton(
            text = "Take Photo with Glasses",
            onClick = onTakePhoto,
            gradientColors = listOf(LeanEatColor, LeanEatColorLight),
            modifier = Modifier.fillMaxWidth(0.8f)
        )

        Spacer(modifier = Modifier.height(AppSpacing.medium))

        Text(
            text = "Use your Ray-Ban Meta glasses to capture the food",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
        )

        Spacer(modifier = Modifier.height(AppSpacing.extraLarge))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// CAPTURED
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CapturedImageContent(
    image: Bitmap?,
    onAnalyze: () -> Unit,
    onRetake: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(AppSpacing.medium)
    ) {
        // Image preview
        image?.let { bitmap ->
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Captured food",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .clip(RoundedCornerShape(AppRadius.large)),
                contentScale = ContentScale.Crop
            )
        } ?: Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
                .clip(RoundedCornerShape(AppRadius.large))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Text("No image", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
        }

        Spacer(modifier = Modifier.height(AppSpacing.large))

        // Captured label
        Surface(
            shape = RoundedCornerShape(AppRadius.small),
            color = LeanEatColor.copy(alpha = 0.1f)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppSpacing.medium, vertical = AppSpacing.small),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = LeanEatColor,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(AppSpacing.small))
                Text(
                    text = "Photo captured! Now analyze with AI",
                    style = MaterialTheme.typography.bodySmall,
                    color = LeanEatColor
                )
            }
        }

        Spacer(modifier = Modifier.height(AppSpacing.medium))

        // Action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.medium)
        ) {
            OutlinedButton(
                onClick = onRetake,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(AppSpacing.small))
                Text("Retake")
            }

            // Analyze with AI button (gradient)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(AppRadius.medium))
                    .background(Brush.horizontalGradient(listOf(LeanEatColor, LeanEatColorLight)))
                    .clickable(onClick = onAnalyze)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(AppSpacing.small))
                    Text(
                        text = "Analyze with AI",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ANALYZING
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AnalyzingContent(image: Bitmap?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(AppSpacing.medium),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        image?.let { bitmap ->
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Analyzing food",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .clip(RoundedCornerShape(AppRadius.large)),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.height(AppSpacing.extraLarge))
        }

        LoadingIndicator(message = stringResource(R.string.analyzing_nutrition))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// RESULT
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ResultContent(
    image: Bitmap?,
    result: FoodNutritionResponse,
    onSave: () -> Unit,
    onRetake: () -> Unit,
    onTakePhoto: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(AppSpacing.medium)
    ) {
        // Image
        image?.let { bitmap ->
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Food",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(AppRadius.large)),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.height(AppSpacing.medium))
        }

        // ── Health Score Card ──
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(AppRadius.large),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(AppSpacing.large),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.health_score),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${result.totalCalories} kcal total",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = result.healthScoreText,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = result.healthScoreColor
                    )
                }
                HealthScoreRing(score = result.healthScore, modifier = Modifier.size(80.dp))
            }
        }

        Spacer(modifier = Modifier.height(AppSpacing.medium))

        // ── Nutrition Breakdown ──
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(AppRadius.large),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(AppSpacing.large)
            ) {
                Text(
                    text = stringResource(R.string.nutrition_breakdown),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(AppSpacing.medium))

                NutritionBar(
                    label = stringResource(R.string.protein),
                    value = result.totalProtein,
                    maxValue = 50.0,
                    color = NutritionProtein
                )
                Spacer(modifier = Modifier.height(AppSpacing.small))
                NutritionBar(
                    label = stringResource(R.string.carbs),
                    value = result.totalCarbs,
                    maxValue = 100.0,
                    color = NutritionCarbs
                )
                Spacer(modifier = Modifier.height(AppSpacing.small))
                NutritionBar(
                    label = stringResource(R.string.fat),
                    value = result.totalFat,
                    maxValue = 50.0,
                    color = NutritionFat
                )
            }
        }

        // ── Food Items ──
        if (result.foods.isNotEmpty()) {
            Spacer(modifier = Modifier.height(AppSpacing.medium))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(AppRadius.large),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(AppSpacing.large)
                ) {
                    Text(
                        text = stringResource(R.string.food_items),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(AppSpacing.small))

                    result.foods.forEachIndexed { index, food ->
                        FoodItemRow(
                            name = food.name,
                            portion = food.portion,
                            calories = food.calories,
                            rating = food.healthRating
                        )
                        if (index < result.foods.lastIndex) {
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = AppSpacing.small),
                                color = MaterialTheme.colorScheme.outlineVariant
                            )
                        }
                    }
                }
            }
        }

        // ── Suggestions ──
        if (result.suggestions.isNotEmpty()) {
            Spacer(modifier = Modifier.height(AppSpacing.medium))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(AppRadius.large),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(AppSpacing.large)
                ) {
                    Text(
                        text = stringResource(R.string.suggestions),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(AppSpacing.small))

                    result.suggestions.forEach { suggestion ->
                        Row(
                            modifier = Modifier.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lightbulb,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = Warning
                            )
                            Spacer(modifier = Modifier.width(AppSpacing.small))
                            Text(
                                text = suggestion,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(AppSpacing.large))

        // ── Action buttons ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.medium)
        ) {
            OutlinedButton(
                onClick = onSave,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(AppSpacing.small))
                Text(stringResource(R.string.save))
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(AppRadius.medium))
                    .background(Brush.horizontalGradient(listOf(LeanEatColor, LeanEatColorLight)))
                    .clickable(onClick = onTakePhoto)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.CameraAlt,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(AppSpacing.small))
                    Text(
                        text = "New Photo",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(AppSpacing.large))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// FOOD ITEM ROW
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun FoodItemRow(
    name: String,
    portion: String,
    calories: Int,
    rating: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            if (portion.isNotBlank()) {
                Text(
                    text = portion,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "$calories kcal",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Surface(
                shape = RoundedCornerShape(AppRadius.small),
                color = when (rating) {
                    "优秀" -> HealthExcellent.copy(alpha = 0.15f)
                    "良好" -> HealthGood.copy(alpha = 0.15f)
                    "一般" -> HealthFair.copy(alpha = 0.15f)
                    else -> HealthPoor.copy(alpha = 0.15f)
                }
            ) {
                Text(
                    text = rating,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = when (rating) {
                        "优秀" -> HealthExcellent
                        "良好" -> HealthGood
                        "一般" -> HealthFair
                        else -> HealthPoor
                    },
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ERROR
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ErrorContent(
    message: String,
    onRetry: () -> Unit,
    onRetake: () -> Unit,
    onTakePhoto: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(AppSpacing.large),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(AppSpacing.extraLarge))

        Icon(
            imageVector = Icons.Default.Error,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = Error
        )
        Spacer(modifier = Modifier.height(AppSpacing.medium))
        Text(
            text = "Analysis Failed",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(AppSpacing.small))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
        Spacer(modifier = Modifier.height(AppSpacing.large))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(onClick = { onRetake() }) {
                Text(stringResource(R.string.retake))
            }
            Spacer(modifier = Modifier.width(AppSpacing.medium))

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(AppRadius.medium))
                    .background(Brush.horizontalGradient(listOf(LeanEatColor, LeanEatColorLight)))
                    .clickable(onClick = onRetry)
                    .padding(horizontal = 24.dp, vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Retry",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(modifier = Modifier.height(AppSpacing.medium))

        // Also offer to take a new photo
        TextButton(onClick = onTakePhoto) {
            Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(AppSpacing.small))
            Text("Take New Photo with Glasses")
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// FOOD HISTORY SECTION
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun FoodHistorySection(
    entries: List<FoodEntry>,
    onDeleteEntry: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(AppSpacing.medium)
    ) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(modifier = Modifier.height(AppSpacing.small))

        // Section header (collapsible)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(vertical = AppSpacing.small),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = null,
                    tint = LeanEatColor,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(AppSpacing.small))
                Text(
                    text = "Food History (${entries.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (expanded) "Collapse" else "Expand",
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }

        // History entries
        AnimatedVisibility(visible = expanded) {
            Column {
                entries.take(20).forEach { entry ->
                    FoodHistoryItem(
                        entry = entry,
                        onDelete = { onDeleteEntry(entry.id) }
                    )
                    if (entry != entries.take(20).last()) {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 4.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                    }
                }

                if (entries.size > 20) {
                    Text(
                        text = "Showing 20 of ${entries.size} entries",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        modifier = Modifier.padding(top = AppSpacing.small)
                    )
                }
            }
        }
    }
}

@Composable
private fun FoodHistoryItem(
    entry: FoodEntry,
    onDelete: () -> Unit
) {
    var showDetails by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    // Delete confirmation dialog
    if (showDeleteConfirm) {
        ConfirmDialog(
            title = "Delete Entry",
            message = "Remove this food entry from history?",
            confirmText = "Delete",
            onConfirm = {
                showDeleteConfirm = false
                onDelete()
            },
            onDismiss = { showDeleteConfirm = false }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppRadius.medium))
            .clickable { showDetails = !showDetails }
            .padding(vertical = AppSpacing.small)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbnail
            if (entry.thumbnailPath.isNotBlank()) {
                AsyncImage(
                    model = entry.thumbnailPath,
                    contentDescription = "Food thumbnail",
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(AppRadius.small)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(AppRadius.small))
                        .background(LeanEatColor.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Restaurant,
                        contentDescription = null,
                        tint = LeanEatColor,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(AppSpacing.medium))

            // Info
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${entry.calories} kcal",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.width(AppSpacing.small))
                    // Health score badge
                    Surface(
                        shape = CircleShape,
                        color = entryHealthColor(entry.healthScore).copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = "${entry.healthScore}",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = entryHealthColor(entry.healthScore),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Text(
                    text = entry.shortDate,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
                // Food names
                if (entry.foods.isNotEmpty()) {
                    val foodNames = entry.foods.take(3).joinToString(", ") { it.name }
                    Text(
                        text = foodNames + if (entry.foods.size > 3) "…" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Delete button
            IconButton(
                onClick = { showDeleteConfirm = true },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // Expanded details
        AnimatedVisibility(visible = showDetails) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 64.dp, top = AppSpacing.small)
            ) {
                Surface(
                    shape = RoundedCornerShape(AppRadius.small),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Column(modifier = Modifier.padding(AppSpacing.medium)) {
                        Text(
                            text = "Protein: ${String.format("%.1f", entry.protein)}g  |  Carbs: ${String.format("%.1f", entry.carbs)}g  |  Fat: ${String.format("%.1f", entry.fat)}g",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                        if (entry.suggestions.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Tips: ${entry.suggestions.first()}",
                                style = MaterialTheme.typography.bodySmall,
                                color = LeanEatColor,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun entryHealthColor(score: Int): Color = when {
    score >= 80 -> HealthExcellent
    score >= 60 -> HealthGood
    score >= 40 -> HealthFair
    else -> HealthPoor
}

// ─────────────────────────────────────────────────────────────────────────────
// HEALTH SCORE RING (fancier version of the existing circle)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun HealthScoreRing(
    score: Int,
    modifier: Modifier = Modifier
) {
    val color = entryHealthColor(score)

    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = score.toString(),
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(
                text = "pts",
                fontSize = 11.sp,
                color = color.copy(alpha = 0.8f)
            )
        }
    }
}
