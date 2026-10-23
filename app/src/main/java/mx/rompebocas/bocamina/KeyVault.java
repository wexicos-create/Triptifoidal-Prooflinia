package mx.rompebocas.bocamina;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Hardware-backed where available; AndroidKeyStore owns the non-exportable storage keys. */
public final class KeyVault {
 private static final String ENC_ALIAS="bocamina_aes_2026_1", MAC_ALIAS="bocamina_hmac_2026_1";
 private final SecretKey aes,mac;
 public KeyVault(Context ignored)throws Exception {
  KeyStore ks=KeyStore.getInstance("AndroidKeyStore");ks.load(null);
  if(!ks.containsAlias(ENC_ALIAS)){
   KeyGenerator g=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
   g.init(new KeyGenParameterSpec.Builder(ENC_ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
    .setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());g.generateKey();
  }
  if(!ks.containsAlias(MAC_ALIAS)){
   KeyGenerator g=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256,"AndroidKeyStore");
   g.init(new KeyGenParameterSpec.Builder(MAC_ALIAS,KeyProperties.PURPOSE_SIGN|KeyProperties.PURPOSE_VERIFY).setKeySize(256).build());g.generateKey();
  }
  aes=(SecretKey)ks.getKey(ENC_ALIAS,null);mac=(SecretKey)ks.getKey(MAC_ALIAS,null);
 }
 public byte[] seal(byte[] plain, String type)throws Exception {
  Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,aes);
  c.updateAAD(CryptoEngine.utf8(type));byte[] iv=c.getIV(),ct=c.doFinal(plain),ret=new byte[1+iv.length+ct.length];
  ret[0]=1;System.arraycopy(iv,0,ret,1,iv.length);System.arraycopy(ct,0,ret,13,ct.length);return ret;
 }
 public byte[] open(byte[] blob,String type)throws Exception {
  if(blob==null||blob.length<30||blob[0]!=1)throw new SecurityException("Registro cifrado inválido");
  byte[] iv=new byte[12];System.arraycopy(blob,1,iv,0,12);
  Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,aes,new GCMParameterSpec(128,iv));
  c.updateAAD(CryptoEngine.utf8(type));return c.doFinal(blob,13,blob.length-13);
 }
 public String index(String domain,String id)throws Exception {
  Mac m=Mac.getInstance("HmacSHA256");m.init(mac);return CryptoEngine.b64(m.doFinal(CryptoEngine.utf8(domain+"|"+id)));
 }
}
