// Execute the production PreviewPager with lightweight UI/decoder stand-ins, without UI clicks.
const fs=require('fs'),path=require('path'),{spawnSync}=require('child_process');
const project=__dirname,source=fs.readFileSync(path.join(project,'src/com/comfymobile/app/ImagePickerActivity.java'),'utf8');
const begin=source.indexOf(' private final class PreviewPager'),end=source.indexOf(' private void goBack()',begin);
if(begin<0||end<0)throw Error('PreviewPager source missing');
const directory=path.join(project,'build/preview-tests');fs.mkdirSync(directory,{recursive:true});
const program=`package com.comfymobile.app;
import java.util.*;
class ImagePickerActivity {
 boolean destroyed=false;final Work queryWorker=new Work();final ArrayDeque<Runnable> ui=new ArrayDeque<>();
 int dp(float n){return Math.round(n);}Object getContentResolver(){return this;}void runOnUiThread(Runnable r){ui.add(r);}void drainUi(){while(!ui.isEmpty())ui.remove().run();}void drainLoads(){while(!queryWorker.tasks.isEmpty()){queryWorker.tasks.remove().run();drainUi();}}
 static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
 static class Work {final ArrayDeque<Runnable> tasks=new ArrayDeque<>();void execute(Runnable task){tasks.add(task);}}
 static class Drawable {final String uri;Drawable(String uri){this.uri=uri;}}
 static class Bitmap {final String uri;Bitmap(String uri){this.uri=uri;}}
 static class Color {static final int BLACK=0;}
 static class Uri {final String value;Uri(String v){value=v;}static Uri parse(String v){return new Uri(v);}}
 static class ImageDecoder {
  static final int ALLOCATOR_SOFTWARE=1;static final Map<String,Integer> decodes=new HashMap<>();
  interface Listener {void onHeader(ImageDecoder decoder,Info info,Object source);}
  static class Size {int getWidth(){return 1600;}int getHeight(){return 1000;}}
  static class Info {Size getSize(){return new Size();}}
  void setAllocator(int allocator){}void setTargetSampleSize(int size){}
  static Uri createSource(Object resolver,Uri uri){return uri;}
  static Bitmap decodeBitmap(Uri uri,Listener listener){decodes.put(uri.value,decodes.getOrDefault(uri.value,0)+1);listener.onHeader(new ImageDecoder(),new Info(),uri);return new Bitmap(uri.value);}
 }
 static class Dialog {boolean showing=true;boolean isShowing(){return showing;}void setTitle(String title){}}
 static class ImageView {
  enum ScaleType {FIT_CENTER}Object tag;Drawable drawable;
  ImageView(Object context){}void setScaleType(ScaleType t){}void setPadding(int a,int b,int c,int d){}void setLayoutParams(Object p){}
  Object getTag(){return tag;}void setTag(Object value){tag=value;}Drawable getDrawable(){return drawable;}void setImageDrawable(Drawable value){drawable=value;}void setImageBitmap(Bitmap bitmap){drawable=new Drawable(bitmap.uri);}
 }
 static class LinearLayout {
  static final int HORIZONTAL=0;static class LayoutParams {LayoutParams(int width,int height){}}
  LinearLayout(Object context){}void setOrientation(int direction){}void addView(ImageView image,LayoutParams params){}
 }
 class HorizontalScrollView {
  static final int OVER_SCROLL_NEVER=2;int scrollX;
  class LayoutParams {LayoutParams(int width,int height){}}
  HorizontalScrollView(Object context){}void setBackgroundColor(int color){}void setHorizontalScrollBarEnabled(boolean b){}void setOverScrollMode(int mode){}void setFillViewport(boolean b){}void addView(LinearLayout row,LayoutParams p){}
  int getWidth(){return 390;}int getScrollX(){return scrollX;}void post(Runnable r){ui.add(r);}void scrollTo(int x,int y){int previous=scrollX;scrollX=x;if(previous!=x)onScrollChanged(x,y,previous,0);}protected void onScrollChanged(int x,int y,int oldX,int oldY){}public boolean onTouchEvent(MotionEvent event){return true;}
 }
 static class MotionEvent {
  static final int ACTION_DOWN=0,ACTION_UP=1,ACTION_CANCEL=3;int action;float x;long time;
  static MotionEvent obtain(MotionEvent source){MotionEvent copy=new MotionEvent();copy.action=source.action;copy.x=source.x;copy.time=source.time;return copy;}
  int getActionMasked(){return action;}float getX(){return x;}long getEventTime(){return time;}void setAction(int value){action=value;}void recycle(){}
 }
 static class Animator {}
 static class AnimatorListenerAdapter {public void onAnimationEnd(Animator animation){}}
 static class ValueAnimator extends Animator {
  interface UpdateListener {void update(ValueAnimator animation);}int start,end,value;UpdateListener update;AnimatorListenerAdapter listener;
  static ValueAnimator ofInt(int from,int to){ValueAnimator a=new ValueAnimator();a.start=from;a.end=to;a.value=from;return a;}
  void setDuration(int duration){}void addUpdateListener(UpdateListener l){update=l;}void addListener(AnimatorListenerAdapter l){listener=l;}void start(){}Integer getAnimatedValue(){return value;}
  void frame(float portion){value=Math.round(start+(end-start)*portion);if(update!=null)update.update(this);}void finish(){frame(1);if(listener!=null)listener.onAnimationEnd(this);}void removeAllListeners(){listener=null;}void removeAllUpdateListeners(){update=null;}void cancel(){}
 }
${source.slice(begin,end).replace('AlertDialog dialog;','Dialog dialog;')}
 static ArrayList<AlbumIndex.Photo> photos(){ArrayList<AlbumIndex.Photo> list=new ArrayList<>();for(int i=0;i<8;i++)list.add(new AlbumIndex.Photo("content://photo/"+i,"Photo "+i,"Test","Pictures/Test/","external","1",720,480));return list;}
 PreviewPager pager(int index){PreviewPager p=new PreviewPager(photos(),index);p.dialog=new Dialog();p.bind();drainUi();return p;}
 void finish(PreviewPager p,int step){p.settle(step);p.settleAnimation.finish();drainUi();}
 static String uri(int index){return "content://photo/"+index;}
 static void visible(PreviewPager p,int index){check(p.pages[1].getDrawable()!=null,"center must never be cleared after settle");check(p.pages[1].getDrawable().uri.equals(uri(index)),"center matches selected photo");check(p.getScrollX()==p.width,"recenter happens together with rebinding");}
 public static void main(String[] args){
  ImagePickerActivity host=new ImagePickerActivity();PreviewPager p=host.pager(2);host.drainLoads();visible(p,2);Drawable right=p.pages[2].getDrawable(),center=p.pages[1].getDrawable();int loaded=ImageDecoder.decodes.size();
  p.settle(1);p.settleAnimation.frame(.5f);check(p.pages[1].getDrawable()==center&&p.pages[2].getDrawable()==right,"drag keeps both existing image surfaces");p.settleAnimation.finish();visible(p,3);check(p.pages[1].getDrawable()==right,"new center reuses preloaded neighbor immediately, before any queued decode");host.drainUi();visible(p,3);check(host.queryWorker.tasks.size()==1,"only new outer neighbor requires decode");host.drainLoads();check(ImageDecoder.decodes.size()==loaded+1,"retained pictures are not decoded again");
  center=p.pages[1].getDrawable();host.finish(p,0);visible(p,3);check(p.pages[1].getDrawable()==center&&host.queryWorker.tasks.isEmpty(),"short-drag rollback never clears or reloads current photo");
  host.finish(p,-1);visible(p,2);host.drainLoads();for(int i=3;i<8;i++){Drawable next=p.pages[2].getDrawable();host.finish(p,1);visible(p,i);check(p.pages[1].getDrawable()==next,"repeated forward switches retain neighbor");host.drainLoads();}for(int i=6;i>=0;i--){host.finish(p,-1);visible(p,i);host.drainLoads();}
  ImageDecoder.decodes.clear();host=new ImagePickerActivity();p=host.pager(2);check(host.queryWorker.tasks.size()==3,"bounded initial decode window");host.finish(p,1);check(host.queryWorker.tasks.size()==4,"pending URI loads are coalesced across slot changes");host.drainLoads();visible(p,3);check(ImageDecoder.decodes.values().stream().allMatch(n->n==1),"pending callbacks follow URI rather than obsolete page slot/generation");
  p.settle(-1);ValueAnimator pending=p.settleAnimation;p.dialog.showing=false;p.dispose();pending.finish();check(p.current==3,"dismissal cancels pending settle without rebinding a closed preview");
  System.out.println("PASS: production preview retains bitmaps across left/right switches and rollback, preloads only new neighbors, coalesces pending loads by URI, recenters on animation completion and cancels on dismissal");
 }
}
`;
const testFile=path.join(directory,'ImagePickerActivity.java');fs.writeFileSync(testFile,program,'utf8');
const javaBin=path.join(process.env.JAVA_HOME||'C:/Program Files/Java/jdk-21.0.10','bin');
function run(exe,args){const r=spawnSync(path.join(javaBin,exe),args,{encoding:'utf8'});if(r.status!==0)throw Error(r.stdout+'\n'+r.stderr);process.stdout.write(r.stdout)}
run('javac.exe',['-J-Duser.language=en','-J-Dfile.encoding=UTF-8','-encoding','UTF-8','-d',directory,path.join(project,'src/com/comfymobile/app/AlbumIndex.java'),testFile]);
run('java.exe',['-Dfile.encoding=UTF-8','-cp',directory,'com.comfymobile.app.ImagePickerActivity']);
