package com.net.zhangwenbin.testproxy.proxy;

import android.util.Log;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;

public class TCPProxy {
    private final String TAG = "TCPProxy";

    private static final int WAIT_FOR_CONNECT = 0;
    private static final int SYN_RECEIVED = 1;
    private static final int SYN_ACK_SEND = 2;
    private static final int ESTABLISHED = 3;
    private static final int FIN_RECEIVED = 4;
    private static final int CLOSED = 5;

    private int mState;
    private ConcurrentLinkedQueue<ByteBuffer> mBufferToApp;
    private InetAddress mDestinationAddress;
    private int mPort;
    private ConnectToNet mConnectToNet;
    private long mySequenceNum, theirSequenceNum;
    private long myAcknowledgementNum, theirAcknowledgementNum;
    private Packet mPacket;
    private String mProxyAddress;
    private int mProxyPort;

    public TCPProxy(InetAddress destinationAddress, int port, ConcurrentLinkedQueue<ByteBuffer> bufferToApp, Selector selector, String proxyAddress, int proxyPort) {
        mBufferToApp = bufferToApp;
        mDestinationAddress = destinationAddress;
        mPort = port;
        mState = WAIT_FOR_CONNECT;
        mConnectToNet = new ConnectToNet(selector);
        mProxyAddress = proxyAddress;
        mProxyPort = proxyPort;
    }

    public synchronized void processInput(Packet packet){
        Log.i("in_out___out", "seq:"+packet.tcpHeader.sequenceNumber + "         ack:"+packet.tcpHeader.acknowledgementNumber);
        Log.i(TAG,packet.tcpHeader.isSYN()?"SYN":"NOT_SYN");
        switch (mState) {
            case WAIT_FOR_CONNECT: initConnect(packet);break;
            case SYN_RECEIVED: processSYN(packet);break;
            case SYN_ACK_SEND: processSYNACK(packet);break;
            case ESTABLISHED: processESTABLISHED(packet);break;
            case FIN_RECEIVED: processFIN(packet);break;
            default: processDefault(packet);break;
        }
        Log.i(TAG,"process_over");
    }

