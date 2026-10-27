const fs=require('fs'),vm=require('vm'),assert=require('assert/strict'),crypto=require('crypto');const {parseHTML}=require(require.resolve('linkedom',{paths:[__dirname,__dirname+'/../app-tests']}));
const workflows=JSON.parse(fs.readFileSync(__dirname+'/assets/workflows.json','utf8'));
const tick=()=>new Promise(r=>setImmediate(r));
async function harness(){const {document,HTMLElement,Event}=parseHTML(fs.readFileSync(__dirname+'/assets/index.html','utf8'));HTMLElement.prototype.scrollIntoView=function(){};document.getElementById('deleteDialog').showModal=function(){this.open=true};document.getElementById('deleteDialog').close=function(){this.open=false};for(const id of ['file1','file2'])document.getElementById(id).files=[];
 const storage=new Map([['comfy-connection',JSON.stringify({address:'127.0.0.1',port:8188,protocol:'http'})]]),jobs=new Map(),sent=[],cancelled=[],cached=[];let counter=0,gate=null,cacheGate=null,failCancel=false;const pending=[];
 const context={document,HTMLElement,Event,crypto:crypto.webcrypto,console,URL,URLSearchParams,Uint32Array,Uint8Array,structuredClone,innerWidth:390,localStorage:{getItem:k=>storage.get(k)||null,setItem:(k,v)=>storage.set(k,v),removeItem:k=>storage.delete(k)},setTimeout,clearTimeout,setInterval:()=>0,clearInterval,requestAnimationFrame:()=>0,cancelAnimationFrame:()=>{},btoa:s=>Buffer.from(s,'binary').toString('base64'),fetch:async()=>({ok:true,json:async()=>structuredClone(workflows)}),addEventListener:()=>{},Android:{configure(){},reconnect(){},setCanGoBack(){},haptic(){},copy(){},call(id,action,path,body){const input=JSON.parse(body);const work=async()=>{try{let data={};if(action==='POST'){const promptId='test-'+(++counter);sent.push({id:promptId,input});jobs.set(promptId,{state:'queued'});if(gate)await gate;data={prompt_id:promptId}}
 else if(action==='GET'&&path==='/queue'){data={queue_running:[['external','external-job']],queue_pending:[...jobs].filter(x=>x[1].state==='queued').map(([id],i)=>[i,id])}}
 else if(action==='GET'&&path.startsWith('/history/')){
  const pid=path.split('/').pop(),job=jobs.get(pid);
  if(job?.state==='done'){
   const output=sent.find(x=>x.id===pid).input.prompt['50']?'50':'29';
   data={};data[pid]={status:{completed:true,status_str:'success'},outputs:{}};data[pid].outputs[output]={images:[{filename:pid+'.png',subfolder:'',type:'output'}]};
  }
 }
 else if(action==='cache'){if(cacheGate)await cacheGate;const imageId=crypto.randomUUID()+'.png';cached.push(imageId);data={id:imageId,src:'https://app.comfymobile.local/images/'+imageId}}
 else if(action==='cancelBatch'){if(failCancel)throw Error('offline');cancelled.push(...input.ids);for(const id of input.ids)jobs.delete(id);data={cancelled:true}}
 else if(action==='deleteImages')data={deleted:input.ids};else if(action==='upload')data={name:'uploaded.png',subfolder:''};
 context.nativeResult(id,data,null)}catch(e){context.nativeResult(id,null,e.message)}};pending.push(work());}}};context.window=context;vm.createContext(context);for(const file of ['app.js','image-sizing.js','batch.js','gallery.js','boot.js'])vm.runInContext(fs.readFileSync(__dirname+'/assets/'+file,'utf8'),context,{filename:file});await tick();const run=code=>vm.runInContext(code,context);run("connected=true;$('#positive').value='A red cabin';$('#negative').value='blurry';$('#width').value=256;$('#height').value=256");return {context,run,sent,jobs,cancelled,cached,document,gate:(v)=>gate=v,cacheGate:(v)=>cacheGate=v,fail:(v)=>failCancel=v,flush:async()=>{for(let i=0;i<5;i++)await tick()}}}
module.exports={harness};
