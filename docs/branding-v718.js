(()=>{'use strict';
const VERSION='7.1.8';
const RELEASE='718';
const THEME_KEY='atrangi.theme';
const LOGO=`atrangi-brand-logo.png?v=${RELEASE}`;
const $=(s,r=document)=>r.querySelector(s);
const $$=(s,r=document)=>[...r.querySelectorAll(s)];

function switchTab(name){
  try{
    if(window.AtrangiScannerApp&&typeof window.AtrangiScannerApp.switchTab==='function'){
      window.AtrangiScannerApp.switchTab(name);
      return true;
    }
  }catch(_){ }
  const nav=$(`#desktopNav [data-tab="${name}"]`)||$(`#mobileNav [data-tab="${name}"]`)||$(`[data-tab="${name}"]`);
  if(nav){nav.click();return true;}
  const panel=$(`#${name}Panel`);
  if(panel){$$('.panel').forEach(p=>p.classList.remove('active-panel'));panel.classList.add('active-panel');return true;}
  return false;
}
function closeDrawer(){
  const drawer=$('#navDrawer'),backdrop=$('#drawerBackdrop');
  if(drawer){drawer.hidden=true;drawer.setAttribute('aria-hidden','true');drawer.classList.remove('open','active','show')}
  if(backdrop){backdrop.hidden=true;backdrop.classList.remove('open','active','show')}
  document.body.style.overflow='';
}
function goHome(e){
  e?.preventDefault?.();
  switchTab('home');
  closeDrawer();
  try{window.scrollTo({top:0,behavior:'smooth'})}catch(_){window.scrollTo(0,0)}
}
function bindHome(el,label){
  if(!el||el.dataset.atrangiHomeBound==='1')return;
  el.dataset.atrangiHomeBound='1';
  el.classList.add('atrangi-home-link');
  if(!/^(A|BUTTON)$/.test(el.tagName)){el.tabIndex=0;el.setAttribute('role','link')}
  el.setAttribute('aria-label',label||'Go to Home');
  el.title='Go to Home';
  el.addEventListener('click',goHome);
  el.addEventListener('keydown',e=>{if(e.key==='Enter'||e.key===' '){e.preventDefault();goHome(e)}});
}
function installLogos(){
  const logos=$$('.brand-logo');
  for(const logo of logos){
    logo.classList.add('atrangi-logo-live');
    logo.innerHTML=`<img src="${LOGO}" alt="Atrangi Riders logo" class="atrangi-brand-image">`;
    bindHome(logo,'Atrangi Riders logo — go to Home');
  }
  bindHome($('.brand-block h1'),'Atrangi Document Workspace — go to Home');
  bindHome($('.drawer-head b'),'Atrangi Document Workspace — go to Home');
}
function preferredTheme(){
  const saved=localStorage.getItem(THEME_KEY);
  if(saved==='light'||saved==='dark')return saved;
  return typeof matchMedia==='function'&&matchMedia('(prefers-color-scheme: dark)').matches?'dark':'light';
}
function renderThemeButton(theme){
  const b=$('#themeToggleBtn');if(!b)return;
  const dark=theme==='dark';
  b.setAttribute('aria-pressed',String(dark));
  b.setAttribute('aria-label',dark?'Switch to Light Mode':'Switch to Dark Mode');
  b.title=dark?'Light Mode':'Dark Mode';
  b.innerHTML=`<span aria-hidden="true" class="theme-toggle-icon">${dark?'☀️':'🌙'}</span><span class="theme-toggle-label">${dark?'Light':'Dark'}</span>`;
}
function applyTheme(theme,persist=true){
  const value=theme==='dark'?'dark':'light';
  document.documentElement.dataset.theme=value;
  document.documentElement.style.colorScheme=value;
  document.body?.classList.toggle('theme-dark',value==='dark');
  document.body?.classList.toggle('theme-light',value==='light');
  if(persist)localStorage.setItem(THEME_KEY,value);
  const meta=$('meta[name="theme-color"]')||(()=>{const m=document.createElement('meta');m.name='theme-color';document.head.appendChild(m);return m})();
  meta.content=value==='dark'?'#07171f':'#0e3b4d';
  renderThemeButton(value);
  try{window.dispatchEvent(new CustomEvent('atrangi:themechange',{detail:{theme:value}}))}catch(_){ }
}
function installTheme(){
  const actions=$('.top-actions');
  if(actions&&!$('#themeToggleBtn')){
    const b=document.createElement('button');
    b.id='themeToggleBtn';b.type='button';b.className='icon-button theme-toggle-btn';
    b.addEventListener('click',()=>applyTheme(document.documentElement.dataset.theme==='dark'?'light':'dark'));
    actions.insertBefore(b,$('#moreBtn')||null);
  }
  applyTheme(preferredTheme(),false);
}
function updateVersion(){
  document.title=`Atrangi Document Workspace v${VERSION}`;
  document.documentElement.dataset.atrangiVersion=VERSION;
  const chip=$('.version-chip');if(chip)chip.textContent=`v${VERSION} All-in-One`;
  const drawerVersion=$('.drawer-head small');if(drawerVersion)drawerVersion.textContent=`v${VERSION} • All-in-One Document Platform`;
  const hero=$('.hero-badge-row .eyebrow');if(hero)hero.textContent=`ATRANGI DOCUMENT WORKSPACE v${VERSION}`;
}
function installFavicon(){
  let link=$('link[data-atrangi-favicon]');
  if(!link){link=document.createElement('link');link.rel='icon';link.type='image/png';link.dataset.atrangiFavicon='1';document.head.appendChild(link)}
  link.href=LOGO;
}
function verifyRuntime(){
  const images=$$('.brand-logo img.atrangi-brand-image');
  document.documentElement.dataset.atrangiBrandReady=String(images.length>=2&&!!$('#themeToggleBtn'));
}
function apply(){installLogos();installTheme();updateVersion();installFavicon();verifyRuntime()}
if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',apply,{once:true});else apply();
setTimeout(apply,100);
setTimeout(apply,750);
})();
