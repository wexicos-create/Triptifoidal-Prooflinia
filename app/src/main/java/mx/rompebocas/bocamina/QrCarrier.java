package mx.rompebocas.bocamina;

import android.graphics.Bitmap;
import android.graphics.Color;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Explicit opt-in, human store/carry/forward via QR (no invisible background camera access). */
public final class QrCarrier {
    private QrCarrier(){}
    public static List<String> frames(String content,String bundleId){
        byte[] raw=content.getBytes(StandardCharsets.UTF_8);
        if(raw.length>Bundle.MAX_BYTES)throw new IllegalArgumentException("Paquete grande");
        int bytesPerFrame=420,total=(raw.length+bytesPerFrame-1)/bytesPerFrame;
        if(total>50)throw new IllegalArgumentException("Demasiados fotogramas");
        List<String> out=new ArrayList<>();
        for(int i=0;i<total;i++){
            int begin=i*bytesPerFrame, end=Math.min(begin+bytesPerFrame,raw.length);
            String data=CryptoEngine.b64(Arrays.copyOfRange(raw,begin,end));
            out.add("BQR1:"+bundleId+":"+(i+1)+":"+total+":"+data);
        }
        return out;
    }
    public static final class Collector {
        private String id="";private int total;
        private final Map<Integer,byte[]> received=new HashMap<>();
        public String accept(String frame)throws Exception{
            if(frame==null||!frame.startsWith("BQR1:")||frame.length()>3000)throw new SecurityException("No es un fotograma BOCAMINA");
            String[] fields=frame.split(":",5);if(fields.length!=5)throw new SecurityException("Fotograma incompleto");
            String incomingId=fields[1];int part=Integer.parseInt(fields[2]),count=Integer.parseInt(fields[3]);
            if(!incomingId.matches("[A-Za-z0-9-]{10,80}")||count<=0||count>50||part<=0||part>count)throw new SecurityException("Secuencia inválida");
            if(!id.equals(incomingId)){id=incomingId;total=count;received.clear();}
            if(total!=count)throw new SecurityException("Secuencia alterada");
            byte[] bytes=CryptoEngine.unb64(fields[4]);if(bytes.length>420)throw new SecurityException("Fragmento demasiado grande");
            received.put(part,bytes);
            if(received.size()!=total)return null;
            java.io.ByteArrayOutputStream assembled=new java.io.ByteArrayOutputStream();
            for(int i=1;i<=total;i++){byte[] x=received.get(i);if(x==null)return null;assembled.write(x);}
            String ready=assembled.toString(StandardCharsets.UTF_8.name());
            Bundle b=Bundle.parse(ready);if(!b.id().equals(id))throw new SecurityException("El paquete no coincide con el QR");
            id="";total=0;received.clear();return ready;
        }
        public String progress(){return received.size()+" / "+total;}
    }
    public static Bitmap makeBitmap(String text,int width)throws Exception{
        BitMatrix m=new MultiFormatWriter().encode(text,BarcodeFormat.QR_CODE,width,width);
        Bitmap b=Bitmap.createBitmap(width,width,Bitmap.Config.ARGB_8888);
        for(int y=0;y<width;y++)for(int x=0;x<width;x++)b.setPixel(x,y,m.get(x,y)?Color.BLACK:Color.WHITE);
        return b;
    }
}
