(function(root){'use strict';
const DB='atrangi-doc-scanner-v3',STORE='documents';
function open(){return new Promise((res,rej)=>{const r=indexedDB.open(DB,1);r.onupgradeneeded=()=>{if(!r.result.objectStoreNames.contains(STORE))r.result.createObjectStore(STORE,{keyPath:'id'})};r.onsuccess=()=>res(r.result);r.onerror=()=>rej(r.error)})}
async function put(doc){const db=await open();return new Promise((res,rej)=>{const tx=db.transaction(STORE,'readwrite');tx.objectStore(STORE).put(doc);tx.oncomplete=()=>res(doc);tx.onerror=()=>rej(tx.error)})}
async function all(){const db=await open();return new Promise((res,rej)=>{const r=db.transaction(STORE).objectStore(STORE).getAll();r.onsuccess=()=>res(r.result||[]);r.onerror=()=>rej(r.error)})}
async function remove(id){const db=await open();return new Promise((res,rej)=>{const tx=db.transaction(STORE,'readwrite');tx.objectStore(STORE).delete(id);tx.oncomplete=()=>res();tx.onerror=()=>rej(tx.error)})}
async function clear(){const db=await open();return new Promise((res,rej)=>{const tx=db.transaction(STORE,'readwrite');tx.objectStore(STORE).clear();tx.oncomplete=()=>res();tx.onerror=()=>rej(tx.error)})}
root.AtrangiStorage={open,put,all,remove,clear};
})(window);
