package com.example.ui.components

import kotlin.math.max
import kotlin.math.min

data class ViewerOffset(val x: Float, val y: Float)

/** Bounds use the fitted image, so portrait/landscape images cannot pan into empty space. */
object ViewerGeometry {
    fun clamp(x: Float, y: Float, scale: Float, viewportWidth: Float, viewportHeight: Float, aspect: Float): ViewerOffset {
        if (viewportWidth <= 0 || viewportHeight <= 0 || scale <= 1.05f) return ViewerOffset(0f, 0f)
        val ratio = aspect.takeIf { it > 0 && it.isFinite() } ?: 1f
        val fittedWidth = min(viewportWidth, viewportHeight * ratio)
        val fittedHeight = min(viewportHeight, viewportWidth / ratio)
        val maxX = max(0f, (fittedWidth * scale - viewportWidth) / 2)
        val maxY = max(0f, (fittedHeight * scale - viewportHeight) / 2)
        return ViewerOffset(x.coerceIn(-maxX, maxX), y.coerceIn(-maxY, maxY))
    }
}
