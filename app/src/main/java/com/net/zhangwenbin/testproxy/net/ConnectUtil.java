package com.net.zhangwenbin.testproxy.net;

public class ConnectUtil {

//    private String connect = "CONNECT 47.95.226.210:80 HTTP/1.1\r\n" +
//            "Host: 47.95.226.210:80\r\n" +
//            "Proxy-Connection: Keep-Alive\r\n" +
//            "User-Agent: okhttp/3.12.1\r\n\r\n";
//
    public static String getConnect(String address,String port){
//        address = "malliu.jinritemai.com";
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

}
