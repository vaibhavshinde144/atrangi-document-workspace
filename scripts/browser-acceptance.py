#!/usr/bin/env python3
import json,os,sys,tempfile,time
from pathlib import Path
from selenium import webdriver
from selenium.webdriver.chrome.options import Options
from selenium.webdriver.common.by import By
from selenium.webdriver.support.ui import WebDriverWait

if hasattr(sys.stdout,'reconfigure'):sys.stdout.reconfigure(encoding='utf-8',errors='replace')

URL=sys.argv[1] if len(sys.argv)>1 else 'http://127.0.0.1:8123/'
OUT=Path(os.environ.get('ATRANGI_TEST_REPORT','ci/browser-acceptance.json'));OUT.parent.mkdir(parents=True,exist_ok=True)
INSTALL_URL='https://vaibhavshinde144.github.io/atrangi-document-workspace/downloads/Atrangi-Document-Workspace.apk'
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
        record('branding/theme/navigation/share functional self-test',brand=='PASS',brand)
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
        expected_title='Atrangi Document Workspace v7.2.1'
        WebDriverWait(d,5).until(lambda x:x.title==expected_title)
        browser_title=d.title
        record('browser title v7.2.1',browser_title==expected_title,browser_title)
        version=d.execute_script("return document.documentElement.dataset.atrangiVersion||''")
        record('runtime version marker',version=='7.2.1',version)
        labels=d.execute_script("return {chip:document.querySelector('.version-chip')?.textContent||'',drawer:document.querySelector('.drawer-head small')?.textContent||'',hero:document.querySelector('.hero-badge-row .eyebrow')?.textContent||''}")
        record('visible version labels v7.2.1',all('7.2.1' in labels[k] for k in labels),json.dumps(labels))
        body=d.execute_script("return document.body.innerText||''")
        record('runtime has no loader/bootstrap text',not any(x in body for x in ['Loading Atrangi Document Workspace','const text=source.replace','Unable to load app:']),body[:500])
        direct=d.execute_script("return ![...document.scripts].some(s=>/DecompressionStream|document\\.write\\(text\\)/.test(s.textContent||''))")
        record('startup implementation is not inline in rendered workspace',direct)
        logos=d.execute_script("return [...document.querySelectorAll('.brand-logo img')].map(x=>({src:(x.getAttribute('src')||'').slice(0,80),w:x.naturalWidth,h:x.naturalHeight,approved:x.dataset.atrangiApprovedLogo||'',display:getComputedStyle(x).display}))")
        record('header and drawer logos load',len(logos)>=2 and all(x['w']>0 and x['h']>0 for x in logos),json.dumps(logos))
        approved=d.execute_script("return document.documentElement.dataset.atrangiLogo||''")
        record('exact supplied Atrangi Riders PNG logo applied',approved=='approved',approved)
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
        share_visible=d.execute_script("const b=document.getElementById('shareAppBtn');if(!b)return false;const r=b.getBoundingClientRect(),s=getComputedStyle(b);return r.width>0&&r.height>0&&s.display!=='none'&&s.visibility!=='hidden'")
        record('Share / Install App visible in hamburger menu',share_visible)
        opened_share=click(d,'#shareAppBtn')
        if opened_share:WebDriverWait(d,5).until(lambda x:x.execute_script("return !!document.getElementById('appShareDialog')?.open"))
        choices=d.execute_script("return {apps:!!document.getElementById('appShareToApps'),copy:!!document.getElementById('appCopyInstallLink'),download:document.querySelector('#appShareDialog a[download]')?.href||''}")
        record('Share App shows direct-share and copy-link choices',opened_share and choices['apps'] and choices['copy'] and choices['download']==INSTALL_URL,repr(choices))
        d.execute_script("Object.defineProperty(navigator,'share',{configurable:true,value:async p=>{window.__atrangiSharePayload=p;return true;}})")
        shared=click(d,'#appShareToApps')
        if shared:WebDriverWait(d,5).until(lambda x:x.execute_script("return !!window.__atrangiSharePayload"))
        payload=d.execute_script("return window.__atrangiSharePayload||null")
        record('Share App invokes platform sharing',shared and bool(payload),json.dumps(payload))
        record('Share App uses stable APK install URL',bool(payload) and payload.get('url')==INSTALL_URL,json.dumps(payload))
        closed_by_share=d.execute_script("const n=document.getElementById('navDrawer'),s=document.getElementById('appShareDialog');return (!n||n.hidden||n.getAttribute('aria-hidden')==='true')&&(!s||!s.open)")
        record('Share App closes hamburger menu',closed_by_share)
        click(d,'#menuBtn');WebDriverWait(d,5).until(lambda x:x.execute_script("return !document.getElementById('navDrawer').hidden"));click(d,'#shareAppBtn');WebDriverWait(d,5).until(lambda x:x.execute_script("return document.getElementById('appShareDialog').open"))
        d.execute_script("Object.defineProperty(navigator,'clipboard',{configurable:true,value:{writeText:async t=>{window.__atrangiCopied=t;}}})")
        copied=click(d,'#appCopyInstallLink')
        if copied:WebDriverWait(d,5).until(lambda x:x.execute_script("return !!window.__atrangiCopied"))
        record('Copy install link copies stable APK URL',copied and d.execute_script("return window.__atrangiCopied")==INSTALL_URL,d.execute_script("return window.__atrangiCopied||''"))
        opened2=click(d,'#menuBtn')
        if opened2:WebDriverWait(d,5).until(lambda x:x.execute_script("const n=document.getElementById('navDrawer');return n&&!n.hidden&&n.getAttribute('aria-hidden')!=='true'"))
        closed=click(d,'#drawerCloseBtn')
        if closed:WebDriverWait(d,5).until(lambda x:x.execute_script("const n=document.getElementById('navDrawer');return !n||n.hidden||n.getAttribute('aria-hidden')==='true'"))
        record('drawer closes',opened2 and closed)

        click(d,'#mobileNav [data-tab="home"],#desktopNav [data-tab="home"]')
        record('hero Files Workspace route',click(d,'#heroWorkspaceBtn') and WebDriverWait(d,5).until(lambda x:active(x,'filesPanel')))
        click(d,'#mobileNav [data-tab="home"],#desktopNav [data-tab="home"]');imp=click(d,'#heroImportBtn');time.sleep(.25);record('hero Import route',imp and active(d,'filesPanel'),'filesPanel active')
        f=Path(tempfile.gettempdir())/'atrangi-browser-acceptance.txt';f.write_text('Atrangi browser acceptance file\nOCR universal workspace test\n',encoding='utf-8')
        d.execute_script("const i=document.getElementById('universalImportInput');if(i){i.hidden=false;i.style.display='block'}");d.find_element(By.ID,'universalImportInput').send_keys(str(f))
        imported=WebDriverWait(d,12).until(lambda x:x.execute_script("return Number(document.getElementById('universalCount')?.textContent||0)>=1||document.querySelectorAll('#universalRepo .universal-file-card').length>=1"));record('universal text-file import',imported)
        handoff=d.execute_script("""const actions=['addPassword','removePassword','signPdf'],file=new File([new Uint8Array([37,80,68,70,45])],'External.pdf',{type:'application/pdf'}),out=[];for(const action of actions){window.AtrangiScannerApp.openToolWithFile(action,file);out.push({action,label:document.getElementById('toolFileLabel')?.textContent||'',choose:document.getElementById('toolChooseBtn')?.textContent||'',run:typeof document.getElementById('toolRunBtn')?.onclick==='function'});document.getElementById('toolDialog')?.close()}return out""")
        record('external PDF edit tools keep the open file preselected',len(handoff)==3 and all(x['label']=='External.pdf' and x['run'] and 'different PDF' in x['choose'] for x in handoff),json.dumps(handoff))
        click(d,'#mobileNav [data-tab="tools"],#desktopNav [data-tab="tools"]');s=d.find_element(By.ID,'toolSearch');s.clear();s.send_keys('PDF');time.sleep(.25);n=d.execute_script("return [...document.querySelectorAll('#toolGroups .tool-card-button')].filter(b=>!b.hidden&&getComputedStyle(b).display!=='none').length");record('tools search/filter',n>0,n)
        click(d,'#mobileNav [data-tab="home"],#desktopNav [data-tab="home"]');sc=click(d,'#heroScanBtn');time.sleep(.5);surface=d.execute_script("return ['scanStartSheet','cameraStage','documentBuilderDialog'].some(id=>{const e=document.getElementById(id);if(!e)return false;if(e.tagName==='DIALOG')return e.open;const s=getComputedStyle(e);return !e.hidden&&s.display!=='none'&&s.visibility!=='hidden'})");record('scan entry action',sc and surface,surface)
        errs=severe(d);record('no severe JavaScript console errors',len(errs)==0,'\n'.join(errs[:10]))
    except Exception as e:record('runtime suite completed',False,repr(e))
    finally:d.quit()

