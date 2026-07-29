# Atrangi Document Workspace v7.1.5 — QA & Hardening Report

## Result

- Automated regression/functional/contract tests: **13,085 / 13,085 passed**
- Failed: **0**
- JavaScript syntax validation: **passed**

## Rendered UI audit

The final application was rendered in Chromium at phone, Android-like phone, tablet and desktop viewports.

- Runtime buttons: **434**
- Buttons without a click/form handler: **0**
- Runtime fields (`input`, `select`, `textarea`): **228**
- Runtime dropdown options: **369**
- Duplicate DOM IDs: **0 after v7.1.5 hardening**
- Empty dropdowns: **0**
- Invalid number/range min/max constraints: **0**
- Horizontal overflow: **0 px**
- JavaScript page errors during shell/render audit: **0**

Viewports checked:

- 390×844 standard mobile web
- 390×844 Android-wrapper layout
- 820×1180 tablet
- 1440×900 laptop/desktop

## Defects resolved

1. **Research Pro large-touch-target control duplication**
   - The previous build generated the same DOM ID in two Research Pro sections.
   - v7.1.5 hardening assigns a unique enterprise control ID and synchronises both controls to the same saved setting.

2. **Pre-scan folder dropdown initialization**
   - The folder selector could initially be empty until a scan workflow repopulated it.
   - v7.1.5 guarantees New Folder, ID Cards and Bills are available immediately and restores the selected default folder.

3. **Android system status-bar alignment**
   - Installed Android WebView mode now reserves a native-shell top inset before the Atrangi header.
   - The app header begins below the phone time/network/battery status area.
   - Standard browser/mobile web layout is unaffected.

4. **Codex v7.1.4 fixes retained**
   - Defensive tool search when a card lacks a `data-search` value.
   - Universal-file import routing from the Home import action.
   - Responsive phone/tablet/desktop grids, touch targets and safe bottom navigation.

## Scope note

The suite exercises application logic, UI contracts, scanner/passport/workspace features, fields/options, security/versioning and conversion routing. Physical-device-only behaviour such as real camera autofocus/torch hardware, OS vendor file pickers, printers and external cloud/provider credentials still requires the corresponding device or external service; those capabilities are not falsely reported as physically executed in Chromium.
