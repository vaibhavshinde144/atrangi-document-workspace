(()=>{'use strict';
const VERSION='7.1.9',THEME_KEY='atrangi.theme';
const $=(s,r=document)=>r.querySelector(s);
const $$=(s,r=document)=>[...r.querySelectorAll(s)];
function home(){
 try{if(window.AtrangiScannerApp&&typeof window.AtrangiScannerApp.switchTab==='function')window.AtrangiScannerApp.switchTab('home');else{const nav=$('#desktopNav [data-tab="home"]')||$('#mobileNav [data-tab="home"]')||$('[data-tab="home"]');if(nav)nav.click();else{$$('.panel').forEach(p=>p.classList.remove('active-panel'));$('#homePanel')?.classList.add('active-panel')}}}catch(_){const nav=$('[data-tab="home"]');if(nav)nav.click()}
 const drawer=$('#navDrawer'),backdrop=$('#drawerBackdrop');if(drawer){drawer.hidden=true;drawer.setAttribute('aria-hidden','true')}if(backdrop)backdrop.hidden=true;document.body.style.overflow='';window.scrollTo(0,0)
}
function bindHome(el,label='Atrangi Document Workspace — go to Home'){if(!el||el.dataset.homeBound)return;el.dataset.homeBound='1';el.classList.add('brand-home-button');if(!/^(A|BUTTON)$/.test(el.tagName)){el.tabIndex=0;el.setAttribute('role','button')}el.setAttribute('aria-label',label);el.title='Go to Home';el.addEventListener('click',home);el.addEventListener('keydown',e=>{if(e.key==='Enter'||e.key===' '){e.preventDefault();home()}})}
function preferred(){const x=localStorage.getItem(THEME_KEY);return x==='dark'||x==='light'?x:(typeof matchMedia==='function'&&matchMedia('(prefers-color-scheme: dark)').matches?'dark':'light')}
function renderTheme(theme){const dark=theme==='dark';document.documentElement.dataset.theme=theme;document.documentElement.style.colorScheme=theme;document.body.classList.toggle('theme-dark',dark);document.body.classList.toggle('theme-light',!dark);const b=$('#themeToggleBtn');if(b){b.setAttribute('aria-pressed',String(dark));b.setAttribute('aria-label',dark?'Switch to Light Mode':'Switch to Dark Mode');b.title=dark?'Light Mode':'Dark Mode';b.innerHTML=`<span aria-hidden="true">${dark?'☀️':'🌙'}</span><span class="theme-toggle-label">${dark?'Light':'Dark'}</span>`}const meta=$('meta[name="theme-color"]');if(meta)meta.content=dark?'#07171f':'#0e3b4d'}
function toggle(){const next=document.documentElement.dataset.theme==='dark'?'light':'dark';localStorage.setItem(THEME_KEY,next);renderTheme(next)}
function syncVersion(){
 if(document.title!==`Atrangi Document Workspace v${VERSION}`)document.title=`Atrangi Document Workspace v${VERSION}`;
 document.documentElement.dataset.atrangiVersion=VERSION;
 const chip=$('.version-chip');if(chip&&chip.textContent!==`v${VERSION} All-in-One`)chip.textContent=`v${VERSION} All-in-One`;
 const ds=$('.drawer-head small');if(ds&&ds.textContent!==`v${VERSION} • All-in-One Document Platform`)ds.textContent=`v${VERSION} • All-in-One Document Platform`;
 $$('.hero-badge-row .eyebrow').forEach(e=>{if(/ATRANGI DOCUMENT WORKSPACE/i.test(e.textContent||''))e.textContent=`ATRANGI DOCUMENT WORKSPACE v${VERSION}`});
 $$('.section-heading .eyebrow').forEach(e=>{if(/^NEW IN/i.test(e.textContent||''))e.textContent=`NEW IN v${VERSION}`});
 document.querySelector('meta[name="description"]')?.setAttribute('content',`Atrangi Document Workspace v${VERSION} — professional all-in-one scanner, passport photo, universal file viewer/editor, OCR, PDF conversion, annotation, security and version workspace.`)
}
async function applyApprovedLogo(){try{const r=await fetch('atrangi-brand-logo.b64?v=719',{cache:'no-store'});if(!r.ok)return false;let b64=(await r.text()).trim().replace(/\s+/g,'');if(b64.startsWith('data:image')&&b64.includes(','))b64=b64.split(',',2)[1];if(!b64)return false;const uri='data:image/png;base64,'+b64;$$('.brand-logo img').forEach(img=>{img.src=uri;img.dataset.atrangiApprovedLogo='1'});document.documentElement.dataset.atrangiLogo='approved';return true}catch(_){return false}}
function verify(){const headerLogo=$('#brandHomeBtn img'),drawerLogo=$('.drawer-logo img'),theme=$('#themeToggleBtn'),homeTab=$('[data-tab="home"]');const ok=!!headerLogo&&!!drawerLogo&&!!theme&&!!$('#brandHomeBtn')&&!!$('#homePanel')&&!!homeTab;document.body.dataset.brandingTest=ok?'PASS':'FAIL';document.documentElement.dataset.atrangiBrandReady=String(ok);window.AtrangiBrandingV719={version:VERSION,home,toggle,verify};return ok}
async function functionalSelfTest(){
 const results=[];const sleep=ms=>new Promise(r=>setTimeout(r,ms));
 try{
  const b=$('#themeToggleBtn'),before=document.documentElement.dataset.theme||preferred();if(b){b.click();await sleep(40)}const after=document.documentElement.dataset.theme;results.push(!!b&&!!after&&after!==before&&localStorage.getItem(THEME_KEY)===after);
  const panels={home:'homePanel',tools:'toolsPanel',ocr:'ocrPanel',idPhoto:'idPhotoPanel',files:'filesPanel',settings:'settingsPanel'};
  for(const [tab,panel] of Object.entries(panels)){const nav=$(`#mobileNav [data-tab="${tab}"]`)||$(`#desktopNav [data-tab="${tab}"]`);if(nav){nav.click();await sleep(35)}results.push(!!nav&&!!$(`#${panel}`)?.classList.contains('active-panel'))}
  const tools=$('#mobileNav [data-tab="tools"]')||$('#desktopNav [data-tab="tools"]');tools?.click();await sleep(35);$('#brandHomeBtn')?.click();await sleep(35);results.push(!!$('#homePanel')?.classList.contains('active-panel'));
  const menu=$('#menuBtn');menu?.click();await sleep(35);const drawer=$('#navDrawer');results.push(!!drawer&&!drawer.hidden);$('#drawerCloseBtn')?.click();await sleep(35);results.push(!drawer||drawer.hidden||drawer.getAttribute('aria-hidden')==='true');
  if(document.documentElement.dataset.theme!==before)b?.click();home();
 }catch(_){results.push(false)}
 document.body.dataset.brandingFunctionalTest=results.length>0&&results.every(Boolean)?'PASS':'FAIL';
}
function init(){
 bindHome($('#brandHomeBtn'));
 bindHome($('.drawer-logo'),'Atrangi Riders logo — go to Home');
 bindHome($('.drawer-head > div:nth-child(2)'),'Atrangi Document Workspace — go to Home');
 const b=$('#themeToggleBtn');if(b&&!b.dataset.themeBound){b.dataset.themeBound='1';b.addEventListener('click',toggle)}
 renderTheme(preferred());syncVersion();verify();applyApprovedLogo().then(()=>verify());
 if(new URLSearchParams(location.search).get('selftest')==='1')setTimeout(functionalSelfTest,900)
}
if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',init,{once:true});else init();
for(const delay of [80,300,1000,2200])setTimeout(()=>{syncVersion();verify()},delay);
})();
