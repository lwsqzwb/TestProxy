package com.net.zhangwenbin.testproxy.proxy.View;

import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.net.zhangwenbin.testproxy.R;

import java.util.List;

public class AppsAdapter extends RecyclerView.Adapter<AppsAdapter.ViewHolder> {

    private SelectAppListener mSelectAppListener;

    public AppsAdapter(SelectAppListener selectAppListener) {
        mSelectAppListener = selectAppListener;
    }

    private List<MyAppInfo> mAppInfoList;

    public void setAppInfoList(List<MyAppInfo> list) {
        mAppInfoList = list;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int i) {
        View view = LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.item_appinfo, viewGroup, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder viewHolder, int i) {
        viewHolder.fill(mAppInfoList.get(i));
    }

    @Override
    public int getItemCount() {
        if (mAppInfoList == null) {
            return 0;
        }
        return mAppInfoList.size();
    }

    public class ViewHolder extends RecyclerView.ViewHolder{

        private ImageView mAppImage;
        private TextView mAppName;
        private MyAppInfo mAppInfo;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            mAppImage = itemView.findViewById(R.id.item_app_img);
            mAppName = itemView.findViewById(R.id.item_app_name);

            itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (mSelectAppListener != null) {
                        mSelectAppListener.select(mAppInfo);
                    }
                }
            });
        }

        public void fill(MyAppInfo myAppInfo) {
            mAppInfo = myAppInfo;
            mAppImage.setImageDrawable(myAppInfo.getAppImage());
            mAppName.setText(myAppInfo.getAppName());
        }
    }

    public interface SelectAppListener{
        void select(MyAppInfo myAppInfo);
    }

}
