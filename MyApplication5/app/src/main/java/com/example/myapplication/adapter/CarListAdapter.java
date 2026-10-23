package com.example.myapplication.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.R;
import com.example.myapplication.entity.CarInfo;

import java.util.List;

public class CarListAdapter extends RecyclerView.Adapter<CarListAdapter.CarViewHolder> {
    private Context mContext;
    private List<CarInfo> mCarInfoList;
    private OnCarItemClickListener mListener;
    
    public CarListAdapter(Context context, List<CarInfo> carItemList) {
        this.mContext = context;
        this.mCarInfoList = carItemList;
    }

    public void setOnCarItemClickListener(OnCarItemClickListener listener) {
        this.mListener = listener;
    }
    
    public void updateData(List<CarInfo> newData) {
        this.mCarInfoList = newData;
        notifyDataSetChanged();
        // 输出日志，方便调试
        android.util.Log.d("CarListAdapter", "updateData: updated with " + (newData != null ? newData.size() : 0) + " items");
    }

    @NonNull
    @Override
    public CarViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_car, parent, false);
        return new CarViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CarViewHolder holder, int position) {
        CarInfo item = mCarInfoList.get(position);
        if (item != null) {
            // 设置选择框状态
            holder.cbSelect.setChecked(item.isSelected());
            
            // 绑定商品图片
            holder.ivImage.setImageResource(item.getProduct_img());
            
            // 绑定商品名称和价格
            holder.tvName.setText(item.getProduct_title());
            try {
                double price = Double.parseDouble(item.getProduct_price());
                holder.tvPrice.setText(String.format("¥%.2f", price));
            } catch (NumberFormatException e) {
                holder.tvPrice.setText("¥" + item.getProduct_price());
            }
            
            // 绑定商品数量
            holder.tvQuantity.setText(String.valueOf(item.getProduct_count()));
            
            // 商品选择框点击事件
            holder.cbSelect.setOnClickListener(v -> {
                item.setSelected(holder.cbSelect.isChecked());
                if (mListener != null) {
                    mListener.onSelectChange(position, holder.cbSelect.isChecked());
                }
            });
            
            // 增加数量按钮点击事件
            holder.tvIncrease.setOnClickListener(v -> {
                // 注意: 不在此处修改 item 的数据, 统一由 Fragment.onQuantityChange 处理,
                // 否则 Fragment 中无法正确计算 oldCount, 导致 delta 为 0 而跳过库存扣减。
                int newCount = item.getProduct_count() + 1;
                if (mListener != null) {
                    mListener.onQuantityChange(position, newCount);
                }
            });

            // 减少数量按钮点击事件
            holder.tvDecrease.setOnClickListener(v -> {
                int newCount = item.getProduct_count() - 1;
                if (newCount < 1) {
                    Toast.makeText(mContext, "商品数量不能少于1", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (mListener != null) {
                    mListener.onQuantityChange(position, newCount);
                }
            });
            
            // 商品项长按事件，用于删除商品
            holder.itemView.setOnLongClickListener(v -> {
                if (mListener != null) {
                    mListener.onItemLongClick(position);
                }
                return true;
            });
        }
    }

    @Override
    public int getItemCount() {
        return mCarInfoList == null ? 0 : mCarInfoList.size();
    }

    static class CarViewHolder extends RecyclerView.ViewHolder {
        
        CheckBox cbSelect;
        ImageView ivImage;
        TextView tvName;
        TextView tvPrice;
        TextView tvQuantity;
        TextView tvDecrease;
        TextView tvIncrease;

        public CarViewHolder(@NonNull View itemView){
            super(itemView);
            cbSelect = itemView.findViewById(R.id.cb_select);
            ivImage = itemView.findViewById(R.id.iv_image);
            tvName = itemView.findViewById(R.id.tv_name);
            tvPrice = itemView.findViewById(R.id.tv_price);
            tvDecrease = itemView.findViewById(R.id.tv_decrease);
            tvQuantity = itemView.findViewById(R.id.tv_quantity);
            tvIncrease = itemView.findViewById(R.id.tv_increase);
        }
    }
    
    // 定义接口，用于回调商品选择、数量变化和长按删除事件
    public interface OnCarItemClickListener {
        void onSelectChange(int position, boolean isSelected);
        void onQuantityChange(int position, int quantity);
        void onItemLongClick(int position);
    }
}