import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { gunzipSync } from 'node:zlib';

const root=path.resolve(import.meta.dirname,'..');
const docs=path.join(root,'docs');
const index=fs.readFileSync(path.join(docs,'index.html'),'utf8');
assert.match(index,/<!doctype html>/i,'index must be valid HTML');
assert.match(index,/bootstrap-v720\.js/,'index must use the external v7.2.0 bootstrap');
assert.match(index,/Atrangi Document Workspace v7\.2\.0/,'index title must be v7.2.0');
for(const forbidden of ['DecompressionStream','document.write(text)','const text=source.replace','hardCss=document.createElement'])assert.ok(!index.includes(forbidden),`bootstrap implementation must not be inline in index: ${forbidden}`);
const inline=[...index.matchAll(/<script(?![^>]+src=)[^>]*>([\s\S]*?)<\/script>/gi)].map(m=>m[1].trim()).filter(Boolean);
assert.equal(inline.length,0,'index must contain no inline JavaScript');

const bootstrap=fs.readFileSync(path.join(docs,'bootstrap-v720.js'),'utf8');
assert.doesNotThrow(()=>new Function(bootstrap),'external bootstrap JS must parse');
for(const token of ["RELEASE='720'","VERSION='7.2.0'",'DecompressionStream','branding-v720.css','branding-v720.js','brandHomeBtn','themeToggleBtn','shareAppBtn','Share / Install App'])assert.ok(bootstrap.includes(token),`external bootstrap missing ${token}`);

const chunks=Array.from({length:7},(_,i)=>`app-${String(i).padStart(2,'0')}.txt`);
const encoded=chunks.map(name=>{const f=path.join(docs,name);assert.ok(fs.existsSync(f),`missing chunk ${name}`);return fs.readFileSync(f,'utf8').trim()}).join('');
const app=gunzipSync(Buffer.from(encoded,'base64')).toString('utf8');
assert.match(app,/<!doctype html>/i,'decoded bundle must be HTML');
for(const token of ['class="topbar"','class="brand-block"','class="brand-logo"','class="top-actions"','id="homePanel"','id="desktopNav"','id="mobileNav"','id="heroScanBtn"','id="heroImportBtn"','id="heroWorkspaceBtn"','id="universalImportInput"','<section class="drawer-section"><span>APP</span>'])assert.ok(app.includes(token),`missing base UI contract ${token}`);
const refs=[...app.matchAll(/<(?:link|script)[^>]+(?:href|src)="([^"]+)"[^>]*>/gi)].map(m=>m[1]);
const local=refs.filter(u=>!/^(?:[a-z]+:|\/\/|#|data:)/i.test(u)).map(u=>u.split(/[?#]/,1)[0]);
for(const asset of new Set(local))assert.ok(fs.existsSync(path.join(docs,asset)),`missing base asset ${asset}`);
for(const asset of ['bootstrap-v720.js','branding-v720.css','branding-v720.js','atrangi-brand-logo.svg','atrangi-brand-logo.b64','hardening-v715.css','hardening-v715.js'])assert.ok(fs.existsSync(path.join(docs,asset)),`missing release asset ${asset}`);

const version=JSON.parse(fs.readFileSync(path.join(docs,'version.json'),'utf8'));
assert.equal(version.webVersion,'7.2.0','version.json must be v7.2.0');
assert.equal(version.nativeVersionCode,5,'native version code must be 5');
assert.equal(version.nativeVersionName,'7.2.0','native version name must be v7.2.0');
assert.equal(version.apkUrl,'https://vaibhavshinde144.github.io/atrangi-document-workspace/downloads/Atrangi-Document-Workspace.apk','stable APK URL mismatch');

const branding=fs.readFileSync(path.join(docs,'branding-v720.js'),'utf8');
assert.doesNotThrow(()=>new Function(branding),'branding JS must parse');
for(const token of ["VERSION='7.2.0'",'themeToggleBtn','brandingTest','brandingFunctionalTest','brandHomeBtn','shareAppBtn','shareApp','INSTALL_URL','applyApprovedLogo','atrangi-brand-logo.b64'])assert.ok(branding.includes(token),`branding runtime missing ${token}`);
const b64=fs.readFileSync(path.join(docs,'atrangi-brand-logo.b64'),'utf8').replace(/\s+/g,'');assert.ok(b64.length>1000,'approved logo base64 must be populated');

const sw=fs.readFileSync(path.join(docs,'sw.js'),'utf8');
assert.doesNotThrow(()=>new Function(sw),'service worker JS must parse');
assert.match(sw,/atrangi-document-workspace-v7\.2\.0/,'service worker cache must be v7.2.0');
for(const asset of ['bootstrap-v720.js','branding-v720.js','branding-v720.css','atrangi-brand-logo.b64'])assert.ok(sw.includes(asset),`service worker missing ${asset}`);

const gradle=fs.readFileSync(path.join(root,'app/build.gradle.kts'),'utf8');
assert.match(gradle,/versionCode\s*=\s*5/,'Android versionCode must be 5');
assert.match(gradle,/versionName\s*=\s*"7\.2\.0"/,'Android versionName must be 7.2.0');
assert.match(gradle,/atrangi_riders_launcher\.png/,'Android build must generate new Atrangi Riders launcher resource');
const manifest=fs.readFileSync(path.join(root,'app/src/main/AndroidManifest.xml'),'utf8');
assert.match(manifest,/android:icon="@drawable\/atrangi_riders_launcher"/,'Android launcher icon must use Atrangi Riders resource');
assert.match(manifest,/android:roundIcon="@drawable\/atrangi_riders_launcher"/,'Android round launcher icon must use Atrangi Riders resource');
const main=fs.readFileSync(path.join(root,'app/src/main/java/com/atrangi/documentworkspace/MainActivity.kt'),'utf8');
for(const token of ['AtrangiNativeBridge','addJavascriptInterface','shareInstallLink','Atrangi-Document-Workspace.apk'])assert.ok(main.includes(token),`native share bridge missing ${token}`);

console.log(`Verified v7.2.0 package: ${chunks.length} chunks, ${new Set(local).size} bundled assets, native launcher icon and Share App bridge.`);
