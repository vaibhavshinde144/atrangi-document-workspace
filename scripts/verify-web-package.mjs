import assert from 'node:assert/strict';
import crypto from 'node:crypto';
import fs from 'node:fs';
import path from 'node:path';
import { gunzipSync } from 'node:zlib';

const root=path.resolve(import.meta.dirname,'..');
const docs=path.join(root,'docs');
const expectedLogoSha256='4fae167cca608d0f8d3c6a158eac828b6a560d60d99f01e3c308ef18201b3c52';
const index=fs.readFileSync(path.join(docs,'index.html'),'utf8');
assert.match(index,/<!doctype html>/i,'index must be valid HTML');
assert.match(index,/bootstrap-v721\.js/,'index must use the external v7.2.1 bootstrap');
assert.match(index,/Atrangi Document Workspace v7\.2\.1/,'index title must be v7.2.1');
assert.match(index,/atrangi-brand-logo\.png\?v=721/,'startup must use the exact supplied PNG logo');
assert.ok(!index.includes('Loading Atrangi Document Workspace'),'startup must not expose provisional text/version layout');
for(const forbidden of ['DecompressionStream','document.write(text)','const text=source.replace','hardCss=document.createElement'])assert.ok(!index.includes(forbidden),`bootstrap implementation must not be inline in index: ${forbidden}`);
const inline=[...index.matchAll(/<script(?![^>]+src=)[^>]*>([\s\S]*?)<\/script>/gi)].map(match=>match[1].trim()).filter(Boolean);
assert.equal(inline.length,0,'index must contain no inline JavaScript');

const bootstrap=fs.readFileSync(path.join(docs,'bootstrap-v721.js'),'utf8');
assert.doesNotThrow(()=>new Function(bootstrap),'external bootstrap JS must parse');
for(const token of ["RELEASE='721'","VERSION='7.2.1'",'DecompressionStream','branding-v721.css','branding-v721.js','passport-simple-v721.css','passport-simple-v721.js','atrangi-startup-gate','brandHomeBtn','themeToggleBtn','shareAppBtn','Share / Install App'])assert.ok(bootstrap.includes(token),`external bootstrap missing ${token}`);

