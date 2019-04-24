package com.net.zhangwenbin.testproxy.proxy;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.VpnService;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.net.zhangwenbin.testproxy.MainActivity;
import com.net.zhangwenbin.testproxy.R;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.Selector;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ProxyService extends VpnService {

    private final String TAG = "ProxyService";

    private static final String VPN_ADDRESS = "10.0.0.2"; // Only IPv4 support for now
    private static final String VPN_ROUTE = "0.0.0.0"; // Intercept everything

    private ParcelFileDescriptor vpnInterface = null;

    private ConcurrentLinkedQueue<Packet> deviceToNetworkUDPQueue;
    private ConcurrentLinkedQueue<Packet> deviceToNetworkTCPQueue;
    private ConcurrentLinkedQueue<ByteBuffer> networkToDeviceQueue;

    private Selector udpSelector;
    private Selector tcpSelector;

    private ExecutorService executorService;

    private String mProxyAddress;
    private int mProxyPort;

    private void setupVpn(String packageName) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Builder builder = new Builder();
            try {
                //com.zhangwenbin.jlulife
                //com.ss.android.huimai
                builder = builder.addAllowedApplication(packageName);
            } catch (PackageManager.NameNotFoundException e) {
                e.printStackTrace();
            }
            vpnInterface = builder.setSession("ProxyService")
                    .setBlocking(false)
                    .addAddress(VPN_ADDRESS,32)
                    .addRoute(VPN_ROUTE,0)
                    .establish();
        }
    }

    public void init() {
        deviceToNetworkTCPQueue = new ConcurrentLinkedQueue<>();
        deviceToNetworkUDPQueue = new ConcurrentLinkedQueue<>();
        networkToDeviceQueue = new ConcurrentLinkedQueue<>();

        try {
            udpSelector = Selector.open();
            tcpSelector = Selector.open();
        } catch (IOException e) {
            e.printStackTrace();
        }

        executorService = Executors.newFixedThreadPool(6);
        executorService.submit(new TCPInput(tcpSelector));
        executorService.submit(new TCPOutput(deviceToNetworkTCPQueue,networkToDeviceQueue,tcpSelector,this, mProxyAddress, mProxyPort));
        executorService.submit(new UDPInput(networkToDeviceQueue,udpSelector));
        executorService.submit(new UDPOutput(deviceToNetworkUDPQueue,udpSelector,this));
        executorService.submit(new VPNInput(vpnInterface.getFileDescriptor(),deviceToNetworkTCPQueue,deviceToNetworkUDPQueue));
        executorService.submit(new VPNOutput(vpnInterface.getFileDescriptor(),networkToDeviceQueue));
    }

    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String packageName = intent.getStringExtra(MainActivity.PACKAGE_NAME);
        mProxyAddress = intent.getStringExtra(MainActivity.PROXY_ADDRESS);
        mProxyPort = intent.getIntExtra(MainActivity.PROXY_PORT, -1);
        if (packageName != null && mProxyAddress != null && mProxyPort != -1) {
            setupVpn(packageName);
            init();
        } else {
            throw new IllegalArgumentException("packageName is null");
        }

        startForeground(110, buildNotification());

        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        stopForeground(true);
        super.onDestroy();
    }

    private Notification buildNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            String NOTIFICATION_CHANNEL_ID = "my_service";
            String channelName = "My Background Service";
            NotificationChannel chan = new NotificationChannel(NOTIFICATION_CHANNEL_ID, channelName, NotificationManager.IMPORTANCE_NONE);
            chan.setLightColor(Color.BLUE);
            chan.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            assert manager != null;
            manager.createNotificationChannel(chan);
            NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID);
            notificationBuilder.setOngoing(true)
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setContentTitle("App is running in background")
                    .setPriority(NotificationManager.IMPORTANCE_MIN)
                    .setCategory(Notification.CATEGORY_SERVICE)
                    .build();
            return notificationBuilder.build();
        }

        Notification.Builder builder = new Notification.Builder(this.getApplicationContext());
        Intent intent = new Intent(this,MainActivity.class);
        builder.setContentIntent(PendingIntent.getActivity(this, 0, intent, 0))
                .setLargeIcon(BitmapFactory.decodeResource(this.getResources(), R.mipmap.ic_launcher))
                .setContentTitle("proxy")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentText("VpnService")
                .setWhen(System.currentTimeMillis());

        return builder.build();
    }

    private static class VPNInput implements Runnable {

        private FileDescriptor mVpnFileDescriptor;
        private ConcurrentLinkedQueue<Packet> mDeviceToNetworkUDPQueue;
        private ConcurrentLinkedQueue<Packet> mDeviceToNetworkTCPQueue;

        public VPNInput(FileDescriptor vpnFileDescriptor, ConcurrentLinkedQueue<Packet> deviceToNetworkTCPQueue,ConcurrentLinkedQueue<Packet> deviceToNetworkUDPQueue){
            mVpnFileDescriptor = vpnFileDescriptor;
            mDeviceToNetworkTCPQueue = deviceToNetworkTCPQueue;
            mDeviceToNetworkUDPQueue = deviceToNetworkUDPQueue;
        }

        @Override
        public void run() {
            Log.i("TAG","vpninputrun");
            FileChannel vpnInput = new FileInputStream(mVpnFileDescriptor).getChannel();
            boolean hasDataSend = true;
            ByteBuffer bufferToNet = null;
            try {
                while (!Thread.interrupted()) {
                    if (hasDataSend) {
                        //发送出去了，申请新的空间
                        bufferToNet = ProxyUtil.createByteBuffer();
                    } else {
                        bufferToNet.clear();
                    }
                    int bufferSize = vpnInput.read(bufferToNet);
                    if (bufferSize > 0) {
                        hasDataSend = true;
                        bufferToNet.flip();
                        Packet packet = new Packet(bufferToNet);
                        if (packet.isTCP()) {
                            mDeviceToNetworkTCPQueue.offer(packet);
                            Log.i("TAG","isSYN:" + packet.tcpHeader.isSYN());
                            Log.i("TCP_UDP" , "    TCP");
                        } else if (packet.isUDP()) {
                            Log.i("TCP_UDP" , "    UDP");
                            mDeviceToNetworkUDPQueue.offer(packet);
                        } else {
                            hasDataSend = false;
                        }
                    } else {
                        hasDataSend = false;
                    }
                    if (!hasDataSend) {
                        Thread.sleep(15);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                ProxyUtil.closeResource(vpnInput);
            }
        }
    }

    private static class VPNOutput implements Runnable {

        private FileDescriptor mVpnFileDescriptor;
        private ConcurrentLinkedQueue<ByteBuffer> mNetworkToDeviceQueue;

        public VPNOutput(FileDescriptor vpnFileDescriptor, ConcurrentLinkedQueue<ByteBuffer> networkToDeviceQueue) {
            mVpnFileDescriptor = vpnFileDescriptor;
            mNetworkToDeviceQueue = networkToDeviceQueue;
        }

        @Override
        public void run() {
            Log.i("TAG","vpnoutrun");
            FileChannel vpnOutput = new FileOutputStream(mVpnFileDescriptor).getChannel();
            try {
                while (!Thread.interrupted()) {
                    ByteBuffer byteBuffer = mNetworkToDeviceQueue.poll();
                    if (byteBuffer != null) {
//                        byteBuffer.flip();
//                        Packet packet = new Packet(byteBuffer);
//                        if (packet.isTCP()) {
//                            Log.i("reveive_data:", "tcp syn:" + packet.tcpHeader.isSYN() + "   data:  " + ProxyUtil.bufferToString(packet.backingBuffer));
//                        } else {
//                            Log.i("reveive_data:", "udp");
//                        }
                        byteBuffer.flip();
                        Log.i("size:", byteBuffer.limit() - byteBuffer.position() + "");
                        while (byteBuffer.hasRemaining()) {
                            vpnOutput.write(byteBuffer);
                        }
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                ProxyUtil.closeResource(vpnOutput);
            }
        }
    }

}
