package com.example.ui.theme

import android.app.Activity
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.example.model.AppTheme
import com.example.model.ColorPalette

private val ArcticSignalDarkColorScheme = darkColorScheme(
    primary = ArcticSignalPrimaryDark,
    onPrimary = ArcticSignalOnPrimaryDark,
    primaryContainer = ArcticSignalPrimaryContainerDark,
    onPrimaryContainer = ArcticSignalOnPrimaryContainerDark,
    secondary = ArcticSignalSecondaryDark,
    onSecondary = ArcticSignalOnSecondaryDark,
    secondaryContainer = ArcticSignalSecondaryContainerDark,
    onSecondaryContainer = ArcticSignalOnSecondaryContainerDark,
    tertiary = ArcticSignalTertiaryDark,
    background = ArcticSignalBackgroundDark,
    onBackground = TextPrimaryDark,
    surface = ArcticSignalSurfaceDark,
    onSurface = TextPrimaryDark,
    surfaceVariant = ArcticSignalSurfaceVariantDark,
    onSurfaceVariant = TextSecondaryDark,
    outline = ArcticSignalOutlineDark
)

private val ArcticSignalLightColorScheme = lightColorScheme(
    primary = ArcticSignalPrimaryLight,
    onPrimary = ArcticSignalOnPrimaryLight,
    primaryContainer = ArcticSignalPrimaryContainerLight,
    onPrimaryContainer = ArcticSignalOnPrimaryContainerLight,
    secondary = ArcticSignalSecondaryLight,
    onSecondary = ArcticSignalOnSecondaryLight,
    secondaryContainer = ArcticSignalSecondaryContainerLight,
    onSecondaryContainer = ArcticSignalOnSecondaryContainerLight,
    tertiary = ArcticSignalTertiaryLight,
    background = ArcticSignalBackgroundLight,
    onBackground = TextPrimaryLight,
    surface = ArcticSignalSurfaceLight,
    onSurface = TextPrimaryLight,
    surfaceVariant = ArcticSignalSurfaceVariantLight,
    onSurfaceVariant = TextSecondaryLight,
    outline = ArcticSignalOutlineLight
)

private val EmeraldNoirDarkColorScheme = darkColorScheme(
    primary = EmeraldNoirPrimaryDark,
    onPrimary = EmeraldNoirOnPrimaryDark,
    primaryContainer = EmeraldNoirPrimaryContainerDark,
    onPrimaryContainer = EmeraldNoirOnPrimaryContainerDark,
    secondary = EmeraldNoirSecondaryDark,
    onSecondary = EmeraldNoirOnSecondaryDark,
    secondaryContainer = EmeraldNoirSecondaryContainerDark,
    onSecondaryContainer = EmeraldNoirOnSecondaryContainerDark,
    tertiary = EmeraldNoirTertiaryDark,
    background = EmeraldNoirBackgroundDark,
    onBackground = TextPrimaryDark,
    surface = EmeraldNoirSurfaceDark,
    onSurface = TextPrimaryDark,
    surfaceVariant = EmeraldNoirSurfaceVariantDark,
    onSurfaceVariant = TextSecondaryDark,
    outline = EmeraldNoirOutlineDark
)

private val EmeraldNoirLightColorScheme = lightColorScheme(
    primary = EmeraldNoirPrimaryLight,
    onPrimary = EmeraldNoirOnPrimaryLight,
    primaryContainer = EmeraldNoirPrimaryContainerLight,
    onPrimaryContainer = EmeraldNoirOnPrimaryContainerLight,
    secondary = EmeraldNoirSecondaryLight,
    onSecondary = EmeraldNoirOnSecondaryLight,
    secondaryContainer = EmeraldNoirSecondaryContainerLight,
    onSecondaryContainer = EmeraldNoirOnSecondaryContainerLight,
    tertiary = EmeraldNoirTertiaryLight,
    background = EmeraldNoirBackgroundLight,
    onBackground = TextPrimaryLight,
    surface = EmeraldNoirSurfaceLight,
    onSurface = TextPrimaryLight,
    surfaceVariant = EmeraldNoirSurfaceVariantLight,
    onSurfaceVariant = TextSecondaryLight,
    outline = EmeraldNoirOutlineLight
)

private val CrimsonFluxDarkColorScheme = darkColorScheme(
    primary = CrimsonFluxPrimaryDark,
    onPrimary = CrimsonFluxOnPrimaryDark,
    primaryContainer = CrimsonFluxPrimaryContainerDark,
    onPrimaryContainer = CrimsonFluxOnPrimaryContainerDark,
    secondary = CrimsonFluxSecondaryDark,
    onSecondary = CrimsonFluxOnSecondaryDark,
    secondaryContainer = CrimsonFluxSecondaryContainerDark,
    onSecondaryContainer = CrimsonFluxOnSecondaryContainerDark,
    tertiary = CrimsonFluxTertiaryDark,
    background = CrimsonFluxBackgroundDark,
    onBackground = TextPrimaryDark,
    surface = CrimsonFluxSurfaceDark,
    onSurface = TextPrimaryDark,
    surfaceVariant = CrimsonFluxSurfaceVariantDark,
    onSurfaceVariant = TextSecondaryDark,
    outline = CrimsonFluxOutlineDark
)

private val CrimsonFluxLightColorScheme = lightColorScheme(
    primary = CrimsonFluxPrimaryLight,
    onPrimary = CrimsonFluxOnPrimaryLight,
    primaryContainer = CrimsonFluxPrimaryContainerLight,
    onPrimaryContainer = CrimsonFluxOnPrimaryContainerLight,
    secondary = CrimsonFluxSecondaryLight,
    onSecondary = CrimsonFluxOnSecondaryLight,
    secondaryContainer = CrimsonFluxSecondaryContainerLight,
    onSecondaryContainer = CrimsonFluxOnSecondaryContainerLight,
    tertiary = CrimsonFluxTertiaryLight,
    background = CrimsonFluxBackgroundLight,
    onBackground = TextPrimaryLight,
    surface = CrimsonFluxSurfaceLight,
    onSurface = TextPrimaryLight,
    surfaceVariant = CrimsonFluxSurfaceVariantLight,
    onSurfaceVariant = TextSecondaryLight,
    outline = CrimsonFluxOutlineLight
)

@Composable
fun ArtfluxTheme(
    theme: AppTheme = AppTheme.DARK,
    colorPalette: ColorPalette = ColorPalette.ARCTIC_SIGNAL,
    content: @Composable () -> Unit
) {
    val isLight = theme == AppTheme.LIGHT
    val colorScheme: ColorScheme = when (colorPalette) {
        ColorPalette.ARCTIC_SIGNAL -> if (isLight) ArcticSignalLightColorScheme else ArcticSignalDarkColorScheme
        ColorPalette.EMERALD_NOIR -> if (isLight) EmeraldNoirLightColorScheme else EmeraldNoirDarkColorScheme
        ColorPalette.CRIMSON_FLUX -> if (isLight) CrimsonFluxLightColorScheme else CrimsonFluxDarkColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = isLight
                WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = isLight
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
