package com.example.myapplication.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.R;

import java.util.List;

/**
 * 专门用于商品详情页面中图片列表(rv_pd_items)的适配器
 */
public class ProductImageAdapter extends RecyclerView.Adapter<ProductImageAdapter.ProductImageViewHolder> {

    private List<Integer> mImageList;

    /**
     * 构造函数，接收图片资源ID列表
     * @param imageList 图片资源ID列表
     */
    public ProductImageAdapter(List<Integer> imageList) {
        this.mImageList = imageList;
    }

    @NonNull
    @Override
    public ProductImageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // 加载自定义的item_product_image.xml布局
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_product_image, parent, false);
        return new ProductImageViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ProductImageViewHolder holder, int position) {
        // 设置图片资源
        holder.productDetailImg.setImageResource(mImageList.get(position));
    }

    @Override
    public int getItemCount() {
        return mImageList == null ? 0 : mImageList.size();
    }

    /**
     * 更新数据方法
     * @param newImageList 新的图片列表
     */
    public void updateData(List<Integer> newImageList) {
        this.mImageList = newImageList;
        notifyDataSetChanged();
    }

    /**
     * 自定义ViewHolder
     */
    static class ProductImageViewHolder extends RecyclerView.ViewHolder {
        ImageView productDetailImg;

        public ProductImageViewHolder(@NonNull View itemView) {
            super(itemView);
            productDetailImg = itemView.findViewById(R.id.product_detail_img);
        }
    }
}
