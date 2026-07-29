#!/usr/bin/env python3
import json
import os
import sys
import tempfile
import time
from pathlib import Path

from selenium import webdriver
from selenium.common.exceptions import JavascriptException, TimeoutException
from selenium.webdriver.chrome.options import Options
from selenium.webdriver.common.by import By
from selenium.webdriver.support.ui import WebDriverWait

URL = sys.argv[1] if len(sys.argv) > 1 else "http://127.0.0.1:8123/"
OUT = Path(os.environ.get("ATRANGI_TEST_REPORT", "ci/browser-acceptance.json"))
OUT.parent.mkdir(parents=True, exist_ok=True)

checks = []
failures = []


def record(name, ok, detail=""):
    checks.append({"name": name, "ok": bool(ok), "detail": str(detail)[:1200]})
    if not ok:
        failures.append(f"{name}: {detail}")
    print(("PASS" if ok else "FAIL"), name, detail)


def make_driver(width=390, height=844):
    opts = Options()
    for arg in ["--headless=new", "--no-sandbox", "--disable-gpu", "--disable-dev-shm-usage", "--disable-background-networking"]:
        opts.add_argument(arg)
    opts.add_argument(f"--window-size={width},{height}")
    opts.set_capability("goog:loggingPrefs", {"browser": "ALL"})
    d = webdriver.Chrome(options=opts)
    d.set_window_size(width, height)
    return d


def wait_js(d, expr, timeout=15):
    return WebDriverWait(d, timeout).until(lambda x: x.execute_script(f"return Boolean({expr})"))


def active_panel(d, panel_id):
    return d.execute_script("const p=document.getElementById(arguments[0]);return !!p&&p.classList.contains('active-panel')", panel_id)


def safe_click(d, selector):
    return d.execute_script("const e=document.querySelector(arguments[0]);if(!e)return false;e.click();return true", selector)


def console_errors(d):
    out = []
    try:
        for row in d.get_log("browser"):
            msg = row.get("message", "")
            level = row.get("level", "")
            if level == "SEVERE" and not any(x in msg for x in ["favicon.ico", "ERR_BLOCKED_BY_CLIENT"]):
                out.append(msg)
    except Exception:
        pass
    return out


