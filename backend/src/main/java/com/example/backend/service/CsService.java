package com.example.backend.service;

import com.example.backend.ai.assistant.CsAgent;
import com.example.backend.common.BusinessException;
import com.example.backend.entity.CsConversation;
import com.example.backend.entity.CsMessage;
import com.example.backend.repository.CsConversationRepository;
import com.example.backend.repository.CsMessageRepository;
import com.example.backend.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 在线客服服务（前台客户 + 后台客服共用一套会话数据）。
 * <p>
 * 设计要点：
 * <ol>
 *   <li><b>一用户一会话</b>：按 {@code user_id} 复用同一条 {@link CsConversation}，
 *       后台客服打开某客户时能看到完整历史，而不是一堆断头会话。</li>
 *   <li><b>AI 自动应答走真实大模型</b>：客户发消息后，把最近若干轮对话拼成上下文交给
 *       {@link CsAgent}（LangChain4j 声明式 AI 服务），用客服人设的提示词生成回复并落库，
 *       前台立刻能看到 AI 的答复。</li>
 *   <li><b>可无缝转人工</b>：客户说「转人工」或 AI 调用失败时，会话状态切到
 *       {@code HUMAN}；后台客服在同一会话里回复，客户刷新即可看到。</li>
 *   <li><b>后台未读数</b>：客户每发一条 {@code unreadForAdmin} +1，客服打开会话后清零，
 *       后台侧边栏用这个数字做红点提醒。</li>
 * </ol>
 */
@Service
public class CsService {

    private static final Logger log = LoggerFactory.getLogger(CsService.class);

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("yyyy年MM月dd日 EEEE", Locale.CHINA);

    /** 带入 LLM 的历史轮数上限（每轮≈一问一答）。 */
    private static final int HISTORY_LIMIT = 12;

    /** 客户消息最大长度，与 cs_messages.content 列宽保持一致。 */
    private static final int MAX_CONTENT = 1000;

    /** 转人工的显式指令关键词。 */
    private static final List<String> HUMAN_KEYWORDS =
            List.of("转人工", "人工客服", "真人", "找人工", "人工服务");

    /** 单条消息最多调几次模型（含首次）：偶发瞬断重试一次，仍失败才降级转人工。 */
    private static final int AI_MAX_ATTEMPTS = 2;

    /** 重试前的等待毫秒数，给对端一点恢复时间。 */
    private static final long AI_RETRY_BACKOFF_MS = 600L;

    /**
     * AI 客服应答器（LangChain4j 声明式 {@code @AiService}）。
     * <p>改造前这里注入的是手写的 {@code DeepSeekClient}，由本类自己拼 messages 并发 HTTP。
     */
    private final CsAgent csAgent;
    private final CsConversationRepository conversationRepository;
    private final CsMessageRepository messageRepository;
    private final UserRepository userRepository;

    public CsService(CsAgent csAgent,
                     CsConversationRepository conversationRepository,
                     CsMessageRepository messageRepository,
                     UserRepository userRepository) {
        this.csAgent = csAgent;
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.userRepository = userRepository;
    }

    // ==================== 前台（客户侧） ====================

    /** 取当前用户的会话，没有就按用户名建一条（幂等）。 */
    @Transactional
    public CsConversation getOrCreate(Long userId) {
        if (userId == null) throw new BusinessException(401, "未登录");
        return conversationRepository.findByUserId(userId).orElseGet(() -> {
            CsConversation c = new CsConversation();
            c.setUserId(userId);
            c.setUserName(displayNameOf(userId));
            c.setStatus(CsConversation.STATUS_AI);
            c.setUnreadForAdmin(0);
            c.setLastMessageAt(LocalDateTime.now());
            return conversationRepository.save(c);
        });
    }

    /** 客户会话概览（前台进入客服页时先拿这个）。 */
    @Transactional
    public Map<String, Object> myConversation(Long userId) {
        CsConversation c = getOrCreate(userId);
        // 重新进入客服页时，主动把状态从「用户结束」拉回可服务状态
        if (CsConversation.STATUS_CLOSED.equals(c.getStatus())) {
            c.setStatus(CsConversation.STATUS_AI);
            conversationRepository.save(c);
        }
        return toConversationMap(c, false, userId);
    }

