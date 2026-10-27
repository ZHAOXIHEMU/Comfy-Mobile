package com.comfymobile.app;

import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class RemoteBatchHttpTest {
 static final class Http implements BatchEngine.Transport {
  final String origin;int cached;Http(String origin){this.origin=origin;}
  JSONObject request(String path,JSONObject body)throws Exception{HttpURLConnection c=(HttpURLConnection)new URL(origin+path).openConnection();c.setConnectTimeout(5000);c.setReadTimeout(10000);try{if(body!=null){c.setRequestMethod("POST");c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json");try(OutputStream out=c.getOutputStream()){out.write(body.toString().getBytes(StandardCharsets.UTF_8));}}if(c.getResponseCode()!=200)throw new IOException("HTTP "+c.getResponseCode()+" "+path);try(InputStream in=c.getInputStream()){ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] buf=new byte[4096];int n;while((n=in.read(buf))>=0)bytes.write(buf,0,n);return new JSONObject(new String(bytes.toByteArray(),StandardCharsets.UTF_8));}}finally{c.disconnect();}}
  public JSONObject get(JSONObject j,String path)throws Exception{return request(path,null);}
  public JSONObject post(JSONObject j,String path,JSONObject body)throws Exception{return request(path,body);}
  public String upload(JSONObject j,JSONObject input){return input.optString("name","first.png");}
  public JSONObject cache(JSONObject j,String path,String id)throws Exception{HttpURLConnection c=(HttpURLConnection)new URL(origin+path).openConnection();try(InputStream in=c.getInputStream()){ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] b=new byte[4096];int n;while((n=in.read(b))>=0)bytes.write(b,0,n);byte[] png=bytes.toByteArray();if(png.length<24||png[0]!=(byte)137||png[1]!=80)throw new IOException("invalid PNG");}finally{c.disconnect();}cached++;return new JSONObject().put("id",id).put("src","images/"+id).put("width",2).put("height",2);}
 }
 static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
 static void tick(BatchEngine engine)throws Exception{JSONObject j=engine.store.job("text");j.put("retryAt",0);engine.store.save(j);engine.tick("text");}
 public static void main(String[] args)throws Exception{JSONObject workflows=new JSONObject(new String(Files.readAllBytes(Paths.get("assets/workflows.json")),StandardCharsets.UTF_8));Http http=new Http(args[0]);BatchStore store=new BatchStore(Files.createTempDirectory("comfy-http-").resolve("jobs.json").toFile());JSONObject j=BatchEngineTest.job(4,"text","text");j.put("remotePreferred",true);store.start(j);BatchEngine engine=new BatchEngine(store,http,workflows);tick(engine);String id=j.getJSONObject("batch").getString("id");Thread.sleep(3200);JSONObject pc=http.get(j,"/comfy-mobile/batches/"+id+"?owner=unit");check(pc.getString("state").equals("done")&&pc.getInt("completed")==4,"all images must finish with no native engine or phone polling");tick(engine);check(store.job("text").getString("task").equals("done")&&http.cached==4,"every background result arrives through real HTTP routes");
  j=BatchEngineTest.job(32,"text","text");j.put("remotePreferred",true);store.start(j);id=j.getJSONObject("batch").getString("id");tick(engine);Thread.sleep(1100);store.control("text",id,"pause");tick(engine);Thread.sleep(700);tick(engine);check(store.job("text").getString("task").equals("paused"),"HTTP pause after current image");int completed=store.job("text").getJSONObject("batch").getInt("completed");Thread.sleep(1100);pc=http.get(j,"/comfy-mobile/batches/"+id+"?owner=unit");check(pc.getInt("completed")==completed,"paused computer does not submit further images");store.control("text",id,"resume");tick(engine);Thread.sleep(700);store.control("text",id,"cancel");tick(engine);Thread.sleep(700);tick(engine);pc=http.get(j,"/comfy-mobile/batches/"+id+"?owner=unit");check(pc.getString("state").equals("cancelled"),"HTTP cancel cancels remainder");int stopped=pc.getInt("completed");Thread.sleep(1100);pc=http.get(j,"/comfy-mobile/batches/"+id+"?owner=unit");check(pc.getInt("completed")==stopped&&stopped<32,"no remaining jobs after cancellation");
  System.out.println("PASS: production Android batch engine and production computer extension communicate through real HTTP; computer finishes while phone is completely idle; all images downloaded; pause/resume/whole-batch cancel confirmed");
 }
}
