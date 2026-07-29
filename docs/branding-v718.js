(()=>{'use strict';
const VERSION='7.1.8',THEME_KEY='atrangi.theme';
const $=(s,r=document)=>r.querySelector(s);
const $$=(s,r=document)=>[...r.querySelectorAll(s)];
function home(){
 const nav=$('#desktopNav [data-tab="home"]')||$('#mobileNav [data-tab="home"]')||$('[data-tab="home"]');
 if(nav)nav.click();
 else { $$('.panel').forEach(p=>p.classList.remove('active-panel')); $('#homePanel')?.classList.add('active-panel'); }
 const drawer=$('#navDrawer'),backdrop=$('#drawerBackdrop');
 if(drawer){drawer.hidden=true;drawer.setAttribute('aria-hidden','true')}
 if(backdrop)backdrop.hidden=true;
 document.body.style.overflow=''; window.scrollTo(0,0);
}
function bindHome(el){if(!el||el.dataset.homeBound)return;el.dataset.homeBound='1';el.addEventListener('click',home);el.addEventListener('keydown',e=>{if(e.key==='Enter'||e.key===' '){e.preventDefault();home()}})}
function preferred(){const x=localStorage.getItem(THEME_KEY);return x==='dark'||x==='light'?x:(matchMedia?.('(prefers-color-scheme: dark)').matches?'dark':'light')}
function renderTheme(theme){
 const dark=theme==='dark'; document.documentElement.dataset.theme=theme; document.documentElement.style.colorScheme=theme;
 document.body.classList.toggle('theme-dark',dark);document.body.classList.toggle('theme-light',!dark);
 const b=$('#themeToggleBtn'); if(b){b.setAttribute('aria-pressed',String(dark));b.setAttribute('aria-label',dark?'Switch to Light Mode':'Switch to Dark Mode');b.title=dark?'Light Mode':'Dark Mode';b.innerHTML=`<span aria-hidden="true">${dark?'☀️':'🌙'}</span><span class="theme-toggle-label">${dark?'Light':'Dark'}</span>`}
 const meta=$('meta[name="theme-color"]');if(meta)meta.content=dark?'#07171f':'#0e3b4d';
}
function toggle(){const next=document.documentElement.dataset.theme==='dark'?'light':'dark';localStorage.setItem(THEME_KEY,next);renderTheme(next)}
function verify(){
 const logo=$('#brandHomeBtn img[src*="atrangi-brand-logo.svg"]');
 const drawerLogo=$('.drawer-logo img[src*="atrangi-brand-logo.svg"]');
 const theme=$('#themeToggleBtn');
 const ok=!!logo&&!!drawerLogo&&!!theme&&!!$('#brandHomeBtn')&&!!$('#homePanel')&&!!($('[data-tab="home"]'));
 document.body.dataset.brandingTest=ok?'PASS':'FAIL';
 window.AtrangiBrandingV718={version:VERSION,home,toggle,verify:()=>ok};
}
function init(){
 bindHome($('#brandHomeBtn'));bindHome($('#drawerBrandHomeBtn'));
 const b=$('#themeToggleBtn');if(b&&!b.dataset.themeBound){b.dataset.themeBound='1';b.addEventListener('click',toggle)}
 renderTheme(preferred());
 document.title=`Atrangi Document Workspace v${VERSION}`;
 verify();
}
if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',init,{once:true});else init();
})();
