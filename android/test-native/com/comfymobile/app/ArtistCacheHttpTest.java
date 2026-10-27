package com.comfymobile.app;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;

public final class ArtistCacheHttpTest {
 static final class Source implements ArtistCache.Source {final String origin;int downloads;Source(String origin){this.origin=origin;}InputStream open(String route)throws Exception{final HttpURLConnection connection=(HttpURLConnection)new URL(origin+route).openConnection();connection.setConnectTimeout(5000);connection.setReadTimeout(20000);if(connection.getResponseCode()!=200){connection.disconnect();throw new IOException("HTTP request failed");}return new FilterInputStream(connection.getInputStream()){@Override public void close()throws IOException{try{super.close();}finally{connection.disconnect();}}};}public JSONArray catalog()throws Exception{try(InputStream in=open("/anima_artist_gallery/catalog")){ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] b=new byte[8192];int n;while((n=in.read(b))>=0)bytes.write(b,0,n);return new JSONArray(new String(bytes.toByteArray(),StandardCharsets.UTF_8));}}public InputStream image(String path)throws Exception{downloads++;return open(path);}}
 public static void main(String[] args)throws Exception{ArtistCache cache=new ArtistCache(Files.createTempDirectory("comfy-artist-http-").toFile());Source source=new Source(args.length>0?args[0]:"http://127.0.0.1:8188");JSONObject state=cache.sync(source.origin,source,snapshot->{});int cached=state.getInt("cached");if(cached<1||cached!=state.getInt("total"))throw new AssertionError("Not all available examples cached");int requests=source.downloads;cache.sync(source.origin,source,snapshot->{});if(source.downloads!=requests)throw new AssertionError("Unchanged previews downloaded twice");long bytes=state.getLong("bytes");cache.clear();if(cache.state().getInt("cached")!=0)throw new AssertionError("Cache clear failed");System.out.println("PASS: actual ComfyUI artist API -> native cache: "+cached+" PNG previews, "+bytes+" bytes; unchanged images reused; clear verified");}
}
