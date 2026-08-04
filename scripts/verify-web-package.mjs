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
assert.equal(version.webVersion,'7.2.1','version.json hosted web version must stay v7.2.1');
assert.equal(version.nativeVersionCode,8,'native version code must be 8');
assert.equal(version.nativeVersionName,'7.2.3','native version name must be v7.2.3');
assert.equal(version.apkUrl,'https://vaibhavshinde144.github.io/atrangi-document-workspace/downloads/Atrangi-Document-Workspace.apk','stable APK URL mismatch');

const branding=fs.readFileSync(path.join(docs,'branding-v721.js'),'utf8');
assert.doesNotThrow(()=>new Function(branding),'branding JS must parse');
for(const token of ["VERSION='7.2.1'",'themeToggleBtn','brandingTest','brandingFunctionalTest','brandHomeBtn','shareAppBtn','shareApp','copyInstallLink','appShareToApps','appCopyInstallLink','contentReady','INSTALL_URL','atrangi-brand-logo.png'])assert.ok(branding.includes(token),`branding runtime missing ${token}`);

const passport=fs.readFileSync(path.join(docs,'passport-simple-v721.js'),'utf8');
assert.doesNotThrow(()=>new Function(passport),'simple passport JS must parse');
for(const token of ['SIMPLE PASSPORT PHOTO','Capture','Background','Brightness','Contrast','Remove plain background','DRESSES','formalKurti','blackSuit','sharePhoto','downloadPhoto','Optional official compliance check','complianceMode=\'general\''])assert.ok(passport.includes(token),`simple passport workflow missing ${token}`);
assert.ok((passport.match(/\{label:/g)||[]).length>=6,'simple passport workflow must provide at least six common sizes');

const workspace=fs.readFileSync(path.join(docs,'workspace-v7.js'),'utf8');
for(const token of ['AtrangiNative?.shareFile','navigator.canShare','Direct sharing is unavailable here','window.AtrangiWorkspaceV7={load,importFiles,openAsset'])assert.ok(workspace.includes(token),`workspace sharing/viewer contract missing ${token}`);

const sw=fs.readFileSync(path.join(docs,'sw.js'),'utf8');
assert.doesNotThrow(()=>new Function(sw),'service worker JS must parse');
assert.match(sw,/atrangi-document-workspace-v7\.2\.1/,'service worker cache must be v7.2.1');
for(const asset of ['bootstrap-v721.js','branding-v721.js','branding-v721.css','passport-simple-v721.js','passport-simple-v721.css','atrangi-brand-logo.png'])assert.ok(sw.includes(asset),`service worker missing ${asset}`);

const gradle=fs.readFileSync(path.join(root,'app/build.gradle.kts'),'utf8');
assert.match(gradle,/versionCode\s*=\s*8/,'Android versionCode must be 8');
assert.match(gradle,/versionName\s*=\s*"7\.2\.3"/,'Android versionName must be 7.2.3');
assert.match(gradle,/androidx\.recyclerview:recyclerview/,'offline PDF viewer must include RecyclerView');
for(const token of ['ATRANGI_KEYSTORE_PATH','ATRANGI_KEYSTORE_PASSWORD','ATRANGI_KEY_ALIAS','ATRANGI_KEY_PASSWORD','atrangiRelease'])assert.ok(gradle.includes(token),`stable Android signing contract missing ${token}`);
assert.ok(!gradle.includes('atrangi-brand-logo.b64'),'Android build must use the exact tracked PNG directly');
for(const workflowPath of ['.github/workflows/pages.yml','.github/workflows/android-build.yml']){
  const workflow=fs.readFileSync(path.join(root,workflowPath),'utf8');
  for(const token of ['secrets.ATRANGI_SIGNING_KEY_BASE64','secrets.ATRANGI_KEYSTORE_PASSWORD','secrets.ATRANGI_KEY_ALIAS','secrets.ATRANGI_KEY_PASSWORD',':app:assembleRelease','app/build/outputs/apk/release/app-release.apk','apksigner'])assert.ok(workflow.includes(token),`${workflowPath} stable release workflow missing ${token}`);
  assert.ok(!workflow.includes(':app:assembleDebug'),`${workflowPath} must not publish a per-run debug-signed APK`);
}
const pagesWorkflow=fs.readFileSync(path.join(root,'.github/workflows/pages.yml'),'utf8');
for(const token of ['aapt','dump resources','drawable/atrangi_riders_logo'])assert.ok(pagesWorkflow.includes(token),`release APK resource-table verification missing ${token}`);
const androidLogo=fs.readFileSync(path.join(root,'app/src/main/res/drawable-nodpi/atrangi_riders_logo.png'));
assert.equal(crypto.createHash('sha256').update(androidLogo).digest('hex'),expectedLogoSha256,'Android logo must be byte-identical to the supplied circular logo');
const manifest=fs.readFileSync(path.join(root,'app/src/main/AndroidManifest.xml'),'utf8');
assert.match(manifest,/android:icon="@mipmap\/ic_launcher"/,'Android launcher icon must use the adaptive launcher resource');
assert.match(manifest,/android:roundIcon="@mipmap\/ic_launcher_round"/,'Android round icon must use the adaptive launcher resource');
assert.match(manifest,/android:launchMode="singleTop"/,'MainActivity must reuse the foreground instance for external documents');
for(const token of ['android.intent.action.VIEW','android.intent.action.SEND','android:scheme="content"','android:mimeType="*/*"'])assert.ok(manifest.includes(token),`Android external document intent contract missing ${token}`);
const main=fs.readFileSync(path.join(root,'app/src/main/java/com/atrangi/documentworkspace/MainActivity.kt'),'utf8');
for(const token of ['AtrangiNativeBridge','addJavascriptInterface','shareInstallLink','shareFile(','copyInstallUrl','contentReady','createCameraCaptureIntent','MediaStore.ACTION_IMAGE_CAPTURE','loadWithOverviewMode = false','useWideViewPort = true','WindowCompat.setDecorFitsSystemWindows(window, false)','applySystemBarInsets','openExternalPdfIfNeeded','PdfViewerActivity.createIntent','family:detected.id','Atrangi-Document-Workspace.apk','?app=7.2.1','extractExternalDocumentUri','prepareExternalDocument','externalDocumentInfo','readExternalDocumentChunk','markExternalDocumentConsumed','externalDocumentFailed','EXTERNAL_DOCUMENT_OPEN_SCRIPT','AtrangiWorkspaceV7','AtrangiWorkspaceCore','openAsset(asset)','EXTERNAL_CHUNK_BYTES'])assert.ok(main.includes(token),`native Android contract missing ${token}`);
assert.ok(!main.includes('setInitialScale(100)'),'Android WebView must not force physical-pixel desktop scaling');
const pdfViewer=fs.readFileSync(path.join(root,'app/src/main/java/com/atrangi/documentworkspace/PdfViewerActivity.kt'),'utf8');
for(const token of ['class PdfViewerActivity','PdfRenderer','MODE_READ_ONLY','RENDER_MODE_FOR_DISPLAY','R.string.pdf_viewer_opening_offline','R.string.pdf_viewer_offline_fit','FileProvider.getUriForFile','WindowInsetsCompat.Type.navigationBars'])assert.ok(pdfViewer.includes(token),`offline PDF viewer contract missing ${token}`);
const updateManager=fs.readFileSync(path.join(root,'app/src/main/java/com/atrangi/documentworkspace/UpdateManager.kt'),'utf8');
for(const token of ['BASE_WEB_VERSION = "7.2.1"','getString(KEY_APPLIED_WEB_VERSION, BASE_WEB_VERSION)','val versionLabel = if (hasNativeUpdate) info.nativeVersionName else info.webVersion'])assert.ok(updateManager.includes(token),`update version separation missing ${token}`);

console.log(`Verified web v7.2.1 + Android v7.2.3 package: ${chunks.length} chunks, ${new Set(local).size} bundled assets, exact logo, mobile-safe scaling/insets, native sharing and a completely offline external PDF viewer.`);
