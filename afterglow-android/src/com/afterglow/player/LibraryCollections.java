package com.afterglow.player;
import java.util.*;
import org.json.*;
/** Playlist membership uses stable IDs, independent of sorting and resolved URLs. */
public final class LibraryCollections {
 public final LinkedHashMap<String,ArrayList<String>> playlists=new LinkedHashMap<>();
 public void load(String raw){playlists.clear();try{JSONArray all=new JSONArray(raw);for(int i=0;i<all.length();i++){JSONObject p=all.getJSONObject(i);ArrayList<String> ids=new ArrayList<>();JSONArray a=p.getJSONArray("tracks");for(int n=0;n<a.length();n++)if(!ids.contains(a.getString(n)))ids.add(a.getString(n));playlists.put(p.getString("name"),ids);}}catch(Exception ignored){}}
 public String json(){JSONArray all=new JSONArray();for(Map.Entry<String,ArrayList<String>> e:playlists.entrySet())try{all.put(new JSONObject().put("name",e.getKey()).put("tracks",new JSONArray(e.getValue())));}catch(Exception ignored){}return all.toString();}
 public boolean create(String name){name=name.trim();if(name.isEmpty()||name.length()>80)return false;for(String old:playlists.keySet())if(old.equalsIgnoreCase(name))return false;playlists.put(name,new ArrayList<>());return true;}
 public void add(String name,Track t){ArrayList<String> ids=playlists.get(name);if(ids!=null&&!ids.contains(t.id))ids.add(t.id);}
 public void remove(String name,Track t){ArrayList<String> ids=playlists.get(name);if(ids!=null)ids.remove(t.id);}
 public void removeTrack(Track t){for(ArrayList<String> ids:playlists.values())ids.remove(t.id);}
 public List<Track> members(String name,List<Track> library){List<Track> result=new ArrayList<>();List<String> ids=playlists.get(name);if(ids!=null)for(String id:ids)for(Track t:library)if(t.id.equals(id)){result.add(t);break;}return result;}
}
