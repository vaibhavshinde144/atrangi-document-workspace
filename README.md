# Atrangi Document Workspace v7.1.2

All-in-one document workspace for Android and web: scanning, passport/ID photos, OCR, PDF tools, document viewing/editing, conversion, file management and one-tap workspace updates.

## Website
The production web app is published from `docs/` with GitHub Pages.

Website: `https://vaibhavshinde144.github.io/atrangi-document-workspace/`

## Android
- Package: `com.atrangi.documentworkspace`
- Version: `7.1.2`
- Version code: `3`
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

This allows most future Atrangi releases to update without downloading or reinstalling the APK.

### Native APK updates
Android does not universally allow a sideloaded app to silently replace its own APK on every device/version. Native wrapper changes may still require Android's package-installer approval unless the distribution/install context qualifies for Android's no-user-action update flow or the app is distributed through Google Play/managed devices.

## Build Android locally
Open the repository in Android Studio, sync Gradle, then use **Build → Build APK(s)**.

## GitHub Actions
- **Build Android APK** — builds `app-debug.apk`.
- **Deploy Website to GitHub Pages** — publishes `docs/` including `version.json`.

## Privacy architecture
The scanner/workspace is local-first for normal browser-side scanning/editing. Update checks request only the public `version.json`; document contents are not uploaded as part of update checking.