def passport_suite():
    d=driver(412,915)
    try:
        d.get(URL+('&' if '?' in URL else '?')+'acceptance=passport')
        wait(d,"document.getElementById('passportSimpleDialog')")
        d.execute_script("window.AtrangiScannerApp.switchTab('idPhoto')")
        record('simple passport start button opens five-step editor',click(d,'#passportNewWorkflowBtn') and WebDriverWait(d,5).until(lambda x:x.execute_script("return document.getElementById('passportSimpleDialog').open&&!document.getElementById('passportPhotoDialog').open")))
        capture=d.execute_script("const i=document.getElementById('psCameraInput');return !!i&&i.accept==='image/*'&&i.getAttribute('capture')==='user'")
        record('simple capture uses front-facing Android camera input',capture)
        d.set_script_timeout(30)
        upload=d.execute_async_script("""
          const done=arguments[0];
          fetch('atrangi-brand-logo.png?v=721').then(r=>{
            if(!r.ok)throw new Error(`fixture HTTP ${r.status}`);
            return r.blob();
          }).then(blob=>{
            const file=new File([blob],'passport-acceptance.png',{type:'image/png'});
            const transfer=new DataTransfer();transfer.items.add(file);
            const input=document.getElementById('psGalleryInput');input.files=transfer.files;
            input.dispatchEvent(new Event('change',{bubbles:true}));
            done({name:file.name,type:file.type,bytes:file.size});
          }).catch(error=>done({error:String(error)}));
        """)
        if upload.get('error') or upload.get('type')!='image/png' or upload.get('bytes',0)<1000:raise RuntimeError(f'passport fixture injection failed: {upload}')
        loaded=WebDriverWait(d,25).until(lambda x:x.execute_script("return document.querySelector('[data-ps-panel=\"2\"]')?.classList.contains('active')&&document.getElementById('psPreviewEmpty').hidden"))
        record('passport photo imports and advances to size',loaded)
        size_count=d.execute_script("return document.querySelectorAll('#psSizeGrid .ps-size-card').length")
        size_ok=size_count>=6
        for index in range(size_count):
            size_ok=size_ok and d.execute_script("const b=document.querySelectorAll('#psSizeGrid .ps-size-card')[arguments[0]];b.click();return b.classList.contains('active')",index)
        record('all common passport sizes select correctly',size_ok,size_count)
        d.execute_script("document.getElementById('psCustomW').value=40;document.getElementById('psCustomH').value=50;document.getElementById('psUseCustom').click()")
        custom=d.execute_script("const s=window.AtrangiScannerApp.state.idSize;return s.w===40&&s.h===50&&s.unit==='mm'")
        record('custom passport dimensions apply',custom)
        click(d,'#psNext')
        bg_count=d.execute_script("return document.querySelectorAll('#psSwatches .ps-swatch').length")
        bg_ok=bg_count>=7
        for index in range(bg_count):
            bg_ok=bg_ok and d.execute_script("const b=document.querySelectorAll('#psSwatches .ps-swatch')[arguments[0]];b.click();return document.querySelectorAll('#psSwatches .ps-swatch')[arguments[0]].classList.contains('active')",index)
        d.execute_script("const r=document.getElementById('psRemoveBg');r.checked=true;r.dispatchEvent(new Event('change',{bubbles:true}));const t=document.getElementById('psTolerance');t.value=55;t.dispatchEvent(new Event('input',{bubbles:true}));const f=document.getElementById('psFeather');f.value=18;f.dispatchEvent(new Event('input',{bubbles:true}))")
        bg_state=d.execute_script("const p=window.AtrangiScannerApp.state.passport;return p.removeBg&&p.bgTolerance===55&&p.feather===18")
        record('background removal, colours, strength and edge controls work',bg_ok and bg_state,f'{bg_count} backgrounds')
        click(d,'#psNext')
        d.execute_script("for(const [id,value] of [['psA_brightness',14],['psA_contrast',18],['psA_zoom',125],['psA_offsetY',-6]]){const e=document.getElementById(id);e.value=value;e.dispatchEvent(new Event('input',{bubbles:true}))}")
        adjusted=d.execute_script("const p=window.AtrangiScannerApp.state.passport;return p.brightness===14&&p.contrast===18&&Math.round(p.zoom*100)===125&&p.offsetY===-6")
        record('brightness, contrast, zoom and position controls work',adjusted)
        click(d,'#psResetAdjust')
        reset=d.execute_script("const p=window.AtrangiScannerApp.state.passport;return p.brightness===0&&p.contrast===0&&Math.round(p.zoom*100)===110")
        record('passport adjustment reset works',reset)
        click(d,'#psNext')
        men_count=d.execute_script("return document.querySelectorAll('#psOutfits .ps-outfit').length")
        men_ok=men_count>=10
        for index in range(men_count):
            men_ok=men_ok and d.execute_script("const b=document.querySelectorAll('#psOutfits .ps-outfit')[arguments[0]];b.click();return document.querySelectorAll('#psOutfits .ps-outfit')[arguments[0]].classList.contains('active')",index)
        click(d,'[data-ps-gender="women"]')
        women_count=d.execute_script("return document.querySelectorAll('#psOutfits .ps-outfit').length")
        women_ok=women_count>=10
        for index in range(women_count):
            women_ok=women_ok and d.execute_script("const b=document.querySelectorAll('#psOutfits .ps-outfit')[arguments[0]];b.click();return document.querySelectorAll('#psOutfits .ps-outfit')[arguments[0]].classList.contains('active')",index)
        record('all men and women formal outfit options apply',men_ok and women_ok,f'men={men_count}, women={women_count}')
        d.execute_script("for(const [id,value] of [['psD_dressScale',116],['psD_dressOffsetX',5],['psD_dressOffsetY',-8],['psD_dressRotate',2]]){const e=document.getElementById(id);e.value=value;e.dispatchEvent(new Event('input',{bubbles:true}))}")
        fit=d.execute_script("const p=window.AtrangiScannerApp.state.passport;return p.dressScale===116&&p.dressOffsetX===5&&p.dressOffsetY===-8&&p.dressRotate===2")
        record('outfit fit controls work',fit)
        saved=click(d,'#psSaveLibrary') and WebDriverWait(d,8).until(lambda x:x.execute_script("return window.AtrangiScannerApp.state.savedPassportPhotos.length>0"))
        record('finished passport photo saves to local library',saved)
        official=click(d,'#psOfficial') and WebDriverWait(d,5).until(lambda x:x.execute_script("return document.getElementById('passportPhotoDialog').open&&document.getElementById('passportComplianceMode').value==='official'"))
        record('strict official compliance is optional and opens only on request',official)
        errs=severe(d);record('passport workflow has no severe JavaScript console errors',len(errs)==0,'\n'.join(errs[:10]))
    except Exception as e:record('passport workflow suite completed',False,repr(e))
    finally:d.quit()

