package com.comfymobile.app;

import java.util.*;

/** Groups only public MediaStore rows; equal album names never merge distinct folders. */
final class AlbumIndex {
 static final class Photo {
  final String uri,name,albumName,relativePath,volume,bucketId;
  final int width,height;
  Photo(String uri,String name,String albumName,String path,String volume,String bucketId,int width,int height){this.uri=uri;this.name=name==null?"":name;this.relativePath=path==null?"":path;this.volume=volume==null?"external_primary":volume;this.bucketId=bucketId==null?"":bucketId;this.albumName=albumName==null||albumName.isEmpty()?folderName(this.relativePath):albumName;this.width=width;this.height=height;}
  String albumKey(){return volume+"\u0000"+relativePath+"\u0000"+bucketId;}
 }
 static final class Album {
  final String key,name,path,volume;
  final ArrayList<Photo> photos=new ArrayList<>();
  Album(Photo p){key=p.albumKey();name=p.albumName;path=p.relativePath;volume=p.volume;}
 }
 static List<Album> group(List<Photo> photos){LinkedHashMap<String,Album> albums=new LinkedHashMap<>();for(Photo p:photos){Album a=albums.get(p.albumKey());if(a==null){a=new Album(p);albums.put(a.key,a);}a.photos.add(p);}return new ArrayList<>(albums.values());}
 static String folderName(String path){String value=path==null?"":path.replaceAll("/+$","");int slash=value.lastIndexOf('/');return value.isEmpty()?"未命名相册":value.substring(slash+1);}
 static boolean matches(String text,String query){return text.toLowerCase(Locale.ROOT).contains(query.trim().toLowerCase(Locale.ROOT));}
 static List<Album> filterAlbums(List<Album> albums,String query){ArrayList<Album> result=new ArrayList<>();for(Album a:albums)if(matches(a.name+" "+a.path,query))result.add(a);return result;}
 static List<Photo> filterPhotos(List<Photo> photos,String query){if(query.trim().isEmpty())return photos;ArrayList<Photo> result=new ArrayList<>();for(Photo p:photos)if(matches(p.name,query))result.add(p);return result;}
 static List<Album> pinnedFirst(List<Album> albums,Set<String> pinned){ArrayList<Album> result=new ArrayList<>(albums);result.sort((a,b)->Boolean.compare(pinned.contains(b.key),pinned.contains(a.key)));return result;}
 // 0: no selection, 1: all unpinned, 2: all pinned, 3: mixed.
 static int pinAction(Set<String> selected,Set<String> pinned){int result=0;for(String key:selected)result|=pinned.contains(key)?2:1;return result;}
}
