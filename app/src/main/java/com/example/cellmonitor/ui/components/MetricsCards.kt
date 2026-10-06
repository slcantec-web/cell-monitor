package com.example.cellmonitor.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

                Row(verticalAlignment = Alignment.CenterVertically) {
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
