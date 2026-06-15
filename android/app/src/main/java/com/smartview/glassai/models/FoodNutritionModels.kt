package com.smartview.glassai.models

import com.smartview.glassai.ui.theme.HealthExcellent
import com.smartview.glassai.ui.theme.HealthFair
import com.smartview.glassai.ui.theme.HealthGood
import com.smartview.glassai.ui.theme.HealthPoor
import androidx.compose.ui.graphics.Color

data class FoodNutritionResponse(
    val foods: List<FoodItem> = emptyList(),
    val totalCalories: Int = 0,
    val totalProtein: Double = 0.0,
    val totalFat: Double = 0.0,
    val totalCarbs: Double = 0.0,
    val healthScore: Int = 0,
    val suggestions: List<String> = emptyList()
) {
    val healthScoreColor: Color
        get() = when {
            healthScore >= 80 -> HealthExcellent
            healthScore >= 60 -> HealthGood
            healthScore >= 40 -> HealthFair
            else -> HealthPoor
        }

    val healthScoreText: String
        get() = when {
            healthScore >= 80 -> "Excellent"
            healthScore >= 60 -> "Good"
            healthScore >= 40 -> "Fair"
            else -> "Poor"
        }
}

data class FoodItem(
    val name: String,
    val portion: String,
    val calories: Int,
    val protein: Double,
    val fat: Double,
    val carbs: Double,
    val fiber: Double? = null,
    val sugar: Double? = null,
    val healthRating: String = "good"
) {
    val healthRatingLabel: String
        get() = when (healthRating.lowercase()) {
            "excellent" -> "Excellent"
            "good" -> "Good"
            "fair" -> "Fair"
            "poor" -> "Poor"
            "\u4f18\u79c0" -> "Excellent"
            "\u826f\u597d" -> "Good"
            "\u4e00\u822c" -> "Fair"
            "\u8f83\u5dee" -> "Poor"
            else -> "Good"
        }

    /** @deprecated kept for legacy callers only; use healthRatingLabel */
    val healthRatingEmoji: String
        get() = when (healthRating.lowercase()) {
            "excellent" -> "G"
            "good" -> "g"
            "fair" -> "f"
            "poor" -> "p"
            // Legacy Chinese values
            "\u4f18\u79c0" -> "G"
            "\u826f\u597d" -> "g"
            "\u4e00\u822c" -> "f"
            "\u8f83\u5dee" -> "p"
            else -> "g"
        }

    val healthRatingColor: Color
        get() = ratingColor(healthRating)
}

fun ratingColor(rating: String): Color = when (rating.lowercase()) {
    "excellent" -> HealthExcellent
    "good" -> HealthGood
    "fair" -> HealthFair
    "poor" -> HealthPoor
    // Legacy Chinese values
    "\u4f18\u79c0" -> HealthExcellent
    "\u826f\u597d" -> HealthGood
    "\u4e00\u822c" -> HealthFair
    "\u8f83\u5dee" -> HealthPoor
    else -> HealthGood
}
