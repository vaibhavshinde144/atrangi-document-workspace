import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { gunzipSync } from 'node:zlib';

const root = path.resolve(import.meta.dirname, '..');
const docs = path.join(root, 'docs');
const loader = fs.readFileSync(path.join(docs, 'index.html'), 'utf8');
const chunkList = loader.match(/const names=\[([^\]]+)\]/);

assert.ok(chunkList, 'docs/index.html must declare its application chunks');
const chunks = [...chunkList[1].matchAll(/'([^']+)'/g)].map((match) => match[1]);
assert.ok(chunks.length > 0, 'the application chunk list must not be empty');

const encoded = chunks.map((name) => {
  const file = path.join(docs, name);
  assert.ok(fs.existsSync(file), `missing application chunk: ${name}`);
  return fs.readFileSync(file, 'utf8').trim();
}).join('');

const applicationHtml = gunzipSync(Buffer.from(encoded, 'base64')).toString('utf8');
assert.match(applicationHtml, /<!doctype html>/i, 'decoded application is not HTML');

const assetUrls = [...applicationHtml.matchAll(
  /<(?:link|script)[^>]+(?:href|src)="([^"]+)"[^>]*>/gi,
)].map((match) => match[1]);
const localAssets = assetUrls
  .filter((url) => !/^(?:[a-z]+:|\/\/|#|data:)/i.test(url))
  .map((url) => url.split(/[?#]/, 1)[0]);

assert.ok(localAssets.filter((name) => name.endsWith('.css')).length >= 5,
  'decoded application must reference its complete stylesheet set');
assert.ok(localAssets.filter((name) => name.endsWith('.js')).length >= 12,
  'decoded application must reference its complete script set');

for (const asset of new Set(localAssets)) {
  assert.ok(fs.existsSync(path.join(docs, asset)), `missing deployed asset: ${asset}`);
}

const update = JSON.parse(fs.readFileSync(path.join(docs, 'version.json'), 'utf8'));
assert.match(update.webVersion, /^\d+\.\d+\.\d+$/, 'webVersion must be semantic');

console.log(`Verified ${chunks.length} chunks and ${new Set(localAssets).size} deployed assets for web ${update.webVersion}.`);
