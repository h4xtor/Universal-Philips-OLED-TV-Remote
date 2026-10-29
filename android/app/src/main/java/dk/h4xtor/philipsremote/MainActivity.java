package dk.h4xtor.philipsremote;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.*;
import java.net.*;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
  private final java.util.concurrent.ExecutorService io=Executors.newSingleThreadExecutor();
  private TextView status;
  private EditText mac;
  private int blue=Color.rgb(52,154,238);
  @Override public void onCreate(Bundle b) {
    super.onCreate(b);
    LinearLayout root=new LinearLayout(this);
    root.setOrientation(LinearLayout.VERTICAL); root.setPadding(32,50,32,32);
    root.setBackgroundColor(Color.rgb(9,14,24));
    ScrollView sc=new ScrollView(this); sc.addView(root); setContentView(sc);
    TextView heading=new TextView(this); heading.setText("H4XTOR  /  OLED REMOTE");
    heading.setTextColor(Color.WHITE); heading.setTextSize(25); heading.setTypeface(null,Typeface.BOLD); root.addView(heading);
    TextView sub=new TextView(this); sub.setText("Philips 65OLED865/12 • S24 Ultra"); sub.setTextColor(0xff94a8c0); sub.setTextSize(14); root.addView(sub);
    status=new TextView(this); status.setText("v3 alpha • Wake-on-LAN test • API remote under migration");
    status.setTextColor(0xff9ed2ff); status.setPadding(0,24,0,18); root.addView(status);
    mac=new EditText(this); mac.setSingleLine(true); mac.setTextColor(Color.WHITE);
    mac.setHintTextColor(0xff8090a0); mac.setHint("TV MAC (AA:BB:CC:DD:EE:FF)");
    mac.setText(getPreferences(0).getString("mac","")); root.addView(mac);
    Button wake=button("WAKE TV  •  SEND MAGIC PACKET",root);
    wake.setOnClickListener(v->wake());
    TextView notice=new TextView(this);
    notice.setText("Denne alpha er en installations- og Wake-on-LAN-test. Kanalvalg, apps, Ambilight, parring og volumen er endnu ikke migreret fra Windows/Android-kildeprojektet. TV skal understøtte network standby.");
    notice.setTextColor(0xffb9c5d4); notice.setTextSize(15); notice.setPadding(0,32,0,0); root.addView(notice);
  }
  private Button button(String s,LinearLayout root) {
    Button b=new Button(this); b.setText(s); b.setTextColor(Color.WHITE);
    GradientDrawable bg=new GradientDrawable();bg.setColor(blue);bg.setCornerRadius(24);b.setBackground(bg);
    LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,150);p.topMargin=20;root.addView(b,p);return b;
  }
  private void wake() {
    String address=mac.getText().toString().trim().replace("-","").replace(":","");
    if(!address.matches("(?i)[0-9a-f]{12}")) {status.setText("Ugyldig MAC-adresse");return;}
    getPreferences(0).edit().putString("mac",mac.getText().toString().trim()).apply();
    status.setText("Sender Wake-on-LAN…");
    io.execute(()->{
      try {
        byte[] m=new byte[6];for(int i=0;i<6;i++)m[i]=(byte)Integer.parseInt(address.substring(i*2,i*2+2),16);
        byte[] packet=new byte[102];java.util.Arrays.fill(packet,0,6,(byte)0xff);
        for(int i=6;i<102;i++)packet[i]=m[(i-6)%6];
        try(DatagramSocket s=new DatagramSocket()) {
          s.setBroadcast(true);
          for(String ip:new String[]{"255.255.255.255","192.168.0.255"}) {
            DatagramPacket p=new DatagramPacket(packet,packet.length,InetAddress.getByName(ip),9);
            for(int j=0;j<3;j++)s.send(p);
          }
        }
        runOnUiThread(()->status.setText("Magic packet sendt. TV-opvågning er ikke verificeret."));
      }catch(Exception e){runOnUiThread(()->status.setText("WoL-fejl: "+e.getMessage()));}
    });
  }
  @Override protected void onDestroy(){io.shutdownNow();super.onDestroy();}
}
