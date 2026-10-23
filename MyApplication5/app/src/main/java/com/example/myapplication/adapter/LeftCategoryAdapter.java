package com.example.myapplication.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import com.example.myapplication.R;
import java.util.List;

/**
 * 左侧分类列表适配器
 * 用于在分类页面左侧展示分类列表，并处理选中状态
 */
public class LeftCategoryAdapter extends RecyclerView.Adapter<LeftCategoryAdapter.ViewHolder> {
    // 分类数据列表
    private List<String> mData;
    // 分类点击监听器
    private OnItemClickListener mListener;
    // 当前选中的分类位置，默认为0
    private int mSelectedPosition = 0;

    /**
     * 构造方法
     * @param data 分类数据列表
     */
    public LeftCategoryAdapter(List<String> data) {
        mData = data;
    }

    /**
     * 创建ViewHolder
     * @param parent 父容器
     * @param viewType 视图类型
     * @return ViewHolder实例
     */
    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // 加载左侧分类列表项布局
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.left_list_item, parent, false);
        return new ViewHolder(view);
    }

    /**
     * 绑定数据到ViewHolder
     * @param holder ViewHolder实例
     * @param position 数据位置
     */
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        String item = mData.get(position);
        // 设置分类名称
        holder.textView.setText(item);
        
        // 设置选中状态
        boolean isSelected = position == mSelectedPosition;
        if (isSelected) {
            // 选中状态：白色背景、红色文字、显示左侧红色指示器
            holder.itemView.setBackgroundColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.rt_surface));
            holder.textView.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.rt_primary));
            holder.textView.setPadding(20, 0, 0, 0); // 调整文字左边距，为红色指示器留出空间
            holder.selectedIndicator.setVisibility(View.VISIBLE); // 显示左侧红色指示器
        } else {
            // 未选中状态：灰色背景、黑色文字、隐藏左侧红色指示器
            holder.itemView.setBackgroundColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.rt_background));
            holder.textView.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.rt_text_primary));
            holder.textView.setPadding(20, 0, 0, 0); // 调整文字左边距，保持一致
            holder.selectedIndicator.setVisibility(View.GONE); // 隐藏左侧红色指示器
        }
    }

    /**
     * 获取分类数量
     * @return 分类数量
     */
    @Override
    public int getItemCount() {
        return mData.size();
    }

    /**
     * 分类点击监听器接口
     */
    public interface OnItemClickListener {
        /**
         * 分类点击回调
         * @param position 被点击的分类位置
         */
        void onItemClick(int position);
    }

    /**
     * 设置分类点击监听器
     * @param listener 监听器实例
     */
    public void setOnItemClickListener(OnItemClickListener listener) {
        mListener = listener;
    }

    /**
     * 设置选中的分类位置
     * @param position 要选中的分类位置
     */
    public void setSelectedPosition(int position) {
        mSelectedPosition = position;
        notifyDataSetChanged(); // 刷新列表，更新选中状态
    }

    /**
     * 左侧分类列表项ViewHolder
     */
    class ViewHolder extends RecyclerView.ViewHolder {
        TextView textView; // 分类名称
        View selectedIndicator; // 选中指示器

        /**
         * ViewHolder构造方法
         * @param itemView 分类列表项视图
         */
        ViewHolder(View itemView) {
            super(itemView);
            // 初始化控件
            textView = itemView.findViewById(R.id.left_text_name);
            selectedIndicator = itemView.findViewById(R.id.selected_indicator);
            
            // 设置分类项点击事件
            itemView.setOnClickListener(v -> {
                if (mListener != null) {
                    int position = getAdapterPosition();
                    // 检查位置是否有效，防止返回 -1 导致的 IndexOutOfBoundsException
                    if (position != RecyclerView.NO_POSITION) {
                        mSelectedPosition = position; // 更新选中位置
                        notifyDataSetChanged(); // 刷新列表，更新选中状态
                        mListener.onItemClick(position); // 回调监听器
                    }
                }
            });
        }
    }
}
