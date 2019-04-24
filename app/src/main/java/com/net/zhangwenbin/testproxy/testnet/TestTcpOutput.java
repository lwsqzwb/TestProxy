package com.net.zhangwenbin.testproxy.testnet;

import java.nio.ByteBuffer;
import java.nio.channels.Selector;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class TestTcpOutput implements Runnable{

    private ConcurrentHashMap<String,TCPPacketHub> mTCPPacketHubs;
    private ConcurrentLinkedQueue<TestPacket> mDataToNet;
    private ConcurrentLinkedQueue<ByteBuffer> mDataToApp;
    private Selector mSelector;

    public TestTcpOutput(ConcurrentLinkedQueue<TestPacket> dataToNet, ConcurrentLinkedQueue<ByteBuffer> dataToApp, Selector selector) {
        mTCPPacketHubs = new ConcurrentHashMap<>();
        mDataToNet = dataToNet;
        mDataToApp = dataToApp;
        mSelector = selector;
    }

    @Override
    public void run() {
        while (!Thread.interrupted()) {
            TestPacket packet = mDataToNet.poll();
            if (packet != null) {
                String key = packet.ip4Header.destinationAddress.getHostAddress() + ":" +
                        packet.tcpHeader.destinationPort + ":" +packet.tcpHeader.sourcePort;
                TCPPacketHub tcpPacketHub = mTCPPacketHubs.get(key);
                if (tcpPacketHub == null) {
                    tcpPacketHub = new TCPPacketHub(packet.ip4Header.destinationAddress,packet.tcpHeader.destinationPort,mDataToApp,mSelector);
                    mTCPPacketHubs.put(key,tcpPacketHub);
                }
                tcpPacketHub.addPacket(packet);
            }
            for (TCPPacketHub tcpPacketHub : mTCPPacketHubs.values()) {
                tcpPacketHub.process();
            }
        }
    }
}
