package com.platen4.superheater.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp

/**
 * Minimal line chart (spec §16 minimum plotting requirement). Series are drawn
 * normalized to their own min/max; axes show only end labels to stay readable.
 */
@Composable
fun SimpleChart(
    x: DoubleArray,
    y: DoubleArray,
    xLabel: String,
    yLabel: String,
    modifier: Modifier = Modifier,
) {
    val lineColor = MaterialTheme.colorScheme.primary
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val yMin = y.min()
    val yMax = y.max()
    val xMin = x.first()
    val xMax = x.last()

    Canvas(modifier.fillMaxWidth().height(140.dp)) {
        if (x.size < 2) return@Canvas
        val w = size.width
        val h = size.height
        val pad = 4f
        fun px(xi: Double): Float {
            val f = if (xMax > xMin) (xi - xMin) / (xMax - xMin) else 0.0
            return (pad + f * (w - 2 * pad)).toFloat()
        }
        fun py(yi: Double): Float {
            val f = if (yMax > yMin) (yi - yMin) / (yMax - yMin) else 0.5
            return (h - pad - f * (h - 2 * pad)).toFloat()
        }
        for (i in 1 until x.size) {
            drawLine(
                color = lineColor,
                start = Offset(px(x[i - 1]), py(y[i - 1])),
                end = Offset(px(x[i]), py(y[i])),
                strokeWidth = 2f,
            )
        }
    }
    // Labels drawn outside the canvas to avoid text-in-canvas complexity.
    androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth()) {
        androidx.compose.material3.Text(
            "$yLabel  [${"%.4g".format(yMin)} .. ${"%.4g".format(yMax)}]  vs  $xLabel [${"%.0f".format(xMin)}..${"%.0f".format(xMax)}]",
            style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
            color = labelColor,
        )
    }
}