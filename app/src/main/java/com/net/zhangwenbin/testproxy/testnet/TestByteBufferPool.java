package com.net.zhangwenbin.testproxy.testnet;

import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentLinkedQueue;

public class TestByteBufferPool {
    private static final int BUFFER_SIZE = 16384;
    private static ConcurrentLinkedQueue<ByteBuffer> pool = new ConcurrentLinkedQueue<>();

    public static ByteBuffer acquire(){
        ByteBuffer buffer = pool.poll();
        if (buffer == null) {
            buffer = ByteBuffer.allocateDirect(BUFFER_SIZE);
        }
        return buffer;
    }

    public static void release(ByteBuffer byteBuffer) {
        byteBuffer.clear();
        pool.offer(byteBuffer);
    }

    public static void clear() {
        pool.clear();
    }
}
