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

public class MineGridAdapter extends RecyclerView.Adapter<MineGridAdapter.ViewHolder> {
    private List<GridItem> mData;
    private OnItemClickListener mListener;

    public MineGridAdapter(List<GridItem> data) {
        mData = data;
    }

    /** 更新数据并刷新 */
    public void updateData(List<GridItem> newData) {
        mData = newData;
        notifyDataSetChanged();
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
        holder.imageView.setImageResource(item.getImageRes());
        holder.textView.setText(item.getText());

        // 显示订单数量角标(>0 时显示)
        if (item.getBadge() > 0) {
            holder.badgeText.setVisibility(android.view.View.VISIBLE);
            // 数量超过 99 显示 99+
            String badgeStr = item.getBadge() > 99 ? "99+" : String.valueOf(item.getBadge());
            holder.badgeText.setText(badgeStr);
        } else {
            holder.badgeText.setVisibility(android.view.View.GONE);
        }

        // 为整个item设置点击事件
        holder.itemView.setOnClickListener(v -> {
            if (mListener != null) {
                mListener.onItemClick(item, position);
            }
        });
    }

    @Override
    public int getItemCount() {
        return mData.size();
    }

    // 点击事件接口
    public interface OnItemClickListener {
        void onItemClick(GridItem item, int position);
    }

    // 设置点击监听器
    public void setOnItemClickListener(OnItemClickListener listener) {
        mListener = listener;
    }

    // ViewHolder类
    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView imageView;
        TextView textView;
        TextView badgeText;

        ViewHolder(View itemView) {
            super(itemView);
            imageView = itemView.findViewById(R.id.grid_item_image);
            textView = itemView.findViewById(R.id.grid_item_text);
            badgeText = itemView.findViewById(R.id.grid_item_badge);
        }
    }
}