def scan_export_suite():
    d=driver(390,844)
    try:
        d.get(URL+('&' if '?' in URL else '?')+'acceptance=scan-export')
        wait(d,"document.getElementById('builderPages')")
        d.set_script_timeout(45)
        imported=d.execute_async_script("""
          const done=arguments[0],app=window.AtrangiScannerApp;
          (async()=>{
            const make=async(label,color)=>{
              const c=document.createElement('canvas');c.width=760;c.height=480;
              const x=c.getContext('2d');x.fillStyle='#e5e7e8';x.fillRect(0,0,c.width,c.height);
              x.fillStyle=color;x.fillRect(105,95,550,290);x.fillStyle='#fff';x.font='bold 54px Arial';x.fillText(label,260,250);
              const blob=await new Promise((resolve,reject)=>c.toBlob(b=>b?resolve(b):reject(new Error('encode failed')),'image/jpeg',.94));
              return new File([blob],label+'.jpg',{type:'image/jpeg'});
            };
            document.getElementById('autoReviewSetting').checked=false;
            app.state.builder=null;app.state.selectedProfile='idCard';
            app.state.scanOptions={idSides:2,idLayout:'onePage',idArrangement:'vertical',autoOcr:false,name:'Paired_ID'};
            const files=[await make('FRONT','#167da4'),await make('BACK','#8c552d')];
            const added=await app.addScannedFiles(files,{source:'gallery'});
            app.renderBuilder();
            done({added,pages:app.state.builder?.pages?.length||0});
          })().catch(error=>done({error:String(error)}));
        """)
        if imported.get('error'):raise RuntimeError(imported['error'])
        paired=d.execute_script("""const app=window.AtrangiScannerApp,b=app.state.builder,s=document.querySelector('.builder-id-sheet'),pair=s?.querySelector('.builder-pair-sides');return {pages:b?.pages?.length||0,sheets:document.querySelectorAll('.builder-id-sheet').length,sides:pair?.querySelectorAll('.builder-page').length||0,summary:document.getElementById('builderSummary')?.innerText||'',hint:document.getElementById('builderReviewHint')?.innerText||'',arrangement:pair?.className||''}""")
        paired_ok=paired['pages']==2 and paired['sheets']==1 and paired['sides']==2 and '1 output page' in paired['summary'] and '2 source photos' in paired['summary'] and 'vertical' in paired['arrangement']
        record('ID gallery import groups front and back into one visible output page',paired_ok,json.dumps(paired))

        sheet=d.execute_async_script("""
          const done=arguments[0],app=window.AtrangiScannerApp,b=app.state.builder;
          app.composeScanSheet(b.pages,{page:'a4',dpi:150,quality:'high',orientation:'portrait',layout:'fit',marginMm:6,gapMm:3,alignX:'center',alignY:'center',background:'#fff',arrangement:'vertical'},true)
            .then(c=>{const x=c.getContext('2d'),top=x.getImageData(Math.floor(c.width/2),Math.floor(c.height*.25),1,1).data,bottom=x.getImageData(Math.floor(c.width/2),Math.floor(c.height*.75),1,1).data;done({w:c.width,h:c.height,top:[...top],bottom:[...bottom]})})
            .catch(error=>done({error:String(error)}));
        """)
        if sheet.get('error'):raise RuntimeError(sheet['error'])
        record('paired ID compositor produces one portrait sheet with two slots',sheet['w']>800 and sheet['h']>sheet['w'] and sheet['top']!=sheet['bottom'],json.dumps(sheet))

        defaults=d.execute_script("""window.AtrangiScannerApp.openTool('scanExport',window.AtrangiScannerApp.state.builder);const value=id=>document.getElementById(id)?.value||'',text=id=>document.getElementById(id)?.innerText||'';return {page:value('scanExportPage'),orientation:value('scanExportOrientation'),layout:value('scanExportLayout'),margin:value('scanExportMargin'),tip:text('scanExportTip')}""")
        record('paired ID export defaults preserve both sides on A4',defaults['page']=='a4' and defaults['orientation']=='portrait' and defaults['layout']=='fit' and defaults['margin']=='6' and 'same output page' in defaults['tip'],json.dumps(defaults))
        d.execute_script("document.getElementById('toolDialog')?.close()")

        native=d.execute_async_script("""
          const done=arguments[0],chunks=[],meta={};
          window.AtrangiNative={
            beginFileExport:(name,mime,size)=>{Object.assign(meta,{name,mime,size});return 'native-test'},
            appendFileExportChunk:(id,data)=>{chunks.push(atob(data).length);return id==='native-test'},
            finishFileExport:id=>{meta.finished=id;return true},cancelFileExport:id=>{meta.cancelled=id}
          };
          const bytes=new Uint8Array(700000);for(let i=0;i<bytes.length;i++)bytes[i]=i%251;
          window.AtrangiTools.download(new Blob([bytes],{type:'application/pdf'}),'Paired_ID.pdf')
            .then(ok=>done({ok,meta,chunks,total:chunks.reduce((a,b)=>a+b,0)}))
            .catch(error=>done({error:String(error),meta,chunks}));
        """)
        native_ok=not native.get('error') and native['ok'] and native['meta']['name']=='Paired_ID.pdf' and native['meta']['mime']=='application/pdf' and native['meta']['size']==700000 and native['meta']['finished']=='native-test' and native['total']==700000 and len(native['chunks'])==3
        record('offline Android export bridge transfers complete files in bounded chunks',native_ok,json.dumps(native))

        profiles=d.execute_async_script("""
          const done=arguments[0],app=window.AtrangiScannerApp;
          (async()=>{const c=document.createElement('canvas');c.width=640;c.height=480;const x=c.getContext('2d');x.fillStyle='#fff';x.fillRect(0,0,640,480);x.fillStyle='#111';x.font='bold 42px Arial';x.fillText('ATRANGI SCAN',120,220);const blob=await new Promise(r=>c.toBlob(r,'image/jpeg',.92));const f=new File([blob],'scan.jpg',{type:'image/jpeg'}),out={};for(const mode of ['document','idCard','book','receipt']){const p=await app.createScanPage(f,mode);out[mode]={width:p.width,height:p.height,quality:p.quality?.score,src:(p.src||'').startsWith('data:image/')};}done(out)})().catch(error=>done({error:String(error)}));
        """)
        profiles_ok=not profiles.get('error') and all(profiles.get(k,{}).get('width',0)>0 and profiles.get(k,{}).get('height',0)>0 and profiles.get(k,{}).get('src') for k in ['document','idCard','book','receipt'])
        record('photo/gallery scan processing works for document, ID, book and receipt modes',profiles_ok,json.dumps(profiles))
        errs=severe(d);record('scan/export workflow has no severe JavaScript console errors',len(errs)==0,'\n'.join(errs[:10]))
    except Exception as e:record('scan/export workflow suite completed',False,repr(e))
    finally:d.quit()

