package com.net.zhangwenbin.testproxy.testnet;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.VpnService;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.Selector;
import java.util.concurrent.ConcurrentLinkedQueue;

public class TestService extends VpnService {

    private static final String TAG = "TestService";
    private static final String VPN_ADDRESS = "10.1.10.1";
    private static final String VPN_ROUTE = "0.0.0.0";

    //虚拟网卡的文件描述符
    private ParcelFileDescriptor mInterface;

    private ConcurrentLinkedQueue<TestPacket> mDeviceToNetworkUDPQueue;
    private ConcurrentLinkedQueue<TestPacket> mDeviceToNetworkTCPQueue;
    private ConcurrentLinkedQueue<ByteBuffer> mNetworkToDeviceQueue;

    private Selector mUdpSelector;
    private Selector mTcpSelector;

    private void setupVpn(){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Builder builder = new Builder();
            try {
                builder = builder.addAllowedApplication("com.zhangwenbin.jlulife");
            } catch (PackageManager.NameNotFoundException e) {
                e.printStackTrace();
            }
            mInterface = builder.setSession("TestSerice")
                    .setBlocking(false)
                    .addAddress(VPN_ADDRESS,32)
                    .addRoute(VPN_ROUTE,0)
                    .establish();
        } else {
            Log.i(TAG,"暂不支持当前安卓版本");
        }
    }

    private void init(){
        try {
            mUdpSelector = Selector.open();
            mTcpSelector = Selector.open();

            mDeviceToNetworkTCPQueue = new ConcurrentLinkedQueue<>();
            mDeviceToNetworkUDPQueue = new ConcurrentLinkedQueue<>();
            mNetworkToDeviceQueue = new ConcurrentLinkedQueue<>();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        setupVpn();
        init();
        return super.onStartCommand(intent, flags, startId);
    }

    private static class VPNInput implements Runnable{

        private FileDescriptor mVpnFileDescriptor;
        private ConcurrentLinkedQueue<TestPacket> mDeviceToNetworkUDPQueue;
        private ConcurrentLinkedQueue<TestPacket> mDeviceToNetworkTCPQueue;

        public VPNInput(FileDescriptor vpnFileDescriptor,ConcurrentLinkedQueue<TestPacket> deviceToNetworkUDPQueue,
                           ConcurrentLinkedQueue<TestPacket> deviceToNetworkTCPQueue){
            mVpnFileDescriptor = vpnFileDescriptor;
            mDeviceToNetworkTCPQueue = deviceToNetworkTCPQueue;
            mDeviceToNetworkUDPQueue = deviceToNetworkUDPQueue;
        }

        @Override
        public void run() {
            FileChannel vpnInput = new FileInputStream(mVpnFileDescriptor).getChannel();

            ByteBuffer bufferToNet = TestByteBufferPool.acquire();
            boolean hasDataSend = true;
            try {
                while (!Thread.interrupted()) {
                    bufferToNet.clear();
                    int bufferSize = vpnInput.read(bufferToNet);
                    if (bufferSize > 0) {
                        hasDataSend = true;
                        bufferToNet.flip();
                        TestPacket packet = new TestPacket(bufferToNet);
                        if (packet.isTCP()) {
                            mDeviceToNetworkTCPQueue.offer(packet);
                        } else if (packet.isUDP()) {
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
            } catch (IOException e) {
                e.printStackTrace();
            } catch (InterruptedException e){
                Log.i(TAG,e.getMessage());
                e.printStackTrace();
            } finally {
                TestVpnUtils.closeResource(vpnInput);
            }
        }
    }

    private static class VPNOutput implements Runnable{

        private FileDescriptor mVpnFileDescriptor;
        private ConcurrentLinkedQueue<ByteBuffer> mNetworkToDeviceQueue;

        public VPNOutput(FileDescriptor vpnFileDescriptor,ConcurrentLinkedQueue<ByteBuffer> networkToDeviceQueue) {
            mVpnFileDescriptor = vpnFileDescriptor;
            mNetworkToDeviceQueue = networkToDeviceQueue;
        }

        @Override
        public void run() {
            FileChannel vpnOutput = new FileOutputStream(mVpnFileDescriptor).getChannel();
            try {
                while (!Thread.interrupted()) {
                    ByteBuffer dataFromNet = mNetworkToDeviceQueue.poll();
                    if (dataFromNet != null) {
                        while (dataFromNet.hasRemaining()) {
                            vpnOutput.write(dataFromNet);
                        }
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                TestVpnUtils.closeResource(vpnOutput);
            }
        }
    }

}
