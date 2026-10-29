package dk.h4xtor.philipsremote;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.X509TrustManager;

/** Philips JointSpace API v6. All public network calls must run off the UI thread. */
public final class PhilipsApi {
    private static final int TIMEOUT = 5500;
    private static final Pattern AUTH = Pattern.compile("(\\w+)=(?:\"([^\"]*)\"|([^,]*))");
    private static final byte[] PAIR_SECRET = Base64.decode(
        "ZmVay1EQVFOaZhwQ4Kv81ypLAZNczV9sG4KkseXWn1NEk6cXmPKO/MCa9sryslvLCFMnNe4Z4CPXzToowvhHvA==",
        Base64.DEFAULT);
    private final Context context;
    private final String host;
    private final SharedPreferences pins;
    private final Vault vault;
    private final Object lock = new Object();

    public PhilipsApi(Context context, String host) {
        this.context = context.getApplicationContext();
        this.host = privateIp(host);
        this.pins = this.context.getSharedPreferences("philips_certificate_pins",Context.MODE_PRIVATE);
        this.vault = new Vault(this.context);
    }
    public String host(){return host;}
    public boolean paired(){return vault.load(host)!=null;}
    public boolean pinned(){return pins.contains(host);}
    public String certificateFingerprint(){return pins.getString(host,"");}

    public static String privateIp(String value){
        String h=value==null?"":value.trim();
        String[] a=h.split("\\.",-1);
        if(a.length!=4)throw new IllegalArgumentException("Indtast en privat IPv4-adresse");
        int[] n=new int[4];
        for(int i=0;i<4;i++){try{
            if(a[i].isEmpty() || (a[i].length()>1 && a[i].startsWith("0")))throw new NumberFormatException();
            n[i]=Integer.parseInt(a[i]); if(n[i]<0||n[i]>255)throw new NumberFormatException();
        }catch(NumberFormatException e){throw new IllegalArgumentException("Ugyldig IPv4-adresse");}}
        if(!(n[0]==10||(n[0]==172&&n[1]>=16&&n[1]<=31)||(n[0]==192&&n[1]==168)))
            throw new IllegalArgumentException("TV skal have en privat LAN-adresse");
        return h;
    }

