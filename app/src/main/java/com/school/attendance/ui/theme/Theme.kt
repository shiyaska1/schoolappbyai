package com.school.attendance.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Blue = Color(0xFF1565C0)
private val BlueDark = Color(0xFF0D47A1)

private val LightColors = lightColorScheme(primary = Blue, secondary = BlueDark)
private val DarkColors = darkColorScheme(primary = Color(0xFF90CAF9), secondary = Color(0xFF64B5F6))

@Composable
fun SchoolAttendanceTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (darkTheme) DarkColors else LightColors, content = content)
}
