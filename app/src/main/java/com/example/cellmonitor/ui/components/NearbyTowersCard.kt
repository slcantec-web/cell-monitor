package com.example.cellmonitor.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cellmonitor.data.DbTower
import com.example.cellmonitor.data.TowerDatabase
import com.example.cellmonitor.ui.theme.TechAmber
import com.example.cellmonitor.ui.theme.TechCardBorder
import com.example.cellmonitor.ui.theme.TechCardSurface
import com.example.cellmonitor.ui.theme.TechCardSurfaceVariant
import com.example.cellmonitor.ui.theme.TechCyan
import com.example.cellmonitor.ui.theme.TechRose
import com.example.cellmonitor.ui.theme.TextMuted
import com.example.cellmonitor.ui.theme.TextPrimary
import com.example.cellmonitor.ui.theme.TextSecondary
import kotlinx.coroutines.launch

private const val MAX_ROWS_PER_OPERATOR = 5

/**
 * Towers of every operator near the phone, from the OpenCellID database.
 * Works with a single SIM: Android never reports other operators' cells to apps, so this is a
 * database lookup (where towers are), not a live measurement (what the phone hears).
 */
@Composable
fun NearbyTowersCard(
    registeredMcc: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var token by remember { mutableStateOf(TowerDatabase.getToken(context)) }
    var tokenInput by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var towers by remember { mutableStateOf<List<DbTower>?>(null) }
    var radiusUsed by remember { mutableStateOf(0) }
    var editingToken by remember { mutableStateOf(false) }

    fun search() {
        val loc = TowerDatabase.lastKnownLocation(context)
        if (loc == null) {
            error = "No saved location yet. Turn on Location, open Google Maps once, then try again."
            return
        }
        loading = true
        error = null
        scope.launch {
            try {
                val result = TowerDatabase.fetchNearby(
                    token = token,
                    lat = loc.first,
                    lon = loc.second,
                    mcc = registeredMcc
                )
                towers = result.towers
                radiusUsed = result.radiusM
            } catch (e: Exception) {
                error = e.message ?: "Lookup failed"
            } finally {
                loading = false
            }
        }
    }

    Box(
        modifier = modifier
            .testTag("nearby_towers_card")
            .fillMaxWidth()
            .background(TechCardSurface, RoundedCornerShape(20.dp))
            .border(1.dp, TechCardBorder, RoundedCornerShape(20.dp))
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "NEARBY TOWERS · ALL OPERATORS",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    letterSpacing = 0.5.sp
                )
                Text(
                    text = "DATABASE",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = TechAmber,
                    modifier = Modifier
                        .background(TechAmber.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }

            Text(
                text = "Android only lets this app hear the network your SIM is on. This list shows " +
                    "other operators' towers near you (up to ~1.5 km) from the OpenCellID database " +
                    "(crowd-sourced, so positions are approximate and not a live scan).",
                fontSize = 11.sp,
                color = TextMuted
            )

            if (token.isBlank() || editingToken) {
                Text(
                    text = "Enter your free OpenCellID API token",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextSecondary
                )
                OutlinedTextField(
                    value = tokenInput,
                    onValueChange = { tokenInput = it },
                    singleLine = true,
                    placeholder = { Text("API token", fontSize = 12.sp, color = TextMuted) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = TechCyan,
                        unfocusedBorderColor = TechCardBorder,
                        cursorColor = TechCyan
                    )
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            val t = tokenInput.trim()
                            if (t.isNotEmpty()) {
                                TowerDatabase.saveToken(context, t)
                                token = t
                                tokenInput = ""
                                editingToken = false
                                error = null
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = TechCyan,
                            contentColor = androidx.compose.ui.graphics.Color(0xFF0B1220)
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Save token", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    TextButton(onClick = { openLink(context, "https://opencellid.org/register") }) {
                        Text("Get a free token", fontSize = 12.sp, color = TechCyan)
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = { search() },
                        enabled = !loading,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = TechCyan.copy(alpha = 0.15f),
                            contentColor = TechCyan
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = if (loading) "Searching…" else "Find towers near me",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    TextButton(onClick = { editingToken = true }) {
                        Text("Change token", fontSize = 11.sp, color = TextMuted)
                    }
                }
            }

            error?.let {
                Text(text = it, fontSize = 12.sp, color = TechRose)
            }

            towers?.let { list ->
                if (list.isEmpty()) {
                    Text(
                        text = "The database has no towers within $radiusUsed m of your last known location.",
                        fontSize = 12.sp,
                        color = TextMuted
                    )
                } else {
                    val groups = list.groupBy { it.operatorName }
                        .entries.sortedBy { e -> e.value.minOf { it.distanceM } }
                    Text(
                        text = "${list.size} towers · ${groups.size} operators · within $radiusUsed m " +
                            "(each lookup uses up to ${TowerDatabase.MAX_CELLS} of your daily API credits)",
                        fontSize = 10.sp,
                        color = TextMuted
                    )
                    groups.forEach { (operator, items) ->
                        OperatorGroup(operator, items)
                    }
                }
            }
        }
    }
}

@Composable
private fun OperatorGroup(operator: String, items: List<DbTower>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(TechCardSurfaceVariant, RoundedCornerShape(12.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = operator, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TechCyan)
            Text(
                text = "${items.size} tower${if (items.size == 1) "" else "s"} · nearest ${items.first().distanceM} m",
                fontSize = 10.sp,
                color = TextMuted
            )
        }
        items.take(MAX_ROWS_PER_OPERATOR).forEach { t ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = "${t.radio} · CID ${t.cellId} · LAC/TAC ${t.lac}",
                    fontSize = 11.sp,
                    color = TextSecondary,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "${t.distanceM} m",
                    fontSize = 11.sp,
                    color = TextPrimary,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
        if (items.size > MAX_ROWS_PER_OPERATOR) {
            Text(
                text = "+ ${items.size - MAX_ROWS_PER_OPERATOR} more",
                fontSize = 10.sp,
                color = TextMuted
            )
        }
    }
}

private fun openLink(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (_: Exception) {
        Toast.makeText(context, "Could not open link", Toast.LENGTH_SHORT).show()
    }
}
