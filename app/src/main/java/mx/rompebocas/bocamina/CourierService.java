package mx.rompebocas.bocamina;

import android.Manifest;
import android.app.*;
import android.bluetooth.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/** Secure RFCOMM exchange with previously paired Bluetooth Classic devices (no Internet). */
@SuppressWarnings("MissingPermission")
public final class CourierService extends Service {
    private static final UUID APP_UUID=UUID.fromString("6c513aae-e788-4a43-8414-5c4e450c9b51");
    private static final int MAX_FRAME=400000;
    private static volatile CourierService current;
    public static CourierService instance(){return current;}
    public interface Listener {void changed(String message);}
    private volatile Listener listener;
    public void listen(Listener l){listener=l;}
    public void unlisten(Listener l){if(listener==l)listener=null;}
    private final ExecutorService pool=Executors.newCachedThreadPool();
    private BluetoothServerSocket server;
    private volatile boolean working=true;
    private Store store;
    private BundleRouter router;
    private void update(String msg){Listener l=listener;if(l!=null)l.changed(msg);}
    private boolean permitted(){return Build.VERSION.SDK_INT<31||checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)==PackageManager.PERMISSION_GRANTED;}
    @Override public void onCreate(){super.onCreate();current=this;
        NotificationManager manager=getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel("courier","BOCAMINA · carteros",NotificationManager.IMPORTANCE_LOW));
        Notification notification=new Notification.Builder(this,"courier")
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("BOCAMINA · buzón DTN")
            .setContentText("Mensajes guardados; escucha Bluetooth cuando está permitido")
            .setOngoing(true).setContentIntent(PendingIntent.getActivity(this,1,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT)).build();
        if(Build.VERSION.SDK_INT>=29)startForeground(53,notification,android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);
        else startForeground(53,notification);
        try{store=new Store(this);router=new BundleRouter(store);if(permitted())pool.execute(this::acceptLoop);}catch(Exception e){update("Error en buzón: "+e.getMessage());stopSelf();}
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId){return START_STICKY;}
    @Override public android.os.IBinder onBind(Intent intent){return null;}
    private void acceptLoop(){
        if(!permitted())return;
        BluetoothAdapter adapter=BluetoothAdapter.getDefaultAdapter();
        if(adapter==null||!adapter.isEnabled()){update("Activa Bluetooth para recibir visitas de carteros");return;}
        try(BluetoothServerSocket opened=adapter.listenUsingRfcommWithServiceRecord("BOCAMINA-PLUS",APP_UUID)){
            server=opened;update("Cartero Bluetooth listo");
            while(working){
                BluetoothSocket socket=opened.accept();
                if(socket!=null)pool.execute(()->session(socket,false));
            }
        }catch(Exception e){if(working)update("Receptor Bluetooth detenido: "+e.getMessage());}
    }
    /** Trigger a manual encounter with a bonded Bluetooth device. */
    public void visit(String address){pool.execute(()->{
        try{
            if(!permitted())throw new SecurityException("Permiso de dispositivos cercanos requerido");
            BluetoothAdapter adapter=BluetoothAdapter.getDefaultAdapter();
            if(adapter==null||!adapter.isEnabled())throw new IOException("Bluetooth desactivado");
            BluetoothDevice matched=null;
            for(BluetoothDevice d:adapter.getBondedDevices())if(d.getAddress().equals(address)){matched=d;break;}
            if(matched==null)throw new IOException("Primero empareja el teléfono desde Ajustes de Android");
            try(BluetoothSocket sock=matched.createRfcommSocketToServiceRecord(APP_UUID)){
                sock.connect();session(sock,true);
            }
        }catch(Exception e){update("No pudo sincronizarse: "+e.getMessage());}
    });}
    private static void write(DataOutputStream out,JSONObject obj)throws Exception{
        byte[] bytes=CryptoEngine.utf8(obj.toString());if(bytes.length>MAX_FRAME)throw new IOException("Lote excesivo");
        out.writeInt(bytes.length);out.write(bytes);out.flush();
    }
    private static JSONObject read(DataInputStream in)throws Exception{
        int length=in.readInt();if(length<2||length>MAX_FRAME)throw new SecurityException("Lote inválido");
        byte[] bytes=new byte[length];in.readFully(bytes);
        return new JSONObject(new String(bytes,java.nio.charset.StandardCharsets.UTF_8));
    }
    private JSONObject outgoing() throws Exception {
        JSONArray arr=new JSONArray();for(Bundle b:router.forwarding(25))arr.put(b.serialized());
        return new JSONObject().put("protocol","BOCAMINA-BT1").put("bundles",arr);
    }
    private int absorb(JSONObject packet)throws Exception{
        if(!"BOCAMINA-BT1".equals(packet.getString("protocol")))throw new SecurityException("Protocolo desconocido");
        JSONArray array=packet.getJSONArray("bundles");if(array.length()>25)throw new SecurityException("Demasiados paquetes");
        int count=0;
        for(int i=0;i<array.length();i++){
            try{BundleRouter.Outcome outcome=router.receive(array.getString(i));if(outcome.accepted)count++;}
            catch(Exception ignored){} // Skip corrupted/unauthorized individual bundles; do not abort batch.
        }
        return count;
    }
    private void session(BluetoothSocket socket,boolean initiator){
        try(BluetoothSocket s=socket){
            DataInputStream in=new DataInputStream(new BufferedInputStream(s.getInputStream()));
            DataOutputStream out=new DataOutputStream(new BufferedOutputStream(s.getOutputStream()));
            int accepted;
            if(initiator){write(out,outgoing());accepted=absorb(read(in));}
            else{accepted=absorb(read(in));write(out,outgoing());}
            update("Sincronización Bluetooth: "+accepted+" nuevos paquetes recibidos");
        }catch(Exception e){update("Intercambio Bluetooth interrumpido: "+e.getMessage());}
    }
    public static List<BluetoothDevice> bonded(Context c){
        if(Build.VERSION.SDK_INT>=31&&c.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED)return Collections.emptyList();
        BluetoothAdapter a=BluetoothAdapter.getDefaultAdapter();if(a==null||!a.isEnabled())return Collections.emptyList();
        return new ArrayList<>(a.getBondedDevices());
    }
    @Override public void onDestroy(){working=false;try{if(server!=null)server.close();}catch(Exception ignored){}pool.shutdownNow();if(store!=null)store.close();current=null;super.onDestroy();}
}
