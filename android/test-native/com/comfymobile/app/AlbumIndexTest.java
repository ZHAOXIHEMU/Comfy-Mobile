package com.comfymobile.app;
import java.util.*;
public class AlbumIndexTest {
 static AlbumIndex.Photo p(String id,String name,String album,String path,String volume,String bucket){return new AlbumIndex.Photo("content://media/"+volume+"/images/media/"+id,name,album,path,volume,bucket,720,480);}
 static void check(boolean pass,String message){if(!pass)throw new AssertionError(message);}
 public static void main(String[] args){
  List<AlbumIndex.Photo> photos=Arrays.asList(
   p("10","初音.png","我创建的分类","Pictures/我创建的分类/","external_primary","100"),
   p("9","PHOTO_02.JPG","QQ图片","Pictures/QQ/","external_primary","200"),
   p("8","另一张.jpg","我创建的分类","Pictures/我创建的分类/","external_primary","100"),
   p("7","external.jpg","我创建的分类","DCIM/我创建的分类/","external_primary","300"),
   p("6","sd.jpg","我创建的分类","Pictures/我创建的分类/","1234-5678","100"),
   p("5","wechat.jpg","","Pictures/微信/","external_primary","400"));
  List<AlbumIndex.Album> groups=AlbumIndex.group(photos);
  check(groups.size()==5,"same names in different folders/volumes must not merge");
  check(groups.get(0).photos.size()==2,"custom local category groups its media rows");
  check(groups.get(0).photos.get(0).name.equals("初音.png"),"query order preserved for album cover");
  check(groups.get(4).name.equals("微信"),"missing display name falls back to path");
  check(AlbumIndex.filterAlbums(groups,"我创建").size()==3,"Chinese category search");
  check(AlbumIndex.filterAlbums(groups,"pictures/qq").size()==1,"case-insensitive path search");
  check(AlbumIndex.filterPhotos(photos,"photo_02").size()==1,"case-insensitive filename search");
  check(AlbumIndex.filterAlbums(groups,"不存在的相册").isEmpty(),"empty search result");
  check(AlbumIndex.group(Collections.emptyList()).isEmpty(),"denied/empty library handled");
  Set<String> pins=new HashSet<>(Arrays.asList(groups.get(2).key,groups.get(4).key));List<AlbumIndex.Album> pinned=AlbumIndex.pinnedFirst(groups,pins);check(pinned.get(0)==groups.get(2)&&pinned.get(1)==groups.get(4)&&pinned.get(2)==groups.get(0),"multiple pins sorted first, stable within sections");
  check(AlbumIndex.pinAction(Collections.emptySet(),pins)==0,"no selection");check(AlbumIndex.pinAction(new HashSet<>(Arrays.asList(groups.get(0).key,groups.get(1).key)),pins)==1,"all unpinned supports pin");check(AlbumIndex.pinAction(pins,pins)==2,"all pinned supports unpin");check(AlbumIndex.pinAction(new HashSet<>(Arrays.asList(groups.get(0).key,groups.get(2).key)),pins)==3,"mixed selection disables action");pins.clear();check(AlbumIndex.pinnedFirst(groups,pins).equals(groups),"unpin restores natural order");
  ArrayList<AlbumIndex.Photo> many=new ArrayList<>();for(int i=0;i<20000;i++)many.add(p(String.valueOf(i),"photo-"+i+".jpg","相册"+(i%80),"Pictures/相册"+(i%80)+"/","external_primary",String.valueOf(i%80)));
  List<AlbumIndex.Album> large=AlbumIndex.group(many);check(large.size()==80&&large.get(0).photos.size()==250,"large library grouping");
  System.out.println("PASS: custom category grouping, folder/volume separation, Chinese names, cover ordering, filename/path search, empty library and 20000-photo library");
 }
}
