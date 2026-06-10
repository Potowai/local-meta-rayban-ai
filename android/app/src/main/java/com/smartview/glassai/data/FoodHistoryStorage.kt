package com.smartview.glassai.data

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.smartview.glassai.models.FoodEntry
import java.io.File
import java.io.FileOutputStream

/**
 * JSON-based persistence for food analysis history entries.
 * Follows the same pattern as [QuickVisionStorage].
 */
class FoodHistoryStorage(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )
    private val gson = Gson()
    private val thumbnailDir: File by lazy {
        File(context.filesDir, THUMBNAIL_DIR).also {
            if (!it.exists()) it.mkdirs()
        }
    }

    companion object {
        private const val TAG = "FoodHistoryStorage"
        private const val PREFS_NAME = "localmeta_food_history"
        private const val KEY_ENTRIES = "saved_food_entries"
        private const val THUMBNAIL_DIR = "food_thumbnails"
        private const val MAX_ENTRIES = 50

        @Volatile
        private var instance: FoodHistoryStorage? = null

        fun getInstance(context: Context): FoodHistoryStorage {
            return instance ?: synchronized(this) {
                instance ?: FoodHistoryStorage(context.applicationContext).also { instance = it }
            }
        }
    }

    /**
     * Save a food entry with thumbnail.
     */
    fun saveEntry(
        bitmap: Bitmap?,
        calories: Int,
        protein: Double,
        carbs: Double,
        fat: Double,
        healthScore: Int,
        foods: List<com.smartview.glassai.models.FoodItem>,
        suggestions: List<String>,
        responseJson: String = "",
        notes: String = ""
    ): Boolean {
        return try {
            val id = java.util.UUID.randomUUID().toString()
            val thumbnailPath = if (bitmap != null) saveThumbnail(id, bitmap) else ""

            val entry = FoodEntry(
                id = id,
                thumbnailPath = thumbnailPath ?: "",
                calories = calories,
                protein = protein,
                carbs = carbs,
                fat = fat,
                healthScore = healthScore,
                foods = foods,
                suggestions = suggestions,
                notes = notes,
                responseJson = responseJson
            )

            val entries = getAllEntries().toMutableList()
            entries.add(0, entry)

            // Trim to max entries and clean up old thumbnails
            if (entries.size > MAX_ENTRIES) {
                val toRemove = entries.subList(MAX_ENTRIES, entries.size)
                toRemove.forEach { if (it.thumbnailPath.isNotBlank()) deleteThumbnail(it.thumbnailPath) }
                entries.removeAll(toRemove.toSet())
            }

            val json = gson.toJson(entries)
            prefs.edit().putString(KEY_ENTRIES, json).apply()
            Log.d(TAG, "Food entry saved: $id — ${calories}kcal health=$healthScore")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save food entry: ${e.message}", e)
            false
        }
    }

    /**
     * Get all food history entries (newest first).
     */
    fun getAllEntries(): List<FoodEntry> {
        return try {
            val json = prefs.getString(KEY_ENTRIES, null) ?: return emptyList()
            val type = object : TypeToken<List<FoodEntry>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load food entries: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * Get a single entry by ID.
     */
    fun getEntry(id: String): FoodEntry? {
        return getAllEntries().find { it.id == id }
    }

    /**
     * Delete an entry and its thumbnail.
     */
    fun deleteEntry(id: String): Boolean {
        return try {
            val entries = getAllEntries().toMutableList()
            val entry = entries.find { it.id == id }
            if (entry != null) {
                if (entry.thumbnailPath.isNotBlank()) deleteThumbnail(entry.thumbnailPath)
                entries.removeAll { it.id == id }
                val json = gson.toJson(entries)
                prefs.edit().putString(KEY_ENTRIES, json).apply()
                Log.d(TAG, "Food entry deleted: $id")
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete entry: ${e.message}", e)
            false
        }
    }

    /**
     * Delete all entries and thumbnails.
     */
    fun deleteAllEntries(): Boolean {
        return try {
            thumbnailDir.listFiles()?.forEach { it.delete() }
            prefs.edit().remove(KEY_ENTRIES).apply()
            Log.d(TAG, "All food entries deleted")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete all entries: ${e.message}", e)
            false
        }
    }

    /**
     * Get entry count.
     */
    fun getEntryCount(): Int = getAllEntries().size

    /**
     * Save bitmap as thumbnail and return file path.
     */
    private fun saveThumbnail(id: String, bitmap: Bitmap): String? {
        return try {
            val file = File(thumbnailDir, "${id}.jpg")
            FileOutputStream(file).use { out ->
                val scaledBitmap = if (bitmap.width > 480) {
                    val scale = 480f / bitmap.width
                    Bitmap.createScaledBitmap(
                        bitmap,
                        480,
                        (bitmap.height * scale).toInt(),
                        true
                    )
                } else {
                    bitmap
                }
                scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
                if (scaledBitmap != bitmap) {
                    scaledBitmap.recycle()
                }
            }
            file.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save thumbnail: ${e.message}", e)
            null
        }
    }

    /**
     * Delete a thumbnail file.
     */
    private fun deleteThumbnail(path: String) {
        try {
            File(path).delete()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete thumbnail: ${e.message}", e)
        }
    }
}
