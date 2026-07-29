#!/usr/bin/env python3
import argparse,base64,gzip,json,re
from pathlib import Path

def decode_bundle(docs):
    encoded=''.join((docs/f'app-{i:02d}.txt').read_text().strip() for i in range(7))
    return gzip.decompress(base64.b64decode(encoded)).decode('utf-8','replace')

def main():
    ap=argparse.ArgumentParser();ap.add_argument('--docs',default='docs');ap.add_argument('--report',default='ci/runtime-diagnostics.json');ap.add_argument('--rendered-dom');a=ap.parse_args();docs=Path(a.docs)
    index=(docs/'index.html').read_text(encoding='utf-8',errors='replace');source=decode_bundle(docs)
    report={'htmlBytes':len(source.encode()),'loaderBytes':len(index.encode()),'webVersion':'7.1.9','deliveryMode':'external-bootstrap','hasTopbar':'class="topbar"' in source,'loaderIsExternal':'bootstrap-v719.js' in index,'indexHasInlineBootstrap':any(x in index for x in ['DecompressionStream','document.write(text)','const text=source.replace']),'baseHasHomePanel':'id="homePanel"' in source,'baseHasDesktopNav':'id="desktopNav"' in source,'baseHasMobileNav':'id="mobileNav"' in source}
    if a.rendered_dom:
        dom=Path(a.rendered_dom).read_text(encoding='utf-8',errors='replace');report['browserRuntime']={'renderedDomBytes':len(dom.encode()),'hasApp':'id="app"' in dom,'hasBrandHome':'id="brandHomeBtn"' in dom,'hasThemeToggle':'id="themeToggleBtn"' in dom,'hasLogoImage':bool(re.search(r'class="brand-logo[^>]*>\s*<img',dom,re.I)),'brandingTestPass':'data-branding-test="PASS"' in dom,'brandingFunctionalTestPass':'data-branding-functional-test="PASS"' in dom,'coreSelfTestPass':'data-self-test="PASS"' in dom,'stillHasPlaceholder':'<span>A</span>' in dom,'titleIs719':'Atrangi Document Workspace v7.1.9' in dom,'showsBootstrapCodeAsText':any(x in dom for x in ['const text=source.replace','hardCss=document.createElement','Unable to load app:'])}
    path=Path(a.report);path.parent.mkdir(parents=True,exist_ok=True);path.write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8');print(json.dumps(report,indent=2))
if __name__=='__main__':main()
