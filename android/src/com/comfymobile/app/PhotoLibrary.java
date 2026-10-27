package com.comfymobile.app;

import android.content.*;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import android.util.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/** One process-owned snapshot, refreshed only on a foreground entry or explicit request. */
final class PhotoLibrary {
 interface Listener {void changed(Snapshot snapshot);}
 static final class Snapshot {
  final List<AlbumIndex.Photo> photos;final List<AlbumIndex.Album> albums;final boolean ready;final String error;final int permission;
  Snapshot(List<AlbumIndex.Photo> photos,boolean ready,String error,int permission){this.photos=Collections.unmodifiableList(new ArrayList<>(photos));this.albums=Collections.unmodifiableList(AlbumIndex.group(photos));this.ready=ready;this.error=error;this.permission=permission;}
 }
 private final Context context;
 private final Handler main=new Handler(Looper.getMainLooper());
 private final ExecutorService queries=Executors.newSingleThreadExecutor(),warming=Executors.newFixedThreadPool(2);
 private final AtomicInteger revision=new AtomicInteger();
 private final Set<Listener> listeners=new HashSet<>();
 private Snapshot snapshot=new Snapshot(Collections.emptyList(),false,null,-1);
 final LruCache<String,Bitmap> thumbnails=new LruCache<String,Bitmap>(16*1024*1024){@Override protected int sizeOf(String key,Bitmap image){return image.getAllocationByteCount();}};
 PhotoLibrary(Context context){this.context=context.getApplicationContext();}
 static PhotoLibrary get(Context context){return ((ComfyApplication)context.getApplicationContext()).photos;}
 private int permission(){if(context.checkSelfPermission("android.permission.READ_MEDIA_IMAGES")==PackageManager.PERMISSION_GRANTED)return 2;return Build.VERSION.SDK_INT>=34&&context.checkSelfPermission("android.permission.READ_MEDIA_VISUAL_USER_SELECTED")==PackageManager.PERMISSION_GRANTED?1:0;}
 Snapshot peek(){int scope=permission();if(snapshot.permission!=scope){snapshot=new Snapshot(Collections.emptyList(),false,null,scope);thumbnails.evictAll();}return snapshot;}
 void listen(Listener listener){listeners.add(listener);}
 void unlisten(Listener listener){listeners.remove(listener);}
 private static String get(Cursor cursor,String column){int index=cursor.getColumnIndex(column);return index<0||cursor.isNull(index)?"":cursor.getString(index);}
 void refresh(){final int token=revision.incrementAndGet(),scope=permission();peek();if(scope==0){publish(new Snapshot(Collections.emptyList(),true,null,scope));return;}
  queries.execute(()->{ArrayList<AlbumIndex.Photo> rows=new ArrayList<>();String failure=null;try{String[] projection={MediaStore.Images.Media._ID,MediaStore.Images.Media.DISPLAY_NAME,MediaStore.Images.Media.BUCKET_ID,MediaStore.Images.Media.BUCKET_DISPLAY_NAME,MediaStore.Images.Media.RELATIVE_PATH,MediaStore.Images.Media.VOLUME_NAME,MediaStore.Images.Media.WIDTH,MediaStore.Images.Media.HEIGHT};try(Cursor cursor=context.getContentResolver().query(MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL),projection,MediaStore.Images.Media.IS_PENDING+" = 0 AND "+MediaStore.Images.Media.IS_TRASHED+" = 0",null,MediaStore.Images.Media.DATE_ADDED+" DESC, "+MediaStore.Images.Media._ID+" DESC")){if(cursor!=null)while(cursor.moveToNext()){if(token!=revision.get())return;long id=cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID));String volume=get(cursor,MediaStore.Images.Media.VOLUME_NAME);if(volume.isEmpty())volume=MediaStore.VOLUME_EXTERNAL_PRIMARY;Uri uri=ContentUris.withAppendedId(MediaStore.Images.Media.getContentUri(volume),id);rows.add(new AlbumIndex.Photo(uri.toString(),get(cursor,MediaStore.Images.Media.DISPLAY_NAME),get(cursor,MediaStore.Images.Media.BUCKET_DISPLAY_NAME),get(cursor,MediaStore.Images.Media.RELATIVE_PATH),volume,get(cursor,MediaStore.Images.Media.BUCKET_ID),cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)),cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT))));}}}catch(Exception error){failure=error.getMessage();}final Snapshot next=new Snapshot(rows,true,failure,scope);main.post(()->{if(token!=revision.get())return;if(permission()!=scope){refresh();return;}if(next.error==null||!snapshot.ready)publish(next);else publish(new Snapshot(snapshot.photos,true,next.error,scope));warmCovers(token);});});
 }
 private void publish(Snapshot next){snapshot=next;for(Listener listener:new ArrayList<>(listeners))listener.changed(next);}
 private void warmCovers(int token){if(snapshot.permission==0)return;float density=context.getResources().getDisplayMetrics().density;int screen=context.getResources().getDisplayMetrics().widthPixels,albumWidth=Math.max(Math.round(70*density),(screen-Math.round(37*density))/2),photoWidth=Math.max(Math.round(70*density),(screen-Math.round(46*density))/3);for(int i=0;i<Math.min(12,snapshot.albums.size());i++)warm(snapshot.albums.get(i).photos.get(0),albumWidth,token);for(int i=0;i<Math.min(18,snapshot.photos.size());i++)warm(snapshot.photos.get(i),photoWidth,token);}
 private void warm(AlbumIndex.Photo photo,int width,int token){String key=photo.uri+"@"+width;if(thumbnails.get(key)!=null)return;warming.execute(()->{if(token!=revision.get())return;try{Bitmap image=context.getContentResolver().loadThumbnail(Uri.parse(photo.uri),new Size(width,width),null);if(token==revision.get())thumbnails.put(key,image);}catch(Exception ignored){}});}
}
