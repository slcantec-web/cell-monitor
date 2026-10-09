package com.example.cellmonitor.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

class UpdateChecker(private val context: Context) {

    private val prefs = context.getSharedPreferences("cell_monitor_updates", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(initialStateFromPrefs())
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    private fun initialStateFromPrefs(): UpdateState {
        val dismissed = prefs.getInt(KEY_DISMISSED, 0)
        val cached = readCachedRemote()
        val local = currentVersionCode()
        val newer = cached != null && cached.versionCode > local
        val available = newer && cached!!.versionCode > dismissed
        return UpdateState(
            dismissedVersionCode = dismissed,
            remote = cached,
            newerAvailable = newer,
            available = available,
            upToDate = cached != null && cached.versionCode <= local
        )
    }

    private fun readCachedRemote(): RemoteVersion? {
        val code = prefs.getInt(KEY_CACHED_CODE, 0)
        if (code <= 0) return null
        val name = prefs.getString(KEY_CACHED_NAME, null) ?: return null
        val url = prefs.getString(KEY_CACHED_URL, null) ?: return null
        val notes = prefs.getString(KEY_CACHED_NOTES, "") ?: ""
        return RemoteVersion(code, name, url, notes)
    }

    private fun cacheRemote(remote: RemoteVersion) {
        prefs.edit()
            .putInt(KEY_CACHED_CODE, remote.versionCode)
            .putString(KEY_CACHED_NAME, remote.versionName)
            .putString(KEY_CACHED_URL, remote.apkUrl)
            .putString(KEY_CACHED_NOTES, remote.releaseNotes)
            .apply()
    }

    private fun applyRemote(remote: RemoteVersion, force: Boolean) {
        cacheRemote(remote)
        val local = currentVersionCode()
        val dismissed = prefs.getInt(KEY_DISMISSED, 0)
        val newer = remote.versionCode > local
        val available = newer && (force || remote.versionCode > dismissed)
        _state.update {
            it.copy(
                checking = false,
                available = available,
                newerAvailable = newer,
                remote = remote,
                error = null,
                dismissedVersionCode = dismissed,
                upToDate = !newer
            )
        }
    }

    fun currentVersionCode(): Int {
        return try {
            val pi = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pi.longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                pi.versionCode
            }
        } catch (_: Exception) {
            1
        }
    }

    fun currentVersionName(): String {
        return try {
            val pi = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            pi.versionName ?: "1.0"
        } catch (_: Exception) {
            "1.0"
        }
    }

    /** Call on resume / startup. Respects cooldown unless [force]. */
    suspend fun check(force: Boolean = false) {
        if (_state.value.checking) return
        if (!force) {
            val last = prefs.getLong(KEY_LAST_CHECK, 0L)
            if (System.currentTimeMillis() - last < UpdateConfig.CHECK_COOLDOWN_MS) {
                // Still refresh UI from cache so banner/dialog can show without a network hit
                val cached = readCachedRemote()
                if (cached != null) applyRemote(cached, force = false)
                return
            }
        }
        _state.update { it.copy(checking = true, error = null) }
        try {
            val remote = fetchRemoteVersion()
            prefs.edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply()
            applyRemote(remote, force = force)
        } catch (e: Exception) {
            // Keep any cached "update available" state so the banner does not disappear offline
            _state.update {
                it.copy(checking = false, error = e.message ?: "Update check failed")
            }
        }
    }

    /** Re-open the full update dialog for the known newer version. */
    fun showUpdateDialog() {
        val remote = _state.value.remote ?: return
        if (remote.versionCode <= currentVersionCode()) return
        clearDismissed()
        _state.update { it.copy(available = true, newerAvailable = true) }
    }

    fun dismiss() {
        val remote = _state.value.remote ?: return
        val code = remote.versionCode
        prefs.edit().putInt(KEY_DISMISSED, code).apply()
        val stillNewer = code > currentVersionCode()
        _state.update {
            // Keep newerAvailable so the banner stays visible after "Later"
            it.copy(available = false, dismissedVersionCode = code, newerAvailable = stillNewer)
        }
    }

    fun clearDismissed() {
        prefs.edit().remove(KEY_DISMISSED).apply()
        _state.update { it.copy(dismissedVersionCode = 0) }
    }

    /**
     * Download APK and launch system installer.
     * Requires REQUEST_INSTALL_PACKAGES on Android 8+.
     */
    suspend fun downloadAndInstall(): Boolean = withContext(Dispatchers.IO) {
        val remote = _state.value.remote ?: return@withContext false
        if (!canRequestInstallPackages()) {
            _state.update {
                it.copy(
                    error = "Allow “Install unknown apps” for Cell Monitor, then tap Update again."
                )
            }
            return@withContext false
        }
        _state.update { it.copy(downloading = true, downloadProgress = 0, error = null) }
        try {
            val dest = File(context.cacheDir, "CellMonitor-update.apk")
            if (dest.exists()) dest.delete()

            downloadToFile(remote.apkUrl, dest) { pct ->
                _state.update { it.copy(downloadProgress = pct) }
            }

            // Basic integrity: APK is a ZIP starting with PK
            val header = dest.inputStream().use { it.readNBytes(4) }
            if (header.size < 2 || header[0] != 'P'.code.toByte() || header[1] != 'K'.code.toByte()) {
                throw IllegalStateException("Downloaded file is not a valid APK")
            }
            if (dest.length() < 50_000L) {
                throw IllegalStateException("Downloaded APK is too small (${dest.length()} bytes)")
            }

            _state.update { it.copy(downloadProgress = 100) }
            withContext(Dispatchers.Main) {
                installApk(dest)
            }
            _state.update {
                it.copy(
                    downloading = false,
                    error = "Installer opened. If it says “App not installed”, uninstall the old app once (different signing key), then install this APK again."
                )
            }
            true
        } catch (e: Exception) {
            _state.update {
                it.copy(downloading = false, error = e.message ?: "Download failed")
            }
            false
        }
    }

