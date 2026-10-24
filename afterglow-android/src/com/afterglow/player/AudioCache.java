package com.afterglow.player;

import android.content.Context;
import java.io.*;
import java.util.*;

/** Completed files only are exposed to playback. Partials remain in a separate directory. */
public final class AudioCache {
 private static volatile Set<String> statusIds=Collections.emptySet();private static volatile long nextScan;private static final java.util.concurrent.atomic.AtomicBoolean scanning=new java.util.concurrent.atomic.AtomicBoolean();
 public static boolean cachedForStatus(Context c,String id){long now=android.os.SystemClock.elapsedRealtime();if(now>=nextScan&&scanning.compareAndSet(false,true)){nextScan=now+2000;Context app=c.getApplicationContext();new Thread(()->{try{Set<String> ids=new HashSet<>();File[] files=directory(app).listFiles();if(files!=null)for(File f:files){String name=f.getName();if(name.matches("[A-Za-z0-9_-]{11}\\.(m4a|opus|mp3|ogg|aac|flac|wav|webm)")&&f.length()>0)ids.add(name.substring(0,11));}statusIds=Collections.unmodifiableSet(ids);}finally{scanning.set(false);}},"cache-status").start();}return id!=null&&!id.isEmpty()&&statusIds.contains(id);}

 public static boolean remove(Context c,String id){File f=find(c,id);return f==null||f.delete();}
 public static final long LIMIT=3L*1024*1024*1024;
 private static boolean protectedFile(File f){for(String id:PlaybackService.protectedCacheIds)if(!id.isEmpty()&&f.getName().startsWith(id+"."))return true;return false;}
 public static File directory(Context c){File d=new File(c.getCacheDir(),"music");d.mkdirs();return d;}
 public static File find(Context c,String id){if(id==null||!id.matches("[A-Za-z0-9_-]{11}"))return null;File[] files=directory(c).listFiles();if(files!=null)for(File f:files)if(f.getName().matches(java.util.regex.Pattern.quote(id)+"\\.(m4a|opus|mp3|ogg|aac|flac|wav|webm)")&&f.length()>0)return f;return null;}
 public static long size(Context c){long total=0;File[] files=directory(c).listFiles();if(files!=null)for(File f:files)if(f.isFile())total+=f.length();return total;}
 public static void trim(Context c,File preserve,long reserve){File[] files=directory(c).listFiles();if(files==null)return;Arrays.sort(files,Comparator.comparingLong(File::lastModified));long size=size(c);for(File f:files){if(size+reserve<=LIMIT)break;if(f.isFile()&&!f.equals(preserve)&&!protectedFile(f)){long bytes=f.length();if(f.delete())size-=bytes;}}}
 public static int clear(Context c,File preserve){int count=0;File[] files=directory(c).listFiles();if(files!=null)for(File f:files)if(f.isFile()&&!f.equals(preserve)&&!protectedFile(f)&&f.delete())count++;return count;}
}