def native_safe_area_suite():
    d=driver(390,844)
    try:
        d.get(URL+('&' if '?' in URL else '?')+'app=7.2.1&acceptance=native-safe-area')
        wait(d,"document.getElementById('app')")
        shell=d.execute_script("""const rect=e=>{const r=e.getBoundingClientRect();return {top:r.top,bottom:r.bottom,left:r.left,right:r.right,height:r.height}};return {viewport:{w:innerWidth,h:innerHeight},topbar:rect(document.querySelector('.topbar')),main:rect(document.querySelector('.main-content')),nav:rect(document.querySelector('.mobile-nav')),native:document.documentElement.classList.contains('atrangi-native-shell'),scrollWidth:document.documentElement.scrollWidth}""")
        click(d,'#heroScanBtn');WebDriverWait(d,5).until(lambda x:not x.execute_script("return document.getElementById('scanStartSheet').hidden"))
        sheet=d.execute_script("""const s=document.getElementById('scanStartSheet'),c=s.querySelector('.sheet-card'),r=c.getBoundingClientRect();return {top:r.top,bottom:r.bottom,maxHeight:getComputedStyle(c).maxHeight,paddingBottom:getComputedStyle(s).paddingBottom}""")
        aligned=shell['native'] and shell['topbar']['top']>=26 and abs(shell['main']['top']-shell['topbar']['bottom'])<=1 and abs(shell['nav']['bottom']-shell['viewport']['h'])<=1 and shell['scrollWidth']<=shell['viewport']['w']+2 and sheet['top']>=shell['topbar']['top'] and sheet['bottom']<=shell['viewport']['h']
        record('native Android shell applies each system safe area exactly once',aligned,json.dumps({'shell':shell,'scanSheet':sheet}))
    except Exception as e:record('native safe-area suite completed',False,repr(e))
    finally:d.quit()

