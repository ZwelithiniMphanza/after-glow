package com.afterglow.player;
import org.json.*;
public class Track {
 public String title,artist,uri,kind;
 public Track(String t,String a,String u,String k){title=t;artist=a;uri=u;kind=k;}
 public JSONObject json() throws JSONException{return new JSONObject().put("title",title).put("artist",artist).put("uri",uri).put("kind",kind);}
 public static Track from(JSONObject j)throws JSONException{return new Track(j.getString("title"),j.optString("artist"),j.optString("uri"),j.getString("kind"));}
}