    /** 客户视角的完整消息列表（时间正序）。 */
    public List<Map<String, Object>> myMessages(Long userId, Long conversationId) {
        CsConversation c = requireOwned(userId, conversationId);
        return toMessageMaps(messageRepository.findByConversationIdOrderByIdAsc(c.getId()));
    }

    /**
     * 客户发送一条消息 → 落库 → 调用大模型生成自动回复 → 落库并返回。
     * <p>
     * 返回体同时带上刚写入的两条消息（客户消息 + AI 回复），前端据此原地追加气泡，
     * 不必再拉一次全量列表。
     */
    @Transactional
    public Map<String, Object> sendFromUser(Long userId, Long conversationId, String rawContent) {
        String content = normalize(rawContent);
        CsConversation c = requireOwned(userId, conversationId);

        CsMessage userMsg = new CsMessage();
        userMsg.setConversationId(c.getId());
        userMsg.setSender(CsMessage.SENDER_USER);
        userMsg.setSenderName(c.getUserName());
        userMsg.setContent(content);
        messageRepository.save(userMsg);

        // 客户一旦说话，就说明会话重新活跃了
        c.setStatus(CsConversation.STATUS_HUMAN.equals(c.getStatus())
                ? CsConversation.STATUS_HUMAN : CsConversation.STATUS_AI);
        c.setLastMessage(shorten(content));
        c.setLastMessageAt(LocalDateTime.now());
        c.setUnreadForAdmin((c.getUnreadForAdmin() == null ? 0 : c.getUnreadForAdmin()) + 1);
        conversationRepository.save(c);

        // 明确要求转人工：直接挂起 AI，等后台客服接手
        if (wantsHuman(content)) {
            c.setStatus(CsConversation.STATUS_HUMAN);
            conversationRepository.save(c);
            CsMessage handoff = saveAiMessage(c.getId(), HANDOFF_TEXT);
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("conversationId", c.getId());
            resp.put("conversationStatus", c.getStatus());
            resp.put("userMessage", toMessageMap(userMsg));
            resp.put("reply", toMessageMap(handoff));
            return resp;
        }

        CsMessage aiMsg;
        try {
            String answer = askAiWithRetry(c, content);
            aiMsg = saveAiMessage(c.getId(), answer);
        } catch (Exception e) {
            // 重试后仍不可用才降级为「已转人工」，让后台客服接手，而不是把报错甩给客户
            log.warn("AI 客服回复失败 conversationId={}，已转人工", c.getId(), e);
            c.setStatus(CsConversation.STATUS_HUMAN);
            conversationRepository.save(c);
            aiMsg = saveAiMessage(c.getId(), FALLBACK_TEXT);
        }

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("conversationId", c.getId());
        resp.put("conversationStatus", c.getStatus());
        resp.put("userMessage", toMessageMap(userMsg));
        resp.put("reply", toMessageMap(aiMsg));
        return resp;
    }

    /** 客户主动结束会话。 */
    @Transactional
    public void closeByUser(Long userId, Long conversationId) {
        CsConversation c = requireOwned(userId, conversationId);
        c.setStatus(CsConversation.STATUS_CLOSED);
        conversationRepository.save(c);
    }

    // ==================== 后台（客服侧） ====================

