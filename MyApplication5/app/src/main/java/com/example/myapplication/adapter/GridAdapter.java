package com.example.myapplication.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.myapplication.R;
import com.example.myapplication.entity.GridItem;
import java.util.List;

public class GridAdapter extends RecyclerView.Adapter<GridAdapter.ViewHolder> {
    private List<GridItem> mData; // 改为 GridItem 类型

    // 构造方法接收 GridItem 列表
    public GridAdapter(List<GridItem> data) {
        mData = data;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_grid, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        GridItem item = mData.get(position);
        holder.imageView.setImageResource(item.getImageRes()); // 设置图片
        holder.textView.setText(item.getText()); // 设置文字
        holder.currentItem = item; // 绑定当前item数据
        holder.mHolderListener = mListener; // 传递外部监听器引用给ViewHolder
    }

    @Override
    public int getItemCount() {
        return mData.size();
    }
    public interface OnItemClickListener {
        void onItemClick(GridItem item);
    }

    private OnItemClickListener mListener;

    // 设置点击监听器的方法
    public void setOnItemClickListener(OnItemClickListener listener) {
        mListener = listener;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView imageView;
        TextView textView;
        GridItem currentItem; // 存储当前item数据
        OnItemClickListener mHolderListener; // 新增：持有监听器引用

        ViewHolder(View itemView) {
            super(itemView);
            imageView = itemView.findViewById(R.id.grid_item_image);
            textView = itemView.findViewById(R.id.grid_item_text);

            // 设置item点击事件（使用ViewHolder自己的监听器引用）
            itemView.setOnClickListener(v -> {
                if (mHolderListener != null && currentItem != null) {
                    mHolderListener.onItemClick(currentItem); // 使用当前item数据
                }
            });
        }
    }

}