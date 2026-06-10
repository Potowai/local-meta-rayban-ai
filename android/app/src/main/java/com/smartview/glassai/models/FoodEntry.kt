package com.smartview.glassai.models

import java.text.SimpleDateFormat
import java.util.*

/**
 * A food analysis history entry, persisted via [FoodHistoryStorage].
 */
data class FoodEntry(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val thumbnailPath: String = "",
    val calories: Int = 0,
    val protein: Double = 0.0,
    val carbs: Double = 0.0,
    val fat: Double = 0.0,
    val healthScore: Int = 0,
    val foods: List<FoodItem> = emptyList(),
    val suggestions: List<String> = emptyList(),
    val notes: String = "",
    val responseJson: String = ""
) {
    val formattedDate: String
        get() {
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            return sdf.format(Date(timestamp))
        }

    val shortDate: String
        get() {
            val now = Calendar.getInstance()
            val entryDate = Calendar.getInstance().apply { timeInMillis = timestamp }
            return when {
                isSameDay(now, entryDate) -> {
                    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
                }
                isYesterday(now, entryDate) -> "Yesterday"
                else -> SimpleDateFormat("MM/dd", Locale.getDefault()).format(Date(timestamp))
            }
        }

    private fun isSameDay(c1: Calendar, c2: Calendar): Boolean =
        c1.get(Calendar.YEAR) == c2.get(Calendar.YEAR) &&
                c1.get(Calendar.DAY_OF_YEAR) == c2.get(Calendar.DAY_OF_YEAR)

    private fun isYesterday(today: Calendar, other: Calendar): Boolean {
        val yesterday = Calendar.getInstance().apply {
            timeInMillis = today.timeInMillis
            add(Calendar.DAY_OF_YEAR, -1)
        }
        return isSameDay(yesterday, other)
    }
}
