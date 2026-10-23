package com.example.myapplication.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.example.myapplication.R;
import com.example.myapplication.entity.FeatureItem;

import java.util.List;

/**
 * 功能列表适配器，用于适配ListView
 * 每个ListView item显示完整的功能列表行
 */
public class FeatureAdapter extends BaseAdapter {
    private Context mContext;
    private List<FeatureItem> mData;
    private OnItemClickListener mListener;

    /**
     * 构造方法
     * @param context 上下文
     * @param data 功能列表数据
     */
    public FeatureAdapter(Context context, List<FeatureItem> data) {
        this.mContext = context;
        this.mData = data;
    }

    @Override
    public int getCount() {
        // 只显示一行数据，包含所有功能项
        return 1;
    }

    @Override
    public Object getItem(int position) {
        return mData;
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        
        if (convertView == null) {
            convertView = LayoutInflater.from(mContext).inflate(R.layout.item_feature, parent, false);
            holder = new ViewHolder();
            
            // 初始化第一行四个功能项
            holder.feature1 = convertView.findViewById(R.id.feature1);
            holder.feature1Icon = convertView.findViewById(R.id.feature1_icon);
            holder.feature1Text = convertView.findViewById(R.id.feature1_text);
            
            holder.feature2 = convertView.findViewById(R.id.feature2);
            holder.feature2Icon = convertView.findViewById(R.id.feature2_icon);
            holder.feature2Text = convertView.findViewById(R.id.feature2_text);
            
            holder.feature3 = convertView.findViewById(R.id.feature3);
            holder.feature3Icon = convertView.findViewById(R.id.feature3_icon);
            holder.feature3Text = convertView.findViewById(R.id.feature3_text);
            
            holder.feature4 = convertView.findViewById(R.id.feature4);
            holder.feature4Icon = convertView.findViewById(R.id.feature4_icon);
            holder.feature4Text = convertView.findViewById(R.id.feature4_text);
            
            // 初始化第二行两个功能项
            holder.feature5 = convertView.findViewById(R.id.feature5);
            holder.feature5Icon = convertView.findViewById(R.id.feature5_icon);
            holder.feature5Text = convertView.findViewById(R.id.feature5_text);
            
            holder.feature6 = convertView.findViewById(R.id.feature6);
            holder.feature6Icon = convertView.findViewById(R.id.feature6_icon);
            holder.feature6Text = convertView.findViewById(R.id.feature6_text);
            
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }
        
        // 绑定第一行四个功能项数据
        if (mData.size() > 0) {
            FeatureItem item1 = mData.get(0);
            holder.feature1Icon.setImageResource(item1.getIconResId());
            holder.feature1Text.setText(item1.getText());
            
            // 设置促销海报点击事件
            holder.feature1.setOnClickListener(v -> {
                if (mListener != null) {
                    mListener.onItemClick(item1, 0);
                }
            });
        }
        
        if (mData.size() > 1) {
            FeatureItem item2 = mData.get(1);
            holder.feature2Icon.setImageResource(item2.getIconResId());
            holder.feature2Text.setText(item2.getText());
            
            holder.feature2.setOnClickListener(v -> {
                if (mListener != null) {
                    mListener.onItemClick(item2, 1);
                }
            });
        }
        
        if (mData.size() > 2) {
            FeatureItem item3 = mData.get(2);
            holder.feature3Icon.setImageResource(item3.getIconResId());
            holder.feature3Text.setText(item3.getText());
            
            holder.feature3.setOnClickListener(v -> {
                if (mListener != null) {
                    mListener.onItemClick(item3, 2);
                }
            });
        }
        
        if (mData.size() > 3) {
            FeatureItem item4 = mData.get(3);
            holder.feature4Icon.setImageResource(item4.getIconResId());
            holder.feature4Text.setText(item4.getText());
            
            holder.feature4.setOnClickListener(v -> {
                if (mListener != null) {
                    mListener.onItemClick(item4, 3);
                }
            });
        }
        
        // 绑定第二行两个功能项数据
        if (mData.size() > 4) {
            FeatureItem item5 = mData.get(4);
            holder.feature5Icon.setImageResource(item5.getIconResId());
            holder.feature5Text.setText(item5.getText());
            
            holder.feature5.setOnClickListener(v -> {
                if (mListener != null) {
                    mListener.onItemClick(item5, 4);
                }
            });
        }
        
        if (mData.size() > 5) {
            FeatureItem item6 = mData.get(5);
            holder.feature6Icon.setImageResource(item6.getIconResId());
            holder.feature6Text.setText(item6.getText());
            
            holder.feature6.setOnClickListener(v -> {
                if (mListener != null) {
                    mListener.onItemClick(item6, 5);
                }
            });
        }
        
        return convertView;
    }

    /**
     * 点击监听器接口
     */
    public interface OnItemClickListener {
        /**
         * 功能项点击回调
         * @param item 被点击的功能项
         * @param position 被点击的位置
         */
        void onItemClick(FeatureItem item, int position);
    }

    /**
     * 设置点击监听器
     * @param listener 监听器实例
     */
    public void setOnItemClickListener(OnItemClickListener listener) {
        this.mListener = listener;
    }

    /**
     * ViewHolder类，用于缓存控件
     */
    static class ViewHolder {
        // 第一行功能项
        LinearLayout feature1;
        ImageView feature1Icon;
        TextView feature1Text;
        
        LinearLayout feature2;
        ImageView feature2Icon;
        TextView feature2Text;
        
        LinearLayout feature3;
        ImageView feature3Icon;
        TextView feature3Text;
        
        LinearLayout feature4;
        ImageView feature4Icon;
        TextView feature4Text;
        
        // 第二行功能项
        LinearLayout feature5;
        ImageView feature5Icon;
        TextView feature5Text;
        
        LinearLayout feature6;
        ImageView feature6Icon;
        TextView feature6Text;
    }
}