    /** Follow redirects manually (GitHub → release-assets CDN). */
    private fun downloadToFile(urlStr: String, dest: File, onProgress: (Int) -> Unit) {
        var current = urlStr
        var redirects = 0
        while (redirects < 8) {
            val conn = (URL(current).openConnection() as HttpURLConnection).apply {
                connectTimeout = 20_000
                readTimeout = 120_000
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", "CellMonitor-Updater")
                setRequestProperty("Accept", "*/*")
            }
            conn.connect()
            val code = conn.responseCode
            if (code in 300..399) {
                val loc = conn.getHeaderField("Location")
                    ?: throw IllegalStateException("Redirect $code with no Location")
                current = if (loc.startsWith("http")) loc else URL(URL(current), loc).toString()
                conn.disconnect()
                redirects++
                continue
            }
            if (code !in 200..299) {
                throw IllegalStateException("Download HTTP $code")
            }
            val total = conn.contentLengthLong.coerceAtLeast(0L)
            conn.inputStream.use { input ->
                FileOutputStream(dest).use { out ->
                    val buf = ByteArray(64 * 1024)
                    var read: Int
                    var done = 0L
                    while (input.read(buf).also { read = it } != -1) {
                        out.write(buf, 0, read)
                        done += read
                        if (total > 0) {
                            onProgress(((done * 100) / total).toInt().coerceIn(0, 100))
                        }
                    }
                }
            }
            conn.disconnect()
            return
        }
        throw IllegalStateException("Too many redirects downloading APK")
    }

    fun openApkUrlInBrowser() {
        val url = _state.value.remote?.apkUrl ?: return
        try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Exception) { /* ignore */ }
    }

    fun canRequestInstallPackages(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else true
    }

    fun intentInstallPermissionSettings(): Intent {
        return Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    private fun installApk(file: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
            )
        }
        // Grant to package installer packages that may handle the intent
        val resInfo = context.packageManager.queryIntentActivities(intent, 0)
        for (ri in resInfo) {
            context.grantUriPermission(
                ri.activityInfo.packageName,
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
        context.startActivity(intent)
    }

    private suspend fun fetchRemoteVersion(): RemoteVersion = withContext(Dispatchers.IO) {
        var pages: RemoteVersion? = null
        var pagesError: Exception? = null
        try {
            pages = parseVersionJson(httpGet(UpdateConfig.VERSION_JSON_URL))
        } catch (e: Exception) {
            pagesError = e
        }

        var github: RemoteVersion? = null
        var githubError: Exception? = null
        try {
            val api = "https://api.github.com/repos/${UpdateConfig.GITHUB_REPO}/releases/latest"
            github = parseGithubRelease(httpGet(api))
        } catch (e: Exception) {
            githubError = e
        }

        listOfNotNull(pages, github).maxByOrNull { it.versionCode }
            ?: throw (githubError ?: pagesError ?: IllegalStateException("No update source reachable"))
    }

    private fun httpGet(url: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 12_000
            readTimeout = 12_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "CellMonitor-Updater")
        }
        conn.connect()
        if (conn.responseCode !in 200..299) {
            throw IllegalStateException("HTTP ${conn.responseCode} for $url")
        }
        return conn.inputStream.bufferedReader().use { it.readText() }
    }

    private fun parseVersionJson(json: String): RemoteVersion {
        val o = JSONObject(json)
        return RemoteVersion(
            versionCode = o.getInt("versionCode"),
            versionName = o.optString("versionName", ""),
            apkUrl = o.getString("apkUrl"),
            releaseNotes = o.optString("releaseNotes", ""),
            publishedAt = o.optString("publishedAt", "")
        )
    }

    private fun parseGithubRelease(json: String): RemoteVersion {
        val o = JSONObject(json)
        val tag = o.optString("tag_name", "v0").removePrefix("v")
        val assets = o.optJSONArray("assets")
        var apkUrl: String? = null
        if (assets != null) {
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                val name = a.optString("name", "")
                if (name.endsWith(".apk", ignoreCase = true)) {
                    apkUrl = a.getString("browser_download_url")
                    break
                }
            }
        }
        val finalApkUrl = apkUrl ?: throw IllegalStateException("Release $tag has no .apk asset")
        val code = tag.split(".").mapNotNull { it.toIntOrNull() }.let { p ->
            when (p.size) {
                3 -> p[0] * 10000 + p[1] * 100 + p[2]
                2 -> p[0] * 10000 + p[1] * 100
                1 -> p[0]
                else -> 0
            }
        }.coerceAtLeast(1)
        return RemoteVersion(
            versionCode = code,
            versionName = tag,
            apkUrl = finalApkUrl,
            releaseNotes = o.optString("body", "").take(500),
            publishedAt = o.optString("published_at", "")
        )
    }

    companion object {
        private const val KEY_LAST_CHECK = "last_check_ms"
        private const val KEY_DISMISSED = "dismissed_version_code"
        private const val KEY_CACHED_CODE = "cached_remote_code"
        private const val KEY_CACHED_NAME = "cached_remote_name"
        private const val KEY_CACHED_URL = "cached_remote_url"
        private const val KEY_CACHED_NOTES = "cached_remote_notes"
    }
}
