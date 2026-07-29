#!/usr/bin/env python3
import json,os,sys,tempfile,time
from pathlib import Path
from selenium import webdriver
from selenium.webdriver.chrome.options import Options
from selenium.webdriver.common.by import By
from selenium.webdriver.support.ui import WebDriverWait

URL=sys.argv[1] if len(sys.argv)>1 else 'http://127.0.0.1:8123/'
OUT=Path(os.environ.get('ATRANGI_TEST_REPORT','ci/browser-acceptance.json'));OUT.parent.mkdir(parents=True,exist_ok=True)
checks=[];failures=[]
def record(name,ok,detail=''):
    item={'name':name,'ok':bool(ok),'detail':str(detail)[:1600]};checks.append(item)
    if not ok:failures.append(f'{name}: {detail}')
    print(('PASS' if ok else 'FAIL'),name,detail)
def driver(width=390,height=844):
    o=Options()
    for a in ['--headless=new','--no-sandbox','--disable-gpu','--disable-dev-shm-usage','--disable-background-networking']:o.add_argument(a)
    o.add_argument(f'--window-size={width},{height}');o.set_capability('goog:loggingPrefs',{'browser':'ALL'})
    d=webdriver.Chrome(options=o);d.set_window_size(width,height);return d
def wait(d,expr,seconds=18):return WebDriverWait(d,seconds).until(lambda x:x.execute_script(f'return Boolean({expr})'))
def active(d,panel):return d.execute_script("const p=document.getElementById(arguments[0]);return !!p&&p.classList.contains('active-panel')",panel)
def click(d,selector):return d.execute_script("const e=document.querySelector(arguments[0]);if(!e)return false;e.click();return true",selector)
def severe(d):
    out=[]
    try:
        for row in d.get_log('browser'):
            msg=row.get('message','')
            if row.get('level')=='SEVERE' and not any(x in msg for x in ['favicon.ico','ERR_BLOCKED_BY_CLIENT']):out.append(msg)
    except Exception:pass
    return out

def core_selftest_suite():
    d=driver()
    try:
        d.get(URL+('&' if '?' in URL else '?')+'selftest=1&acceptance=core')
        wait(d,"document.getElementById('app')")
        wait(d,"document.body && document.body.dataset.brandingTest==='PASS'",12)
        wait(d,"document.body && document.body.dataset.selfTest",18)
        wait(d,"document.body && document.body.dataset.brandingFunctionalTest",18)
        core=d.execute_script("return document.body.dataset.selfTest||''")
        brand=d.execute_script("return document.body.dataset.brandingFunctionalTest||''")
        record('built-in scanner core self-test',core=='PASS',core)
        record('branding/theme/navigation functional self-test',brand=='PASS',brand)
        body=d.execute_script("return document.body.innerText||''")
        leaked=any(x in body for x in ['const text=source.replace','DecompressionStream','Unable to load app:','hardCss=document.createElement'])
        record('no bootstrap JavaScript rendered as page text',not leaked,body[:500] if leaked else 'clean')
        errs=severe(d);record('core self-test has no severe JavaScript console errors',len(errs)==0,'\n'.join(errs[:10]))
    except Exception as e:record('core self-test suite completed',False,repr(e))
    finally:d.quit()

