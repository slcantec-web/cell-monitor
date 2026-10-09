package com.example.cellmonitor.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.telephony.CellIdentityGsm
import android.telephony.CellIdentityLte
import android.telephony.CellIdentityNr
import android.telephony.CellIdentityWcdma
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
import android.telephony.SubscriptionManager
import android.telephony.TelephonyCallback
import android.telephony.TelephonyDisplayInfo
import android.telephony.TelephonyManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
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

    // Declared before init{} on purpose: init starts a refresh coroutine that uses it.
    private val refreshMutex = Mutex()

    // --- Running log of every cell the phone has reported since the last clear ---
    private data class CellObservation(
        val techType: String,
        val band: String,
        val arfcn: Int,
        val pci: Int,
        val rsrp: Int?,
        val serving: Boolean,
        val operatorName: String = "---",
        val mcc: String = "---",
        val mnc: String = "---",
        val cellId: Long? = null
    )

    private val seenMap = LinkedHashMap<String, SeenCell>()

    private fun recordSeen(obs: List<CellObservation>) {
        val now = System.currentTimeMillis()
        synchronized(seenMap) {
            obs.forEach { o ->
                // Key includes operator so the same PCI on Dialog vs Hutch is distinct
                val key = "${o.operatorName}|${o.techType}|${o.band}|${o.arfcn}|${o.pci}|${o.cellId ?: 0}"
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
                    seenCount = (old?.seenCount ?: 0) + 1,
                    operatorName = o.operatorName,
                    mcc = o.mcc,
                    mnc = o.mnc,
                    cellId = o.cellId ?: old?.cellId
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
        // The seen-cells log, TDD check and band check only fill while refreshing,
        // so scanning starts automatically (the play/stop button still controls it).
        setAutoRefresh(true)
    }

    fun checkPermissions(): Boolean {
        val fineLocation = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val phoneState = context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
        val granted = fineLocation && phoneState
        // With the Location switch off Android returns an empty cell list.
        val locationOn = try {
            (context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager)?.isLocationEnabled ?: true
        } catch (e: Exception) {
            true
        }
        _state.update { it.copy(isPermissionGranted = granted, locationEnabled = locationOn) }
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

    /** One active SIM / subscription and the TelephonyManager bound to it. */
    private class SimSource(
        val tm: TelephonyManager,
        val subId: Int,
        val slot: Int,
        val isData: Boolean
    )

    /** A CellInfo tagged with the SIM that reported it (and that SIM's registered PLMN). */
    private class SimCell(
        val info: CellInfo,
        val slot: Int,
        val isData: Boolean,
        val fbMcc: String,
        val fbMnc: String
    )

    private fun looksLikeEmulator(): Boolean =
        Build.FINGERPRINT.startsWith("generic") ||
            Build.FINGERPRINT.contains("emulator") ||
            Build.MODEL.contains("Emulator") ||
            Build.MODEL.contains("Android SDK built for") ||
            Build.HARDWARE.contains("goldfish") ||
            Build.HARDWARE.contains("ranchu") ||
            Build.PRODUCT.contains("sdk_gphone")

    /**
     * Every active subscription (dual-SIM etc.) with its own TelephonyManager.
     * Exactly one entry is flagged as the data SIM; it drives the serving cell and carrier card.
     */
    @SuppressLint("MissingPermission")
    private fun simSources(): List<SimSource> {
        val defaultTm = telephonyManager ?: return emptyList()
        val result = ArrayList<SimSource>()
        try {
            val sm = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
            val dataSubId = SubscriptionManager.getDefaultDataSubscriptionId()
            val subs = sm?.activeSubscriptionInfoList
            if (!subs.isNullOrEmpty()) {
                for (info in subs) {
                    val tmForSub = try {
                        defaultTm.createForSubscriptionId(info.subscriptionId)
                    } catch (e: Exception) {
                        null
                    }
                    if (tmForSub == null) continue
                    result.add(
                        SimSource(tmForSub, info.subscriptionId, info.simSlotIndex, info.subscriptionId == dataSubId)
                    )
                }
            }
        } catch (e: Exception) {
            // fall through to the default manager
        }
        if (result.isEmpty()) {
            result.add(SimSource(defaultTm, -1, 0, true))
        } else if (result.none { it.isData }) {
            // Default data subscription unknown: treat the first SIM as the data SIM
            val first = result[0]
            result[0] = SimSource(first.tm, first.subId, first.slot, true)
        }
        return result
    }

    /**
     * Fresh cell info for one SIM. Waits for the radio callback, but never longer than 4 s
     * so a missing callback cannot leave the app stuck in "refreshing".
     */
    @SuppressLint("MissingPermission")
    private suspend fun cellInfoFor(src: SimSource): List<CellInfo> {
        val tm = src.tm
        val fresh: List<CellInfo>? = withTimeoutOrNull(4000L) {
            suspendCancellableCoroutine<List<CellInfo>?> { cont ->
                try {
                    tm.requestCellInfoUpdate(context.mainExecutor, object : TelephonyManager.CellInfoCallback() {
                        override fun onCellInfo(cellInfo: MutableList<CellInfo>) {
                            if (cont.isActive) cont.resume(cellInfo)
                        }

                        override fun onError(errorCode: Int, detail: Throwable?) {
                            if (cont.isActive) cont.resume(null)
                        }
                    })
                } catch (e: Exception) {
                    if (cont.isActive) cont.resume(null)
                }
            }
        }
        if (fresh != null) return fresh
        return try {
            tm.allCellInfo ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private suspend fun refreshInternal() {
        // One scan at a time: manual refresh, auto refresh and display-info callbacks can overlap.
        if (!refreshMutex.tryLock()) return
        try {
            refreshLocked()
        } finally {
            refreshMutex.unlock()
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun refreshLocked() {
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

        val sources = simSources()
        if (sources.isEmpty()) {
            // No telephony hardware. Never fabricate data while the screen says LIVE.
            _state.update { it.copy(isRefreshing = false) }
            return
        }

        try {
            // Ask every SIM at the same time and process once, when all of them answered.
            val perSim = coroutineScope {
                sources.map { src -> async { src to cellInfoFor(src) } }.awaitAll()
            }
            processCellInfo(perSim)
        } catch (e: CancellationException) {
            _state.update { it.copy(isRefreshing = false) }
            throw e
        } catch (e: Exception) {
            _state.update { it.copy(isRefreshing = false) }
        }
    }

    /** Stable key so the same physical cell from two SIMs is not duplicated. */
    private fun cellDedupeKey(cell: CellInfo): String {
        return when (cell) {
            is CellInfoLte -> {
                val id = cell.cellIdentity
                "LTE|${id.earfcn}|${id.pci}|${id.ci}"
            }
            is CellInfoNr -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val id = cell.cellIdentity as? CellIdentityNr
                    "NR|${id?.nrarfcn}|${id?.pci}|${id?.nci}"
                } else ""
            }
            is CellInfoWcdma -> {
                val id = cell.cellIdentity
                "WCDMA|${id.uarfcn}|${id.psc}|${id.cid}"
            }
            is CellInfoGsm -> {
                val id = cell.cellIdentity
                "GSM|${id.arfcn}|${id.bsic}|${id.cid}"
            }
            else -> cell.toString()
        }
    }

    /** Registered network PLMN of one SIM (fallback when a cell identity omits MCC/MNC). */
    @SuppressLint("MissingPermission")
    private fun plmnOf(tm: TelephonyManager?): Pair<String, String> {
        val raw = tm?.networkOperator.orEmpty()
        val mcc = if (raw.length >= 3) raw.substring(0, 3) else "---"
        val mnc = if (raw.length > 3) raw.substring(3) else "---"
        return mcc to mnc
    }

    /** "B3", "B40"... from the EARFCN table, or from the identity's own band list for bands the table lacks. */
    private fun lteBand(id: CellIdentityLte): String {
        val fromTable = BandCalculators.getLteBandString(id.earfcn)
        if (fromTable != "B?" && fromTable != "---") return fromTable
        val b = try {
            id.bands.firstOrNull()
        } catch (e: Exception) {
            null
        }
        return if (b != null && b > 0 && b != Int.MAX_VALUE) "B$b" else fromTable
    }

    /**
     * Extract MCC/MNC/operator from a CellIdentity when the platform exposes them.
     * [fallback] is the registered PLMN of the SIM that reported the cell, so a second SIM's
     * cells are never labelled with the first SIM's operator.
     */
    private fun plmnFromIdentity(identity: Any?, fallback: Pair<String, String>): Triple<String, String, String> {
        var mcc = "---"
        var mnc = "---"
        when (identity) {
            is CellIdentityLte -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    mcc = identity.mccString?.takeIf { it.isNotBlank() } ?: "---"
                    mnc = identity.mncString?.takeIf { it.isNotBlank() } ?: "---"
                }
            }
            is CellIdentityNr -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    mcc = identity.mccString?.takeIf { it.isNotBlank() } ?: "---"
                    mnc = identity.mncString?.takeIf { it.isNotBlank() } ?: "---"
                }
            }
            is CellIdentityWcdma -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    mcc = identity.mccString?.takeIf { it.isNotBlank() } ?: "---"
                    mnc = identity.mncString?.takeIf { it.isNotBlank() } ?: "---"
                }
            }
            is CellIdentityGsm -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    mcc = identity.mccString?.takeIf { it.isNotBlank() } ?: "---"
                    mnc = identity.mncString?.takeIf { it.isNotBlank() } ?: "---"
                }
            }
        }
        // Many devices omit MCC/MNC on neighbor CellIdentity; use the reporting SIM's PLMN
        if (mcc == "---" || mnc == "---") {
            if (mcc == "---") mcc = fallback.first
            if (mnc == "---") mnc = fallback.second
        }
        val op = OperatorNames.resolve(null, mcc, mnc)
        return Triple(mcc, mnc, op)
    }

    @SuppressLint("MissingPermission")
    private fun processCellInfo(perSim: List<Pair<SimSource, List<CellInfo>>>) {
        // The data SIM drives the serving cell and the carrier card
        val primary = perSim.firstOrNull { it.first.isData } ?: perSim.firstOrNull()
        val tm = primary?.first?.tm ?: telephonyManager

        // Tag every cell with the SIM that reported it. If two SIMs report the same physical
        // cell keep the registered copy, then the copy from the data SIM.
        val tagged = LinkedHashMap<String, SimCell>()
        for ((src, list) in perSim) {
            val (fbMcc, fbMnc) = plmnOf(src.tm)
            for (c in list) {
                val key = cellDedupeKey(c)
                if (key.isEmpty()) continue
                val old = tagged[key]
                val better = old == null ||
                    (!old.info.isRegistered && c.isRegistered) ||
                    (old.info.isRegistered == c.isRegistered && !old.isData && src.isData)
                if (better) tagged[key] = SimCell(c, src.slot, src.isData, fbMcc, fbMnc)
            }
        }
        val cells = tagged.values.map { it.info }
        val simOf = java.util.IdentityHashMap<CellInfo, SimCell>()
        tagged.values.forEach { simOf[it.info] = it }

        fun fromData(c: CellInfo): Boolean = simOf[c]?.isData != false
        fun fallbackOf(c: CellInfo): Pair<String, String> =
            simOf[c]?.let { it.fbMcc to it.fbMnc } ?: plmnOf(tm)
        /** SIM slot (0-based) when the cell came from a SIM other than the data SIM. */
        fun otherSlot(c: CellInfo): Int? = simOf[c]?.takeIf { !it.isData && it.slot >= 0 }?.slot

        // Only the emulator may fall back to simulated data. On a real phone an empty list
        // means "nothing reported" (location off, no signal...) and must be shown as such.
        if (cells.isEmpty() && _state.value.servingCell == null && looksLikeEmulator()) {
            val operator = tm?.networkOperatorName.orEmpty()
            if (operator.isBlank() || operator.equals("Android", ignoreCase = true)) {
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

        // Serving = registered cell of the DATA SIM. The other SIM's registered cells stay in
        // the neighbor list (tagged with their SIM) instead of disappearing.
        val registeredLte = lteCells.firstOrNull { it.isRegistered && fromData(it) }
        val registeredNr = nrCells.firstOrNull { it.isRegistered && fromData(it) }
        val registeredWcdma = wcdmaCells.firstOrNull { it.isRegistered && fromData(it) }
        val registeredGsm = gsmCells.firstOrNull { it.isRegistered && fromData(it) }

        val isNrConnected = registeredNr != null || displayIs5g || (try {
            tm?.serviceState?.toString()?.contains("nrState=CONNECTED") == true
        } catch (e: Exception) {
            false
        })

        // NSA: the NR leg is usually not "registered"; pick the cell flagged as secondary serving
        val nrServing = registeredNr ?: if (isNrConnected) {
            nrCells.firstOrNull { fromData(it) && it.cellConnectionStatus == CellInfo.CONNECTION_SECONDARY_SERVING }
                ?: nrCells.firstOrNull { fromData(it) }
        } else null

        val tech = when {
            registeredNr != null && registeredLte == null -> RadioTech.NR_SA
            isNrConnected && registeredLte != null -> RadioTech.NR_NSA
            registeredLte != null -> RadioTech.LTE
            registeredWcdma != null -> RadioTech.WCDMA
            registeredGsm != null -> RadioTech.GSM
            else -> RadioTech.UNKNOWN
        }

        // Build Carrier Info (data SIM)
        // networkOperator / networkOperatorName = currently registered network
        // simOperator / simOperatorName = SIM home network
        val rawPlmn = tm?.networkOperator.orEmpty()
        val mcc = if (rawPlmn.length >= 3) rawPlmn.substring(0, 3) else "---"
        val mnc = if (rawPlmn.length > 3) rawPlmn.substring(3) else "---"

        val rawSimPlmn = tm?.simOperator.orEmpty()
        val simMcc = if (rawSimPlmn.length >= 3) rawSimPlmn.substring(0, 3) else "---"
        val simMnc = if (rawSimPlmn.length > 3) rawSimPlmn.substring(3) else "---"

        val androidNetName = tm?.networkOperatorName
        val androidSimName = tm?.simOperatorName

        // Friendly names via PLMN map (fixes Hutch SIM showing only "Dialog" when camped on Dialog)
        val opName = OperatorNames.resolve(androidNetName, mcc, mnc)
        val simOp = OperatorNames.resolve(
            androidSimName?.ifBlank { null } ?: androidNetName,
            simMcc,
            simMnc
        )

        // Roaming: Android flag OR registered PLMN differs from SIM home PLMN
        val netKey = OperatorNames.plmnKey(mcc, mnc)
        val simKey = OperatorNames.plmnKey(simMcc, simMnc)
        val plmnMismatch = netKey.isNotEmpty() && simKey.isNotEmpty() && netKey != simKey
        val isRoaming = (tm?.isNetworkRoaming == true) || plmnMismatch

        val carrier = CarrierInfo(
            operatorName = opName,
            simOperator = simOp,
            mcc = mcc,
            mnc = mnc,
            simMcc = simMcc,
            simMnc = simMnc,
            countryCode = tm?.networkCountryIso?.uppercase()?.ifBlank { "---" } ?: "---",
            isRoaming = isRoaming,
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
            val nci = id?.nci?.takeIf { it != Long.MAX_VALUE } ?: 0L
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
            val ci = id.ci.takeIf { it != Int.MAX_VALUE }?.toLong() ?: 0L
            val tac = id.tac

            val rsrp = strength.rsrp.takeIf { it != Int.MAX_VALUE && it in -140..-44 }
            val rsrq = strength.rsrq.takeIf { it != Int.MAX_VALUE }
            val rssnr = strength.rssnr.takeIf { it != Int.MAX_VALUE }
            val ta = strength.timingAdvance.takeIf { it != Int.MAX_VALUE && it >= 0 }
            val cqi = strength.cqi.takeIf { it != Int.MAX_VALUE && it >= 0 }

            val (enb, sector) = BandCalculators.parseNodeBAndSector(ci)

            servingCell = ServingCell(
                tech = tech,
                band = lteBand(id),
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

        // Parse Neighbor Cells: every reported cell except the data SIM's serving cell(s),
        // so the second SIM's serving cell shows up too (tagged with its SIM slot).
        val neighborsList = mutableListOf<NeighborCell>()
        val servingRsrp = signalMetrics.rsrp

        lteCells.filter { it !== registeredLte }.forEach { lte ->
            val id = lte.cellIdentity
            val str = lte.cellSignalStrength
            val rsrp = str.rsrp.takeIf { it != Int.MAX_VALUE && it in -140..-44 }
            val delta = if (rsrp != null && servingRsrp != null) rsrp - servingRsrp else null
            val (mcc, mnc, op) = plmnFromIdentity(id, fallbackOf(lte))
            val ci = id.ci.takeIf { it != Int.MAX_VALUE }?.toLong()
            neighborsList.add(
                NeighborCell(
                    techType = "LTE",
                    band = lteBand(id),
                    arfcn = id.earfcn,
                    pci = id.pci,
                    rsrp = rsrp,
                    rsrq = str.rsrq.takeIf { it != Int.MAX_VALUE },
                    deltaRsrp = delta,
                    operatorName = op,
                    mcc = mcc,
                    mnc = mnc,
                    cellId = ci,
                    simSlot = otherSlot(lte),
                    servingOnSim = lte.isRegistered && otherSlot(lte) != null
                )
            )
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            nrCells.filter { it !== nrServing }.forEach { nr ->
                val id = nr.cellIdentity as? CellIdentityNr
                val str = nr.cellSignalStrength as? CellSignalStrengthNr
                val band = id?.bands?.firstOrNull() ?: 0
                val rsrp = str?.ssRsrp?.takeIf { it != Int.MAX_VALUE && it in -140..-44 }
                val delta = if (rsrp != null && servingRsrp != null) rsrp - servingRsrp else null
                val (mcc, mnc, op) = plmnFromIdentity(id, fallbackOf(nr))
                val nci = id?.nci?.takeIf { it != Long.MAX_VALUE && it > 0 }
                neighborsList.add(
                    NeighborCell(
                        techType = "5G NR",
                        band = BandCalculators.getNrBandString(band),
                        arfcn = id?.nrarfcn ?: 0,
                        pci = id?.pci ?: 0,
                        rsrp = rsrp,
                        rsrq = str?.ssRsrq?.takeIf { it != Int.MAX_VALUE },
                        deltaRsrp = delta,
                        operatorName = op,
                        mcc = mcc,
                        mnc = mnc,
                        cellId = nci,
                        simSlot = otherSlot(nr),
                        servingOnSim = nr.isRegistered && otherSlot(nr) != null
                    )
                )
            }
        }

        wcdmaCells.filter { it !== registeredWcdma }.forEach { w ->
            val id = w.cellIdentity
            val str = w.cellSignalStrength
            val rsrp = str.dbm.takeIf { it != Int.MAX_VALUE }
            val delta = if (rsrp != null && servingRsrp != null) rsrp - servingRsrp else null
            val (mcc, mnc, op) = plmnFromIdentity(id, fallbackOf(w))
            neighborsList.add(
                NeighborCell(
                    techType = "WCDMA",
                    band = "UARFCN ${id.uarfcn}",
                    arfcn = id.uarfcn,
                    pci = id.psc,
                    rsrp = rsrp,
                    deltaRsrp = delta,
                    operatorName = op,
                    mcc = mcc,
                    mnc = mnc,
                    cellId = id.cid.takeIf { it != Int.MAX_VALUE }?.toLong(),
                    simSlot = otherSlot(w),
                    servingOnSim = w.isRegistered && otherSlot(w) != null
                )
            )
        }

        gsmCells.filter { it !== registeredGsm }.forEach { g ->
            val id = g.cellIdentity
            val str = g.cellSignalStrength
            val rsrp = str.dbm.takeIf { it != Int.MAX_VALUE }
            val delta = if (rsrp != null && servingRsrp != null) rsrp - servingRsrp else null
            val (mcc, mnc, op) = plmnFromIdentity(id, fallbackOf(g))
            neighborsList.add(
                NeighborCell(
                    techType = "GSM",
                    band = "ARFCN ${id.arfcn}",
                    arfcn = id.arfcn,
                    pci = id.bsic,
                    rsrp = rsrp,
                    deltaRsrp = delta,
                    operatorName = op,
                    mcc = mcc,
                    mnc = mnc,
                    cellId = id.cid.takeIf { it != Int.MAX_VALUE }?.toLong(),
                    simSlot = otherSlot(g),
                    servingOnSim = g.isRegistered && otherSlot(g) != null
                )
            )
        }

        // Record every reported cell (all technologies / all ISPs) in the running log
        val observations = mutableListOf<CellObservation>()
        lteCells.forEach { c ->
            val id = c.cellIdentity
            val rsrp = c.cellSignalStrength.rsrp.takeIf { it != Int.MAX_VALUE && it in -140..-44 }
            val earfcn = id.earfcn.takeIf { it != Int.MAX_VALUE } ?: 0
            val pci = id.pci.takeIf { it != Int.MAX_VALUE } ?: 0
            val (mcc, mnc, op) = plmnFromIdentity(id, fallbackOf(c))
            val ci = id.ci.takeIf { it != Int.MAX_VALUE }?.toLong()
            if (earfcn > 0 || pci > 0) {
                observations.add(
                    CellObservation(
                        "LTE", lteBand(id), earfcn, pci, rsrp,
                        c.isRegistered, op, mcc, mnc, ci
                    )
                )
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            nrCells.forEach { c ->
                val id = c.cellIdentity as? CellIdentityNr ?: return@forEach
                val str = c.cellSignalStrength as? CellSignalStrengthNr
                val band = id.bands?.firstOrNull() ?: 0
                val rsrp = str?.ssRsrp?.takeIf { it != Int.MAX_VALUE && it in -140..-44 }
                val arfcn = id.nrarfcn.takeIf { it != Int.MAX_VALUE } ?: 0
                val pci = id.pci.takeIf { it != Int.MAX_VALUE } ?: 0
                val (mcc, mnc, op) = plmnFromIdentity(id, fallbackOf(c))
                val nci = id.nci.takeIf { it != Long.MAX_VALUE && it > 0 }
                if (arfcn > 0 || pci > 0) {
                    observations.add(
                        CellObservation(
                            "5G NR", BandCalculators.getNrBandString(band), arfcn, pci, rsrp,
                            c.isRegistered, op, mcc, mnc, nci
                        )
                    )
                }
            }
        }
        wcdmaCells.forEach { c ->
            val id = c.cellIdentity
            val rsrp = c.cellSignalStrength.dbm.takeIf { it != Int.MAX_VALUE }
            val uarfcn = id.uarfcn.takeIf { it != Int.MAX_VALUE } ?: 0
            val pci = id.psc.takeIf { it != Int.MAX_VALUE } ?: 0
            val (mcc, mnc, op) = plmnFromIdentity(id, fallbackOf(c))
            observations.add(
                CellObservation(
                    "WCDMA", "B$uarfcn", uarfcn, pci, rsrp, c.isRegistered,
                    op, mcc, mnc, id.cid.takeIf { it != Int.MAX_VALUE }?.toLong()
                )
            )
        }
        gsmCells.forEach { c ->
            val id = c.cellIdentity
            val rsrp = c.cellSignalStrength.dbm.takeIf { it != Int.MAX_VALUE }
            val arfcn = id.arfcn.takeIf { it != Int.MAX_VALUE } ?: 0
            val pci = id.bsic.takeIf { it != Int.MAX_VALUE } ?: 0
            val (mcc, mnc, op) = plmnFromIdentity(id, fallbackOf(c))
            observations.add(
                CellObservation(
                    "GSM", "ARFCN$arfcn", arfcn, pci, rsrp, c.isRegistered,
                    op, mcc, mnc, id.cid.takeIf { it != Int.MAX_VALUE }?.toLong()
                )
            )
        }
        recordSeen(observations)

        // History
        val now = System.currentTimeMillis()
        val currentHistory = _state.value.signalHistory.toMutableList()
        signalMetrics.rsrp?.let { r ->
            currentHistory.add(SignalHistoryPoint(now, r))
            while (currentHistory.size > maxHistoryPoints) currentHistory.removeAt(0)
        }

        _state.update {
            it.copy(
                isRefreshing = false,
                lastUpdatedMs = now,
                carrier = carrier,
                servingCell = servingCell,
                signal = signalMetrics,
                neighbors = neighborsList.sortedByDescending { n -> n.rsrp ?: -999 },
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
            operatorName = "Dialog",
            simOperator = "Dialog",
            mcc = "413",
            mnc = "02",
            simMcc = "413",
            simMnc = "02",
            countryCode = "LK",
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
            quality = quality,
            scorePercentage = score
        )

        // Demo shows multiple ISPs (Sri Lanka style) so UI can be verified offline
        val neighbors = listOf(
            NeighborCell("LTE", "B3", 1850, 120, simRsrp - 8, -12, -8,
                operatorName = "Dialog", mcc = "413", mnc = "02", cellId = 12345678L),
            NeighborCell("LTE", "B40", 39150, 55, simRsrp - 14, -14, -14,
                operatorName = "Mobitel", mcc = "413", mnc = "01", cellId = 23456789L),
            NeighborCell("5G NR", "n78", 643200, 200, simRsrp - 6, -11, -6,
                operatorName = "Dialog", mcc = "413", mnc = "02", cellId = 34567890L),
            NeighborCell("LTE", "B8", 3500, 88, simRsrp - 18, -15, -18,
                operatorName = "Hutch", mcc = "413", mnc = "08", cellId = 45678901L),
            NeighborCell("LTE", "B1", 300, 42, simRsrp - 20, -16, -20,
                operatorName = "Airtel", mcc = "413", mnc = "05", cellId = 56789012L)
        )

        recordSeen(
            listOf(
                CellObservation("5G NR", "n78", 643334, 384, simRsrp, true,
                    "Dialog", "413", "02", 268435456L),
                CellObservation("LTE", "B3", 1850, 120, simRsrp - 8, false,
                    "Dialog", "413", "02", 12345678L),
                CellObservation("LTE", "B40", 39150, 55, simRsrp - 14, false,
                    "Mobitel", "413", "01", 23456789L),
                CellObservation("5G NR", "n78", 643200, 200, simRsrp - 6, false,
                    "Dialog", "413", "02", 34567890L),
                CellObservation("LTE", "B8", 3500, 88, simRsrp - 18, false,
                    "Hutch", "413", "08", 45678901L),
                CellObservation("LTE", "B1", 300, 42, simRsrp - 20, false,
                    "Airtel", "413", "05", 56789012L)
            )
        )

        val currentHistory = _state.value.signalHistory.toMutableList()
        currentHistory.add(SignalHistoryPoint(now, simRsrp))
        while (currentHistory.size > maxHistoryPoints) currentHistory.removeAt(0)

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
}
