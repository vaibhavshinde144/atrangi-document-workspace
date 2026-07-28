(function(root){'use strict';
const C=root.AtrangiCore,L=root.AtrangiLib;
const pdfTools=[
{id:'openPdf',icon:'📄',name:'Open PDF',desc:'View PDF pages'},
{id:'mergePdf',icon:'⊞',name:'Merge PDF',desc:'Combine multiple PDFs'},
{id:'splitPdf',icon:'✂',name:'Split / Extract',desc:'Extract page ranges'},
{id:'rotatePdf',icon:'↻',name:'Rotate PDF',desc:'Rotate all pages'},
{id:'compressPdf',icon:'⇲',name:'Compress PDF',desc:'Optimize PDF structure'},
{id:'pdfToImage',icon:'🖼️',name:'PDF to Image',desc:'Convert pages to PNG/JPEG'},
{id:'imageToPdf',icon:'▤',name:'Image to PDF',desc:'Combine images into PDF'},
{id:'pdfToText',icon:'Aa',name:'PDF to Text',desc:'Extract selectable text'},
{id:'textToPdf',icon:'T',name:'Text to PDF',desc:'Create PDF from text'},
{id:'pdfToWord',icon:'W',name:'PDF to Word',desc:'Extract text into DOCX'},
{id:'pdfToExcel',icon:'X',name:'PDF to Excel',desc:'Heuristic table extraction'},
{id:'watermarkPdf',icon:'💧',name:'Watermark',desc:'Add text watermark'},
{id:'pageNumbers',icon:'#',name:'Page Numbers',desc:'Add page numbering'},
{id:'addPassword',icon:'🔒',name:'Add Password',desc:'AES-256 PDF protection'},
{id:'removePassword',icon:'🔓',name:'Remove Password',desc:'Decrypt with known password'},
{id:'signPdf',icon:'✍',name:'Add Signature',desc:'Sign a PDF page'}
];
const fileTools=[
{id:'viewImage',icon:'🖼️',name:'View Image',desc:'JPEG, PNG, WebP, HEIC if supported'},
{id:'viewWord',icon:'W',name:'View Word',desc:'DOCX preview'},
{id:'viewExcel',icon:'X',name:'View Excel',desc:'XLSX / XLS / CSV preview'},
{id:'viewText',icon:'T',name:'View Text',desc:'TXT, JSON, XML, Markdown'},
{id:'viewArchive',icon:'🗜️',name:'View ZIP / 7Z',desc:'Browse archive contents'},
{id:'extractArchive',icon:'📦',name:'Extract ZIP / 7Z',desc:'Extract files locally'},
{id:'wordToPdf',icon:'W→',name:'Word to PDF',desc:'Text/layout-friendly conversion'},
{id:'wordToImage',icon:'W▧',name:'Word to Image',desc:'Render Word content to PNG'},
{id:'excelToPdf',icon:'X→',name:'Excel to PDF',desc:'Convert worksheets to PDF'},
{id:'imageToWord',icon:'▧W',name:'Image to Word',desc:'Place image into DOCX'},
{id:'imageOcrToWord',icon:'AaW',name:'Image OCR to Word',desc:'OCR image and create DOCX'},
{id:'createZip',icon:'＋🗜',name:'Create ZIP',desc:'Bundle selected files'}
];
function readBytes(file){return file.arrayBuffer().then(b=>new Uint8Array(b));}
function readDataURL(file){return new Promise((res,rej)=>{const r=new FileReader();r.onload=()=>res(r.result);r.onerror=()=>rej(r.error);r.readAsDataURL(file)})}
function download(blob,name){const u=URL.createObjectURL(blob),a=document.createElement('a');a.href=u;a.download=name;document.body.appendChild(a);a.click();a.remove();setTimeout(()=>URL.revokeObjectURL(u),2000)}
function dataUrlBytes(data){const b=atob(data.split(',')[1]);const out=new Uint8Array(b.length);for(let i=0;i<b.length;i++)out[i]=b.charCodeAt(i);return out;}
function escXml(s){return String(s).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;').replace(/'/g,'&apos;')}
async function createDocxText(text,title='Atrangi Document'){
 await L.ensure(['jszip']);const zip=new JSZip();
 zip.file('[Content_Types].xml','<?xml version="1.0" encoding="UTF-8"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/></Types>');
 zip.folder('_rels').file('.rels','<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/></Relationships>');
 const paras=String(text||'').split(/\n/).map(line=>`<w:p><w:r><w:t xml:space="preserve">${escXml(line)}</w:t></w:r></w:p>`).join('');
 zip.folder('word').file('document.xml',`<?xml version="1.0" encoding="UTF-8" standalone="yes"?><w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body><w:p><w:r><w:rPr><w:b/></w:rPr><w:t>${escXml(title)}</w:t></w:r></w:p>${paras}<w:sectPr><w:pgSz w:w="11906" w:h="16838"/><w:pgMar w:top="1440" w:right="1440" w:bottom="1440" w:left="1440"/></w:sectPr></w:body></w:document>`);
 return zip.generateAsync({type:'blob',mimeType:'application/vnd.openxmlformats-officedocument.wordprocessingml.document'});
}
async function createDocxImage(file,title='Scanned Image'){
 await L.ensure(['jszip']);const zip=new JSZip(),bytes=await file.arrayBuffer(),ext=(file.type.split('/')[1]||'png').replace('jpeg','jpg');
 zip.file('[Content_Types].xml',`<?xml version="1.0" encoding="UTF-8"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Default Extension="${ext}" ContentType="${file.type||'image/png'}"/><Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/></Types>`);
 zip.folder('_rels').file('.rels','<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/></Relationships>');
 const word=zip.folder('word');word.folder('media').file('image1.'+ext,bytes);word.folder('_rels').file('document.xml.rels','<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId5" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="media/image1.'+ext+'"/></Relationships>');
 word.file('document.xml',`<?xml version="1.0" encoding="UTF-8" standalone="yes"?><w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:wp="http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing" xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:pic="http://schemas.openxmlformats.org/drawingml/2006/picture"><w:body><w:p><w:r><w:t>${escXml(title)}</w:t></w:r></w:p><w:p><w:r><w:drawing><wp:inline><wp:extent cx="5486400" cy="7315200"/><wp:docPr id="1" name="Image 1"/><a:graphic><a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/picture"><pic:pic><pic:nvPicPr><pic:cNvPr id="0" name="image1.${ext}"/><pic:cNvPicPr/></pic:nvPicPr><pic:blipFill><a:blip r:embed="rId5"/><a:stretch><a:fillRect/></a:stretch></pic:blipFill><pic:spPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="5486400" cy="7315200"/></a:xfrm><a:prstGeom prst="rect"><a:avLst/></a:prstGeom></pic:spPr></pic:pic></a:graphicData></a:graphic></wp:inline></w:drawing></w:r></w:p><w:sectPr/></w:body></w:document>`);
 return zip.generateAsync({type:'blob',mimeType:'application/vnd.openxmlformats-officedocument.wordprocessingml.document'});
}
async function pdfDoc(file,password){await L.ensure(['pdfLib']);return PDFLib.PDFDocument.load(await file.arrayBuffer(),{ignoreEncryption:false,password});}
async function extractPdfText(file,onProgress){await L.ensure(['pdfJs']);const pdf=await pdfjsLib.getDocument({data:await file.arrayBuffer()}).promise;const pages=[];for(let i=1;i<=pdf.numPages;i++){const p=await pdf.getPage(i),tc=await p.getTextContent();pages.push(tc.items.map(x=>x.str).join(' '));onProgress&&onProgress(i/pdf.numPages,`Reading page ${i}/${pdf.numPages}`);}return pages;}
async function renderPdf(file,scale=1.6,onProgress){await L.ensure(['pdfJs']);const pdf=await pdfjsLib.getDocument({data:await file.arrayBuffer()}).promise,out=[];for(let i=1;i<=pdf.numPages;i++){const p=await pdf.getPage(i),vp=p.getViewport({scale}),c=document.createElement('canvas');c.width=vp.width;c.height=vp.height;await p.render({canvasContext:c.getContext('2d'),viewport:vp}).promise;out.push(c);onProgress&&onProgress(i/pdf.numPages,`Rendering page ${i}/${pdf.numPages}`);}return out;}
function wrapText(text,font,size,maxWidth){const words=String(text).split(/\s+/),lines=[];let line='';for(const w of words){const test=line?line+' '+w:w;if(font.widthOfTextAtSize(test,size)>maxWidth){if(line)lines.push(line);line=w}else line=test}if(line)lines.push(line);return lines}
async function textToPdfBlob(text,title='Atrangi Document'){
 await L.ensure(['pdfLib']);const doc=await PDFLib.PDFDocument.create(),font=await doc.embedFont(PDFLib.StandardFonts.Helvetica);let page=doc.addPage([595.28,841.89]),y=800;const size=11;page.drawText(title,{x:45,y,size:16,font,color:PDFLib.rgb(.08,.25,.33)});y-=30;for(const para of String(text||'').split('\n')){const lines=wrapText(para||' ',font,size,505);for(const line of lines){if(y<50){page=doc.addPage([595.28,841.89]);y=800}page.drawText(line,{x:45,y,size,font,color:PDFLib.rgb(.12,.16,.18)});y-=16}y-=5;}return new Blob([await doc.save()],{type:'application/pdf'});
}
function pdfTextBytes(text){return new TextEncoder().encode(String(text))}
function concatByteArrays(chunks){const total=chunks.reduce((n,c)=>n+c.length,0),out=new Uint8Array(total);let at=0;for(const c of chunks){out.set(c,at);at+=c.length}return out}
function canvasBlob(c,type='image/jpeg',quality=.94){return new Promise((resolve,reject)=>c.toBlob(b=>b?resolve(b):reject(new Error('Could not encode image for PDF')),type,quality))}
async function imageFileToRgbJpeg(file){const url=URL.createObjectURL(file);try{const im=await new Promise((resolve,reject)=>{const x=new Image();x.onload=()=>resolve(x);x.onerror=()=>reject(new Error('Could not decode image for PDF'));x.src=url}),maxPixels=18_000_000,pixels=im.naturalWidth*im.naturalHeight,scale=pixels>maxPixels?Math.sqrt(maxPixels/pixels):1,c=document.createElement('canvas');c.width=Math.max(1,Math.round(im.naturalWidth*scale));c.height=Math.max(1,Math.round(im.naturalHeight*scale));const x=c.getContext('2d');x.fillStyle='#fff';x.fillRect(0,0,c.width,c.height);x.drawImage(im,0,0,c.width,c.height);const blob=await canvasBlob(c,'image/jpeg',.95);return{bytes:new Uint8Array(await blob.arrayBuffer()),width:c.width,height:c.height}}finally{URL.revokeObjectURL(url)}}
function buildImageOnlyPdf(images){if(!images.length)throw new Error('At least one image is required');const chunks=[],offsets=[0];let byteLength=0;const push=bytes=>{chunks.push(bytes);byteLength+=bytes.length},pushText=text=>push(pdfTextBytes(text));pushText('%PDF-1.4\n% Atrangi Scanner\n');const pageNums=images.map((_,i)=>3+i*3),contentNums=images.map((_,i)=>4+i*3),imageNums=images.map((_,i)=>5+i*3),total=2+images.length*3;const begin=n=>{offsets[n]=byteLength;pushText(`${n} 0 obj\n`)},end=()=>pushText('endobj\n');begin(1);pushText('<< /Type /Catalog /Pages 2 0 R >>\n');end();begin(2);pushText(`<< /Type /Pages /Count ${images.length} /Kids [${pageNums.map(n=>`${n} 0 R`).join(' ')}] >>\n`);end();images.forEach((img,i)=>{const pageW=595.28,pageH=841.89,r=C.computeFit(img.width,img.height,pageW,pageH,24),content=`q\n${r.w.toFixed(3)} 0 0 ${r.h.toFixed(3)} ${r.x.toFixed(3)} ${r.y.toFixed(3)} cm\n/Im${i+1} Do\nQ\n`,contentBytes=pdfTextBytes(content);begin(pageNums[i]);pushText(`<< /Type /Page /Parent 2 0 R /MediaBox [0 0 ${pageW} ${pageH}] /Resources << /XObject << /Im${i+1} ${imageNums[i]} 0 R >> >> /Contents ${contentNums[i]} 0 R >>\n`);end();begin(contentNums[i]);pushText(`<< /Length ${contentBytes.length} >>\nstream\n`);push(contentBytes);pushText('endstream\n');end();begin(imageNums[i]);pushText(`<< /Type /XObject /Subtype /Image /Width ${img.width} /Height ${img.height} /ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /DCTDecode /Length ${img.bytes.length} >>\nstream\n`);push(img.bytes);pushText('\nendstream\n');end()});const xref=byteLength;pushText(`xref\n0 ${total+1}\n0000000000 65535 f \n`);for(let i=1;i<=total;i++)pushText(`${String(offsets[i]).padStart(10,'0')} 00000 n \n`);pushText(`trailer\n<< /Size ${total+1} /Root 1 0 R >>\nstartxref\n${xref}\n%%EOF`);return new Blob([concatByteArrays(chunks)],{type:'application/pdf'})}
async function imageToPdf(files,onProgress){const images=[];for(let i=0;i<files.length;i++){images.push(await imageFileToRgbJpeg(files[i]));onProgress&&onProgress((i+.7)/files.length,`Preparing image ${i+1}/${files.length}`)}const out=buildImageOnlyPdf(images);onProgress&&onProgress(1,'PDF ready');return out}
async function imageToSearchablePdf(files,texts=[],onProgress){try{await Promise.race([L.ensure(['pdfLib']),new Promise((_,reject)=>setTimeout(()=>reject(new Error('Searchable PDF library timed out')),7000))]);const doc=await PDFLib.PDFDocument.create(),font=await doc.embedFont(PDFLib.StandardFonts.Helvetica);for(let i=0;i<files.length;i++){const f=files[i],bytes=await f.arrayBuffer(),img=f.type.includes('png')?await doc.embedPng(bytes):await doc.embedJpg(bytes),page=doc.addPage([595.28,841.89]),r=C.computeFit(img.width,img.height,595.28,841.89,24);page.drawImage(img,{x:r.x,y:r.y,width:r.w,height:r.h});const raw=String(texts[i]||'').replace(/[^\x20-\x7E\n]/g,' ').slice(0,24000),words=raw.split(/\s+/).filter(Boolean);let line='',y=820;for(const w of words){const next=(line?line+' ':'')+w;if(next.length>92){page.drawText(line,{x:18,y,size:6,font,opacity:0});line=w;y-=7;if(y<18)y=820}else line=next}if(line)page.drawText(line,{x:18,y,size:6,font,opacity:0});onProgress&&onProgress((i+1)/files.length,`Building searchable page ${i+1}/${files.length}`)}return new Blob([await doc.save()],{type:'application/pdf'})}catch(e){console.warn('Searchable PDF fallback:',e.message);onProgress&&onProgress(.05,'Searchable layer unavailable; creating image PDF locally');return imageToPdf(files,onProgress)}}

async function mergePdf(files,onProgress){await L.ensure(['pdfLib']);const out=await PDFLib.PDFDocument.create();for(let i=0;i<files.length;i++){const src=await PDFLib.PDFDocument.load(await files[i].arrayBuffer());const pages=await out.copyPages(src,src.getPageIndices());pages.forEach(p=>out.addPage(p));onProgress&&onProgress((i+1)/files.length,`Merging ${i+1}/${files.length}`)}return new Blob([await out.save()],{type:'application/pdf'});}
async function splitPdf(file,range){await L.ensure(['pdfLib']);const src=await PDFLib.PDFDocument.load(await file.arrayBuffer()),idx=C.parsePageRange(range,src.getPageCount());if(!idx.length)throw new Error('Enter a valid page range, for example 1-3,5');const out=await PDFLib.PDFDocument.create(),pages=await out.copyPages(src,idx);pages.forEach(p=>out.addPage(p));return new Blob([await out.save()],{type:'application/pdf'});}
async function rotatePdf(file,degrees){await L.ensure(['pdfLib']);const doc=await PDFLib.PDFDocument.load(await file.arrayBuffer());doc.getPages().forEach(p=>p.setRotation(PDFLib.degrees((((p.getRotation().angle||0)+degrees)%360+360)%360)));return new Blob([await doc.save()],{type:'application/pdf'});}
async function watermarkPdf(file,text){await L.ensure(['pdfLib']);const doc=await PDFLib.PDFDocument.load(await file.arrayBuffer()),font=await doc.embedFont(PDFLib.StandardFonts.HelveticaBold);doc.getPages().forEach(p=>{const {width,height}=p.getSize();p.drawText(text||'ATRANGI',{x:width*.18,y:height*.48,size:Math.min(width,height)*.08,font,rotate:PDFLib.degrees(35),color:PDFLib.rgb(.45,.52,.56),opacity:.22})});return new Blob([await doc.save()],{type:'application/pdf'});}
async function addPageNumbers(file){await L.ensure(['pdfLib']);const doc=await PDFLib.PDFDocument.load(await file.arrayBuffer()),font=await doc.embedFont(PDFLib.StandardFonts.Helvetica),pages=doc.getPages();pages.forEach((p,i)=>{const {width}=p.getSize(),t=`Page ${i+1} of ${pages.length}`,tw=font.widthOfTextAtSize(t,9);p.drawText(t,{x:(width-tw)/2,y:18,size:9,font,color:PDFLib.rgb(.35,.4,.43)})});return new Blob([await doc.save()],{type:'application/pdf'});}
async function signPdf(file,sigDataUrl,pageNumber=1){await L.ensure(['pdfLib']);const doc=await PDFLib.PDFDocument.load(await file.arrayBuffer()),png=await doc.embedPng(dataUrlBytes(sigDataUrl)),pages=doc.getPages(),page=pages[C.clamp(pageNumber-1,0,pages.length-1)],{width}=page.getSize();const w=Math.min(180,width*.35),h=w*(png.height/png.width);page.drawImage(png,{x:width-w-35,y:35,width:w,height:h});return new Blob([await doc.save()],{type:'application/pdf'});}
async function compressPdf(file){try{const runner=await L.qpdf(),bytes=await readBytes(file),out=await runner.runOne({input:bytes,inputName:'input.pdf',outputName:'output.pdf',args:['--object-streams=generate','--compress-streams=y','--recompress-flate','--','input.pdf','output.pdf']});return new Blob([out],{type:'application/pdf'});}catch(e){await L.ensure(['pdfLib']);const doc=await PDFLib.PDFDocument.load(await file.arrayBuffer());return new Blob([await doc.save({useObjectStreams:true})],{type:'application/pdf'});}}
async function protectPdf(file,password){if(!password)throw new Error('Enter a password');const runner=await L.qpdf(),out=await runner.runOne({input:await readBytes(file),inputName:'input.pdf',outputName:'protected.pdf',args:['--encrypt',password,password,'256','--','input.pdf','protected.pdf']});return new Blob([out],{type:'application/pdf'});}
async function unlockPdf(file,password){const runner=await L.qpdf(),out=await runner.runOne({input:await readBytes(file),inputName:'input.pdf',outputName:'unlocked.pdf',args:[`--password=${password||''}`,'--decrypt','--','input.pdf','unlocked.pdf']});return new Blob([out],{type:'application/pdf'});}
async function pdfToWord(file,onProgress){
 await L.ensure(['pdfJs']);
 const pdf=await pdfjsLib.getDocument({data:await file.arrayBuffer()}).promise,pages=[];
 for(let i=1;i<=pdf.numPages;i++){
  const p=await pdf.getPage(i),tc=await p.getTextContent();
  let text=tc.items.map(x=>x.str).join(' ').trim();
  if(text.length<8){
   try{
    await L.ensure(['tesseract']);
    const vp=p.getViewport({scale:1.8}),c=document.createElement('canvas');c.width=vp.width;c.height=vp.height;
    await p.render({canvasContext:c.getContext('2d'),viewport:vp}).promise;
    const blob=await new Promise(res=>c.toBlob(res,'image/png'));
    const r=await Tesseract.recognize(blob,'eng',{logger:m=>onProgress&&onProgress(((i-1)+(m.progress||0))/pdf.numPages,`OCR page ${i}/${pdf.numPages}`)});
    text=(r.data.text||'').trim();
   }catch{}
  }
  pages.push(text);onProgress&&onProgress(i/pdf.numPages,`Reading page ${i}/${pdf.numPages}`);
 }
 const out=pages.map((x,i)=>`Page ${i+1}\n${x||'[No readable text detected]'}`).join('\n\n');
 return createDocxText(out,file.name.replace(/\.pdf$/i,''));
}
async function pdfToExcel(file,onProgress){
 await L.ensure(['pdfJs','xlsx']);
 const pdf=await pdfjsLib.getDocument({data:await file.arrayBuffer()}).promise,wb=XLSX.utils.book_new();
 for(let i=1;i<=pdf.numPages;i++){
  const p=await pdf.getPage(i),tc=await p.getTextContent();
  let rows=C.rowsFromPdfTextItems(tc.items);
  if(!rows.length||rows.flat().join('').trim().length<8){
   try{
    await L.ensure(['tesseract']);
    const vp=p.getViewport({scale:1.8}),c=document.createElement('canvas');c.width=vp.width;c.height=vp.height;
    await p.render({canvasContext:c.getContext('2d'),viewport:vp}).promise;
    const blob=await new Promise(res=>c.toBlob(res,'image/png'));
    const r=await Tesseract.recognize(blob,'eng',{logger:m=>onProgress&&onProgress(((i-1)+(m.progress||0))/pdf.numPages,`OCR table page ${i}/${pdf.numPages}`)});
    rows=(r.data.text||'').split(/\r?\n/).filter(Boolean).map(line=>line.trim().split(/\s{2,}|\t|\s\|\s/));
   }catch{}
  }
  if(!rows.length)rows=[['No readable table/text detected']];
  XLSX.utils.book_append_sheet(wb,XLSX.utils.aoa_to_sheet(rows),`Page ${i}`);
  onProgress&&onProgress(i/pdf.numPages,`Extracting page ${i}/${pdf.numPages}`);
 }
 const arr=XLSX.write(wb,{bookType:'xlsx',type:'array'});
 return new Blob([arr],{type:'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'});
}
async function wordToPdf(file){await L.ensure(['mammoth']);const r=await mammoth.extractRawText({arrayBuffer:await file.arrayBuffer()});return textToPdfBlob(r.value,file.name.replace(/\.docx?$/i,''));}
async function wordToImage(file){await L.ensure(['mammoth']);const r=await mammoth.extractRawText({arrayBuffer:await file.arrayBuffer()}),text=r.value||'(Empty document)',c=document.createElement('canvas'),ctx=c.getContext('2d'),lines=text.split(/\n/),width=1240,pad=70,fontSize=24;ctx.font=`${fontSize}px Arial`;const wrapped=[];for(const raw of lines){const words=raw.split(/\s+/);let line='';for(const w of words){const t=line?line+' '+w:w;if(ctx.measureText(t).width>width-pad*2){wrapped.push(line);line=w}else line=t}wrapped.push(line)}c.width=width;c.height=Math.max(1754,pad*2+wrapped.length*36);ctx.fillStyle='white';ctx.fillRect(0,0,c.width,c.height);ctx.fillStyle='#18242b';ctx.font=`${fontSize}px Arial`;wrapped.forEach((l,i)=>ctx.fillText(l,pad,pad+i*36));return new Promise(res=>c.toBlob(res,'image/png'));}
async function excelToPdf(file){await L.ensure(['xlsx']);const wb=XLSX.read(await file.arrayBuffer(),{type:'array'});let text='';wb.SheetNames.forEach(n=>{text+=`\n${n}\n`;const rows=XLSX.utils.sheet_to_json(wb.Sheets[n],{header:1,raw:false});text+=rows.map(r=>r.join(' | ')).join('\n')+'\n'});return textToPdfBlob(text,file.name.replace(/\.(xlsx?|csv)$/i,''));}
async function ocrImage(file,lang='eng',onProgress){await L.ensure(['tesseract']);const result=await Tesseract.recognize(file,lang,{logger:m=>{if(m.status&&onProgress)onProgress(m.progress||0,m.status)}});return result.data.text||'';}
async function imageOcrToWord(file,lang,onProgress){return createDocxText(await ocrImage(file,lang,onProgress),file.name.replace(/\.[^.]+$/,''));}
async function createZip(files){await L.ensure(['jszip']);const z=new JSZip();for(const f of files)z.file(f.name,await f.arrayBuffer());return z.generateAsync({type:'blob',compression:'DEFLATE',compressionOptions:{level:6}});}
async function archiveList(file){const kind=C.guessFileKind(file.name,file.type);if(kind==='zip'){await L.ensure(['jszip']);const z=await JSZip.loadAsync(file);return Object.values(z.files).map(x=>({name:x.name,dir:x.dir,size:null}));}const {Archive}=await L.archive(),a=await Archive.open(file),arr=await a.getFilesArray();return arr.map(x=>({name:(x.path||'')+(x.file&&x.file.name||''),dir:!!(x.file&&x.file.isDirectory),size:x.file&&x.file.size||null}));}
async function extractArchive(file,onEntry){const kind=C.guessFileKind(file.name,file.type);if(kind==='zip'){await L.ensure(['jszip']);const z=await JSZip.loadAsync(file);const entries=[];for(const [name,obj] of Object.entries(z.files)){if(obj.dir)continue;const blob=await obj.async('blob');entries.push({name,blob});onEntry&&onEntry(name);}return entries;}const {Archive}=await L.archive(),a=await Archive.open(file),out=[];await a.extractFiles(entry=>{if(entry&&entry.file){out.push({name:(entry.path||'')+entry.file.name,blob:entry.file});onEntry&&onEntry((entry.path||'')+entry.file.name)}});return out;}
root.AtrangiTools={pdfTools,fileTools,readBytes,readDataURL,download,createDocxText,createDocxImage,extractPdfText,renderPdf,textToPdfBlob,imageToPdf,imageToSearchablePdf,mergePdf,splitPdf,rotatePdf,watermarkPdf,addPageNumbers,signPdf,compressPdf,protectPdf,unlockPdf,pdfToWord,pdfToExcel,wordToPdf,wordToImage,excelToPdf,ocrImage,imageOcrToWord,createZip,archiveList,extractArchive};
})(window);
