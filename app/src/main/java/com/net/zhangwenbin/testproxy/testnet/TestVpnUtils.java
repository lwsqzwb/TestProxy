package com.net.zhangwenbin.testproxy.testnet;

import java.io.Closeable;
import java.io.IOException;

public class TestVpnUtils {

    public static void closeResource(Closeable closeable) {
        try {
            closeable.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
