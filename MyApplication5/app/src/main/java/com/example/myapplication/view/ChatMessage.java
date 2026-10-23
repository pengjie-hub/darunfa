package com.example.myapplication.view;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * 聊天消息模型。
 * <p>
 * 设计为不可变对象：UI 更新通过生成新实例并调用
 * {@link #withContent(String, Status)}，避免适配器在滚动中读到半改状态。
 *
 * @param id        消息唯一 ID（毫秒时间戳），用于 RecyclerView 局部刷新
 * @param role      {@link Role#USER} 用户 / {@link Role#AI} 助手
 * @param content   消息文本（流式过程中逐步增长）
 * @param status    {@link Status#STREAMING} 正在打字 / {@link Status#DONE} 完成 /
 *                  {@link Status#ERROR} 出错 / {@link Status#ABORTED} 用户中断
 * @param error     错误信息（status == ERROR 时展示）
 * @param attachments 结构化附件（{@link ChatAttachment} 列表），渲染为对应卡片。
 *                    用户消息和纯文本 AI 消息的 attachments 为空。
 */
public final class ChatMessage {

    public enum Role { USER, AI }
    public enum Status { STREAMING, DONE, ERROR, ABORTED }

    public final long id;
    public final Role role;
    @NonNull public final String content;
    public final Status status;
    @Nullable public final String error;
    @NonNull public final List<ChatAttachment> attachments;

    private ChatMessage(long id,
                        Role role,
                        @NonNull String content,
                        Status status,
                        @Nullable String error,
                        @NonNull List<ChatAttachment> attachments) {
        this.id = id;
        this.role = role;
        this.content = content;
        this.status = status;
        this.error = error;
        this.attachments = Collections.unmodifiableList(attachments);
    }

    /**
     * 消息 ID 生成器（单调自增，初值取当前毫秒时间戳）。
     * <p>
     * 这里<b>不能</b>直接用 {@code System.currentTimeMillis()}：适配器是
     * {@link androidx.recyclerview.widget.ListAdapter} + {@code setHasStableIds(true)}，以 id 作为行的唯一标识。
     * 回放历史时会在一瞬间连续创建十几条消息，毫秒级时间戳必然重复 →
     * DiffUtil 认为它们是同一行，气泡会互相顶掉（表现为"少显示了几条"）。
     */
    private static final java.util.concurrent.atomic.AtomicLong SEQ =
            new java.util.concurrent.atomic.AtomicLong(System.currentTimeMillis());

    private static long nextId() {
        return SEQ.incrementAndGet();
    }

    /** 用户消息（固定 DONE、无附件）。 */
    public static ChatMessage user(@NonNull String content) {
        return new ChatMessage(nextId(), Role.USER, content, Status.DONE, null, Collections.emptyList());
    }

    /** AI 消息初始（STREAMING + 空内容，AI 头部会显示打字指示器）。 */
    public static ChatMessage aiPlaceholder() {
        return new ChatMessage(nextId(), Role.AI, "", Status.STREAMING, null, Collections.emptyList());
    }

    /**
     * 带文案的 AI 等待消息（STREAMING + 提示语）。
     * <p>
     * 与 {@link #aiPlaceholder()} 的区别：这里的 content 不为空，因此会<b>显示提示文字</b>
     * 而不是三个点的打字动画。后端一次问答要跑完最多 5 轮工具调用（2-8 秒），
     * 期间给一句「小发正在为你查询…」远比一个空转动的动画让人安心。
     * <p>
     * 真回复开始流式输出时，content 会被整体替换掉，提示语自动消失。
     */
    public static ChatMessage aiPending(@NonNull String hint) {
        return new ChatMessage(nextId(), Role.AI, hint, Status.STREAMING, null, Collections.emptyList());
    }

    /**
     * 已完成的 AI 消息（DONE、无附件）。
     * <p>
     * 专供<b>回放服务端持久化的历史</b>使用 —— 历史里存的是纯文本，
     * 当时的工具卡片（商品/对比/物流/订单预览）没有落库，所以这里不带附件。
     */
    public static ChatMessage aiDone(@NonNull String content) {
        return new ChatMessage(nextId(), Role.AI, content, Status.DONE, null, Collections.emptyList());
    }

    /** 流式增量 / 终态拷贝（只改 content + status）。 */
    public ChatMessage withContent(@NonNull String newContent, Status newStatus) {
        return new ChatMessage(id, role, newContent, newStatus, error, attachments);
    }

    /** 终态：完成后一次性挂上 attachments（覆盖原有）。通常只调用一次。 */
    public ChatMessage withAttachments(@NonNull List<ChatAttachment> newAttachments) {
        return new ChatMessage(id, role, content, status, error, ChatAttachment.copyOf(newAttachments));
    }

    /** 标记错误（保留 content 与 attachments）。 */
    public ChatMessage withError(@NonNull String errorMsg) {
        return new ChatMessage(id, role, content, Status.ERROR, errorMsg, attachments);
    }

    /** 标记用户主动中断。 */
    public ChatMessage withAborted() {
        return new ChatMessage(id, role, content, Status.ABORTED, null, attachments);
    }
}
