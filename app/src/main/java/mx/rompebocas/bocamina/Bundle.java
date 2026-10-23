package mx.rompebocas.bocamina;

import org.json.JSONObject;
import java.security.KeyPair;
import java.security.PublicKey;
import java.nio.charset.StandardCharsets;

/** Opaque, signed DTN envelope. The transport is deliberately independent of the content. */
public final class Bundle {
    public static final long MAX_LIFETIME = 14L * 24 * 3600 * 1000;
    public static final int MAX_BYTES = 16384;
    public final JSONObject wire;
    private Bundle(JSONObject j) { wire = j; }
    public String id() { return wire.optString("id"); }
    public String from() { return wire.optString("from"); }
    public String to() { return wire.optString("to"); }
    public String kind() { return wire.optString("kind"); }
    public long expires() { return wire.optLong("expires"); }
    public int priority() { return wire.optInt("priority"); }
    public boolean expired() { return expires() <= System.currentTimeMillis(); }
    public String serialized() { return wire.toString(); }

    private static byte[] signing(JSONObject j) throws Exception {
        return CryptoEngine.canonical("BM1", j.getString("kind"), j.getString("id"), j.getString("from"), j.getString("to"),
            j.getString("pub"), String.valueOf(j.getLong("created")), String.valueOf(j.getLong("expires")),
            String.valueOf(j.getInt("priority")), j.getString("nonce"), j.getString("ciphertext"));
    }
    private static byte[] aad(JSONObject j) throws Exception {
        return CryptoEngine.canonical("BOCAMINA-AAD-1",j.getString("kind"),j.getString("id"),j.getString("from"),j.getString("to"),
            String.valueOf(j.getLong("created")),String.valueOf(j.getLong("expires")));
    }
    private static String pairContext(String a,String b) { return a.compareTo(b)<0 ? a+"|"+b+"|bundle" : b+"|"+a+"|bundle"; }
    public static Bundle make(Store store, Store.Contact peer, String kind, String body, long ttlMillis, int priority) throws Exception {
        if (!"message".equals(kind) && !"receipt".equals(kind)) throw new SecurityException("Tipo incorrecto");
        if (body.length()>1200 || body.isEmpty()) throw new SecurityException("Texto inválido");
        if (ttlMillis < 60000 || ttlMillis>MAX_LIFETIME) throw new SecurityException("TTL inválido");
        if (priority<0 || priority>2) throw new SecurityException("Prioridad inválida");
        String from=store.myId(),to=peer.id;
        long now=System.currentTimeMillis();
        byte[] nonce=CryptoEngine.random(12);
        JSONObject j = new JSONObject().put("v",1).put("kind",kind).put("id",java.util.UUID.randomUUID().toString())
            .put("from",from).put("to",to).put("pub",CryptoEngine.b64(store.keys().getPublic().getEncoded()))
            .put("created",now).put("expires",now+ttlMillis).put("priority",priority).put("nonce",CryptoEngine.b64(nonce));
        byte[] secret=CryptoEngine.shared(store.keys().getPrivate(),peer.key(),pairContext(from,to));
        j.put("ciphertext", CryptoEngine.b64(CryptoEngine.encrypt(secret,nonce,CryptoEngine.utf8(body),aad(j))));
        j.put("sig",CryptoEngine.b64(CryptoEngine.signature(store.keys().getPrivate(),signing(j))));
        return parse(j.toString());
    }
    public static Bundle parse(String data) throws Exception {
        if (data == null || data.getBytes(StandardCharsets.UTF_8).length>MAX_BYTES) throw new SecurityException("Paquete supera tamaño límite");
        JSONObject j=new JSONObject(data);
        if (j.getInt("v")!=1 || (!j.getString("kind").equals("message")&&!j.getString("kind").equals("receipt"))) throw new SecurityException("Versión/tipo desconocido");
        if (j.getString("id").length()>80 || !j.getString("id").matches("[A-Za-z0-9-]{10,80}")) throw new SecurityException("ID inválido");
        if (!j.getString("from").matches("[0-9a-f]{24}")||!j.getString("to").matches("[0-9a-f]{24}")) throw new SecurityException("Destino inválido");
        if (j.getInt("priority")<0 || j.getInt("priority")>2) throw new SecurityException("Prioridad inválida");
        long creation=j.getLong("created"),exp=j.getLong("expires"),now=System.currentTimeMillis();
        if (creation>now+10*60*1000 || exp<=creation || exp-creation>MAX_LIFETIME) throw new SecurityException("TTL inválido");
        if (j.getString("ciphertext").length()>11000 || j.getString("nonce").length()>32||j.getString("sig").length()>200) throw new SecurityException("Tamaño criptográfico inválido");
        if (CryptoEngine.unb64(j.getString("nonce")).length!=12) throw new SecurityException("Nonce inválido");
        PublicKey pub=CryptoEngine.decodePublic(j.getString("pub"));
        if (!Store.deviceId(pub).equals(j.getString("from"))) throw new SecurityException("ID y clave no corresponden");
        if (!CryptoEngine.verify(pub,signing(j),CryptoEngine.unb64(j.getString("sig")))) throw new SecurityException("Firma incorrecta");
        return new Bundle(j);
    }
    public String decrypt(Store store,Store.Contact contact) throws Exception {
        if(!store.myId().equals(from())&&!store.myId().equals(to()))throw new SecurityException("No eres parte de esta conversación");
        String other=store.myId().equals(from())?to():from();
        if(!other.equals(contact.id))throw new SecurityException("Contacto incorrecto");
        String pk=wire.getString("pub");
        if(store.myId().equals(to())&&!pk.equals(contact.pub))throw new SecurityException("Clave de remitente no verificada");
        byte[] shared=CryptoEngine.shared(store.keys().getPrivate(),contact.key(),pairContext(from(),to()));
        return new String(CryptoEngine.decrypt(shared,CryptoEngine.unb64(wire.getString("nonce")),
            CryptoEngine.unb64(wire.getString("ciphertext")),aad(wire)),StandardCharsets.UTF_8);
    }
}
