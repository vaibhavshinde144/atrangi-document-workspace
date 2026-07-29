# Atrangi Document Workspace

All-in-one document workspace for Android and web: scanning, passport/ID photos, OCR, PDF tools, document viewing/editing, conversion, file management, Light/Dark mode and one-tap workspace updates.

## Website
The production web app is published from `docs/` with GitHub Pages.

Website: `https://vaibhavshinde144.github.io/atrangi-document-workspace/`

## Current releases
- Hosted workspace: `7.1.6`
- Android source/build: `7.1.6` (`versionCode 4`)
- Existing installed Android wrapper `7.1.2` can hot-update to the v7.1.6 hosted workspace without reinstalling.

## v7.1.6 branding and appearance
- Atrangi Riders circular logo is used in the workspace header/drawer and as the PWA/favicon brand asset.
- Clicking the logo or **Atrangi Document Workspace** brand returns to Home.
- Light/Dark mode is available from the top bar and persists locally.
- Android launcher source now uses the Atrangi Riders logo. Launcher-icon changes require installing the newly built v7.1.6 APK once because a hosted web update cannot replace the Android package icon.

## Android
- Package: `com.atrangi.documentworkspace`
- New build version: `7.1.6`
- Version code: `4`
- Minimum Android: 7.0 (API 24)

GitHub Actions builds an installable debug APK and uploads it as the `Atrangi-Document-Workspace-Android` workflow artifact.

## Automatic update engine
The Android wrapper checks `docs/version.json` when the app launches and periodically in the background.

For normal Atrangi UI/feature releases:
1. Android posts an **Atrangi update available** notification.
2. The user taps **Update** once.
3. Atrangi clears only WebView cache (not local document/browser storage).
4. The latest hosted workspace is loaded with a cache-busted URL.
5. The Android activity restarts automatically.

This allows normal UI and feature releases to update without reinstalling the APK. Native resources such as the launcher icon still require a native APK update.

## Build Android locally
Open the repository in Android Studio, sync Gradle, then use **Build → Build APK(s)**.

## GitHub Actions
- **Build Android APK** — builds `app-debug.apk`.
- **Deploy Website to GitHub Pages** — publishes `docs/` including `version.json` and materializes the logo PNG from the tracked base64 brand source.

## Privacy architecture
The scanner/workspace is local-first for normal browser-side scanning/editing. Update checks request only the public `version.json`; document contents are not uploaded as part of update checking.
