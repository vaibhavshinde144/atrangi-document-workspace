#!/usr/bin/env python3
import argparse,base64,gzip,json,re
from pathlib import Path
def decode_bundle(docs):
    encoded=''.join((docs/f'app-{i:02d}.txt').read_text().strip() for i in range(7))
    return gzip.decompress(base64.b64decode(encoded)).decode('utf-8','replace')
def main():
    ap=argparse.ArgumentParser();ap.add_argument('--docs',default='docs');ap.add_argument('--report',default='ci/runtime-diagnostics.json');ap.add_argument('--rendered-dom');a=ap.parse_args();docs=Path(a.docs);source=decode_bundle(docs)
    index=(docs/'index.html').read_text()
    report={'htmlBytes':len(source.encode()),'webVersion':'7.1.8','hasTopbar':'class="topbar"' in source,'bootstrapInjectsBrandHome':'brandHomeBtn' in index,'bootstrapInjectsThemeToggle':'themeToggleBtn' in index,'bootstrapInjectsLogo':'atrangi-brand-logo.svg' in index,'hasHomePanel':'homePanel' in source,'hasDesktopNav':'desktopNav' in source,'hasMobileNav':'mobileNav' in source}
    if a.rendered_dom:
        dom=Path(a.rendered_dom).read_text(errors='replace');report['browserRuntime']={'renderedDomBytes':len(dom.encode()),'hasBrandHome':'id="brandHomeBtn"' in dom,'hasThemeToggle':'id="themeToggleBtn"' in dom,'hasLogo':'atrangi-brand-logo.svg' in dom,'brandingTestPass':'data-branding-test="PASS"' in dom,'stillHasPlaceholder':'<span>A</span>' in dom,'titleIs718':'Atrangi Document Workspace v7.1.8' in dom}
    path=Path(a.report);path.parent.mkdir(parents=True,exist_ok=True);path.write_text(json.dumps(report,indent=2)+'\n');print(json.dumps(report,indent=2))
if __name__=='__main__':main()
