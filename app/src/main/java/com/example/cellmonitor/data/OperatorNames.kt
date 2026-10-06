package com.example.cellmonitor.data

/**
 * Maps MCC+MNC (PLMN) to friendly operator brand names.
 * Used when Android's networkOperatorName is blank/generic, or to normalize
 * known networks (especially Sri Lanka: Dialog vs Hutch confusion when
 * a Hutch SIM camps on Dialog cells).
 */
object OperatorNames {

    // Key = MCC + MNC (MNC as reported, 2 or 3 digits). Both padded and unpadded forms included where needed.
    private val plmnToName: Map<String, String> = mapOf(
        // Sri Lanka (MCC 413)
        "41301" to "Mobitel",
        "41302" to "Dialog",
        "41303" to "Etisalat",
        "41304" to "Lanka Bell",
        "41305" to "Airtel",
        "41308" to "Hutch",
        "41309" to "Hutch",
        "41311" to "Dialog",
        "41312" to "SLT",
        // Common demo / US
        "310260" to "T-Mobile",
        "310410" to "AT&T",
        "311480" to "Verizon"
    )

    fun fromPlmn(mcc: String, mnc: String): String? {
        if (mcc.isBlank() || mcc == "---" || mnc.isBlank() || mnc == "---") return null
        val raw = "$mcc$mnc"
        plmnToName[raw]?.let { return it }
        // Try zero-padded 2-digit MNC
        if (mnc.length == 1) {
            plmnToName["$mcc${mnc.padStart(2, '0')}"]?.let { return it }
        }
        // Try without leading zero on 2-digit MNC (e.g. 08 -> 8)
        if (mnc.length == 2 && mnc.startsWith("0")) {
            plmnToName["$mcc${mnc.toIntOrNull() ?: mnc}"]?.let { return it }
        }
        return null
    }

    /**
     * Prefer PLMN map for known operators; otherwise use Android-reported name.
     * Falls back to "Unknown" only when both are missing/generic.
     */
    fun resolve(androidName: String?, mcc: String, mnc: String): String {
        val mapped = fromPlmn(mcc, mnc)
        val name = androidName?.trim().orEmpty()
        val generic = name.isEmpty() ||
            name.equals("Android", ignoreCase = true) ||
            name.equals("Carrier", ignoreCase = true) ||
            name.equals("---", ignoreCase = true) ||
            name.equals("null", ignoreCase = true)

        return when {
            mapped != null -> mapped
            !generic -> name
            else -> "Unknown"
        }
    }

    fun plmnKey(mcc: String, mnc: String): String {
        if (mcc.isBlank() || mcc == "---" || mnc.isBlank() || mnc == "---") return ""
        return "$mcc${mnc.padStart(2, '0')}"
    }
}