    public synchronized void processOutput(){
        if (mConnectToNet.processOutput()) {
            sendSYNACK();
            mState = SYN_ACK_SEND;
            return;
        }
        ByteBuffer byteBuffer = ProxyUtil.createByteBuffer();
        try {
            byteBuffer.clear();
            //遇到的坑 太沙雕了
            byteBuffer.position(Packet.IP4_HEADER_SIZE + Packet.TCP_HEADER_SIZE);
            int dataSize = mConnectToNet.readFromNet(byteBuffer);
            if (dataSize == -1) {

            } else {
                sendData(byteBuffer, dataSize);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public synchronized void processConnect() {
        mConnectToNet.processConnectToProxy();
        mConnectToNet.sendConnectToServer(mDestinationAddress.getHostAddress(), mPort);
    }

    private void initConnect(Packet packet) {
        if (packet.tcpHeader.isSYN()) {
            Log.i(TAG,"init");
            initConnectToNet(packet);
            mState = SYN_RECEIVED;
            Log.i(TAG,"initOver");
        } else {
            sendRST();
        }
    }

    private void initConnectToNet(Packet packet) {
        mySequenceNum = new Random().nextInt(Short.MAX_VALUE);
        theirSequenceNum = packet.tcpHeader.sequenceNumber;
        myAcknowledgementNum = theirSequenceNum + 1;
        theirAcknowledgementNum = packet.tcpHeader.acknowledgementNumber;
        mPacket = packet;
        mPacket.swapSourceAndDestination();
        if (mConnectToNet.initConnect(this,mProxyAddress,mProxyPort)) {
            mConnectToNet.sendConnectToServer(mDestinationAddress.getHostAddress(), mPort);
        }
    }

    private void processSYN(Packet packet) {

    }

    private void processSYNACK(Packet packet) {
        if (packet.tcpHeader.sequenceNumber != myAcknowledgementNum) {
            return;
        }
        if (packet.tcpHeader.isACK()) {
            mState = ESTABLISHED;
        }
    }

    private void processESTABLISHED(Packet packet) {
        if (packet.tcpHeader.sequenceNumber != myAcknowledgementNum) {
            return;
        }
        if (packet.tcpHeader.isSYN()) {

        } else if (packet.tcpHeader.isFIN()) {
            Log.i("data_state" , "fin" + "  seq:" + packet.tcpHeader.sequenceNumber + "ack:" + packet.tcpHeader.acknowledgementNumber);
        } else if (packet.tcpHeader.isRST()) {
            Log.i("data_state" , "rst" + "  seq:" + packet.tcpHeader.sequenceNumber + "ack:" + packet.tcpHeader.acknowledgementNumber);
        } else if (packet.tcpHeader.isACK()) {
            Log.i("data_state" , "ack" + "  seq:" + packet.tcpHeader.sequenceNumber + "ack:" + packet.tcpHeader.acknowledgementNumber);
            ByteBuffer playloadBuffer = packet.backingBuffer;
            int dataSize = (playloadBuffer.limit() - playloadBuffer.position());
            if (dataSize <= 0) {
                return;
            }
            myAcknowledgementNum += dataSize;
            sendACK();
            mConnectToNet.sendPacketToNet(playloadBuffer);
            Log.i("send_data_to_net",""+dataSize);
        }
    }

    private void processFIN(Packet packet) {

    }

    private void processDefault(Packet packet) {

    }

    private void sendRST() {

    }

    private void sendSYNACK() {
        ByteBuffer responseData = ProxyUtil.createByteBuffer();
        Log.i("in_out___in", "seq:"+mySequenceNum + "     ack:"+myAcknowledgementNum + "   address:" + mPacket.ip4Header.destinationAddress);
        mPacket.updateTCPBuffer(responseData,(byte)(Packet.TCPHeader.SYN|Packet.TCPHeader.ACK),mySequenceNum,myAcknowledgementNum,0);
        mySequenceNum++;
        mBufferToApp.offer(responseData);
        Log.i(TAG,"SYN_ACK");
    }

    private void sendData(ByteBuffer byteBuffer, int dataSize) {
        Log.i("in_out___in", "seq:"+mySequenceNum + "       ack:"+myAcknowledgementNum);
        Log.i("send_data_to_app", "" +dataSize + "  address:"+mPacket.ip4Header.destinationAddress + "src:" + mPacket.ip4Header.sourceAddress);
        Log.i("trans_data", "receive_data_size:"+dataSize);
        Log.i("seq_ack","receive:seq:"+mySequenceNum + "ack:" + myAcknowledgementNum);
        mPacket.updateTCPBuffer(byteBuffer, (byte) (Packet.TCPHeader.PSH | Packet.TCPHeader.ACK),mySequenceNum,myAcknowledgementNum,dataSize);
        mySequenceNum += dataSize;
        //TODO ???
        byteBuffer.position(Packet.IP4_HEADER_SIZE + Packet.TCP_HEADER_SIZE + dataSize);
        mBufferToApp.offer(byteBuffer);
    }

    private void sendACK() {
        Log.i("in_out___in", "seq:"+mySequenceNum + "        ack:"+myAcknowledgementNum);
        ByteBuffer responseData = ProxyUtil.createByteBuffer();
        mPacket.updateTCPBuffer(responseData, (byte)Packet.TCPHeader.ACK, mySequenceNum, myAcknowledgementNum, 0);
        mBufferToApp.offer(responseData);
    }


    private static class ConnectToNet {
        private static final int WAIT_FOR_CONNECT = 0;
        private static final int WAIT_FOR_PROXY_CONNECT = 1;
        private static final int ConnectToProxy = 2;
        private static final int ConnectToServer = 3;

        private SocketChannel mSocketChannel;
        private int mState;
        private Selector mSelector;
        private SelectionKey mSelectionKey;

        public ConnectToNet(Selector selector){
            mState = WAIT_FOR_CONNECT;
            mSelector = selector;
        }

        public boolean initConnect(Object attachment, String proxyAddress, int proxyPort) {
            try {
                mSocketChannel = SocketChannel.open(new InetSocketAddress(proxyAddress,proxyPort));
                mSocketChannel.configureBlocking(false);
                mSelector.wakeup();
                if (mSocketChannel.finishConnect()) {
                    mState = ConnectToProxy;
                    mSelectionKey = mSocketChannel.register(mSelector, SelectionKey.OP_READ, attachment);
                    return true;
                } else {
                    mState = WAIT_FOR_PROXY_CONNECT;
                    mSelectionKey = mSocketChannel.register(mSelector, SelectionKey.OP_CONNECT, attachment);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            return false;
//            Log.i("TCPProxy","initNet");
//            while (mState != ConnectToServer) {
//                switch (mState) {
//                    case WAIT_FOR_CONNECT: processInit(address, port);break;
//                    case ConnectToProxy: processConnect();break;
//                }
//            }
//            Log.i("TCPProxy", "netSuccess");
        }

        private void sendConnectToServer(String address, int port) {
            try {
                String connect = ProxyUtil.getConnect(address, port);
                ByteBuffer byteBuffer = ByteBuffer.wrap(connect.getBytes());
                while (byteBuffer.hasRemaining()) {
                    mSocketChannel.write(byteBuffer);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        private void processConnectToProxy() {
            mState = ConnectToProxy;
            mSelectionKey.interestOps(SelectionKey.OP_READ);
        }

        private boolean processOutput() {
            if (mState != ConnectToServer) {
                try {
                    ByteBuffer byteBuffer = ProxyUtil.createByteBuffer();
                    int size = mSocketChannel.read(byteBuffer);
                    if (size > 0){
                        byteBuffer.flip();
                        String dataFromNet = ProxyUtil.bufferToString(byteBuffer);
                        if (dataFromNet.contains(ProxyUtil.getResult())) {
                            mState = ConnectToServer;
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
                return true;
            }
            return false;
        }

//        private void processInit(InetAddress address, int port) {
//            try {
//                if (mSocketChannel.finishConnect()) {
//                    mState = ConnectToProxy;
//                    String connect = ProxyUtil.getConnect(address.getHostAddress(),port);
//                    ByteBuffer byteBuffer = ByteBuffer.wrap(connect.getBytes());
//                    while (byteBuffer.hasRemaining()) {
//                        mSocketChannel.write(byteBuffer);
//                    }
//                }
//            } catch (IOException e) {
//                e.printStackTrace();
//            }
//        }

//        private void processConnect() {
//            try {
//                ByteBuffer byteBuffer = ProxyUtil.createByteBuffer();
//                int size = mSocketChannel.read(byteBuffer);
//                if (size > 0){
//                    byteBuffer.flip();
//                    String dataFromNet = ProxyUtil.bufferToString(byteBuffer);
//                    if (dataFromNet.contains(ProxyUtil.getResult())) {
//                        mState = ConnectToServer;
//                    }
//                }
//            } catch (IOException e) {
//                e.printStackTrace();
//            }
//        }

        public void sendPacketToNet(ByteBuffer byteBuffer){
            Log.i("trans_data", "send_data_size:"+(byteBuffer.limit() - byteBuffer.position()));
            try {
                while (byteBuffer.hasRemaining()) {
                    mSocketChannel.write(byteBuffer);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public int readFromNet(ByteBuffer byteBuffer) throws IOException {
            return mSocketChannel.read(byteBuffer);
        }

//        public SelectionKey bindProxy(TCPProxy proxy) throws IOException {
//            Log.i("TCPProxy","selector==null?" + (mSelector == null));
//
//            //遇到的坑
//            mSocketChannel.configureBlocking(false);
//            mSelector.wakeup();
//            return mSocketChannel.register(mSelector, SelectionKey.OP_READ, proxy);
//        }

    }


}