def runtime_suite():
    d=driver()
    try:
        d.get(URL+('&' if '?' in URL else '?')+'acceptance=runtime')
        wait(d,"document.getElementById('app')")
        wait(d,"document.body && document.body.dataset.brandingTest==='PASS'",16)
        record('runtime app and branding initialized',True)
        record('browser title v7.1.9',d.title=='Atrangi Document Workspace v7.1.9',d.title)
        version=d.execute_script("return document.documentElement.dataset.atrangiVersion||''")
        record('runtime version marker',version=='7.1.9',version)
        labels=d.execute_script("return {chip:document.querySelector('.version-chip')?.textContent||'',drawer:document.querySelector('.drawer-head small')?.textContent||'',hero:document.querySelector('.hero-badge-row .eyebrow')?.textContent||''}")
        record('visible version labels v7.1.9',all('7.1.9' in labels[k] for k in labels),json.dumps(labels))
        body=d.execute_script("return document.body.innerText||''")
        record('runtime has no loader/bootstrap text',not any(x in body for x in ['Loading Atrangi Document Workspace','const text=source.replace','Unable to load app:']),body[:500])
        direct=d.execute_script("return ![...document.scripts].some(s=>/DecompressionStream|document\\.write\\(text\\)/.test(s.textContent||''))")
        record('startup implementation is not inline in rendered workspace',direct)
        logos=d.execute_script("return [...document.querySelectorAll('.brand-logo img')].map(x=>({src:(x.getAttribute('src')||'').slice(0,80),w:x.naturalWidth,h:x.naturalHeight,approved:x.dataset.atrangiApprovedLogo||'',display:getComputedStyle(x).display}))")
        record('header and drawer logos load',len(logos)>=2 and all(x['w']>0 and x['h']>0 for x in logos),json.dumps(logos))
        approved=d.execute_script("return document.documentElement.dataset.atrangiLogo||''")
        record('approved Atrangi Riders PNG logo applied',approved=='approved',approved)
        placeholder=d.execute_script("return [...document.querySelectorAll('.brand-logo')].some(x=>x.textContent.trim()==='A')")
        record('old A placeholder removed',not placeholder,placeholder)
        visible=d.execute_script("const b=document.getElementById('themeToggleBtn');if(!b)return false;const s=getComputedStyle(b),r=b.getBoundingClientRect();return s.display!=='none'&&s.visibility!=='hidden'&&r.width>0&&r.height>0")
        record('theme control visible on mobile',visible)
        before=d.execute_script("return document.documentElement.dataset.theme");click(d,'#themeToggleBtn');WebDriverWait(d,5).until(lambda x:x.execute_script("return document.documentElement.dataset.theme")!=before)
        after=d.execute_script("return document.documentElement.dataset.theme");saved=d.execute_script("return localStorage.getItem('atrangi.theme')")
        record('theme toggles and persists',after in ('light','dark') and after!=before and saved==after,f'{before}->{after}; saved={saved}')
        nav={'home':'homePanel','tools':'toolsPanel','ocr':'ocrPanel','idPhoto':'idPhotoPanel','files':'filesPanel','settings':'settingsPanel'}
        for tab,panel in nav.items():
            ok=d.execute_script("const t=arguments[0];const b=document.querySelector('#mobileNav [data-tab=\"'+t+'\"]')||document.querySelector('#desktopNav [data-tab=\"'+t+'\"]');if(!b)return false;b.click();return true",tab)
            if ok:ok=WebDriverWait(d,5).until(lambda x,p=panel:active(x,p))
            record(f'navigation {tab}',ok,panel)
        click(d,'#mobileNav [data-tab="tools"],#desktopNav [data-tab="tools"]');WebDriverWait(d,5).until(lambda x:active(x,'toolsPanel'))
        record('header brand returns Home',click(d,'#brandHomeBtn') and WebDriverWait(d,5).until(lambda x:active(x,'homePanel')))
        click(d,'#mobileNav [data-tab="ocr"],#desktopNav [data-tab="ocr"]');WebDriverWait(d,5).until(lambda x:active(x,'ocrPanel'))
        record('header logo returns Home',click(d,'.topbar .brand-logo') and WebDriverWait(d,5).until(lambda x:active(x,'homePanel')))
        click(d,'#mobileNav [data-tab="tools"],#desktopNav [data-tab="tools"]');WebDriverWait(d,5).until(lambda x:active(x,'toolsPanel'))
        drawer_logo_bound=click(d,'.drawer-logo')
        if drawer_logo_bound:WebDriverWait(d,5).until(lambda x:active(x,'homePanel'))
        record('drawer logo returns Home',drawer_logo_bound and active(d,'homePanel'))
        opened=click(d,'#menuBtn')
        if opened:WebDriverWait(d,5).until(lambda x:x.execute_script("const n=document.getElementById('navDrawer');return n&&!n.hidden&&n.getAttribute('aria-hidden')!=='true'"))
        record('drawer opens',opened)
        closed=click(d,'#drawerCloseBtn')
        if closed:WebDriverWait(d,5).until(lambda x:x.execute_script("const n=document.getElementById('navDrawer');return !n||n.hidden||n.getAttribute('aria-hidden')==='true'"))
        record('drawer closes',closed)
        click(d,'#mobileNav [data-tab="home"],#desktopNav [data-tab="home"]')
        record('hero Files Workspace route',click(d,'#heroWorkspaceBtn') and WebDriverWait(d,5).until(lambda x:active(x,'filesPanel')))
        click(d,'#mobileNav [data-tab="home"],#desktopNav [data-tab="home"]');imp=click(d,'#heroImportBtn');time.sleep(.25);record('hero Import route',imp and active(d,'filesPanel'),'filesPanel active')
        f=Path(tempfile.gettempdir())/'atrangi-browser-acceptance.txt';f.write_text('Atrangi browser acceptance file\nOCR universal workspace test\n',encoding='utf-8')
        d.execute_script("const i=document.getElementById('universalImportInput');if(i){i.hidden=false;i.style.display='block'}");d.find_element(By.ID,'universalImportInput').send_keys(str(f))
        imported=WebDriverWait(d,12).until(lambda x:x.execute_script("return Number(document.getElementById('universalCount')?.textContent||0)>=1||document.querySelectorAll('#universalRepo .universal-file-card').length>=1"));record('universal text-file import',imported)
        click(d,'#mobileNav [data-tab="tools"],#desktopNav [data-tab="tools"]');s=d.find_element(By.ID,'toolSearch');s.clear();s.send_keys('PDF');time.sleep(.25);n=d.execute_script("return [...document.querySelectorAll('#toolGroups .tool-card-button')].filter(b=>!b.hidden&&getComputedStyle(b).display!=='none').length");record('tools search/filter',n>0,n)
        click(d,'#mobileNav [data-tab="home"],#desktopNav [data-tab="home"]');sc=click(d,'#heroScanBtn');time.sleep(.5);surface=d.execute_script("return ['scanStartSheet','cameraStage','documentBuilderDialog'].some(id=>{const e=document.getElementById(id);if(!e)return false;if(e.tagName==='DIALOG')return e.open;const s=getComputedStyle(e);return !e.hidden&&s.display!=='none'&&s.visibility!=='hidden'})");record('scan entry action',sc and surface,surface)
        errs=severe(d);record('no severe JavaScript console errors',len(errs)==0,'\n'.join(errs[:10]))
    except Exception as e:record('runtime suite completed',False,repr(e))
    finally:d.quit()

