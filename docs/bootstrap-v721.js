(()=>{'use strict';
const RELEASE='721',VERSION='7.2.1';
const chunks=['app-00.txt','app-01.txt','app-02.txt','app-03.txt','app-04.txt','app-05.txt','app-06.txt'];
const q=url=>url+(url.includes('?')?'&':'?')+'v='+RELEASE;
function fail(error){console.error('Atrangi bootstrap failed',error);const message=error?.message||String(error||'Unknown error');document.body.innerHTML=`<main style="max-width:720px;margin:0 auto;padding:28px;font:16px/1.5 system-ui,-apple-system,Segoe UI,sans-serif"><h1 style="font-size:22px">Atrangi Document Workspace could not start</h1><p>${message.replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]))}</p><button type="button" onclick="location.reload()" style="padding:11px 16px;border-radius:10px;border:0;font-weight:700">Retry</button></main>`}
async function decode(){if(typeof DecompressionStream!=='function')throw new Error('Android System WebView/Chrome is out of date. Update it and reopen Atrangi Document Workspace.');let b64='';for(const name of chunks){const response=await fetch(q(name),{cache:'no-store'});if(!response.ok)throw new Error(`Unable to load ${name} (${response.status})`);b64+=(await response.text()).trim()}const raw=Uint8Array.from(atob(b64),c=>c.charCodeAt(0));const stream=new Blob([raw]).stream().pipeThrough(new DecompressionStream('gzip'));return new Response(stream).text()}
function patch(source){
 source=source.replace(/<title>Atrangi Document Workspace v7\.1<\/title>/,'<title>Atrangi Document Workspace v7.2.1</title>');
 source=source.replace('<meta name="theme-color" content="#0e3b4d">','<meta name="theme-color" content="#0e3b4d"><meta http-equiv="Cache-Control" content="no-cache, no-store, must-revalidate"><style id="atrangi-startup-gate">body{visibility:hidden;animation:atrangi-fail-open 0s 5s forwards}@keyframes atrangi-fail-open{to{visibility:visible}}</style>');
 source=source.replace('<link rel="stylesheet" href="workspace-v7.css">','<link rel="stylesheet" href="workspace-v7.css"><link rel="stylesheet" href="hardening-v715.css"><link rel="stylesheet" href="branding-v721.css"><link rel="stylesheet" href="passport-simple-v721.css">');
 source=source.replace('<div class="brand-block">','<div id="brandHomeBtn" class="brand-block brand-home-button" role="button" tabindex="0" aria-label="Atrangi Document Workspace — go to Home" title="Go to Home">');
 source=source.replaceAll('<div class="brand-logo"><span>A</span></div>','<div class="brand-logo"><img src="atrangi-brand-logo.png" alt="Atrangi Riders logo" width="48" height="48"></div>');
 source=source.replace('<div class="brand-logo drawer-logo"><span>A</span></div>','<div class="brand-logo drawer-logo"><img src="atrangi-brand-logo.png" alt="Atrangi Riders logo" width="48" height="48"></div>');
 source=source.replace('<button id="privacyBtn"','<button id="themeToggleBtn" class="icon-button theme-toggle-btn" type="button" title="Dark Mode" aria-label="Switch to Dark Mode" aria-pressed="false"><span aria-hidden="true">🌙</span><span class="theme-toggle-label">Dark</span></button><button id="privacyBtn"');
 source=source.replace('<section class="drawer-section"><span>APP</span>','<section class="drawer-section"><span>APP</span><button id="shareAppBtn" class="share-app-button" type="button"><i>↗</i><b>Share / Install App</b><small>Share directly to an app, copy the install link, or download the latest Android app</small></button>');
 source=source.replace('</body>','<script src="hardening-v715.js"></script><script src="passport-simple-v721.js"></script><script src="branding-v721.js"></script></body>');
 return source.replace(/(<(?:link|script|img)\b[^>]+(?:href|src)=")([^"]+)(")/gi,(all,start,url,end)=>/^(?:data:|https?:|\/\/|#)/i.test(url)?all:start+q(url)+end)
}
async function start(){try{if('caches'in window)caches.keys().then(keys=>Promise.all(keys.filter(k=>k.startsWith('atrangi-document-workspace-')&&k!=='atrangi-document-workspace-v7.2.1').map(k=>caches.delete(k)))).catch(()=>{});const source=await decode();document.open();document.write(patch(source));document.close()}catch(error){fail(error)}}
start();
})();