def responsive_suite():
    for w,h,label in [(360,800,'small-mobile'),(390,844,'mobile'),(768,1024,'tablet'),(1440,900,'desktop')]:
        d=driver(w,h)
        try:
            d.get(URL+('&' if '?' in URL else '?')+f'acceptance={label}');wait(d,"document.getElementById('app')");wait(d,"document.body.dataset.brandingTest==='PASS'",16)
            m=d.execute_script("const de=document.documentElement,b=document.body,h=document.querySelector('.topbar'),l=document.querySelector('.topbar .brand-logo img'),t=document.getElementById('themeToggleBtn'),share=document.getElementById('shareAppBtn');const v=e=>!!e&&getComputedStyle(e).display!=='none'&&getComputedStyle(e).visibility!=='hidden'&&e.getBoundingClientRect().width>0;return {innerWidth,scrollWidth:Math.max(de.scrollWidth,b.scrollWidth),header:h?h.getBoundingClientRect():null,logo:v(l),theme:v(t),shareExists:!!share}")
            no_over=m['scrollWidth']<=m['innerWidth']+2;head=m['header'] and m['header']['left']>=-2 and m['header']['right']<=m['innerWidth']+2
            d.execute_script("window.AtrangiScannerApp.switchTab('idPhoto');document.getElementById('passportNewWorkflowBtn').click()")
            WebDriverWait(d,5).until(lambda x:x.execute_script("return document.getElementById('passportSimpleDialog').open"))
            passport_layout=d.execute_script("const d=document.getElementById('passportSimpleDialog'),c=d.querySelector('.ps-card'),b=d.querySelector('.ps-body'),r=d.getBoundingClientRect();return {open:d.open,left:r.left,right:r.right,viewport:innerWidth,cardScroll:c.scrollWidth,cardClient:c.clientWidth,bodyScroll:b.scrollWidth,bodyClient:b.clientWidth}")
            passport_fit=passport_layout['open'] and passport_layout['left']>=-2 and passport_layout['right']<=passport_layout['viewport']+2 and passport_layout['cardScroll']<=passport_layout['cardClient']+2 and passport_layout['bodyScroll']<=passport_layout['bodyClient']+2
            record(f'responsive {label}',no_over and head and m['logo'] and m['theme'] and m['shareExists'] and passport_fit,json.dumps({'shell':m,'passport':passport_layout}))
        except Exception as e:record(f'responsive {label}',False,repr(e))
        finally:d.quit()

core_selftest_suite();runtime_suite();passport_suite();scan_export_suite();native_safe_area_suite();responsive_suite()
OUT.write_text(json.dumps({'url':URL,'checks':checks,'failureCount':len(failures),'failures':failures},indent=2)+'\n',encoding='utf-8')
print(f'Acceptance checks: {len(checks)}, failures: {len(failures)}')
if failures:raise SystemExit(1)
