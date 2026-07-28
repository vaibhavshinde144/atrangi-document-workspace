(function(root){'use strict';
const loaded=new Map();
const SCRIPTS={
 pdfLib:'https://cdn.jsdelivr.net/npm/pdf-lib@1.17.1/dist/pdf-lib.min.js',
 pdfJs:'https://cdn.jsdelivr.net/npm/pdfjs-dist@3.11.174/build/pdf.min.js',
 tesseract:'https://cdn.jsdelivr.net/npm/tesseract.js@5.1.1/dist/tesseract.min.js',
 mammoth:'https://cdn.jsdelivr.net/npm/mammoth@1.8.0/mammoth.browser.min.js',
 xlsx:'https://cdn.jsdelivr.net/npm/xlsx@0.18.5/dist/xlsx.full.min.js',
 jszip:'https://cdn.jsdelivr.net/npm/jszip@3.10.1/dist/jszip.min.js',
 html2canvas:'https://cdn.jsdelivr.net/npm/html2canvas@1.4.1/dist/html2canvas.min.js'
};
function loadScript(key,url=SCRIPTS[key]){if(loaded.has(key))return loaded.get(key);const p=new Promise((resolve,reject)=>{const s=document.createElement('script');s.src=url;s.async=true;s.onload=()=>{if(key==='pdfJs'&&root.pdfjsLib)root.pdfjsLib.GlobalWorkerOptions.workerSrc='https://cdn.jsdelivr.net/npm/pdfjs-dist@3.11.174/build/pdf.worker.min.js';resolve(true)};s.onerror=()=>reject(new Error(`Could not load ${key}. Internet is required the first time this tool is used.`));document.head.appendChild(s)});loaded.set(key,p);return p;}
async function ensure(keys){for(const k of keys)await loadScript(k);return true;}
async function qpdf(){if(root.__qpdfRunner)return root.__qpdfRunner;try{const mod=await import('https://cdn.jsdelivr.net/npm/qpdf-run@0.2.1/+esm');const runner=await mod.createQpdfRunner({workerUrl:'https://cdn.jsdelivr.net/npm/qpdf-run@0.2.1/src/worker.js',qpdfJsUrl:'https://cdn.jsdelivr.net/npm/qpdf-run@0.2.1/vendor/qpdf/lib/qpdf.js',wasmUrl:'https://cdn.jsdelivr.net/npm/qpdf-run@0.2.1/vendor/qpdf/lib/qpdf.wasm',timeoutMs:60000});root.__qpdfRunner=runner;return runner;}catch(e){throw new Error('PDF password engine could not load. Serve the app over HTTPS/HTTP and check internet access. '+e.message)}}
async function archive(){if(root.__archiveLib)return root.__archiveLib;try{const mod=await import('https://cdn.jsdelivr.net/npm/libarchive.js@2.0.2/main.js');mod.Archive.init({workerUrl:'https://cdn.jsdelivr.net/npm/libarchive.js@2.0.2/dist/worker-bundle.js'});root.__archiveLib=mod;return mod;}catch(e){throw new Error('7Z/archive engine could not load. Internet is required the first time. '+e.message)}}
root.AtrangiLib={SCRIPTS,loadScript,ensure,qpdf,archive};
})(window);
