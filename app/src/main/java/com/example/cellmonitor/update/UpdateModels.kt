package com.example.cellmonitor.update

/**
 * Remote version payload (web/version.json on Cloudflare Pages).
 */
data class RemoteVersion(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val releaseNotes: String = "",
    val publishedAt: String = ""
)

data class UpdateState(
    val checking: Boolean = false,
    /** True when a newer version exists and the user has not dismissed the full dialog. */
    val available: Boolean = false,
    /**
     * True whenever remote versionCode > installed versionCode.
     * Stays true even after "Later" so the in-app banner / badge can keep notifying.
     */
    val newerAvailable: Boolean = false,
    val remote: RemoteVersion? = null,
    val error: String? = null,
    val downloading: Boolean = false,
    val downloadProgress: Int = 0, // 0–100
    val dismissedVersionCode: Int = 0,
    /** True after a successful check when remote <= local */
    val upToDate: Boolean = false
)
