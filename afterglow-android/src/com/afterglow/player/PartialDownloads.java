package com.afterglow.player;
import android.content.Context;import java.io.File;import java.util.*;
/** Retain up to 300 MiB of inactive partials for seven days, separate from playable audio. */
final class PartialDownloads {
 static File[] folders(Context c){File[] f=c.getCacheDir().listFiles(x->x.isDirectory()&&x.getName().matches("audio-download-[A-Za-z0-9_-]{11}"));return f==null?new File[0]:f;}
 static long size(File d){long n=0;File[] fs=d.listFiles();if(fs!=null)for(File f:fs)if(f.isFile())n+=f.length();return n;}
 static void erase(File d){File[] fs=d.listFiles();if(fs!=null)for(File f:fs)if(f.isFile())f.delete();d.delete();}
 static void remove(Context c,String id){if(id!=null&&id.matches("[A-Za-z0-9_-]{11}"))erase(new File(c.getCacheDir(),"audio-download-"+id));}
 static void clear(Context c){for(File d:folders(c)){String id=d.getName().substring(15);if(!id.equals(DownloadService.trackId)&&!PlaybackService.protectedCacheIds.contains(id))erase(d);}}
 static void prune(Context c,String active){File[] dirs=folders(c);Arrays.sort(dirs,Comparator.comparingLong(File::lastModified));long total=0;for(File d:dirs)total+=size(d);for(File d:dirs)if(!d.getName().equals("audio-download-"+active)&&(total>300L*1024*1024||System.currentTimeMillis()-d.lastModified()>7L*86400000)){long n=size(d);erase(d);total-=n;}}
}
