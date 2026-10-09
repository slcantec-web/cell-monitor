package com.example.cellmonitor.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cellmonitor.data.BandCalculators
import com.example.cellmonitor.data.CarrierInfo
import com.example.cellmonitor.data.NeighborCell
import com.example.cellmonitor.data.SeenCell
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

// ---------------------------------------------------------------------------
// Carrier header (dual network / SIM aware)
// ---------------------------------------------------------------------------

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
                            text = "Network PLMN ${carrier.mcc}-${carrier.mnc}" +
                                if (carrier.countryCode.isNotBlank() && carrier.countryCode != "---")
                                    " • ${carrier.countryCode}" else "",
                            fontSize = 12.sp,
                            color = TextSecondary,
                            fontFamily = FontFamily.Monospace
                        )
                        val simPlmn = if (carrier.simMcc != "---" && carrier.simMnc != "---")
                            " (${carrier.simMcc}-${carrier.simMnc})" else ""
                        Text(
                            text = "SIM: ${carrier.simOperator}$simPlmn",
                            fontSize = 12.sp,
                            color = TextSecondary,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

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

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MiniInfoBadge(label = "SIM STATE", value = carrier.simState)
                MiniInfoBadge(label = "DATA TECH", value = carrier.dataNetworkType)
                MiniInfoBadge(label = "NET MCC/MNC", value = "${carrier.mcc} / ${carrier.mnc}")
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MiniInfoBadge(label = "SIM MCC/MNC", value = "${carrier.simMcc} / ${carrier.simMnc}")
                MiniInfoBadge(label = "STATUS BAR ICON", value = carrier.displayType)
            }
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

// ---------------------------------------------------------------------------
// Signal quality grid
// ---------------------------------------------------------------------------

