package dk.h4xtor.philipsremote;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Build;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Zero-dependency native S24 Ultra remote. JointSpace calls never run on the UI thread. */
@SuppressLint("SetTextI18n")
public final class MainActivity extends Activity {
    private static final int BG=0xff070b12, PANEL=0xff111b2a, PANEL2=0xff19273a,
        STROKE=0xff284058, CYAN=0xff69dafa, WHITE=0xfff0f7ff, DIM=0xff92a6bb,
        GREEN=0xff7df2b0, RED=0xffff7886, GOLD=0xffffd27a, PURPLE=0xffc8a9ff;
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private final List<PhilipsApi.App> apps=new ArrayList<>();
    private final List<PhilipsApi.Channel> channels=new ArrayList<>();
    private SharedPreferences prefs;
    private PhilipsApi api;
    private FrameLayout content;
    private TextView status,headline,nowPlaying;
    private String page="REMOTE";
    private boolean online=false;
    private String tvName="PHILIPS OLED";
    private String channelName="Ingen kanal registreret";
    private int currentVolume=-1;

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        prefs=getSharedPreferences("h4xtor_s24_settings",Context.MODE_PRIVATE);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        setContentView(buildUi());
        page("REMOTE");
        connect();
    }
    @Override protected void onDestroy(){io.shutdownNow();super.onDestroy();}

    private View buildUi(){
        LinearLayout root=vertical(BG);
        if(Build.VERSION.SDK_INT>=35){
            root.setOnApplyWindowInsetsListener((view,insets)->{
                android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars());
                view.setPadding(0,bars.top,0,bars.bottom);
                return insets;
            });
        }
        LinearLayout header=vertical(BG);
        header.setPadding(dp(19),dp(18),dp(19),dp(6));
        TextView brand=text("H4XTOR  /  PHILIPS OLED",CYAN,12,true);
        brand.setLetterSpacing(.16f);header.addView(brand);
        headline=text("Remote Control",WHITE,27,true);header.addView(headline);
        TextView model=text("65OLED865/12  •  JointSpace API v6  •  S24 Ultra",DIM,12,false);
        header.addView(model);
        LinearLayout line=row();
        TextView chip=text("● CONNECTING",GOLD,12,true);chip.setTag("onlinechip");
        line.addView(chip,new LinearLayout.LayoutParams(0,dp(35),1));
        Button refresh=button("↻  SYNC",CYAN,this::connect);
        line.addView(refresh,new LinearLayout.LayoutParams(dp(110),dp(42)));
        header.addView(line,match());
        root.addView(header,match());

        HorizontalScrollView tabsScroll=new HorizontalScrollView(this);
        tabsScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout tabs=row();tabs.setPadding(dp(8),dp(2),dp(8),dp(8));
        String[][] tabList={{"REMOTE","REMOTE"},{"CHANNELS","KANALER"},{"APPS","APPS"},{"AMBILIGHT","AMBI"},{"TOOLS","TOOLS"}};
        for(String[] tab:tabList){
            Button b=button(tab[1],WHITE,()->page(tab[0]));
            tabs.addView(b,new LinearLayout.LayoutParams(dp(96),dp(46)));
        }
        tabsScroll.addView(tabs);
        root.addView(tabsScroll,match());

        content=new FrameLayout(this);
        root.addView(content,new LinearLayout.LayoutParams(-1,0,1));
        status=text("LOCAL CONTROL  •  READY",DIM,12,false);
        status.setMaxLines(3);status.setPadding(dp(17),dp(12),dp(17),dp(13));
        status.setBackgroundColor(PANEL);
        root.addView(status,match());
        return root;
    }

    private void page(String target){
        page=target;headline.setText(switch(target){
            case "CHANNELS"->"Channel Matrix";
            case "APPS"->"App Launcher";
            case "AMBILIGHT"->"Ambilight Lab";
            case "TOOLS"->"Device & Tools";
            default->"Remote Control";
        });
        if(content==null)return;
        content.removeAllViews();
        ScrollView sc=new ScrollView(this);sc.setFillViewport(true);
        LinearLayout body=vertical(BG);body.setPadding(dp(14),dp(6),dp(14),dp(18));
        switch(target){
            case "CHANNELS" -> channelsPage(body);
            case "APPS" -> appsPage(body);
            case "AMBILIGHT" -> ambilightPage(body);
            case "TOOLS" -> toolsPage(body);
            default -> remotePage(body);
        }
        sc.addView(body);content.addView(sc);
    }
    private void remotePage(LinearLayout root){
        LinearLayout hero=panel();
        hero.setPadding(dp(16),dp(16),dp(16),dp(16));
        TextView eyebrow=text("NOW PLAYING  /  "+tvName.toUpperCase(Locale.ROOT),CYAN,11,true);
        hero.addView(eyebrow);
        nowPlaying=text(channelName,WHITE,19,true);nowPlaying.setPadding(0,dp(7),0,0);
        hero.addView(nowPlaying);
        hero.addView(text(currentVolume<0?"VOL  --":"VOL  "+currentVolume,DIM,12,false));
        root.addView(hero,gap(dp(8),dp(0)));
        if(currentVolume>=0){
            root.addView(section("VOLUME  /  "+currentVolume,CYAN),gap(dp(8),dp(12)));
            SeekBar slider=new SeekBar(this);
            slider.setMax(100);slider.setProgress(currentVolume);
            slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
                public void onProgressChanged(SeekBar bar,int progress,boolean fromUser){}
                public void onStartTrackingTouch(SeekBar bar){}
                public void onStopTrackingTouch(SeekBar bar){
                    if(api!=null)run("VOLUME",()->api.setVolume(bar.getProgress()),r->{
                        currentVolume=bar.getProgress();message("Volume  "+currentVolume,GREEN);
                    });
                }
            });
            root.addView(slider,gap(dp(50),dp(1)));
        }

        LinearLayout power=row();
        addWeight(power,"⏻  STANDBY",RED,()->key("Standby"));
        addWeight(power,"⚡  WAKE TV",GREEN,this::wake);
        root.addView(power,gap(dp(5),dp(14)));
        LinearLayout shortcuts=row();
        addWeight(shortcuts,"⌂  HOME",CYAN,()->key("Home"));
        addWeight(shortcuts,"↩  BACK",WHITE,()->key("Back"));
        addWeight(shortcuts,"▣  SOURCE",WHITE,()->key("Source"));
        root.addView(shortcuts,gap(dp(5),dp(8)));

        TextView navTitle=section("NAVIGATION",CYAN);root.addView(navTitle,gap(dp(4),dp(17)));
        LinearLayout nav=vertical(PANEL);
        nav.setPadding(dp(9),dp(10),dp(9),dp(10));
        Button up=button("▲",CYAN,()->key("CursorUp"));
        LinearLayout.LayoutParams upP=new LinearLayout.LayoutParams(dp(106),dp(65));
        upP.gravity=Gravity.CENTER;nav.addView(up,upP);
        LinearLayout middle=row();
        addWeight(middle,"◀",CYAN,()->key("CursorLeft"));
        addWeight(middle,"OK",GREEN,()->key("Confirm"));
        addWeight(middle,"▶",CYAN,()->key("CursorRight"));
        nav.addView(middle,match());
        Button down=button("▼",CYAN,()->key("CursorDown"));
        LinearLayout.LayoutParams downP=new LinearLayout.LayoutParams(dp(106),dp(65));
        downP.gravity=Gravity.CENTER;nav.addView(down,downP);
        root.addView(nav,gap(dp(5),dp(7)));

        LinearLayout cols=row();
        LinearLayout audio=panel(),channel=panel();
        audio.setPadding(dp(7),dp(8),dp(7),dp(8));
        channel.setPadding(dp(7),dp(8),dp(7),dp(8));
        audio.addView(section("AUDIO",GREEN));channel.addView(section("CHANNEL",CYAN));
        audio.addView(filled("＋ VOL",GREEN,()->key("VolumeUp")),gap(dp(60),dp(6)));
        audio.addView(filled("MUTE",PURPLE,()->key("Mute")),gap(dp(51),dp(5)));
        audio.addView(filled("－ VOL",GREEN,()->key("VolumeDown")),gap(dp(60),dp(5)));
        channel.addView(filled("＋ CH",CYAN,()->key("ChannelStepUp")),gap(dp(60),dp(6)));
        channel.addView(filled("GUIDE",PURPLE,()->key("Guide")),gap(dp(51),dp(5)));
        channel.addView(filled("－ CH",CYAN,()->key("ChannelStepDown")),gap(dp(60),dp(5)));
        cols.addView(audio,new LinearLayout.LayoutParams(0,-2,1));
        cols.addView(channel,new LinearLayout.LayoutParams(0,-2,1));
        root.addView(cols,gap(dp(10),dp(10)));
        root.addView(section("MEDIA & QUICK ACCESS",CYAN),gap(dp(6),dp(14)));
        LinearLayout media=row();
        addWeight(media,"⏮",WHITE,()->key("Previous"));
        addWeight(media,"⏪",WHITE,()->key("Rewind"));
        addWeight(media,"⏯",GREEN,()->key("PlayPause"));
        addWeight(media,"⏩",WHITE,()->key("FastForward"));
        addWeight(media,"⏭",WHITE,()->key("Next"));
        root.addView(media,gap(dp(0),dp(6)));
        LinearLayout actions=row();
        addWeight(actions,"APPS",PURPLE,()->page("APPS"));
        addWeight(actions,"KANALER",CYAN,()->page("CHANNELS"));
        addWeight(actions,"AMBI",GOLD,()->page("AMBILIGHT"));
        root.addView(actions,gap(dp(0),dp(9)));
        root.addView(section("MINE GENVEJE / MACROS",PURPLE),gap(dp(7),dp(12)));
        LinearLayout macroQuick=row();
        for(int i=1;i<=3;i++){
            int which=i;
            addWeight(macroQuick,"M"+i,PURPLE,()->executeMacro(which));
        }
        root.addView(macroQuick,gap(dp(4),dp(5)));
        if(!apps.isEmpty()){
            root.addView(section("QUICK LAUNCH",CYAN),gap(dp(6),dp(13)));
            LinearLayout quick=row();int count=0;
            for(PhilipsApi.App app:apps){
                String n=app.name.toLowerCase(Locale.ROOT);
                if(n.contains("netflix")||n.contains("youtube")||n.contains("prime")||n.contains("dr tv")||n.contains("viaplay")){
                    Button b=button(app.name,WHITE,()->launch(app));
                    quick.addView(b,new LinearLayout.LayoutParams(0,dp(58),1));
                    if(++count>=3)break;
                }
            }
            if(count>0)root.addView(quick);
        }
    }
    private void channelsPage(LinearLayout root){
        root.addView(section("DIREKTE KANALVALG  /  "+channels.size()+" KANALER",CYAN));
        if(channels.isEmpty())root.addView(note("Ingen kanaler indlæst. Tryk SYNC, og par eventuelt TV'et under TOOLS."));
        EditText filter=input("Søg kanalnavn eller kanalnummer");
        root.addView(filter,gap(dp(52),dp(10)));
        LinearLayout list=vertical(BG);root.addView(list);
        filter.addTextChangedListener(watcher(q->renderChannels(list,q)));
        renderChannels(list,"");
    }
    private void renderChannels(LinearLayout view,String query){
        view.removeAllViews();
        int shown=0;String match=query.toLowerCase(Locale.ROOT).trim();
        for(PhilipsApi.Channel c:channels){
            if(!(c.name.toLowerCase(Locale.ROOT).contains(match)||c.preset.contains(match)))continue;
            LinearLayout r=row();
            boolean favorite=prefs.getBoolean("fav_channel_"+c.ccid,false);
            Button star=button(favorite?"★":"☆",favorite?GOLD:DIM,()->{
                prefs.edit().putBoolean("fav_channel_"+c.ccid,!prefs.getBoolean("fav_channel_"+c.ccid,false)).apply();
                renderChannels(view,query);
            });
            r.addView(star,new LinearLayout.LayoutParams(dp(52),dp(52)));
            Button tune=button(c.preset+"   "+c.name,WHITE,()->tune(c));
            r.addView(tune,new LinearLayout.LayoutParams(0,dp(52),1));
            view.addView(r,gap(dp(4),dp(4)));shown++;
        }
        if(shown==0&&!match.isEmpty())view.addView(note("Ingen kanaler matcher søgningen."));
    }
    private void appsPage(LinearLayout root){
        root.addView(section("INSTALLEREDE TV-APPS  /  "+apps.size(),CYAN));
        if(apps.isEmpty())root.addView(note("App-listen er tom. Forbind og par TV'et, og tryk SYNC."));
        EditText filter=input("Søg efter app");
        root.addView(filter,gap(dp(52),dp(10)));
        LinearLayout list=vertical(BG);root.addView(list);
        filter.addTextChangedListener(watcher(q->renderApps(list,q)));
        renderApps(list,"");
    }
    private void renderApps(LinearLayout root,String query){
        root.removeAllViews();
        int shown=0;
        for(PhilipsApi.App a:apps){
            if(!a.name.toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT).trim()))continue;
            Button b=button("▣   "+a.name+"    ↗",WHITE,()->launch(a));
            root.addView(b,gap(dp(61),dp(5)));shown++;
        }
        if(shown==0&&!query.isEmpty())root.addView(note("Ingen apps matcher søgningen."));
    }
    private void ambilightPage(LinearLayout root){
        root.addView(section("AMBILIGHT CONTROL  /  API V6",PURPLE));
        root.addView(note("Profiler sendes direkte til TV'et. Hvis en stil ikke understøttes af firmware, vises API-fejlen."));
        LinearLayout p=row();
        addWeight(p,"☀  TÆND",GREEN,()->ambiPower(true));
        addWeight(p,"◯  SLUK",RED,()->ambiPower(false));
        root.addView(p,gap(dp(8),dp(8)));
        String[][] presets={
            {"VIDEO · NATURAL","FOLLOW_VIDEO","NATURAL"},
            {"VIDEO · GAME","FOLLOW_VIDEO","GAME"},
            {"VIDEO · VIVID","FOLLOW_VIDEO","VIVID"},
            {"AUDIO · ENERGY","FOLLOW_AUDIO","ENERGY_ADAPTIVE_BRIGHTNESS"},
            {"AUDIO · PARTY","FOLLOW_AUDIO","PARTY"},
            {"LOUNGE · HOT LAVA","Lounge light","HOT_LAVA"},
            {"LOUNGE · DEEP WATER","Lounge light","DEEP_WATER"}
        };
        root.addView(section("STIL & PROFIL",CYAN),gap(dp(8),dp(18)));
        for(String[] preset:presets)root.addView(filled(preset[0],PURPLE,()->ambiStyle(preset[1],preset[2],preset[0])),gap(dp(59),dp(7)));
        root.addView(filled("☷  STILARTER SOM TV'ET UNDERSTØTTER",GREEN,this::selectSupportedAmbilight),gap(dp(56),dp(18)));
        root.addView(filled("↻  LÆS AKTUEL AMBILIGHT",CYAN,this::readAmbilight),gap(dp(54),dp(8)));
    }
    private void toolsPage(LinearLayout root){
        root.addView(section("TV & FORBINDELSE",CYAN));
        root.addView(note("Denne app bruger kun lokalt netværk. Credentials gemmes krypteret i Android Keystore."));
        root.addView(text("TV IP",DIM,13,true),gap(dp(23),dp(14)));
        EditText host=input("192.168.0.110");
        host.setText(prefs.getString("tv_ip","192.168.0.110"));
        root.addView(host,gap(dp(52),dp(4)));
        root.addView(filled("GEM IP OG FORBIND",CYAN,()->{
            try{String ip=PhilipsApi.privateIp(host.getText().toString());
                prefs.edit().putString("tv_ip",ip).apply();connect();
            }catch(Exception e){error(e);}
        }),gap(dp(54),dp(8)));
        root.addView(filled("PAR TV  /  4-CIFRET PIN",GREEN,this::pair),gap(dp(56),dp(7)));
        root.addView(text("WAKE-ON-LAN MAC",DIM,13,true),gap(dp(26),dp(20)));
        EditText mac=input("AA:BB:CC:DD:EE:FF");
        mac.setText(prefs.getString("tv_mac",""));
        root.addView(mac,gap(dp(52),dp(5)));
        root.addView(filled("GEM MAC-ADRESSE",CYAN,()->{
            try{String parsed=WakeOnLan.normalizeMac(mac.getText().toString());
                prefs.edit().putString("tv_mac",parsed).apply();message("MAC gemt. Prøv WAKE under REMOTE",GREEN);
            }catch(Exception e){error(e);}
        }),gap(dp(54),dp(7)));
        root.addView(filled("⚡  SEND WOL OG TJEK ONLINE",GREEN,this::wake),gap(dp(56),dp(8)));
        root.addView(section("DIREKTE INPUT / HDMI",CYAN),gap(dp(8),dp(20)));
        String[][] inputs={{"LIVE TV","content://android.media.tv/channel"},{"HDMI 1","HW5"},
            {"HDMI 2","HW6"},{"HDMI 3","HW7"},{"HDMI 4","HW8"}};
        for(String[] route:inputs)root.addView(filled("▣  "+route[0],WHITE,()->selectInput(route[1],route[0])),gap(dp(53),dp(5)));
        root.addView(note("Input-routing er firmwareafhængig. Brug SOURCE-knappen, hvis direkte HDMI ikke accepteres."));
        root.addView(section("PROGRAMMERBARE MACROS",PURPLE),gap(dp(8),dp(20)));
        root.addView(note("Gem en sekvens af TV-taster, fx Home,CursorDown,Confirm. Kør via M1–M3 på fjernbetjeningen."));
        for(int i=1;i<=3;i++){
            int which=i;
            LinearLayout actions=row();
            addWeight(actions,"▶  M"+i,PURPLE,()->executeMacro(which));
            addWeight(actions,"REDIGER M"+i,CYAN,()->editMacro(which));
            root.addView(actions,gap(dp(6),dp(6)));
        }
        root.addView(section("AVANCERET",CYAN),gap(dp(6),dp(22)));
        root.addView(filled("GENINDLÆS TV / APPS / KANALER",WHITE,this::connect),gap(dp(54),dp(5)));
        root.addView(filled("NUMERISK TASTATUR & SPECIALTASTER",WHITE,this::keypadDialog),gap(dp(54),dp(5)));
        root.addView(filled("GLEM PARRING",RED,()->{
            new AlertDialog.Builder(this).setTitle("Slet gemt TV-parring?")
                .setNegativeButton("Annuller",null).setPositiveButton("Slet",(d,w)->{
                    if(api!=null)api.clearPairing();message("Parring slettet. Tryk PAR TV",GOLD);connect();
                }).show();
        }),gap(dp(54),dp(5)));
        root.addView(filled("NULSTIL TV-CERTIFIKATPIN",RED,()->{
            new AlertDialog.Builder(this).setTitle("Nulstil certifikatpin?")
                .setMessage("Brug kun dette hvis TV-certifikatet bevidst er ændret. Kontroller TV-IP og netværk først.")
                .setNegativeButton("Annuller",null).setPositiveButton("Nulstil",(d,w)->{
                    if(api!=null)api.clearCertificatePin();message("Certifikatpin nulstillet",GOLD);
                }).show();
        }),gap(dp(54),dp(5)));
        if(api!=null&&api.pinned())root.addView(note("TV TLS SHA-256: "+api.certificateFingerprint()));
    }
    private void keypadDialog(){
        ScrollView sc=new ScrollView(this);
        LinearLayout body=vertical(BG);body.setPadding(dp(12),dp(12),dp(12),dp(12));
        String[][] keys={{"1","Digit1"},{"2","Digit2"},{"3","Digit3"},
            {"4","Digit4"},{"5","Digit5"},{"6","Digit6"},{"7","Digit7"},
            {"8","Digit8"},{"9","Digit9"},{"0","Digit0"},
            {"INFO","Info"},{"GUIDE","Guide"},{"OPTIONS","Options"},
            {"SUBTITLE","Subtitle"},{"WATCH TV","WatchTV"},{"TELETEXT","Teletext"},
            {"RED","RedColour"},{"GREEN","GreenColour"},{"YELLOW","YellowColour"},{"BLUE","BlueColour"},
            {"STOP","Stop"},{"RECORD","Record"},{"PLAY","PlayPause"}};
        GridLayout grid=new GridLayout(this);grid.setColumnCount(3);
        for(String[] entry:keys){
            Button b=button(entry[0],CYAN,()->key(entry[1]));
            GridLayout.LayoutParams lp=new GridLayout.LayoutParams();
            lp.width=0;lp.height=dp(56);lp.columnSpec=GridLayout.spec(GridLayout.UNDEFINED,1f);
            lp.setMargins(dp(2),dp(2),dp(2),dp(2));grid.addView(b,lp);
        }
        body.addView(grid);sc.addView(body);
        new AlertDialog.Builder(this).setTitle("Tastatur & funktioner").setView(sc).setPositiveButton("Luk",null).show();
    }

    private void connect(){
        final PhilipsApi target;
        try{
            target=new PhilipsApi(this,prefs.getString("tv_ip","192.168.0.110"));
            api=target;
        }catch(Exception e){error(e);return;}
        message("CONNECTING  •  "+target.host(),GOLD);
        run("CONNECT",()->{
            JSONObject sys=target.system();
            List<PhilipsApi.App> loadedApps=new ArrayList<>();
            List<PhilipsApi.Channel> loadedChannels=new ArrayList<>();
            JSONObject volume=null,current=null;
            if(target.paired()){
                try{loadedApps.addAll(PhilipsApi.parseApps(target.applications()));}catch(Exception ignored){}
                try{loadedChannels.addAll(PhilipsApi.parseChannels(target.channels()));}catch(Exception ignored){}
                try{volume=target.volume();}catch(Exception ignored){}
                try{current=target.currentChannel();}catch(Exception ignored){}
            }
            return new SyncResult(sys,loadedApps,loadedChannels,volume,current,target.paired());
        },result->{
            SyncResult data=(SyncResult)result;
            apps.clear();apps.addAll(data.apps);
            channels.clear();channels.addAll(data.channels);
            tvName=data.system.optString("name","Philips OLED");
            online=true;
            currentVolume=data.volume==null?-1:data.volume.optInt("current",-1);
            channelName=data.current==null?"TV online":data.current.optString("name",data.current.optJSONObject("channel")!=null?
                data.current.optJSONObject("channel").optString("name","TV online"):"TV online");
            message("● ONLINE  •  "+apps.size()+" apps  •  "+channels.size()+" kanaler"+(data.paired?"":"  •  PAR TV"),GREEN);
            page(page);
        });
    }
    private void pair(){
        if(api==null){message("Indstil TV-IP først",RED);return;}
        run("PAIR",api::beginPair,result->{
            PhilipsApi.PairRequest p=(PhilipsApi.PairRequest)result;
            EditText pin=input("4 cifre fra TV");
            pin.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_VARIATION_PASSWORD);
            LinearLayout wrap=vertical(BG);wrap.setPadding(dp(20),dp(4),dp(20),dp(4));
            wrap.addView(pin,gap(dp(55),dp(0)));
            new AlertDialog.Builder(this).setTitle("Par med Philips TV")
                .setMessage("Indtast koden som vises på TV'et. Timeout: "+p.timeout+" sek.")
                .setView(wrap).setNegativeButton("Annuller",null)
                .setPositiveButton("PAR",(d,w)->run("PIN",()->{api.grant(p,pin.getText().toString().trim());return true;},
                    ok->{message("Parret og sikkert gemt",GREEN);connect();})).show();
        });
    }
    private void wake(){
        String mac=prefs.getString("tv_mac","");
        if(mac.isEmpty()){page("TOOLS");message("Angiv TV MAC under TOOLS før Wake-on-LAN",GOLD);return;}
        final String host=prefs.getString("tv_ip","192.168.0.110");
        run("WOL",()->{
            WakeOnLan.send(getApplicationContext(),mac,host);
            boolean awake=false;
            for(int i=0;i<4&&!awake;i++){
                try{Thread.sleep(1700);new PhilipsApi(this,host).system();awake=true;}catch(Exception ignored){}
            }
            return awake;
        },result->{
            boolean awake=(Boolean)result;
            message(awake?"TV SVARER  •  Wake-on-LAN bekræftet":"WOL SENDT  •  TV svarer endnu ikke. Kontrollér Network Standby",
                awake?GREEN:GOLD);
            if(awake)connect();
        });
    }
    private void key(String code){if(api==null){message("Tryk SYNC først",GOLD);return;}
        run("KEY",()->api.sendKey(code),r->message("✓  "+code,GREEN));}
    private void launch(PhilipsApi.App a){if(api==null)return;
        run("APP",()->api.launch(a),r->message("Åbnet  •  "+a.name,GREEN));}
    private void tune(PhilipsApi.Channel c){if(api==null)return;
        run("KANAL",()->api.tune(c),r->{channelName=c.name;message("Valgt  •  "+c.name,GREEN);page(page);});}
    private String macroDefault(int which){
        return which==1?"Home":which==2?"WatchTV":"AmbilightOnOff";
    }
    private void editMacro(int which){
        EditText field=input("Home,CursorDown,Confirm");
        field.setText(prefs.getString("macro_"+which,macroDefault(which)));
        LinearLayout wrap=vertical(BG);wrap.setPadding(dp(15),dp(7),dp(15),dp(7));
        wrap.addView(field,gap(dp(56),dp(0)));
        new AlertDialog.Builder(this).setTitle("Rediger Macro M"+which)
            .setMessage("Skriv op til 12 tastetryk adskilt af komma. Eksempel: Home,CursorDown,Confirm")
            .setView(wrap).setNegativeButton("Annuller",null)
            .setPositiveButton("GEM",(d,w)->{
                try{String validated=validateMacro(field.getText().toString());
                    prefs.edit().putString("macro_"+which,validated).apply();
                    message("Macro M"+which+" gemt",GREEN);
                }catch(Exception e){error(e);}
            }).show();
    }
    private String validateMacro(String raw){
        String[] commands=raw.trim().split(",");
        if(commands.length<1||commands.length>12)throw new IllegalArgumentException("Macro må have 1–12 kommandoer");
        String allowed="|Home|Back|Source|WatchTV|Confirm|CursorUp|CursorDown|CursorLeft|CursorRight|"+
            "VolumeUp|VolumeDown|Mute|ChannelStepUp|ChannelStepDown|PlayPause|Stop|Next|Previous|"+
            "Options|Info|Guide|AmbilightOnOff|Digit0|Digit1|Digit2|Digit3|Digit4|Digit5|Digit6|Digit7|Digit8|Digit9|";
        for(String key:commands)if(key.trim().isEmpty()||!allowed.contains("|"+key.trim()+"|"))
            throw new IllegalArgumentException("Ukendt tast: "+key.trim());
        StringBuilder normalized=new StringBuilder();
        for(String key:commands){if(normalized.length()>0)normalized.append(",");normalized.append(key.trim());}
        return normalized.toString();
    }
    private void executeMacro(int which){
        if(api==null)return;
        String commands=prefs.getString("macro_"+which,macroDefault(which));
        final String normalized;
        try{normalized=validateMacro(commands);}catch(Exception e){error(e);return;}
        run("MACRO M"+which,()->{
            for(String key:normalized.split(",")){
                api.sendKey(key);Thread.sleep(220);
            }
            return true;
        },r->message("✓  Macro M"+which+" gennemført",GREEN));
    }
    private void selectInput(String id,String name){if(api==null)return;
        run("INPUT",()->api.selectSource(id),r->message("Kilde valgt  •  "+name,GREEN));
    }
    private void selectSupportedAmbilight(){
        if(api==null)return;
        run("STILARTER",api::supportedAmbilightStyles,result->{
            JSONObject config=(JSONObject)result;
            org.json.JSONArray styles=config.optJSONArray("supportedStyles");
            if(styles==null||styles.length()==0){
                message("Ingen understøttede Ambilight-profiler returneret",GOLD);return;
            }
            ArrayList<String> labels=new ArrayList<>();
            ArrayList<String[]> commands=new ArrayList<>();
            for(int i=0;i<styles.length();i++){
                JSONObject style=styles.optJSONObject(i);
                if(style==null)continue;
                String name=style.optString("styleName","");
                if(name.isEmpty())continue;
                org.json.JSONArray alg=style.optJSONArray("algorithms");
                if(alg==null||alg.length()==0){
                    labels.add(name);
                    commands.add(new String[]{name,name.equals("FOLLOW_VIDEO")?"NATURAL":"STANDARD"});
                }else{
                    for(int j=0;j<alg.length();j++){
                        String setting=alg.optString(j,"");
                        if(setting.isEmpty())continue;
                        labels.add(name+" · "+setting);
                        commands.add(new String[]{name,setting});
                    }
                }
            }
            new AlertDialog.Builder(this).setTitle("Ambilight-stilarter fra dit TV")
                .setItems(labels.toArray(new String[0]),(dialog,which)->{
                    String[] selection=commands.get(which);
                    if(selection[0].equals("OFF"))ambiPower(false);
                    else ambiStyle(selection[0],selection[1],labels.get(which));
                }).setNegativeButton("Luk",null).show();
        });
    }
    private void ambiPower(boolean on){if(api==null)return;
        run("AMBI",()->api.ambilightPower(on),r->message("Ambilight "+(on?"tændt":"slukket"),GREEN));}
    private void ambiStyle(String style,String setting,String name){if(api==null)return;
        run("AMBI",()->api.ambilightStyle(style,setting),r->message("Ambilight  •  "+name,GREEN));}
    private void readAmbilight(){if(api==null)return;
        run("AMBI",api::ambilight,r->new AlertDialog.Builder(this).setTitle("Aktuel Ambilight-konfiguration")
            .setMessage(((JSONObject)r).toString()).setPositiveButton("OK",null).show());}

    private interface Job{Object run()throws Exception;}
    private interface Done{void accept(Object value);}
    private void run(String name,Job job,Done done){
        message("⟳  "+name+"…",CYAN);
        io.execute(()->{
            try{Object result=job.run();runOnUiThread(()->done.accept(result));}
            catch(Exception e){runOnUiThread(()->error(e));}
        });
    }
    private void error(Exception e){message("FEJL  •  "+(e.getMessage()==null?e.getClass().getSimpleName():e.getMessage()),RED);}
    private void message(String text,int colour){if(status!=null){status.setText(text);status.setTextColor(colour);}}
    private interface Search{void onChange(String value);}
    private TextWatcher watcher(Search handler){
        return new TextWatcher(){
            @Override public void beforeTextChanged(CharSequence s,int st,int count,int after){}
            @Override public void onTextChanged(CharSequence s,int st,int before,int count){handler.onChange(s.toString());}
            @Override public void afterTextChanged(Editable e){}
        };
    }
    private LinearLayout vertical(int color){LinearLayout l=new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);l.setBackgroundColor(color);return l;}
    private LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    private LinearLayout panel(){LinearLayout l=vertical(PANEL);
        l.setBackground(round(PANEL,STROKE,18));return l;}
    private TextView text(String s,int color,int sp,boolean bold){
        TextView t=new TextView(this);t.setText(s);t.setTextColor(color);t.setTextSize(sp);
        if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);t.setGravity(Gravity.CENTER_VERTICAL);return t;
    }
    private TextView section(String s,int c){TextView t=text(s,c,12,true);t.setLetterSpacing(.11f);
        t.setPadding(dp(6),dp(5),dp(5),dp(6));return t;}
    private TextView note(String s){TextView t=text(s,DIM,14,false);
        t.setPadding(dp(14),dp(13),dp(14),dp(13));t.setBackground(round(PANEL,STROKE,14));return t;}
    private EditText input(String hint){EditText e=new EditText(this);e.setSingleLine(true);
        e.setHint(hint);e.setTextColor(WHITE);e.setHintTextColor(DIM);e.setTextSize(16);
        e.setPadding(dp(15),0,dp(15),0);e.setBackground(round(PANEL,STROKE,13));return e;}
    private GradientDrawable round(int bg,int stroke,int radius){GradientDrawable d=new GradientDrawable();
        d.setColor(bg);d.setStroke(dp(1),stroke);d.setCornerRadius(dp(radius));return d;}
    private Button button(String s,int color,Runnable action){
        Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextSize(13);
        b.setTextColor(color);b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        b.setBackground(round(PANEL2,STROKE,14));b.setPadding(dp(5),0,dp(5),0);
        b.setOnClickListener(v->{v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);action.run();});
        return b;
    }
    private Button filled(String s,int c,Runnable action){return button(s,c,action);}
    private void addWeight(LinearLayout row,String s,int color,Runnable action){
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(57),1);
        p.setMargins(dp(3),dp(3),dp(3),dp(3));row.addView(button(s,color,action),p);
    }
    private LinearLayout.LayoutParams gap(int height,int top){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,height<dp(30)?ViewGroup.LayoutParams.WRAP_CONTENT:height);
        p.topMargin=top;return p;}
    private LinearLayout.LayoutParams match(){return new LinearLayout.LayoutParams(-1,-2);}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}

    private static final class SyncResult{
        final JSONObject system,volume,current;final List<PhilipsApi.App> apps;final List<PhilipsApi.Channel> channels;
        final boolean paired;
        SyncResult(JSONObject sys,List<PhilipsApi.App> a,List<PhilipsApi.Channel> c,JSONObject v,JSONObject current,boolean paired){
            system=sys;apps=a;channels=c;volume=v;this.current=current;this.paired=paired;
        }
    }
}
