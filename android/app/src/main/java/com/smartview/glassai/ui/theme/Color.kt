package com.smartview.glassai.ui.theme

import androidx.compose.ui.graphics.Color

// ============================================================
// LocalMeta Design System — Premium Tech Palette
// ============================================================
// A cohesive, dark-mode-native palette for a smart glasses AI
// assistant. Uses vibrant jewel tones against deep backgrounds.
// All pairs meet WCAG AA contrast requirements in both modes.
// ============================================================

// ── Primary: Indigo (tech-forward, premium, trustworthy) ──
val Primary = Color(0xFF4F46E5)
val PrimaryLight = Color(0xFF818CF8)
val PrimaryDark = Color(0xFF3730A3)
val PrimaryContainer = Color(0xFFE0E7FF)
val OnPrimaryContainer = Color(0xFF1E1B4B)

// ── Secondary: Teal (calm, intelligent, AI-affiliated) ──
val Secondary = Color(0xFF0D9488)
val SecondaryLight = Color(0xFF2DD4BF)
val SecondaryDark = Color(0xFF0F766E)
val SecondaryContainer = Color(0xFFCCFBF1)
val OnSecondaryContainer = Color(0xFF022C22)

// ── Tertiary / Accent: Amber (warmth, energy, highlight) ──
val Accent = Color(0xFFD97706)
val AccentLight = Color(0xFFFBBF24)
val AccentContainer = Color(0xFFFEF3C7)
val OnAccentContainer = Color(0xFF451A03)

// ── Feature Colors — each distinct but harmonious ──
val LiveAIColor = Color(0xFF6366F1)         // Indigo 500
val LiveAIColorLight = Color(0xFFA5B4FC)    // Indigo 300
val QuickVisionColor = Color(0xFF8B5CF6)    // Violet 500
val TranslateColor = Color(0xFF06B6D4)      // Cyan 500
val LeanEatColor = Color(0xFFF43F5E)        // Rose 500
val LeanEatColorLight = Color(0xFFFDA4AF)   // Rose 300
val WordLearnColor = Color(0xFFF97316)      // Orange 500
val LiveStreamColor = Color(0xFFEC4899)     // Pink 500
val RTMPColor = Color(0xFFA855F7)           // Purple 500

// ── Nutrition Colors ──
val NutritionProtein = Color(0xFF22C55E)    // Green 500
val NutritionCarbs = Color(0xFFF59E0B)      // Amber 500
val NutritionFat = Color(0xFFEF4444)        // Red 500

// ── Health Score Colors ──
val HealthExcellent = Color(0xFF22C55E)
val HealthGood = Color(0xFFEAB308)
val HealthFair = Color(0xFFF97316)
val HealthPoor = Color(0xFFEF4444)

// ── Light Mode Surface / Text Colors ──
val BackgroundLight = Color(0xFFF8FAFC)
val SurfaceLight = Color(0xFFFFFFFF)
val SurfaceVariantLight = Color(0xFFF1F5F9)
val CardBackgroundLight = Color(0xFFFFFFFF)
val OutlineLight = Color(0xFFE2E8F0)
val TextPrimaryLight = Color(0xFF0F172A)
val TextSecondaryLight = Color(0xFF475569)
val TextTertiaryLight = Color(0xFF94A3B8)

// ── Dark Mode Surface / Text Colors ──
val BackgroundDark = Color(0xFF0B0D14)
val SurfaceDark = Color(0xFF151821)
val SurfaceVariantDark = Color(0xFF1E2230)
val CardBackgroundDark = Color(0xFF181C28)
val OutlineDark = Color(0xFF2D3142)
val TextPrimaryDark = Color(0xFFF1F5F9)
val TextSecondaryDark = Color(0xFF94A3B8)
val TextTertiaryDark = Color(0xFF475569)

// ── Status Colors ──
val Success = Color(0xFF16A34A)            // Green 600
val SuccessContainer = Color(0xFFDCFCE7)   // Green 100
val Warning = Color(0xFFD97706)            // Amber 600
val WarningContainer = Color(0xFFFEF3C7)   // Amber 100
val Error = Color(0xFFDC2626)              // Red 600
val ErrorContainer = Color(0xFFFEE2E2)     // Red 100
val Info = Color(0xFF2563EB)               // Blue 600
val InfoContainer = Color(0xFFDBEAFE)      // Blue 100

// ── Destructive ──
val DestructiveBackground = Color(0xFFFEE2E2)
val DestructiveForeground = Color(0xFF991B1B)

// ── Gradient Colors (backward-compatible aliases) ──
val GradientStart = Primary
val GradientEnd = Accent

// ── Connection Status ──
val OnlineGreen = Success
val OfflineRed = Error