const chunks=Array.from({length:7},(_,index)=>`app-${String(index).padStart(2,'0')}.txt`);
const encoded=chunks.map(name=>{const file=path.join(docs,name);assert.ok(fs.existsSync(file),`missing chunk ${name}`);return fs.readFileSync(file,'utf8').trim()}).join('');
const app=gunzipSync(Buffer.from(encoded,'base64')).toString('utf8');
assert.match(app,/<!doctype html>/i,'decoded bundle must be HTML');
for(const token of ['class="topbar"','class="brand-block"','class="brand-logo"','class="top-actions"','id="homePanel"','id="desktopNav"','id="mobileNav"','id="heroScanBtn"','id="heroImportBtn"','id="heroWorkspaceBtn"','id="universalImportInput"','<section class="drawer-section"><span>APP</span>'])assert.ok(app.includes(token),`missing base UI contract ${token}`);
const refs=[...app.matchAll(/<(?:link|script)[^>]+(?:href|src)="([^"]+)"[^>]*>/gi)].map(match=>match[1]);
const local=refs.filter(url=>!/^(?:[a-z]+:|\/\/|#|data:)/i.test(url)).map(url=>url.split(/[?#]/,1)[0]);
for(const asset of new Set(local))assert.ok(fs.existsSync(path.join(docs,asset)),`missing base asset ${asset}`);
for(const asset of ['bootstrap-v721.js','branding-v720.css','branding-v721.css','branding-v721.js','passport-simple-v721.css','passport-simple-v721.js','atrangi-brand-logo.png','hardening-v715.css','hardening-v715.js'])assert.ok(fs.existsSync(path.join(docs,asset)),`missing release asset ${asset}`);

const logo=fs.readFileSync(path.join(docs,'atrangi-brand-logo.png'));
assert.equal(crypto.createHash('sha256').update(logo).digest('hex'),expectedLogoSha256,'web logo must be byte-identical to the supplied circular logo');
assert.ok(logo.subarray(0,8).equals(Buffer.from([0x89,0x50,0x4e,0x47,0x0d,0x0a,0x1a,0x0a])),'web logo must be a PNG');

const version=JSON.parse(fs.readFileSync(path.join(docs,'version.json'),'utf8'));
assert.equal(version.webVersion,'7.2.1','version.json must be v7.2.1');
assert.equal(version.nativeVersionCode,6,'native version code must be 6');
assert.equal(version.nativeVersionName,'7.2.1','native version name must be v7.2.1');
assert.equal(version.apkUrl,'https://vaibhavshinde144.github.io/atrangi-document-workspace/downloads/Atrangi-Document-Workspace.apk','stable APK URL mismatch');

const branding=fs.readFileSync(path.join(docs,'branding-v721.js'),'utf8');
assert.doesNotThrow(()=>new Function(branding),'branding JS must parse');
for(const token of ["VERSION='7.2.1'",'themeToggleBtn','brandingTest','brandingFunctionalTest','brandHomeBtn','shareAppBtn','shareApp','copyInstallLink','appShareToApps','appCopyInstallLink','contentReady','INSTALL_URL','atrangi-brand-logo.png'])assert.ok(branding.includes(token),`branding runtime missing ${token}`);

const passport=fs.readFileSync(path.join(docs,'passport-simple-v721.js'),'utf8');
assert.doesNotThrow(()=>new Function(passport),'simple passport JS must parse');
for(const token of ['SIMPLE PASSPORT PHOTO','Capture','Background','Brightness','Contrast','Remove plain background','DRESSES','formalKurti','blackSuit','sharePhoto','downloadPhoto','Optional official compliance check','complianceMode=\'general\''])assert.ok(passport.includes(token),`simple passport workflow missing ${token}`);
assert.ok((passport.match(/\{label:/g)||[]).length>=6,'simple passport workflow must provide at least six common sizes');

const workspace=fs.readFileSync(path.join(docs,'workspace-v7.js'),'utf8');
for(const token of ['AtrangiNative?.shareFile','navigator.canShare','Direct sharing is unavailable here'])assert.ok(workspace.includes(token),`workspace sharing missing ${token}`);

const sw=fs.readFileSync(path.join(docs,'sw.js'),'utf8');
assert.doesNotThrow(()=>new Function(sw),'service worker JS must parse');
assert.match(sw,/atrangi-document-workspace-v7\.2\.1/,'service worker cache must be v7.2.1');
for(const asset of ['bootstrap-v721.js','branding-v721.js','branding-v721.css','passport-simple-v721.js','passport-simple-v721.css','atrangi-brand-logo.png'])assert.ok(sw.includes(asset),`service worker missing ${asset}`);

const gradle=fs.readFileSync(path.join(root,'app/build.gradle.kts'),'utf8');
assert.match(gradle,/versionCode\s*=\s*6/,'Android versionCode must be 6');
assert.match(gradle,/versionName\s*=\s*"7\.2\.1"/,'Android versionName must be 7.2.1');
assert.ok(!gradle.includes('atrangi-brand-logo.b64'),'Android build must use the exact tracked PNG directly');
const androidLogo=fs.readFileSync(path.join(root,'app/src/main/res/drawable-nodpi/atrangi_riders_logo.png'));
assert.equal(crypto.createHash('sha256').update(androidLogo).digest('hex'),expectedLogoSha256,'Android logo must be byte-identical to the supplied circular logo');
const manifest=fs.readFileSync(path.join(root,'app/src/main/AndroidManifest.xml'),'utf8');
assert.match(manifest,/android:icon="@mipmap\/ic_launcher"/,'Android launcher icon must use the adaptive launcher resource');
assert.match(manifest,/android:roundIcon="@mipmap\/ic_launcher_round"/,'Android round icon must use the adaptive launcher resource');
const main=fs.readFileSync(path.join(root,'app/src/main/java/com/atrangi/documentworkspace/MainActivity.kt'),'utf8');
for(const token of ['AtrangiNativeBridge','addJavascriptInterface','shareInstallLink','shareFile(','copyInstallUrl','contentReady','createCameraCaptureIntent','MediaStore.ACTION_IMAGE_CAPTURE','loadWithOverviewMode = false','useWideViewPort = false','setInitialScale(100)','Atrangi-Document-Workspace.apk','?app=7.2.1'])assert.ok(main.includes(token),`native Android contract missing ${token}`);

console.log(`Verified v7.2.1 package: ${chunks.length} chunks, ${new Set(local).size} bundled assets, exact logo, simple passport workflow, stable startup and native sharing.`);
