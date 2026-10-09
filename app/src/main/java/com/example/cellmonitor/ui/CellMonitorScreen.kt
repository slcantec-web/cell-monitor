package com.example.cellmonitor.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cellmonitor.data.CellMonitorRepository
import com.example.cellmonitor.data.RadioTech
import com.example.cellmonitor.data.SeenCell
import com.example.cellmonitor.ui.components.BandSupportCard
import com.example.cellmonitor.ui.components.CarrierHeaderCard
import com.example.cellmonitor.ui.components.missingWatchedBands
import com.example.cellmonitor.ui.components.NeighborCellItem
import com.example.cellmonitor.ui.components.ServingCellCard
import com.example.cellmonitor.ui.components.SeenCellItem
import com.example.cellmonitor.ui.components.SignalGauge
import com.example.cellmonitor.ui.components.SignalGraph
import com.example.cellmonitor.ui.components.SignalQualityGrid
import com.example.cellmonitor.ui.components.TddBandCheckCard
import com.example.cellmonitor.ui.theme.TechAmber
import com.example.cellmonitor.ui.theme.TechCardBorder
import com.example.cellmonitor.ui.theme.TechCardSurface
import com.example.cellmonitor.ui.theme.TechCyan
import com.example.cellmonitor.ui.theme.TechDarkBackground
import com.example.cellmonitor.ui.theme.TechEmerald
import com.example.cellmonitor.ui.theme.TechRose
import com.example.cellmonitor.ui.theme.TextMuted
import com.example.cellmonitor.ui.theme.TextPrimary
import com.example.cellmonitor.ui.theme.TextSecondary
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CellMonitorScreen(
    repository: CellMonitorRepository,
    updateChecker: com.example.cellmonitor.update.UpdateChecker? = null
) {
    val state by repository.state.collectAsState()
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    // Auto-update prompt (GitHub Releases / Cloudflare Pages version.json)
    if (updateChecker != null) {
        UpdatePromptHost(updateChecker = updateChecker)
    }
    val updateState by (updateChecker?.state ?: kotlinx.coroutines.flow.MutableStateFlow(
        com.example.cellmonitor.update.UpdateState()
    )).collectAsState()

    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        repository.checkPermissions()
        repository.refresh()
    }

    Scaffold(
        containerColor = TechDarkBackground,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(TechDarkBackground)
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                // Top App Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "CELL MONITOR",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Black,
                                color = TextPrimary,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            // Mode Pill (LIVE vs SIMULATION)
                            val modeColor = if (state.isDemoMode) TechAmber else TechEmerald
                            val modeLabel = if (state.isDemoMode) "SIMULATED" else "LIVE ANTENNA"
                            Box(
                                modifier = Modifier
                                    .background(modeColor.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = modeLabel,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = modeColor
                                )
                            }
                        }

                        // Last updated timestamp + app version
                        val timeStr = if (state.lastUpdatedMs > 0) {
                            SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(state.lastUpdatedMs))
                        } else "Updating..."
                        val versionLabel = if (updateChecker != null) {
                            "v${updateChecker.currentVersionName()} (${updateChecker.currentVersionCode()})"
                        } else null
                        Text(
                            text = buildString {
                                append("Last sync: $timeStr")
                                if (versionLabel != null) append("  ·  $versionLabel")
                            },
                            fontSize = 11.sp,
                            color = TextMuted,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    // Action Controls
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Check for app updates
                        if (updateChecker != null) {
                            val checking = updateState.checking
                            val hasUpdate = updateState.newerAvailable
                            IconButton(
                                onClick = {
                                    if (updateState.newerAvailable) {
                                        updateChecker.showUpdateDialog()
                                    } else {
                                        updateChecker.clearDismissed()
                                        scope.launch { updateChecker.check(force = true) }
                                    }
                                },
                                modifier = Modifier
                                    .testTag("check_update_button")
                                    .size(38.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SystemUpdate,
                                    contentDescription = "Check for updates",
                                    tint = when {
                                        hasUpdate -> TechCyan
                                        checking -> TechAmber
                                        else -> TextSecondary
                                    }
                                )
                            }
                        }

                        // Demo Mode Toggle Button
                        IconButton(
                            onClick = { repository.toggleDemoMode() },
                            modifier = Modifier
                                .testTag("demo_mode_button")
                                .size(38.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Science,
                                contentDescription = "Toggle Simulation",
                                tint = if (state.isDemoMode) TechAmber else TextSecondary
                            )
                        }

                        // Auto-refresh Toggle Button
                        IconButton(
                            onClick = { repository.setAutoRefresh(!state.isAutoRefresh) },
                            modifier = Modifier
                                .testTag("auto_refresh_button")
                                .size(38.dp)
                        ) {
                            Icon(
                                imageVector = if (state.isAutoRefresh) Icons.Default.Stop else Icons.Default.PlayArrow,
                                contentDescription = "Auto Refresh",
                                tint = if (state.isAutoRefresh) TechEmerald else TextSecondary
                            )
                        }

                        // Manual Refresh Button (spinning when refreshing)
                        val infiniteTransition = rememberInfiniteTransition(label = "refresh_spin")
                        val rotation by infiniteTransition.animateFloat(
                            initialValue = 0f,
                            targetValue = 360f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(800, easing = LinearEasing),
                                repeatMode = RepeatMode.Restart
                            ),
                            label = "spin_angle"
                        )

                        IconButton(
                            onClick = { repository.refresh() },
                            modifier = Modifier
                                .testTag("refresh_button")
                                .size(38.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh",
                                tint = TechCyan,
                                modifier = Modifier.rotate(if (state.isRefreshing) rotation else 0f)
                            )
                        }
                    }
                }

                // Auto Refresh Interval Selector when active
                AnimatedVisibility(visible = state.isAutoRefresh) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End
                    ) {
                        Text(
                            text = "Interval: ",
                            fontSize = 11.sp,
                            color = TextMuted
                        )
                        listOf(1, 2, 5).forEach { sec ->
                            val isSelected = state.refreshIntervalSec == sec
                            Box(
                                modifier = Modifier
                                    .padding(start = 6.dp)
                                    .background(
                                        if (isSelected) TechEmerald else TechCardSurface,
                                        RoundedCornerShape(6.dp)
                                    )
                                    .clickable { repository.setRefreshInterval(sec) }
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = "${sec}s",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) Color.Black else TextSecondary
                                )
                            }
                        }
                    }
                }
            }
        },
        bottomBar = {
            NavigationBar(
                containerColor = TechCardSurface,
                modifier = Modifier
                    .navigationBarsPadding()
                    .testTag("bottom_nav_bar")
            ) {
                val navItems = listOf(
                    Triple(0, "Overview", Icons.Default.Speed),
                    Triple(1, "Tower", Icons.Default.CellTower),
                    Triple(2, "Neighbors", Icons.Default.ViewList),
                    Triple(3, "Raw Log", Icons.Default.Info)
                )

                navItems.forEach { (index, label, icon) ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = { Icon(icon, contentDescription = label) },
                        label = { Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = TechCyan,
                            selectedTextColor = TechCyan,
                            unselectedIconColor = TextMuted,
                            unselectedTextColor = TextMuted,
                            indicatorColor = TechCyan.copy(alpha = 0.15f)
                        ),
                        modifier = Modifier.testTag("nav_tab_$index")
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // In-app update notification (auto; no need to tap the toolbar icon first)
            if (updateChecker != null) {
                UpdateAvailableBanner(updateChecker = updateChecker)
            }

            // Permission Banner (if permission is not granted)
            if (!state.isPermissionGranted && !state.isDemoMode) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .background(TechRose.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                        .border(1.dp, TechRose.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                        .padding(14.dp)
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = "Permission Needed",
                                tint = TechRose,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Location & Phone Permissions Required",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = TechRose
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Android requires Fine Location to read cellular radio tower identity and physical signal strength.",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    permissionLauncher.launch(
                                        arrayOf(
                                            Manifest.permission.ACCESS_FINE_LOCATION,
                                            Manifest.permission.ACCESS_COARSE_LOCATION,
                                            Manifest.permission.READ_PHONE_STATE
                                        )
                                    )
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = TechRose),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Grant Permission", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            TextButton(
                                onClick = { repository.toggleDemoMode() }
                            ) {
                                Text("Use Simulation Mode", fontSize = 12.sp, color = TechAmber)
                            }
                        }
                    }
                }
            }

            // Location switch off -> Android returns an empty cell list
            if (state.isPermissionGranted && !state.locationEnabled && !state.isDemoMode) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .background(TechAmber.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                        .border(1.dp, TechAmber.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                        .padding(14.dp)
                ) {
                    Column {
                        Text(
                            text = "Location is turned off",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = TechAmber
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Android only reports cell towers while the Location switch is on. Turn it on in quick settings, then come back.",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }
                }
            }

            // Tab Content
            when (selectedTab) {
                0 -> OverviewTabContent(state)
                1 -> ServingCellTabContent(state)
                2 -> NeighborsTabContent(state) { repository.clearSeenCells() }
                3 -> DiagnosticsTabContent(state, context)
            }
        }
    }
}

