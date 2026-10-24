package com.afterglow.player;

import android.app.*;import android.content.*;import android.os.*;
import java.util.*;import java.util.concurrent.*;

/** One worker, a replaceable look-ahead plan, and no index-based autoplay callbacks. */
public class DownloadService extends Service {
 public static volatile boolean busy=false;
 public static volatile String status="",trackId="";
 private static volatile String lastPlan="";
 private final ArrayDeque<String> pending=new ArrayDeque<>();
 private AudioDownloader downloader;private ExecutorService executor;private Handler main;
 private String active;private boolean obsolete,cancelled,destroyed;private PowerManager.WakeLock lock;

 static void schedule(Context context,List<String> videos){
  String signature=String.join(",",videos);
  if(signature.equals(lastPlan)||(videos.isEmpty()&&!busy))return;
  lastPlan=signature;
  Intent intent=new Intent(context,DownloadService.class).setAction("plan").putStringArrayListExtra("videos",new ArrayList<>(videos));
  try{context.startForegroundService(intent);}catch(Exception e){lastPlan="";PlaybackService p=PlaybackService.instance;if(p!=null)p.error="Open Afterglow to prepare upcoming audio.";}
 }
 public IBinder onBind(Intent i){return null;}
 public void onCreate(){super.onCreate();main=new Handler(getMainLooper());executor=Executors.newSingleThreadExecutor();getSystemService(NotificationManager.class).createNotificationChannel(new NotificationChannel("downloads","Audio downloads",NotificationManager.IMPORTANCE_LOW));}
 Notification notification(){
  PendingIntent cancel=PendingIntent.getService(this,91,new Intent(this,DownloadService.class).setAction("cancel"),PendingIntent.FLAG_IMMUTABLE);
  return new Notification.Builder(this,"downloads").setSmallIcon(R.drawable.icon).setContentTitle("Afterglow · preparing queue").setContentText(status).setOngoing(true).setContentIntent(PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE)).addAction(android.R.drawable.ic_menu_close_clear_cancel,"Cancel",cancel).build();
 }
 public int onStartCommand(Intent intent,int flags,int startId){
  if(intent==null){stopSelf();return START_NOT_STICKY;}
  if("cancel".equals(intent.getAction())){
   cancelled=true;pending.clear();lastPlan="";status="Background preparation cancelled";
   PlaybackService p=PlaybackService.instance;if(p!=null)p.cancelPreparation();
   if(downloader!=null)downloader.cancel();else finishIdle();return START_NOT_STICKY;
  }
  startForeground(2,notification());
  ArrayList<String> requested=intent.getStringArrayListExtra("videos");
  if(requested==null){requested=new ArrayList<>();String id=intent.getStringExtra("video");if(id!=null)requested.add(id);}
  cancelled=false;pending.clear();LinkedHashSet<String> ids=new LinkedHashSet<>(requested);
  for(String id:ids)if(id.matches("[A-Za-z0-9_-]{11}")&&!id.equals(active)&&AudioCache.find(this,id)==null)pending.add(id);
  if(active!=null&&!ids.contains(active)){obsolete=true;if(downloader!=null)downloader.cancel();}
  if(active==null)startNext();return START_NOT_STICKY;
 }
 private void startNext(){
  if(destroyed)return;
  while(!pending.isEmpty()&&AudioCache.find(this,pending.peek())!=null)pending.remove();
  if(pending.isEmpty()){finishIdle();return;}
  active=pending.remove();final String id=active;trackId=id;busy=true;obsolete=false;
  status="Preparing upcoming audio…";getSystemService(NotificationManager.class).notify(2,notification());
  if(lock==null)lock=((PowerManager)getSystemService(POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"Afterglow:download");
  if(!lock.isHeld())lock.acquire(11*60*1000L);
  downloader=new AudioDownloader(this);final AudioDownloader worker=downloader;
  executor.execute(()->{
   String failure=null;
   try{worker.download(id,message->main.post(()->{if(!destroyed&&id.equals(active)&&!obsolete&&!cancelled){status=message;getSystemService(NotificationManager.class).notify(2,notification());}}));}
   catch(Exception e){failure=e.getMessage()==null?"Audio download failed":e.getMessage();}
   final String result=failure;
   main.post(()->{
    if(destroyed)return;
    if(PlaybackService.deletedCacheIds.contains(id))AudioCache.remove(this,id);boolean ignored=obsolete||cancelled||PlaybackService.deletedCacheIds.contains(id);active=null;trackId="";downloader=null;lastPlan="";
    if(lock!=null&&lock.isHeld())lock.release();
    if(!ignored){status=result==null?"Cached · upcoming audio ready":result;PlaybackService p=PlaybackService.instance;if(p!=null)p.onDownloadResult(id,result);}
    startNext();
   });
  });
 }
 private void finishIdle(){busy=false;trackId="";lastPlan="";if(lock!=null&&lock.isHeld())lock.release();stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();}
 public void onTimeout(int id,int type){cancelled=true;if(downloader!=null)downloader.cancel();status="Background download timed out";PlaybackService p=PlaybackService.instance;if(p!=null)p.cancelPreparation();stopSelf();}
 public void onDestroy(){destroyed=true;if(downloader!=null)downloader.cancel();if(executor!=null)executor.shutdownNow();if(lock!=null&&lock.isHeld())lock.release();busy=false;lastPlan="";super.onDestroy();}
}
