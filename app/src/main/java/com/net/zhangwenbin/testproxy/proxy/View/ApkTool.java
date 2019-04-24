package com.net.zhangwenbin.testproxy.proxy.View;


import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

public class ApkTool {
    private static String TAG = "ApkTool";
    public static List<MyAppInfo> scanLocalInstallAppList(PackageManager packageManager) {
        List<MyAppInfo> myAppInfos = new ArrayList<MyAppInfo>();
        try {
            List<PackageInfo> packageInfos = packageManager.getInstalledPackages(0);
            for (int i = 0; i < packageInfos.size(); i++) {
                    PackageInfo packageInfo = packageInfos.get(i);
                    //过滤掉系统app
                    if ((ApplicationInfo.FLAG_SYSTEM & packageInfo.applicationInfo.flags) != 0) {
                        continue;
                    }
                    MyAppInfo myAppInfo = new MyAppInfo();
                    myAppInfo.setPackageName(packageInfo.packageName);
                    myAppInfo.setAppName(packageInfo.applicationInfo.loadLabel(packageManager).toString());
                    myAppInfo.setAppImage(packageInfo.applicationInfo.loadIcon(packageManager));
                    if (myAppInfo.isValid()) {
                        myAppInfos.add(myAppInfo);
                    }
                }
        }catch (Exception e){
            Log.e(TAG,"===============获取应用包信息失败");
        }
        return myAppInfos;
    }

}
