package com.example.cellmonitor.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cellmonitor.data.CarrierInfo
import com.example.cellmonitor.data.NeighborCell
import com.example.cellmonitor.data.ServingCell
import com.example.cellmonitor.data.SignalMetrics
import com.example.cellmonitor.ui.theme.TechAmber
import com.example.cellmonitor.ui.theme.TechBlue
import com.example.cellmonitor.ui.theme.TechCardBorder
import com.example.cellmonitor.ui.theme.TechCardSurface
import com.example.cellmonitor.ui.theme.TechCardSurfaceVariant
import com.example.cellmonitor.ui.theme.TechCyan
import com.example.cellmonitor.ui.theme.TechEmerald
import com.example.cellmonitor.ui.theme.TechPurple
import com.example.cellmonitor.ui.theme.TechRose
import com.example.cellmonitor.ui.theme.TextMuted
import com.example.cellmonitor.ui.theme.TextPrimary
import com.example.cellmonitor.ui.theme.TextSecondary

@Composable
fun CarrierHeaderCard(
    carrier: CarrierInfo,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .testTag("carrier_header_card")
            .fillMaxWidth()
            .background(TechCardSurface, RoundedCornerShape(20.dp))
            .border(1.dp, TechCardBorder, RoundedCornerShape(20.dp))
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
                            .size(36.dp)
                            .background(TechCyan.copy(alpha = 0.15f), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.SignalCellularAlt,
                            contentDescription = "Carrier",
                            tint = TechCyan,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = carrier.operatorName,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = if (carrier.countryCode.isNotBlank()) "PLMN ${carrier.mcc}-${carrier.mnc} • ${carrier.countryCode}" else "PLMN ---",
                            fontSize = 12.sp,
                            color = TextSecondary,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                // SIM & Roaming Badges
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val roamingColor = if (carrier.isRoaming) TechAmber else TechEmerald
                    val roamingText = if (carrier.isRoaming) "ROAMING" else "HOME"
                    Text(
                        text = roamingText,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = roamingColor,
                        modifier = Modifier
                            .background(roamingColor.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MiniInfoBadge(label = "SIM STATE", value = carrier.simState)
                MiniInfoBadge(label = "DATA TECH", value = carrier.dataNetworkType)
                MiniInfoBadge(label = "MCC / MNC", value = "${carrier.mcc} / ${carrier.mnc}")
            }

            Spacer(modifier = Modifier.height(10.dp))
            MiniInfoBadge(label = "STATUS BAR ICON", value = carrier.displayType)
        }
    }
}

