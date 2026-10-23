package mx.rompebocas.bocamina;

import java.util.*;

/** Store-carry-forward router shared by QR and Bluetooth carriers. */
public final class BundleRouter {
    public static final class Outcome {
        public final boolean accepted, delivered;
        public final String detail;
        Outcome(boolean a,boolean d,String t){accepted=a;delivered=d;detail=t;}
    }
    private final Store db;
    public BundleRouter(Store store){db=store;}

    public synchronized Bundle send(Store.Contact to,String message,long ttl,int priority)throws Exception{
        if(message==null||message.trim().isEmpty()||message.length()>1200)throw new IllegalArgumentException("Texto de 1 a 1200 caracteres");
        Bundle b=Bundle.make(db,to,"message",message.trim(),ttl,priority);
        db.storeBundle(b,message.trim(),"mina");return b;
    }
    public synchronized Outcome receive(String serialized)throws Exception{
        Bundle b=Bundle.parse(serialized);
        if(b.expired())return new Outcome(false,false,"Paquete caducado");
        if(db.exists(b.id()))return new Outcome(false,false,"Ya estaba en el buzón");
        boolean mine=b.to().equals(db.myId());
        if(!mine){
            if(db.bundles().size()>=1000)return new Outcome(false,false,"Buzón de acarreo lleno");
            db.storeBundle(b,"","camino");return new Outcome(true,false,"Paquete cifrado tomado para acarreo");
        }
        Store.Contact sender=db.getContact(b.from());
        if(sender==null){
            // Unknown senders do not become trusted contacts just because the packet verifies.
            db.storeBundle(b,"","sin contacto");return new Outcome(true,false,"Paquete recibido, requiere vincular remitente");
        }
        String plaintext=b.decrypt(db,sender);
        if(b.kind().equals("message")){
            db.storeBundle(b,plaintext,"recibido");
            Bundle receipt=Bundle.make(db,sender,"receipt","RCPT:"+b.id(),Bundle.MAX_LIFETIME,2);
            db.storeBundle(receipt,"","mina");
            return new Outcome(true,true,"Mensaje descifrado; acuse firmado en el buzón");
        }
        if(!plaintext.startsWith("RCPT:"))throw new SecurityException("Acuse inválido");
        String related=plaintext.substring(5);
        Store.Stored original=db.getBundle(related);
        if(original==null||!original.bundle.kind().equals("message")||!original.bundle.from().equals(db.myId())||!original.bundle.to().equals(sender.id))
            throw new SecurityException("Acuse no coincide con un mensaje pendiente");
        db.storeBundle(b,"","recibido");db.setBundleState(related,"entregado");
        return new Outcome(true,true,"Acuse verificado criptográficamente: entregado");
    }
    public synchronized List<Bundle> forwarding(int limit)throws Exception{
        List<Store.Stored> items=db.bundles();
        items.sort((a,b)->{
            int p=Integer.compare(b.bundle.priority(),a.bundle.priority());
            return p!=0?p:Long.compare(a.bundle.wire.optLong("created"),b.bundle.wire.optLong("created"));
        });
        List<Bundle> out=new ArrayList<>();
        for(Store.Stored item:items){
            if(out.size()>=limit)break;
            if(item.bundle.expired()||"entregado".equals(item.status)||"caducado".equals(item.status))continue;
            out.add(item.bundle);
        }
        return out;
    }
    public synchronized int waiting()throws Exception{
        int n=0;for(Store.Stored s:db.bundles())if(!s.bundle.expired()&&("mina".equals(s.status)||"camino".equals(s.status)))n++;return n;
    }
}
