package com.example.data

import android.content.Context
import android.content.SharedPreferences
import com.example.model.AppTheme
import com.example.model.ColorPalette
import com.example.model.ThumbnailQuality
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PreferencesRepository(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("artflux_preferences", Context.MODE_PRIVATE)

    // Migration logic for theme:
    // If "app_theme" is not set, check legacy "dark_amoled_theme"
    private val initialTheme: AppTheme = run {
        val savedTheme = prefs.getString(KEY_APP_THEME, null)
        if (savedTheme != null) {
            try {
                AppTheme.valueOf(savedTheme)
            } catch (e: Exception) {
                AppTheme.DEFAULT
            }
        } else {
            // Legacy migration: if dark_amoled_theme was present, map to AppTheme.DARK
            AppTheme.DEFAULT
        }
    }

    private val _theme = MutableStateFlow(initialTheme)
    val theme: StateFlow<AppTheme> = _theme.asStateFlow()

    private val initialColorPalette: ColorPalette = run {
        val saved = prefs.getString(KEY_COLOR_PALETTE, null)
        if (saved != null) {
            try {
                ColorPalette.valueOf(saved)
            } catch (e: Exception) {
                ColorPalette.DEFAULT
            }
        } else {
            ColorPalette.DEFAULT
        }
    }

    private val _colorPalette = MutableStateFlow(initialColorPalette)
    val colorPalette: StateFlow<ColorPalette> = _colorPalette.asStateFlow()

    private val _blurNsfw = MutableStateFlow(prefs.getBoolean(KEY_BLUR_NSFW, true))
    val blurNsfw: StateFlow<Boolean> = _blurNsfw.asStateFlow()

    private val _thumbnailQuality = MutableStateFlow(
        try {
            ThumbnailQuality.valueOf(prefs.getString(KEY_THUMBNAIL_QUALITY, ThumbnailQuality.DEFAULT.name) ?: ThumbnailQuality.DEFAULT.name)
        } catch (e: Exception) {
            ThumbnailQuality.DEFAULT
        }
    )
    val thumbnailQuality: StateFlow<ThumbnailQuality> = _thumbnailQuality.asStateFlow()

    private val _loopVideo = MutableStateFlow(prefs.getBoolean(KEY_LOOP_VIDEO, true))
    val loopVideo: StateFlow<Boolean> = _loopVideo.asStateFlow()

    fun setTheme(theme: AppTheme) {
        prefs.edit().putString(KEY_APP_THEME, theme.name).apply()
        _theme.value = theme
    }

    fun setColorPalette(palette: ColorPalette) {
        prefs.edit().putString(KEY_COLOR_PALETTE, palette.name).apply()
        _colorPalette.value = palette
    }

    fun setBlurNsfw(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BLUR_NSFW, enabled).apply()
        _blurNsfw.value = enabled
    }

    fun setThumbnailQuality(quality: ThumbnailQuality) {
        prefs.edit().putString(KEY_THUMBNAIL_QUALITY, quality.name).apply()
        _thumbnailQuality.value = quality
    }

    fun setLoopVideo(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_LOOP_VIDEO, enabled).apply()
        _loopVideo.value = enabled
    }

    companion object {
        private const val KEY_APP_THEME = "app_theme"
        private const val KEY_COLOR_PALETTE = "color_palette"
        private const val KEY_BLUR_NSFW = "blur_nsfw_content"
        private const val KEY_THUMBNAIL_QUALITY = "thumbnail_quality"
        private const val KEY_LOOP_VIDEO = "loop_video_playback"
    }
}