def responsive_suite():
    for w,h,label in [(360,800,'small-mobile'),(390,844,'mobile'),(768,1024,'tablet'),(1440,900,'desktop')]:
        d=driver(w,h)
        try:
            d.get(URL+('&' if '?' in URL else '?')+f'acceptance={label}');wait(d,"document.getElementById('app')");wait(d,"document.body.dataset.brandingTest==='PASS'",16)
            m=d.execute_script("const de=document.documentElement,b=document.body,h=document.querySelector('.topbar'),l=document.querySelector('.topbar .brand-logo img'),t=document.getElementById('themeToggleBtn');const v=e=>!!e&&getComputedStyle(e).display!=='none'&&getComputedStyle(e).visibility!=='hidden'&&e.getBoundingClientRect().width>0;return {innerWidth,scrollWidth:Math.max(de.scrollWidth,b.scrollWidth),header:h?h.getBoundingClientRect():null,logo:v(l),theme:v(t)}")
            no_over=m['scrollWidth']<=m['innerWidth']+2;head=m['header'] and m['header']['left']>=-2 and m['header']['right']<=m['innerWidth']+2
            record(f'responsive {label}',no_over and head and m['logo'] and m['theme'],json.dumps(m))
        except Exception as e:record(f'responsive {label}',False,repr(e))
        finally:d.quit()

core_selftest_suite();runtime_suite();responsive_suite()
OUT.write_text(json.dumps({'url':URL,'checks':checks,'failureCount':len(failures),'failures':failures},indent=2)+'\n',encoding='utf-8')
print(f'Acceptance checks: {len(checks)}, failures: {len(failures)}')
if failures:raise SystemExit(1)
