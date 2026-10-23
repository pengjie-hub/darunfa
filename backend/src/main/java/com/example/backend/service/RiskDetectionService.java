package com.example.backend.service;

import com.example.backend.ai.assistant.RiskAnalyst;
import com.example.backend.common.BusinessException;
import com.example.backend.entity.Order;
import com.example.backend.entity.OrderItem;
import com.example.backend.entity.RiskLevel;
import com.example.backend.entity.RiskScore;
import com.example.backend.repository.OrderItemRepository;
import com.example.backend.repository.OrderRepository;
import com.example.backend.repository.RiskScoreRepository;
import com.example.backend.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 订单风险检测服务（方案 #4）。
 *
 * <h3>设计</h3>
 * <ul>
 *   <li><b>规则引擎</b>：纯本地计算，无外部依赖。包括：
 *     <ul>
 *       <li>金额异常（相对该用户历史订单 P95）</li>
 *       <li>短时多单（最近 1 小时内同用户订单数 ≥ 3）</li>
 *       <li>地址敏感词（"快递柜/代收/虚拟号/中转站" 等疑似代收/异常地址）</li>
 *       <li>首单大额（账户注册 7 天内首单金额 > 500）</li>
 *       <li>凌晨下单（00:00 - 05:00）</li>
 *       <li>支付金额为 0（疑似刷单或测试）</li>
 *     </ul>
 *   </li>
 *   <li><b>大模型综合评分</b>：把规则 signals + 用户历史 + 收货地址喂给 LLM（{@link RiskAnalyst}，
 *       LangChain4j 声明式 AI 服务），让模型给出最终的 0-100 分 + 综合理由 + 处置建议（JSON 格式）。</li>
 *   <li><b>降级策略</b>：LLM 调用失败（超时 / 限流 / API Key 缺失）时退回规则引擎结果，
 *       保证核心风控不因 AI 故障而瘫痪。</li>
 *   <li><b>异步触发</b>：下单后调用 {@link #scanOrderAsync(Long)}，不阻塞下单主流程。</li>
 * </ul>
 *
 * <h3>输出</h3>
 * 写入 {@link RiskScore} 表，同时回写 {@link Order#riskLevel/riskScore/riskScannedAt} 冗余字段
 * 便于后台列表快速筛选（无需 JOIN）。
 */
@Service
public class RiskDetectionService {

    private static final Logger log = LoggerFactory.getLogger(RiskDetectionService.class);

    // ---------- 阈值常量（按业务可调） ----------
    /** 1 小时内同一用户订单数 ≥ 此值 → 视为短时多单（疑似刷单） */
    private static final int BURST_ORDER_THRESHOLD = 3;
    /** 首单大额阈值（用户最近 7 天内有订单则视为"非首单"） */
    private static final BigDecimal FIRST_ORDER_AMOUNT_LIMIT = new BigDecimal("500");
    /** 凌晨时段（小时） */
    private static final int LATE_NIGHT_START = 0;
    private static final int LATE_NIGHT_END = 5;
    /** 收货地址敏感关键词 */
    private static final String[] ADDRESS_SUSPICIOUS_KEYWORDS = {
            "快递柜", "代收点", "代收", "虚拟号", "中转站", "菜鸟驿站", "丰巢"
    };

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final UserRepository userRepository;
    private final RiskScoreRepository riskScoreRepository;
    /** LangChain4j 声明式风控分析服务（改造前是手写的 DeepSeekClient）。 */
    private final RiskAnalyst riskAnalyst;
    private final ObjectMapper objectMapper;

    @Autowired
    public RiskDetectionService(OrderRepository orderRepository,
                                OrderItemRepository orderItemRepository,
                                UserRepository userRepository,
                                RiskScoreRepository riskScoreRepository,
                                RiskAnalyst riskAnalyst) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.userRepository = userRepository;
        this.riskScoreRepository = riskScoreRepository;
        this.riskAnalyst = riskAnalyst;
        this.objectMapper = new ObjectMapper();
    }

    // ==================== 对外接口 ====================

    /**
     * 异步扫描单订单（订单创建时调用）。
     * 任何异常都会被捕获并 log，绝不抛出以免影响主流程。
     */
    @Async
    public void scanOrderAsync(Long orderId) {
        try {
            scan(orderId);
        } catch (Exception e) {
            log.warn("异步风险扫描失败 orderId={} err={}", orderId, e.getMessage());
        }
    }

    /**
     * 同步扫描单订单：先跑规则，再调 LLM 综合评分，失败时退回规则结果。
     *
     * @return 最新一条评分记录
     */
    @Transactional
    public RiskScore scan(Long orderId) {
        Optional<Order> opt = orderRepository.findById(orderId);
        if (opt.isEmpty()) {
            // 原来是 IllegalArgumentException → 冒到全局处理器变成 HTTP 500「服务器内部错误」。
            // 「这单不存在」是用户可以自己修正的输入错误，不是系统故障，因此改抛业务异常：
            // 全局处理器会返回 200 + {code:404, subCode:TARGET_NOT_FOUND, message:...}，
            // 与后台其他接口的 404 口径一致（对应验收 ⑯：不再是 500）。
            throw new BusinessException(404, "TARGET_NOT_FOUND", "订单不存在: " + orderId);
        }
        Order order = opt.get();

        // 1) 规则引擎
        RuleSignals rule = runRules(order);

        // 2) LLM 综合（失败降级）
        Assessment llm;
        try {
            llm = callLlm(order, rule);
        } catch (Exception e) {
            log.warn("LLM 风险评分失败，降级为规则结果 orderId={} err={}", orderId, e.getMessage());
            llm = null;
        }

        // 3) 合并
        RiskScore score = mergeToRiskScore(orderId, rule, llm);

        // 4) 写库
        RiskScore saved = riskScoreRepository.save(score);

        // 5) 回写订单冗余字段
        order.setRiskLevel(saved.getLevel());
        order.setRiskScore(saved.getScore());
        order.setRiskScannedAt(saved.getCreatedAt());
        orderRepository.save(order);

        return saved;
    }

    /**
     * 批量扫描：取最近 N 个订单，对其中未评分的进行扫描。
     *
     * @param limit 最多扫描多少条
     * @return 实际扫描条数
     */
    @Transactional
    public int batchScan(int limit) {
        if (limit <= 0) limit = 30;
        if (limit > 500) limit = 500;
        int n = 0;
        // 简单策略：取最近 200 条订单中 riskScannedAt 为空的
        List<Order> recent = orderRepository.findByCreatedAtAfterOrderByCreatedAtAsc(
                LocalDateTime.now().minusDays(30));
        for (Order order : recent) {
            if (n >= limit) break;
            if (order.getRiskScannedAt() != null) continue;
            try {
                scan(order.getId());
                n++;
            } catch (Exception e) {
                log.warn("批量扫描失败 orderId={} err={}", order.getId(), e.getMessage());
            }
        }
        return n;
    }

    /**
     * 概览：各级别计数 + 未读数。
     */
    public Map<String, Object> summary() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("high", riskScoreRepository.countByLevel(RiskLevel.HIGH));
        m.put("medium", riskScoreRepository.countByLevel(RiskLevel.MEDIUM));
        m.put("low", riskScoreRepository.countByLevel(RiskLevel.LOW));
        m.put("unread", riskScoreRepository.countByIsReadFalse());
        m.put("total", riskScoreRepository.count());
        return m;
    }

    /**
     * 未读集合的指纹（条数 / 最大 id / 分数和），供「一键已读」的幂等键使用。
     *
     * @param level {@code null} 表示全部未读；否则只看该等级
     */
    public RiskScoreRepository.UnreadStat unreadStat(RiskLevel level) {
        return level == null
                ? riskScoreRepository.unreadStatAll()
                : riskScoreRepository.unreadStatByLevel(level);
    }

    /**
     * 批量把未读标记置为已读。
     *
     * <p>只改 {@code is_read} 这一列：<b>不动分数、等级、建议处置，也不动订单的任何字段</b>。
     * 「已读」在业务上只是运营侧的收件箱状态，不是风控结论。
     *
     * @param level {@code null} 表示全部；否则只标该等级
     * @return 实际被标记的行数（0 表示本来就没有未读，属于正常结果而非错误）
     */
    @Transactional
    public int markAllRead(RiskLevel level) {
        return level == null
                ? riskScoreRepository.markAllRead()
                : riskScoreRepository.markAllReadByLevel(level);
    }

    // ==================== 规则引擎 ====================

    /**
     * 单条规则信号。
     */
    static class Signal {
        final String code;
        final String message;
        final int weight; // 命中加分

        Signal(String code, String message, int weight) {
            this.code = code;
            this.message = message;
            this.weight = weight;
        }

        Map<String, Object> toJson() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("code", code);
            m.put("message", message);
            m.put("weight", weight);
            return m;
        }
    }

    /** 规则引擎输出：信号列表 + 规则分（0-100）。 */
    static class RuleSignals {
        final List<Signal> signals = new ArrayList<>();
        int ruleScore = 0;

        void add(Signal s) {
            signals.add(s);
            ruleScore += s.weight;
        }
    }

    private RuleSignals runRules(Order order) {
        RuleSignals rs = new RuleSignals();

        // 规则 1：金额异常（相对该用户历史 20 单 P95）
        List<Order> history = orderRepository.findTop20ByUserIdOrderByCreatedAtDesc(order.getUserId());
        if (history.size() >= 5) {
            BigDecimal p95 = percentile(history, 0.95);
            if (p95 != null && order.getPayAmount().compareTo(p95.multiply(new BigDecimal("2"))) > 0) {
                rs.add(new Signal("AMOUNT_HIGH",
                        "支付金额 " + order.getPayAmount() + " 超过该用户历史订单 P95 (" + p95 + ") 的 2 倍", 30));
            }
        }

        // 规则 2：短时多单（最近 1 小时同用户订单数）
        long last1h = orderRepository.countByUserIdAndCreatedAtAfter(
                order.getUserId(), LocalDateTime.now().minusHours(1));
        if (last1h >= BURST_ORDER_THRESHOLD) {
            rs.add(new Signal("BURST_ORDERS",
                    "最近 1 小时内该用户已下 " + last1h + " 单", 35));
        }

        // 规则 3：地址敏感词
        String addr = order.getReceiverAddress() == null ? "" : order.getReceiverAddress();
        for (String kw : ADDRESS_SUSPICIOUS_KEYWORDS) {
            if (addr.contains(kw)) {
                rs.add(new Signal("ADDRESS_SUSPICIOUS",
                        "收货地址含敏感词「" + kw + "」（疑似代收/快递柜）", 15));
                break;
            }
        }

        // 规则 4：首单大额（账户 7 天内有订单则非首单）
        if (history.isEmpty() && order.getPayAmount().compareTo(FIRST_ORDER_AMOUNT_LIMIT) > 0) {
            rs.add(new Signal("FIRST_BIG",
                    "新账户首单金额 " + order.getPayAmount() + " > " + FIRST_ORDER_AMOUNT_LIMIT, 25));
        }

        // 规则 5：凌晨下单
        if (order.getCreatedAt() != null) {
            int h = order.getCreatedAt().getHour();
            if (h >= LATE_NIGHT_START && h < LATE_NIGHT_END) {
                rs.add(new Signal("LATE_NIGHT",
                        "下单时间 " + h + ":00 属于凌晨时段（0-5 点）", 10));
            }
        }

        // 规则 6：金额为 0 / 极低
        if (order.getPayAmount().compareTo(new BigDecimal("0.01")) < 0) {
            rs.add(new Signal("AMOUNT_ZERO",
                    "支付金额为 0 或负值，疑似刷单或测试", 30));
        }

        // 截断 0-100
        rs.ruleScore = Math.min(100, rs.ruleScore);
        return rs;
    }

    private static BigDecimal percentile(List<Order> orders, double p) {
        if (orders == null || orders.isEmpty()) return null;
        List<BigDecimal> amounts = new ArrayList<>();
        for (Order o : orders) amounts.add(o.getPayAmount());
        Collections.sort(amounts);
        int idx = (int) Math.ceil(p * amounts.size()) - 1;
        if (idx < 0) idx = 0;
        if (idx >= amounts.size()) idx = amounts.size() - 1;
        return amounts.get(idx);
    }

    // ==================== LLM 综合评分 ====================

    /** LLM 输出结构。 */
    static class Assessment {
        int score;          // 0-100
        RiskLevel level;    // LOW / MEDIUM / HIGH
        String reason;      // 综合理由（中文，1-2 句）
        String action;      // 处置建议
    }

    private Assessment callLlm(Order order, RuleSignals rule) throws Exception {
        String userName = userRepository.findById(order.getUserId())
                .map(u -> u.getUsername() + (u.getNickname() != null ? "(" + u.getNickname() + ")" : ""))
                .orElse("用户#" + order.getUserId());

        List<OrderItem> items = orderItemRepository.findByOrderId(order.getId());

        // 构造 user 消息（含订单快照 + 规则信号）
        String ruleJson = serializeRuleSignals(rule);
        String itemBrief = items.stream()
                .map(it -> "商品#" + it.getProductId() + " x" + it.getQuantity() + " ¥" + it.getPrice())
                .reduce((a, b) -> a + "；" + b).orElse("(无明细)");

        String userMsg = String.format(
                "请评估以下订单的欺诈/异常风险，并严格按 JSON 输出。\n\n" +
                        "【订单快照】\n订单号：%s，下单时间：%s，用户：%s\n" +
                        "订单金额：应付 ¥%s，实付 ¥%s，优惠 ¥%s\n" +
                        "收货人：%s，电话：%s，地址：%s\n" +
                        "商品明细：%s\n\n" +
                        "【规则引擎已识别信号】\n%s\n\n" +
                        "【任务】\n" +
                        "综合以上信息，给出 0-100 的风险评分（越大越可疑），对应等级 LOW/MEDIUM/HIGH，" +
                        "并用 1-2 句中文说明综合理由，最后给出处置建议（放行/人工审核/暂停发货）。\n\n" +
                        "【输出格式（严格 JSON，不要任何多余文字）】\n" +
                        "{\"score\": 0, \"level\": \"LOW\", \"reason\": \"...\", \"action\": \"放行\"}",
                order.getOrderNo(),
                order.getCreatedAt(),
                userName,
                order.getTotalAmount(),
                order.getPayAmount(),
                order.getDiscountAmount(),
                order.getReceiverName(),
                order.getReceiverPhone(),
                order.getReceiverAddress(),
                itemBrief,
                ruleJson
        );

        // 风控人设提示词与严格 JSON 输出要求已移到 AiPrompts.RISK_ANALYST，
        // 这里只负责把「订单快照 + 规则信号」渲染成模型能读的文本。
        return parseAssessment(riskAnalyst.assess(userMsg));
    }

    private static String serializeRuleSignals(RuleSignals rs) {
        try {
            ObjectMapper om = new ObjectMapper();
            List<Map<String, Object>> list = new ArrayList<>();
            for (Signal s : rs.signals) list.add(s.toJson());
            Map<String, Object> root = new LinkedHashMap<>();
            root.put("ruleScore", rs.ruleScore);
            root.put("signals", list);
            return om.writeValueAsString(root);
        } catch (Exception e) {
            return "{\"ruleScore\":" + rs.ruleScore + "}";
        }
    }

    /** 解析 LLM JSON 输出，失败返回 null。 */
    private Assessment parseAssessment(String raw) {
        if (raw == null) return null;
        String trimmed = raw.trim();
        // 去除 markdown ```json ... ``` 包裹
        if (trimmed.startsWith("```")) {
            int firstLineEnd = trimmed.indexOf('\n');
            if (firstLineEnd > 0) trimmed = trimmed.substring(firstLineEnd + 1);
            if (trimmed.endsWith("```")) trimmed = trimmed.substring(0, trimmed.length() - 3);
            trimmed = trimmed.trim();
        }
        try {
            JsonNode node = objectMapper.readTree(trimmed);
            Assessment a = new Assessment();
            a.score = clampScore(node.path("score").asInt(0));
            a.level = parseLevel(node.path("level").asText(""));
            a.reason = node.path("reason").asText("").trim();
            a.action = node.path("action").asText("人工审核").trim();
            if (a.reason.isEmpty()) a.reason = "AI 未给出明确理由";
            return a;
        } catch (Exception e) {
            log.warn("LLM JSON 解析失败 raw={}", trimmed);
            return null;
        }
    }

    private static int clampScore(int s) {
        if (s < 0) return 0;
        if (s > 100) return 100;
        return s;
    }

    private static RiskLevel parseLevel(String s) {
        if (s == null) return RiskLevel.LOW;
        s = s.trim().toUpperCase();
        try {
            return RiskLevel.valueOf(s);
        } catch (Exception e) {
            // 中文兜底
            if (s.contains("高")) return RiskLevel.HIGH;
            if (s.contains("中")) return RiskLevel.MEDIUM;
            return RiskLevel.LOW;
        }
    }

    // ==================== 合并 / 落库 ====================

    /**
     * 把规则结果与 LLM 结果合并成最终 RiskScore。
     * <p>合并策略：取规则分与 LLM 分的较大者（保守，宁误杀勿漏放）。
     * 若 LLM 缺失，则直接用规则结果。</p>
     */
    private RiskScore mergeToRiskScore(Long orderId, RuleSignals rule, Assessment llm) {
        int finalScore;
        RiskLevel finalLevel;
        RiskScore.Source source;
        String action;
        String reasonJson;

        if (llm != null) {
            // 综合分：max(规则, LLM)，再加成一点权重偏向 LLM（更"智能"）
            finalScore = Math.max(rule.ruleScore, llm.score);
            // 若两者差距大，向较高者再 +5
            if (Math.abs(rule.ruleScore - llm.score) >= 20) finalScore = Math.min(100, finalScore + 5);
            finalLevel = RiskLevel.values()[scoreToLevelIdx(finalScore)];
            source = RiskScore.Source.HYBRID;
            action = llm.action;
            reasonJson = buildHybridJson(rule, llm);
        } else {
            finalScore = rule.ruleScore;
            finalLevel = RiskLevel.values()[scoreToLevelIdx(finalScore)];
            source = RiskScore.Source.RULE;
            action = finalLevel == RiskLevel.HIGH ? "暂停发货"
                    : finalLevel == RiskLevel.MEDIUM ? "人工审核" : "放行";
            reasonJson = buildRuleJson(rule);
        }

        RiskScore rs = new RiskScore();
        rs.setOrderId(orderId);
        rs.setScore(finalScore);
        rs.setLevel(finalLevel);
        rs.setReasons(reasonJson);
        rs.setSuggestedAction(action);
        rs.setSource(source);
        rs.setIsRead(Boolean.FALSE);
        return rs;
    }

    private static int scoreToLevelIdx(int s) {
        if (s >= 70) return RiskLevel.HIGH.ordinal();
        if (s >= 40) return RiskLevel.MEDIUM.ordinal();
        return RiskLevel.LOW.ordinal();
    }

    private String buildHybridJson(RuleSignals rule, Assessment llm) {
        try {
            Map<String, Object> root = new LinkedHashMap<>();
            List<Map<String, Object>> sigs = new ArrayList<>();
            for (Signal s : rule.signals) sigs.add(s.toJson());
            root.put("ruleScore", rule.ruleScore);
            root.put("signals", sigs);
            root.put("llmSummary", llm.reason);
            root.put("llmAction", llm.action);
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            return "{\"ruleScore\":" + rule.ruleScore + "}";
        }
    }

    private String buildRuleJson(RuleSignals rule) {
        try {
            Map<String, Object> root = new LinkedHashMap<>();
            List<Map<String, Object>> sigs = new ArrayList<>();
            for (Signal s : rule.signals) sigs.add(s.toJson());
            root.put("ruleScore", rule.ruleScore);
            root.put("signals", sigs);
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            return "{\"ruleScore\":" + rule.ruleScore + "}";
        }
    }

    // ==================== 给前端用的便捷方法 ====================

    /**
     * 给前端：把 reasons JSON 反序列化成"可读字符串 + 列表"。
     */
    public static class ReasonView {
        public int ruleScore;
        public List<Map<String, Object>> signals = new ArrayList<>();
        public String llmSummary;
        public String llmAction;
    }

    public static ReasonView parseReasons(String reasonsJson) {
        ReasonView v = new ReasonView();
        if (reasonsJson == null || reasonsJson.isEmpty()) return v;
        try {
            ObjectMapper om = new ObjectMapper();
            JsonNode node = om.readTree(reasonsJson);
            v.ruleScore = node.path("ruleScore").asInt(0);
            JsonNode sigs = node.path("signals");
            if (sigs.isArray()) {
                for (JsonNode s : sigs) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("code", s.path("code").asText());
                    m.put("message", s.path("message").asText());
                    m.put("weight", s.path("weight").asInt());
                    v.signals.add(m);
                }
            }
            v.llmSummary = node.path("llmSummary").asText("");
            v.llmAction = node.path("llmAction").asText("");
        } catch (Exception ignore) { }
        return v;
    }
}