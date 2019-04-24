package com.net.zhangwenbin.testproxy.net;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.VpnService;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

public class VPNService extends VpnService implements Runnable{

    //VPN转发的IP地址
    public static String  VPN_ADDRESS = "10.1.10.1";

    //从虚拟网卡拿到的文件描述符
    private ParcelFileDescriptor mInterface;

    private void setupVpn(){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Builder builder = new Builder();
            try {
                builder = builder.addAllowedApplication("com.zhangwenbin.jlulife");
            } catch (PackageManager.NameNotFoundException e) {
                e.printStackTrace();
            }
            mInterface = builder.setSession("TestProxy")
                    .setBlocking(false)
                    .addAddress(VPN_ADDRESS,32)
                    .addRoute("0.0.0.0",0)
                    .establish();
        } else {
            Log.i("VPNService","当前安卓版本不支持VPNService");
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        setupVpn();
        new Thread(this).start();
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void run() {

        FileInputStream vpnInput = new FileInputStream(mInterface.getFileDescriptor());
        FileOutputStream vpnOutput = new FileOutputStream(mInterface.getFileDescriptor());

        byte[] data = new byte[4096];

        while (true) {
            try {
                int length = vpnInput.read(data);
                if (length >0 ) {
                    Log.i("VPNService","length:"+length);
                    vpnOutput.write(data,0,length);
                    data = new byte[4096];
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
