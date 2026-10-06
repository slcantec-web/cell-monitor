package com.example.cellmonitor.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.telephony.CellIdentityLte
import android.telephony.CellIdentityNr
import android.telephony.CellInfo
import android.telephony.CellInfoGsm
import android.telephony.CellInfoLte
import android.telephony.CellInfoNr
import android.telephony.CellInfoWcdma
import android.telephony.CellSignalStrengthGsm
import android.telephony.CellSignalStrengthLte
import android.telephony.CellSignalStrengthNr
import android.telephony.CellSignalStrengthWcdma
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyDisplayInfo
import android.telephony.TelephonyManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

class CellMonitorRepository(
    private val context: Context,
    private val scope: CoroutineScope
) {
    private val telephonyManager: TelephonyManager? =
        context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager

    private val _state = MutableStateFlow(CellMonitorState())
    val state: StateFlow<CellMonitorState> = _state.asStateFlow()

    private var autoRefreshJob: Job? = null

    // --- Running log of every cell the phone has reported since the last clear ---
    private data class CellObservation(
        val techType: String,
        val band: String,
        val arfcn: Int,
        val pci: Int,
        val rsrp: Int?,
        val serving: Boolean
    )

    private val seenMap = LinkedHashMap<String, SeenCell>()

    private fun recordSeen(obs: List<CellObservation>) {
        val now = System.currentTimeMillis()
        synchronized(seenMap) {
            obs.forEach { o ->
                val key = "${o.techType}|${o.band}|${o.arfcn}|${o.pci}"
                val old = seenMap[key]
                seenMap[key] = SeenCell(
                    techType = o.techType,
                    band = o.band,
                    arfcn = o.arfcn,
                    pci = o.pci,
                    wasServing = (old?.wasServing == true) || o.serving,
                    lastRsrp = o.rsrp ?: old?.lastRsrp,
                    bestRsrp = listOfNotNull(old?.bestRsrp, o.rsrp).maxOrNull(),
                    firstSeenMs = old?.firstSeenMs ?: now,
                    lastSeenMs = now,
                    seenCount = (old?.seenCount ?: 0) + 1
                )
            }
        }
    }

    private fun seenSnapshot(): List<SeenCell> = synchronized(seenMap) { seenMap.values.toList() }

    fun clearSeenCells() {
        synchronized(seenMap) { seenMap.clear() }
        _state.update { it.copy(seenCells = emptyList()) }
    }
    private val maxHistoryPoints = 30

    // --- Phone's own "display" network type (what drives the 5G icon in the status bar) ---
    @Volatile private var displayOverride: Int = TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NONE
    @Volatile private var displayNetworkType: Int = TelephonyManager.NETWORK_TYPE_UNKNOWN
    private var displayListenerRegistered = false
    private var displayListenerRef: Any? = null

    private val displayIs5g: Boolean
        get() = displayOverride == TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA ||
            displayOverride == 4 /* NR_NSA_MMWAVE (legacy) */ ||
            displayOverride == 5 /* NR_ADVANCED */ ||
            displayNetworkType == TelephonyManager.NETWORK_TYPE_NR

    private fun displayLabel(): String = when {
        displayOverride == 5 -> "5G+ (Advanced)"
        displayOverride == 4 -> "5G NSA mmWave"
        displayOverride == TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA -> "5G NSA"
        displayNetworkType == TelephonyManager.NETWORK_TYPE_NR -> "5G SA"
        displayOverride == TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_LTE_ADVANCED_PRO -> "LTE-A Pro (5Ge)"
        displayOverride == TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_LTE_CA -> "LTE-CA"
        displayNetworkType == TelephonyManager.NETWORK_TYPE_LTE -> "LTE"
        displayNetworkType == TelephonyManager.NETWORK_TYPE_UNKNOWN -> "---"
        else -> "Other"
    }

    private fun onDisplayInfo(info: TelephonyDisplayInfo) {
        displayOverride = info.overrideNetworkType
        displayNetworkType = info.networkType
        refresh()
    }

    @SuppressLint("MissingPermission")
    private fun registerDisplayListener() {
        val tm = telephonyManager ?: return
        if (displayListenerRegistered) return
        if (context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) return
        displayListenerRegistered = true
        Handler(Looper.getMainLooper()).post {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val cb = object : TelephonyCallback(), TelephonyCallback.DisplayInfoListener {
                        override fun onDisplayInfoChanged(telephonyDisplayInfo: TelephonyDisplayInfo) {
                            onDisplayInfo(telephonyDisplayInfo)
                        }
                    }
                    displayListenerRef = cb
                    tm.registerTelephonyCallback(context.mainExecutor, cb)
                } else {
                    @Suppress("DEPRECATION")
                    val listener = object : PhoneStateListener() {
                        @Deprecated("Deprecated in Java")
                        override fun onDisplayInfoChanged(telephonyDisplayInfo: TelephonyDisplayInfo) {
                            onDisplayInfo(telephonyDisplayInfo)
                        }
                    }
                    displayListenerRef = listener
                    @Suppress("DEPRECATION")
                    tm.listen(listener, PhoneStateListener.LISTEN_DISPLAY_INFO_CHANGED)
                }
            } catch (e: Exception) {
                displayListenerRegistered = false
            }
        }
    }

    init {
        checkPermissions()
        refresh()
    }

    fun checkPermissions(): Boolean {
        val fineLocation = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val phoneState = context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
        val granted = fineLocation && phoneState
        _state.update { it.copy(isPermissionGranted = granted) }
        if (phoneState) registerDisplayListener()
        return granted
    }

    fun setAutoRefresh(enabled: Boolean) {
        _state.update { it.copy(isAutoRefresh = enabled) }
        restartAutoRefreshJob()
    }

    fun setRefreshInterval(seconds: Int) {
        _state.update { it.copy(refreshIntervalSec = seconds.coerceIn(1, 10)) }
        if (_state.value.isAutoRefresh) {
            restartAutoRefreshJob()
        }
    }

    fun toggleDemoMode() {
        val newDemo = !_state.value.isDemoMode
        _state.update { it.copy(isDemoMode = newDemo) }
        refresh()
    }

    private fun restartAutoRefreshJob() {
        autoRefreshJob?.cancel()
        if (_state.value.isAutoRefresh) {
            autoRefreshJob = scope.launch(Dispatchers.Default) {
                while (isActive) {
                    delay(_state.value.refreshIntervalSec * 1000L)
                    refreshInternal()
                }
            }
        }
    }

    fun refresh() {
        scope.launch(Dispatchers.Default) {
            refreshInternal()
        }
    }

    @SuppressLint("MissingPermission")
    private fun refreshInternal() {
        _state.update { it.copy(isRefreshing = true) }

        if (!checkPermissions()) {
            if (_state.value.isDemoMode) {
                simulateCellData()
            } else {
                _state.update { it.copy(isRefreshing = false) }
            }
            return
        }

        if (_state.value.isDemoMode) {
            simulateCellData()
            return
        }

        val tm = telephonyManager
        if (tm == null) {
            simulateCellData()
            return
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tm.requestCellInfoUpdate(context.mainExecutor, object : TelephonyManager.CellInfoCallback() {
                    override fun onCellInfo(cellInfo: MutableList<CellInfo>) {
                        processCellInfo(cellInfo)
                    }

                    override fun onError(errorCode: Int, detail: Throwable?) {
                        processCellInfo(tm.allCellInfo ?: emptyList())
                    }
                })
            } else {
                processCellInfo(tm.allCellInfo ?: emptyList())
            }
        } catch (e: SecurityException) {
            _state.update { it.copy(isRefreshing = false) }
        } catch (e: Exception) {
            processCellInfo(tm.allCellInfo ?: emptyList())
        }
    }

    @SuppressLint("MissingPermission")
    private fun processCellInfo(cells: List<CellInfo>) {
        val tm = telephonyManager

        // If no hardware cell towers are detected (typical on emulator without SIM),
        // we can check if all cells are empty.
        if (cells.isEmpty() && _state.value.servingCell == null) {
            // Check if user is in an emulator/no SIM environment
            val operator = tm?.networkOperatorName.orEmpty()
            if (operator.isBlank() || operator.equals("Android", ignoreCase = true)) {
                // Auto enable demo mode so emulator displays rich UI instead of blank
                _state.update { it.copy(isDemoMode = true) }
                simulateCellData()
                return
            }
        }

        val lteCells = cells.filterIsInstance<CellInfoLte>()
        val nrCells = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            cells.filterIsInstance<CellInfoNr>()
        } else emptyList()
        val wcdmaCells = cells.filterIsInstance<CellInfoWcdma>()
        val gsmCells = cells.filterIsInstance<CellInfoGsm>()

        val registeredLte = lteCells.firstOrNull { it.isRegistered }
        val registeredNr = nrCells.firstOrNull { it.isRegistered }
        val registeredWcdma = wcdmaCells.firstOrNull { it.isRegistered }
        val registeredGsm = gsmCells.firstOrNull { it.isRegistered }

        val isNrConnected = registeredNr != null || displayIs5g || (try {
            tm?.serviceState?.toString()?.contains("nrState=CONNECTED") == true
        } catch (e: Exception) {
            false
        })

        val nrServing = registeredNr ?: if (isNrConnected) nrCells.firstOrNull() else null

        val tech = when {
            registeredNr != null && registeredLte == null -> RadioTech.NR_SA
            isNrConnected && registeredLte != null -> RadioTech.NR_NSA
            registeredLte != null -> RadioTech.LTE
            registeredWcdma != null -> RadioTech.WCDMA
            registeredGsm != null -> RadioTech.GSM
            else -> RadioTech.UNKNOWN
        }

        // Build Carrier Info
        val rawPlmn = tm?.networkOperator.orEmpty()
        val mcc = if (rawPlmn.length >= 3) rawPlmn.substring(0, 3) else "---"
        val mnc = if (rawPlmn.length > 3) rawPlmn.substring(3) else "---"
        val opName = tm?.networkOperatorName?.ifBlank { "Carrier" } ?: "Carrier"
        val simOp = tm?.simOperatorName?.ifBlank { opName } ?: opName

        val carrier = CarrierInfo(
            operatorName = opName,
            simOperator = simOp,
            mcc = mcc,
            mnc = mnc,
            countryCode = tm?.networkCountryIso?.uppercase() ?: "US",
            isRoaming = tm?.isNetworkRoaming ?: false,
            simState = when (tm?.simState) {
                TelephonyManager.SIM_STATE_READY -> "READY"
                TelephonyManager.SIM_STATE_ABSENT -> "NO SIM"
                else -> "ACTIVE"
            },
            dataNetworkType = tech.displayTitle,
            displayType = displayLabel()
        )

        // Build Serving Cell & Signal
        var servingCell: ServingCell? = null
        var signalMetrics = SignalMetrics()

        if (nrServing != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val id = nrServing.cellIdentity as? CellIdentityNr
            val strength = nrServing.cellSignalStrength as? CellSignalStrengthNr
            val band = id?.bands?.firstOrNull() ?: 0
            val nrarfcn = id?.nrarfcn ?: 0
            val pci = id?.pci ?: 0
            val nci = id?.nci ?: 0L
            val tac = id?.tac ?: 0

            val rsrp = strength?.ssRsrp?.takeIf { it != Int.MAX_VALUE && it in -140..-44 }
            val rsrq = strength?.ssRsrq?.takeIf { it != Int.MAX_VALUE }
            val sinr = strength?.ssSinr?.takeIf { it != Int.MAX_VALUE }

            val (gnb, sector) = BandCalculators.parseNodeBAndSector(nci)

            servingCell = ServingCell(
                tech = tech,
                band = BandCalculators.getNrBandString(band),
                bandName = BandCalculators.getNrBandName(band),
                duplex = BandCalculators.getNrDuplex(band),
                frequencyMhz = BandCalculators.calculateNrFrequencyMhz(nrarfcn),
                arfcn = nrarfcn,
                pci = pci,
                cellId = nci,
                nodeBId = gnb,
                sectorId = sector,
                tac = tac,
                bandwidth = "100 MHz"
            )

            val quality = BandCalculators.evaluateSignalQuality(rsrp)
            val score = BandCalculators.calculateSignalPercentage(rsrp)
            signalMetrics = SignalMetrics(
                rsrp = rsrp,
                rsrq = rsrq,
                sinr = sinr,
                asu = strength?.asuLevel?.takeIf { it != Int.MAX_VALUE },
                quality = quality,
                scorePercentage = score
            )
        } else if (registeredLte != null) {
            val id = registeredLte.cellIdentity as CellIdentityLte
            val strength = registeredLte.cellSignalStrength as CellSignalStrengthLte
            val earfcn = id.earfcn
            val pci = id.pci
            val ci = id.ci.toLong()
            val tac = id.tac

            val rsrp = strength.rsrp.takeIf { it != Int.MAX_VALUE && it in -140..-44 }
            val rsrq = strength.rsrq.takeIf { it != Int.MAX_VALUE }
            val rssnr = strength.rssnr.takeIf { it != Int.MAX_VALUE }
            val ta = strength.timingAdvance.takeIf { it != Int.MAX_VALUE && it >= 0 }
            val cqi = strength.cqi.takeIf { it != Int.MAX_VALUE && it >= 0 }

            val (enb, sector) = BandCalculators.parseNodeBAndSector(ci)

            servingCell = ServingCell(
                tech = tech,
                band = BandCalculators.getLteBandString(earfcn),
                bandName = BandCalculators.getLteBandName(earfcn),
                duplex = BandCalculators.getLteDuplex(earfcn),
                frequencyMhz = BandCalculators.calculateLteFrequencyMhz(earfcn),
                arfcn = earfcn,
                pci = pci,
                cellId = ci,
                nodeBId = enb,
                sectorId = sector,
                tac = tac,
                bandwidth = if (id.bandwidth != Int.MAX_VALUE && id.bandwidth > 0) "${id.bandwidth / 1000} MHz" else "20 MHz"
            )

            val quality = BandCalculators.evaluateSignalQuality(rsrp)
            val score = BandCalculators.calculateSignalPercentage(rsrp)
            signalMetrics = SignalMetrics(
                rsrp = rsrp,
                rsrq = rsrq,
                sinr = rssnr,
                rssi = strength.rssi.takeIf { it != Int.MAX_VALUE },
                asu = strength.asuLevel.takeIf { it != Int.MAX_VALUE },
                cqi = cqi,
                timingAdvance = ta,
                quality = quality,
                scorePercentage = score
            )
        }

        // Parse Neighbor Cells
        val neighborsList = mutableListOf<NeighborCell>()
        val servingRsrp = signalMetrics.rsrp

        lteCells.filter { !it.isRegistered }.forEach { lte ->
            val id = lte.cellIdentity
            val str = lte.cellSignalStrength
            val rsrp = str.rsrp.takeIf { it != Int.MAX_VALUE && it in -140..-44 }
            val delta = if (rsrp != null && servingRsrp != null) rsrp - servingRsrp else null
            neighborsList.add(
                NeighborCell(
                    techType = "LTE",
                    band = BandCalculators.getLteBandString(id.earfcn),
                    arfcn = id.earfcn,
                    pci = id.pci,
                    rsrp = rsrp,
                    rsrq = str.rsrq.takeIf { it != Int.MAX_VALUE },
                    deltaRsrp = delta
                )
            )
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            nrCells.filter { !it.isRegistered }.forEach { nr ->
                val id = nr.cellIdentity as? CellIdentityNr
                val str = nr.cellSignalStrength as? CellSignalStrengthNr
                val band = id?.bands?.firstOrNull() ?: 0
                val rsrp = str?.ssRsrp?.takeIf { it != Int.MAX_VALUE && it in -140..-44 }
                val delta = if (rsrp != null && servingRsrp != null) rsrp - servingRsrp else null
                neighborsList.add(
                    NeighborCell(
                        techType = "5G NR",
                        band = BandCalculators.getNrBandString(band),
                        arfcn = id?.nrarfcn ?: 0,
                        pci = id?.pci ?: 0,
                        rsrp = rsrp,
                        rsrq = str?.ssRsrq?.takeIf { it != Int.MAX_VALUE },
                        deltaRsrp = delta
                    )
                )
            }
        }

        // Record every reported cell (all technologies) in the running log
        val observations = mutableListOf<CellObservation>()
        lteCells.forEach { c ->
            val id = c.cellIdentity
            val rsrp = c.cellSignalStrength.rsrp.takeIf { it != Int.MAX_VALUE && it in -140..-44 }
            val earfcn = id.earfcn.takeIf { it != Int.MAX_VALUE } ?: 0
            val pci = id.pci.takeIf { it != Int.MAX_VALUE } ?: 0
            if (earfcn > 0 || pci > 0) {
                observations.add(
                    CellObservation("LTE", BandCalculators.getLteBandString(earfcn), earfcn, pci, rsrp, c.isRegistered)
                )
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            nrCells.forEach { c ->
                val id = c.cellIdentity as? CellIdentityNr
                val str = c.cellSignalStrength as? CellSignalStrengthNr
                val band = id?.bands?.firstOrNull() ?: 0
                val arfcn = id?.nrarfcn?.takeIf { it != Int.MAX_VALUE } ?: 0
                val pci = id?.pci?.takeIf { it != Int.MAX_VALUE } ?: 0
                val rsrp = str?.ssRsrp?.takeIf { it != Int.MAX_VALUE && it in -140..-44 }
                if (arfcn > 0 || pci > 0) {
                    observations.add(
                        CellObservation("5G NR", BandCalculators.getNrBandString(band), arfcn, pci, rsrp, c.isRegistered)
                    )
                }
            }
        }
        wcdmaCells.forEach { c ->
            val id = c.cellIdentity
            val arfcn = id.uarfcn.takeIf { it != Int.MAX_VALUE } ?: 0
            val psc = id.psc.takeIf { it != Int.MAX_VALUE } ?: 0
            val dbm = c.cellSignalStrength.dbm.takeIf { it != Int.MAX_VALUE && it in -140..-30 }
            if (arfcn > 0 || psc > 0) {
                observations.add(CellObservation("3G", "UMTS", arfcn, psc, dbm, c.isRegistered))
            }
        }
        gsmCells.forEach { c ->
            val id = c.cellIdentity
            val arfcn = id.arfcn.takeIf { it != Int.MAX_VALUE } ?: 0
            val bsic = id.bsic.takeIf { it != Int.MAX_VALUE } ?: 0
            val dbm = c.cellSignalStrength.dbm.takeIf { it != Int.MAX_VALUE && it in -140..-30 }
            if (arfcn > 0) {
                observations.add(CellObservation("2G", "GSM", arfcn, bsic, dbm, c.isRegistered))
            }
        }
        recordSeen(observations)

        updateWithNewMetrics(carrier, servingCell, signalMetrics, neighborsList)
    }

    private fun updateWithNewMetrics(
        carrier: CarrierInfo,
        servingCell: ServingCell?,
        signal: SignalMetrics,
        neighbors: List<NeighborCell>
    ) {
        val now = System.currentTimeMillis()
        val currentHistory = _state.value.signalHistory.toMutableList()

        signal.rsrp?.let { rsrpVal ->
            currentHistory.add(SignalHistoryPoint(now, rsrpVal))
            if (currentHistory.size > maxHistoryPoints) {
                currentHistory.removeAt(0)
            }
        }

        _state.update {
            it.copy(
                isRefreshing = false,
                lastUpdatedMs = now,
                carrier = carrier,
                servingCell = servingCell,
                signal = signal,
                neighbors = neighbors.sortedByDescending { n -> n.rsrp ?: -999 },
                signalHistory = currentHistory,
                seenCells = seenSnapshot()
            )
        }
    }

    // Realistic Cellular RF Simulation for emulator & field testing
    private var simStep = 0
    private fun simulateCellData() {
        simStep++
        val now = System.currentTimeMillis()

        // Base fluctuating RSRP between -82 and -94 dBm with natural RF drift
        val jitter = (kotlin.math.sin(simStep * 0.4) * 6).toInt() + Random.nextInt(-2, 3)
        val simRsrp = (-86 + jitter).coerceIn(-120, -60)
        val simRsrq = (-10 + (jitter / 2)).coerceIn(-20, -5)
        val simSinr = (18 + jitter).coerceIn(0, 30)

        val carrier = CarrierInfo(
            operatorName = "5G Ultra Spectrum",
            simOperator = "5G Ultra Spectrum",
            mcc = "310",
            mnc = "260",
            countryCode = "US",
            isRoaming = false,
            simState = "READY",
            dataNetworkType = "5G NSA",
            displayType = "5G NSA"
        )

        val servingCell = ServingCell(
            tech = RadioTech.NR_NSA,
            band = "n78",
            bandName = "3500 C-Band",
            duplex = "TDD",
            frequencyMhz = "3650.0 MHz",
            arfcn = 643334,
            pci = 384,
            cellId = 268435456L + (simStep % 4),
            nodeBId = 1048576L,
            sectorId = 1 + (simStep % 3),
            tac = 21450,
            bandwidth = "100 MHz"
        )

        val quality = BandCalculators.evaluateSignalQuality(simRsrp)
        val score = BandCalculators.calculateSignalPercentage(simRsrp)

        val signal = SignalMetrics(
            rsrp = simRsrp,
            rsrq = simRsrq,
            sinr = simSinr,
            rssi = simRsrp + 25,
            asu = (simRsrp + 140).coerceIn(0, 97),
            cqi = 14,
            timingAdvance = 480,
            quality = quality,
            scorePercentage = score
        )

        val neighbors = listOf(
            NeighborCell("5G NR", "n78", 643334, 385, simRsrp - 8, -12, -8),
            NeighborCell("5G NR", "n41", 504990, 112, simRsrp - 14, -14, -14),
            NeighborCell("LTE", "B3", 1500, 204, simRsrp - 11, -11, -11),
            NeighborCell("LTE", "B7", 3100, 88, simRsrp - 19, -16, -19),
            NeighborCell("LTE", "B20", 6300, 412, simRsrp - 22, -18, -22)
        )

        recordSeen(
            listOf(
                CellObservation(
                    if (servingCell.band.startsWith("n")) "5G NR" else "LTE",
                    servingCell.band, servingCell.arfcn, servingCell.pci, simRsrp, true
                )
            ) + neighbors.map { CellObservation(it.techType, it.band, it.arfcn, it.pci, it.rsrp, false) }
        )

        updateWithNewMetrics(carrier, servingCell, signal, neighbors)
    }
}
