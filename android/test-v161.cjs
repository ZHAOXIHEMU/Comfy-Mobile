const fs=require('fs'),vm=require('vm'),assert=require('assert/strict'),{harness}=require('./test-harness.cjs');
(async()=>{
 const h=await harness();vm.runInContext(fs.readFileSync(__dirname+'/assets/navigation.js','utf8'),h.context,{filename:'navigation.js'});
 const el=id=>h.document.getElementById(id),point=(x,y)=>({clientX:x,clientY:y});
 function touch(target,type,points,changed=points){const e=new h.context.Event(type,{bubbles:true,cancelable:true});Object.defineProperty(e,'touches',{value:points});Object.defineProperty(e,'changedTouches',{value:changed});target.dispatchEvent(e);return e}
 function swipe(target,x1,y1,x2,y2){touch(target,'touchstart',[point(x1,y1)]);touch(target,'touchmove',[point(x2,y2)]);touch(target,'touchend',[],[point(x2,y2)])}
 h.run("mode='anime';renderMode()");
 swipe(el('generationPage'),300,250,80,252);assert.equal(h.run('page'),'gallery');assert.equal(h.run('mode'),'anime');
 swipe(el('galleryPage'),300,250,80,252);assert.equal(h.run('page'),'settings');
 swipe(el('settingsPage'),300,250,80,252);assert.equal(h.run('page'),'settings','last main page must not wrap');
 swipe(el('settingsPage'),80,250,300,252);assert.equal(h.run('page'),'gallery');
 swipe(el('galleryPage'),80,250,300,252);assert.equal(h.run('page'),'generate');
 swipe(el('generationPage'),80,250,300,252);assert.equal(h.run('page'),'generate','first main page must not wrap');
 swipe(el('generationPage'),150,250,155,450);assert.equal(h.run('page'),'generate','vertical scrolling must not navigate');
 swipe(el('generationPage'),300,250,270,251);assert.equal(h.run('page'),'generate','short drags must not navigate');
 swipe(el('positive'),300,250,80,252);assert.equal(h.run('page'),'generate','text selection/editing does not navigate');
 swipe(el('batchSlider'),300,250,80,252);assert.equal(h.run('page'),'generate','batch controls do not navigate');
 swipe(el('generationPage'),3,250,200,252);assert.equal(h.run('page'),'generate','system back edge is reserved');
 h.run("showPage('library')");swipe(el('libraryPage'),300,250,80,252);assert.equal(h.run('page'),'library','settings subpages are outside the pager');
 h.run("showPage('gallery');setSelecting(true)");swipe(el('galleryPage'),300,250,80,252);assert.equal(h.run('page'),'gallery','selection gestures do not navigate');
 h.run("setSelecting(false);showPage('generate')");
 touch(el('generationPage'),'touchstart',[point(280,200)]);touch(el('generationPage'),'touchmove',[point(200,200),point(250,200)]);touch(el('generationPage'),'touchend',[],[point(80,200)]);assert.equal(h.run('page'),'generate','a multi-touch gesture cannot become page navigation');
 assert(touch(el('generationPage'),'touchmove',[point(100,200),point(200,200)]).defaultPrevented,'main-page pinch cannot scale the app');
 el('artistDialog').open=true;swipe(el('artistGrid'),300,250,80,252);assert.equal(h.run('page'),'generate','artist picker owns its gestures');el('artistDialog').open=false;
 h.run("gallery=Array.from({length:3},(_,i)=>({id:'image-'+i,src:'image'+i,mode:'text',created:1,width:1000,height:1000}));openImage(gallery[1],gallery)");
 const stage=el('viewerStage'),pager=el('viewerPager');Object.defineProperty(pager,'clientWidth',{value:390,configurable:true});
 for(const slide of pager.children){slide.getBoundingClientRect=()=>({left:0,top:0,width:390,height:600});Object.defineProperty(slide.firstElementChild,'naturalWidth',{value:1000});Object.defineProperty(slide.firstElementChild,'naturalHeight',{value:1000})}
 h.run('positionViewer(viewerIndex)');const current=pager.children[1].firstElementChild,src=current.src;
 assert(touch(stage,'touchstart',[point(145,300),point(245,300)]).defaultPrevented);
 touch(stage,'touchmove',[point(95,300),point(295,300)]);assert.equal(h.run('viewerZoom.scale'),2);assert(pager.classList.contains('zoom-active'));assert.equal(h.run('viewerIndex'),1);assert.equal(h.run('viewerInfoOpen'),false);assert.equal(h.run('moveViewer(1)'),false);
 touch(stage,'touchend',[point(95,300)],[point(295,300)]);touch(stage,'touchmove',[point(135,330)]);assert.equal(h.run('viewerZoom.x'),40);assert.equal(h.run('viewerZoom.y'),30);touch(stage,'touchend',[],[point(135,330)]);
 swipe(stage,130,300,350,450);assert.equal(h.run('viewerZoom.x'),188,'panning clamps to the actual fitted image');assert.equal(h.run('viewerZoom.y'),76);assert.equal(h.run('viewerInfoOpen'),false,'zoom-pan must not open metadata');assert.equal(h.run('page'),'generate','viewer gestures never navigate main pages');assert.equal(current.src,src,'zoom/pan retains decoded image');
 touch(stage,'touchstart',[point(95,300),point(295,300)]);touch(stage,'touchmove',[point(170,300),point(220,300)]);touch(stage,'touchend',[],[point(170,300),point(220,300)]);assert.equal(h.run('viewerZoom.scale'),1);assert.equal(h.run('viewerZoom.x'),0);assert.equal(h.run('viewerZoom.y'),0);assert(!pager.classList.contains('zoom-active'));
 touch(stage,'touchstart',[point(300,300)]);const nativeMove=touch(stage,'touchmove',[point(100,302)]);assert.equal(nativeMove.defaultPrevented,false,'1x keeps continuous native paging');pager.scrollLeft=2*h.run('viewerStride()');pager.dispatchEvent(new h.context.Event('scroll'));touch(stage,'touchend',[],[point(100,302)]);assert.equal(h.run('viewerIndex'),2);assert.equal(h.run('viewerZoom.scale'),1);
 swipe(stage,150,500,150,300);assert.equal(h.run('viewerInfoOpen'),true);touch(stage,'touchstart',[point(145,300),point(245,300)]);touch(stage,'touchmove',[point(95,300),point(295,300)]);touch(stage,'touchend',[],[point(95,300),point(295,300)]);assert.equal(h.run('viewerZoom.scale'),1,'metadata mode is not zoomed');assert.equal(h.run('viewerIndex'),2);
 h.run('closeImage();openImage(gallery[0],[gallery[0]])');assert.equal(h.run('viewerZoom.scale'),1,'result and gallery share clean viewer state');assert.equal(h.run('viewerInfoOpen'),false);
 assert(h.document.querySelector('[data-mode="anime"] svg.miku-icon'));assert(!h.document.querySelector('[data-mode="anime"]').textContent.includes('✦'));
 assert(h.document.querySelector('meta[name="viewport"]').getAttribute('content').includes('user-scalable=no'));
 console.log('PASS: bounded main-page swipes, mode preservation, editor/slider/selection/dialog isolation, disabled app zoom, photo pinch/pan/bounds/reset, continuous paging restoration, info gesture isolation, Miku icon.');process.exit(0);
})().catch(error=>{console.error(error);process.exit(1)});
