package com.comfymobile.app;

import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.*;

/** The example-image cache is separate from generated images and user favorites. */
final class ArtistCache {
 interface Source {JSONArray catalog()throws Exception;InputStream image(String path)throws Exception;}
 interface Listener {void changed(JSONObject state);}
 private final File directory,index;private JSONObject manifest;private long revision;private final AtomicBoolean syncing=new AtomicBoolean();private final AtomicInteger epoch=new AtomicInteger();
 ArtistCache(File files)throws Exception{directory=new File(files,"artist-examples");directory.mkdirs();index=new File(directory,"index.json");manifest=index.exists()?new JSONObject(new String(Files.readAllBytes(index.toPath()),StandardCharsets.UTF_8)):new JSONObject().put("server","").put("rows",new JSONArray());revision=manifest.optLong("revision");}
 File file(String name)throws IOException{if(!name.matches("[a-f0-9\\-]+\\.png"))throw new IOException("例图编号无效");return new File(directory,name);}
 private void save()throws Exception{manifest.put("revision",++revision);File part=new File(directory,"index.tmp");try(FileOutputStream out=new FileOutputStream(part)){out.write(manifest.toString().getBytes(StandardCharsets.UTF_8));out.getFD().sync();}Files.move(part.toPath(),index.toPath(),StandardCopyOption.REPLACE_EXISTING);}
 synchronized JSONObject state()throws Exception{JSONObject result=new JSONObject(manifest.toString());JSONArray rows=result.getJSONArray("rows");int cached=0,total=0;long bytes=0;for(int i=0;i<rows.length();i++){JSONObject row=rows.getJSONObject(i);if(row.optBoolean("available"))total++;File image=row.has("file")?file(row.getString("file")):null;if(image!=null&&image.isFile()&&image.length()>0){row.put("src","https://app.comfymobile.local/artist-previews/"+image.getName());cached++;bytes+=image.length();}else row.remove("src");}return result.put("cached",cached).put("total",total).put("bytes",bytes).put("syncing",syncing.get());}
 synchronized JSONObject clear()throws Exception{epoch.incrementAndGet();File[] files=directory.listFiles();if(files!=null)for(File item:files)if(item.isFile()&&(item.getName().matches("[a-f0-9\\-]+\\.png(?:\\.part)?")||item.getName().equals("index.tmp")))if(!item.delete()&&item.exists())throw new IOException("部分例图未能删除，请重试");manifest=new JSONObject().put("server", "").put("rows",new JSONArray()).put("cleared",true);save();return state();}
 JSONObject sync(String server,Source source,Listener listener)throws Exception{if(!syncing.compareAndSet(false,true))return state();int generation=epoch.get();try{JSONArray catalog=source.catalog(),rows=new JSONArray();for(int i=0;i<catalog.length();i++){JSONObject entry=catalog.getJSONObject(i);String id=entry.getString("id");if(!id.matches("[a-zA-Z0-9_-]{1,80}"))throw new IOException("画师编号无效");String version=entry.optString("preview_version","0");boolean available=!entry.isNull("preview_url")&&!entry.optString("preview_url").isEmpty();if(available&&!entry.getString("preview_url").equals("/anima_artist_gallery/preview/"+id))throw new IOException("例图路径无效");String name=UUID.nameUUIDFromBytes((server+"|"+id+"|"+version).getBytes(StandardCharsets.UTF_8))+".png";JSONObject row=new JSONObject().put("id",id).put("name",entry.getString("name")).put("label",entry.getString("label")).put("option",entry.getString("option")).put("pinned",entry.optBoolean("pinned")).put("version",version).put("available",available);if(available)row.put("file",name);rows.put(row);}
   synchronized(this){if(generation!=epoch.get())return state();manifest=new JSONObject().put("server",server).put("rows",rows);save();}listener.changed(state());
   for(int i=0;i<rows.length();i++){JSONObject row=rows.getJSONObject(i);if(!row.optBoolean("available"))continue;File target=file(row.getString("file")),part=new File(directory,target.getName()+".part");synchronized(this){if(generation!=epoch.get())return state();if(target.isFile()&&target.length()>0)continue;}
    try{try(InputStream in=source.image("/anima_artist_gallery/preview/"+row.getString("id"));FileOutputStream out=new FileOutputStream(part)){byte[] buffer=new byte[16384];long length=0;int n;while((n=in.read(buffer))!=-1){if(generation!=epoch.get())return state();length+=n;if(length>30L*1024*1024)throw new IOException("例图文件过大");out.write(buffer,0,n);}out.getFD().sync();}try(InputStream header=new FileInputStream(part)){byte[] magic=new byte[8];if(header.read(magic)!=8||!Arrays.equals(magic,new byte[]{(byte)137,80,78,71,13,10,26,10}))throw new IOException("例图下载不完整");}synchronized(this){if(generation!=epoch.get())return state();Files.move(part.toPath(),target.toPath(),StandardCopyOption.REPLACE_EXISTING);save();}listener.changed(state());}finally{part.delete();}
   }
   synchronized(this){Set<String> keep=new HashSet<>();for(int i=0;i<rows.length();i++)if(rows.getJSONObject(i).has("file"))keep.add(rows.getJSONObject(i).getString("file"));if(generation==epoch.get()){File[] files=directory.listFiles();if(files!=null)for(File cached:files)if(cached.getName().matches("[a-f0-9\\-]+\\.png")&&!keep.contains(cached.getName()))cached.delete();}}
   return state();
  }finally{syncing.set(false);listener.changed(state());}}
}
