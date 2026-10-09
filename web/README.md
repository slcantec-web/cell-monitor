# Cell Monitor — download site (Cloudflare Pages)

## Deploy

1. In [Cloudflare Dashboard](https://dash.cloudflare.com) → **Workers & Pages** → **Create** → **Pages** → **Connect to Git**.
2. Select repo `slcantec-web/cell-monitor`.
3. Build settings:
   - **Root directory:** `web`
   - **Build command:** *(leave empty)*
   - **Build output directory:** `/` (or leave default; root is already the site)
4. Save and deploy. Note your URL, e.g. `https://cell-monitor.pages.dev`.

5. In the Android app, set that host in  
   `app/src/main/java/com/example/cellmonitor/update/UpdateConfig.kt`:

```kotlin
const val VERSION_JSON_URL = "https://cell-monitor.pages.dev/version.json"
```

## What users see

- Landing page with **Download APK** (reads `version.json`).
- App checks the same `version.json` on launch / resume and shows an update dialog when `versionCode` is higher.

## Publish a new build

**Option A — tag**

```bash
# bump is done by CI from the tag name
git tag v1.1.0
git push origin v1.1.0
```

**Option B — Actions UI**

GitHub → **Actions** → **Release APK + update site** → **Run workflow**  
Enter `versionName`, `versionCode` (must increase), and notes.

CI will:

1. Build the APK  
2. Create a GitHub Release and upload `CellMonitor.apk`  
3. Update `web/version.json` and push (Cloudflare Pages redeploys automatically if connected)

## Signing key (required once)

Android only installs an update over an existing app when both APKs are signed with the **same key**.
`build-apk.yml` makes a brand-new throw-away key on every run, so its APKs can never update each other.
The release workflow therefore signs with one fixed key stored in GitHub secrets.

```bash
keytool -genkeypair -v -keystore cellmonitor.keystore -alias cellmonitor \
  -keyalg RSA -keysize 2048 -validity 36500
base64 -w0 cellmonitor.keystore   # copy the output
```

GitHub repo → **Settings → Secrets and variables → Actions → New repository secret**:

| Secret | Value |
|---|---|
| `KEYSTORE_BASE64` | the base64 output above |
| `KEYSTORE_PASSWORD` | the keystore password |
| `KEY_ALIAS` | `cellmonitor` |
| `KEY_PASSWORD` | the key password |

Keep a private backup of `cellmonitor.keystore`; if it is lost, no future build can update installed copies.

Phones that have an APK signed with an older (different) key must uninstall once and install the first
release built by this workflow. After that, updates install in place.
