package com.example.backend.ai.memory;

import com.example.backend.ai.config.AiMemoryProperties;
import com.example.backend.entity.AiChatMessage;
import com.example.backend.repository.AiChatMessageRepository;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 基于 JPA 的会话记忆存储（LangChain4j {@link ChatMemoryStore} 实现）。
 *
 * <h3>为什么复用 ai_chat_messages 而不新建表</h3>
 * 这张表是既有实现留下的对话历史表，两个 {@code /history} 接口（后台
 * {@code GET /api/admin/ai/history}、前台 {@code GET /api/assistant/history}）以及 App
 * 的历史气泡回放都直接读它。<b>把它实现成 ChatMemoryStore 的底层</b>，
 * 等于在不改表结构、不改接口契约的前提下把「手写读写」换成「框架托管」。
 *
 * <h3>memoryId 约定</h3>
 * memoryId 是一个<b>不透明字符串</b>，本项目使用：
 * <ul>
 *   <li>{@code u:<userId>} —— 登录用户（小运与小发共用，与原实现一致）；</li>
 *   <li>{@code anon:<uuid>} —— 匿名会话，不落库（{@link #resolveUserId} 返回 null）。</li>
 * </ul>
 *
 * <h3>为什么 updateMessages 是「追加」而不是「覆盖」</h3>
 * 两个原因：
 * <ol>
 *   <li>{@link JpaBackedChatMemory} 载入历史时会先按 {@code ai.memory.max-messages} 裁剪，
 *       传入的列表通常是「库中完整历史的一个尾部窗口 + 本次新增」。
 *       若照单全收地覆盖，窗口之外的老消息就被永久抹掉了，{@code /history} 回放会缺段；</li>
 *   <li>一次请求内 {@code updateMessages} 会被调用多次（用户消息、每个工具回合、最终回答各一次），
 *       每次都传入「截至当前」的完整列表。按「前缀重叠」求增量，天然幂等，
 *       不会因为调用次数多而写重复行。</li>
 * </ol>
 * 因此这里先求出「传入列表的前缀」与「库中尾部」的最大重叠长度，只把真正新增的尾部落库 ——
 * 数据库始终保留完整历史，窗口只影响注入模型的那几条。
 */
@Component
public class JpaChatMemoryStore implements ChatMemoryStore {

    /** 登录用户 memoryId 前缀。 */
    public static final String USER_PREFIX = "u:";

    /** 匿名会话 memoryId 前缀（不落库）。 */
    public static final String ANON_PREFIX = "anon:";

    /** 助手未能生成答复时的占位文本（保证 content 列非空，且重载后比对仍一致）。 */
    private static final String EMPTY_REPLY_PLACEHOLDER = "（未生成答复）";

    private static final String ROLE_USER = "user";
    private static final String ROLE_ASSISTANT = "assistant";

    /**
     * LangChain4j 默认内容注入模板在「提问」与「召回知识」之间插入的分隔行。
     * 落库前据此把 RAG 脚手架剥掉，详见 {@link #stripRagInjection(String)}。
     */
    private static final String RAG_INJECTION_MARKER = "Answer using the following information:";

    private final AiChatMessageRepository repository;
    private final AiMemoryProperties props;

    public JpaChatMemoryStore(AiChatMessageRepository repository, AiMemoryProperties props) {
        this.repository = repository;
        this.props = props;
    }

    // =====================================================================
    //  ChatMemoryStore 实现
    // =====================================================================

    /**
     * 读取该 memoryId 的历史消息（时间正序）。
     * <p>只返回 user / assistant 两类；system 提示由框架每次请求现算，不参与记忆。
     */
    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        Long userId = resolveUserId(memoryId);
        if (userId == null) return List.of();

        List<AiChatMessage> rows = recentAsc(userId);
        List<ChatMessage> out = new ArrayList<>(rows.size());
        for (AiChatMessage row : rows) {
            String text = normalizeText(row.getContent());
            if (ROLE_USER.equalsIgnoreCase(row.getRole())) {
                out.add(new UserMessage(text));
            } else if (ROLE_ASSISTANT.equalsIgnoreCase(row.getRole())) {
                out.add(new AiMessage(text));
            }
            // 其它角色（理论上不存在）直接跳过
        }
        return out;
    }

    /** 追加写入新增的会话消息（见类注释：只追加，绝不整体覆盖）。 */
    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        Long userId = resolveUserId(memoryId);
        if (userId == null || messages == null || messages.isEmpty()) return;

        List<PendingRow> incoming = new ArrayList<>(messages.size());
        for (ChatMessage m : messages) {
            PendingRow row = toPendingRow(m);
            if (row != null) incoming.add(row);
        }
        if (incoming.isEmpty()) return;

        int overlap = longestPrefixOverlap(incoming, recentAsc(userId));
        for (int i = overlap; i < incoming.size(); i++) {
            PendingRow pending = incoming.get(i);
            AiChatMessage entity = new AiChatMessage();
            entity.setUserId(userId);
            entity.setRole(pending.role);
            entity.setContent(pending.content);
            repository.save(entity);
        }
    }

    /** 清空该 memoryId 的全部会话记忆（对应后台「清空记忆」）。 */
    @Override
    public void deleteMessages(Object memoryId) {
        Long userId = resolveUserId(memoryId);
        if (userId != null) {
            repository.deleteByUserId(userId);
        }
    }

    // =====================================================================
    //  内部实现
    // =====================================================================

    /** 取该用户最近的会话消息并还原成时间正序。 */
    private List<AiChatMessage> recentAsc(Long userId) {
        List<AiChatMessage> desc = repository.findTop20ByUserIdOrderByIdDesc(userId);
        if (desc == null || desc.isEmpty()) return List.of();
        List<AiChatMessage> asc = new ArrayList<>(desc);
        Collections.reverse(asc);   // 仓储按 id 降序返回，倒过来才是对话顺序
        return asc;
    }

    /**
     * 求「传入列表的前缀」与「库中记录的尾部」的最大重叠长度。
     * <p>
     * 例：库中为 [u1,a1,u2,a2]，传入 [u2,a2,u3,a3]（窗口驱逐后回写）→ 重叠 2，只追加 u3、a3。
     */
    private int longestPrefixOverlap(List<PendingRow> incoming, List<AiChatMessage> existingAsc) {
        int max = Math.min(incoming.size(), existingAsc.size());
        for (int k = max; k >= 1; k--) {
            int dbFrom = existingAsc.size() - k;
            boolean same = true;
            for (int i = 0; i < k; i++) {
                if (!sameAs(incoming.get(i), existingAsc.get(dbFrom + i))) {
                    same = false;
                    break;
                }
            }
            if (same) return k;
        }
        return 0;
    }

    private boolean sameAs(PendingRow pending, AiChatMessage row) {
        if (row.getRole() == null || !row.getRole().equalsIgnoreCase(pending.role)) return false;
        return normalizeText(row.getContent()).equals(pending.content);
    }

    /**
     * 把框架消息转成待落库的一行。
     *
     * <p>三类消息<b>刻意不落库</b>：
     * <ul>
     *   <li>{@link dev.langchain4j.data.message.SystemMessage} —— 系统提示由框架每次请求现算
     *       （含当天的日期、最新长期记忆），落库等于把一份过期副本写进历史；</li>
     *   <li>{@link dev.langchain4j.data.message.ToolExecutionResultMessage} —— 工具结果属于
     *       「本次推理的中间过程」，表里没有对应列，用户翻历史也不该看到原始 JSON；</li>
     *   <li><b>空文本且带工具请求的 {@link AiMessage}</b> —— 那是「模型决定调工具」的中间回合，
     *       它本身没有内容。若照常落库，历史里会混进一串「（未生成答复）」的假回复。</li>
     * </ul>
     * 只有真正说给用户听的话（用户提问 + 助手最终回答）才进历史表。
     */
    private PendingRow toPendingRow(ChatMessage message) {
        if (message instanceof UserMessage um) {
            return new PendingRow(ROLE_USER, normalizeText(stripRagInjection(um.singleText())));
        }
        if (message instanceof AiMessage am) {
            boolean blank = am.text() == null || am.text().isBlank();
            if (blank && am.hasToolExecutionRequests()) {
                return null;
            }
            return new PendingRow(ROLE_ASSISTANT, normalizeText(am.text()));
        }
        return null;
    }

    /**
     * 剥掉 RAG（检索增强）注入到用户消息里的脚手架文本。
     *
     * <h3>为什么必须剥</h3>
     * LangChain4j 的检索增强会把召回的知识片段<b>并入用户消息本身</b>，形如：
     * <pre>
     *   今天有多少订单？销售额多少？
     *
     *   Answer using the following information:
     *   【客服接待量统计】每位客服的接待量按…
     * </pre>
     * 这份「合并稿」是给模型看的<b>内部格式</b>：凡是通过 {@code ChatMemory} 落库的内容，
     * 都会原样出现在 {@code /history} 接口与 App 的历史气泡里。不剥掉的话，用户翻聊天记录
     * 会看到一句中文提问后面跟着一段英文指令和别人的规则文本 —— 既难看，也泄露了内部实现。
     * <p>
     * 附带好处：历史里存的是用户真正问的那句话，不会被召回片段挤占掉截断额度
     * （{@code ai.memory.max-content-chars}）。
     * <p>
     * 分隔符取 LangChain4j 默认注入模板里的那一行。它在「提问 + 召回知识」之间，
     * 正常用户输入几乎不可能命中；且这里判定的是<b>位置大于 0</b>，
     * 万一句子以它开头也不会被截成空串。
     */
    private String stripRagInjection(String raw) {
        if (raw == null) return null;
        int marker = raw.indexOf(RAG_INJECTION_MARKER);
        return marker > 0 ? raw.substring(0, marker).trim() : raw;
    }

    /**
     * 统一规范化：去空白 → 空文本转占位符 → 按 {@code ai.memory.max-content-chars} 截断。
     * <p>
     * 读、写、比对三处<b>共用同一函数</b>是「前缀重叠」算法成立的前提 ——
     * 只要有一处用了不同的文本形式，比对就会失配，从而重复插入历史。
     */
    private String normalizeText(String raw) {
        if (raw == null) return EMPTY_REPLY_PLACEHOLDER;
        String text = raw.trim();
        if (text.isEmpty()) return EMPTY_REPLY_PLACEHOLDER;
        int max = props.getMaxContentChars();
        if (max > 0 && text.length() > max) return text.substring(0, max);
        return text;
    }

    /**
     * 解析 memoryId → 用户 ID。
     * <p>支持 {@code "u:123"} / 纯数字字符串 / {@link Number}；匿名前缀与非法值返回 {@code null}。
     */
    public static Long resolveUserId(Object memoryId) {
        if (memoryId == null) return null;
        if (memoryId instanceof Number n) return n.longValue();

        String key = String.valueOf(memoryId).trim();
        if (key.isEmpty()) return null;
        if (key.startsWith(USER_PREFIX)) key = key.substring(USER_PREFIX.length());
        else if (key.startsWith(ANON_PREFIX)) return null;

        try {
            return Long.parseLong(key);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 构造登录用户的 memoryId。 */
    public static String userKey(Long userId) {
        return USER_PREFIX + userId;
    }

    /** 构造匿名会话的 memoryId（不落库，因此不会污染登录用户的历史）。 */
    public static String anonKey() {
        return ANON_PREFIX + java.util.UUID.randomUUID();
    }

    /** 待落库的一行（role + 规范化后的内容）。 */
    private record PendingRow(String role, String content) {}
}