def runtime_suite():
    d = make_driver(390, 844)
    try:
        d.get(URL + ("&" if "?" in URL else "?") + "acceptance=runtime")
        wait_js(d, "document.getElementById('app')")
        wait_js(d, "document.documentElement.dataset.atrangiBrandReady==='true'", 20)
        record("runtime app loaded", True)

        title = d.title
        record("browser title v7.1.8", title == "Atrangi Document Workspace v7.1.8", title)
        version = d.execute_script("return document.documentElement.dataset.atrangiVersion")
        record("runtime version marker", version == "7.1.8", version)

        logo_info = d.execute_script("return [...document.querySelectorAll('.brand-logo img.atrangi-brand-image')].map(x=>({src:x.getAttribute('src'),w:x.naturalWidth,h:x.naturalHeight,display:getComputedStyle(x).display}))")
        record("header and drawer logo replaced", len(logo_info) >= 2 and all(x["w"] > 0 and x["h"] > 0 for x in logo_info), json.dumps(logo_info))
        placeholder = d.execute_script("return [...document.querySelectorAll('.brand-logo')].some(x=>x.textContent.trim()==='A')")
        record("old A placeholder removed", not placeholder, placeholder)

        theme_visible = d.execute_script("const b=document.getElementById('themeToggleBtn');if(!b)return false;const s=getComputedStyle(b);const r=b.getBoundingClientRect();return s.display!=='none'&&s.visibility!=='hidden'&&r.width>0&&r.height>0")
        record("theme control visible on mobile", theme_visible)
        before = d.execute_script("return document.documentElement.dataset.theme")
        safe_click(d, "#themeToggleBtn")
        WebDriverWait(d, 5).until(lambda x: x.execute_script("return document.documentElement.dataset.theme") != before)
        after = d.execute_script("return document.documentElement.dataset.theme")
        saved = d.execute_script("return localStorage.getItem('atrangi.theme')")
        record("theme toggles and persists", after in ("dark", "light") and after != before and saved == after, f"{before}->{after}, saved={saved}")

        nav_map = {"home":"homePanel","tools":"toolsPanel","ocr":"ocrPanel","idPhoto":"idPhotoPanel","files":"filesPanel","settings":"settingsPanel"}
        for tab, panel in nav_map.items():
            clicked = d.execute_script("const b=document.querySelector('#mobileNav [data-tab=\"'+arguments[0]+'\"]')||document.querySelector('#desktopNav [data-tab=\"'+arguments[0]+'\"]');if(!b)return false;b.click();return true", tab)
            ok = clicked and WebDriverWait(d, 5).until(lambda x, p=panel: active_panel(x, p))
            record(f"navigation {tab}", ok, panel)

        # Home title and logo must navigate back from another panel.
        safe_click(d, '#mobileNav [data-tab="tools"],#desktopNav [data-tab="tools"]')
        WebDriverWait(d, 5).until(lambda x: active_panel(x, "toolsPanel"))
        record("app title clickable Home", safe_click(d, ".brand-block h1") and WebDriverWait(d, 5).until(lambda x: active_panel(x, "homePanel")))
        safe_click(d, '#mobileNav [data-tab="ocr"],#desktopNav [data-tab="ocr"]')
        WebDriverWait(d, 5).until(lambda x: active_panel(x, "ocrPanel"))
        record("logo clickable Home", safe_click(d, ".topbar .brand-logo") and WebDriverWait(d, 5).until(lambda x: active_panel(x, "homePanel")))

        # Drawer controls.
        opened = safe_click(d, "#menuBtn")
        if opened:
            WebDriverWait(d, 5).until(lambda x: x.execute_script("const n=document.getElementById('navDrawer');return n && !n.hidden && n.getAttribute('aria-hidden')!=='true'"))
        record("navigation drawer opens", opened)
        closed = safe_click(d, "#drawerCloseBtn")
        if closed:
            WebDriverWait(d, 5).until(lambda x: x.execute_script("const n=document.getElementById('navDrawer');return !n || n.hidden || n.getAttribute('aria-hidden')==='true'"))
        record("navigation drawer closes", closed)

        # Hero workspace and import routes.
        safe_click(d, '#mobileNav [data-tab="home"],#desktopNav [data-tab="home"]')
        record("hero Files Workspace route", safe_click(d, "#heroWorkspaceBtn") and WebDriverWait(d, 5).until(lambda x: active_panel(x, "filesPanel")))
        safe_click(d, '#mobileNav [data-tab="home"],#desktopNav [data-tab="home"]')
        import_clicked = safe_click(d, "#heroImportBtn")
        time.sleep(.25)
        record("hero Import route", import_clicked and active_panel(d, "filesPanel"), "filesPanel active after import action")

        # Universal file import uses real local file into IndexedDB/repository.
        test_file = Path(tempfile.gettempdir()) / "atrangi-browser-acceptance.txt"
        test_file.write_text("Atrangi browser acceptance file\nOCR and universal workspace test\n", encoding="utf-8")
        d.execute_script("const i=document.getElementById('universalImportInput');if(i){i.hidden=false;i.style.display='block'}")
        input_el = d.find_element(By.ID, "universalImportInput")
        input_el.send_keys(str(test_file))
        imported = WebDriverWait(d, 12).until(lambda x: x.execute_script("const c=Number(document.getElementById('universalCount')?.textContent||0);return c>=1 || document.querySelectorAll('#universalRepo .universal-file-card').length>=1"))
        record("universal file import and repository", imported)

        # Tools search must filter without throwing even on injected cards.
        safe_click(d, '#mobileNav [data-tab="tools"],#desktopNav [data-tab="tools"]')
        search = d.find_element(By.ID, "toolSearch")
        search.clear(); search.send_keys("PDF")
        time.sleep(.25)
        visible_tools = d.execute_script("return [...document.querySelectorAll('#toolGroups .tool-card-button')].filter(b=>!b.hidden).length")
        record("tools search/filter", visible_tools > 0, visible_tools)

        # Scan launch should trigger one of the known scanner launch surfaces without breaking the app.
        safe_click(d, '#mobileNav [data-tab="home"],#desktopNav [data-tab="home"]')
        scan_clicked = safe_click(d, "#heroScanBtn")
        time.sleep(.5)
        scan_surface = d.execute_script("return ['scanStartSheet','cameraStage','documentBuilderDialog'].some(id=>{const e=document.getElementById(id);if(!e)return false;if(e.tagName==='DIALOG')return e.open;const s=getComputedStyle(e);return !e.hidden&&s.display!=='none'&&s.visibility!=='hidden'})")
        record("scan entry action", scan_clicked and scan_surface, scan_surface)

        errs = console_errors(d)
        record("no severe JavaScript console errors", len(errs) == 0, "\n".join(errs[:10]))
    except Exception as e:
        record("runtime suite completed", False, repr(e))
    finally:
        d.quit()


def responsive_suite():
    for width, height, label in [(360, 800, "small-mobile"), (390, 844, "mobile"), (768, 1024, "tablet"), (1440, 900, "desktop")]:
        d = make_driver(width, height)
        try:
            d.get(URL + ("&" if "?" in URL else "?") + f"acceptance={label}")
            wait_js(d, "document.getElementById('app')")
            wait_js(d, "document.documentElement.dataset.atrangiBrandReady==='true'", 20)
            metrics = d.execute_script("const de=document.documentElement,b=document.body,h=document.querySelector('.topbar'),l=document.querySelector('.topbar .brand-logo img'),t=document.getElementById('themeToggleBtn');const visible=e=>!!e&&getComputedStyle(e).display!=='none'&&e.getBoundingClientRect().width>0;return {innerWidth:innerWidth,scrollWidth:Math.max(de.scrollWidth,b.scrollWidth),header:h?h.getBoundingClientRect():null,logo:visible(l),theme:visible(t)}")
            no_overflow = metrics["scrollWidth"] <= metrics["innerWidth"] + 2
            header_ok = metrics["header"] and metrics["header"]["left"] >= -2 and metrics["header"]["right"] <= metrics["innerWidth"] + 2
            record(f"responsive {label}", no_overflow and header_ok and metrics["logo"] and metrics["theme"], json.dumps(metrics))
        except Exception as e:
            record(f"responsive {label}", False, repr(e))
        finally:
            d.quit()


runtime_suite()
responsive_suite()
OUT.write_text(json.dumps({"url": URL, "checks": checks, "failureCount": len(failures), "failures": failures}, indent=2) + "\n", encoding="utf-8")
print(f"Acceptance checks: {len(checks)}, failures: {len(failures)}")
if failures:
    raise SystemExit(1)