@Composable
private fun OverviewTabContent(state: com.example.cellmonitor.data.CellMonitorState) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            SignalGauge(
                signal = state.signal,
                tech = state.servingCell?.tech ?: RadioTech.UNKNOWN
            )
        }

        item {
            CarrierHeaderCard(carrier = state.carrier)
        }

        item {
            TddBandCheckCard(
                seenCells = state.seenCells,
                nowMs = state.lastUpdatedMs
            )
        }

        item {
            BandSupportCard(seenCells = state.seenCells)
        }

        item {
            SignalQualityGrid(signal = state.signal)
        }

        item {
            SignalGraph(history = state.signalHistory)
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ServingCellTabContent(state: com.example.cellmonitor.data.CellMonitorState) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            ServingCellCard(
                cell = state.servingCell,
                signal = state.signal,
                mcc = state.carrier.mcc,
                mnc = state.carrier.mnc
            )
        }

        item {
            CellMapperButton(state = state)
        }

        item {
            CarrierHeaderCard(carrier = state.carrier)
        }

        item {
            SignalQualityGrid(signal = state.signal)
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun NeighborsTabContent(
    state: com.example.cellmonitor.data.CellMonitorState,
    onClearSeen: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "NEIGHBOR CELLS (${state.neighbors.size})",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    letterSpacing = 0.5.sp
                )
                Text(
                    text = "Sorted by RSRP",
                    fontSize = 11.sp,
                    color = TextMuted
                )
            }
        }

        if (state.neighbors.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(TechCardSurface, RoundedCornerShape(16.dp))
                        .padding(28.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "No Neighbor Towers Detected",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Only the primary serving cell is currently reporting RF telemetry.",
                            fontSize = 12.sp,
                            color = TextMuted
                        )
                    }
                }
            }
        } else {
            items(state.neighbors) { neighbor ->
                NeighborCellItem(neighbor = neighbor)
            }
        }

        // ---- Running log of every cell seen since last clear ----
        val seenSorted = state.seenCells.sortedWith(
            compareByDescending<SeenCell> { it.techType == "LTE" && (it.band == "B40" || it.band == "B41") }
                .thenByDescending { it.bestRsrp ?: -999 }
        )
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "ALL CELLS SEEN (${seenSorted.size})",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        letterSpacing = 0.5.sp
                    )
                    Text(text = "Every cell reported since last clear", fontSize = 11.sp, color = TextMuted)
                }
                TextButton(onClick = onClearSeen) {
                    Text("Clear", fontSize = 12.sp, color = TechAmber)
                }
            }
        }
        if (seenSorted.isEmpty()) {
            item {
                Text(
                    text = "Nothing logged yet. Keep the app open with auto-refresh on.",
                    fontSize = 12.sp,
                    color = TextMuted
                )
            }
        } else {
            items(seenSorted) { cell ->
                SeenCellItem(cell = cell, nowMs = state.lastUpdatedMs)
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun DiagnosticsTabContent(
    state: com.example.cellmonitor.data.CellMonitorState,
    context: Context
) {
    val fullDump = buildString {
        appendLine("========================================")
        appendLine("CELL MONITOR TELEMETRY EXPORT")
        appendLine("Timestamp: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(state.lastUpdatedMs))}")
        appendLine("Mode: ${if (state.isDemoMode) "Simulation" else "Live Hardware"}")
        appendLine("========================================")
        appendLine("[CARRIER / SUBSCRIPTION]")
        appendLine("Operator Name: ${state.carrier.operatorName}")
        appendLine("SIM Operator: ${state.carrier.simOperator}")
        appendLine("PLMN (MCC/MNC): ${state.carrier.mcc}-${state.carrier.mnc}")
        appendLine("Country: ${state.carrier.countryCode}")
        appendLine("SIM State: ${state.carrier.simState}")
        appendLine("Data Network: ${state.carrier.dataNetworkType}")
        appendLine("Status-bar icon type: ${state.carrier.displayType}")
        appendLine("Roaming: ${state.carrier.isRoaming}")
        appendLine()
        appendLine("[SERVING CELL]")
        state.servingCell?.let { cell ->
            appendLine("Radio Tech: ${cell.tech.displayTitle} (${cell.tech.generation})")
            appendLine("Band: ${cell.band} (${cell.bandName})")
            appendLine("Duplex: ${cell.duplex}")
            appendLine("Frequency: ${cell.frequencyMhz}")
            appendLine("ARFCN: ${cell.arfcn}")
            appendLine("Bandwidth: ${cell.bandwidth}")
            appendLine("PCI: ${cell.pci}")
            appendLine("Cell ID: ${cell.cellId}")
            cell.nodeBId?.let { appendLine("NodeB ID: $it") }
            cell.sectorId?.let { appendLine("Sector ID: $it") }
            appendLine("TAC: ${cell.tac}")
        } ?: appendLine("None")
        appendLine()
        appendLine("[SIGNAL METRICS]")
        appendLine("RSRP: ${state.signal.rsrp ?: "---"} dBm")
        appendLine("RSRQ: ${state.signal.rsrq ?: "---"} dB")
        appendLine("SINR: ${state.signal.sinr ?: "---"} dB")
        appendLine("RSSI: ${state.signal.rssi ?: "---"} dBm")
        appendLine("ASU: ${state.signal.asu ?: "---"}")
        appendLine("CQI: ${state.signal.cqi ?: "---"}")
        appendLine("Timing Advance: ${state.signal.timingAdvance ?: "---"}")
        appendLine("Quality Rating: ${state.signal.quality.label}")
        appendLine("Score Percentage: ${state.signal.scorePercentage}%")
        appendLine()
        appendLine("[TDD BAND CHECK (B40 / B41)]")
        val tddSeen = state.seenCells.filter { it.techType == "LTE" && (it.band == "B40" || it.band == "B41") }
        if (tddSeen.isEmpty()) {
            appendLine("Not detected")
        } else {
            tddSeen.forEach {
                appendLine("${if (it.wasServing) "Serving" else "Neighbor"}: ${it.band} | PCI: ${it.pci} | EARFCN: ${it.arfcn} | Best RSRP: ${it.bestRsrp ?: "---"} dBm | Seen: ${it.seenCount}x")
            }
        }
        appendLine()
        appendLine("[BAND SUPPORT CHECK]")
        val missingBands = missingWatchedBands(context, state.seenCells)
        appendLine(if (missingBands.isEmpty()) "All watched bands detected" else "Not detected: ${missingBands.joinToString(", ")}")
        appendLine()
        appendLine("[NEIGHBORS (${state.neighbors.size})]")
        state.neighbors.forEachIndexed { i, n ->
            appendLine("Tower #${i + 1}: ${n.techType} ${n.band} | PCI: ${n.pci} | ARFCN: ${n.arfcn} | RSRP: ${n.rsrp ?: "---"} dBm | Delta: ${n.deltaRsrp ?: "---"} dB")
        }
        appendLine()
        appendLine("[ALL CELLS SEEN (${state.seenCells.size})]")
        state.seenCells.forEach { c ->
            appendLine("${c.techType} ${c.band} | PCI: ${c.pci} | ARFCN: ${c.arfcn} | Best: ${c.bestRsrp ?: "---"} dBm | Last: ${c.lastRsrp ?: "---"} dBm | Seen: ${c.seenCount}x${if (c.wasServing) " | was serving" else ""}")
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "FIELD DIAGNOSTICS & EXPORT",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Cell Telemetry Export", fullDump))
                            Toast.makeText(context, "Full telemetry report copied!", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier
                            .size(36.dp)
                            .background(TechCardSurface, RoundedCornerShape(10.dp))
                            .border(1.dp, TechCardBorder, RoundedCornerShape(10.dp))
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy report",
                            tint = TechCyan,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = {
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "Cell Monitor telemetry report")
                                putExtra(Intent.EXTRA_TEXT, fullDump)
                            }
                            context.startActivity(Intent.createChooser(send, "Share cell report"))
                        },
                        modifier = Modifier
                            .size(36.dp)
                            .background(TechCyan.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share report",
                            tint = TechCyan,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(TechCardSurface, RoundedCornerShape(16.dp))
                    .border(1.dp, TechCardBorder, RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Text(
                    text = fullDump,
                    fontSize = 11.sp,
                    color = TextSecondary,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 16.sp
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}


@Composable
private fun CellMapperButton(state: com.example.cellmonitor.data.CellMonitorState) {
    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxWidth()) {
        Button(
            onClick = { openCellMapper(context, state) },
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = TechCyan.copy(alpha = 0.15f),
                contentColor = TechCyan
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Map,
                contentDescription = "Open CellMapper",
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Find tower on CellMapper", fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        Text(
            text = "Opens CellMapper near your last known location, filtered to your carrier. Match the eNB ID above with the towers on the map.",
            fontSize = 10.sp,
            color = TextMuted,
            modifier = Modifier.padding(top = 6.dp, start = 4.dp, end = 4.dp)
        )
    }
}

private fun openCellMapper(context: Context, state: com.example.cellmonitor.data.CellMonitorState) {
    if (state.isDemoMode) {
        Toast.makeText(context, "Simulation is on - turn it off to use your real carrier", Toast.LENGTH_LONG).show()
        return
    }

    val mcc = state.carrier.mcc.takeIf { it.isNotEmpty() && it.all(Char::isDigit) }
    val mnc = state.carrier.mnc.toIntOrNull()
    val type = when (state.servingCell?.tech) {
        RadioTech.NR_SA -> "NR"
        RadioTech.WCDMA -> "UMTS"
        RadioTech.GSM -> "GSM"
        else -> "LTE"
    }

    // Last known phone location (no extra services needed)
    var lat: Double? = null
    var lon: Double? = null
    if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
        try {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val best = listOf(
                LocationManager.GPS_PROVIDER,
                LocationManager.NETWORK_PROVIDER,
                LocationManager.PASSIVE_PROVIDER
            ).mapNotNull { provider -> runCatching { lm.getLastKnownLocation(provider) }.getOrNull() }
                .maxByOrNull { it.time }
            lat = best?.latitude
            lon = best?.longitude
        } catch (e: SecurityException) {
            // ignored - falls back to default map view
        }
    }

    if (lat == null || lon == null) {
        Toast.makeText(
            context,
            "No saved location yet - turn on Location and open Google Maps once, then try again",
            Toast.LENGTH_LONG
        ).show()
    }

    val builder = Uri.Builder().scheme("https").authority("www.cellmapper.net").path("map")
    if (mcc != null) builder.appendQueryParameter("MCC", mcc)
    if (mnc != null) builder.appendQueryParameter("MNC", mnc.toString())
    builder.appendQueryParameter("type", type)
    if (lat != null && lon != null) {
        builder.appendQueryParameter("latitude", String.format(Locale.US, "%.6f", lat))
        builder.appendQueryParameter("longitude", String.format(Locale.US, "%.6f", lon))
        builder.appendQueryParameter("zoom", "15")
    }
    builder.appendQueryParameter("showTowers", "true")

    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, builder.build()))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "No browser found to open CellMapper", Toast.LENGTH_SHORT).show()
    }
}
