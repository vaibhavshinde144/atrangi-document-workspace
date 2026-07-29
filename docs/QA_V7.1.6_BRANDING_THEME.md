# Atrangi Document Workspace v7.1.6 — Branding & Theme QA

## Implemented
- Atrangi Riders circular logo in the app header and navigation drawer.
- Logo/app-name Home navigation with mouse, touch, Enter and Space keyboard activation.
- Persistent Light/Dark mode toggle with browser/system theme fallback.
- Dynamic `theme-color` update for browser and Android WebView chrome.
- PWA/favicon branding source.
- Android launcher icon generated from the same transparent circular logo source during Gradle configuration.

## Automated regression
- Full Node test suite: **13,095 / 13,095 passed**.
- Branding/theme-specific tests cover logo binding, Home routing contract, theme persistence, dark-style coverage, PWA/service-worker references and print-like document fidelity.
- `branding-v716.js` syntax validation: **PASS**.

## Asset validation
- Logo source remains transparent outside the circular mark; no square or white outer background is introduced.
- GitHub Pages materializes the PNG from the tracked base64 source before publication.
- Android Gradle materializes the same logo as the launcher drawable before resource processing.

## Boundary
A physical Android launcher-icon change requires installing the newly built v7.1.6 APK once. The existing v7.1.2 Android wrapper can receive the v7.1.6 web UI/theme/Home-navigation changes through the existing hosted-workspace updater, but a hosted web update cannot replace Android package launcher resources.
