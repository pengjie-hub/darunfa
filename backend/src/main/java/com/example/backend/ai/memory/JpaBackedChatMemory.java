package com.example.backend.ai.memory;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;

import java.util.ArrayList;
import java.util.List;

/**
 * 「请求内驻留 + 跨请求持久化」的会话记忆实现。
 *
 * <h3>为什么不直接用 {@code MessageWindowChatMemory}</h3>
 * {@code MessageWindowChatMemory} 把 {@link ChatMemoryStore} 当作<b>唯一事实来源</b>：
 * 它的 {@code add()} 每次都先 {@code messages()} 重新从 store 读一遍整个列表，追加后再回写。
 * 这个设计对「store 存的就是完整会话」成立，但我们的 store 是一张<b>纯文本历史表</b>
 * （{@code ai_chat_messages} 只有 {@code role} 与 {@code content} 两列），装不下：
 * <ul>
 *   <li>系统提示 —— 读回来就没有了，<b>模型根本收不到角色设定</b>；</li>
 *   <li>助手回合里的 {@code tool_calls} —— 读回来变成一句空文本；</li>
 *   <li>{@code ToolExecutionResultMessage} —— 干脆没有对应列。</li>
 * </ul>
 * 后果在工具调用上最致命：第 1 轮模型要求调工具，框架执行后把「助手回合 + 工具结果」写进记忆，
 * 可一旦回读，工具请求就消失了，第 2 轮请求里只剩「一句空话 + 一条无主的工具结果」，
 * 接口直接报错（实测为 DeepSeek 的
 * {@code The reasoning_content in the thinking mode must be passed back to the API}）。
 *
 * <h3>本实现的模型</h3>
 * <pre>
 *   请求进入 → ChatMemoryProvider 造一个新实例
 *            → 首次访问 messages() 时<b>一次性</b>从 store 载入历史并裁剪到窗口大小
 *            → 之后本次请求内所有读写都发生在这个内存列表上（工具循环因此完整）
 *            → 每次 add() 顺便把「新增的尾部落库」（store 只追加，见 JpaChatMemoryStore）
 * </pre>
 * 关键点是<b>「只载入一次」</b>：数据库是「跨请求的记忆」，
 * 内存列表是「本次请求的真相」。二者职责分离，各司其职。
 *
 * <h3>窗口为什么只在载入时裁剪</h3>
 * 窗口的作用是「别把几百轮历史都塞给模型」，它针对的是<b>历史</b>。
 * 若在请求中途也裁剪，就可能把某次工具调用请求裁掉、只留下它的结果 —— 制造出非法消息序列。
 * 因此本实现只在载入时裁剪，请求中途只增不减；一次请求的轮数由框架的工具循环上限约束，不会失控。
 */
public class JpaBackedChatMemory implements ChatMemory {

    private final Object id;
    private final int maxMessages;
    private final ChatMemoryStore store;

    /** 本次请求的消息列表；{@code null} 表示尚未载入。 */
    private List<ChatMessage> messages;

    public JpaBackedChatMemory(Object id, int maxMessages, ChatMemoryStore store) {
        this.id = id;
        this.maxMessages = maxMessages;
        this.store = store;
    }

    @Override
    public Object id() {
        return id;
    }

    /**
     * 追加一条消息并落库。
     * <p>系统提示固定在列表首位：重复添加时原地替换而不是插一条新的，
     * 否则多轮对话里会堆积出一串内容相同的 system 消息。
     */
    @Override
    public void add(ChatMessage message) {
        List<ChatMessage> list = messages();
        if (message instanceof SystemMessage) {
            int index = indexOfSystemMessage(list);
            if (index >= 0) {
                list.set(index, message);
            } else {
                list.add(0, message);
            }
        } else {
            list.add(message);
        }
        store.updateMessages(id, list);
    }

    /** 返回本次请求的消息列表（首次调用时从 store 载入并裁剪）。 */
    @Override
    public List<ChatMessage> messages() {
        if (messages == null) {
            messages = new ArrayList<>(store.getMessages(id));
            trimToWindow(messages);
        }
        return messages;
    }

    /** 清空记忆：同时清掉内存列表与数据库历史（对应后台「清空记忆」）。 */
    @Override
    public void clear() {
        messages = null;
        store.deleteMessages(id);
    }

    // ==================== 内部实现 ====================

    private int indexOfSystemMessage(List<ChatMessage> list) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i) instanceof SystemMessage) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 把载入的历史裁剪到窗口大小：系统提示永远保留，其余只留最近若干条。
     */
    private void trimToWindow(List<ChatMessage> list) {
        if (maxMessages <= 0 || list.size() <= maxMessages) {
            return;
        }
        int systemCount = 0;
        for (ChatMessage m : list) {
            if (m instanceof SystemMessage) {
                systemCount++;
            }
        }
        int keep = Math.max(maxMessages - systemCount, 1);
        int drop = list.size() - systemCount - keep;
        if (drop <= 0) {
            return;
        }
        // 从前向后删非系统消息，直到剩下 keep 条
        int removed = 0;
        for (int i = 0; i < list.size() && removed < drop; ) {
            if (list.get(i) instanceof SystemMessage) {
                i++;
            } else {
                list.remove(i);
                removed++;
            }
        }
        // 窗口首条若是「无主的工具结果」，一并丢弃 —— 它引用的工具请求已被裁掉
        while (!list.isEmpty() && list.get(0) instanceof ToolExecutionResultMessage) {
            list.remove(0);
        }
    }
}