    private SSLContext tls() throws Exception {
        X509TrustManager trust = new X509TrustManager(){
            @Override public void checkClientTrusted(X509Certificate[] c,String t) throws java.security.cert.CertificateException {
                throw new java.security.cert.CertificateException("Client certificates not allowed");
            }
            @Override public void checkServerTrusted(X509Certificate[] certificates,String authType) throws java.security.cert.CertificateException {
                if(certificates==null||certificates.length==0)throw new java.security.cert.CertificateException("Missing TV certificate");
                String fingerprint;
                try{fingerprint=hex(MessageDigest.getInstance("SHA-256").digest(certificates[0].getEncoded()));}
                catch(Exception e){throw new java.security.cert.CertificateException(e);}
                String expected=pins.getString(host,null);
                if(expected==null){
                    // Trust on first use: only pair on a trusted home network; pin this certificate thereafter.
                    if(!pins.edit().putString(host,fingerprint).commit())
                        throw new java.security.cert.CertificateException("Could not save TV certificate pin");
                } else if(!MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII), fingerprint.getBytes(StandardCharsets.US_ASCII))){
                    throw new java.security.cert.CertificateException("TV CERTIFICATE CHANGED! Check TV/network before resetting pin");
                }
            }
            @Override public X509Certificate[] getAcceptedIssuers(){return new X509Certificate[0];}
        };
        SSLContext context=SSLContext.getInstance("TLS");
        context.init(null,new javax.net.ssl.TrustManager[]{trust},new SecureRandom());
        return context;
    }
    private static String hex(byte[] b) {StringBuilder s=new StringBuilder();for(byte x:b)s.append(String.format(Locale.US,"%02x",x&255));return s.toString();}
    private HttpURLConnection open(URL url,String method,byte[] body,String authorization) throws Exception{
        HttpURLConnection c=(HttpURLConnection)url.openConnection();
        if(c instanceof HttpsURLConnection){
            HttpsURLConnection h=(HttpsURLConnection)c;
            h.setSSLSocketFactory(tls().getSocketFactory());
            h.setHostnameVerifier((name,session)->host.equals(name));
        }
        c.setConnectTimeout(TIMEOUT);c.setReadTimeout(TIMEOUT);
        c.setRequestMethod(method);
        c.setRequestProperty("Accept","application/json");
        c.setRequestProperty("Content-Type","application/json; charset=utf-8");
        c.setRequestProperty("User-Agent","H4XTOR-Remote-S24/3.0");
        if(authorization!=null)c.setRequestProperty("Authorization",authorization);
        if(body!=null){c.setDoOutput(true);c.setFixedLengthStreamingMode(body.length);
            try(OutputStream out=c.getOutputStream()){out.write(body);}
        }
        return c;
    }
    public JSONObject request(String method,String path,JSONObject payload,boolean auth,boolean secure) throws Exception{
        return request(method,path,payload,auth,secure,null);
    }
    private JSONObject request(String method,String path,JSONObject payload,boolean auth,boolean secure,Vault.Credentials override) throws Exception{
        synchronized(lock){
            URL url=new URL((secure?"https://":"http://")+host+":"+(secure?1926:1925)+"/6/"+path);
            byte[] bytes=payload==null?null:payload.toString().getBytes(StandardCharsets.UTF_8);
            HttpURLConnection initial=open(url,method,bytes,null);
            int code=initial.getResponseCode();
            if(code==401&&auth){
                String challenge=initial.getHeaderField("WWW-Authenticate");
                initial.disconnect();
                Vault.Credentials login=override!=null?override:vault.load(host);
                if(login==null)throw new java.io.IOException("TV ikke parret. Brug PAR-knappen");
                if(challenge==null||!challenge.regionMatches(true,0,"Digest",0,6))
                    throw new java.io.IOException("TV returnerede ingen Digest challenge");
                return read(open(url,method,bytes,digest(challenge,method,url,login)),path);
            }
            return read(initial,path);
        }
    }
    private JSONObject read(HttpURLConnection c,String path) throws Exception{
        try{
            int status=c.getResponseCode();
            InputStream stream=status>=400?c.getErrorStream():c.getInputStream();
            ByteArrayOutputStream out=new ByteArrayOutputStream();
            if(stream!=null)try(InputStream in=stream){byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1){
                if(out.size()>1024*1024)throw new java.io.IOException("Oversized TV response");out.write(b,0,n);
            }}
            String body=out.toString(StandardCharsets.UTF_8.name());
            if(status==401)throw new java.io.IOException("Parring afvist. Par TV igen");
            if(status>=400)throw new java.io.IOException(path+": HTTP "+status+" "+body.substring(0,Math.min(180,body.length())));
            return body.trim().isEmpty()?new JSONObject():new JSONObject(body);
        }finally{c.disconnect();}
    }
    private static String md5(String s) throws Exception{return hex(MessageDigest.getInstance("MD5").digest(s.getBytes(StandardCharsets.ISO_8859_1)));}
    private static String digest(String challenge,String method,URL url,Vault.Credentials login) throws Exception {
        Map<String,String> args=new HashMap<>();
        Matcher m=AUTH.matcher(challenge.substring(6));
        while(m.find())args.put(m.group(1).toLowerCase(Locale.US),(m.group(2)!=null?m.group(2):m.group(3)).trim());
        String realm=args.getOrDefault("realm",""),nonce=args.getOrDefault("nonce","");
        if(realm.isEmpty()||nonce.isEmpty())throw new java.io.IOException("Digest challenge incomplete");
        String uri=url.getPath();String qop=args.getOrDefault("qop","").toLowerCase(Locale.US).contains("auth")?"auth":"";
        byte[] seed=new byte[12];new SecureRandom().nextBytes(seed);String cn=hex(seed);
        String ha1=md5(login.user+":"+realm+":"+login.password),ha2=md5(method+":"+uri);
        String response=qop.isEmpty()?md5(ha1+":"+nonce+":"+ha2):md5(ha1+":"+nonce+":00000001:"+cn+":"+qop+":"+ha2);
        String value="Digest username=\""+login.user+"\", realm=\""+realm+"\", nonce=\""+nonce+"\", uri=\""+uri+"\", response=\""+response+"\", algorithm=MD5";
        if(!qop.isEmpty())value+=", qop="+qop+", nc=00000001, cnonce=\""+cn+"\"";
        if(args.containsKey("opaque"))value+=", opaque=\""+args.get("opaque")+"\"";
        return value;
    }

    public JSONObject system()throws Exception{return request("GET","system",null,false,false);}
    public JSONObject sendKey(String key)throws Exception{return request("POST","input/key",new JSONObject().put("key",key),true,true);}
    public JSONObject volume()throws Exception{return request("GET","audio/volume",null,true,true);}
    public JSONObject setVolume(int value)throws Exception{return request("POST","audio/volume",new JSONObject().put("current",Math.max(0,Math.min(100,value))),true,true);}
    public JSONObject currentChannel()throws Exception{return request("GET","activities/tv",null,true,true);}
    public JSONObject applications()throws Exception{return request("GET","applications",null,true,true);}
    public JSONObject channels()throws Exception{return request("GET","channeldb/tv/channelLists/all",null,true,true);}
    public JSONObject ambilight()throws Exception{return request("GET","ambilight/currentconfiguration",null,true,true);}
    public JSONObject ambilightPower(boolean on)throws Exception{return request("POST","ambilight/power",new JSONObject().put("power",on?"On":"Off"),true,true);}
    public JSONObject ambilightStyle(String style,String setting)throws Exception{
        return request("POST","ambilight/currentconfiguration",new JSONObject().put("styleName",style).put("isExpert",false).put("menuSetting",setting),true,true);
    }
    public JSONObject launch(App app)throws Exception{return request("POST","activities/launch",new JSONObject().put("intent",app.intent),true,true);}
    public JSONObject tune(Channel c)throws Exception{return request("POST","activities/tv",
        new JSONObject().put("channelList",new JSONObject().put("id",c.listId))
            .put("channel",new JSONObject().put("ccid",c.ccid)),true,true);}

    public PairRequest beginPair() throws Exception{
        String alphabet="ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        SecureRandom random=new SecureRandom();StringBuilder id=new StringBuilder();
        for(int i=0;i<16;i++)id.append(alphabet.charAt(random.nextInt(alphabet.length())));
        JSONObject device=new JSONObject().put("device_name","H4XTOR S24 Ultra").put("device_os","Android")
           .put("app_id","dk.h4xtor.philipsremote").put("app_name","H4XTOR OLED Remote")
           .put("type","native").put("id",id.toString());
        JSONObject p=new JSONObject().put("scope",new JSONArray().put("read").put("write").put("control")).put("device",device);
        JSONObject result=request("POST","pair/request",p,false,true);
        String authKey=result.optString("auth_key");
        long stamp=result.optLong("timestamp");
        if(authKey.isEmpty()||stamp==0)throw new java.io.IOException("TV returnerede ingen pairing key/timestamp");
        return new PairRequest(id.toString(),authKey,stamp,result.optInt("timeout",60),device);
    }
    public void grant(PairRequest p,String pin)throws Exception{
        if(!pin.matches("\\d{4}"))throw new IllegalArgumentException("PIN skal være 4 cifre");
        Mac mac=Mac.getInstance("HmacSHA1");mac.init(new SecretKeySpec(PAIR_SECRET,"HmacSHA1"));
        String hmac=hex(mac.doFinal((p.timestamp+pin).getBytes(StandardCharsets.UTF_8)));
        String signature=Base64.encodeToString(hmac.getBytes(StandardCharsets.US_ASCII),Base64.NO_WRAP);
        JSONObject auth=new JSONObject().put("auth_AppId","1").put("pin",pin)
             .put("auth_timestamp",p.timestamp).put("auth_signature",signature);
        JSONObject result=request("POST","pair/grant",new JSONObject().put("auth",auth).put("device",p.device),
            true,true,new Vault.Credentials(p.id,p.key));
        String error=result.optString("error_id","SUCCESS");
        if(!error.equals("SUCCESS")&&!error.equals("SUCCESS_PAIRING"))
            throw new java.io.IOException(result.optString("error_text","TV afviste PIN"));
        vault.save(host,p.id,p.key);
    }
    public void clearPairing(){vault.forget(host);}
    public void clearCertificatePin(){pins.edit().remove(host).apply();}

    public static List<App> parseApps(JSONObject response){
        List<App> result=new ArrayList<>();JSONArray a=response.optJSONArray("applications");
        if(a==null)return result;
        for(int i=0;i<a.length();i++){JSONObject o=a.optJSONObject(i);if(o==null||o.optJSONObject("intent")==null)continue;
            result.add(new App(o.optString("id","app"+i),o.optString("label","App "+i),o.optJSONObject("intent")));
        }
        Collections.sort(result,Comparator.comparing(x->x.name.toLowerCase(Locale.ROOT)));return result;
    }
    public static List<Channel> parseChannels(JSONObject response){
        List<Channel> result=new ArrayList<>();JSONArray raw=response.optJSONArray("Channel");
        if(raw==null)raw=response.optJSONArray("channels");
        if(raw==null)return result;
        String listId=response.optString("id","all");
        for(int i=0;i<raw.length();i++){JSONObject o=raw.optJSONObject(i);if(o==null||!o.has("ccid"))continue;
            result.add(new Channel(o.optInt("ccid"),o.optString("preset",""),o.optString("name","Kanal"),listId));}
        result.sort((a,b)->{
            try{return Integer.compare(Integer.parseInt(a.preset),Integer.parseInt(b.preset));}
            catch(Exception e){return a.name.compareToIgnoreCase(b.name);}
        });return result;
    }

    public static final class PairRequest{
        public final String id,key;public final long timestamp;public final int timeout;public final JSONObject device;
        PairRequest(String id,String key,long stamp,int timeout,JSONObject d){this.id=id;this.key=key;this.timestamp=stamp;this.timeout=timeout;this.device=d;}
    }
    public static final class App{
        public final String id,name;public final JSONObject intent;
        App(String id,String name,JSONObject intent){this.id=id;this.name=name;this.intent=intent;}
    }
    public static final class Channel{
        public final int ccid;public final String preset,name,listId;
        Channel(int id,String preset,String name,String listId){this.ccid=id;this.preset=preset;this.name=name;this.listId=listId;}
    }

    private static final class Vault{
        private static final String ALIAS="h4xtor_tv_keystore_v3";
        private final SharedPreferences prefs;
        Vault(Context c){prefs=c.getSharedPreferences("h4xtor_secure_pairing_v3",Context.MODE_PRIVATE);}
        private SecretKey key() throws Exception {
            KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);
            KeyStore.Entry entry=store.getEntry(ALIAS,null);
            if(entry instanceof KeyStore.SecretKeyEntry)return ((KeyStore.SecretKeyEntry)entry).getSecretKey();
            KeyGenerator g=KeyGenerator.getInstance("AES","AndroidKeyStore");
            g.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).build());return g.generateKey();
        }
        void save(String host,String user,String password)throws Exception{
            Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,key());
            String plain=user+"\n"+password;
            String v=Base64.encodeToString(c.getIV(),Base64.NO_WRAP)+":"+
                Base64.encodeToString(c.doFinal(plain.getBytes(StandardCharsets.UTF_8)),Base64.NO_WRAP);
            if(!prefs.edit().putString(host,v).commit())throw new java.io.IOException("Could not store TV credentials");
        }
        Credentials load(String host){
            String v=prefs.getString(host,null);if(v==null)return null;
            try{String[] parts=v.split(":",2);
                Cipher c=Cipher.getInstance("AES/GCM/NoPadding");
                c.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Base64.decode(parts[0],Base64.NO_WRAP)));
                String[] credentials=new String(c.doFinal(Base64.decode(parts[1],Base64.NO_WRAP)),StandardCharsets.UTF_8).split("\n",2);
                return credentials.length==2?new Credentials(credentials[0],credentials[1]):null;
            }catch(Exception e){return null;}
        }
        void forget(String host){prefs.edit().remove(host).apply();}
        static final class Credentials{
            final String user,password;
            Credentials(String u,String p){user=u;password=p;}
        }
    }
}
