(()=>{'use strict';
const VERSION='7.1.8',THEME_KEY='atrangi.theme';
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
function verify(){const headerLogo=$('#brandHomeBtn img[src*="atrangi-brand-logo.svg"]'),drawerLogo=$('.drawer-logo img[src*="atrangi-brand-logo.svg"]'),theme=$('#themeToggleBtn'),homeTab=$('[data-tab="home"]');const ok=!!headerLogo&&!!drawerLogo&&!!theme&&!!$('#brandHomeBtn')&&!!$('#homePanel')&&!!homeTab;document.body.dataset.brandingTest=ok?'PASS':'FAIL';document.documentElement.dataset.atrangiBrandReady=String(ok);window.AtrangiBrandingV718={version:VERSION,home,toggle,verify};return ok}
function init(){
 bindHome($('#brandHomeBtn'));
 bindHome($('.drawer-logo'),'Atrangi Riders logo — go to Home');
 bindHome($('.drawer-head > div:nth-child(2)'),'Atrangi Document Workspace — go to Home');
 const b=$('#themeToggleBtn');if(b&&!b.dataset.themeBound){b.dataset.themeBound='1';b.addEventListener('click',toggle)}
 renderTheme(preferred());syncVersion();verify()
}
if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',init,{once:true});else init();
for(const delay of [80,300,1000,2200])setTimeout(()=>{syncVersion();verify()},delay);
})();