    /** 后台会话列表，按最近消息倒序，带未读数。 */
    public List<Map<String, Object>> adminList() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (CsConversation c : conversationRepository.findAllByOrderByLastMessageAtDescIdDesc()) {
            out.add(toConversationMap(c, true, null));
        }
        return out;
    }

    /**
     * 后台查看某会话的全部消息（时间正序）。
     * <p>
     * 副作用：顺手把 {@code unreadForAdmin} 清零 —— 客服打开会话即视为已读，
     * 这样侧边栏红点能自动消失。
     */
    @Transactional
    public List<Map<String, Object>> adminMessages(Long conversationId) {
        CsConversation c = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new BusinessException(404, "会话不存在"));
        if (c.getUnreadForAdmin() != null && c.getUnreadForAdmin() > 0) {
            c.setUnreadForAdmin(0);
            conversationRepository.save(c);
        }
        return toMessageMaps(messageRepository.findByConversationIdOrderByIdAsc(c.getId()));
    }

    /** 后台客服（人工）在会话内回复客户。 */
    @Transactional
    public Map<String, Object> adminReply(Long operatorId, String operatorName,
                                          Long conversationId, String rawContent) {
        String content = normalize(rawContent);
        CsConversation c = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new BusinessException(404, "会话不存在"));

        CsMessage msg = new CsMessage();
        msg.setConversationId(c.getId());
        msg.setSender(CsMessage.SENDER_AGENT);
        msg.setSenderName((operatorName == null || operatorName.isBlank()) ? "人工客服" : operatorName);
        msg.setContent(content);
        messageRepository.save(msg);

        // 人工介入后，会话交给人工继续跟进
        c.setStatus(CsConversation.STATUS_HUMAN);
        c.setLastMessage(shorten(content));
        c.setLastMessageAt(LocalDateTime.now());
        conversationRepository.save(c);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("conversationId", c.getId());
        resp.put("conversationStatus", c.getStatus());
        resp.put("message", toMessageMap(msg));
        return resp;
    }

    /** 后台把会话交还给 AI 自动应答。 */
    @Transactional
    public Map<String, Object> adminHandBackToAi(Long conversationId) {
        CsConversation c = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new BusinessException(404, "会话不存在"));
        c.setStatus(CsConversation.STATUS_AI);
        conversationRepository.save(c);
        return toConversationMap(c, true, null);
    }

    /** 后台总览：会话数 / 待人工数 / 未读总数。 */
    public Map<String, Object> adminStats() {
        List<CsConversation> all = conversationRepository.findAll();
        int human = 0, unread = 0;
        for (CsConversation c : all) {
            if (CsConversation.STATUS_HUMAN.equals(c.getStatus())) human++;
            unread += (c.getUnreadForAdmin() == null ? 0 : c.getUnreadForAdmin());
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("total", all.size());
        m.put("humanPending", human);
        m.put("unread", unread);
        return m;
    }

    // ==================== LLM ====================

    /**
     * 调模型生成回复，失败重试一次再放弃。
     * <p>
     * 之所以要重试：DeepSeek 侧偶发 {@code Connection reset}（实测出现过一次，
     * 属于连接复用时的瞬断）。若不重试，一次网络抖动就会把会话永久置为「人工跟进」，
     * 客户侧看到的是「智能客服暂时无法响应」，很像功能坏了。
     * 重试后仍失败才降级 —— 这样才能把「服务真不可用」和「偶发瞬断」区分开。
     */
    private String askAiWithRetry(CsConversation c, String content) throws Exception {
        Exception last = null;
        for (int attempt = 1; attempt <= AI_MAX_ATTEMPTS; attempt++) {
            try {
                return askAi(c, content);
            } catch (Exception e) {
                last = e;
                log.warn("AI 客服第 {}/{} 次调用失败 conversationId={}：{}",
                        attempt, AI_MAX_ATTEMPTS, c.getId(), e.getMessage());
                if (attempt < AI_MAX_ATTEMPTS) {
                    try {
                        Thread.sleep(AI_RETRY_BACKOFF_MS);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        throw last == null ? new IllegalStateException("AI 调用失败") : last;
    }

    /**
     * 调用大模型生成客服自动回复。
     * <p>
     * 上下文 = 客服人设提示词（{@code AiPrompts.CS_AGENT}，含今日日期与客户名）
     * + 本会话最近 {@value #HISTORY_LIMIT} 条消息（以文本形式通过 {@code {{history}}} 注入）。
     * <p>
     * 只把「客户 / AI / 人工客服」三类消息按 user/assistant 语义映射：
     * 客户消息写成「客户：…」，AI 与人工客服的消息都写成「客服：…」，
     * 这样 AI 接手人工会话时风格能延续。
     */
    private String askAi(CsConversation c, String latestContent) throws Exception {
        String today = LocalDate.now().format(DATE_FMT);
        String customer = (c.getUserName() == null || c.getUserName().isBlank()) ? "顾客" : c.getUserName();

        String answer = csAgent.reply(today, customer, buildHistoryText(c), latestContent);
        if (answer == null || answer.isBlank()) {
            throw new IllegalStateException("模型返回空内容");
        }
        return answer.length() > MAX_CONTENT ? answer.substring(0, MAX_CONTENT) : answer;
    }

    /**
     * 把本会话已有的消息渲染成一段纯文本对话记录。
     * <p>
     * 末条恒为「刚写入的这条客户消息」，它由 AI 服务的 {@code @UserMessage} 参数补位，
     * 因此这里刻意排除，避免同一句话被注入两次。
     */
    private String buildHistoryText(CsConversation c) {
        List<CsMessage> recent = messageRepository.findTop20ByConversationIdOrderByIdDesc(c.getId());
        // 仓储按 id 倒序返回，这里倒回时间正序
        List<CsMessage> asc = new ArrayList<>(recent);
        java.util.Collections.reverse(asc);

        int end = asc.isEmpty() ? 0 : asc.size() - 1;
        int from = Math.max(0, end - HISTORY_LIMIT);

        StringBuilder sb = new StringBuilder();
        for (int i = from; i < end; i++) {
            CsMessage m = asc.get(i);
            if (m.getContent() == null || m.getContent().isBlank()) continue;
            sb.append(m.isFromCustomer() ? "客户：" : "客服：")
                    .append(m.getContent().trim())
                    .append("\n");
        }
        return sb.isEmpty() ? "（本次对话刚刚开始，暂无历史消息）" : sb.toString();
    }

    // ==================== 内部工具 ====================

    private CsConversation requireOwned(Long userId, Long conversationId) {
        if (userId == null) throw new BusinessException(401, "未登录");
        CsConversation c = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new BusinessException(404, "会话不存在"));
        if (!c.getUserId().equals(userId)) {
            throw new BusinessException(403, "无权访问他人的会话");
        }
        return c;
    }

    private CsMessage saveAiMessage(Long conversationId, String content) {
        CsMessage msg = new CsMessage();
        msg.setConversationId(conversationId);
        msg.setSender(CsMessage.SENDER_AI);
        msg.setSenderName("AI 客服");
        msg.setContent(content);
        return messageRepository.save(msg);
    }

    private String displayNameOf(Long userId) {
        return userRepository.findById(userId)
                .map(u -> (u.getNickname() != null && !u.getNickname().isBlank())
                        ? u.getNickname() : u.getUsername())
                .orElse("顾客");
    }

    private String normalize(String raw) {
        if (raw == null || raw.isBlank()) throw new BusinessException(400, "消息内容不能为空");
        String s = raw.trim();
        return s.length() > MAX_CONTENT ? s.substring(0, MAX_CONTENT) : s;
    }

    private boolean wantsHuman(String content) {
        for (String k : HUMAN_KEYWORDS) {
            if (content.contains(k)) return true;
        }
        return false;
    }

    private String shorten(String s) {
        if (s == null) return null;
        String one = s.replace('\n', ' ').trim();
        return one.length() > 200 ? one.substring(0, 200) : one;
    }

    private Map<String, Object> toConversationMap(CsConversation c, boolean forAdmin, Long viewerId) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.getId());
        m.put("userId", c.getUserId());
        m.put("userName", c.getUserName());
        m.put("status", c.getStatus());
        m.put("statusLabel", statusLabel(c.getStatus()));
        m.put("lastMessage", c.getLastMessage());
        m.put("lastMessageAt", fmt(c.getLastMessageAt()));
        m.put("createdAt", fmt(c.getCreatedAt()));
        m.put("messageCount", messageRepository.countByConversationId(c.getId()));
        if (forAdmin) {
            m.put("unread", c.getUnreadForAdmin() == null ? 0 : c.getUnreadForAdmin());
        }
        return m;
    }

    private List<Map<String, Object>> toMessageMaps(List<CsMessage> list) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (CsMessage msg : list) out.add(toMessageMap(msg));
        return out;
    }

    private Map<String, Object> toMessageMap(CsMessage msg) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", msg.getId());
        m.put("conversationId", msg.getConversationId());
        m.put("sender", msg.getSender());
        m.put("senderName", msg.getSenderName());
        m.put("content", msg.getContent());
        m.put("createdAt", fmt(msg.getCreatedAt()));
        m.put("fromCustomer", msg.isFromCustomer());
        return m;
    }

    private static String statusLabel(String status) {
        if (CsConversation.STATUS_HUMAN.equals(status)) return "人工跟进";
        if (CsConversation.STATUS_CLOSED.equals(status)) return "已结束";
        return "AI 应答";
    }

    private static String fmt(LocalDateTime t) {
        return t == null ? null : t.format(TIME_FMT);
    }

    /** AI 失败时的兜底话术。 */
    private static final String FALLBACK_TEXT =
            "抱歉，智能客服暂时无法响应，已为您转接人工客服，客服会尽快回复您，请稍候～";

    /** 客户要求转人工时的即时话术。 */
    private static final String HANDOFF_TEXT =
            "好的，正在为您转接人工客服，客服会尽快回复您，请稍候～";
}
