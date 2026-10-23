package mx.rompebocas.bocamina;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.*;
import java.util.*;
import javax.crypto.*;
import javax.crypto.spec.*;

/** Pure Java cryptographic protocol, independent of Android UI/storage. */
public final class CryptoEngine {
 private static final SecureRandom RNG=new SecureRandom();
 private CryptoEngine(){}
 public static KeyPair identity() throws Exception {
  KeyPairGenerator g=KeyPairGenerator.getInstance("EC");g.initialize(new ECGenParameterSpec("secp256r1"),RNG);return g.generateKeyPair();
 }
 public static PublicKey decodePublic(String b64)throws Exception {
  byte[] bytes=unb64(b64);if(bytes.length>256 || bytes.length<80)throw new SecurityException("Clave pública inválida");
  PublicKey k=KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(bytes));
  if(!(k instanceof java.security.interfaces.ECPublicKey))throw new SecurityException("Algoritmo inválido");
  java.security.spec.ECParameterSpec p=((java.security.interfaces.ECPublicKey)k).getParams();
  if(p.getOrder().bitLength()!=256||p.getCurve().getField().getFieldSize()!=256)throw new SecurityException("Se requiere curva P-256");
  return k;
 }
 public static PrivateKey decodePrivate(byte[] bytes)throws Exception {return KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(bytes));}
 public static String b64(byte[] x){return Base64.getUrlEncoder().withoutPadding().encodeToString(x);}
 public static byte[] unb64(String x){return Base64.getUrlDecoder().decode(x);}
 public static byte[] utf8(String x){return x.getBytes(StandardCharsets.UTF_8);}
 public static String fingerprint(PublicKey k)throws Exception {
  byte[] h=MessageDigest.getInstance("SHA-256").digest(k.getEncoded());StringBuilder b=new StringBuilder();
  for(int i=0;i<12;i++)b.append(String.format(Locale.US,"%02X",h[i]&0xff));return b.toString();
 }
 public static byte[] shared(PrivateKey my,PublicKey other,String context)throws Exception {
  KeyAgreement ka=KeyAgreement.getInstance("ECDH");ka.init(my);ka.doPhase(other,true);
  byte[] secret=ka.generateSecret();return hkdf(secret,utf8("BOCAMINA/2026/v1|"+context),32);
 }
 private static byte[] hkdf(byte[] ikm,byte[] info,int n)throws Exception {
  Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(new byte[32],"HmacSHA256"));byte[] prk=mac.doFinal(ikm);
  mac.init(new SecretKeySpec(prk,"HmacSHA256"));mac.update(info);mac.update((byte)1);return Arrays.copyOf(mac.doFinal(),n);
 }
 public static byte[] random(int n){byte[] b=new byte[n];RNG.nextBytes(b);return b;}
 public static byte[] encrypt(byte[] key,byte[] nonce,byte[] plain,byte[] aad)throws Exception {
  if(nonce.length!=12)throw new SecurityException("Nonce inválido");
  Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(key,"AES"),new GCMParameterSpec(128,nonce));c.updateAAD(aad);return c.doFinal(plain);
 }
 public static byte[] decrypt(byte[] key,byte[] nonce,byte[] encrypted,byte[] aad)throws Exception {
  if(nonce.length!=12)throw new SecurityException("Nonce inválido");
  Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,new SecretKeySpec(key,"AES"),new GCMParameterSpec(128,nonce));c.updateAAD(aad);return c.doFinal(encrypted);
 }
 public static byte[] signature(PrivateKey privateKey,byte[] data)throws Exception {
  Signature s=Signature.getInstance("SHA256withECDSA");s.initSign(privateKey,RNG);s.update(data);return s.sign();
 }
 public static boolean verify(PublicKey publicKey,byte[] data,byte[] sig)throws Exception {
  Signature s=Signature.getInstance("SHA256withECDSA");s.initVerify(publicKey);s.update(data);return s.verify(sig);
 }
 /** Length-delimited canonical encoding; impossible to alter JSON ordering to change meaning. */
 public static byte[] canonical(String... fields)throws Exception {
  ByteArrayOutputStream out=new ByteArrayOutputStream();DataOutputStream d=new DataOutputStream(out);
  for(String s:fields){byte[] b=utf8(s);if(b.length>1024*1024)throw new SecurityException("Campo demasiado grande");d.writeInt(b.length);d.write(b);}d.flush();return out.toByteArray();
 }
}
