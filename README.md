# Atrangi Document Workspace

All-in-one document workspace for Android and web: scanning, passport/ID photos, OCR, PDF tools, document viewing/editing, conversion, file management, Light/Dark mode and one-tap workspace updates.

## Website
The production web app is published from `docs/` with GitHub Pages.

Website: `https://vaibhavshinde144.github.io/atrangi-document-workspace/`

## Current releases
- Hosted workspace candidate: `7.1.8` (deploys only after the validation gate passes on `main`)
- Android source/build: `7.1.6` (`versionCode 4`)
- Existing installed Android wrapper `7.1.2` can hot-update to the v7.1.8 hosted workspace without reinstalling because v7.1.8 changes only the hosted UI/runtime layer.

## v7.1.8 branding, appearance and runtime fix
- Atrangi Riders circular logo replaces the built-in `A` placeholder in both the workspace header and drawer.
- Branding and hardening assets are injected into the decoded application document before it is written, so Android WebView loads them as part of the real application lifecycle.
- Clicking the header logo, drawer logo, or **Atrangi Document Workspace** title returns to Home.
- Persistent Light/Dark Mode is available from the actual top action bar.
- Visible header/drawer/hero version labels are updated to v7.1.8.
- A browser acceptance suite validates the real rendered workspace at mobile, tablet, and desktop sizes before GitHub Pages can deploy.

## Android
- Package: `com.atrangi.documentworkspace`
- Native source version: `7.1.6`
- Version code: `4`
- Minimum Android: 7.0 (API 24)

GitHub Actions can build an installable debug APK. Normal hosted workspace releases do not require an APK reinstall unless native Android resources or wrapper code change.

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
- **Build Android APK** — builds the native debug APK when required.
- **Validate and Deploy Atrangi Workspace** — validates the exact deployable web package, runs real browser acceptance tests, validates the Android project, then publishes `docs/` to GitHub Pages only after validation passes.

## Privacy architecture
The scanner/workspace is local-first for normal browser-side scanning/editing. Update checks request only the public `version.json`; document contents are not uploaded as part of update checking.
