package com.example.myapplication.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.R;
import com.example.myapplication.remote.dto.CsMessageDTO;

import java.util.ArrayList;
import java.util.List;

/**
 * 客服消息列表适配器。
 * <p>
 * 两种气泡：客户自己发的在右侧（品牌红），AI 与人工客服的在左侧（白底描边）。
 * 人工客服额外打一个「人工」标签，让用户能一眼分清哪句是机器回的、哪句是真人回的。
 */
public class CsMessageAdapter extends RecyclerView.Adapter<CsMessageAdapter.VH> {

    private static final int TYPE_SENT = 0;      // 我发出的
    private static final int TYPE_RECEIVED = 1;  // 客服发出的

    private final List<CsMessageDTO> data = new ArrayList<>();

    public void submit(List<CsMessageDTO> list) {
        data.clear();
        if (list != null) data.addAll(list);
        notifyDataSetChanged();
    }

    /** 追加一条（发消息后原地插入，避免整表重刷导致滚动位置跳动）。 */
    public void append(CsMessageDTO msg) {
        if (msg == null) return;
        data.add(msg);
        notifyItemInserted(data.size() - 1);
    }

    public int size() {
        return data.size();
    }

    @Override
    public int getItemViewType(int position) {
        return data.get(position).isMine() ? TYPE_SENT : TYPE_RECEIVED;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        int layout = viewType == TYPE_SENT ? R.layout.item_cs_msg_sent : R.layout.item_cs_msg_received;
        View v = LayoutInflater.from(parent.getContext()).inflate(layout, parent, false);
        return new VH(v, viewType);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        CsMessageDTO msg = data.get(position);
        holder.tvContent.setText(msg.content == null ? "" : msg.content);
        if (holder.viewType == TYPE_RECEIVED) {
            holder.tvSender.setText(msg.senderName == null
                    ? (msg.isAgent() ? "人工客服" : "AI 客服")
                    : msg.senderName);
            holder.tvAgentTag.setVisibility(msg.isAgent() ? View.VISIBLE : View.GONE);
        }
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final int viewType;
        TextView tvContent;
        TextView tvSender;
        TextView tvAgentTag;

        VH(@NonNull View itemView, int viewType) {
            super(itemView);
            this.viewType = viewType;
            tvContent = itemView.findViewById(R.id.tv_cs_content);
            if (viewType == TYPE_RECEIVED) {
                tvSender = itemView.findViewById(R.id.tv_cs_sender);
                tvAgentTag = itemView.findViewById(R.id.tv_cs_agent_tag);
            }
        }
    }
}
