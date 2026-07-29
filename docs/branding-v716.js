(()=>{'use strict';
const VERSION='7.1.7';
const RELEASE='717';
const THEME_KEY='atrangi.theme';
const LOGO_SOURCE='atrangi-brand-logo.b64';
const $=(s,r=document)=>r.querySelector(s);
const $$=(s,r=document)=>[...r.querySelectorAll(s)];
let logoDataPromise=null;

function logoData(){
  if(!logoDataPromise){
    logoDataPromise=fetch(LOGO_SOURCE+'?v='+RELEASE,{cache:'no-store'})
      .then(r=>{if(!r.ok)throw new Error('logo '+r.status);return r.text()})
      .then(t=>'data:image/png;base64,'+t.replace(/\s+/g,''))
      .catch(()=> 'atrangi-brand-logo.png?v='+RELEASE);
  }
  return logoDataPromise;
}

function findHomeNav(){
  const selectors=['#desktopNav [data-tab="home"]','#mobileNav [data-tab="home"]','[data-tab="home"]','[data-panel="home"]','[data-page="home"]','[data-route="home"]','a[href="#home"]','button[aria-label="Home"]'];
  for(const s of selectors){const el=$(s);if(el)return el;}
  return $$('button,a,[role="tab"],[role="button"]').find(el=>/^home$/i.test((el.textContent||'').trim()));
}
function goHome(e){
  e?.preventDefault?.();
  const nav=findHomeNav();
  if(nav&&nav!==e?.currentTarget){nav.click();}
  else{
    const home=$('#homePanel');
    if(home){$$('.panel').forEach(p=>p.classList.remove('active-panel'));home.classList.add('active-panel');}
    try{history.replaceState(history.state,'',location.pathname+location.search+'#home')}catch(_){ }
  }
  const drawer=$('#navDrawer,.nav-drawer,.drawer'),backdrop=$('#drawerBackdrop,.drawer-backdrop');
  if(drawer){drawer.hidden=true;drawer.setAttribute('aria-hidden','true');drawer.classList.remove('open','active','show')}
  if(backdrop){backdrop.hidden=true;backdrop.classList.remove('open','active','show')}
  document.body.style.overflow='';
  try{window.scrollTo({top:0,behavior:'smooth'})}catch(_){window.scrollTo(0,0)}
}

function bindHome(el,label){
  if(!el||el.dataset.atrangiHomeBound==='1')return;
  el.dataset.atrangiHomeBound='1';
  el.classList.add('brand-home-link');
  if(!/^(A|BUTTON)$/.test(el.tagName)){
    el.tabIndex=0;el.setAttribute('role','link');
  }
  el.setAttribute('aria-label',label||'Atrangi Document Workspace — go to Home');
  el.title='Go to Home';
  el.addEventListener('click',goHome);
  el.addEventListener('keydown',e=>{if(e.key==='Enter'||e.key===' '){e.preventDefault();goHome(e)}});
}

function titleCandidates(){
  const known=['.brand-title','.brand-name','.app-title','.app-name','.title','.brand-block h1','.brand-block strong','.drawer-head strong','.drawer-head b','header h1','header h2'];
  const found=[];
  for(const s of known)found.push(...$$(s));
  found.push(...$$('h1,h2,h3,strong,b,span,div').filter(el=>{
    const text=(el.textContent||'').trim();
    return text.length>0&&text.length<=100&&/Atrangi\s+Document\s+Workspace/i.test(text);
  }));
  return [...new Set(found)].filter(el=>{
    const text=(el.textContent||'').trim();
    return text.length<=120&&/Atrangi\s+Document\s+Workspace/i.test(text);
  });
}
function headerHost(title){
  return $('.brand-block')||title?.closest('header,.app-header,.top-header,.topbar,.top-bar,.toolbar,.app-bar,[class*="header"],[class*="topbar"],[class*="toolbar"]')||title?.parentElement||$('header')||$('.top-actions')?.parentElement||document.body;
}

async function setLogoImage(img){
  if(!img)return;
  img.alt='Atrangi Riders logo';
  img.classList.add('atrangi-brand-image');
  const src=await logoData();
  if(img.src!==src)img.src=src;
}
function ensureLogoButton(host,title){
  let button=$('#atrangiHomeLogo');
  const existing=$('.brand-logo');
  if(existing){
    existing.classList.add('atrangi-logo-live');
    let img=$('img',existing);
    if(!img){existing.innerHTML='<img alt="Atrangi Riders logo" class="atrangi-brand-image">';img=$('img',existing)}
    setLogoImage(img);
    bindHome(existing,'Atrangi Riders logo — go to Home');
    return existing;
  }
  if(!button){
    button=document.createElement('button');
    button.id='atrangiHomeLogo';button.type='button';button.className='atrangi-v717-logo-button';
    button.innerHTML='<img alt="Atrangi Riders logo" class="atrangi-brand-image">';
    bindHome(button,'Atrangi Riders logo — go to Home');
    if(title?.parentElement)title.parentElement.insertBefore(button,title);
    else host?.insertBefore(button,host.firstChild||null);
  }
  setLogoImage($('img',button));
  return button;
}
function ensureFallbackBrand(host){
  if($('#atrangiFallbackBrand'))return;
  const wrap=document.createElement('div');
  wrap.id='atrangiFallbackBrand';wrap.className='atrangi-v717-fallback-brand';
  if(host&&host!==document.body)wrap.classList.add('atrangi-v717-inline-brand');
  wrap.innerHTML='<button id="atrangiFallbackHome" type="button" class="atrangi-v717-home"><img alt="Atrangi Riders logo" class="atrangi-brand-image"><span>Atrangi Document Workspace</span></button>';
  (host||document.body).insertBefore(wrap,(host||document.body).firstChild||null);
  const home=$('#atrangiFallbackHome');bindHome(home,'Atrangi Document Workspace — go to Home');setLogoImage($('img',home));
}
function installBrand(){
  const titles=titleCandidates();
  const title=titles[0]||null;
  const host=headerHost(title);
  if(host&&host!==document.body)host.classList.add('atrangi-safe-header');
  titles.forEach(t=>bindHome(t,'Atrangi Document Workspace — go to Home'));
  const hasNativeBrand=Boolean(title||$('.brand-block')||$('.brand-logo'));
  if(hasNativeBrand)ensureLogoButton(host,title);else ensureFallbackBrand(host);
  const drawerHead=$('.drawer-head');
  if(drawerHead){
    const drawerTitle=titleCandidates().find(t=>drawerHead.contains(t));
    bindHome(drawerTitle,'Atrangi Document Workspace — go to Home');
    const drawerLogo=$('.brand-logo',drawerHead);if(drawerLogo)bindHome(drawerLogo,'Atrangi Riders logo — go to Home');
  }
}

function preferredTheme(){
  const saved=localStorage.getItem(THEME_KEY);
  if(saved==='dark'||saved==='light')return saved;
  return typeof matchMedia==='function'&&matchMedia('(prefers-color-scheme: dark)').matches?'dark':'light';
}
function updateThemeButton(theme){
  const dark=theme==='dark';
  $$('#themeToggleBtn,#atrangiFloatingTheme').forEach(b=>{
    b.setAttribute('aria-pressed',String(dark));
    b.setAttribute('aria-label',dark?'Switch to Light Mode':'Switch to Dark Mode');
    b.title=dark?'Light Mode':'Dark Mode';
    b.innerHTML=`<span class="theme-toggle-icon" aria-hidden="true">${dark?'☀️':'🌙'}</span><span class="theme-toggle-label">${dark?'Light':'Dark'}</span>`;
  });
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
function themeClick(){applyTheme(document.documentElement.dataset.theme==='dark'?'light':'dark')}
function installThemeToggle(){
  let b=$('#themeToggleBtn');
  const title=titleCandidates()[0]||null;
  const host=$('.top-actions')||title?.closest('header,.app-header,.top-header,.topbar,.top-bar,.toolbar,.app-bar,[class*="header"],[class*="topbar"],[class*="toolbar"]')||$('header');
  if(!b&&host){
    b=document.createElement('button');b.id='themeToggleBtn';b.type='button';b.className='icon-button theme-toggle-btn';b.addEventListener('click',themeClick);
    const actionArea=$('.top-actions',host)||$('.header-actions',host)||$('.actions',host);
    if(actionArea)actionArea.insertBefore(b,actionArea.firstChild||null);else host.appendChild(b);
  }
  if(!b&&!$('#atrangiFloatingTheme')){
    b=document.createElement('button');b.id='atrangiFloatingTheme';b.type='button';b.className='theme-toggle-btn atrangi-floating-theme';b.addEventListener('click',themeClick);document.body.appendChild(b);
  }
  updateThemeButton(document.documentElement.dataset.theme||preferredTheme());
}

function installFavicon(){
  if($('link[data-atrangi-favicon]'))return;
  const link=document.createElement('link');link.rel='icon';link.type='image/png';link.dataset.atrangiFavicon='1';document.head.appendChild(link);
  logoData().then(src=>{link.href=src});
}
function brandVersion(){
  document.title=`Atrangi Document Workspace v${VERSION}`;
  const chip=$('.version-chip');if(chip)chip.textContent=`v${VERSION} All-in-One`;
  const ds=$('.drawer-head small');if(ds)ds.textContent=`v${VERSION} • All-in-One Document Platform`;
  document.documentElement.dataset.atrangiVersion=VERSION;
}
function apply(){
  installBrand();
  applyTheme(preferredTheme(),false);
  installThemeToggle();
  installFavicon();
  brandVersion();
}
if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',apply,{once:true});else apply();
let queued=false;
new MutationObserver(()=>{if(queued)return;queued=true;queueMicrotask(()=>{queued=false;installBrand();installThemeToggle();brandVersion()})}).observe(document.documentElement,{subtree:true,childList:true});
setTimeout(apply,250);
setTimeout(apply,1200);
})();
