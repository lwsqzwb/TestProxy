package com.net.zhangwenbin.testproxy.testnet;

import com.net.zhangwenbin.testproxy.net.ByteBufferPool;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;

public class TCPPacketHub {

    private ConnectToNet mConnectToNet;
    private ConnectToApp mConnectToApp;
    private ConcurrentLinkedQueue<TestPacket> mDataToNet;
    public long mySequenceNum, theirSequenceNum;
    public long myAcknowledgementNum, theirAcknowledgementNum;

    public TCPPacketHub(InetAddress destinationAddress, int port, ConcurrentLinkedQueue<ByteBuffer> mNetworkToDeviceQueue, Selector selector) {
        mConnectToNet = new ConnectToNet(destinationAddress,port,selector);
        mConnectToApp = new ConnectToApp(mNetworkToDeviceQueue);
    }

    public void process(){
        if (!mConnectToNet.isReady()) {
            return;
        }
        TestPacket packet = mDataToNet.poll();
        if (packet == null) {
            return;
        }
        boolean flag = mConnectToApp.processPacket(packet);
        if (!flag) {
            mConnectToNet.processPacket(packet);
        }
    }

    public void addPacket(TestPacket packet){
        if (mDataToNet == null) {
            mDataToNet = new ConcurrentLinkedQueue<>();
            mySequenceNum = new Random().nextInt(Short.MAX_VALUE);
            theirSequenceNum = packet.tcpHeader.sequenceNumber;
            myAcknowledgementNum = theirSequenceNum + 1;
            theirAcknowledgementNum = packet.tcpHeader.acknowledgementNumber;
        }
        mDataToNet.offer(packet);
    }

    private static class ConnectToNet{
        private static final int ConnectToProxy = 1;
        private static final int ConnectToServer = 2;

        private SocketChannel mSocketChannel;
        private int mState;
        private InetAddress mDestinationAddress;
        private int mPort;
        private ByteBuffer mBuffer;
        private Selector mSelector;

        public ConnectToNet(InetAddress destinationAddress,int port,Selector selector) {
            mDestinationAddress = destinationAddress;
            mPort = port;
            try {
                mSocketChannel = SocketChannel.open(new InetSocketAddress("10.92.234.48",8888));
            } catch (IOException e) {
                e.printStackTrace();
            }
            mState = 0;
            mBuffer = ByteBufferPool.acquire();
            mSelector = selector;
        }
        public boolean isReady() {
            if (mState == ConnectToServer) {
                return true;
            }
            if (mState == ConnectToProxy) {
                processConnectToProxy();
            } else {
                processInit();
            }
            return false;
        }

        private void processInit() {
            try {
                if (mSocketChannel.isConnected()) {
                    mState = ConnectToProxy;
                    String connect = TestConnectUtil.getConnect(mDestinationAddress.getHostAddress(),mPort);
                    ByteBuffer byteBuffer = ByteBuffer.wrap(connect.getBytes());
                    while (byteBuffer.hasRemaining()) {
                        mSocketChannel.write(byteBuffer);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        private void processConnectToProxy() {
            try {
                mBuffer.clear();
                int size = mSocketChannel.read(mBuffer);
                if (size > 0){
                    String dataFromNet = TestConnectUtil.bufferToString(mBuffer);
                    if (dataFromNet.contains(TestConnectUtil.getResult())) {
                        mState = ConnectToServer;
                        mSocketChannel.register(mSelector,SelectionKey.OP_READ);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        private void processPacket(TestPacket packet){
            ByteBuffer data = packet.data;
            try {
                while (data.hasRemaining()) {
                    mSocketChannel.write(data);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }

    private static class ConnectToApp{
        private static final int WAITFORCONNECT = 0;
        private static final int SYN_RECEIVED = 1;
        private static final int ESTABLISHED = 2;
        private static final int FIN_RECEIVED = 3;
        private static final int CLOSED = 4;

        private int mState;
        private ConcurrentLinkedQueue<ByteBuffer> mNetworkToDeviceQueue;

        public ConnectToApp(ConcurrentLinkedQueue<ByteBuffer> networkToDeviceQueue){
            mState = WAITFORCONNECT;
            mNetworkToDeviceQueue = networkToDeviceQueue;
        }

        public boolean processPacket(TestPacket packet){
            switch (mState) {
                case WAITFORCONNECT: {
                    if (packet.tcpHeader.isSYN()) {
                        sendSYNACK();
                        mState = SYN_RECEIVED;
                        return true;
                    } else if (packet.tcpHeader.isACK()) {
                        mState = ESTABLISHED;
                        return false;
                    }
                    break;
                }
                case SYN_RECEIVED: {
                    if (packet.tcpHeader.isSYN()) {
                        return true;
                    } else if (packet.tcpHeader.isACK()) {
                        mState = ESTABLISHED;
                        return true;
                    }
                    break;
                }
                case ESTABLISHED: {
                    if (packet.tcpHeader.isFIN()) {
                        mState = FIN_RECEIVED;
                        sendFINACK();
                        return true;
                    }
                    break;
                }
                case FIN_RECEIVED: {
                    if (packet.tcpHeader.isACK()) {
                        mState = CLOSED;
                        return true;
                    }
                    break;
                }
            }
            return false;
        }

        private void sendFINACK() {

        }

        private void sendSYNACK() {

        }
    }

}
