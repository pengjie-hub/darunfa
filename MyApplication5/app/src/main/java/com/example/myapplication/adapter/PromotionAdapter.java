package com.example.myapplication.adapter;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.example.myapplication.R;
import com.example.myapplication.remote.dto.PromotionDTO;
import com.example.myapplication.util.PromotionNavigator;

import java.util.ArrayList;
import java.util.List;

/**
 * 促销活动卡片适配器（横向滑动 / 纵向列表均可用）。
 * <p>
 * 图片加载：
 * <ul>
 *   <li>{@code imageUrl} 为 http(s) 地址 → 交给 Glide 异步加载（自带内存/磁盘缓存，
 *       即"图片懒加载"，配合 RecyclerView 的视图复用不会阻塞主线程）；</li>
 *   <li>否则（为空 / 非 http）→ 按促销类型使用渐变占位图，保证无图也清晰可读。</li>
 * </ul>
 */
public class PromotionAdapter extends RecyclerView.Adapter<PromotionAdapter.VH> {

    public interface OnItemClickListener {
        void onItemClick(PromotionDTO item);
    }

    private final List<PromotionDTO> data = new ArrayList<>();
    private OnItemClickListener listener;
    /** true=横向滑动（首页板块，卡片定宽）；false=纵向列表（促销列表页，卡片撑满）。 */
    private boolean horizontal = true;

    /**
     * 设置展示方向。必须在 {@code setAdapter} 之前调用，以决定 item 的宽度策略。
     */
    public void setHorizontal(boolean horizontal) {
        this.horizontal = horizontal;
    }

    public void setData(List<PromotionDTO> list) {
        data.clear();
        if (list != null) {
            data.addAll(list);
        }
        notifyDataSetChanged();
    }

    public void setOnItemClickListener(OnItemClickListener l) {
        this.listener = l;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_promotion, parent, false);
        if (!horizontal) {
            // 纵向列表：卡片撑满可用宽度，去掉右侧间距，改为底部间距
            ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) v.getLayoutParams();
            lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
            lp.setMarginEnd(0);
            lp.bottomMargin = dp(v, 12);
            v.setLayoutParams(lp);
        }
        return new VH(v);
    }

    private static int dp(View v, int value) {
        return Math.round(value * v.getResources().getDisplayMetrics().density);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        PromotionDTO p = data.get(position);

        h.tvType.setText(TextUtils.isEmpty(p.typeLabel) ? "促销" : p.typeLabel);
        h.tvTag.setText(TextUtils.isEmpty(p.tagText) ? "优惠活动" : p.tagText);
        h.tvName.setText(p.name == null ? "" : p.name);
        h.tvDesc.setText(p.description == null ? "" : p.description);

        int placeholder = PromotionNavigator.placeholderRes(p);
        if (isHttpUrl(p.imageUrl)) {
            Glide.with(h.ivImage.getContext())
                    .load(p.imageUrl.trim())
                    .placeholder(placeholder)
                    .error(placeholder)
                    .centerCrop()
                    .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                    .transition(DrawableTransitionOptions.withCrossFade(200))
                    .into(h.ivImage);
        } else {
            // 切回占位图前先取消可能还在飞的 Glide 请求，避免旧图残留
            Glide.with(h.ivImage.getContext()).clear(h.ivImage);
            h.ivImage.setImageResource(placeholder);
        }

        View.OnClickListener click = v -> {
            if (listener != null) {
                listener.onItemClick(p);
            }
        };
        h.itemView.setOnClickListener(click);
        // "查看详情"与整卡点击行为一致，扩大可点区域
        h.tvAction.setOnClickListener(click);
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    private static boolean isHttpUrl(String s) {
        if (s == null) return false;
        String t = s.trim().toLowerCase();
        return t.startsWith("http://") || t.startsWith("https://");
    }

    static class VH extends RecyclerView.ViewHolder {
        final ImageView ivImage;
        final TextView tvType;
        final TextView tvTag;
        final TextView tvName;
        final TextView tvDesc;
        final TextView tvAction;

        VH(@NonNull View itemView) {
            super(itemView);
            ivImage = itemView.findViewById(R.id.iv_promo_image);
            tvType = itemView.findViewById(R.id.tv_promo_type);
            tvTag = itemView.findViewById(R.id.tv_promo_tag);
            tvName = itemView.findViewById(R.id.tv_promo_name);
            tvDesc = itemView.findViewById(R.id.tv_promo_desc);
            tvAction = itemView.findViewById(R.id.tv_promo_action);
        }
    }
}
