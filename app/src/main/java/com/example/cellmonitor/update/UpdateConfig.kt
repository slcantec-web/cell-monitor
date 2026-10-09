package com.example.cellmonitor.update

/**
 * Where the app looks for updates.
 *
 * After you connect the `web/` folder to Cloudflare Pages, set [VERSION_JSON_URL]
 * to your Pages URL, e.g.:
 *   https://cell-monitor.pages.dev/version.json
 *   https://your-custom-domain.com/version.json
 *
 * [GITHUB_REPO] is used as a fallback (GitHub Releases API) if the Pages URL fails.
 */
object UpdateConfig {
    /**
     * Primary: Cloudflare Pages (or any static host) version.json.
     * Leave as-is until you deploy Pages — fallback to GitHub still works.
     */
    const val VERSION_JSON_URL = "https://cell-monitor.pages.dev/version.json"

    /** owner/repo for GitHub Releases fallback */
    const val GITHUB_REPO = "slcantec-web/cell-monitor"

    /** How often to auto-check (ms). Checked on app resume / cold start. */
    const val CHECK_COOLDOWN_MS = 30 * 60 * 1000L // 30 minutes
}
