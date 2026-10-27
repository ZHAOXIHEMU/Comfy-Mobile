package com.comfymobile.app;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.Color;
import android.content.pm.PackageManager;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.provider.MediaStore;
import android.view.*;
import android.widget.FrameLayout;
import android.webkit.*;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;
import org.json.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import okhttp3.*;

public class MainActivity extends Activity {
 private static final String ORIGIN="https://app.comfymobile.local/";
 private WebView web;
 private ValueCallback<Uri[]> chooser;
 private final ExecutorService workers=Executors.newFixedThreadPool(4);
 private final OkHttpClient client=new OkHttpClient.Builder().connectTimeout(12,TimeUnit.SECONDS).readTimeout(90,TimeUnit.SECONDS).writeTimeout(120,TimeUnit.SECONDS).pingInterval(20,TimeUnit.SECONDS).build();
 private final AtomicInteger epoch=new AtomicInteger();
 private volatile String base="",token="",clientId="";
 private volatile WebSocket socket;
 private volatile boolean connecting=false,destroyed=false;
 private boolean backRegistered=false;private ArtistCache artistExamples;
 private final OnBackInvokedCallback backCallback=()->web.evaluateJavascript("window.handleSystemBack&&handleSystemBack()",null);

 @Override public void onCreate(Bundle state){super.onCreate(state);getWindow().setStatusBarColor(Color.rgb(16,17,18));getWindow().setNavigationBarColor(Color.rgb(16,17,18));getWindow().setDecorFitsSystemWindows(false);
  FrameLayout content=new FrameLayout(this);content.setBackgroundColor(Color.rgb(16,17,18));
  web=new WebView(this);web.setBackgroundColor(Color.rgb(16,17,18));content.addView(web,new FrameLayout.LayoutParams(-1,-1));setContentView(content);
  content.setOnApplyWindowInsetsListener((v,insets)->{android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout()|WindowInsets.Type.ime());v.setPadding(bars.left,bars.top,bars.right,bars.bottom);return WindowInsets.CONSUMED;});content.requestApplyInsets();
  WebSettings settings=web.getSettings();settings.setJavaScriptEnabled(true);settings.setDomStorageEnabled(true);settings.setAllowFileAccess(false);settings.setAllowContentAccess(true);settings.setMediaPlaybackRequiresUserGesture(true);settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);settings.setSupportZoom(false);settings.setBuiltInZoomControls(false);settings.setDisplayZoomControls(false);
  web.addJavascriptInterface(new Bridge(),"Android");
  web.setWebViewClient(new WebViewClient(){
   @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest r){return !r.getUrl().toString().startsWith(ORIGIN);}
   @Override public WebResourceResponse shouldInterceptRequest(WebView v,WebResourceRequest r){Uri u=r.getUrl();if(!"app.comfymobile.local".equals(u.getHost()))return blocked();try{String p=u.getPath();if(p==null||p.contains(".."))return blocked();InputStream stream;String mime;
     if(p.startsWith("/images/")){String id=p.substring(8);if(!id.matches("[a-f0-9\\-]+\\.png"))return blocked();stream=new FileInputStream(new File(imageDir(),id));mime="image/png";}
     else if(p.startsWith("/artist-previews/")){stream=new FileInputStream(artistCache().file(p.substring(17)));mime="image/png";}
     else{if(p.equals("/"))p="/index.html";stream=getAssets().open(p.substring(1));mime=p.endsWith(".js")?"application/javascript":p.endsWith(".css")?"text/css":p.endsWith(".json")?"application/json":"text/html";}
     return new WebResourceResponse(mime,"UTF-8",200,"OK",Collections.singletonMap("Cache-Control","no-cache"),stream);
    }catch(Exception e){return blocked();}}
  });
  web.setWebChromeClient(new WebChromeClient(){@Override public boolean onShowFileChooser(WebView v,ValueCallback<Uri[]> callback,FileChooserParams params){if(chooser!=null)chooser.onReceiveValue(null);chooser=callback;boolean image=false;for(String accept:params.getAcceptTypes())if(accept!=null&&accept.startsWith("image/"))image=true;try{if(image){launchImagePicker();}else{Intent intent=params.createIntent();intent.setAction(Intent.ACTION_OPEN_DOCUMENT);intent.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(intent,41);}return true;}catch(Exception e){chooser=null;callback.onReceiveValue(null);return true;}}});
  web.loadUrl(ORIGIN);
  try{if(BatchStore.open(getFilesDir()).hasWork())startForegroundService(new Intent(this,NativeBatchService.class));}catch(Exception e){event("connectionError","后台任务恢复失败："+e.getMessage());}
 }
 private WebResourceResponse blocked(){return new WebResourceResponse("text/plain","UTF-8",404,"Not Found",Collections.emptyMap(),new ByteArrayInputStream(new byte[0]));}
 private synchronized ArtistCache artistCache()throws Exception{if(artistExamples==null)artistExamples=new ArtistCache(getFilesDir());return artistExamples;}
 private void launchImagePicker(){startActivityForResult(new Intent(this,ImagePickerActivity.class),42);}
 private File imageDir(){File f=new File(getFilesDir(),"images");f.mkdirs();return f;}
 private void js(String script){if(!destroyed)runOnUiThread(()->{if(!destroyed)web.evaluateJavascript(script,null);});}
 private String json(Object data){return data==null?"null":data instanceof JSONObject||data instanceof JSONArray?data.toString():JSONObject.quote(data.toString());}
 private void event(String type,Object data){js("window.nativeEvent&&nativeEvent("+JSONObject.quote(type)+","+json(data)+")");}
 private void result(String id,Object data,String error){js("window.nativeResult&&nativeResult("+JSONObject.quote(id)+","+json(data)+","+(error==null?"null":JSONObject.quote(error))+")");}
 private Request.Builder request(String path){if(base.isEmpty())throw new IllegalStateException("请先填写并保存服务器地址");Request.Builder b=new Request.Builder().url(base+path);if(!token.isEmpty())b.header("Authorization","Bearer "+token);return b;}
 private String checked(Response r)throws IOException{String text=r.body()==null?"":r.body().string();if(!r.isSuccessful())throw new IOException("HTTP "+r.code()+" "+text.substring(0,Math.min(text.length(),1400)));return text;}
 private void connect(){if(connecting||base.isEmpty()||socket!=null)return;connecting=true;int version=epoch.get();event("connection","connecting");workers.execute(()->{try{
   try(Response r=client.newCall(request("/system_stats").build()).execute()){new JSONObject(checked(r)).getJSONObject("system");}
   if(version!=epoch.get()||destroyed){connecting=false;return;}
   Request req=request("/ws?clientId="+Uri.encode(clientId)).build();
   socket=client.newWebSocket(req,new WebSocketListener(){
    @Override public void onOpen(WebSocket ws,Response r){if(version!=epoch.get()){ws.close(1000,"changed");return;}connecting=false;event("connection","connected");}
    @Override public void onMessage(WebSocket ws,String message){if(version!=epoch.get())return;try{event("comfy",new JSONObject(message));}catch(Exception ignored){}}
    @Override public void onFailure(WebSocket ws,Throwable t,Response r){if(version!=epoch.get())return;connecting=false;socket=null;event("connection","offline");}
    @Override public void onClosed(WebSocket ws,int c,String reason){if(version!=epoch.get())return;connecting=false;socket=null;event("connection","offline");}
   });
  }catch(Exception e){if(version==epoch.get()){connecting=false;socket=null;event("connection","offline");event("connectionError",e.getMessage());}}});}

 public class Bridge{
  @JavascriptInterface public int batchVersion(){return 1;}
  @JavascriptInterface public void configure(String address,String bearer,String cid){try{HttpUrl url=HttpUrl.parse(address);if(url==null||!(url.scheme().equals("http")||url.scheme().equals("https"))||!url.username().isEmpty()||!url.password().isEmpty()||!url.encodedPath().equals("/")||url.query()!=null||url.fragment()!=null)throw new Exception("服务器地址格式无效");String next=url.toString().replaceAll("/$","");if(!next.equals(base)||!bearer.equals(token)||!cid.equals(clientId)){epoch.incrementAndGet();if(socket!=null)socket.cancel();socket=null;connecting=false;base=next;token=bearer;clientId=cid;}connect();}catch(Exception e){event("connectionError",e.getMessage());event("connection","offline");}}
  @JavascriptInterface public void reconnect(){connect();}
  @JavascriptInterface public void setCanGoBack(boolean can){runOnUiThread(()->{if(Build.VERSION.SDK_INT>=33){if(can&&!backRegistered){getOnBackInvokedDispatcher().registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT,backCallback);backRegistered=true;}else if(!can&&backRegistered){getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(backCallback);backRegistered=false;}}});}
  @JavascriptInterface public void copy(String text){runOnUiThread(()->((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("提示词",text)));}
  @JavascriptInterface public void haptic(){runOnUiThread(()->web.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS));}
  @JavascriptInterface public void openPhotoPermissions(){runOnUiThread(()->startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName()))));}
  @JavascriptInterface public void call(String id,String action,String path,String body){workers.execute(()->{try{JSONObject input=body.isEmpty()?new JSONObject():new JSONObject(body);Object output;
    if(action.equals("artistState")){output=artistCache().state();}
    else if(action.equals("artistClear")){output=artistCache().clear();}
    else if(action.equals("artistSync")){final String server=base,bearer=token;if(server.isEmpty())throw new Exception("请先连接 ComfyUI");output=artistCache().sync(server,new ArtistCache.Source(){private Request.Builder api(String route){Request.Builder request=new Request.Builder().url(server+route);if(!bearer.isEmpty())request.header("Authorization","Bearer "+bearer);return request;}public JSONArray catalog()throws Exception{try(Response response=client.newCall(api("/anima_artist_gallery/catalog").build()).execute()){return new JSONArray(checked(response));}}public InputStream image(String route)throws Exception{final Response response=client.newCall(api(route).build()).execute();if(!response.isSuccessful()||response.body()==null){response.close();throw new IOException("例图下载失败");}return new FilterInputStream(response.body().byteStream()){@Override public void close()throws IOException{try{super.close();}finally{response.close();}}};}},state->event("artistSync",state));}
    else if(action.equals("batchState")){output=BatchStore.open(getFilesDir()).snapshot();}
    else if(action.equals("batchStart")){JSONObject job=input.getJSONObject("job");job.put("server",base).put("bearer",token).put("clientId",clientId).put("remotePreferred",true);String batchId=job.getJSONObject("batch").getString("id");if(!batchId.matches("[a-zA-Z0-9\\-]+"))throw new Exception("批次编号无效");for(int n=1;n<=2;n++){JSONObject upload=input.optJSONObject("upload"+n);if(upload!=null){byte[] data=android.util.Base64.decode(upload.getString("data"),android.util.Base64.DEFAULT);if(data.length>30*1024*1024)throw new Exception("图片请控制在 30 MB 内");File dir=new File(getFilesDir(),"batch-inputs");dir.mkdirs();String relative="batch-inputs/"+batchId+"-"+n;try(FileOutputStream out=new FileOutputStream(new File(getFilesDir(),relative))){out.write(data);out.getFD().sync();}job.put("upload"+n,new JSONObject().put("path",relative).put("name",upload.getString("name")).put("mime",upload.optString("mime","image/png")));}}BatchStore store=BatchStore.open(getFilesDir());store.start(job);try{launchBatchService();}catch(Exception e){job.put("task","error").put("error","后台服务启动失败："+e.getMessage());store.save(job);throw e;}output=store.snapshot();}
    else if(action.equals("batchControl")){BatchStore store=BatchStore.open(getFilesDir());store.control(input.getString("mode"),input.getString("id"),input.getString("command"));if(store.hasWork())launchBatchService();boolean pending=false;try{NativeBatchService.controlRemote(store.job(input.getString("mode")));}catch(Exception unavailable){pending=true;}output=store.snapshot().put("controlPending",pending);}
    else if(action.equals("deleteImages")){JSONArray ids=input.getJSONArray("ids"),deleted=new JSONArray();for(int i=0;i<ids.length();i++){String file=ids.getString(i);if(!file.matches("[a-f0-9\\-]+\\.png"))throw new Exception("图片编号无效");File target=new File(imageDir(),file);if(!target.exists()||target.delete())deleted.put(file);}BatchStore store=BatchStore.open(getFilesDir());store.deleteImages(deleted);output=new JSONObject().put("deleted",deleted).put("snapshot",store.snapshot());}
    else if(action.equals("cancelBatch")){JSONArray ids=input.getJSONArray("ids");for(int i=0;i<ids.length();i++)if(!ids.getString(i).matches("[a-zA-Z0-9\\-]+"))throw new Exception("任务编号无效");if(ids.length()>0){try(Response r=client.newCall(request("/queue").post(RequestBody.create(MediaType.parse("application/json"),new JSONObject().put("delete",ids).toString())).build()).execute()){checked(r);}for(int i=0;i<ids.length();i++){try(Response r=client.newCall(request("/interrupt").post(RequestBody.create(MediaType.parse("application/json"),new JSONObject().put("prompt_id",ids.getString(i)).toString())).build()).execute()){checked(r);}}}output=new JSONObject().put("cancelled",true);}
    else if(action.equals("export")){String file=input.getString("id");if(!file.matches("[a-f0-9\\-]+\\.png"))throw new Exception("图片编号无效");ContentValues values=new ContentValues();values.put(MediaStore.Images.Media.DISPLAY_NAME,"ComfyMobile_"+System.currentTimeMillis()+".png");values.put(MediaStore.Images.Media.MIME_TYPE,"image/png");values.put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/ComfyMobile");values.put(MediaStore.Images.Media.IS_PENDING,1);Uri uri=getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values);if(uri==null)throw new IOException("无法保存图片");try(InputStream in=new FileInputStream(new File(imageDir(),file));OutputStream out=getContentResolver().openOutputStream(uri)){copyBytes(in,out);}catch(Exception e){getContentResolver().delete(uri,null,null);throw e;}values.clear();values.put(MediaStore.Images.Media.IS_PENDING,0);getContentResolver().update(uri,values,null,null);output=new JSONObject().put("saved",true);}
    else if(action.equals("cache")){if(!path.startsWith("/view?"))throw new Exception("图片地址无效");String file=UUID.randomUUID()+".png";File target=new File(imageDir(),file);try(Response r=client.newCall(request(path).build()).execute()){if(!r.isSuccessful()||r.body()==null)throw new IOException("下载图片失败 HTTP "+r.code());try(InputStream in=r.body().byteStream();OutputStream out=new FileOutputStream(target)){copyBytes(in,out);}}catch(Exception e){target.delete();throw e;}BitmapFactory.Options dimensions=new BitmapFactory.Options();dimensions.inJustDecodeBounds=true;BitmapFactory.decodeFile(target.getAbsolutePath(),dimensions);output=new JSONObject().put("id",file).put("src",ORIGIN+"images/"+file).put("width",dimensions.outWidth).put("height",dimensions.outHeight);}
    else if(action.equals("upload")){byte[] bytes=android.util.Base64.decode(input.getString("data"),android.util.Base64.DEFAULT);if(bytes.length>30*1024*1024)throw new IOException("图片请控制在 30 MB 内");RequestBody multipart=new MultipartBody.Builder().setType(MultipartBody.FORM).addFormDataPart("image",input.getString("name"),RequestBody.create(MediaType.parse(input.optString("mime","image/png")),bytes)).addFormDataPart("type","input").addFormDataPart("overwrite","false").build();try(Response r=client.newCall(request("/upload/image").post(multipart).build()).execute()){output=new JSONObject(checked(r));}}
    else{if(!(path.equals("/system_stats")||path.equals("/queue")||path.equals("/prompt")||path.matches("/history/[a-zA-Z0-9\\-]+")))throw new Exception("接口路径无效");Request.Builder req=request(path);if(action.equals("POST")){if(!path.equals("/prompt"))throw new Exception("请求不允许");req.post(RequestBody.create(MediaType.parse("application/json; charset=utf-8"),body));}else if(!action.equals("GET"))throw new Exception("请求方法无效");try(Response r=client.newCall(req.build()).execute()){output=new JSONObject(checked(r));}}
    result(id,output,null);
   }catch(Exception e){result(id,null,e.getMessage()==null?e.toString():e.getMessage());}});}
 }
 private void launchBatchService()throws Exception{FutureTask<Void> start=new FutureTask<>(()->{if(checkSelfPermission("android.permission.POST_NOTIFICATIONS")!=PackageManager.PERMISSION_GRANTED&&!getPreferences(MODE_PRIVATE).getBoolean("askedNotifications",false)){getPreferences(MODE_PRIVATE).edit().putBoolean("askedNotifications",true).apply();requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"},54);}startForegroundService(new Intent(this,NativeBatchService.class));return null;});runOnUiThread(start);start.get(10,TimeUnit.SECONDS);}
 private static void copyBytes(InputStream in,OutputStream out)throws IOException{byte[] bytes=new byte[16384];int n;while((n=in.read(bytes))!=-1)out.write(bytes,0,n);}
 @Override protected void onActivityResult(int request,int code,Intent data){super.onActivityResult(request,code,data);if((request==41||request==42)&&chooser!=null){chooser.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(code,data));chooser=null;}}
 @Override public void onBackPressed(){web.evaluateJavascript("window.handleSystemBack?handleSystemBack():false",value->{if(!"true".equals(value))super.onBackPressed();});}
 @Override protected void onResume(){super.onResume();if(web!=null){web.onResume();js("window.onNativeResume&&onNativeResume()");}}
 @Override protected void onDestroy(){destroyed=true;epoch.incrementAndGet();if(socket!=null)socket.cancel();if(chooser!=null)chooser.onReceiveValue(null);workers.shutdownNow();if(web!=null){web.removeJavascriptInterface("Android");web.destroy();}super.onDestroy();}
}
