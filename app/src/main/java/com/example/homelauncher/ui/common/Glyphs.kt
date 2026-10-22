package com.example.homelauncher.ui.common

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** The few line icons the launcher needs, drawn on a 24-unit grid (no icon library dependency). */
object Glyphs {

    private fun icon(name: String, block: ImageVector.Builder.() -> Unit) =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply(block).build()

    private fun ImageVector.Builder.stroke(block: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit) =
        path(stroke = SolidColor(Color.Black), strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round, pathBuilder = block)

    val Search: ImageVector = icon("search") {
        stroke {
            moveTo(4.5f, 10.5f)
            arcTo(6f, 6f, 0f, true, true, 16.5f, 10.5f)
            arcTo(6f, 6f, 0f, true, true, 4.5f, 10.5f)
            moveTo(15f, 15f)
            lineTo(20f, 20f)
        }
    }

    val Close: ImageVector = icon("close") {
        stroke {
            moveTo(6.5f, 6.5f); lineTo(17.5f, 17.5f)
            moveTo(17.5f, 6.5f); lineTo(6.5f, 17.5f)
        }
    }

    val More: ImageVector = icon("more") {
        path(fill = SolidColor(Color.Black)) {
            for (y in listOf(5f, 12f, 19f)) {
                moveTo(10f, y)
                arcTo(2f, 2f, 0f, true, true, 14f, y)
                arcTo(2f, 2f, 0f, true, true, 10f, y)
                close()
            }
        }
    }
}
