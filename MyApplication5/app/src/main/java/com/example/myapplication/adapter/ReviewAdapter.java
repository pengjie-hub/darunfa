package com.example.myapplication.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.R;
import com.example.myapplication.entity.ReviewInfo;
import com.example.myapplication.entity.ReviewReply;

import java.util.ArrayList;
import java.util.List;

/**
 * 用户评论列表适配器
 * 用于商品详情页展示用户评论(初始评论 + 追评)
 * 仅评论作者本人可见"追评"按钮
 */
public class ReviewAdapter extends RecyclerView.Adapter<ReviewAdapter.VH> {

    private List<ReviewInfo> data;
    private int currentUserId = -1; // 当前登录用户ID, 用于判断是否显示追评按钮
    private OnReplyClickListener onReplyClickListener;

    public interface OnReplyClickListener {
        void onReplyClick(ReviewInfo review);
    }

    public ReviewAdapter(List<ReviewInfo> data) {
        this.data = data;
    }

    public void setCurrentUserId(int userId) {
        this.currentUserId = userId;
    }

    public void setOnReplyClickListener(OnReplyClickListener listener) {
        this.onReplyClickListener = listener;
    }

    public void updateData(List<ReviewInfo> newData) {
        List<ReviewInfo> copy = new ArrayList<>();
        if (newData != null) copy.addAll(newData);
        if (this.data != null) this.data.clear();
        if (this.data != null) this.data.addAll(copy);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_review, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        ReviewInfo r = data.get(position);
        holder.tvUser.setText(r.getUserName());
        holder.tvRating.setText(formatRating(r.getRating()));
        holder.tvTime.setText(r.getCreatedAt() == null ? "" : r.getCreatedAt());
        holder.tvContent.setText(r.getContent());

        // 渲染追评列表
        renderReplies(holder, r);

        // 追评按钮: 仅评论作者本人可见
        if (currentUserId != -1 && currentUserId == r.getUserId()) {
            holder.btnReply.setVisibility(View.VISIBLE);
            holder.btnReply.setOnClickListener(v -> {
                if (onReplyClickListener != null) onReplyClickListener.onReplyClick(r);
            });
        } else {
            holder.btnReply.setVisibility(View.GONE);
            holder.btnReply.setOnClickListener(null);
        }
    }

    private void renderReplies(@NonNull VH holder, ReviewInfo r) {
        List<ReviewReply> replies = r.getReplies();
        if (replies == null || replies.isEmpty()) {
            holder.llRepliesContainer.setVisibility(View.GONE);
            holder.llRepliesContainer.removeAllViews();
            return;
        }
        holder.llRepliesContainer.setVisibility(View.VISIBLE);
        holder.llRepliesContainer.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(holder.llRepliesContainer.getContext());
        for (ReviewReply reply : replies) {
            View replyView = inflater.inflate(R.layout.item_reply, holder.llRepliesContainer, false);
            TextView tvReplyTag = replyView.findViewById(R.id.tv_reply_tag);
            TextView tvReplyUser = replyView.findViewById(R.id.tv_reply_user);
            TextView tvReplyTime = replyView.findViewById(R.id.tv_reply_time);
            TextView tvReplyContent = replyView.findViewById(R.id.tv_reply_content);

            // 区分「用户追评」与「商家回复」：两者同表存储，靠 replyType 分辨，
            // 标签文案与配色都不同，避免用户把商家的话误当成买家自己写的。
            boolean merchant = reply.isMerchant();
            tvReplyTag.setText(merchant ? "商家回复" : "追评");
            tvReplyTag.setTextColor(holder.itemView.getContext().getColor(
                    merchant ? R.color.rt_tag_merchant : R.color.rt_tag_highlight));
            tvReplyTag.setBackgroundColor(holder.itemView.getContext().getColor(
                    merchant ? R.color.rt_tag_merchant_surface : R.color.rt_tag_highlight_surface));

            tvReplyUser.setText(reply.getUserName() == null ? "用户" : reply.getUserName());
            tvReplyTime.setText(reply.getCreatedAt() == null ? "" : reply.getCreatedAt());
            tvReplyContent.setText(reply.getContent());
            holder.llRepliesContainer.addView(replyView);
        }
    }

    private String formatRating(double rating) {
        int full = (int) rating;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 5; i++) {
            sb.append(i < full ? "★" : "☆");
        }
        sb.append(" ").append(rating);
        return sb.toString();
    }

    @Override
    public int getItemCount() {
        return data == null ? 0 : data.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvUser, tvRating, tvTime, tvContent;
        TextView btnReply;
        LinearLayout llRepliesContainer;

        VH(@NonNull View itemView) {
            super(itemView);
            tvUser = itemView.findViewById(R.id.tv_review_user);
            tvRating = itemView.findViewById(R.id.tv_review_rating);
            tvTime = itemView.findViewById(R.id.tv_review_time);
            tvContent = itemView.findViewById(R.id.tv_review_content);
            btnReply = itemView.findViewById(R.id.btn_reply);
            llRepliesContainer = itemView.findViewById(R.id.ll_replies_container);
        }
    }
}
