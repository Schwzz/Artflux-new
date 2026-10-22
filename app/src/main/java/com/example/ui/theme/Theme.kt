package com.example.ui.theme

import android.app.Activity
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.example.model.AppTheme
import com.example.model.ColorPalette

/** Three complete, fixed themes. Stored palette IDs are retained for existing installs. */
fun artfluxColorScheme(palette: ColorPalette): ColorScheme = when (palette) {
    ColorPalette.ARCTIC_SIGNAL -> darkColorScheme(
        primary = Color(0xFF8BDDD4), onPrimary = Color(0xFF052B29), primaryContainer = Color(0xFF193D3A), onPrimaryContainer = Color(0xFFB8EEE8),
        secondary = Color(0xFFBFC6E5), background = Color(0xFF101318), onBackground = Color(0xFFE5E7EE),
        surface = Color(0xFF181C23), onSurface = Color(0xFFE5E7EE), surfaceVariant = Color(0xFF242A34),
        onSurfaceVariant = Color(0xFFADB5C5), outline = Color(0xFF48515F), surfaceContainer = Color(0xFF1D222B))
    ColorPalette.EMERALD_NOIR -> darkColorScheme(
        primary = Color(0xFFBBB5FF), onPrimary = Color(0xFF29234F), primaryContainer = Color(0xFF3E3963), onPrimaryContainer = Color(0xFFE5DFFF),
        secondary = Color(0xFFB8C6D8), background = Color(0xFF202127), onBackground = Color(0xFFF0EFF5),
        surface = Color(0xFF292A32), onSurface = Color(0xFFF0EFF5), surfaceVariant = Color(0xFF353640),
        onSurfaceVariant = Color(0xFFC0BFCB), outline = Color(0xFF555562), surfaceContainer = Color(0xFF30313A))
    ColorPalette.CRIMSON_FLUX -> lightColorScheme(
        primary = Color(0xFF385C50), onPrimary = Color.White, primaryContainer = Color(0xFFDCEAE2), onPrimaryContainer = Color(0xFF17372B),
        secondary = Color(0xFF64605A), background = Color(0xFFF8F7F3), onBackground = Color(0xFF242622),
        surface = Color(0xFFFEFDFA), onSurface = Color(0xFF242622), surfaceVariant = Color(0xFFECEBE5),
        onSurfaceVariant = Color(0xFF585B53), outline = Color(0xFF777B71), surfaceContainer = Color(0xFFF0EFE9))
}

@Composable
fun ArtfluxTheme(theme: AppTheme = AppTheme.DARK, colorPalette: ColorPalette = ColorPalette.ARCTIC_SIGNAL, content: @Composable () -> Unit) {
    val view = LocalView.current
    val isLight = colorPalette == ColorPalette.CRIMSON_FLUX
    if (!view.isInEditMode) SideEffect {
        (view.context as? Activity)?.window?.let {
            WindowCompat.getInsetsController(it, view).isAppearanceLightStatusBars = isLight
            WindowCompat.getInsetsController(it, view).isAppearanceLightNavigationBars = isLight
        }
    }
    MaterialTheme(colorScheme = artfluxColorScheme(colorPalette), typography = Typography, content = content)
}
