package com.example.cellmonitor.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cellmonitor.data.SignalHistoryPoint
import com.example.cellmonitor.ui.theme.TechCardBorder
import com.example.cellmonitor.ui.theme.TechCardSurface
import com.example.cellmonitor.ui.theme.TechCyan
import com.example.cellmonitor.ui.theme.TechEmerald
import com.example.cellmonitor.ui.theme.TextMuted
import com.example.cellmonitor.ui.theme.TextPrimary
import com.example.cellmonitor.ui.theme.TextSecondary

@Composable
fun SignalGraph(
    history: List<SignalHistoryPoint>,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .testTag("signal_graph_card")
            .fillMaxWidth()
            .background(TechCardSurface, RoundedCornerShape(20.dp))
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(TechCyan, RoundedCornerShape(4.dp))
                    )
                    Spacer(modifier = Modifier.size(6.dp))
                    Text(
                        text = "RSRP SIGNAL HISTORY",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        letterSpacing = 0.5.sp
                    )
                }

                val current = history.lastOrNull()?.rsrp?.let { "$it dBm" } ?: "---"
                Text(
                    text = "Live: $current",
                    fontSize = 12.sp,
                    color = TechCyan,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Canvas Chart
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height

                    // Signal Range: -130 dBm (bottom) to -60 dBm (top)
                    val minDb = -130f
                    val maxDb = -60f
                    val dbSpan = maxDb - minDb

                    // Draw reference grid lines
                    val referenceDbs = listOf(-70f, -90f, -110f)
                    referenceDbs.forEach { refDb ->
                        val y = h - ((refDb - minDb) / dbSpan) * h
                        drawLine(
                            color = TechCardBorder,
                            start = Offset(0f, y),
                            end = Offset(w, y),
                            strokeWidth = 1.dp.toPx()
                        )
                    }

                    if (history.size >= 2) {
                        val stepX = w / (history.size - 1).coerceAtLeast(1)
                        val points = history.mapIndexed { index, pt ->
                            val clamped = pt.rsrp.toFloat().coerceIn(minDb, maxDb)
                            val y = h - ((clamped - minDb) / dbSpan) * h
                            Offset(index * stepX, y)
                        }

                        // Path for stroke
                        val linePath = Path().apply {
                            moveTo(points.first().x, points.first().y)
                            for (i in 1 until points.size) {
                                linePathTo(points[i - 1], points[i])
                            }
                        }

                        // Path for filled area
                        val fillPath = Path().apply {
                            moveTo(points.first().x, points.first().y)
                            for (i in 1 until points.size) {
                                linePathTo(points[i - 1], points[i])
                            }
                            lineTo(points.last().x, h)
                            lineTo(points.first().x, h)
                            close()
                        }

                        // Draw gradient fill
                        drawPath(
                            path = fillPath,
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    TechCyan.copy(alpha = 0.35f),
                                    TechEmerald.copy(alpha = 0.05f),
                                    Color.Transparent
                                )
                            )
                        )

                        // Draw stroke
                        drawPath(
                            path = linePath,
                            brush = Brush.horizontalGradient(listOf(TechEmerald, TechCyan)),
                            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                        )

                        // Draw point on latest value
                        val lastPt = points.last()
                        drawCircle(
                            color = Color.White,
                            radius = 4.dp.toPx(),
                            center = lastPt
                        )
                        drawCircle(
                            color = TechCyan,
                            radius = 7.dp.toPx(),
                            center = lastPt,
                            style = Stroke(width = 2.dp.toPx())
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "T-60s", fontSize = 10.sp, color = TextMuted)
                Text(text = "-110 dBm", fontSize = 10.sp, color = TextMuted)
                Text(text = "-90 dBm", fontSize = 10.sp, color = TextMuted)
                Text(text = "NOW", fontSize = 10.sp, color = TechCyan, fontWeight = FontWeight.Bold)
            }
        }
    }
}

private fun Path.linePathTo(p1: Offset, p2: Offset) {
    // Smooth bezier or direct line
    val controlX = (p1.x + p2.x) / 2f
    cubicTo(controlX, p1.y, controlX, p2.y, p2.x, p2.y)
}
