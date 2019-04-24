package com.net.zhangwenbin.testproxy;

import android.content.Intent;
import android.net.VpnService;
import android.os.Handler;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import com.net.zhangwenbin.testproxy.proxy.ProxyService;
import com.net.zhangwenbin.testproxy.proxy.View.ApkTool;
import com.net.zhangwenbin.testproxy.proxy.View.AppsAdapter;
import com.net.zhangwenbin.testproxy.proxy.View.MyAppInfo;

import java.util.List;

public class MainActivity extends AppCompatActivity implements AppsAdapter.SelectAppListener{

    public static final String PACKAGE_NAME = "package_name";
    public static final String PROXY_ADDRESS = "proxy_address";
    public static final String PROXY_PORT = "proxy_port";

    private Button mStart;
    private Button mSelect;
    private EditText mProxyAddressEditText;
    private EditText mProxyPortEditText;
    private ImageView mAppIamge;
    private TextView mAppNameTextView;
    private RecyclerView mAppsRecyclerView;

    private MyAppInfo mSelectedAppInfo;
    private String mProxyAddress;
    private int mProxyPort;
    private AppsAdapter mAppsAdapter;
    private Handler mHandler = new Handler();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        findViews();
    }

    private void initAppList(){
        new Thread() {
            @Override
            public void run() {
                super.run();
                final List<MyAppInfo> appInfos = ApkTool.scanLocalInstallAppList(MainActivity.this.getPackageManager());
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mAppsAdapter.setAppInfoList(appInfos);
                    }
                });
            }
        }.start();
    }

    private void findViews() {
        mStart = findViewById(R.id.bt_start);
        mSelect = findViewById(R.id.bt_select);
        mAppIamge = findViewById(R.id.app_img);
        mAppNameTextView = findViewById(R.id.app_name);
        mAppsRecyclerView = findViewById(R.id.rv_apps);
        mProxyAddressEditText = findViewById(R.id.et_proxy_address);
        mProxyPortEditText = findViewById(R.id.et_proxy_port);
        mAppsRecyclerView.setLayoutManager(new LinearLayoutManager(this));

        mAppsAdapter = new AppsAdapter(this);
        mAppsRecyclerView.setAdapter(mAppsAdapter);

        mSelect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                initAppList();
            }
        });

        mStart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mProxyAddress = mProxyAddressEditText.getText().toString();
                String proxy_port = mProxyPortEditText.getText().toString();
                if (mSelectedAppInfo == null || mProxyAddress.length() == 0 || proxy_port.length() == 0) {
                    return;
                }
                mProxyPort = Integer.parseInt(proxy_port);
                Intent intent = VpnService.prepare(MainActivity.this);
                if (intent != null) {
                    startActivityForResult(intent,0);
                } else {
                    Intent intent1 = new Intent(MainActivity.this,ProxyService.class);
                    intent1.putExtra(PACKAGE_NAME, mSelectedAppInfo.getPackageName());
                    intent1.putExtra(PROXY_ADDRESS, mProxyAddress);
                    intent1.putExtra(PROXY_PORT, mProxyPort);
                    startService(intent1);
                }
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 0) {
            Intent intent = new Intent(this,ProxyService.class);
            intent.putExtra(PACKAGE_NAME, mSelectedAppInfo.getPackageName());
            intent.putExtra(PROXY_ADDRESS, mProxyAddress);
            intent.putExtra(PROXY_PORT, mProxyPort);
            startService(intent);
        }
    }

    @Override
    public void select(MyAppInfo myAppInfo) {
        mSelectedAppInfo = myAppInfo;
        mAppIamge.setImageDrawable(myAppInfo.getAppImage());
        mAppNameTextView.setText(myAppInfo.getAppName());
    }
}
