package com.net.zhangwenbin.testproxy.proxy;


import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Iterator;
import java.util.Set;

public class TCPInput implements Runnable {

    private Selector mSelector;
    private static final int HEADER_SIZE = Packet.IP4_HEADER_SIZE + Packet.TCP_HEADER_SIZE;

    public TCPInput(Selector selector) {
        mSelector = selector;
    }

    @Override
    public void run() {
        try {
            while (!Thread.interrupted()) {
                int readyChannels = mSelector.select();

                if (readyChannels == 0) {
                    Thread.sleep(10);
                    continue;
                }

                Set<SelectionKey> keys = mSelector.selectedKeys();
                Iterator<SelectionKey> keyIterator = keys.iterator();

                while (keyIterator.hasNext() && !Thread.interrupted()) {
                    SelectionKey key = keyIterator.next();
                    if (key.isValid()) {
                        if (key.isConnectable()) {
                            processConnect(key);
                        } else if (key.isReadable()) {
                            processInput(key);
                        }
                        keyIterator.remove();
                    }
                }
            }
        } catch (InterruptedException e) {

        } catch (IOException e) {

        }
    }

    private void processInput(SelectionKey key) {
        ByteBuffer byteBuffer = ProxyUtil.createByteBuffer();
        byteBuffer.position(HEADER_SIZE);
        TCPProxy proxy = (TCPProxy) key.attachment();
        proxy.processOutput();
    }

    private void processConnect(SelectionKey key) {
        TCPProxy proxy = (TCPProxy) key.attachment();
        proxy.processConnect();
    }

}
