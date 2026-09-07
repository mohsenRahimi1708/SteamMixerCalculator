package com.steamcalc.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Simple line chart composable for plotting time-series data.
 *
 * @param data list of (x, y) pairs
 * @param xLabel label for x-axis
 * @param yLabel label for y-axis
 * @param lineColor color of the data line
 * @param title chart title
 */
@Composable
fun ChartView(
    data: List<Pair<Double, Double>>,
    xLabel: String,
    @Suppress("UNUSED_PARAMETER") yLabel: String,
    lineColor: Color,
    title: String,
    modifier: Modifier = Modifier
) {
    if (data.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text("No data", color = Color.Gray)
        }
        return
    }

    Column(modifier = modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 4.dp)
        )

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            val padLeft = 60f
            val padRight = 20f
            val padTop = 10f
            val padBottom = 40f

            val chartW = size.width - padLeft - padRight
            val chartH = size.height - padTop - padBottom

            // Data range
            val xMin = data.minOf { it.first }
            val xMax = data.maxOf { it.first }
            val yMin = data.minOf { it.second }
            val yMax = data.maxOf { it.second }

            // Add 5% padding to y range
            val yRange = yMax - yMin
            val yPad = if (yRange > 0) yRange * 0.05 else 1.0
            val yLo = yMin - yPad
            val yHi = yMax + yPad
            val xRange = if (xMax > xMin) xMax - xMin else 1.0

            fun xPos(x: Double) = padLeft + ((x - xMin) / xRange * chartW).toFloat()
            fun yPos(y: Double) = padTop + chartH - ((y - yLo) / (yHi - yLo) * chartH).toFloat()

            // Grid lines
            val gridColor = Color(0xFFE0E0E0)
            val ySteps = computeNiceSteps(yLo, yHi, 5)
            for (step in ySteps) {
                val py = yPos(step)
                drawLine(gridColor, Offset(padLeft, py), Offset(padLeft + chartW, py), strokeWidth = 1f)
                // Y-axis label
                drawContext.canvas.nativeCanvas.apply {
                    val paint = android.graphics.Paint().apply {
                        textSize = 22f
                        color = android.graphics.Color.GRAY
                        textAlign = android.graphics.Paint.Align.RIGHT
                    }
                    drawText(formatSmart(step), padLeft - 8f, py + 8f, paint)
                }
            }

            // X-axis ticks
            val xSteps = computeNiceSteps(xMin, xMax, 5)
            for (step in xSteps) {
                val px = xPos(step)
                drawLine(gridColor, Offset(px, padTop), Offset(px, padTop + chartH), strokeWidth = 1f)
                drawContext.canvas.nativeCanvas.apply {
                    val paint = android.graphics.Paint().apply {
                        textSize = 22f
                        color = android.graphics.Color.GRAY
                        textAlign = android.graphics.Paint.Align.CENTER
                    }
                    drawText(formatSmart(step), px, size.height - 5f, paint)
                }
            }

            // Axis labels
            drawContext.canvas.nativeCanvas.apply {
                val labelPaint = android.graphics.Paint().apply {
                    textSize = 24f
                    color = android.graphics.Color.DKGRAY
                    textAlign = android.graphics.Paint.Align.CENTER
                }
                drawText(xLabel, padLeft + chartW / 2f, size.height - 20f, labelPaint)
            }

            // Data line
            if (data.size >= 2) {
                val path = Path()
                path.moveTo(xPos(data[0].first), yPos(data[0].second))
                for (i in 1 until data.size) {
                    path.lineTo(xPos(data[i].first), yPos(data[i].second))
                }
                drawPath(path, lineColor, style = Stroke(width = 3f, cap = StrokeCap.Round))
            }

            // Data points (only if not too many)
            if (data.size <= 50) {
                for ((x, y) in data) {
                    drawCircle(lineColor, radius = 4f, center = Offset(xPos(x), yPos(y)))
                }
            }
        }
    }
}

// ─── Helpers ──────────────────────────────────────────────────────────────

/**
 * Compute "nice" step values for axis ticks (e.g., 0, 50, 100, 150...).
 */
private fun computeNiceSteps(lo: Double, hi: Double, maxSteps: Int): List<Double> {
    if (hi <= lo) return listOf(lo)
    val rawRange = hi - lo
    val rawStep = rawRange / maxSteps
    val mag = Math.pow(10.0, floor(Math.log10(rawStep)))
    val normalized = rawStep / mag

    val niceStep = when {
        normalized <= 1.0 -> 1.0 * mag
        normalized <= 2.0 -> 2.0 * mag
        normalized <= 5.0 -> 5.0 * mag
        else -> 10.0 * mag
    }

    val start = Math.ceil(lo / niceStep) * niceStep
    val steps = mutableListOf<Double>()
    var v = start
    while (v <= hi + niceStep * 0.001) {
        steps.add(v)
        v += niceStep
    }
    return steps
}

/**
 * Format a number smartly: remove trailing zeros, use integer if close.
 */
private fun formatSmart(v: Double): String {
    return if (abs(v - floor(v + 0.5)) < 0.01 * max(1.0, abs(v))) {
        "${floor(v + 0.5).toLong()}"
    } else {
        "%.1f".format(v)
    }
}
