package com.example.myapplication.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.myapplication.R;
import com.example.myapplication.entity.Goods;
import java.util.List;

/**
 * 右侧商品列表适配器
 * 用于在分类页面右侧展示商品列表
 */
public class RightCategoryAdapter extends RecyclerView.Adapter<RightCategoryAdapter.ViewHolder> {
    // 商品数据列表
    private List<Goods> mData;
    // 商品点击监听器
    private OnItemClickListener mListener;
    // 添加到购物车点击监听器
    private OnAddToCartClickListener mAddToCartListener;

    /**
     * 构造方法
     * @param data 商品数据列表
     */
    public RightCategoryAdapter(List<Goods> data) {
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
        // 加载商品列表项布局
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.right_list_item, parent, false);
        return new ViewHolder(view);
    }

    /**
     * 绑定数据到ViewHolder
     * @param holder ViewHolder实例
     * @param position 数据位置
     */
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Goods goods = mData.get(position);
        
        // 设置商品图片
        holder.ivGoods.setImageResource(goods.getImg());
        // 设置商品标题
        holder.tvTitle.setText(goods.getTitle());
        // 设置商品价格
        holder.tvPrice.setText(goods.getPrice());
        // 设置商品标签
        holder.tvTag.setText(goods.getTag());

        // 为整个商品项设置点击事件，跳转到商品详情页
        holder.itemView.setOnClickListener(v -> {
            if (mListener != null) {
                mListener.onItemClick(goods);
            }
        });
        
        // 为购物车按钮设置点击事件
        holder.btnAddToCart.setOnClickListener(v -> {
            if (mAddToCartListener != null) {
                mAddToCartListener.onAddToCartClick(goods);
            }
        });
    }

    /**
     * 获取商品数量
     * @return 商品数量
     */
    @Override
    public int getItemCount() {
        return mData.size();
    }

    /**
     * 商品点击监听器接口
     */
    public interface OnItemClickListener {
        /**
         * 商品点击回调
         * @param goods 被点击的商品对象
         */
        void onItemClick(Goods goods);
    }

    /**
     * 设置商品点击监听器
     * @param listener 监听器实例
     */
    public void setOnItemClickListener(OnItemClickListener listener) {
        mListener = listener;
    }

    /**
     * 添加到购物车点击监听器接口
     */
    public interface OnAddToCartClickListener {
        /**
         * 添加到购物车点击回调
         * @param goods 要添加到购物车的商品对象
         */
        void onAddToCartClick(Goods goods);
    }

    /**
     * 设置添加到购物车点击监听器
     * @param listener 监听器实例
     */
    public void setOnAddToCartClickListener(OnAddToCartClickListener listener) {
        mAddToCartListener = listener;
    }

    /**
     * 商品列表项ViewHolder
     */
    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivGoods; // 商品图片
        TextView tvTitle; // 商品标题
        TextView tvPrice; // 商品价格
        TextView tvTag; // 商品标签
        androidx.appcompat.widget.AppCompatButton btnAddToCart; // 添加到购物车按钮

        /**
         * ViewHolder构造方法
         * @param itemView 商品列表项视图
         */
        ViewHolder(View itemView) {
            super(itemView);
            // 初始化控件
            ivGoods = itemView.findViewById(R.id.category_pic);
            tvTitle = itemView.findViewById(R.id.category_title);
            tvPrice = itemView.findViewById(R.id.category_price);
            tvTag = itemView.findViewById(R.id.category_tag);
            btnAddToCart = itemView.findViewById(R.id.category_btn_add_to_cart);
        }
    }
}
