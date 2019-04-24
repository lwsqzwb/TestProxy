package com.net.zhangwenbin.testproxy.proxy.View;

import android.graphics.drawable.Drawable;

public class MyAppInfo {

    private Drawable mAppImage;
    private String mAppName;
    private String mPackageName;

    public Drawable getAppImage() {
        return mAppImage;
    }

    public void setAppImage(Drawable appImage) {
        mAppImage = appImage;
    }

    public String getAppName() {
        return mAppName;
    }

    public void setAppName(String appName) {
        mAppName = appName;
    }

    public String getPackageName() {
        return mPackageName;
    }

    public void setPackageName(String packageName) {
        mPackageName = packageName;
    }

    public boolean isValid() {
        return mAppImage!=null && mAppName!=null && mPackageName != null;
    }
}
