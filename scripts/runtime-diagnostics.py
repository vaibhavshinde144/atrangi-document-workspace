#!/usr/bin/env python3
import argparse
import base64
import gzip
import json
import re
from pathlib import Path


def decode_bundle(docs: Path) -> str:
    names = [f"app-{i:02d}.txt" for i in range(7)]
    encoded = "".join((docs / n).read_text(encoding="utf-8").strip() for n in names)
    return gzip.decompress(base64.b64decode(encoded)).decode("utf-8", "replace")


def snippet(source: str, term: str, radius: int = 1000) -> str:
    i = source.find(term)
    if i < 0:
        return ""
    return re.sub(r"\s+", " ", source[max(0, i - radius): min(len(source), i + radius)])


def static_report(docs: Path) -> dict:
    source = decode_bundle(docs)
    classes = re.findall(r'class=["\']([^"\']+)["\']', source, re.I)
    ids = re.findall(r'id=["\']([^"\']+)["\']', source, re.I)
    refs = re.findall(r'<(?:script|link)\b[^>]+(?:src|href)=["\']([^"\']+)["\']', source, re.I)
    return {
        "htmlBytes": len(source.encode()),
        "hasTopbar": bool(re.search(r'class=["\'][^"\']*\btopbar\b', source, re.I)),
        "hasBrandBlock": "brand-block" in source,
        "hasBrandLogo": "brand-logo" in source,
        "hasTopActions": "top-actions" in source,
        "hasHomePanel": "homePanel" in source,
        "hasDesktopNav": "desktopNav" in source,
        "hasMobileNav": "mobileNav" in source,
        "hasThemeToggleInBase": "themeToggleBtn" in source,
        "hasAtrangiPlaceholder": bool(re.search(r">\s*A\s*<", source)),
        "assetRefs": refs,
        "ids": sorted(set(ids)),
        "classes": sorted(set(c for group in classes for c in group.split())),
        "topbarSnippet": snippet(source, "topbar"),
        "brandSnippet": snippet(source, "brand-block"),
        "logoSnippet": snippet(source, "brand-logo"),
        "actionsSnippet": snippet(source, "top-actions"),
        "homeSnippet": snippet(source, "homePanel"),
        "titleSnippet": snippet(source, "Atrangi Document Workspace"),
    }


def rendered_report(dom: str) -> dict:
    return {
        "renderedDomBytes": len(dom.encode()),
        "brandingScriptLoaded": "branding-v716.js" in dom,
        "brandingCssLoaded": "branding-v716.css" in dom,
        "hasBrandImageClass": "atrangi-brand-image" in dom,
        "hasAtrangiLogoLive": "atrangi-logo-live" in dom,
        "hasThemeToggle": 'id="themeToggleBtn"' in dom or 'id="atrangiFloatingTheme"' in dom,
        "hasThemeDataset": bool(re.search(r"<html[^>]+data-theme=", dom, re.I)),
        "hasVersionDataset": 'data-atrangi-version="7.1.7"' in dom,
        "hasHomeBinding": 'data-atrangi-home-bound="1"' in dom,
        "stillHasHeaderPlaceholder": bool(re.search(r'<div class="brand-logo[^>]*>\s*<span>A</span>', dom, re.I)),
        "titleIs717": "<title>Atrangi Document Workspace v7.1.7</title>" in dom,
        "bodyHasApp": 'id="app"' in dom,
    }


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--docs", default="docs")
    ap.add_argument("--report", default="ci/runtime-diagnostics.json")
    ap.add_argument("--rendered-dom")
    args = ap.parse_args()
    docs = Path(args.docs)
    report_path = Path(args.report)
    report_path.parent.mkdir(parents=True, exist_ok=True)
    report = static_report(docs)
    if args.rendered_dom:
        dom = Path(args.rendered_dom).read_text(encoding="utf-8", errors="replace")
        report["browserRuntime"] = rendered_report(dom)
    report_path.write_text(json.dumps(report, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    summary = {k: report[k] for k in (
        "htmlBytes", "hasTopbar", "hasBrandBlock", "hasBrandLogo", "hasTopActions",
        "hasHomePanel", "hasDesktopNav", "hasMobileNav", "hasThemeToggleInBase", "hasAtrangiPlaceholder"
    )}
    if "browserRuntime" in report:
        summary["browserRuntime"] = report["browserRuntime"]
    print(json.dumps(summary, indent=2))


if __name__ == "__main__":
    main()
