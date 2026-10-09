package com.example.cellmonitor.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cellmonitor.data.RadioTech
import com.example.cellmonitor.data.SignalMetrics
import com.example.cellmonitor.data.SignalQuality
import com.example.cellmonitor.ui.theme.TechAmber
import com.example.cellmonitor.ui.theme.TechCardBorder
import com.example.cellmonitor.ui.theme.TechCardSurface
import com.example.cellmonitor.ui.theme.TechCyan
import com.example.cellmonitor.ui.theme.TechEmerald
import com.example.cellmonitor.ui.theme.TechRose
import com.example.cellmonitor.ui.theme.TextMuted
import com.example.cellmonitor.ui.theme.TextPrimary
import com.example.cellmonitor.ui.theme.TextSecondary
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun SignalGauge(
    signal: SignalMetrics,
    tech: RadioTech,
    modifier: Modifier = Modifier
) {
    val targetProgress = (signal.scorePercentage / 100f).coerceIn(0f, 1f)
    // Short tween keeps scroll smooth when auto-refresh updates the score every few seconds
    val animatedProgress by animateFloatAsState(
        targetValue = targetProgress,
        animationSpec = tween(durationMillis = 280),
        label = "gauge_progress"
    )

    val qualityColor = Color(signal.quality.colorHex)

    Box(
        modifier = modifier
            .testTag("signal_gauge_card")
            .fillMaxWidth()
            .background(TechCardSurface, RoundedCornerShape(20.dp))
            .padding(18.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Radio Tech Banner & Quality Tag
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .background(Color(tech.badgeColor).copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(Color(tech.badgeColor), RoundedCornerShape(4.dp))
                    )
                    Spacer(modifier = Modifier.size(6.dp))
                    Text(
                        text = tech.displayTitle,
                        color = Color(tech.badgeColor),
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                // Signal Quality Tag
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .background(qualityColor.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = signal.quality.label.uppercase(),
                        color = qualityColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Gauge Canvas
            Box(
                modifier = Modifier.size(220.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val strokeWidth = 16.dp.toPx()
                    val diameter = size.minDimension - strokeWidth * 2
                    val arcSize = Size(diameter, diameter)
                    val topLeft = Offset(
                        (size.width - diameter) / 2f,
                        (size.height - diameter) / 2f
                    )

                    val startAngle = 135f
                    val sweepAngle = 270f

                    // Background track
                    drawArc(
                        color = TechCardBorder,
                        startAngle = startAngle,
                        sweepAngle = sweepAngle,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )

                    // Active Progress Arc
                    if (animatedProgress > 0.01f) {
                        val progressSweep = sweepAngle * animatedProgress
                        val gradientBrush = Brush.sweepGradient(
                            listOf(
                                TechRose,
                                TechAmber,
                                TechCyan,
                                TechEmerald
                            ),
                            center = Offset(size.width / 2f, size.height / 2f)
                        )
                        drawArc(
                            brush = gradientBrush,
                            startAngle = startAngle,
                            sweepAngle = progressSweep,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )

                        // Glowing Needle Dot
                        val currentAngleRad = Math.toRadians((startAngle + progressSweep).toDouble())
                        val radius = diameter / 2f
                        val dotX = size.width / 2f + radius * cos(currentAngleRad).toFloat()
                        val dotY = size.height / 2f + radius * sin(currentAngleRad).toFloat()

                        drawCircle(
                            color = Color.White,
                            radius = strokeWidth * 0.45f,
                            center = Offset(dotX, dotY)
                        )
                        drawCircle(
                            color = qualityColor.copy(alpha = 0.5f),
                            radius = strokeWidth * 0.75f,
                            center = Offset(dotX, dotY)
                        )
                    }

                    // Tick indicators for -140, -100, -50 dBm
                    val ticks = listOf(
                        Pair(0f, "-140"),
                        Pair(0.44f, "-100"),
                        Pair(0.72f, "-75"),
                        Pair(1f, "-50")
                    )
                    ticks.forEach { (fraction, _) ->
                        val tickAngleRad = Math.toRadians((startAngle + sweepAngle * fraction).toDouble())
                        val innerR = diameter / 2f - strokeWidth * 0.8f
                        val outerR = diameter / 2f - strokeWidth * 0.35f
                        val start = Offset(
                            size.width / 2f + innerR * cos(tickAngleRad).toFloat(),
                            size.height / 2f + innerR * sin(tickAngleRad).toFloat()
                        )
                        val end = Offset(
                            size.width / 2f + outerR * cos(tickAngleRad).toFloat(),
                            size.height / 2f + outerR * sin(tickAngleRad).toFloat()
                        )
                        drawLine(
                            color = TextMuted.copy(alpha = 0.5f),
                            start = start,
                            end = end,
                            strokeWidth = 2.dp.toPx()
                        )
                    }
                }

                // Center readout
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    val rsrpText = signal.rsrp?.let { "$it" } ?: "---"
                    Text(
                        text = rsrpText,
                        fontSize = 38.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = TextPrimary,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "RSRP  (dBm)",
                        fontSize = 11.sp,
                        color = TextSecondary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${signal.scorePercentage}%",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = qualityColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Sub-metrics row under gauge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                MetricSubItem(label = "RSRQ", value = signal.rsrq?.let { "$it dB" } ?: "---")
                MetricSubItem(label = "SINR", value = signal.sinr?.let { "$it dB" } ?: "---")
                MetricSubItem(label = "RSSI", value = signal.rssi?.let { "$it dBm" } ?: "---")
                MetricSubItem(label = "ASU", value = signal.asu?.toString() ?: "---")
            }
        }
    }
}

@Composable
private fun MetricSubItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            fontSize = 11.sp,
            color = TextMuted,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = value,
            fontSize = 13.sp,
            color = TextPrimary,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}
