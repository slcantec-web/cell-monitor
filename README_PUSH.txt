IMPORTANT
1. Replace app/src/main/java/com/example/cellmonitor/ui/CellMonitorScreen.kt with the file in this zip.
2. Commit and push to main.
3. CI Build APK should succeed.

This fixes:
- Smart-cast errors on updateChecker inside click lambdas (use local "updater")
- Conditional rememberInfiniteTransition (Compose rules) — always remember, apply rotation only when refreshing
- Diagnostics remember/brace issues simplified to plain buildString
