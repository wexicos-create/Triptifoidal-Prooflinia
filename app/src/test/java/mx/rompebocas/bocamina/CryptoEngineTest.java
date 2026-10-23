package mx.rompebocas.bocamina;

import org.junit.Test;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import static org.junit.Assert.*;

public class CryptoEngineTest {
    @Test public void keyAgreementIsSymmetricAndBoundToContext() throws Exception {
        KeyPair a=CryptoEngine.identity(),b=CryptoEngine.identity();
        byte[] k1=CryptoEngine.shared(a.getPrivate(),b.getPublic(),"bocamina-A|B|bundle");
        byte[] k2=CryptoEngine.shared(b.getPrivate(),a.getPublic(),"bocamina-A|B|bundle");
        assertArrayEquals(k1,k2);
        assertFalse(java.util.Arrays.equals(k1,CryptoEngine.shared(a.getPrivate(),b.getPublic(),"other context")));
    }
    @Test public void corruptedPacketsNeverDecrypt() throws Exception {
        byte[] key=CryptoEngine.random(32),nonce=CryptoEngine.random(12),aad="sender|recipient".getBytes(StandardCharsets.UTF_8);
        byte[] cipher=CryptoEngine.encrypt(key,nonce,"hola".getBytes(StandardCharsets.UTF_8),aad);
        assertEquals("hola",new String(CryptoEngine.decrypt(key,nonce,cipher,aad),StandardCharsets.UTF_8));
        cipher[0]^=1;
        try{CryptoEngine.decrypt(key,nonce,cipher,aad);fail("Tampering must be rejected");}
        catch(javax.crypto.AEADBadTagException expected){}
    }
    @Test public void senderSignaturesVerifyOnlyForOriginalData()throws Exception {
        KeyPair signer=CryptoEngine.identity(),other=CryptoEngine.identity();
        byte[] text=CryptoEngine.canonical("BM1","message","a","b");
        byte[] signature=CryptoEngine.signature(signer.getPrivate(),text);
        assertTrue(CryptoEngine.verify(signer.getPublic(),text,signature));
        assertFalse(CryptoEngine.verify(other.getPublic(),text,signature));
        assertFalse(CryptoEngine.verify(signer.getPublic(),CryptoEngine.canonical("BM1","message","a","C"),signature));
    }
}
