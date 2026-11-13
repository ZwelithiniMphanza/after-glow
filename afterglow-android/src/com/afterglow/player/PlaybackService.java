package com.afterglow.player;
import android.app.*;import android.content.*;import android.media.*;import android.media.audiofx.*;import android.media.session.*;import android.net.Uri;import android.os.*;import org.json.*;import java.util.*;
public class PlaybackService extends Service {
 public static PlaybackService instance; public final LibraryCollections collections=new LibraryCollections(); public final ArrayList<Track> backlog=new ArrayList<>(); public boolean repeatAll=false; public final ArrayList<Track> queue=new ArrayList<>();public int index=0; public String error="";public boolean ready=false,repeat=false,eqEnabled=true,limit=true;public float boost=0; boolean ended=false; final ArrayList<Track> history=new ArrayList<>(); VolumeShaper fadeOut,fadeIn; public float preamp=-6,balance=0;public final float[] gains=new float[10];public final float[] cutoffs={44,88,177,354,707,1414,2828,5657,11314,22000};
 int restorePosition=-1;long lastCheckpoint;
 void checkpoint(){if(ready&&player!=null)prefs.edit().putString("resumeTrack",current().id).putInt("resumePosition",ended?0:position()).apply();}
 MediaPlayer player;DynamicsProcessing dsp; MediaSession session;AudioManager manager;AudioFocusRequest focus;boolean resumeOnFocus=false;public String dspStatus="Play a local track to activate processing"; android.content.SharedPreferences prefs;
 final Handler main=new Handler();final java.util.concurrent.ExecutorService resolver=java.util.concurrent.Executors.newSingleThreadExecutor();
 final Set<Track> resolving=new HashSet<>(),resolveFailed=new HashSet<>();final Set<String> failedDownloads=new HashSet<>();
 public static final Set<String> deletedCacheIds=java.util.concurrent.ConcurrentHashMap.newKeySet(); public static volatile Set<String> protectedCacheIds=Collections.emptySet();
 public final ArrayList<Track> upcoming=new ArrayList<>(); public static final int QUEUE_LIMIT=10; float fade=0; boolean crossfading=false; long fadeStarted,fadeDuration=12000; final Track emptyTrack=new Track("No song selected","Add music to begin","","local"); String searchKey="";Track waitingTrack,nextTrack;MediaPlayer nextPlayer;boolean nextPrepared=false,wantPlayback=false,prefetchSuspended=false,destroyed=false;
 public class LocalBinder extends Binder{PlaybackService get(){return PlaybackService.this;}} private final IBinder binder=new LocalBinder();
 public IBinder onBind(Intent i){return binder;}
 public void onCreate(){super.onCreate();instance=this;prefs=getSharedPreferences("player",0);searchKey=KeyStore.load(this);main.postDelayed(crossfadeTick,100);for(int i=0;i<10;i++)gains[i]=prefs.getFloat("gain"+i,0);boost=prefs.getFloat("boost",0);preamp=prefs.getFloat("preamp",-6);balance=prefs.getFloat("balance",0);eqEnabled=prefs.getBoolean("eq",true);limit=prefs.getBoolean("limit",true);repeat=prefs.getBoolean("repeat",false);repeatAll=!repeat&&prefs.getBoolean("repeatAll",false);try{JSONArray a=new JSONArray(prefs.getString("queue","[]"));for(int i=0;i<a.length();i++)queue.add(Track.from(a.getJSONObject(i)));}catch(Exception ignored){}if(queue.isEmpty()&&!prefs.contains("queue"))queue.add(new Track("Midnight Drift","Afterglow · original demo","demo","local"));index=Math.max(0,Math.min(prefs.getInt("index",0),queue.size()-1));try{JSONArray saved=new JSONArray(prefs.getString("upcoming","[]"));for(int n=0;n<saved.length()&&upcoming.size()<QUEUE_LIMIT;n++){int at=saved.getInt(n);if(at>=0&&at<queue.size()&&!upcoming.contains(queue.get(at)))upcoming.add(queue.get(at));}}catch(Exception ignored){}collections.load(prefs.getString("playlists","[]"));try{JSONArray rest=new JSONArray(prefs.getString("backlog","[]"));for(int n=0;n<rest.length();n++){int at=rest.getInt(n);if(at>=0&&at<queue.size()&&!backlog.contains(queue.get(at))&&!upcoming.contains(queue.get(at)))backlog.add(queue.get(at));}}catch(Exception ignored){}save();manager=(AudioManager)getSystemService(AUDIO_SERVICE);focus=new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()).setOnAudioFocusChangeListener(change->{if(change==AudioManager.AUDIOFOCUS_GAIN){applyEQ();if(resumeOnFocus){resumeOnFocus=false;resume();}}else if(change==AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK){if(player!=null)player.setVolume(.15f,.15f);}else {resumeOnFocus=change==AudioManager.AUDIOFOCUS_LOSS_TRANSIENT&&playing();pause(false);}}).build();session=new MediaSession(this,"Afterglow");session.setCallback(new MediaSession.Callback(){public void onPlay(){resume();}public void onPause(){pause(true);}public void onSkipToNext(){next(1);}public void onSkipToPrevious(){next(-1);}public void onSeekTo(long p){seek((int)p);}public void onStop(){pause(true);stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();}});session.setSessionActivity(PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE));session.setActive(true);getSystemService(NotificationManager.class).createNotificationChannel(new NotificationChannel("playback","Music playback",NotificationManager.IMPORTANCE_LOW));registerReceiver(noisy,new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY));}
 final BroadcastReceiver noisy=new BroadcastReceiver(){public void onReceive(Context c,Intent i){pause(true);}};
 public int onStartCommand(Intent i,int flags,int id){if(i!=null){String a=i.getAction();if("pause".equals(a))pause(true);else if("next".equals(a))next(1);else if("prev".equals(a))next(-1);else if("stop".equals(a)){pause(true);stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();}else if("play".equals(a))resume();}return START_NOT_STICKY;}
 public Track current(){return queue.isEmpty()?emptyTrack:queue.get(Math.max(0,Math.min(index,queue.size()-1)));} public boolean playing(){try{return ready&&player!=null&&player.isPlaying();}catch(Exception e){return false;}}public int position(){try{return ready?player.getCurrentPosition():0;}catch(Exception e){return 0;}} public int duration(){try{return ready?player.getDuration():0;}catch(Exception e){return 0;}} public int audioSession(){return player!=null&&ready?player.getAudioSessionId():0;}
 public void save(){try{JSONArray a=new JSONArray();for(Track t:queue)a.put(t.json());JSONArray next=new JSONArray();for(Track t:upcoming)next.put(queue.indexOf(t));JSONArray rest=new JSONArray();for(Track t:backlog)rest.put(queue.indexOf(t));prefs.edit().putString("playlists",collections.json()).putBoolean("repeat",repeat).putBoolean("repeatAll",repeatAll).putString("backlog",rest.toString()).putString("queue",a.toString()).putString("upcoming",next.toString()).putInt("index",index).apply();}catch(Exception ignored){}}
 public void setSearchKey(String key){searchKey=key==null?"":key;KeyStore.save(this,searchKey);resolveFailed.clear();refreshUpcoming();}
 public void play(int idx){restorePosition=-1;playInternal(idx);}
 private void playInternal(int idx){
  if(idx<0||idx>=queue.size())return;
  Track target=queue.get(idx);deletedCacheIds.remove(target.uri);wantPlayback=true;prefetchSuspended=false;error="";
  failedDownloads.remove(target.uri);resolveFailed.remove(target);
  if(!available(target)){
   waitingTrack=target;releaseNext();
   startService(new Intent(this,PlaybackService.class));startForeground(1,notification());
   refreshUpcoming();update();return;
  }
  if(current()!=target&&ready){history.add(current());if(history.size()>100)history.remove(0);}ended=false;waitingTrack=null;index=idx;upcoming.remove(target);backlog.remove(target);fillUpcoming();releasePlayer();
  startService(new Intent(this,PlaybackService.class));startForeground(1,notification());
  try{
   player=createPlayer(target,0);final MediaPlayer loading=player;
   loading.setOnPreparedListener(p->{if(p!=player)return;ready=true;setupDSP();if(restorePosition>0){int offset=Math.min(restorePosition,Math.max(0,duration()-1));restorePosition=-1;p.setOnSeekCompleteListener(done->{done.setOnSeekCompleteListener(null);if(done==player&&wantPlayback)resume();});p.seekTo(offset);}else if(wantPlayback)resume();save();refreshUpcoming();});
   loading.prepareAsync();
  }catch(Exception e){error="Cannot open this file. Re-add it or download it again.";releasePlayer();wantPlayback=false;stopForeground(STOP_FOREGROUND_REMOVE);}
  update();
 }
 private boolean available(Track t){return "local".equals(t.kind)||AudioCache.find(this,t.uri)!=null;}
 private MediaPlayer createPlayer(Track track,int sharedSession)throws Exception{
  MediaPlayer p=new MediaPlayer();
  try{
   if(sharedSession>0)p.setAudioSessionId(sharedSession);
   p.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());
   p.setWakeMode(this,PowerManager.PARTIAL_WAKE_LOCK);
   if("demo".equals(track.uri)){try(android.content.res.AssetFileDescriptor fd=getAssets().openFd("demo.wav")){p.setDataSource(fd.getFileDescriptor(),fd.getStartOffset(),fd.getLength());}}
   else {java.io.File cached=AudioCache.find(this,track.uri);if(cached!=null){cached.setLastModified(System.currentTimeMillis());p.setDataSource(cached.getAbsolutePath());}else p.setDataSource(this,Uri.parse(track.uri));}
   p.setVolume(balance>0?1-balance:1,balance<0?1+balance:1);
   p.setOnCompletionListener(this::completed);
   p.setOnErrorListener((bad,what,extra)->{
    if(bad==nextPlayer){releaseNext();error="Upcoming audio could not be prepared.";return true;}
    if(bad==player){error="Unable to play this file. Re-add it or try another track.";wantPlayback=false;releasePlayer();update();stopForeground(STOP_FOREGROUND_REMOVE);}return true;
   });return p;
  }catch(Exception e){p.release();throw e;}
 }
 private void completed(MediaPlayer ended){
  if(ended!=player)return;
  if(!wantPlayback){pause(true);return;}
  if(waitingTrack!=null&&queue.contains(waitingTrack)){play(queue.indexOf(waitingTrack));return;}
  if(repeat){seek(0);resume();return;}
  if(repeatAll&&!backlog.contains(current())&&!upcoming.contains(current()))backlog.add(current());fillUpcoming();
  if(nextPrepared&&nextPlayer!=null&&!upcoming.isEmpty()&&upcoming.get(0)==nextTrack){
   MediaPlayer old=player;player=nextPlayer;nextPlayer=null;nextPrepared=false;
   history.add(current());if(history.size()>100)history.remove(0);index=queue.indexOf(nextTrack);upcoming.remove(nextTrack);nextTrack=null;fillUpcoming();ready=true;crossfading=false;fade=0;
   old.setOnCompletionListener(null);old.release();fadeOut=null;if(fadeIn!=null){fadeIn.close();fadeIn=null;}volume(player,1);if(!player.isPlaying())player.start();save();update();refreshUpcoming();
  }else if(!upcoming.isEmpty()){play(queue.indexOf(upcoming.get(0)));}else {this.ended=true;pause(true);}
 }
 void resetFade(){if(fadeOut!=null){try{fadeOut.close();}catch(Exception ignored){}fadeOut=null;}fade=0;crossfading=false;if(player!=null&&ready)try{volume(player,1);}catch(Exception ignored){}}
 void releaseNext(){if(nextPlayer!=null){nextPlayer.setOnPreparedListener(null);nextPlayer.release();nextPlayer=null;}nextTrack=null;nextPrepared=false;}
 void prepareNext(){
  Track candidate=!repeat&&!upcoming.isEmpty()?upcoming.get(0):null;
  if(waitingTrack!=null||!wantPlayback||!ready||player==null||candidate==null||!available(candidate)){releaseNext();return;}
  if(nextTrack==candidate&&nextPlayer!=null)return;
  releaseNext();final MediaPlayer owner=player;
  try{
   nextTrack=candidate;nextPlayer=createPlayer(candidate,owner.getAudioSessionId());final MediaPlayer prepared=nextPlayer;
   prepared.setOnPreparedListener(p->{
    if(p!=nextPlayer)return;
    if(player!=owner||repeat||upcoming.isEmpty()||upcoming.get(0)!=candidate){releaseNext();return;}
    nextPrepared=true;
   });prepared.prepareAsync();
  }catch(Exception e){releaseNext();error="Next track could not be preloaded; it will be retried at transition.";}
 }
 public void refreshUpcoming(){
  prepareNext();publishProtectedCache();
  ArrayList<String> needed=new ArrayList<>();
  if(waitingTrack!=null&&queue.contains(waitingTrack)&&wantPlayback)addPreparation(waitingTrack,needed);
  if(!prefetchSuspended){
   for(Track t:upcoming)addPreparation(t,needed);
  }
  DownloadService.schedule(this,needed);
 }
 private void addPreparation(Track track,List<String> videos){
  if(available(track))return;
  if("query".equals(track.kind)){
   if(searchKey.isEmpty()){if(track==waitingTrack)error="Enter your search API key to match this title.";return;}
   if(resolving.contains(track)||resolveFailed.contains(track))return;
   resolving.add(track);final String key=searchKey;
   resolver.execute(()->{
    String id=null,message=null;try{id=TrackResolver.resolve(track,key);}catch(Exception e){message=e.getMessage();}
    final String resolved=id,failure=message;
    main.post(()->{
     if(destroyed)return;resolving.remove(track);
     if(!queue.contains(track))return;
     if(resolved!=null){track.uri=resolved;track.kind="youtube";save();}
     else {resolveFailed.add(track);if(track==waitingTrack){error=failure;waitingTrack=null;}}
     refreshUpcoming();
    });
   });return;
  }
  if("youtube".equals(track.kind)&&!failedDownloads.contains(track.uri)&&!videos.contains(track.uri))videos.add(track.uri);
 }
 public void onDownloadResult(String id,String failure){
  if(failure!=null){failedDownloads.add(id);if(waitingTrack!=null&&id.equals(waitingTrack.uri)){error=failure;waitingTrack=null;if(!playing())pause(true);}}
  else if(waitingTrack!=null&&id.equals(waitingTrack.uri)&&queue.contains(waitingTrack)&&wantPlayback){int target=queue.indexOf(waitingTrack);play(target);return;}
  refreshUpcoming();
 }
 public void cancelPreparation(){prefetchSuspended=true;waitingTrack=null;}
 private void publishProtectedCache(){
  java.util.Set<String> ids=new java.util.HashSet<>();
  if(!queue.isEmpty())ids.add(current().uri);
  for(Track t:upcoming)ids.add(t.uri);
  if(waitingTrack!=null)ids.add(waitingTrack.uri);
  protectedCacheIds=java.util.Collections.unmodifiableSet(ids);
 }
 public String repeatLabel(){return repeat?"Repeat: one":repeatAll?"Repeat: all":"Repeat: off";}
 public void cycleRepeat(){if(repeat){repeat=false;repeatAll=false;}else if(repeatAll){repeat=true;repeatAll=false;}else repeatAll=true;save();refreshUpcoming();}
 public void fillUpcoming(){while(upcoming.size()<QUEUE_LIMIT&&!backlog.isEmpty()){Track t=backlog.remove(0);if(queue.contains(t)&&!upcoming.contains(t))upcoming.add(t);}}
 public void playSequence(List<Track> tracks,boolean shuffle){ArrayList<Track> chosen=new ArrayList<>();for(Track t:tracks)if(queue.contains(t)&&!chosen.contains(t))chosen.add(t);if(chosen.isEmpty())return;if(shuffle)Collections.shuffle(chosen);releaseNext();upcoming.clear();backlog.clear();Track first=chosen.remove(0);backlog.addAll(chosen);fillUpcoming();save();play(queue.indexOf(first));}
 public void shuffleUpcoming(){Collections.shuffle(upcoming);queueChanged();}
 public void favorite(Track t){t.favorite=!t.favorite;save();}
 public int append(Track t){queue.add(t);save();refreshUpcoming();return queue.size()-1;}
 // Manual edits never refill slots; playback advancement owns look-ahead replenishment.
 public void queueChanged(){save();refreshUpcoming();}
 public boolean enqueue(Track track,boolean first){
  if(!queue.contains(track))return false;deletedCacheIds.remove(track.uri);
  if(!upcoming.contains(track)&&upcoming.size()>=QUEUE_LIMIT){error="Queue is full (10 tracks). Remove a queued track first.";return false;}
  failedDownloads.remove(track.uri);resolveFailed.remove(track);backlog.remove(track);upcoming.remove(track);if(first)upcoming.add(0,track);else upcoming.add(track);prefetchSuspended=false;queueChanged();return true;
 }
 public void moveTrack(Track t,int destination){int from=upcoming.indexOf(t);if(from<0)return;upcoming.remove(from);upcoming.add(Math.max(0,Math.min(destination,upcoming.size())),t);queueChanged();}
 public void playNext(Track t){enqueue(t,true);}
 public void dequeue(Track t){upcoming.remove(t);backlog.remove(t);if(waitingTrack==t)waitingTrack=null;queueChanged();}
 public void clearUpcoming(){
  if(waitingTrack!=null&&(upcoming.contains(waitingTrack)||backlog.contains(waitingTrack)))waitingTrack=null;
  upcoming.clear();backlog.clear();repeatAll=false;releaseNext();queueChanged();
 }
 public void removeTrack(Track t){
  deletedCacheIds.add(t.uri);Track selected=current();boolean active=t==selected;if(active){pause(true);releasePlayer();}upcoming.remove(t);if(waitingTrack==t)waitingTrack=null;backlog.remove(t);collections.removeTrack(t);queue.remove(t);
  index=active?0:Math.max(0,queue.indexOf(selected));queueChanged();AudioCache.remove(this,t.uri);update();
 }
 public void clearLibrary(){pause(true);releasePlayer();waitingTrack=null;upcoming.clear();backlog.clear();collections.playlists.clear();queue.clear();index=0;queueChanged();update();}
 public int importUnique(List<Track> songs){int added=0;for(Track t:songs){boolean duplicate=false;for(Track old:queue)if(QueueTools.same(old,t)){duplicate=true;break;}if(!duplicate){queue.add(t);added++;}}queueChanged();return added;}
 void volume(MediaPlayer p,float gain){p.setVolume(gain*(balance>0?1-balance:1),gain*(balance<0?1+balance:1));}
 // Only the outgoing player fades. The prepared player remains stopped until completion.
 final Runnable crossfadeTick=new Runnable(){public void run(){
  try{if(playing()&&android.os.SystemClock.elapsedRealtime()-lastCheckpoint>5000){checkpoint();lastCheckpoint=android.os.SystemClock.elapsedRealtime();}if(wantPlayback&&playing()){int remaining=duration()-position();int window=Math.min(3000,duration()/3);
   if(fadeOut==null&&remaining>0&&remaining<=window){fadeOut=player.createVolumeShaper(new VolumeShaper.Configuration.Builder().setDuration(Math.max(1,remaining)).setCurve(new float[]{0,.5f,1},new float[]{1,.5f,0}).setInterpolatorType(VolumeShaper.Configuration.INTERPOLATOR_TYPE_LINEAR).build());fadeOut.apply(VolumeShaper.Operation.PLAY);}
  }}catch(Exception e){android.util.Log.w("AfterglowFade","End fade unavailable",e);}if(!destroyed)main.postDelayed(this,100);
 }};
 public String trackStatus(Track t){
  if(t==current())return playing()?"Playing":"Selected";
  if(resolving.contains(t))return "Matching title…";
  if(failedDownloads.contains(t.uri)||resolveFailed.contains(t))return "Preparation failed · tap to retry";
  if(DownloadService.busy&&!t.uri.isEmpty()&&"youtube".equals(t.kind)&&t.uri.equals(DownloadService.trackId)&&(upcoming.contains(t)||t==waitingTrack))return "Downloading…";
  if("local".equals(t.kind))return "Local · ready";if(AudioCache.cachedForStatus(this,t.uri))return "Cached · ready";if(upcoming.contains(t))return "Queued · waiting";
  return "query".equals(t.kind)?"Title to match":"Not cached";
 }
 public void resume(){
  wantPlayback=true;prefetchSuspended=false;
  if(waitingTrack!=null){int target=queue.indexOf(waitingTrack);if(target>=0){play(target);return;}}
  if(player==null){restorePosition=current().id.equals(prefs.getString("resumeTrack",""))?prefs.getInt("resumePosition",0):0;playInternal(index);return;}if(!ready)return;
  startService(new Intent(this,PlaybackService.class));startForeground(1,notification());
  if(manager.requestAudioFocus(focus)==AudioManager.AUDIOFOCUS_REQUEST_GRANTED){player.start();update();refreshUpcoming();}
  else{error="Another app currently has audio focus.";wantPlayback=false;stopForeground(STOP_FOREGROUND_REMOVE);}
 }
 public void pause(boolean abandon){
  wantPlayback=false;if(playing())player.pause();checkpoint();resetFade();releaseNext();
  if(abandon){resumeOnFocus=false;manager.abandonAudioFocusRequest(focus);}
  update();if(ready)stopForeground(STOP_FOREGROUND_DETACH);
 }
 public void toggle(){if(playing()||(waitingTrack!=null&&wantPlayback))pause(true);else resume();}
 public void previous(){if(position()>5000||history.isEmpty()){seek(0);resume();return;}Track t=history.remove(history.size()-1);if(queue.contains(t)){play(queue.indexOf(t));if(!history.isEmpty()&&history.get(history.size()-1)!=t)history.remove(history.size()-1);}else {seek(0);resume();}}
 public void next(int step){if(step<0){previous();return;}if(step>0&&repeatAll&&!queue.isEmpty()&&!backlog.contains(current())&&!upcoming.contains(current())){backlog.add(current());fillUpcoming();}if(step>0&&!upcoming.isEmpty())play(queue.indexOf(upcoming.get(0)));else if(!queue.isEmpty())play((index+step+queue.size())%queue.size());}
 public void seek(int ms){if(ready){boolean restart=ended||playing();ended=false;resetFade();releaseNext();player.setOnSeekCompleteListener(p->{if(p!=player)return;p.setOnSeekCompleteListener(null);if(restart)resume();update();refreshUpcoming();});player.seekTo(Math.max(0,Math.min(ms,duration())));update();}}

 public void setupDSP(){try{DynamicsProcessing.Config config=new DynamicsProcessing.Config.Builder(DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,2,true,10,false,0,false,0,true).build();dsp=new DynamicsProcessing(0,player.getAudioSessionId(),config);dsp.setEnabled(true);dspStatus="10-band processing active";applyEQ();}catch(Exception e){dspStatus="Audio processing unavailable on this device";android.util.Log.w("Afterglow","DSP unavailable",e);}}
 public void applyEQ(){try{if(dsp!=null){dsp.setInputGainAllChannelsTo(preamp+boost);DynamicsProcessing.Eq eq=new DynamicsProcessing.Eq(true,eqEnabled,10);for(int i=0;i<10;i++)eq.setBand(i,new DynamicsProcessing.EqBand(true,cutoffs[i],gains[i]));dsp.setPreEqAllChannelsTo(eq);dsp.setLimiterAllChannelsTo(new DynamicsProcessing.Limiter(true,limit||boost>0,0,1,60,10,-1,0));}if(player!=null)volume(player,1);if(nextPlayer!=null)volume(nextPlayer,1);}catch(Exception e){dspStatus="Device could not apply this audio setting";}SharedPreferences.Editor edit=prefs.edit().putFloat("boost",boost).putFloat("preamp",preamp).putFloat("balance",balance).putBoolean("eq",eqEnabled).putBoolean("limit",limit).putBoolean("repeat",repeat);for(int i=0;i<10;i++)edit.putFloat("gain"+i,gains[i]);edit.apply();}
 PendingIntent action(String a){return PendingIntent.getService(this,a.hashCode(),new Intent(this,PlaybackService.class).setAction(a),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);}
 Notification notification(){return new Notification.Builder(this,"playback").setSmallIcon(com.afterglow.player.R.drawable.icon).setContentTitle(current().title).setContentText(current().artist).setContentIntent(PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE)).setVisibility(Notification.VISIBILITY_PUBLIC).setOngoing(playing()).addAction(android.R.drawable.ic_media_previous,"Previous",action("prev")).addAction(playing()?android.R.drawable.ic_media_pause:android.R.drawable.ic_media_play,playing()?"Pause":"Play",action(playing()?"pause":"play")).addAction(android.R.drawable.ic_media_next,"Next",action("next")).addAction(android.R.drawable.ic_menu_close_clear_cancel,"Stop",action("stop")).setStyle(new Notification.MediaStyle().setMediaSession(session.getSessionToken()).setShowActionsInCompactView(0,1,2)).build();}
 void update(){if(session==null)return;session.setMetadata(new MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE,current().title).putString(MediaMetadata.METADATA_KEY_ARTIST,current().artist).putLong(MediaMetadata.METADATA_KEY_DURATION,duration()).build());session.setPlaybackState(new PlaybackState.Builder().setActions(PlaybackState.ACTION_PLAY|PlaybackState.ACTION_PAUSE|PlaybackState.ACTION_PLAY_PAUSE|PlaybackState.ACTION_SKIP_TO_NEXT|PlaybackState.ACTION_SKIP_TO_PREVIOUS|PlaybackState.ACTION_SEEK_TO|PlaybackState.ACTION_STOP).setState(playing()?PlaybackState.STATE_PLAYING:PlaybackState.STATE_PAUSED,position(),playing()?1:0).build());if(player!=null)getSystemService(NotificationManager.class).notify(1,notification());}
 void releasePlayer(){resetFade();releaseNext();if(dsp!=null){dsp.release();dsp=null;}if(player!=null){player.release();player=null;}ready=false;}
 public void onDestroy(){checkpoint();destroyed=true;resolver.shutdownNow();main.removeCallbacksAndMessages(null);protectedCacheIds=Collections.emptySet();save();releasePlayer();manager.abandonAudioFocusRequest(focus);session.release();unregisterReceiver(noisy);getSystemService(NotificationManager.class).cancel(1);instance=null;super.onDestroy();}
}
