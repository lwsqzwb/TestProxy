package com.net.zhangwenbin.testproxy.proxy;

import android.net.VpnService;
import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.channels.Selector;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class TCPOutput implements Runnable {

    private final String TAG = "TCPOutput";

    private ConcurrentHashMap<String,TCPProxy> mProxys;
    private ConcurrentLinkedQueue<Packet> mPacketToNet;
    private ConcurrentLinkedQueue<ByteBuffer> mBufferFromNet;
    private Selector mSelector;
    private VpnService mVpnService;
    private String mProxyAddress;
    private int mProxyPort;

    public TCPOutput(ConcurrentLinkedQueue<Packet> packetToNet, ConcurrentLinkedQueue<ByteBuffer> bufferFromNet,
                     Selector selector, VpnService vpnService, String proxyAddress, int proxyPort) {
        mPacketToNet = packetToNet;
        mBufferFromNet = bufferFromNet;
        mSelector = selector;
        mVpnService = vpnService;
        mProxys = new ConcurrentHashMap<>();
        mProxyAddress = proxyAddress;
        mProxyPort = proxyPort;
    }

    @Override
    public void run() {
        Log.i(TAG,"tcp_out___+run");
        while (!Thread.interrupted()) {
            Packet packet = mPacketToNet.poll();
            if (packet != null) {
                Log.i(TAG,packet.ip4Header.destinationAddress.getHostAddress());
                String key = packet.ip4Header.destinationAddress.getHostAddress() + ":" +
                        packet.tcpHeader.destinationPort + ":" +packet.tcpHeader.sourcePort;
                TCPProxy proxy = mProxys.get(key);
                if (proxy == null) {
                    proxy = new TCPProxy(packet.ip4Header.destinationAddress,packet.tcpHeader.destinationPort,mBufferFromNet,mSelector,mProxyAddress,mProxyPort);
                    mProxys.put(key, proxy);
                    Log.i("create", key);
                }
                Log.i(TAG,"process:"+packet.tcpHeader.isSYN());
                proxy.processInput(packet);
            } else {
                try {
                    Thread.sleep(15);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
        Log.i(TAG,"over");
    }

}
