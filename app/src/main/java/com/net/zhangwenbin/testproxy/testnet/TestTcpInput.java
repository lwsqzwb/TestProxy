package com.net.zhangwenbin.testproxy.testnet;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

public class TestTcpInput implements Runnable {
    private static final int HEADER_SIZE = TestPacket.IP4_HEADER_SIZE + TestPacket.TCP_HEADER_SIZE;

    private ConcurrentLinkedQueue<ByteBuffer> mBufferToNet;
    private Selector mSelector;

    public TestTcpInput(ConcurrentLinkedQueue<ByteBuffer> bufferToNet, Selector selector) {
        mBufferToNet = bufferToNet;
        mSelector = selector;
    }

    @Override
    public void run() {
        try {
            while (!Thread.interrupted()) {
                int readyChannels = mSelector.select();

                if (readyChannels == 0) {
                    Thread.sleep(15);
                    continue;
                }
                Set<SelectionKey> selectionKeySet = mSelector.selectedKeys();
                Iterator<SelectionKey> iterator = selectionKeySet.iterator();
                while (iterator.hasNext()) {
                    SelectionKey key = iterator.next();
                    if (key.isValid()) {
                        if (key.isReadable()) {
                            processInput(key);
                        }
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {

        }
    }

    private void processInput(SelectionKey key) {

    }
}
