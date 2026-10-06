package com.example.cellmonitor.data

enum class SignalQuality(val label: String, val colorHex: Long) {
    EXCELLENT("Excellent", 0xFF10B981), // Emerald
    GOOD("Good", 0xFF06B6D4),           // Cyan
    FAIR("Fair", 0xFFF59E0B),           // Amber
    POOR("Poor", 0xFFEF4444),           // Rose
    UNKNOWN("No Signal", 0xFF6B7280)    // Gray
}

enum class RadioTech(val displayTitle: String, val generation: String, val badgeColor: Long) {
    NR_SA("5G SA", "Standalone", 0xFF10B981),
    NR_NSA("5G NSA", "Non-Standalone", 0xFF06B6D4),
    LTE_A("LTE+", "Advanced Carrier Agg.", 0xFF3B82F6),
    LTE("4G LTE", "E-UTRA", 0xFF6366F1),
    WCDMA("3G UMTS", "WCDMA/HSPA+", 0xFF8B5CF6),
    GSM("2G GSM", "EDGE/GPRS", 0xFFEC4899),
    UNKNOWN("Searching...", "Unknown Network", 0xFF6B7280)
}

data class CarrierInfo(
    val operatorName: String = "---",
    val simOperator: String = "---",
    val mcc: String = "---",
    val mnc: String = "---",
    val countryCode: String = "---",
    val isRoaming: Boolean = false,
    val simState: String = "READY",
    val dataNetworkType: String = "LTE",
    val displayType: String = "---"   // what the phone's status-bar icon uses (e.g. 5G NSA)
)

data class ServingCell(
    val tech: RadioTech = RadioTech.UNKNOWN,
    val band: String = "---",
    val bandName: String = "---",
    val duplex: String = "FDD",
    val frequencyMhz: String = "---",
    val arfcn: Int = 0,
    val pci: Int = 0,
    val cellId: Long = 0L,
    val nodeBId: Long? = null,
    val sectorId: Int? = null,
    val tac: Int = 0,
    val bandwidth: String = "---"
)

data class SignalMetrics(
    val rsrp: Int? = null,           // Reference Signal Received Power (dBm)
    val rsrq: Int? = null,           // Reference Signal Received Quality (dB)
    val sinr: Int? = null,           // Signal to Interference & Noise Ratio (dB)
    val rssi: Int? = null,           // Received Signal Strength Indication (dBm)
    val asu: Int? = null,            // Arbitrary Strength Unit
    val cqi: Int? = null,            // Channel Quality Indicator
    val timingAdvance: Int? = null,  // Timing advance in meters / symbols
    val quality: SignalQuality = SignalQuality.UNKNOWN,
    val scorePercentage: Int = 0
)

data class NeighborCell(
    val techType: String,
    val band: String,
    val arfcn: Int,
    val pci: Int,
    val rsrp: Int?,
    val rsrq: Int? = null,
    val deltaRsrp: Int? = null
)

data class SignalHistoryPoint(
    val timestampMs: Long,
    val rsrp: Int
)

data class CellMonitorState(
    val isPermissionGranted: Boolean = false,
    val isAutoRefresh: Boolean = false,
    val refreshIntervalSec: Int = 2,
    val isDemoMode: Boolean = false,
    val isRefreshing: Boolean = false,
    val lastUpdatedMs: Long = 0L,
    val carrier: CarrierInfo = CarrierInfo(),
    val servingCell: ServingCell? = null,
    val signal: SignalMetrics = SignalMetrics(),
    val neighbors: List<NeighborCell> = emptyList(),
    val signalHistory: List<SignalHistoryPoint> = emptyList()
)