@Composable
private fun MiniInfoBadge(label: String, value: String) {
    Column {
        Text(text = label, fontSize = 10.sp, color = TextMuted, fontWeight = FontWeight.SemiBold)
        Text(
            text = value,
            fontSize = 12.sp,
            color = TextPrimary,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
fun ServingCellCard(
    cell: ServingCell?,
    signal: SignalMetrics,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Box(
        modifier = modifier
            .testTag("serving_cell_card")
            .fillMaxWidth()
            .background(TechCardSurface, RoundedCornerShape(20.dp))
            .border(1.dp, TechCardBorder, RoundedCornerShape(20.dp))
            .padding(18.dp)
    ) {
        if (cell == null) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "No Primary Serving Cell Detected",
                    color = TextSecondary,
                    fontSize = 14.sp
                )
            }
        } else {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(TechEmerald.copy(alpha = 0.15f), RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.WifiTethering,
                                contentDescription = "Serving Tower",
                                tint = TechEmerald,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = cell.band,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = TechEmerald,
                                    fontFamily = FontFamily.Monospace
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "(${cell.duplex})",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextMuted
                                )
                            }
                            Text(
                                text = cell.bandName,
                                fontSize = 12.sp,
                                color = TextSecondary
                            )
                        }
                    }

                    // Copy Telemetry Button
                    IconButton(
                        onClick = {
                            val report = buildString {
                                appendLine("--- CELL MONITOR REPORT ---")
                                appendLine("Tech: ${cell.tech.displayTitle}")
                                appendLine("Band: ${cell.band} (${cell.bandName})")
                                appendLine("Duplex: ${cell.duplex}")
                                appendLine("Frequency: ${cell.frequencyMhz}")
                                appendLine("ARFCN: ${cell.arfcn}")
                                appendLine("PCI: ${cell.pci}")
                                appendLine("Cell ID: ${cell.cellId}")
                                cell.nodeBId?.let { appendLine("NodeB ID: $it") }
                                cell.sectorId?.let { appendLine("Sector: $it") }
                                appendLine("TAC: ${cell.tac}")
                                appendLine("Bandwidth: ${cell.bandwidth}")
                                appendLine("RSRP: ${signal.rsrp ?: "---"} dBm")
                                appendLine("RSRQ: ${signal.rsrq ?: "---"} dB")
                                appendLine("SINR: ${signal.sinr ?: "---"} dB")
                            }
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Cell Telemetry", report))
                            Toast.makeText(context, "Telemetry copied to clipboard", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy Telemetry",
                            tint = TechCyan,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Detail Grid
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(TechCardSurfaceVariant, RoundedCornerShape(12.dp))
                        .padding(14.dp)
                ) {
                    DetailRow(label = "Frequency", value = cell.frequencyMhz)
                    DetailRow(label = "Channel (ARFCN)", value = cell.arfcn.toString())
                    DetailRow(label = "Bandwidth", value = cell.bandwidth)
                    DetailRow(label = "Physical Cell ID (PCI)", value = cell.pci.toString())
                    DetailRow(label = "Cell Identity (CID)", value = cell.cellId.toString())
                    cell.nodeBId?.let { DetailRow(label = "eNB / gNB ID", value = it.toString()) }
                    cell.sectorId?.let { DetailRow(label = "Sector ID", value = it.toString()) }
                    DetailRow(label = "Tracking Area (TAC)", value = cell.tac.toString())
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, fontSize = 12.sp, color = TextMuted)
        Text(
            text = value,
            fontSize = 13.sp,
            color = TextPrimary,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SignalQualityGrid(
    signal: SignalMetrics,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "RADIO SIGNAL TELEMETRY",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            letterSpacing = 0.5.sp,
            modifier = Modifier.padding(bottom = 10.dp)
        )

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            maxItemsInEachRow = 2,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SignalMetricTile(
                modifier = Modifier.weight(1f),
                title = "RSRP",
                subtitle = "Ref Signal Power",
                value = signal.rsrp?.let { "$it dBm" } ?: "---",
                rating = when {
                    (signal.rsrp ?: -999) >= -85 -> "Excellent"
                    (signal.rsrp ?: -999) >= -100 -> "Good"
                    (signal.rsrp ?: -999) >= -115 -> "Fair"
                    else -> "Poor"
                },
                accentColor = Color(signal.quality.colorHex)
            )

            SignalMetricTile(
                modifier = Modifier.weight(1f),
                title = "RSRQ",
                subtitle = "Ref Signal Quality",
                value = signal.rsrq?.let { "$it dB" } ?: "---",
                rating = when {
                    (signal.rsrq ?: -99) >= -10 -> "High"
                    (signal.rsrq ?: -99) >= -15 -> "Medium"
                    else -> "Low"
                },
                accentColor = TechCyan
            )

            SignalMetricTile(
                modifier = Modifier.weight(1f),
                title = "SINR / SNR",
                subtitle = "Signal to Noise",
                value = signal.sinr?.let { "$it dB" } ?: "---",
                rating = when {
                    (signal.sinr ?: -99) >= 15 -> "Clear"
                    (signal.sinr ?: -99) >= 5 -> "Acceptable"
                    else -> "Noisy"
                },
                accentColor = TechBlue
            )

            val taMeters = signal.timingAdvance?.let { ta ->
                // LTE Timing Advance step is approximately 78.12 meters
                val meters = (ta * 78.12).toInt()
                if (meters >= 1000) String.format("%.1f km", meters / 1000f) else "$meters m"
            } ?: "---"

            SignalMetricTile(
                modifier = Modifier.weight(1f),
                title = "TIMING ADVANCE",
                subtitle = "Tower Distance",
                value = taMeters,
                rating = signal.timingAdvance?.let { "Step $it" } ?: "Unavailable",
                accentColor = TechPurple
            )
        }
    }
}

