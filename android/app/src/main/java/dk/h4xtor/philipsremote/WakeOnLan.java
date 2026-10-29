package dk.h4xtor.philipsremote;

import android.content.Context;
import android.net.DhcpInfo;
import android.net.wifi.WifiManager;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** LAN WOL helper. Sending a packet does not prove the target TV woke up. */
public final class WakeOnLan {
    private WakeOnLan(){}
    public static String normalizeMac(String value){
        if(value==null)throw new IllegalArgumentException("TV MAC-adresse mangler");
        String m=value.replace(":","").replace("-","").replace(".","").trim().toUpperCase(Locale.ROOT);
        if(!m.matches("[0-9A-F]{12}"))throw new IllegalArgumentException("MAC skal have 12 hex-tegn");
        return m;
    }
    public static void send(Context context,String macValue,String host)throws Exception{
        String mac=normalizeMac(macValue);
        String ip=PhilipsApi.privateIp(host);
        byte[] address=new byte[6];
        for(int i=0;i<6;i++)address[i]=(byte)Integer.parseInt(mac.substring(i*2,i*2+2),16);
        byte[] payload=new byte[102];
        java.util.Arrays.fill(payload,0,6,(byte)0xff);
        for(int i=6;i<102;i++)payload[i]=address[(i-6)%6];
        List<String> destinations=new ArrayList<>();
        WifiManager wifi=(WifiManager)context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if(wifi!=null){
            DhcpInfo info=wifi.getDhcpInfo();
            if(info!=null&&info.netmask!=0){
                int broadcast=(info.ipAddress & info.netmask)|~info.netmask;
                byte[] bytes=new byte[]{(byte)broadcast,(byte)(broadcast>>8),(byte)(broadcast>>16),(byte)(broadcast>>24)};
                destinations.add(InetAddress.getByAddress(bytes).getHostAddress());
            }
        }
        String[] octets=ip.split("\\.");
        String fallback=octets[0]+"."+octets[1]+"."+octets[2]+".255";
        if(!destinations.contains(fallback))destinations.add(fallback);
        destinations.add("255.255.255.255");
        try(DatagramSocket socket=new DatagramSocket()){
            socket.setBroadcast(true);
            for(String target:destinations){
                DatagramPacket p=new DatagramPacket(payload,payload.length,InetAddress.getByName(target),9);
                for(int j=0;j<3;j++)socket.send(p);
            }
        }
    }
}
