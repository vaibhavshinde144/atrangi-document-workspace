import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { gunzipSync } from 'node:zlib';

const root = path.resolve(import.meta.dirname, '..');
const docs = path.join(root, 'docs');
const loader = fs.readFileSync(path.join(docs, 'index.html'), 'utf8');
const chunkList = loader.match(/const names=\[([^\]]+)\]/);
assert.ok(chunkList, 'docs/index.html must declare application chunks');
const chunks = [...chunkList[1].matchAll(/'([^']+)'/g)].map(m => m[1]);
assert.equal(chunks.length, 7, 'expected seven application chunks');
const encoded = chunks.map(name => {
  const file = path.join(docs, name);
  assert.ok(fs.existsSync(file), `missing application chunk: ${name}`);
  return fs.readFileSync(file, 'utf8').trim();
}).join('');
const applicationHtml = gunzipSync(Buffer.from(encoded, 'base64')).toString('utf8');
assert.match(applicationHtml, /<!doctype html>/i, 'decoded application is not HTML');
for (const contract of ['class="topbar"','class="brand-block"','class="brand-logo"','class="top-actions"','id="homePanel"','id="desktopNav"','id="mobileNav"']) {
  assert.ok(applicationHtml.includes(contract), `decoded application missing UI contract: ${contract}`);
}
const assetUrls = [...applicationHtml.matchAll(/<(?:link|script)[^>]+(?:href|src)="([^"]+)"[^>]*>/gi)].map(m => m[1]);
const localAssets = assetUrls.filter(url => !/^(?:[a-z]+:|\/\/|#|data:)/i.test(url)).map(url => url.split(/[?#]/,1)[0]);
assert.ok(localAssets.filter(n=>n.endsWith('.css')).length >= 5, 'decoded app must reference full stylesheet set');
assert.ok(localAssets.filter(n=>n.endsWith('.js')).length >= 12, 'decoded app must reference full script set');
for (const asset of new Set(localAssets)) assert.ok(fs.existsSync(path.join(docs, asset)), `missing base asset: ${asset}`);
for (const asset of ['hardening-v715.css','hardening-v715.js','branding-v718.css','branding-v718.js','atrangi-brand-logo.b64','atrangi-brand-logo.png']) {
  assert.ok(fs.existsSync(path.join(docs, asset)), `missing v7.1.8 release asset: ${asset}`);
}
const published = fs.readFileSync(path.join(docs, 'atrangi-brand-logo.png'));
assert.ok(published.length > 1000, 'materialized brand image unexpectedly small');
assert.equal(published[0], 0x89, 'brand image must be PNG');
assert.equal(published.subarray(1,4).toString('ascii'), 'PNG', 'brand image must be PNG');
const update = JSON.parse(fs.readFileSync(path.join(docs, 'version.json'), 'utf8'));
assert.equal(update.webVersion, '7.1.8', 'deployment manifest must be v7.1.8');
assert.match(loader, /release='718'/, 'loader cache-bust must be release 718');
assert.match(loader, /Atrangi Document Workspace v7\.1\.8/, 'bootstrap title must be v7.1.8');
assert.match(loader, /branding-v718\.css\?v=718/, 'branding CSS must be injected into decoded document');
assert.match(loader, /branding-v718\.js\?v=718/, 'branding JS must be injected into decoded document');
const writePos = loader.indexOf('document.write(text)');
const injectCssPos = loader.indexOf("branding-v718.css?v=718");
const injectJsPos = loader.indexOf("branding-v718.js?v=718");
assert.ok(injectCssPos >= 0 && injectCssPos < writePos, 'branding CSS injection must occur before document.write');
assert.ok(injectJsPos >= 0 && injectJsPos < writePos, 'branding JS injection must occur before document.write');
const inlineScript = loader.match(/<script>([\s\S]*?)<\/script>/i)?.[1];
assert.ok(inlineScript, 'bootstrap inline script missing');
assert.doesNotThrow(() => new Function(inlineScript), 'bootstrap inline script must parse');
const brandingJs = fs.readFileSync(path.join(docs, 'branding-v718.js'), 'utf8');
assert.doesNotThrow(() => new Function(brandingJs), 'v7.1.8 branding runtime must parse');
for (const token of ["const VERSION='7.1.8'",'atrangi-brand-logo.png','themeToggleBtn','atrangiBrandReady','AtrangiScannerApp.switchTab']) {
  assert.ok(brandingJs.includes(token), `branding runtime missing contract: ${token}`);
}
const sw = fs.readFileSync(path.join(docs, 'sw.js'), 'utf8');
assert.doesNotThrow(() => new Function(sw), 'service worker must parse');
assert.match(sw, /v7\.1\.8/, 'service worker cache must be v7.1.8');
assert.match(sw, /branding-v718\.js/, 'service worker must cache v7.1.8 branding JS');
assert.match(sw, /branding-v718\.css/, 'service worker must cache v7.1.8 branding CSS');
assert.match(sw, /atrangi-brand-logo\.png/, 'service worker must cache PNG logo');
console.log(`Verified ${chunks.length} chunks, ${new Set(localAssets).size} base assets and v7.1.8 runtime-corrected release assets.`);
