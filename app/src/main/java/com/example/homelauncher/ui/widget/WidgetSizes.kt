package com.example.homelauncher.ui.widget

import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetProviderInfo
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import kotlin.math.ceil
import kotlin.math.max

/** A widget's size range in cells. */
data class SpanRange(val minX: Int, val minY: Int, val maxX: Int, val maxY: Int) {
    val resizable get() = minX < maxX || minY < maxY

    fun intersect(o: SpanRange) = SpanRange(max(minX, o.minX), max(minY, o.minY), minOf(maxX, o.maxX), minOf(maxY, o.maxY))
}

/**
 * Converts between a provider's declared sizes (pixels, already scaled to this screen) and grid
 * cells of [cellWidth] x [cellHeight] pixels.
 */
class WidgetSizes(private val cellWidth: Int, private val cellHeight: Int, private val columns: Int, private val rows: Int, private val density: Float) {

    private fun spansFor(px: Int, cell: Int, limit: Int) = ceil(px.toFloat() / cell.coerceAtLeast(1)).toInt().coerceIn(1, limit)

    /** The size a widget starts at. */
    fun defaultSpans(info: AppWidgetProviderInfo): Pair<Int, Int> {
        if (Build.VERSION.SDK_INT >= 31 && info.targetCellWidth > 0 && info.targetCellHeight > 0) {
            return info.targetCellWidth.coerceIn(1, columns) to info.targetCellHeight.coerceIn(1, rows)
        }
        return spansFor(info.minWidth, cellWidth, columns) to spansFor(info.minHeight, cellHeight, rows)
    }

    /** How small and large the user may make it; fixed axes stay at the default size. */
    fun range(info: AppWidgetProviderInfo): SpanRange {
        val (dx, dy) = defaultSpans(info)
        val horizontal = info.resizeMode and AppWidgetProviderInfo.RESIZE_HORIZONTAL != 0
        val vertical = info.resizeMode and AppWidgetProviderInfo.RESIZE_VERTICAL != 0
        var maxX = columns
        var maxY = rows
        if (Build.VERSION.SDK_INT >= 31) {
            if (info.maxResizeWidth > 0) maxX = spansFor(info.maxResizeWidth, cellWidth, columns)
            if (info.maxResizeHeight > 0) maxY = spansFor(info.maxResizeHeight, cellHeight, rows)
        }
        val minX = if (horizontal) spansFor(info.minResizeWidth.takeIf { it > 0 } ?: info.minWidth, cellWidth, columns).coerceAtMost(dx) else dx
        val minY = if (vertical) spansFor(info.minResizeHeight.takeIf { it > 0 } ?: info.minHeight, cellHeight, rows).coerceAtMost(dy) else dy
        return SpanRange(
            minX, minY,
            if (horizontal) maxOf(maxX, dx) else dx,
            if (vertical) maxOf(maxY, dy) else dy,
        )
    }

    /** Tells the provider the size it is shown at, in dp, so it can pick a matching layout. */
    fun applySize(view: AppWidgetHostView, spanX: Int, spanY: Int) {
        val w = spanX * cellWidth / density
        val h = spanY * cellHeight / density
        try {
            if (Build.VERSION.SDK_INT >= 31) {
                view.updateAppWidgetSize(Bundle(), listOf(SizeF(w, h)))
            } else {
                @Suppress("DEPRECATION")
                view.updateAppWidgetSize(null, w.toInt(), h.toInt(), w.toInt(), h.toInt())
            }
        } catch (e: RuntimeException) {
            // The provider may be gone or updating; it gets the size on its next bind.
        }
    }

    /** Options for binding a new widget at [spanX] x [spanY]. */
    fun options(spanX: Int, spanY: Int): Bundle {
        val w = (spanX * cellWidth / density).toInt()
        val h = (spanY * cellHeight / density).toInt()
        return Bundle().apply {
            putInt(android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, w)
            putInt(android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, h)
            putInt(android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, w)
            putInt(android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, h)
            if (Build.VERSION.SDK_INT >= 31) {
                putParcelableArrayList(android.appwidget.AppWidgetManager.OPTION_APPWIDGET_SIZES, arrayListOf(SizeF(w.toFloat(), h.toFloat())))
            }
        }
    }
}
