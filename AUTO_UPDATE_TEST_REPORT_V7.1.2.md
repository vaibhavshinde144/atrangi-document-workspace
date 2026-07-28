# Atrangi Document Workspace v7.1.2 — Automatic Update Validation

- Static checks: **16/16 passed**
- Failed: **0**

## Update flow
- Immediate update check whenever the app starts.
- Background update polling every 15 minutes when Android scheduling/network conditions permit.
- High-priority Android update notification.
- One tap on **Update** refreshes the hosted workspace.
- WebView cache is cleared, but local browser data/documents are preserved.
- MainActivity is restarted automatically with a cache-busted URL.
- Remote release state comes from `docs/version.json`.

## Platform boundary
This provides no-installer one-tap updates for Atrangi web/UI/feature releases. Fully silent replacement of a sideloaded native APK cannot be guaranteed on every Android device because Android may require the package-installer user-action flow.

## Static checks
- PASS — AndroidManifest XML parses
- PASS — Notification permission declared
- PASS — UpdateActivity declared
- PASS — Version 7.1.2
- PASS — Version code 3
- PASS — WorkManager dependency
- PASS — Update check on launch
- PASS — Periodic update scheduled
- PASS — Notification permission request
- PASS — Cache-only refresh
- PASS — Automatic Activity restart
- PASS — 15-minute background polling
- PASS — Public version manifest URL
- PASS — Worker invokes notification
- PASS — Version manifest matches app
- PASS — Version comparison scenarios
