# Atrangi Document Workspace

All-in-one document workspace for Android and web: scanning, passport/ID photos, OCR, PDF tools, document viewing/editing, conversion, file management, Light/Dark mode and workspace updates.

## Website

The production web app is published from `docs/` with GitHub Pages.

Website: `https://vaibhavshinde144.github.io/atrangi-document-workspace/`

## Current release

- Hosted workspace: `7.2.1`
- Android source/build: `7.2.3` (`versionCode 8`)
- Stable APK: `https://vaibhavshinde144.github.io/atrangi-document-workspace/downloads/Atrangi-Document-Workspace.apk`

Web features hot-update through the hosted workspace. Native Android features such as launcher resources, camera/share bridges and external **Open with Atrangi** handling require installing the latest APK.

## v7.2.3 mobile and offline PDF update

- Uses responsive phone layouts instead of rendering a desktop-width canvas inside Android WebView.
- Respects Android status-bar, display-cutout and navigation-bar safe areas.
- Opens PDFs received through Android **Open with** and **Share** directly in a native, ad-free, completely offline viewer.
- Renders PDF pages with Android `PdfRenderer`, including portrait/landscape pages, fit-to-width display, page position and direct sharing.
- Publishes updater metadata and versioned APK filenames from `docs/version.json`, preventing release versions from drifting out of sync.

## v7.2.2 external document update

- Opens external PDFs and other supported documents directly in the existing Atrangi document viewer instead of only launching the workspace home screen.
- Handles Android `ACTION_VIEW` from file managers, browsers, mail clients and other apps that expose a readable `content://` or `file://` document URI.
- Handles Android `ACTION_SEND`, so a document can also be shared directly to Atrangi.
- Streams the incoming temporary file to the web viewer in bounded chunks rather than sending one very large Base64 payload across the Android/WebView bridge.
- Treats externally opened documents as transient viewer files: they are not automatically added to the Atrangi library.
- Deletes the temporary cached copy after the viewer has received the document.
- Keeps a foreground Atrangi activity reusable through `singleTop`, avoiding an unnecessary second app instance when opening another document.

## v7.2.1 workspace highlights

- Uses the exact supplied Atrangi Riders circular logo for Android adaptive/round launcher icons, startup, header, drawer and PWA branding.
- Keeps the WebView hidden until responsive CSS is ready, preventing provisional text and alignment shifts at startup.
- Starts passport photos with a simple five-step flow: Capture, Size, Background, Adjust, and Dress & Save.
- Supports common and custom sizes; original/plain/custom backgrounds; removal strength and edge controls; crop, brightness, contrast, rotation and smoothing; and realistic men/women formal outfit overlays with fit controls.
- Keeps country/document compliance available as an optional strict workflow, so it no longer blocks or silently resets the normal editor.
- Returns full-resolution Android camera captures to the editor.
- Adds native photo/file sharing, direct app sharing, an explicit Copy install link action and a stable APK download option.
- Keeps Light/Dark mode and click-to-Home branding behavior.

## Android

- Package: `com.atrangi.documentworkspace`
- Native version: `7.2.3`
- Version code: `8`
- Hosted workspace loaded by the wrapper: `7.2.1`
- Minimum Android: 7.0 (API 24)

GitHub Actions builds an installable debug APK, validates it and publishes it to the stable APK URL above.

## Automatic update engine

The Android wrapper checks `docs/version.json` when the app launches and periodically in the background.

For hosted Atrangi UI/feature releases:

1. Android posts an **Atrangi update available** notification.
2. The user taps **Update** once.
3. Atrangi clears only WebView cache when required; local document/browser storage is preserved.
4. The latest hosted workspace loads with a versioned URL.
5. The Android activity restarts automatically.

Native resources and behaviors such as the launcher icon, camera-return handling, Android sharing bridge and external document intent handling require installing the new APK.

## Build Android locally

Open the repository in Android Studio, sync Gradle, then use **Build → Build APK(s)**.

## Validation

- `node scripts/verify-web-package.mjs` validates versioned release assets, the exact logo hash, native bridge contracts, external document intent contracts and offline package.
- `python scripts/runtime-diagnostics.py` validates the deployable bootstrap package.
- `python scripts/browser-acceptance.py <url>` runs the scanner, navigation, sharing, passport and responsive browser scenarios.
- `.github/workflows/pages.yml` runs web/browser/Android validation, publishes the stable APK and deploys GitHub Pages.

## Privacy architecture

Normal scanning, passport editing and document work are local-first. Update checks request only the public `version.json`; document contents are not uploaded as part of update checking. Externally opened Android documents are copied only to temporary app cache for viewer transfer and are removed after the viewer receives them.
