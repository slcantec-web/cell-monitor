package com.example.cellmonitor.data

import java.util.Locale

data class LteBandSpec(
    val band: Int,
    val name: String,
    val dlLowMhz: Double,
    val nOffs: Int,
    val earfcnStart: Int,
    val earfcnEnd: Int,
    val duplex: String = "FDD"
)

data class NrBandSpec(
    val bandNumber: Int,
    val name: String,
    val duplex: String = "TDD"
)

object BandCalculators {
    private val lteBands = listOf(
        LteBandSpec(1, "2100 IMT", 2110.0, 0, 0, 599, "FDD"),
        LteBandSpec(2, "1900 PCS", 1930.0, 600, 600, 1199, "FDD"),
        LteBandSpec(3, "1800+ DCS", 1805.0, 1200, 1200, 1949, "FDD"),
        LteBandSpec(4, "1700/2100 AWS", 2110.0, 1950, 1950, 2399, "FDD"),
        LteBandSpec(5, "850 CLR", 869.0, 2400, 2400, 2649, "FDD"),
        LteBandSpec(7, "2600 IMT-E", 2620.0, 2750, 2750, 3449, "FDD"),
        LteBandSpec(8, "900 GSM", 925.0, 3450, 3450, 3799, "FDD"),
        LteBandSpec(12, "700 Lower SMH", 729.0, 5010, 5010, 5179, "FDD"),
        LteBandSpec(13, "700 Upper SMH", 746.0, 5180, 5180, 5279, "FDD"),
        LteBandSpec(14, "700 FirstNet", 758.0, 5280, 5280, 5379, "FDD"),
        LteBandSpec(20, "800 DD", 791.0, 6150, 6150, 6449, "FDD"),
        LteBandSpec(25, "1900+ Ext PCS", 1930.0, 8040, 8040, 8689, "FDD"),
        LteBandSpec(26, "850+ Ext CLR", 859.0, 8690, 8690, 9039, "FDD"),
        LteBandSpec(28, "700 APT", 758.0, 9210, 9210, 9659, "FDD"),
        LteBandSpec(38, "2600 IMT-E TDD", 2570.0, 37750, 37750, 38249, "TDD"),
        LteBandSpec(40, "2300 S-Band", 2300.0, 38650, 38650, 39649, "TDD"),
        LteBandSpec(41, "2500 BRS/EBS", 2496.0, 39650, 39650, 41589, "TDD"),
        LteBandSpec(42, "3500 CBRS", 3400.0, 41590, 41590, 43589, "TDD"),
        LteBandSpec(48, "3600 CBRS", 3550.0, 55240, 55240, 56739, "TDD"),
        LteBandSpec(66, "1700/2100 Ext AWS", 2110.0, 66436, 66436, 67335, "FDD"),
        LteBandSpec(71, "600 DD", 617.0, 68586, 68586, 68935, "FDD")
    )

    private val nrBandMap = mapOf(
        1 to NrBandSpec(1, "2100 IMT", "FDD"),
        2 to NrBandSpec(2, "1900 PCS", "FDD"),
        3 to NrBandSpec(3, "1800+ DCS", "FDD"),
        5 to NrBandSpec(5, "850 CLR", "FDD"),
        7 to NrBandSpec(7, "2600 IMT-E", "FDD"),
        8 to NrBandSpec(8, "900 GSM", "FDD"),
        20 to NrBandSpec(20, "800 DD", "FDD"),
        28 to NrBandSpec(28, "700 APT", "FDD"),
        41 to NrBandSpec(41, "2500 BRS", "TDD"),
        66 to NrBandSpec(66, "Ext AWS", "FDD"),
        71 to NrBandSpec(71, "600 MHz", "FDD"),
        77 to NrBandSpec(77, "3700 C-Band", "TDD"),
        78 to NrBandSpec(78, "3500 C-Band", "TDD"),
        79 to NrBandSpec(79, "4700 MHz", "TDD"),
        258 to NrBandSpec(258, "24 GHz mmWave", "TDD"),
        260 to NrBandSpec(260, "39 GHz mmWave", "TDD"),
        261 to NrBandSpec(261, "28 GHz mmWave", "TDD")
    )

    fun findLteSpec(earfcn: Int): LteBandSpec? {
        return lteBands.firstOrNull { earfcn in it.earfcnStart..it.earfcnEnd }
    }

    fun getLteBandString(earfcn: Int): String {
        return findLteSpec(earfcn)?.let { "B${it.band}" } ?: if (earfcn > 0) "B?" else "---"
    }

    fun getLteBandName(earfcn: Int): String {
        return findLteSpec(earfcn)?.name ?: "LTE Carrier"
    }

    fun getLteDuplex(earfcn: Int): String {
        return findLteSpec(earfcn)?.duplex ?: "FDD"
    }

    fun calculateLteFrequencyMhz(earfcn: Int): String {
        val spec = findLteSpec(earfcn) ?: return "---"
        val mhz = spec.dlLowMhz + 0.1 * (earfcn - spec.nOffs)
        return String.format(Locale.US, "%.1f MHz", mhz)
    }

    fun getNrBandString(band: Int): String {
        return if (band > 0) "n$band" else "---"
    }

    fun getNrBandName(band: Int): String {
        return nrBandMap[band]?.name ?: if (band > 0) "5G NR Sub-6" else "---"
    }

    fun getNrDuplex(band: Int): String {
        return nrBandMap[band]?.duplex ?: if (band >= 38) "TDD" else "FDD"
    }

    fun calculateNrFrequencyMhz(nrarfcn: Int): String {
        if (nrarfcn <= 0 || nrarfcn == Int.MAX_VALUE) return "---"
        val mhz = when {
            nrarfcn < 600000 -> nrarfcn * 0.005
            nrarfcn < 2016667 -> 3000.0 + 0.015 * (nrarfcn - 600000)
            else -> 24250.08 + 0.06 * (nrarfcn - 2016667)
        }
        return String.format(Locale.US, "%.1f MHz", mhz)
    }

    fun evaluateSignalQuality(rsrp: Int?): SignalQuality {
        if (rsrp == null || rsrp == Int.MAX_VALUE || rsrp < -140) return SignalQuality.UNKNOWN
        return when {
            rsrp >= -85 -> SignalQuality.EXCELLENT
            rsrp >= -100 -> SignalQuality.GOOD
            rsrp >= -115 -> SignalQuality.FAIR
            else -> SignalQuality.POOR
        }
    }

    fun calculateSignalPercentage(rsrp: Int?): Int {
        if (rsrp == null || rsrp == Int.MAX_VALUE) return 0
        // Scale from -140 dBm (0%) to -50 dBm (100%)
        val clamped = rsrp.coerceIn(-140, -50)
        return (((clamped + 140).toFloat() / 90f) * 100).toInt().coerceIn(0, 100)
    }

    fun parseNodeBAndSector(cellId: Long): Pair<Long?, Int?> {
        if (cellId <= 0 || cellId == Long.MAX_VALUE || cellId == Int.MAX_VALUE.toLong()) {
            return Pair(null, null)
        }
        // In LTE (ECI 28-bit): eNodeB ID is upper 20 bits (cellId / 256), Sector ID is lower 8 bits (cellId % 256)
        val enodeb = cellId shr 8
        val sector = (cellId and 0xFF).toInt()
        return Pair(enodeb, sector)
    }
}
