(()=>{'use strict';
const VERSION='7.1.6';
const THEME_KEY='atrangi.theme';
const $=(s,r=document)=>r.querySelector(s);
const $$=(s,r=document)=>[...r.querySelectorAll(s)];

function goHome(){
  const nav=$('#desktopNav [data-tab="home"]')||$('#mobileNav [data-tab="home"]');
  if(nav){nav.click();}
  else{
    $$('.panel').forEach(p=>p.classList.remove('active-panel'));
    $('#homePanel')?.classList.add('active-panel');
  }
  const drawer=$('#navDrawer'),backdrop=$('#drawerBackdrop');
  if(drawer){drawer.hidden=true;drawer.setAttribute('aria-hidden','true')}
  if(backdrop)backdrop.hidden=true;
  document.body.style.overflow='';
  window.scrollTo({top:0,behavior:'smooth'});
}

function installBrandLogo(){
  $$('.brand-logo').forEach(logo=>{
    if(!logo.classList.contains('atrangi-logo-live')){
      logo.innerHTML='<img src="atrangi-brand-logo.png?v=716" alt="Atrangi Riders logo" class="atrangi-brand-image">';
      logo.classList.add('atrangi-logo-live');
    }
  });
  const brand=$('.brand-block');
  if(brand&&!brand.dataset.homeBound){
    brand.dataset.homeBound='1';
    brand.classList.add('brand-home-link');
    brand.tabIndex=0;
    brand.setAttribute('role','link');
    brand.setAttribute('aria-label','Atrangi Document Workspace — go to Home');
    brand.title='Go to Home';
    brand.addEventListener('click',goHome);
    brand.addEventListener('keydown',e=>{if(e.key==='Enter'||e.key===' '){e.preventDefault();goHome()}});
  }
  const drawerHead=$('.drawer-head');
  if(drawerHead&&!drawerHead.dataset.homeBound){
    drawerHead.dataset.homeBound='1';
    const drawerLogo=$('.brand-logo',drawerHead),drawerName=drawerHead.querySelector(':scope > div:nth-child(2)');
    for(const el of [drawerLogo,drawerName])if(el){el.classList.add('brand-home-link');el.tabIndex=0;el.setAttribute('role','link');el.title='Go to Home';el.addEventListener('click',goHome);el.addEventListener('keydown',e=>{if(e.key==='Enter'||e.key===' '){e.preventDefault();goHome()}})}
  }
  if(!$('link[data-atrangi-favicon]')){
    const link=document.createElement('link');
    link.rel='icon';link.type='image/png';link.href='atrangi-brand-logo.png?v=716';link.dataset.atrangiFavicon='1';
    document.head.appendChild(link);
  }
}

function preferredTheme(){
  const saved=localStorage.getItem(THEME_KEY);
  if(saved==='dark'||saved==='light')return saved;
  return typeof window.matchMedia==='function'&&window.matchMedia('(prefers-color-scheme: dark)').matches?'dark':'light';
}
function updateThemeButton(theme){
  const b=$('#themeToggleBtn'); if(!b)return;
  const dark=theme==='dark';
  b.setAttribute('aria-pressed',String(dark));
  b.setAttribute('aria-label',dark?'Switch to Light Mode':'Switch to Dark Mode');
  b.title=dark?'Light Mode':'Dark Mode';
  b.innerHTML=`<span class="theme-toggle-icon" aria-hidden="true">${dark?'☀️':'🌙'}</span><span class="theme-toggle-label">${dark?'Light':'Dark'}</span>`;
}
function applyTheme(theme,persist=true){
  const value=theme==='dark'?'dark':'light';
  document.documentElement.dataset.theme=value;
  document.body?.classList.toggle('theme-dark',value==='dark');
  document.body?.classList.toggle('theme-light',value==='light');
  document.documentElement.style.colorScheme=value;
  if(persist)localStorage.setItem(THEME_KEY,value);
  const meta=$('meta[name="theme-color"]')||(()=>{const m=document.createElement('meta');m.name='theme-color';document.head.appendChild(m);return m})();
  meta.content=value==='dark'?'#07171f':'#0e3b4d';
  updateThemeButton(value);
  window.dispatchEvent(new CustomEvent('atrangi:themechange',{detail:{theme:value}}));
}
function installThemeToggle(){
  const top=$('.top-actions');
  if(top&&!$('#themeToggleBtn')){
    const b=document.createElement('button');
    b.id='themeToggleBtn';b.type='button';b.className='icon-button theme-toggle-btn';
    b.addEventListener('click',()=>applyTheme(document.documentElement.dataset.theme==='dark'?'light':'dark'));
    top.insertBefore(b,top.lastElementChild||null);
  }
  updateThemeButton(document.documentElement.dataset.theme||preferredTheme());
}
function brandVersion(){
  document.title=`Atrangi Document Workspace v${VERSION}`;
  const chip=$('.version-chip');if(chip)chip.textContent=`v${VERSION} All-in-One`;
  const ds=$('.drawer-head small');if(ds)ds.textContent=`v${VERSION} • All-in-One Document Platform`;
  document.documentElement.dataset.atrangiVersion=VERSION;
}
function apply(){
  installBrandLogo();
  applyTheme(preferredTheme(),false);
  installThemeToggle();
  brandVersion();
}
if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',apply,{once:true});else apply();
let queued=false;
new MutationObserver(()=>{if(queued)return;queued=true;queueMicrotask(()=>{queued=false;installBrandLogo();installThemeToggle();brandVersion()})}).observe(document.documentElement,{subtree:true,childList:true});
})();
