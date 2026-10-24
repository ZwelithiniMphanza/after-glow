package com.afterglow.player;

import java.io.*;import java.net.*;import org.json.*;

final class TrackResolver {
 static String resolve(Track track,String key)throws Exception{
  if(key==null||key.isEmpty())throw new IOException("Add your search API key to prepare imported titles.");
  URL url=new URL("https://www.googleapis.com/youtube/v3/search?part=snippet&type=video&maxResults=1&q="+URLEncoder.encode(track.title+" "+track.artist,"UTF-8")+"&key="+URLEncoder.encode(key,"UTF-8"));
  HttpURLConnection c=(HttpURLConnection)url.openConnection();c.setConnectTimeout(15000);c.setReadTimeout(15000);
  try{
   if(c.getResponseCode()!=200)throw new IOException("Title matching failed. Check the search key and quota.");
   ByteArrayOutputStream out=new ByteArrayOutputStream();
   try(InputStream in=c.getInputStream()){byte[] bytes=new byte[4096];int n;while((n=in.read(bytes))!=-1){out.write(bytes,0,n);if(out.size()>1024*1024)throw new IOException("Search response too large");}}
   JSONArray items=new JSONObject(out.toString("UTF-8")).getJSONArray("items");
   if(items.length()==0)throw new IOException("No match found for "+track.title);
   String id=items.getJSONObject(0).getJSONObject("id").getString("videoId");
   if(!id.matches("[A-Za-z0-9_-]{11}"))throw new IOException("Invalid search result");return id;
  }finally{c.disconnect();}
 }
}
