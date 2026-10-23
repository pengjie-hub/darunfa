package com.example.myapplication.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.R;

import java.util.ArrayList;
import java.util.List;

/**
 * 搜索标签（关键词胶囊）适配器，复用于「搜索历史」与「热门搜索」两个区域。
 * <p>
 * 通过 {@code hot} 区分样式：历史为白底灰字，热门为浅红底红字。
 * 通过 {@code deletable} 控制是否显示单个删除按钮（仅搜索历史需要）。
 * 点击标签回调 {@link OnTagClickListener#onTagClick(String)}；
 * 点击删除按钮回调 {@link OnTagDeleteListener#onTagDelete(String)}。
 */
public class SearchTagAdapter extends RecyclerView.Adapter<SearchTagAdapter.TagHolder> {

    public interface OnTagClickListener {
        void onTagClick(String keyword);
    }

    public interface OnTagDeleteListener {
        void onTagDelete(String keyword);
    }

    private final List<String> items = new ArrayList<>();
    private final boolean hot;
    private final boolean deletable;
    private OnTagClickListener listener;
    private OnTagDeleteListener deleteListener;

    public SearchTagAdapter(boolean hot, boolean deletable) {
        this.hot = hot;
        this.deletable = deletable;
    }

    public void setOnTagClickListener(OnTagClickListener l) {
        this.listener = l;
    }

    public void setOnTagDeleteListener(OnTagDeleteListener l) {
        this.deleteListener = l;
    }

    public void setItems(List<String> newItems) {
        items.clear();
        if (newItems != null) {
            items.addAll(newItems);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public TagHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_search_tag, parent, false);
        return new TagHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TagHolder holder, int position) {
        String keyword = items.get(position);
        holder.tvTag.setText(keyword);
        if (hot) {
            holder.itemView.setBackgroundResource(R.drawable.search_hot_tag_bg);
            holder.tvTag.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.rt_primary));
        } else {
            holder.itemView.setBackgroundResource(R.drawable.search_tag_bg);
            holder.tvTag.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.rt_text_secondary));
        }
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onTagClick(keyword);
            }
        });
        if (deletable) {
            holder.ivDelete.setVisibility(View.VISIBLE);
            holder.ivDelete.setOnClickListener(v -> {
                if (deleteListener != null) {
                    deleteListener.onTagDelete(keyword);
                }
            });
        } else {
            holder.ivDelete.setVisibility(View.GONE);
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class TagHolder extends RecyclerView.ViewHolder {
        final TextView tvTag;
        final ImageView ivDelete;

        TagHolder(@NonNull View itemView) {
            super(itemView);
            tvTag = itemView.findViewById(R.id.tv_tag_text);
            ivDelete = itemView.findViewById(R.id.iv_tag_delete);
        }
    }
}