@Composable
private fun SignalMetricTile(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String,
    value: String,
    rating: String,
    accentColor: Color
) {
    Box(
        modifier = modifier
            .background(TechCardSurface, RoundedCornerShape(16.dp))
            .border(1.dp, TechCardBorder, RoundedCornerShape(16.dp))
            .padding(14.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = rating,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = accentColor,
                    modifier = Modifier
                        .background(accentColor.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
            Text(text = subtitle, fontSize = 10.sp, color = TextMuted)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                color = TextPrimary,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
fun NeighborCellItem(
    neighbor: NeighborCell,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(TechCardSurface, RoundedCornerShape(14.dp))
            .border(1.dp, TechCardBorder, RoundedCornerShape(14.dp))
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val badgeColor = if (neighbor.techType == "5G NR") TechEmerald else TechCyan
                    Text(
                        text = neighbor.techType,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = badgeColor,
                        modifier = Modifier
                            .background(badgeColor.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = neighbor.band,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "PCI: ${neighbor.pci} • ARFCN: ${neighbor.arfcn}",
                    fontSize = 11.sp,
                    color = TextSecondary,
                    fontFamily = FontFamily.Monospace
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                val rsrpVal = neighbor.rsrp
                val rsrpText = rsrpVal?.let { "$it dBm" } ?: "---"
                val qualityColor = when {
                    (rsrpVal ?: -999) >= -90 -> TechEmerald
                    (rsrpVal ?: -999) >= -105 -> TechAmber
                    else -> TechRose
                }

                Text(
                    text = rsrpText,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = qualityColor,
                    fontFamily = FontFamily.Monospace
                )

                neighbor.deltaRsrp?.let { delta ->
                    val sign = if (delta >= 0) "+$delta" else "$delta"
                    Text(
                        text = "$sign dB vs serv",
                        fontSize = 10.sp,
                        color = if (delta >= 0) TechEmerald else TextMuted
                    )
                }
            }
        }
    }
}


private data class BandHit(
    val band: String,
    val role: String,
    val pci: Int,
    val arfcn: Int,
    val rsrp: Int?
)

/**
 * Looks through the serving cell and all LTE neighbors for Band 40 (2300 MHz TDD)
 * and Band 41 (2500 MHz TDD) and reports whether either was seen.
 */
@Composable
fun TddBandCheckCard(
    serving: ServingCell?,
    signal: SignalMetrics,
    neighbors: List<NeighborCell>,
    modifier: Modifier = Modifier
) {
    val targets = mapOf("B40" to "2300 MHz TDD", "B41" to "2500 MHz TDD")

    val hits = buildList {
        if (serving != null && serving.band in targets) {
            add(BandHit(serving.band, "Serving", serving.pci, serving.arfcn, signal.rsrp))
        }
        neighbors.filter { it.techType == "LTE" && it.band in targets }.forEach {
            add(BandHit(it.band, "Neighbor", it.pci, it.arfcn, it.rsrp))
        }
    }.sortedByDescending { it.rsrp ?: -999 }

    val found = hits.isNotEmpty()
    val statusColor = if (found) TechEmerald else TechAmber

    Box(
        modifier = modifier
            .testTag("tdd_band_check_card")
            .fillMaxWidth()
            .background(TechCardSurface, RoundedCornerShape(20.dp))
            .border(1.dp, TechCardBorder, RoundedCornerShape(20.dp))
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "TDD BAND CHECK",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        letterSpacing = 0.5.sp
                    )
                    Text(text = "Band 40 (2300) & Band 41 (2500)", fontSize = 10.sp, color = TextMuted)
                }
                Text(
                    text = if (found) "DETECTED" else "NOT DETECTED",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = statusColor,
                    modifier = Modifier
                        .background(statusColor.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (!found) {
                Text(
                    text = "No Band 40 / 41 cell is being reported at this location right now.",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            } else {
                hits.forEach { hit ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "${hit.band} \u2022 ${targets[hit.band]}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = "${hit.role} \u2022 PCI ${hit.pci} \u2022 EARFCN ${hit.arfcn}",
                                fontSize = 11.sp,
                                color = TextSecondary,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        val rsrp = hit.rsrp
                        Text(
                            text = rsrp?.let { "$it dBm" } ?: "---",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = when {
                                (rsrp ?: -999) >= -90 -> TechEmerald
                                (rsrp ?: -999) >= -105 -> TechAmber
                                else -> TechRose
                            },
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Only shows cells your phone reports. A phone that doesn't support these bands can't see them.",
                fontSize = 10.sp,
                color = TextMuted
            )
        }
    }
}
