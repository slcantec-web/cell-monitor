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
    val available: Boolean = false,
    val remote: RemoteVersion? = null,
    val error: String? = null,
    val downloading: Boolean = false,
    val downloadProgress: Int = 0, // 0–100
    val dismissedVersionCode: Int = 0
)
