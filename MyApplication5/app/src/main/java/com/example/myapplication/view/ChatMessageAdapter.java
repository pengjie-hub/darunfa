package com.example.myapplication.view;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.R;

import java.util.List;
import java.util.Objects;

/**
 * 聊天消息列表适配器（USER / AI 两套 ViewType）。
 * <p>
 * 设计要点：
 * <ul>
 *   <li>复用 {@link ListAdapter} + {@link DiffUtil}，让流式增量更新（同一 AI 消息 content 变化）
 *       只触发局部刷新，避免每 tick 一次整列表重建；</li>
 *   <li>AI 消息用 {@code getItemId}（id 即创建时间戳）保证稳定；</li>
 *   <li>打字指示器仅在 {@code STREAMING && content 为空} 时显示，进入流式即隐藏；</li>
 *   <li>错误条可点击（外部可注册 listener 让用户重试）。</li>
 *   <li><b>结构化附件</b>：AI 消息可能附带 0~4 张"工具卡片"（商品 / 参数对比 / 物流 / 订单预览），
 *       在 {@link AiVH#bind} 末尾按 attachment.type 分发到 {@link AttachmentCardBinder}</li>
 * </ul>
 */
public class ChatMessageAdapter extends ListAdapter<ChatMessage, RecyclerView.ViewHolder> {

    public interface OnMetaClickListener {
        void onRetry(@NonNull ChatMessage message);
        void onAbortedClick(@NonNull ChatMessage message);
    }

    private static final int TYPE_USER = 1;
    private static final int TYPE_AI = 2;

    private OnMetaClickListener metaClickListener;

    public ChatMessageAdapter() {
        super(DIFF);
        // 启用 stableIds 可以让 ListAdapter 进一步优化动画；
        // 我们以消息 id（毫秒时间戳）作为稳定 ID。
        setHasStableIds(true);
    }

    public void setOnMetaClickListener(OnMetaClickListener l) {
        this.metaClickListener = l;
    }

    /** 拿最新一条（用于构造上下文）。 */
    public ChatMessage getLast() {
        int n = getItemCount();
        return n == 0 ? null : getItem(n - 1);
    }

    @Override
    public long getItemId(int position) {
        return getItem(position).id;
    }

    @Override
    public int getItemViewType(int position) {
        return getItem(position).role == ChatMessage.Role.USER ? TYPE_USER : TYPE_AI;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_USER) {
            View v = inflater.inflate(R.layout.item_chat_message_user, parent, false);
            return new UserVH(v);
        }
        View v = inflater.inflate(R.layout.item_chat_message_ai, parent, false);
        return new AiVH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ChatMessage m = getItem(position);
        if (holder instanceof UserVH) {
            ((UserVH) holder).bind(m);
        } else if (holder instanceof AiVH) {
            ((AiVH) holder).bind(m, metaClickListener);
        }
    }

    static class UserVH extends RecyclerView.ViewHolder {
        final TextView tvText;

        UserVH(@NonNull View itemView) {
            super(itemView);
            tvText = itemView.findViewById(R.id.tv_chat_text);
        }

        void bind(ChatMessage m) {
            tvText.setText(m.content);
        }
    }

    static class AiVH extends RecyclerView.ViewHolder {
        final TextView tvText;
        final TextView tvMeta;
        final ChatTypingIndicator typing;
        final View metaContainer;
        final LinearLayout attachmentsContainer;

        AiVH(@NonNull View itemView) {
            super(itemView);
            tvText = itemView.findViewById(R.id.tv_chat_text);
            tvMeta = itemView.findViewById(R.id.tv_chat_meta);
            typing = itemView.findViewById(R.id.typing_indicator);
            metaContainer = tvMeta; // 同一个 TextView 兼做容器
            attachmentsContainer = itemView.findViewById(R.id.chat_attachments_container);
        }

        void bind(ChatMessage m, OnMetaClickListener l) {
            // 1. 文本与打字指示器互斥：
            //    - 还没开始打字 (STREAMING + content 空) → 显示三个点
            //    - 有内容（STREAMING/DONE/ABORTED/ERROR）→ 隐藏三个点
            boolean showTyping = m.status == ChatMessage.Status.STREAMING && m.content.isEmpty();
            if (showTyping) {
                typing.setVisibility(View.VISIBLE);
                tvText.setText("");
            } else {
                typing.setVisibility(View.GONE);
                tvText.setText(m.content);
            }

            // 2. 状态副文字：仅在 ERROR / ABORTED 显示
            if (m.status == ChatMessage.Status.ERROR) {
                String err = m.error == null ? "未知错误" : m.error;
                tvMeta.setVisibility(View.VISIBLE);
                tvMeta.setText(itemView.getContext()
                        .getString(R.string.chat_meta_error_prefix) + err);
                tvMeta.setOnClickListener(v -> {
                    if (l != null) l.onRetry(m);
                });
            } else if (m.status == ChatMessage.Status.ABORTED) {
                tvMeta.setVisibility(View.VISIBLE);
                tvMeta.setText(R.string.chat_meta_aborted);
                tvMeta.setOnClickListener(v -> {
                    if (l != null) l.onAbortedClick(m);
                });
            } else {
                tvMeta.setVisibility(View.GONE);
                tvMeta.setOnClickListener(null);
            }

            // 3. 结构化附件：只在终态展示（DONE / ABORTED / ERROR）；
            //    STREAMING 阶段附件尚未挂上，attachments 必然为空，这里 renderAttachments
            //    会安全地用空集合处理（清掉旧 view 即可）。
            AttachmentCardBinder.render(attachmentsContainer,
                    m.attachments,
                    itemView.getContext());
        }
    }

    /** DiffUtil：按 id 判定同一行，按 status/content/error/attachments 比较。 */
    private static final DiffUtil.ItemCallback<ChatMessage> DIFF = new DiffUtil.ItemCallback<ChatMessage>() {
        @Override
        public boolean areItemsTheSame(@NonNull ChatMessage oldItem, @NonNull ChatMessage newItem) {
            return oldItem.id == newItem.id;
        }

        @Override
        public boolean areContentsTheSame(@NonNull ChatMessage oldItem, @NonNull ChatMessage newItem) {
            return oldItem.status == newItem.status
                    && Objects.equals(oldItem.content, newItem.content)
                    && Objects.equals(oldItem.error, newItem.error)
                    && attachmentsEqual(oldItem.attachments, newItem.attachments);
        }

        private boolean attachmentsEqual(List<ChatAttachment> a, List<ChatAttachment> b) {
            if (a == null && b == null) return true;
            if (a == null || b == null) return false;
            if (a.size() != b.size()) return false;
            for (int i = 0; i < a.size(); i++) {
                ChatAttachment x = a.get(i), y = b.get(i);
                if (x == null || y == null) return false;
                if (x.type != y.type) return false;
                if (!Objects.equals(x.data.toString(), y.data.toString())) return false;
            }
            return true;
        }
    };
}
