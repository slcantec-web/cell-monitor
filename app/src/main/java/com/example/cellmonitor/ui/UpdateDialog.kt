package com.example.cellmonitor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.cellmonitor.ui.theme.TechCardBorder
import com.example.cellmonitor.ui.theme.TechCardSurface
import com.example.cellmonitor.ui.theme.TechCyan
import com.example.cellmonitor.ui.theme.TechRose
import com.example.cellmonitor.ui.theme.TextMuted
import com.example.cellmonitor.ui.theme.TextPrimary
import com.example.cellmonitor.update.UpdateChecker
import kotlinx.coroutines.launch

/**
 * Full-screen style dialog when a new version is available and not dismissed.
 * Shown automatically after the background check finds a newer release.
 */
@Composable
fun UpdatePromptHost(updateChecker: UpdateChecker) {
    val state by updateChecker.state.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    if (!state.available || state.remote == null) return

    val remote = state.remote!!

    Dialog(onDismissRequest = { /* force explicit dismiss */ }) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(TechCardSurface, RoundedCornerShape(20.dp))
                .border(1.dp, TechCardBorder, RoundedCornerShape(20.dp))
                .padding(20.dp)
        ) {
            Text(
                text = "UPDATE AVAILABLE",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = TechCyan,
                letterSpacing = 0.8.sp
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Cell Monitor ${remote.versionName}",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Text(
                text = "versionCode ${remote.versionCode}  ·  you have ${updateChecker.currentVersionName()} (${updateChecker.currentVersionCode()})",
                fontSize = 11.sp,
                color = TextMuted,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(top = 4.dp)
            )

            if (remote.releaseNotes.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = remote.releaseNotes.take(400),
                    fontSize = 13.sp,
                    color = TextMuted,
                    lineHeight = 18.sp
                )
            }

            if (state.downloading) {
                Spacer(Modifier.height(16.dp))
                LinearProgressIndicator(
                    progress = { state.downloadProgress / 100f },
                    modifier = Modifier.fillMaxWidth(),
                    color = TechCyan,
                    trackColor = TechCardBorder
                )
                Text(
                    text = "Downloading… ${state.downloadProgress}%",
                    fontSize = 11.sp,
                    color = TextMuted,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }

            state.error?.let { err ->
                Spacer(Modifier.height(8.dp))
                Text(text = err, fontSize = 12.sp, color = TechRose, lineHeight = 16.sp)
            }

            Spacer(Modifier.height(18.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = { updateChecker.dismiss() },
                    enabled = !state.downloading
                ) {
                    Text("Later", color = TextMuted)
                }
                Spacer(Modifier.width(4.dp))
                TextButton(
                    onClick = { updateChecker.openApkUrlInBrowser() },
                    enabled = !state.downloading
                ) {
                    Text("Browser", color = TextMuted)
                }
                Spacer(Modifier.width(4.dp))
                Button(
                    onClick = {
                        if (!updateChecker.canRequestInstallPackages()) {
                            context.startActivity(updateChecker.intentInstallPermissionSettings())
                            return@Button
                        }
                        scope.launch { updateChecker.downloadAndInstall() }
                    },
                    enabled = !state.downloading,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = TechCyan,
                        contentColor = androidx.compose.ui.graphics.Color(0xFF0B1220)
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        when {
                            state.downloading -> "Please wait…"
                            state.error != null -> "Retry"
                            else -> "Update now"
                        },
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/**
 * Persistent banner under the top bar when a newer APK exists.
 * Stays visible after the user taps "Later" so they are still notified in-app.
 * Tap opens the full update dialog again.
 */
@Composable
fun UpdateAvailableBanner(updateChecker: UpdateChecker) {
    val state by updateChecker.state.collectAsState()
    val remote = state.remote
    if (!state.newerAvailable || remote == null) return
    // Hide while the full dialog is open to avoid double UI
    if (state.available) return

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .background(TechCyan.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
            .border(1.dp, TechCyan.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
            .clickable { updateChecker.showUpdateDialog() }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.SystemUpdate,
            contentDescription = null,
            tint = TechCyan,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Update available · v${remote.versionName}",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Text(
                text = "Tap to install",
                fontSize = 11.sp,
                color = TextMuted
            )
        }
        Text(
            text = "OPEN",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = TechCyan,
            letterSpacing = 0.6.sp
        )
    }
}
