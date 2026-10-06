package com.example.cellmonitor.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val TechDarkBackground = Color(0xFF090D16)
val TechCardSurface = Color(0xFF111827)
val TechCardBorder = Color(0xFF1F2937)
val TechCardSurfaceVariant = Color(0xFF172033)

val TechCyan = Color(0xFF06B6D4)
val TechEmerald = Color(0xFF10B981)
val TechBlue = Color(0xFF3B82F6)
val TechAmber = Color(0xFFF59E0B)
val TechRose = Color(0xFFEF4444)
val TechPurple = Color(0xFF8B5CF6)

val TextPrimary = Color(0xFFF8FAFC)
val TextSecondary = Color(0xFF94A3B8)
val TextMuted = Color(0xFF64748B)

private val DarkColorScheme = darkColorScheme(
    primary = TechCyan,
    onPrimary = Color(0xFF042F2E),
    primaryContainer = Color(0xFF134E4A),
    onPrimaryContainer = Color(0xFFCCFBF1),
    secondary = TechEmerald,
    onSecondary = Color(0xFF064E3B),
    secondaryContainer = Color(0xFF065F46),
    onSecondaryContainer = Color(0xFFD1FAE5),
    tertiary = TechBlue,
    background = TechDarkBackground,
    onBackground = TextPrimary,
    surface = TechCardSurface,
    onSurface = TextPrimary,
    surfaceVariant = TechCardSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    outline = TechCardBorder
)

@Composable
fun CellMonitorTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
