package com.example.cellmonitor

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.telephony.*
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {
    private lateinit var tm: TelephonyManager
    private lateinit var out: TextView
    private val h = Handler(Looper.getMainLooper())
    private var auto = false
    private val tick = object : Runnable {
        override fun run() { refresh(); if (auto) h.postDelayed(this, 2000) }
    }

    // band, DL low MHz, N-offs, first EARFCN, last EARFCN
    private val lteBands = listOf(
        intArrayOf(1, 2110, 0, 0, 599), intArrayOf(3, 1805, 1200, 1200, 1949),
        intArrayOf(7, 2620, 2750, 2750, 3449), intArrayOf(8, 925, 3450, 3450, 3799),
        intArrayOf(20, 791, 6150, 6150, 6449), intArrayOf(28, 758, 9210, 9210, 9659),
        intArrayOf(40, 2300, 38650, 38650, 39649), intArrayOf(41, 2496, 39650, 39650, 41589)
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tm = getSystemService(TelephonyManager::class.java)
        out = TextView(this).apply {
            typeface = Typeface.MONOSPACE; textSize = 13f
            setTextColor(0xFFE0E0E0.toInt()); setPadding(32, 48, 32, 32)
        }
        val refreshB = Button(this).apply { text = "REFRESH"; setOnClickListener { refresh() } }
        val autoB = Button(this).apply {
            text = "AUTO"
            setOnClickListener {
                auto = !auto
                text = if (auto) "AUTO: ON" else "AUTO"
                h.removeCallbacks(tick)
                if (auto) h.post(tick)
            }
        }
        val row = LinearLayout(this).apply {
            addView(refreshB, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(autoB, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF101418.toInt())
            addView(ScrollView(this@MainActivity).apply { addView(out) },
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(row)
        }
        setContentView(root)
        refresh()
    }

    override fun onDestroy() { h.removeCallbacks(tick); super.onDestroy() }

    override fun onRequestPermissionsResult(c: Int, p: Array<out String>, r: IntArray) {
        super.onRequestPermissionsResult(c, p, r); refresh()
    }

    @SuppressLint("MissingPermission")
    private fun refresh() {
        val perms = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.READ_PHONE_STATE)
        if (perms.any { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }) {
            requestPermissions(perms, 1); return
        }
        tm.requestCellInfoUpdate(mainExecutor, object : TelephonyManager.CellInfoCallback() {
            override fun onCellInfo(cellInfo: MutableList<CellInfo>) = render(cellInfo)
            override fun onError(errorCode: Int, detail: Throwable?) = render(tm.allCellInfo ?: emptyList())
        })
    }

    private fun v(x: Int) = if (x == Int.MAX_VALUE) "---" else x.toString()
    private fun lv(x: Long) = if (x == Long.MAX_VALUE) "---" else x.toString()
    private fun lteRow(e: Int) = lteBands.firstOrNull { e in it[3]..it[4] }
    private fun lteBand(e: Int) = lteRow(e)?.let { "B${it[0]}" } ?: "B?"
    private fun lteMhz(e: Int) = lteRow(e)?.let { "%.0f MHz".format(it[1] + 0.1 * (e - it[2])) } ?: "---"
    private fun nrMhz(n: Int) = if (n == Int.MAX_VALUE) "---" else
        "%.0f MHz".format(if (n < 600000) n * 0.005 else 3000 + 0.015 * (n - 600000))

    @SuppressLint("MissingPermission")
    private fun render(cells: List<CellInfo>) {
        val lte = cells.filterIsInstance<CellInfoLte>()
        val nr = cells.filterIsInstance<CellInfoNr>()
        val rl = lte.firstOrNull { it.isRegistered }
        val rn = nr.firstOrNull { it.isRegistered }
        val nrConn = rn != null ||
            (try { tm.serviceState?.toString()?.contains("nrState=CONNECTED") == true } catch (e: Exception) { false })
        val nrCell = rn ?: if (nrConn) nr.firstOrNull() else null
        val tech = when {
            rn != null && rl == null -> "5G SA"
            nrConn && rl != null -> "5G NSA"
            rl != null -> "LTE"
            else -> "---"
        }
        val op = tm.networkOperator ?: ""
        val sb = StringBuilder()
        fun title(t: String) { sb.append("\n").append(t).append("\n") }
        fun row(k: String, x: String) { sb.append(k.padEnd(15)).append(x).append("\n") }

        sb.append("CELL MONITOR\n────────────────────────\n")
        title("SIM")
        row("Operator", tm.networkOperatorName.ifEmpty { "---" })
        row("MCC / MNC", if (op.length >= 5) "${op.substring(0, 3)} / ${op.substring(3)}" else "---")
        title("CURRENT NETWORK")
        row("Technology", tech)
        rl?.let {
            val id = it.cellIdentity
            row("LTE Anchor", lteBand(id.earfcn))
            row("LTE Frequency", lteMhz(id.earfcn))
            row("LTE PCI", v(id.pci))
            row("LTE Cell ID", v(id.ci))
        }
        nrCell?.let {
            val id = it.cellIdentity as CellIdentityNr
            title("5G NR")
            row("NR Band", id.bands.firstOrNull()?.let { b -> "n$b" } ?: "---")
            row("NR Frequency", nrMhz(id.nrarfcn))
            row("NR PCI", v(id.pci))
            row("NR Cell ID", lv(id.nci))
        }
        title("SIGNAL")
        rl?.let {
            val s = it.cellSignalStrength
            row("RSRP", "${v(s.rsrp)} dBm"); row("RSRQ", "${v(s.rsrq)} dB"); row("SINR", "${v(s.rssnr)} dB")
        }
        nrCell?.let {
            val s = it.cellSignalStrength as CellSignalStrengthNr
            row("NR RSRP", "${v(s.ssRsrp)} dBm"); row("NR RSRQ", "${v(s.ssRsrq)} dB"); row("NR SINR", "${v(s.ssSinr)} dB")
        }
        title("NEIGHBOUR CELLS")
        lte.forEach { sb.append("${lteBand(it.cellIdentity.earfcn)}  •  ${v(it.cellSignalStrength.rsrp)} dBm\n") }
        nr.forEach {
            val b = (it.cellIdentity as CellIdentityNr).bands.firstOrNull()?.let { x -> "n$x" } ?: "n?"
            sb.append("$b  •  ${v((it.cellSignalStrength as CellSignalStrengthNr).ssRsrp)} dBm\n")
        }
        out.text = sb.toString()
    }
}
