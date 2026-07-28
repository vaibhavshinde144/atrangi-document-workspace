# Atrangi Document Workspace v7.1

All-in-one document workspace for Android and web: scanning, passport/ID photos, OCR, PDF tools, document viewing/editing, conversion and file management.

## Website
The production web app is published from `docs/` with GitHub Pages.

Expected URL: `https://vaibhavshinde144.github.io/atrangi-document-workspace/`

## Android
The Android app packages the v7.1 web workspace inside a secure Android WebView wrapper.

- Package: `com.atrangi.documentworkspace`
- Version: `7.1.0`
- Minimum Android: 7.0 (API 24)

GitHub Actions builds an installable debug APK and uploads it as the `Atrangi-Document-Workspace-Android` workflow artifact.

## Build Android locally
Open the repository in Android Studio, sync Gradle, then use **Build → Build APK(s)**.

## GitHub Actions
- **Build Android APK** — builds `app-debug.apk`.
- **Deploy Website to GitHub Pages** — publishes `docs/`.

## Privacy architecture
The bundled scanner/workspace is local-first for normal browser-side scanning/editing. Features that require external OCR/cloud/enterprise providers remain explicit integration paths rather than silent uploads.
