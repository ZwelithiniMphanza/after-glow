package com.afterglow.player;
import android.content.*;import java.io.*;import java.net.*;import org.json.*;
final class ServerSettings {
 static String url(Context c){importProvision(c);return c.getSharedPreferences("stem-server",0).getString("url","http://127.0.0.1:8765");}
 static String token(Context c){importProvision(c);return KeyStore.load(c,"stem-server");}
 static void save(Context c,String url,String token){url=url.trim().replaceAll("/+$","");try{URI u=new URI(url);boolean loopback="127.0.0.1".equals(u.getHost())||"localhost".equals(u.getHost());if(u.getHost()==null||u.getUserInfo()!=null||u.getQuery()!=null||u.getFragment()!=null||(!"https".equals(u.getScheme())&&!(loopback&&"http".equals(u.getScheme()))))throw new Exception();}catch(Exception e){throw new IllegalArgumentException("Use HTTPS, or http://127.0.0.1:8765 for the USB server");}KeyStore.save(c,"stem-server",token.trim());c.getSharedPreferences("stem-server",0).edit().putString("url",url).commit();}
 static void importProvision(Context c){File file=new File(c.getFilesDir(),"stem-server.json");if(!file.exists())return;try(InputStream in=new FileInputStream(file)){ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[1024];int n;while((n=in.read(b))!=-1){out.write(b,0,n);if(out.size()>8192)throw new IOException();}JSONObject json=new JSONObject(out.toString("UTF-8"));save(c,json.getString("url"),json.getString("token"));file.delete();}catch(Exception ignored){}}
}
