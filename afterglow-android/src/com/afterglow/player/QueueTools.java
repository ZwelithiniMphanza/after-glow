package com.afterglow.player;

import java.util.*;

/** Filtering returns original positions and never changes playback order. */
public final class QueueTools {
 public static boolean same(Track a,Track b){return (!a.uri.isEmpty()&&a.uri.equals(b.uri))||(normal(a.title).equals(normal(b.title))&&normal(a.artist).equals(normal(b.artist)));}
 static String normal(String s){return s.trim().replaceAll("\\s+"," ").toLowerCase(Locale.ROOT);}
 public static List<Integer> filter(List<Track> songs,String query){
  String q=query==null?"":query.trim().toLowerCase(Locale.ROOT);
  List<Integer> matches=new ArrayList<>();
  for(int i=0;i<songs.size();i++){
   Track t=songs.get(i);
   if(q.isEmpty()||(t.title+" "+t.artist).toLowerCase(Locale.ROOT).contains(q))matches.add(i);
  }
  return matches;
 }
}
