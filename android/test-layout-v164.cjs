const fs=require('fs'),path=require('path'),assert=require('assert/strict'),{spawn}=require('child_process');
(async()=>{
 const profile=path.join(__dirname,'build','layout-edge-'+process.pid);fs.mkdirSync(profile,{recursive:true});
 const browser=spawn(process.env.COMFY_TEST_CHROMIUM||'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',['--headless=new','--disable-gpu','--disable-extensions','--disable-sync','--disable-background-networking','--no-first-run','--no-default-browser-check','--remote-debugging-port=0','--user-data-dir='+profile,'about:blank'],{stdio:'ignore'});
 let launchError;browser.on('error',error=>launchError=error);let port;
 for(let n=0;n<60;n++){if(launchError)throw launchError;try{port=fs.readFileSync(path.join(profile,'DevToolsActivePort'),'utf8').split('\n')[0];break}catch{}await new Promise(r=>setTimeout(r,250))}
 if(!port)throw Error('Dedicated Chromium layout runtime did not start');
 const version=await(await fetch('http://127.0.0.1:'+port+'/json/version')).json();
 const target=(await(await fetch('http://127.0.0.1:'+port+'/json/list')).json()).find(x=>x.type==='page'&&x.url==='about:blank');
 if(!target)throw Error('Dedicated headless page missing');
 const ws=new WebSocket(target.webSocketDebuggerUrl),pending=new Map();let id=0;
 ws.onmessage=event=>{const data=JSON.parse(event.data),item=pending.get(data.id);if(item){pending.delete(data.id);data.error?item.reject(Error(JSON.stringify(data.error))):item.resolve(data.result)}};
 await new Promise((resolve,reject)=>{ws.onopen=resolve;ws.onerror=reject});
 const send=(method,params={})=>new Promise((resolve,reject)=>{const n=++id,timer=setTimeout(()=>{pending.delete(n);reject(Error('CDP timed out: '+method))},8000);pending.set(n,{resolve:value=>{clearTimeout(timer);resolve(value)},reject:error=>{clearTimeout(timer);reject(error)}});ws.send(JSON.stringify({id:n,method,params}))});
 const evaluate=async expression=>{const r=await send('Runtime.evaluate',{expression,awaitPromise:true,returnByValue:true});if(r.exceptionDetails)throw Error(JSON.stringify(r.exceptionDetails));return r.result.value};
 try{
  await send('Emulation.setDeviceMetricsOverride',{width:390,height:844,deviceScaleFactor:1,mobile:true});
  const image='data:image/svg+xml,'+encodeURIComponent('<svg xmlns="http://www.w3.org/2000/svg" width="720" height="1280"><rect width="720" height="1280" fill="teal"/></svg>');
  const base=fs.readFileSync(path.join(__dirname,'assets/index.html'),'utf8').replace(/<script[\s\S]*?<\/script>/g,'');
  const fixture=css=>base.replace('<link rel="stylesheet" href="style.css">','<style>'+css+'</style>');
  const render=async css=>{
   await evaluate('document.open();document.write('+JSON.stringify(fixture(css))+');document.close()');
   return evaluate(`(async()=>{const grid=document.getElementById('artistGrid');for(let i=0;i<40;i++){const card=document.createElement('div');card.className='artist-card';card.innerHTML=${JSON.stringify('<button class="artist-choose"><img src="'+image+'"><strong>画师 Artist '+('长名称'.repeat(4))+'</strong><small>artist tag</small></button><div class="artist-card-actions"><button>收藏</button><button>放大</button></div>')};grid.append(card)}document.getElementById('artistDialog').showModal();await Promise.all([...grid.querySelectorAll('img')].map(x=>x.decode()));const rect=e=>{const r=e.getBoundingClientRect();return {top:r.top,height:r.height,bottom:r.bottom,width:r.width}};const cards=[...grid.children].map(rect),firstImage=grid.querySelector('img');grid.scrollTop=grid.scrollHeight;return {grid:rect(grid),scrollHeight:grid.scrollHeight,scrollTop:grid.scrollTop,cards,image:rect(firstImage),objectFit:getComputedStyle(firstImage).objectFit,lastVisible:grid.lastElementChild.getBoundingClientRect().bottom<=grid.getBoundingClientRect().bottom+1}})()`);
  };
  const oldPath=path.join(__dirname,'build/style-1.6.0.css');
  if(fs.existsSync(oldPath)){const old=await render(fs.readFileSync(oldPath,'utf8'));console.log('Old layout: '+JSON.stringify({grid:old.grid,scrollHeight:old.scrollHeight,first:old.cards[0],third:old.cards[2],image:old.image}))}
  const css=fs.readFileSync(path.join(__dirname,'assets/style.css'),'utf8'),layout=await render(css);
  for(let i=2;i<layout.cards.length;i++)assert(layout.cards[i].top>=layout.cards[i-2].bottom+10,'row '+i+' must not overlap');
  assert(layout.scrollHeight>layout.grid.height*5,'40 full-height examples scroll instead of compressing');
  assert(layout.scrollTop>0&&layout.lastVisible,'last row and actions can scroll fully into view');
  assert(Math.abs(layout.image.height/layout.image.width-1280/720)<0.02,'portrait example keeps full original proportions');
  assert.equal(layout.objectFit,'contain');
  console.log('PASS: real Chromium layout, 40 complete nonoverlapping cards, uncropped previews, final row reachable. '+JSON.stringify({height:layout.cards[0].height,scrollHeight:layout.scrollHeight}));
  // A short catalog must not stretch its row to the dialog height.
  const short=await evaluate(`(()=>{const g=document.getElementById('artistGrid');[...g.children].slice(2).forEach(x=>x.remove());g.scrollTop=0;return {height:g.firstElementChild.getBoundingClientRect().height,grid:g.getBoundingClientRect().height}})()`);
  assert(Math.abs(short.height-layout.cards[0].height)<2,'one row retains natural card height');
  await evaluate('document.open();document.write('+JSON.stringify(fixture(css))+');document.close()');
  await evaluate("globalThis.$=s=>document.querySelector(s);globalThis.page='generate';globalThis.gallerySelecting=false;globalThis.dragSelection=null;globalThis.renderGallery=()=>{};globalThis.showPage=function(next){page=next;for(const name of ['generate','gallery','settings','library','translator'])$('#'+(name==='generate'?'generation':name)+'Page').hidden=name!==next};showPage('generate')");
  await evaluate(fs.readFileSync(path.join(__dirname,'assets/navigation.js'),'utf8'));
  const paging=await evaluate(`(()=>{const pageNode=document.getElementById('generationPage'),track=document.getElementById('mainPageTrack'),pager=document.getElementById('mainPager');function touch(type,x){const t=new Touch({identifier:1,target:pageNode,clientX:x,clientY:400});pageNode.dispatchEvent(new TouchEvent(type,{bubbles:true,cancelable:true,touches:type==='touchend'?[]:[t],changedTouches:[t]}))}touch('touchstart',300);touch('touchmove',260);const mid={offset:track.getBoundingClientRect().left-pager.getBoundingClientRect().left,neighbor:document.getElementById('galleryPage').getBoundingClientRect().left-pager.getBoundingClientRect().left,page,height:pager.clientHeight,width:pager.clientWidth};touch('touchend',260);const shortPage=page,transition=track.style.transition;track.style.transition='none';touch('touchstart',300);touch('touchmove',80);touch('touchend',80);return {mid,shortPage,transition,longPage:page,target:track.style.transform}})()`);
  assert.equal(paging.mid.offset,-40);assert.equal(paging.mid.neighbor,paging.mid.width-40,'neighbor page moves beside current page');assert(paging.mid.height>400);assert.equal(paging.shortPage,'generate');assert.equal(paging.longPage,'gallery');assert(paging.transition.includes('260ms'));
  const upload=await evaluate(`(()=>{const box=document.getElementById('upload1');box.closest('.uploads').hidden=false;box.querySelector('.upload-copy').hidden=true;box.classList.add('has-image');box.style.aspectRatio='1';const portrait=box.getBoundingClientRect();box.style.aspectRatio='1.77777777778';const landscape=box.getBoundingClientRect();return {portrait:{w:portrait.width,h:portrait.height},landscape:{w:landscape.width,h:landscape.height}}})()`);
  assert(Math.abs(upload.portrait.h-upload.portrait.w)<1);assert(Math.abs(upload.landscape.h/upload.landscape.w-9/16)<.01);console.log('PASS: real Chromium page-track geometry follows finger continuously, long-drag threshold, usable viewport height, upload preview adapts with a square maximum.');
  const feedback=await evaluate(`(()=>{const viewer=document.getElementById('imageViewer'),message=document.getElementById('toast');viewer.hidden=false;message.textContent='已保存到手机相册 · ComfyMobile';message.classList.add('show');message.style.transition='none';message.style.pointerEvents='auto';const r=message.getBoundingClientRect(),top=document.elementFromPoint(r.left+r.width/2,r.top+r.height/2);return {viewerOpen:!viewer.hidden,toastOnTop:top===message,z:getComputedStyle(message).zIndex}})()`);
  assert(feedback.viewerOpen&&feedback.toastOnTop,'save toast must overlay the full-screen photo');console.log('PASS: actual Chromium stacking places save toast above the open full-screen photo.');
 }finally{ws.close();await new Promise(resolve=>{const control=new WebSocket(version.webSocketDebuggerUrl);control.onopen=()=>control.send(JSON.stringify({id:1,method:'Browser.close'}));control.onmessage=()=>control.close();control.onclose=resolve;control.onerror=resolve})}
})().catch(error=>{console.error(error);process.exitCode=1});
