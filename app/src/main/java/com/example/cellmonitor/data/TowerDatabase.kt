package com.example.cellmonitor.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** One tower from the OpenCellID crowd-sourced database (not a live radio measurement). */
data class DbTower(
    val radio: String,
    val mcc: String,
    val mnc: String,
    val operatorName: String,
    val lac: Int,
    val cellId: Long,
    val lat: Double,
    val lon: Double,
    val rangeM: Int,
    val samples: Int,
    val distanceM: Int
)

/** Towers found plus the search radius that finally worked (it can shrink after a 400). */
data class NearbyResult(
    val towers: List<DbTower>,
    val radiusM: Int
)

/**
 * Looks up towers of ALL operators around the phone from OpenCellID.
 *
 * Android only lets an app measure cells of the network the SIM is camped on, so a single-SIM
 * phone can never "hear" other operators. This database lookup shows where their towers are
 * (from other people's contributed data) instead. It is a lookup, not a live scan.
 *
 * Free API keys get ~1000 credits/day and every returned cell costs 1 credit, so lookups are
 * manual (button) and capped at [MAX_CELLS] cells.
 */
object TowerDatabase {
    private const val PREFS = "tower_db_prefs"
    private const val KEY_TOKEN = "opencellid_token"
    const val MAX_CELLS = 50
    private const val ENDPOINT = "https://opencellid.org/cell/getInArea"

    /** Pasted tokens often carry spaces, a newline or quotes; none of those belong in a key. */
    private fun cleanToken(token: String): String =
        token.filterNot { it.isWhitespace() }.trim('"', '\'')

    fun getToken(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_TOKEN, "").orEmpty()

    fun saveToken(context: Context, token: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_TOKEN, cleanToken(token)).apply()
    }

    /** Last known phone location (same approach as the CellMapper button), or null. */
    @Suppress("MissingPermission")
    fun lastKnownLocation(context: Context): Pair<Double, Double>? {
        if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return null
        }
        return try {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val best = listOf(
                LocationManager.GPS_PROVIDER,
                LocationManager.NETWORK_PROVIDER,
                LocationManager.PASSIVE_PROVIDER
            ).mapNotNull { p -> runCatching { lm.getLastKnownLocation(p) }.getOrNull() }
                .maxByOrNull { it.time }
            best?.let { it.latitude to it.longitude }
        } catch (e: Exception) {
            null
        }
    }

    private fun buildUrl(token: String, lat: Double, lon: Double, radiusM: Int, mcc: String?): String {
        val dLat = radiusM / 111_320.0
        val dLon = radiusM / (111_320.0 * cos(Math.toRadians(lat)).coerceAtLeast(0.01))
        // Fixed 6-decimal numbers with a dot, whatever the phone's language/locale is.
        val bbox = String.format(
            Locale.US, "%.6f,%.6f,%.6f,%.6f",
            lat - dLat, lon - dLon, lat + dLat, lon + dLon
        )
        return buildString {
            append(ENDPOINT)
            append("?key=").append(java.net.URLEncoder.encode(token, "UTF-8"))
            append("&BBOX=").append(bbox)
            if (mcc != null) append("&mcc=").append(mcc)
            append("&limit=").append(MAX_CELLS)
            append("&format=json")
        }
    }

    /** HTTP status and body (the error body too, so the real reason can be shown). */
    private fun request(url: String): Pair<Int, String> {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 20_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "CellMonitor")
        }
        try {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            return code to body
        } finally {
            conn.disconnect()
        }
    }

    /** Short ": reason" taken from an XML/JSON/plain error body, or "" when there is none. */
    private fun errorDetail(body: String): String {
        val info = Regex("info=\"([^\"]*)\"").find(body)?.groupValues?.get(1)
        val text = (info ?: body).replace(Regex("\\s+"), " ").trim().take(120)
        return if (text.isEmpty()) "" else ": $text"
    }

    /**
     * Towers around the point, nearest first.
     * [mcc] limits the query to one country (e.g. "413" for Sri Lanka); pass blank for any.
     *
     * OpenCellID answers HTTP 400 ("Invalid input data") without saying which input it dislikes,
     * so on a 400 the search is retried with smaller areas and finally without the country
     * filter before giving up. Throws [IllegalStateException] with a readable message on failure.
     */
    suspend fun fetchNearby(
        token: String,
        lat: Double,
        lon: Double,
        mcc: String
    ): NearbyResult = withContext(Dispatchers.IO) {
        val key = cleanToken(token)
        val mccParam = mcc.takeIf { it.length == 3 && it.all(Char::isDigit) }

        val attempts = buildList {
            add(1500 to mccParam)
            add(700 to mccParam)
            add(300 to mccParam)
            if (mccParam != null) add(300 to null)
        }

        var lastDetail = ""
        for ((radius, mccForTry) in attempts) {
            val (code, body) = request(buildUrl(key, lat, lon, radius, mccForTry))
            when {
                code in 200..299 -> return@withContext NearbyResult(parse(body, lat, lon), radius)
                code == 404 -> return@withContext NearbyResult(emptyList(), radius)
                code == 400 -> {
                    lastDetail = errorDetail(body)
                    continue
                }
                code == 401 -> throw IllegalStateException("OpenCellID says the API token is invalid. Check it and try again.")
                code == 403 -> throw IllegalStateException("OpenCellID refused this token for tower searches (HTTP 403)${errorDetail(body)}")
                code == 429 -> throw IllegalStateException("Daily OpenCellID limit reached. Try again tomorrow.")
                else -> throw IllegalStateException("OpenCellID error (HTTP $code)${errorDetail(body)}")
            }
        }
        throw IllegalStateException("OpenCellID rejected the request (HTTP 400)$lastDetail")
    }

    private fun parse(json: String, lat: Double, lon: Double): List<DbTower> {
        val root = try {
            JSONObject(json)
        } catch (e: Exception) {
            throw IllegalStateException("Unexpected reply from OpenCellID")
        }
        val arr = root.optJSONArray("cells") ?: return emptyList()
        val out = ArrayList<DbTower>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val mcc = o.optInt("mcc", 0).toString()
            val mnc = o.optInt("mnc", -1).takeIf { it >= 0 }?.toString()?.padStart(2, '0') ?: "---"
            val tLat = o.optDouble("lat", Double.NaN)
            val tLon = o.optDouble("lon", Double.NaN)
            if (tLat.isNaN() || tLon.isNaN()) continue
            out.add(
                DbTower(
                    radio = o.optString("radio", "?").ifBlank { "?" },
                    mcc = mcc,
                    mnc = mnc,
                    operatorName = OperatorNames.fromPlmn(mcc, mnc) ?: "MNC $mnc",
                    lac = o.optInt("lac", 0),
                    cellId = o.optLong("cellid", 0L),
                    lat = tLat,
                    lon = tLon,
                    rangeM = o.optInt("range", 0),
                    samples = o.optInt("samples", 0),
                    distanceM = distanceMeters(lat, lon, tLat, tLon).toInt()
                )
            )
        }
        return out.sortedBy { it.distanceM }
    }

    private fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
        return r * 2 * atan2(sqrt(a), sqrt(1 - a))
    }
}
