package com.net.zhangwenbin.testproxy.testnet;

import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;

public class TestConnectUtil {

    public static String getConnect(String address,int port){
        return "CONNECT "+address+":"+port+" HTTP/1.1\r\n"
                +"Host: "+address+":"+port+"\r\n"
                +"Proxy-Connection: Keep-Alive\r\n"
                +"User-Agent: okhttp/3.12.1\r\n\r\n";
        //okhttp/3.12.1
        //com.ss.android.huimai/120 (Linux; U; Android 8.1.0; zh_CN; 16th Plus; Build/OPM1.171019.026; Cronet/58.0.2991.0)
    }

    public static String getResult(){
        return "HTTP/1.0 200 Connection established";
    }

    public static String bufferToString(ByteBuffer buffer) {
        Charset charset = null;
        CharsetDecoder decoder = null;
        CharBuffer charBuffer = null;
        try {
            charset = Charset.forName("UTF-8");
            decoder = charset.newDecoder();
            // charBuffer = decoder.decode(buffer);//用这个的话，只能输出来一次结果，第二次显示为空
            charBuffer = decoder.decode(buffer.asReadOnlyBuffer());
            return charBuffer.toString();
        } catch (Exception ex) {
            ex.printStackTrace();
            return "";
        }
    }
}
