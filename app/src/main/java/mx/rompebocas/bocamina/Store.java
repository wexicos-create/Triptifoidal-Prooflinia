package mx.rompebocas.bocamina;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

/** Local encrypted-at-rest mailbox. Only opaque HMAC indexes and encrypted blobs are persisted. */
public final class Store extends SQLiteOpenHelper {
    public static final class Contact {
        public final String id, name, pub;
        Contact(String id,String name,String pub){this.id=id;this.name=name;this.pub=pub;}
        PublicKey key() throws Exception { return CryptoEngine.decodePublic(pub); }
        JSONObject json() throws Exception {return new JSONObject().put("id",id).put("name",name).put("pub",pub);}
    }
    public static final class Stored {
        public final Bundle bundle;
        public final String note,status;
        Stored(Bundle b,String note,String status){this.bundle=b;this.note=note;this.status=status;}
        JSONObject json() throws Exception {return new JSONObject().put("wire",bundle.serialized()).put("note",note).put("status",status);}
    }
    private final KeyVault vault;
    private final KeyPair identity;
    private final String myId;
    private String myName;
    public Store(Context ctx) throws Exception {
        super(ctx,"bocamina-local-encrypted.db",null,1);
        vault=new KeyVault(ctx);
        JSONObject state=read("state","identity");
        if(state==null){
            KeyPair k=CryptoEngine.identity();
            state=new JSONObject().put("pub",CryptoEngine.b64(k.getPublic().getEncoded()))
                    .put("priv",CryptoEngine.b64(k.getPrivate().getEncoded())).put("name","Miner@ "+System.currentTimeMillis()%10000);
            put("state","identity",state);
        }
        PublicKey pub=CryptoEngine.decodePublic(state.getString("pub"));
        PrivateKey priv=CryptoEngine.decodePrivate(CryptoEngine.unb64(state.getString("priv")));
        identity=new KeyPair(pub,priv);myId=deviceId(pub);myName=state.getString("name");
    }
    public static String deviceId(PublicKey pub)throws Exception {
        byte[] sha=MessageDigest.getInstance("SHA-256").digest(pub.getEncoded());StringBuilder b=new StringBuilder();
        for(int i=0;i<12;i++)b.append(String.format(Locale.ROOT,"%02x",sha[i]&0xff));return b.toString();
    }
    @Override public void onCreate(SQLiteDatabase db){
        db.execSQL("CREATE TABLE state (idx TEXT PRIMARY KEY, data BLOB NOT NULL)");
        db.execSQL("CREATE TABLE contacts (idx TEXT PRIMARY KEY, data BLOB NOT NULL)");
        db.execSQL("CREATE TABLE bundles (idx TEXT PRIMARY KEY, data BLOB NOT NULL)");
    }
    @Override public void onConfigure(SQLiteDatabase db){
        super.onConfigure(db);
        // secure_delete returns a row. Android SQLiteDatabase.execSQL() rejects row-returning statements.
        try (Cursor cursor = db.rawQuery("PRAGMA secure_delete=ON", null)) {
            if (!cursor.moveToFirst() || cursor.getInt(0) != 1) {
                throw new IllegalStateException("No se pudo activar el borrado seguro SQLite");
            }
        }
    }
    @Override public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion){throw new IllegalStateException("Requiere migración cifrada explícita");}
    private String table(String name){if(!"state".equals(name)&&!"contacts".equals(name)&&!"bundles".equals(name))throw new IllegalArgumentException();return name;}
    private String index(String t,String id) throws Exception{return vault.index("bocamina/"+t,id);}
    private byte[] seal(JSONObject v,String t) throws Exception{return vault.seal(CryptoEngine.utf8(v.toString()),"bocamina/"+t);}
    private JSONObject open(byte[] value,String t)throws Exception {return new JSONObject(new String(vault.open(value,"bocamina/"+t),StandardCharsets.UTF_8));}
    private synchronized void put(String t,String id,JSONObject v)throws Exception {
        ContentValues cv=new ContentValues();cv.put("idx",index(t,id));cv.put("data",seal(v,t));
        getWritableDatabase().insertWithOnConflict(table(t),null,cv,SQLiteDatabase.CONFLICT_REPLACE);
    }
    private synchronized JSONObject read(String t,String id)throws Exception{
        try(Cursor c=getReadableDatabase().query(table(t),new String[]{"data"},"idx=?",new String[]{index(t,id)},null,null,null)){
            return c.moveToFirst()?open(c.getBlob(0),t):null;
        }
    }
    private synchronized List<JSONObject> all(String t)throws Exception{
        List<JSONObject> ret=new ArrayList<>();try(Cursor c=getReadableDatabase().query(table(t),new String[]{"data"},null,null,null,null,null,"2000")){
            while(c.moveToNext())ret.add(open(c.getBlob(0),t));
        }return ret;
    }
    public String myId(){return myId;}
    public KeyPair keys(){return identity;}
    public String myName(){return myName;}
    public synchronized void setName(String name)throws Exception{
        if(name.trim().isEmpty()||name.length()>60)throw new IllegalArgumentException("Nombre inválido");
        JSONObject state=read("state","identity");state.put("name",name.trim());put("state","identity",state);myName=name.trim();
    }
    public String identityCode()throws Exception{
        JSONObject j=new JSONObject().put("v",1).put("id",myId).put("name",myName)
                .put("pub",CryptoEngine.b64(identity.getPublic().getEncoded()));
        return "BID1:"+CryptoEngine.b64(CryptoEngine.utf8(j.toString()));
    }
    public static Contact readContactCode(String code)throws Exception{
        if(code==null||!code.startsWith("BID1:")||code.length()>4000)throw new IllegalArgumentException("Código no reconocido");
        JSONObject j=new JSONObject(new String(CryptoEngine.unb64(code.substring(5)),StandardCharsets.UTF_8));
        if(j.getInt("v")!=1)throw new SecurityException("Versión inválida");
        String id=j.getString("id"),name=j.getString("name"),pub=j.getString("pub");
        if(!deviceId(CryptoEngine.decodePublic(pub)).equals(id))throw new SecurityException("El ID no corresponde a la clave");
        if(name.trim().isEmpty()||name.length()>60)throw new IllegalArgumentException("Nombre inválido");
        return new Contact(id,name,pub);
    }
    public synchronized void saveContact(Contact c)throws Exception{
        if(c.id.equals(myId))throw new SecurityException("Esa identidad ya es tuya");
        if(!deviceId(c.key()).equals(c.id))throw new SecurityException("Clave incompatible");
        Contact previous=getContact(c.id);
        if(previous!=null&&!previous.pub.equals(c.pub))throw new SecurityException("Cambio sospechoso de identidad");
        put("contacts",c.id,c.json());
    }
    public synchronized Contact getContact(String id)throws Exception{
        JSONObject j=read("contacts",id);return j==null?null:new Contact(j.getString("id"),j.getString("name"),j.getString("pub"));
    }
    public synchronized List<Contact> contacts()throws Exception{
        List<Contact> r=new ArrayList<>();for(JSONObject j:all("contacts"))r.add(new Contact(j.getString("id"),j.getString("name"),j.getString("pub")));
        r.sort(Comparator.comparing(p->p.name.toLowerCase(Locale.ROOT)));return r;
    }
    public synchronized boolean exists(String bundleId)throws Exception{return read("bundles",bundleId)!=null;}
    public synchronized boolean storeBundle(Bundle b,String note,String state)throws Exception{
        if(exists(b.id()))return false;
        put("bundles",b.id(),new Stored(b,note,state).json());return true;
    }
    public synchronized void setBundleState(String bundleId,String state)throws Exception{
        JSONObject old=read("bundles",bundleId);if(old==null)return;old.put("status",state);put("bundles",bundleId,old);
    }
    public synchronized Stored getBundle(String bundleId)throws Exception{
        JSONObject j=read("bundles",bundleId);return j==null?null:unpack(j);
    }
    private Stored unpack(JSONObject j)throws Exception{return new Stored(Bundle.parse(j.getString("wire")),j.optString("note"),j.optString("status","mina"));}
    public synchronized List<Stored> bundles()throws Exception{
        List<Stored> result=new ArrayList<>();for(JSONObject j:all("bundles")){
            try{result.add(unpack(j));}catch(Exception ignored){} // Corrupt or future-format records aren't forwarded.
        }
        result.sort((a,b)->Long.compare(b.bundle.wire.optLong("created"),a.bundle.wire.optLong("created")));
        return result;
    }
    public synchronized void prune()throws Exception{
        for(Stored s:bundles())if(s.bundle.expired()){
            if(!"entregado".equals(s.status))setBundleState(s.bundle.id(),"caducado");
        }
    }
}
