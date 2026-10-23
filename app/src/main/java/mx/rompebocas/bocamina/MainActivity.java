package mx.rompebocas.bocamina;

import android.Manifest;
import android.app.*;
import android.bluetooth.BluetoothDevice;
import android.content.*;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.view.*;
import android.widget.*;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;
import java.util.*;

/** Offline-only native interface. Every displayed action corresponds to a concrete local operation. */
public final class MainActivity extends Activity implements CourierService.Listener {
    private static final int BG=0xFF110F0C,PANEL=0xFF292117,PANEL2=0xFF3A2B1E,GOLD=0xFFEAC38B,
        INK=0xFFF4ECDD,MUTED=0xFFBDAE97,GREEN=0xFF99DDA7;
    private final Handler main=new Handler(Looper.getMainLooper());
    private Store db;
    private BundleRouter router;
    private LinearLayout page,root;
    private TextView banner;
    private Store.Contact chatWith;
    private final QrCarrier.Collector collector=new QrCarrier.Collector();
    private Runnable qrTick;
    private String tab="mina";
    @Override public void onCreate(android.os.Bundle bundle){super.onCreate(bundle);
        getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
        if(Build.VERSION.SDK_INT>=30)getWindow().setDecorFitsSystemWindows(false);
        else getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        try{db=new Store(this);router=new BundleRouter(db);drawHome();permissionsAndStart();}
        catch(Exception e){new AlertDialog.Builder(this).setTitle("Buzón cifrado inaccesible").setMessage(e.getMessage()).setCancelable(false).setPositiveButton("Cerrar",(d,w)->finish()).show();}
    }
    private void permissionsAndStart(){
        if(Build.VERSION.SDK_INT>=31 && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED){
            requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT},31);return;
        }
        activateCourier();
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},33);
    }
    private void activateCourier(){
        try{startForegroundService(new Intent(this,CourierService.class));}
        catch(Exception e){toast("Cartero Bluetooth inactivo: "+e.getMessage());}
    }
    @Override public void onRequestPermissionsResult(int request,String[] ps,int[] grants){super.onRequestPermissionsResult(request,ps,grants);
        if(request==31&&grants.length>0&&grants[0]==PackageManager.PERMISSION_GRANTED)activateCourier();
        if(request==31&&grants.length>0&&grants[0]!=PackageManager.PERMISSION_GRANTED)toast("Los códigos QR siguen disponibles; Bluetooth requiere permiso");
        drawHome();
    }
    @Override protected void onResume(){super.onResume();main.postDelayed(()->{CourierService s=CourierService.instance();if(s!=null)s.listen(this);},250);}
    @Override protected void onPause(){CourierService s=CourierService.instance();if(s!=null)s.unlisten(this);super.onPause();}
    @Override protected void onDestroy(){stopQr();if(db!=null)db.close();super.onDestroy();}
    @Override public void changed(String text){runOnUiThread(()->{if(banner!=null)banner.setText(text);if(tab.equals("carteros"))drawCouriers();else if(tab.equals("mina")&&chatWith==null)drawHome();});}
    private int dp(float v){return (int)(getResources().getDisplayMetrics().density*v+0.5f);}
    private GradientDrawable bg(int color,int stroke){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(15));if(stroke!=0)g.setStroke(dp(1),stroke);return g;}
    private LinearLayout vertical(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private LinearLayout horizontal(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    private TextView label(String s,int size,int color,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);if(bold)t.setTypeface(Typeface.create("serif",Typeface.BOLD));return t;}
    private void space(LinearLayout l,int value){View v=new View(this);l.addView(v,new LinearLayout.LayoutParams(1,dp(value)));}
    private void addText(LinearLayout l,String s,int size,int color,boolean bold){l.addView(label(s,size,color,bold));}
    private Button button(String title,boolean prominent,Runnable action){Button b=new Button(this);b.setAllCaps(false);b.setText(title);b.setTextSize(14);b.setTextColor(prominent?BG:GOLD);b.setBackground(bg(prominent?GOLD:PANEL2,prominent?0:0xFF6B5338));b.setOnClickListener(v->action.run());return b;}
    private void addButton(LinearLayout l,String s,boolean primary,Runnable r){Button b=button(s,primary,r);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(50));lp.topMargin=dp(8);l.addView(b,lp);}
    private EditText input(String hint){EditText e=new EditText(this);e.setTextColor(INK);e.setHintTextColor(MUTED);e.setTextSize(16);e.setSingleLine(false);e.setMinLines(1);e.setHint(hint);e.setPadding(dp(14),dp(10),dp(14),dp(10));e.setBackground(bg(PANEL,0xFF614B35));return e;}
    private LinearLayout card(){LinearLayout c=vertical();c.setPadding(dp(16),dp(14),dp(16),dp(14));c.setBackground(bg(PANEL,0xFF463625));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(12);page.addView(c,p);return c;}
    private void layout(String subtitle){stopQr();banner=null;
        root=vertical();root.setBackgroundColor(BG);
        ScrollView sc=new ScrollView(this);sc.setFillViewport(true);sc.setClipToPadding(false);
        page=vertical();page.setPadding(dp(16),dp(13),dp(16),dp(26));sc.addView(page);
        root.addView(sc,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);
        root.setOnApplyWindowInsetsListener((view,insets)->{
            int top,bottom;if(Build.VERSION.SDK_INT>=30){android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
                android.graphics.Insets keyboard=insets.getInsets(WindowInsets.Type.ime());top=bars.top;bottom=Math.max(bars.bottom,keyboard.bottom);
            }else{top=insets.getSystemWindowInsetTop();bottom=insets.getSystemWindowInsetBottom();}
            view.setPadding(0,top,0,bottom);return insets;
        });root.requestApplyInsets();
        addText(page,"⛏  BOCAMINA",30,GOLD,true);
        addText(page,"DEL PUEBLO PARA EL PUEBLO  ·  SIN INTERNET",11,MUTED,true);
        space(page,9);addText(page,subtitle,15,INK,false);space(page,18);
    }
    private void nav(){LinearLayout row=horizontal();String[] names={"Mina","Contactos","Carteros"};String[] codes={"mina","contactos","carteros"};
        for(int i=0;i<names.length;i++){
            final int pos=i;Button b=button(names[i],codes[i].equals(tab),()->{chatWith=null;if(pos==0)drawHome();else if(pos==1)drawContacts();else drawCouriers();});
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(45),1);lp.setMargins(dp(2),0,dp(2),0);row.addView(b,lp);
        }page.addView(row);space(page,17);
    }
    private void drawHome(){tab="mina";chatWith=null;layout("El mensaje se mueve como el mercurio: por donde haya hueco.");nav();
        try{
            LinearLayout stat=card();addText(stat,"Buzón soberano cifrado",19,GOLD,true);
            addText(stat,"Paquetes esperando cartero: "+router.waiting(),17,INK,true);
            addText(stat,"Tu identidad: "+db.myId(),12,MUTED,false);
            addButton(stat,"📷  Mostrar mi identidad QR",false,()->showQr(dbCode(),"MI IDENTIDAD"));
            addButton(stat,"➕  Escanear QR / recibir acarreo",true,this::scanQr);
            List<Store.Stored> bundles=db.bundles();int visible=0;
            for(Store.Stored entry:bundles){Bundle b=entry.bundle;
                if(!b.kind().equals("message"))continue;
                Store.Contact peer=db.getContact(b.from().equals(db.myId())?b.to():b.from());
                if(!b.from().equals(db.myId())&& !b.to().equals(db.myId()))continue;
                LinearLayout c=card();String title=peer==null?b.from():peer.name;
                addText(c,title,17,GOLD,true);
                addText(c,entry.note.isEmpty()?"[Contenido cifrado — pendiente de vincular remitente]":entry.note,15,INK,false);
                addText(c,"Estado: "+stateLabel(entry.status)+"  ·  "+(b.priority()==2?"URGENTE":"normal"),12,MUTED,false);
                addButton(c,"Transportar QR",false,()->showQr(b.serialized(),"MENSAJE CIFRADO"));visible++;if(visible>=30)break;
            }
            if(visible==0){LinearLayout c=card();addText(c,"Tu mina está lista",19,GOLD,true);
                addText(c,"Agrega un contacto, escribe un mensaje y entrégalo por Bluetooth o mediante fotogramas QR.",14,INK,false);}
        }catch(Exception e){failure(e);}
    }
    private String stateLabel(String s){switch(s){case "mina":return "En la mina";case "camino":return "En camino";case "entregado":return "Entregado ✔";case "recibido":return "Recibido";case "caducado":return "Caducado";default:return s;}}
    private String dbCode(){try{return db.identityCode();}catch(Exception e){failure(e);return "";}}
    private void drawContacts(){tab="contactos";chatWith=null;layout("Identidades verificables, sin agenda telefónica ni número SIM.");nav();
        LinearLayout top=card();addButton(top,"📷  Escanear identidad QR",true,this::scanQr);
        addButton(top,"📝  Pegar código de contacto",false,this::pasteCode);
        addButton(top,"📜  Compartir mi QR",false,()->showQr(dbCode(),"MI IDENTIDAD"));
        try{for(Store.Contact p:db.contacts()){
            LinearLayout c=card();addText(c,p.name,19,GOLD,true);addText(c,"ID: "+p.id,12,MUTED,false);
            addButton(c,"💬  Abrir conversación",true,()->drawChat(p));
            addButton(c,"🔐  Ver huella criptográfica",false,()->{
                try{alert("Verifica a "+p.name,"Huella SHA-256 de su clave:\n"+CryptoEngine.fingerprint(p.key())+"\n\nConfirma en persona o por un canal independiente.");}catch(Exception e){failure(e);}
            });
        }}catch(Exception e){failure(e);}
    }
    private void drawChat(Store.Contact p){chatWith=p;layout("Conversación cifrada de extremo a extremo");
        addButton(page,"←  Contactos",false,this::drawContacts);
        LinearLayout info=card();addText(info,p.name,20,GOLD,true);addText(info,"ID "+p.id,12,MUTED,false);
        try{for(Store.Stored item:db.bundles()){
            Bundle b=item.bundle;
            if(!b.kind().equals("message")||!(b.from().equals(p.id)&&b.to().equals(db.myId()))&&!(b.from().equals(db.myId())&&b.to().equals(p.id)))continue;
            LinearLayout c=card();addText(c,b.from().equals(db.myId())?"Tú":"Recibido",13,MUTED,true);
            addText(c,item.note.isEmpty()?"[Contenido reservado]":item.note,15,INK,false);
            addText(c,stateLabel(item.status),12,GREEN,false);
            addButton(c,"📷  Mostrar QR de este mensaje",false,()->showQr(b.serialized(),"PAQUETE PARA ACARREO"));
        }}catch(Exception e){failure(e);}
        LinearLayout compose=card();EditText edit=input("Tu mensaje. Si no hay enlace, queda en la mina.");compose.addView(edit,new LinearLayout.LayoutParams(-1,dp(95)));
        Spinner ttl=new Spinner(this);String[] expiries={"Vida: 24 horas","Vida: 7 días","Vida: 14 días","Vida: 1 hora"};
        ArrayAdapter<String> a=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,expiries);a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);ttl.setAdapter(a);compose.addView(ttl);
        CheckBox urgent=new CheckBox(this);urgent.setText("⚡ Mensaje urgente · prioridad múltiple");urgent.setTextColor(GOLD);compose.addView(urgent);
        addButton(compose,"⛏  Guardar y preparar envío",true,()->{
            long[] values={24L*3600000,7L*24*3600000,14L*24*3600000,3600000};
            try{Bundle b=router.send(p,edit.getText().toString(),values[ttl.getSelectedItemPosition()],urgent.isChecked()?2:1);
                toast("Mensaje protegido en la mina");showQr(b.serialized(),"ENTREGAR MENSAJE");
            }catch(Exception e){failure(e);}
        });
    }
    private void drawCouriers(){tab="carteros";chatWith=null;layout("Radio por Bluetooth Classic y transporte humano QR. Ningún servidor.");nav();
        LinearLayout guide=card();addText(guide,"Carteros cercanos",20,GOLD,true);
        addText(guide,"Empareja ambos Android en Ajustes → Bluetooth. Luego visita el teléfono aquí. La sincronización es bidireccional.",14,INK,false);
        addButton(guide,"🔄  Actualizar dispositivos emparejados",false,this::drawCouriers);
        try{
            List<BluetoothDevice> peers=CourierService.bonded(this);
            if(peers.isEmpty())addText(guide,"No aparecen teléfonos emparejados, o Bluetooth está apagado o sin permisos.",14,MUTED,false);
            for(BluetoothDevice device:peers){
                String mac=device.getAddress(),name=device.getName()==null?"Dispositivo":device.getName();
                LinearLayout c=card();addText(c,name,18,GOLD,true);addText(c,"Bluetooth clásico (RFCOMM)",12,MUTED,false);
                addButton(c,"↔  Intercambiar buzones",true,()->{
                    CourierService courier=CourierService.instance();if(courier==null){activateCourier();toast("Reabre esta pantalla cuando el cartero esté activo");}
                    else courier.visit(mac);
                });
            }
        }catch(Exception e){failure(e);}
        LinearLayout status=card();banner=label("Cartero listo para intercambiar paquetes firmados",13,GREEN,false);status.addView(banner);
        addButton(status,"📷  Recibir paquetes QR",true,this::scanQr);
        addButton(status,"📦  Ver paquetes disponibles para acarreo",false,this::showQueue);
    }
    private void showQueue(){tab="carteros";layout("Elige un paquete cifrado para llevar en tu pantalla.");addButton(page,"←  Carteros",false,this::drawCouriers);
        try{for(Bundle b:router.forwarding(80)){
            LinearLayout c=card();addText(c,"Carga "+b.id().substring(0,8),16,GOLD,true);
            addText(c,"Destino "+b.to()+" · Prioridad "+b.priority(),11,MUTED,false);
            addButton(c,"Mostrar QR animado",true,()->showQr(b.serialized(),"CARGA CIFRADA"));
        }}catch(Exception e){failure(e);}
    }
    private void showQr(String code,String title){if(code.isEmpty())return;
        tab="qr";layout(title);addButton(page,"←  Volver al buzón",false,this::drawHome);
        if(code.startsWith("BID1:"))showQrFrames(Collections.singletonList(code));
        else{
            try{Bundle b=Bundle.parse(code);showQrFrames(QrCarrier.frames(code,b.id()));}
            catch(Exception e){failure(e);}
        }
    }
    private void showQrFrames(List<String> frames){
        LinearLayout c=card();TextView counter=label("",14,GOLD,true);c.addView(counter);
        ImageView qr=new ImageView(this);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(330));lp.topMargin=dp(14);c.addView(qr,lp);
        addText(c,"Escanea todos los fotogramas. La persona que acarrea no necesita leer el mensaje.",13,MUTED,false);
        final int[] pos={0};qrTick=new Runnable(){@Override public void run(){
            try{counter.setText("Fotograma "+(pos[0]+1)+" de "+frames.size());qr.setImageBitmap(QrCarrier.makeBitmap(frames.get(pos[0]),Math.min(dp(300),760)));
                pos[0]=(pos[0]+1)%frames.size();if(frames.size()>1)main.postDelayed(this,1900);
            }catch(Exception e){failure(e);}
        }};qrTick.run();
    }
    private void stopQr(){if(qrTick!=null){main.removeCallbacks(qrTick);qrTick=null;}}
    private void scanQr(){new IntentIntegrator(this).setPrompt("BOCAMINA · escanea identidad o carga cifrada").setBeepEnabled(false).setOrientationLocked(false).initiateScan();}
    @Override protected void onActivityResult(int req,int result,Intent data){super.onActivityResult(req,result,data);
        IntentResult scan=IntentIntegrator.parseActivityResult(req,result,data);
        if(scan!=null&&scan.getContents()!=null)ingestCode(scan.getContents(),true);
    }
    private void pasteCode(){EditText entry=input("BID1: o BQR1:");
        new AlertDialog.Builder(this).setTitle("Importar código").setView(entry)
            .setPositiveButton("Procesar",(d,w)->ingestCode(entry.getText().toString().trim(),false))
            .setNegativeButton("Cancelar",null).show();
    }
    private void ingestCode(String code,boolean camera){
        try{
            if(code.startsWith("BID1:")){
                Store.Contact p=Store.readContactCode(code);
                new AlertDialog.Builder(this).setTitle("Verificar identidad de "+p.name)
                    .setMessage("ID: "+p.id+"\nHuella SHA-256:\n"+CryptoEngine.fingerprint(p.key())+
                        "\n\nConfirma la huella directamente con la persona ANTES de confiar en su identidad.")
                    .setPositiveButton("Verificada: agregar",(dialog,which)->{
                        try{db.saveContact(p);drawContacts();toast("Identidad vinculada con clave fijada");}
                        catch(Exception e){failure(e);}
                    }).setNegativeButton("Cancelar",null).show();
            }else if(code.startsWith("BQR1:")){
                String full=collector.accept(code);
                if(full==null){toast("Fragmentos obtenidos: "+collector.progress()+". Escanea el siguiente.");
                    if(camera)main.postDelayed(this::scanQr,600);
                }else{
                    BundleRouter.Outcome result=router.receive(full);
                    drawHome();alert("Cartero de mina",result.detail);
                }
            }else throw new IllegalArgumentException("No se reconoce el formato de código");
        }catch(Exception e){failure(e);}
    }
    private void alert(String title,String text){new AlertDialog.Builder(this).setTitle(title).setMessage(text).setPositiveButton("Entendido",null).show();}
    private void toast(String text){Toast.makeText(this,text,Toast.LENGTH_LONG).show();}
    private void failure(Exception e){toast("No se completó: "+e.getMessage());}
}