@Composable
fun SignalQualityGrid(
    signal: SignalMetrics,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .testTag("signal_quality_grid")
            .fillMaxWidth()
            .background(TechCardSurface, RoundedCornerShape(20.dp))
            .border(1.dp, TechCardBorder, RoundedCornerShape(20.dp))
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "SIGNAL DETAILS",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                letterSpacing = 0.5.sp
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricTile("RSRP", signal.rsrp?.let { "$it dBm" } ?: "---", TechCyan, Modifier.weight(1f))
                MetricTile("RSRQ", signal.rsrq?.let { "$it dB" } ?: "---", TechEmerald, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricTile("SINR", signal.sinr?.let { "$it dB" } ?: "---", TechBlue, Modifier.weight(1f))
                MetricTile("RSSI", signal.rssi?.let { "$it dBm" } ?: "---", TechPurple, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricTile("CQI", signal.cqi?.toString() ?: "---", TechAmber, Modifier.weight(1f))
                MetricTile("TIMING ADV.", signal.timingAdvance?.toString() ?: "---", TechRose, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun MetricTile(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(TechCardSurfaceVariant, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(text = label, fontSize = 10.sp, color = accent, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            fontSize = 15.sp,
            color = TextPrimary,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}

// ---------------------------------------------------------------------------
// Serving cell card
// ---------------------------------------------------------------------------

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
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "SERVING CELL",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    letterSpacing = 0.5.sp
                )
                if (cell != null) {
                    Text(
                        text = "${cell.band} • ${cell.duplex}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = TechCyan,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            if (cell == null) {
                Text(
                    text = "No serving cell reported yet.",
                    fontSize = 12.sp,
                    color = TextMuted
                )
            } else {
                Text(
                    text = "${cell.tech.displayTitle} • ${cell.bandName}",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                InfoRow("Frequency", cell.frequencyMhz)
                InfoRow("ARFCN", cell.arfcn.toString())
                InfoRow("Bandwidth", cell.bandwidth)
                InfoRow("PCI", cell.pci.toString())
                InfoRow("Cell ID", cell.cellId.toString())
                cell.nodeBId?.let { enb ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "eNB / gNB ID", fontSize = 12.sp, color = TextMuted)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = enb.toString(),
                                fontSize = 13.sp,
                                color = TechEmerald,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            IconButton(
                                onClick = {
                                    val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    cb.setPrimaryClip(ClipData.newPlainText("eNB ID", enb.toString()))
                                    Toast.makeText(context, "eNB ID copied", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copy eNB ID",
                                    tint = TechCyan,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
                cell.sectorId?.let { InfoRow("Sector ID", it.toString()) }
                InfoRow("TAC", cell.tac.toString())
                InfoRow("RSRP", signal.rsrp?.let { "$it dBm" } ?: "---")
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
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

// ---------------------------------------------------------------------------
// Neighbor cell row
// ---------------------------------------------------------------------------

@Composable
fun NeighborCellItem(
    neighbor: NeighborCell,
    modifier: Modifier = Modifier
) {
    val quality = BandCalculators.evaluateSignalQuality(neighbor.rsrp)
    val color = Color(quality.colorHex)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(TechCardSurface, RoundedCornerShape(16.dp))
            .border(1.dp, TechCardBorder, RoundedCornerShape(16.dp))
            .padding(14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${neighbor.techType} ${neighbor.band}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                if (neighbor.operatorName.isNotBlank() && neighbor.operatorName != "---" && neighbor.operatorName != "Unknown") {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = neighbor.operatorName,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = TechCyan,
                        modifier = Modifier
                            .background(TechCyan.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Text(
                text = "PCI ${neighbor.pci} • ARFCN ${neighbor.arfcn}",
                fontSize = 11.sp,
                color = TextSecondary,
                fontFamily = FontFamily.Monospace
            )
            val idLine = buildString {
                if (neighbor.cellId != null && neighbor.cellId > 0) append("CID ${neighbor.cellId}")
                if (neighbor.mcc != "---" && neighbor.mnc != "---") {
                    if (isNotEmpty()) append(" • ")
                    append("PLMN ${neighbor.mcc}-${neighbor.mnc}")
                }
            }
            if (idLine.isNotEmpty()) {
                Text(
                    text = idLine,
                    fontSize = 10.sp,
                    color = TextMuted,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = neighbor.rsrp?.let { "$it dBm" } ?: "---",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = color,
                fontFamily = FontFamily.Monospace
            )
            val delta = neighbor.deltaRsrp
            Text(
                text = when {
                    delta == null -> "Δ ---"
                    delta > 0 -> "Δ +$delta dB"
                    else -> "Δ $delta dB"
                },
                fontSize = 11.sp,
                color = TextMuted,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

// ---------------------------------------------------------------------------
// "All cells seen" row
// ---------------------------------------------------------------------------

@Composable
fun SeenCellItem(
    cell: SeenCell,
    nowMs: Long,
    modifier: Modifier = Modifier
) {
    val isTdd = cell.techType == "LTE" && (cell.band == "B40" || cell.band == "B41")
    val accent = if (isTdd) TechAmber else TechCyan
    val ageSec = if (nowMs > 0 && cell.lastSeenMs > 0) ((nowMs - cell.lastSeenMs) / 1000).coerceAtLeast(0) else 0
    val ageText = if (ageSec < 2) "now" else "${ageSec}s ago"

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(TechCardSurface, RoundedCornerShape(16.dp))
            .border(1.dp, if (isTdd) TechAmber.copy(alpha = 0.4f) else TechCardBorder, RoundedCornerShape(16.dp))
            .padding(14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${cell.techType} ${cell.band}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = accent
                )
                if (cell.wasServing) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "SERVED",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = TechEmerald,
                        modifier = Modifier
                            .background(TechEmerald.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                if (cell.operatorName.isNotBlank() && cell.operatorName != "---" && cell.operatorName != "Unknown") {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = cell.operatorName,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = TechCyan,
                        modifier = Modifier
                            .background(TechCyan.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Text(
                text = "PCI ${cell.pci} • ARFCN ${cell.arfcn}",
                fontSize = 11.sp,
                color = TextSecondary,
                fontFamily = FontFamily.Monospace
            )
            val idLine = buildString {
                if (cell.cellId != null && cell.cellId > 0) append("CID ${cell.cellId}")
                if (cell.mcc != "---" && cell.mnc != "---") {
                    if (isNotEmpty()) append(" • ")
                    append("PLMN ${cell.mcc}-${cell.mnc}")
                }
            }
            if (idLine.isNotEmpty()) {
                Text(
                    text = idLine,
                    fontSize = 10.sp,
                    color = TextMuted,
                    fontFamily = FontFamily.Monospace
                )
            }
            Text(
                text = "Seen ${cell.seenCount}x • $ageText",
                fontSize = 10.sp,
                color = TextMuted
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = cell.bestRsrp?.let { "$it dBm" } ?: "---",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                fontFamily = FontFamily.Monospace
            )
            Text(text = "best", fontSize = 10.sp, color = TextMuted)
            Text(
                text = cell.lastRsrp?.let { "last $it" } ?: "last ---",
                fontSize = 10.sp,
                color = TextMuted,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

// ---------------------------------------------------------------------------
// TDD band check (B40 / B41)
// ---------------------------------------------------------------------------

@Composable
fun TddBandCheckCard(
    seenCells: List<SeenCell>,
    nowMs: Long,
    modifier: Modifier = Modifier
) {
    val tdd = seenCells.filter { it.techType == "LTE" && (it.band == "B40" || it.band == "B41") }
    val detected = tdd.isNotEmpty()
    val accent = if (detected) TechEmerald else TextMuted

    Box(
        modifier = modifier
            .testTag("tdd_band_check_card")
            .fillMaxWidth()
            .background(TechCardSurface, RoundedCornerShape(20.dp))
            .border(1.dp, TechCardBorder, RoundedCornerShape(20.dp))
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "TDD BAND CHECK (B40 / B41)",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    letterSpacing = 0.5.sp
                )
                Text(
                    text = if (detected) "DETECTED" else "NOT SEEN",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = accent,
                    modifier = Modifier
                        .background(accent.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }

            if (!detected) {
                Text(
                    text = "No Band 40 (2300) or Band 41 (2500) cell reported yet. Keep auto-refresh on while moving around.",
                    fontSize = 11.sp,
                    color = TextMuted
                )
            } else {
                tdd.sortedByDescending { it.bestRsrp ?: -999 }.forEach { c ->
                    val ageSec = if (nowMs > 0) ((nowMs - c.lastSeenMs) / 1000).coerceAtLeast(0) else 0
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${if (c.wasServing) "Serving" else "Neighbor"} ${c.band} • PCI ${c.pci}",
                            fontSize = 12.sp,
                            color = TextPrimary,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "${c.bestRsrp ?: "---"} dBm • ${if (ageSec < 2) "now" else "${ageSec}s"}",
                            fontSize = 12.sp,
                            color = TechAmber,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Band support check (watch-list of bands the user cares about)
// ---------------------------------------------------------------------------

private const val BAND_PREFS = "band_support_prefs"
private const val BAND_KEY = "watched_bands"

val SELECTABLE_BANDS = listOf(
    "B1", "B2", "B3", "B4", "B5", "B7", "B8", "B12", "B13", "B14", "B20", "B25",
    "B26", "B28", "B38", "B40", "B41", "B42", "B48", "B66", "B71",
    "n28", "n41", "n78", "n79"
)
private val DEFAULT_WATCHED_BANDS = setOf("B1", "B3", "B5", "B7", "B8", "B28", "B40", "B41")

/** Bands from the watch-list that this phone has never reported (shared with the export). */
fun missingWatchedBands(context: Context, seenCells: List<SeenCell>): List<String> {
    val prefs = context.getSharedPreferences(BAND_PREFS, Context.MODE_PRIVATE)
    val watched = prefs.getStringSet(BAND_KEY, DEFAULT_WATCHED_BANDS)?.toSet() ?: DEFAULT_WATCHED_BANDS
    val seen = seenCells.map { it.band }.toSet()
    return SELECTABLE_BANDS.filter { it in watched && it !in seen }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BandSupportCard(
    seenCells: List<SeenCell>,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(BAND_PREFS, Context.MODE_PRIVATE) }
    var watched by remember {
        mutableStateOf(prefs.getStringSet(BAND_KEY, DEFAULT_WATCHED_BANDS)?.toSet() ?: DEFAULT_WATCHED_BANDS)
    }
    val seen = seenCells.map { it.band }.toSet()
    val missing = SELECTABLE_BANDS.filter { it in watched && it !in seen }
    val allFound = watched.isNotEmpty() && missing.isEmpty()

    val statusColor = when {
        watched.isEmpty() -> TextMuted
        allFound -> TechEmerald
        else -> TechAmber
    }

    Box(
        modifier = modifier
            .testTag("band_support_card")
            .fillMaxWidth()
            .background(TechCardSurface, RoundedCornerShape(20.dp))
            .border(1.dp, if (missing.isNotEmpty()) TechAmber.copy(alpha = 0.5f) else TechCardBorder, RoundedCornerShape(20.dp))
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "BAND SUPPORT CHECK",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    letterSpacing = 0.5.sp
                )
                Text(
                    text = when {
                        watched.isEmpty() -> "NONE SELECTED"
                        allFound -> "ALL DETECTED"
                        else -> "${missing.size} MISSING"
                    },
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = statusColor,
                    modifier = Modifier
                        .background(statusColor.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }

            if (missing.isNotEmpty()) {
                Text(
                    text = "⚠ Not detected on this phone: ${missing.joinToString(", ")}. " +
                        "Your phone may not support these bands, or they may just not be " +
                        "available at your location right now.",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TechAmber
                )
            } else if (allFound) {
                Text(
                    text = "Every band you are watching has been reported by this phone.",
                    fontSize = 12.sp,
                    color = TechEmerald
                )
            } else {
                Text(
                    text = "Tap bands below to choose which ones to watch.",
                    fontSize = 12.sp,
                    color = TextMuted
                )
            }

            Text(text = "TAP TO WATCH / UNWATCH", fontSize = 10.sp, color = TextMuted, fontWeight = FontWeight.SemiBold)

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                SELECTABLE_BANDS.forEach { band ->
                    val isWatched = band in watched
                    val isSeen = band in seen
                    val chipColor = when {
                        !isWatched -> TextMuted
                        isSeen -> TechEmerald
                        else -> TechAmber
                    }
                    Text(
                        text = (if (isWatched) (if (isSeen) "✓ " else "✗ ") else "") + band,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = chipColor,
                        modifier = Modifier
                            .background(chipColor.copy(alpha = if (isWatched) 0.15f else 0.06f), RoundedCornerShape(8.dp))
                            .clickable {
                                val updated = if (isWatched) watched - band else watched + band
                                watched = updated
                                prefs.edit().putStringSet(BAND_KEY, updated).apply()
                            }
                            .padding(horizontal = 9.dp, vertical = 5.dp)
                    )
                }
            }

            Text(
                text = "Android does not expose a phone's supported-band list, so this check is based on " +
                    "bands the phone has actually reported since the app started (see Neighbors → Clear to reset). " +
                    "Keep auto-refresh on and move around for a better result.",
                fontSize = 10.sp,
                color = TextMuted
            )
        }
    }
}
