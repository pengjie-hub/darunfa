package com.example.backend.ai.service;

import com.example.backend.ai.assistant.MarketingAgent;
import com.example.backend.common.BusinessException;
import com.example.backend.dto.MarketingCopyRequest;
import com.example.backend.dto.MarketingCopyResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 后台「营销文案生成」编排服务。
 *
 * <h3>它负责什么</h3>
 * 框架（LangChain4j）只负责「把提示词与上下文交给模型、把文本取回」。本类在其上补三件框架不该管的事：
 * <ol>
 *   <li><b>输入校验与归一</b>：非法场景/渠道落到默认值（记 tips 而非报错），原价不高于现价时忽略原价；</li>
 *   <li><b>容错解析</b>：复用 {@code ReviewAiService} 的思路（正则截取 JSON 片段 + KV 解析 + 数组/字符串双形态），
 *       扛住 ```json 围栏、前后多余文字、字段缺失、中文引号、数组被写成单字符串等情况；</li>
 *   <li><b>归一与夹取 + 违禁词二次过滤</b>：按渠道夹取长度、卖点去空去重限 3~6 条、
 *       绝对化用语替换为合规表达（不整单失败）、CTA/标题兜底。</li>
 * </ol>
 *
 * <h3>为什么服务端要再夹一次长度</h3>
 * 提示词里写了长度要求，但模型并不总是遵守。渠道相关的长度约束属于「硬契约」，
 * 必须在服务端再夹一次，避免前端拿到超长字段。
 */
@Service
public class MarketingCopyService {

    private static final Logger log = LoggerFactory.getLogger(MarketingCopyService.class);

    // =====================================================================
    //  场景 / 渠道 / 促销类型字典
    // =====================================================================

    public static final String SCENE_PRODUCT = "PRODUCT";
    public static final String SCENE_PROMOTION = "PROMOTION";

    public static final String CHANNEL_DETAIL = "DETAIL";
    public static final String CHANNEL_HOME_CARD = "HOME_CARD";
    public static final String CHANNEL_PUSH = "PUSH";
    public static final String CHANNEL_SOCIAL = "SOCIAL";

    public static final String PROMO_FULL_REDUCTION = "FULL_REDUCTION";
    public static final String PROMO_DISCOUNT = "DISCOUNT";

    private static final Map<String, String> SCENE_LABELS = new LinkedHashMap<>();
    private static final Map<String, String> CHANNEL_LABELS = new LinkedHashMap<>();
    private static final Map<String, String> PROMO_TYPE_LABELS = new LinkedHashMap<>();

    static {
        SCENE_LABELS.put(SCENE_PRODUCT, "商品文案");
        SCENE_LABELS.put(SCENE_PROMOTION, "活动文案");

        CHANNEL_LABELS.put(CHANNEL_DETAIL, "商品详情页");
        CHANNEL_LABELS.put(CHANNEL_HOME_CARD, "首页卡片");
        CHANNEL_LABELS.put(CHANNEL_PUSH, "短信推送");
        CHANNEL_LABELS.put(CHANNEL_SOCIAL, "社群/朋友圈");

        PROMO_TYPE_LABELS.put(PROMO_FULL_REDUCTION, "满减");
        PROMO_TYPE_LABELS.put(PROMO_DISCOUNT, "折扣");
    }

    // =====================================================================
    //  长度约束（服务端硬夹取）
    // =====================================================================

    private static final int TITLE_MAX = 30;
    private static final int SUBTITLE_MAX = 40;
    private static final int SUBTITLE_MAX_HOME_CARD = 20;
    private static final int SELLING_POINT_MAX = 20;
    private static final int SELLING_POINTS_MIN = 3;
    private static final int SELLING_POINTS_MAX = 6;
    private static final int CTA_MAX = 20;

    private static final int NAME_MAX = 100;
    private static final int FREE_TEXT_MAX = 500;

    // =====================================================================
    //  违禁词（《广告法》绝对化用语）→ 合规表达
    //  顺序敏感：长词在前，保证「最好」先于「最」被替换。
    // =====================================================================

    private static final String[][] BANNED_RULES = {
            {"史上最低", "优惠"},
            {"全网最低", "价格实惠"},
            {"绝无仅有", "比较少见"},
            {"无与伦比", "很不错"},
            {"国家级", "高标准"},
            {"100%", "很高比例"},
            {"最好", "很合适"},
            {"最佳", "很合适"},
            {"最强", "很有优势"},
            {"最优", "很合适"},
            {"最低", "很实惠"},
            {"最高", "很高"},
            {"最新", "较新"},
            {"最大", "很大"},
            {"顶级", "优质"},
            {"首个", "较早"},
            {"首选", "值得考虑"},
            {"唯一", "少有"},
            {"第一", "处在前列"},
            {"一流", "不错"},
            {"领先", "较有优势"},
            {"独家", "特色"},
            {"王牌", "优质"},
            {"冠军", "优秀"},
            {"万能", "很实用"},
            {"最", "很"},
    };

    // =====================================================================
    //  容错解析用的正则（思路来自 ReviewAiService）
    // =====================================================================

    private static final Pattern JSON_OBJECT = Pattern.compile("\\{[\\s\\S]*?\\}");
    private static final Pattern KV_PAIR = Pattern.compile("\"([^\"]+)\"\\s*:\\s*\"((?:\\\\\"|[^\"])*)\"");
    private static final Pattern SP_ARRAY = Pattern.compile("\"sellingPoints\"\\s*:\\s*\\[([\\s\\S]*?)\\]");
    /** 数组被截断（没有收尾 {@code ]}）时的抢救用：一直取到串尾。 */
    private static final Pattern SP_ARRAY_OPEN = Pattern.compile("\"sellingPoints\"\\s*:\\s*\\[([\\s\\S]*)$");
    private static final Pattern ARRAY_ITEM = Pattern.compile("\"((?:\\\\\"|[^\"])*)\"");
    private static final Pattern POINT_SPLIT = Pattern.compile("[,，、;；|/\\n]+");

    /** 截断重试时追加的精简要求（附加在用户消息末尾）。 */
    private static final String RETRY_CONCISE_HINT =
            "\n【重要】上一次输出因过长被截断，请务必精简：正文严格控制在字数上限内，"
                    + "JSON 紧凑输出（不要缩进换行），确保整个 JSON 完整闭合、字段齐全。";

    private final MarketingAgent marketingAgent;

    public MarketingCopyService(MarketingAgent marketingAgent) {
        this.marketingAgent = marketingAgent;
    }

    // =====================================================================
    //  主流程
    // =====================================================================

    /**
     * 生成营销文案。
     *
     * @param req 请求体（可为 null，将抛 400）
     * @return 归一、夹取、合规过滤后的文案
     * @throws BusinessException 400 入参问题 / 502 模型调用或解析失败
     */
    public MarketingCopyResponse generate(MarketingCopyRequest req) {
        long start = System.currentTimeMillis();
        if (req == null) {
            throw new BusinessException(400, "请求内容为空，请填写商品或活动信息");
        }

        List<String> tips = new ArrayList<>();
        String scene = normalizeScene(req.getScene(), tips);
        String channel = normalizeChannel(req.getChannel(), tips);

        validate(req);

        // 原价处理：非空且未高于现价 → 忽略并记 tips（不报错）
        Double effectiveOriginalPrice = resolveOriginalPrice(req.getPrice(), req.getOriginalPrice(), tips);

        // 调用模型（保持 String 契约不变）
        String userMessage = buildUserMessage(req, scene, channel, effectiveOriginalPrice);
        String raw;
        try {
            raw = marketingAgent.generate(userMessage);
        } catch (Exception e) {
            // 原始异常与堆栈只进日志，对外统一 502 人话
            log.error("AI 营销文案生成失败：scene={} channel={}", scene, channel, e);
            throw new BusinessException(502, "AI 文案生成失败，请稍后重试");
        }

        // 截断兜底：我们的 JSON 是扁平结构、唯一的右花括号就是收尾；若输出里连 '}' 都没有，
        // 说明撞到 max-tokens 上限、JSON 被从中间截断，此时重试一次（打散随机性 + 追加精简要求）。
        if (looksIncomplete(raw)) {
            log.warn("[营销文案] 输出疑似被截断（未见收尾花括号），自动重试一次：scene={} channel={}", scene, channel);
            String retry = tryCall(userMessage + RETRY_CONCISE_HINT, scene, channel);
            if (notBlank(retry) && (!looksIncomplete(retry) || isBlank(raw) || retry.length() > raw.length())) {
                raw = retry;
            }
        }

        // 容错解析（含截断抢救）
        Parsed parsed = parse(raw);
        if (isBlank(parsed.body)) {
            String salvaged = salvageBody(raw);
            if (notBlank(salvaged)) {
                parsed.body = salvaged;
                log.warn("[营销文案] parse 未取到正文，已从截断输出中抢救正文（{} 字）", salvaged.length());
            }
        }
        if (isBlank(parsed.body)) {
            log.warn("AI 营销文案解析后正文为空，原始输出前 200 字：{}", safe(raw));
            throw new BusinessException(502, "AI 文案生成失败，请稍后重试");
        }

        // 字段兜底
        String title = firstNonBlank(parsed.title, req.getName());
        if (isBlank(title)) {
            title = SCENE_PROMOTION.equals(scene) ? "活动推荐" : "商品推荐";
        }
        String subtitle = nvl(parsed.subtitle);
        String body = nvl(parsed.body);
        String cta = firstNonBlank(parsed.cta, defaultCta(scene));

        // 违禁词二次过滤（命中即替换 + 记 tips，不整单失败）
        Set<String> bannedHits = new LinkedHashSet<>();
        title = sanitize(title, bannedHits);
        subtitle = sanitize(subtitle, bannedHits);
        body = sanitize(body, bannedHits);
        cta = sanitize(cta, bannedHits);
        List<String> rawPoints = new ArrayList<>();
        for (String s : parsed.sellingPoints) {
            rawPoints.add(sanitize(s, bannedHits));
        }
        for (String hit : bannedHits) {
            tips.add("已过滤绝对化用语：" + hit);
        }

        // 卖点归一 + 长度夹取
        List<String> sellingPoints = normalizeSellingPoints(rawPoints);
        title = truncate(title, TITLE_MAX);
        subtitle = truncate(subtitle, subtitleLimit(channel));
        cta = truncate(cta, CTA_MAX);
        Integer bodyLimit = bodyLimit(channel);
        if (bodyLimit != null) {
            body = truncate(body, bodyLimit);
        }

        MarketingCopyResponse resp = new MarketingCopyResponse();
        resp.setScene(scene);
        resp.setChannel(channel);
        resp.setTitle(title);
        resp.setSubtitle(subtitle);
        resp.setSellingPoints(sellingPoints);
        resp.setBody(body);
        resp.setCta(cta);
        resp.setTips(tips);
        resp.setElapsedMs(System.currentTimeMillis() - start);
        log.info("[营销文案] scene={} channel={} elapsed={}ms tips={}", scene, channel, resp.getElapsedMs(), tips);
        return resp;
    }

    /** 场景 / 渠道字典（供后台前端下拉，避免硬编码）。 */
    public Map<String, Object> dictionaries() {
        List<Map<String, String>> scenes = new ArrayList<>();
        scenes.add(dict(SCENE_PRODUCT, SCENE_LABELS.get(SCENE_PRODUCT)));
        scenes.add(dict(SCENE_PROMOTION, SCENE_LABELS.get(SCENE_PROMOTION)));

        List<Map<String, String>> channels = new ArrayList<>();
        channels.add(dict(CHANNEL_DETAIL, CHANNEL_LABELS.get(CHANNEL_DETAIL)));
        channels.add(dict(CHANNEL_HOME_CARD, CHANNEL_LABELS.get(CHANNEL_HOME_CARD)));
        channels.add(dict(CHANNEL_PUSH, CHANNEL_LABELS.get(CHANNEL_PUSH)));
        channels.add(dict(CHANNEL_SOCIAL, CHANNEL_LABELS.get(CHANNEL_SOCIAL)));

        List<Map<String, String>> promoTypes = new ArrayList<>();
        promoTypes.add(dict(PROMO_FULL_REDUCTION, PROMO_TYPE_LABELS.get(PROMO_FULL_REDUCTION)));
        promoTypes.add(dict(PROMO_DISCOUNT, PROMO_TYPE_LABELS.get(PROMO_DISCOUNT)));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("scenes", scenes);
        out.put("channels", channels);
        out.put("promoTypes", promoTypes);
        return out;
    }

    // =====================================================================
    //  校验 / 归一
    // =====================================================================

    private static void validate(MarketingCopyRequest req) {
        if (isBlank(req.getName()) && isBlank(req.getExtra()) && isBlank(req.getExisting())) {
            throw new BusinessException(400, "请至少填写名称、补充说明，或需要改写的内容");
        }
        if (req.getName() != null && req.getName().trim().length() > NAME_MAX) {
            throw new BusinessException(400, "商品/活动名称过长（最多 100 字），请精简后重试");
        }
        if (req.getExtra() != null && req.getExtra().trim().length() > FREE_TEXT_MAX) {
            throw new BusinessException(400, "补充说明过长（最多 500 字），请精简后重试");
        }
        if (req.getExisting() != null && req.getExisting().trim().length() > FREE_TEXT_MAX) {
            throw new BusinessException(400, "已有文案过长（最多 500 字），请精简后重试");
        }
        if (req.getPrice() != null && req.getPrice() < 0) {
            throw new BusinessException(400, "现价不能为负数");
        }
        if (req.getOriginalPrice() != null && req.getOriginalPrice() < 0) {
            throw new BusinessException(400, "原价不能为负数");
        }
    }

    private static String normalizeScene(String scene, List<String> tips) {
        if (scene == null || scene.isBlank()) {
            return SCENE_PRODUCT;
        }
        String s = scene.trim().toUpperCase();
        if (SCENE_LABELS.containsKey(s)) {
            return s;
        }
        tips.add("场景值「" + scene.trim() + "」无效，已按默认「商品文案」处理");
        return SCENE_PRODUCT;
    }

    private static String normalizeChannel(String channel, List<String> tips) {
        if (channel == null || channel.isBlank()) {
            return CHANNEL_DETAIL;
        }
        String c = channel.trim().toUpperCase();
        if (CHANNEL_LABELS.containsKey(c)) {
            return c;
        }
        tips.add("渠道值「" + channel.trim() + "」无效，已按默认「商品详情页」处理");
        return CHANNEL_DETAIL;
    }

    private static String normalizePromoType(String promoType) {
        if (promoType == null || promoType.isBlank()) {
            return null;
        }
        String p = promoType.trim().toUpperCase();
        return PROMO_TYPE_LABELS.containsKey(p) ? p : null;
    }

    /**
     * 原价归一：非空且未高于现价（或缺少现价无法比较）时忽略原价并记 tips。
     *
     * @return 生效的原价（被忽略时为 null）
     */
    private static Double resolveOriginalPrice(Double price, Double originalPrice, List<String> tips) {
        if (originalPrice == null) {
            return null;
        }
        if (price == null) {
            tips.add("缺少现价，已忽略原价信息");
            return null;
        }
        if (originalPrice <= price) {
            tips.add("原价未高于现价，已忽略原价信息");
            return null;
        }
        return originalPrice;
    }

    private static String defaultCta(String scene) {
        return SCENE_PROMOTION.equals(scene) ? "立即参与" : "立即购买";
    }

    // =====================================================================
    //  用户消息拼装
    // =====================================================================

    private static String buildUserMessage(MarketingCopyRequest req, String scene, String channel, Double originalPrice) {
        StringBuilder sb = new StringBuilder();
        sb.append("请为以下").append(SCENE_LABELS.get(scene))
                .append("生成一段投放于「").append(CHANNEL_LABELS.get(channel)).append("」的文案。\n");
        sb.append("【场景】").append(SCENE_LABELS.get(scene)).append("（").append(scene).append("）\n");
        sb.append("【渠道】").append(CHANNEL_LABELS.get(channel)).append("（").append(channel).append("）\n");
        if (notBlank(req.getName())) sb.append("【名称】").append(req.getName().trim()).append("\n");
        if (notBlank(req.getCategory())) sb.append("【分类】").append(req.getCategory().trim()).append("\n");
        if (req.getPrice() != null) {
            sb.append("【现价】").append(fmtNumber(req.getPrice())).append("元");
            if (notBlank(req.getUnit())) sb.append(" / ").append(req.getUnit().trim());
            sb.append("\n");
        }
        if (originalPrice != null) sb.append("【原价】").append(fmtNumber(originalPrice)).append("元\n");
        if (notBlank(req.getUnit())) sb.append("【规格/单位】").append(req.getUnit().trim()).append("\n");
        if (notBlank(req.getKeywords())) sb.append("【关键词/卖点】").append(req.getKeywords().trim()).append("\n");
        if (notBlank(req.getAudience())) sb.append("【目标人群】").append(req.getAudience().trim()).append("\n");

        if (SCENE_PROMOTION.equals(scene)) {
            String promoType = normalizePromoType(req.getPromoType());
            if (promoType != null) {
                sb.append("【促销类型】").append(PROMO_TYPE_LABELS.get(promoType)).append("\n");
                if (req.getThreshold() != null && req.getThreshold() > 0) {
                    sb.append("【门槛金额】满 ").append(fmtNumber(req.getThreshold())).append(" 元\n");
                }
                if (req.getDiscount() != null && req.getDiscount() > 0) {
                    if (PROMO_DISCOUNT.equals(promoType)) {
                        sb.append("【折扣率】").append(fmtNumber(req.getDiscount())).append("（例如 0.9 表示 9 折）\n");
                    } else {
                        sb.append("【减免金额】").append(fmtNumber(req.getDiscount())).append(" 元\n");
                    }
                }
            }
        }

        if (notBlank(req.getExtra())) {
            sb.append("【补充说明】").append(req.getExtra().trim()).append("\n");
        }
        if (notBlank(req.getExisting())) {
            sb.append("【已有文案（请在此基础上润色改写，保留原意与关键信息）】\n")
                    .append(req.getExisting().trim()).append("\n");
            if (isBlank(req.getName()) && isBlank(req.getExtra())) {
                // 纯改写：除「已有文案」外没有任何其他信息，必须约束模型不得凭空补事实
                sb.append("【纯改写任务】除以上「已有文案」外没有任何其他信息：请只优化表达与合规性，")
                        .append("严禁补充原文中未出现的产地、规格、认证、数字、销量或功效等任何事实。\n");
            }
        }
        sb.append("\n请严格按要求输出 JSON。");
        return sb.toString();
    }

    // =====================================================================
    //  容错解析
    // =====================================================================

    /** 模型输出解析结果载体（scalar 字段 + 卖点列表）。 */
    private static final class Parsed {
        String title;
        String subtitle;
        String body;
        String cta;
        List<String> sellingPoints = new ArrayList<>();
    }

    private static Parsed parse(String raw) {
        Parsed p = new Parsed();
        if (raw == null) {
            return p;
        }
        Map<String, String> kv = parseScalars(raw);
        p.title = kv.get("title");
        p.subtitle = kv.get("subtitle");
        p.body = kv.get("body");
        p.cta = kv.get("cta");
        p.sellingPoints = parseSellingPoints(raw, kv);

        // 中文引号兜底：当直引号解析拿不到正文时，把中文引号规整为直引号再试一次
        if (isBlank(p.body) && (raw.indexOf('\u201c') >= 0 || raw.indexOf('\u201d') >= 0)) {
            String normalized = raw.replace('\u201c', '"').replace('\u201d', '"');
            Map<String, String> kv2 = parseScalars(normalized);
            p.title = firstNonBlank(kv2.get("title"), p.title);
            p.subtitle = firstNonBlank(kv2.get("subtitle"), p.subtitle);
            p.body = firstNonBlank(kv2.get("body"), p.body);
            p.cta = firstNonBlank(kv2.get("cta"), p.cta);
            if (p.sellingPoints.isEmpty()) {
                p.sellingPoints = parseSellingPoints(normalized, kv2);
            }
        }
        return p;
    }

    /** 解析标量字段（title/subtitle/body/cta）。 */
    private static Map<String, String> parseScalars(String s) {
        Map<String, String> out = new LinkedHashMap<>();
        if (s == null) {
            return out;
        }
        String scope = scopeOf(s);
        Matcher kv = KV_PAIR.matcher(scope);
        while (kv.find()) {
            String key = kv.group(1).trim();
            String value = kv.group(2).replace("\\\"", "\"").replace("\\n", "\n");
            if (!out.containsKey(key)) {
                out.put(key, value);
            }
        }
        return out;
    }

    /**
     * 解析 sellingPoints：优先按 JSON 数组解析；若被写成单个字符串，则按分隔符拆成多条。
     * <p>注意：数组形态下 {@code "sellingPoints":[...]} 不会被 {@link #KV_PAIR} 捕获（冒号后是 {@code [}），
     * 只有字符串形态才会落在 {@code kv} 里，因此两条路径互不干扰。
     */
    private static List<String> parseSellingPoints(String raw, Map<String, String> kv) {
        List<String> out = new ArrayList<>();
        if (raw == null) {
            return out;
        }
        String scope = scopeOf(raw);
        Matcher arr = SP_ARRAY.matcher(scope);
        if (arr.find()) {
            return collectItems(arr.group(1), out);
        }
        // 数组被截断（没有收尾 ]）时的抢救：只收完整引号包裹的条目，半截的最后一条会被自动丢弃
        Matcher open = SP_ARRAY_OPEN.matcher(scope);
        if (open.find()) {
            return collectItems(open.group(1), out);
        }
        String sp = kv.get("sellingPoints");
        if (notBlank(sp)) {
            for (String part : POINT_SPLIT.split(sp)) {
                String t = part.trim();
                if (!t.isEmpty()) {
                    out.add(t);
                }
            }
        }
        return out;
    }

    private static List<String> collectItems(String body, List<String> out) {
        Matcher item = ARRAY_ITEM.matcher(body);
        while (item.find()) {
            String t = item.group(1).replace("\\\"", "\"").replace("\\n", " ").trim();
            if (!t.isEmpty()) {
                out.add(t);
            }
        }
        return out;
    }

    /** 从（可能被截断的）原始输出里抢救 body 字段：允许缺失收尾引号。 */
    private static String salvageBody(String raw) {
        if (raw == null) {
            return null;
        }
        int key = raw.indexOf("\"body\"");
        if (key < 0) {
            return null;
        }
        int colon = raw.indexOf(':', key + 6);
        if (colon < 0) {
            return null;
        }
        int quote = raw.indexOf('"', colon + 1);
        if (quote < 0) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        String rest = raw.substring(quote + 1);
        for (int i = 0; i < rest.length(); i++) {
            char c = rest.charAt(i);
            if (c == '\\' && i + 1 < rest.length()) {
                char n = rest.charAt(i + 1);
                sb.append(n == 'n' ? '\n' : n);
                i++;
                continue;
            }
            if (c == '"') {
                break; // 正常闭合
            }
            sb.append(c);
        }
        String body = sb.toString().trim();
        return body.isEmpty() ? null : body;
    }

    /**
     * 判断模型输出是否「不完整」。我们的 JSON 是扁平结构（无嵌套对象），唯一的右花括号就是收尾；
     * 因此「输出里完全没有右花括号」即可判定为被 token 上限截断。空输出同样视为不完整。
     */
    private static boolean looksIncomplete(String raw) {
        return isBlank(raw) || raw.indexOf('}') < 0;
    }

    /** 重试调用：吞掉异常并记日志，返回 null 表示重试本身失败（此时沿用首次结果）。 */
    private String tryCall(String message, String scene, String channel) {
        try {
            return marketingAgent.generate(message);
        } catch (Exception e) {
            log.warn("[营销文案] 截断重试调用失败，沿用首次输出：scene={} channel={}", scene, channel, e);
            return null;
        }
    }

    /** 截取第一个 {@code {...}} 片段；取不到则用原文。 */
    private static String scopeOf(String s) {
        Matcher m = JSON_OBJECT.matcher(s);
        return m.find() ? m.group() : s;
    }

    // =====================================================================
    //  归一 / 夹取 / 合规过滤
    // =====================================================================

    /** 卖点归一：去空 → 截断 → 去重 → 上限 6 条；少于 3 条不硬凑，按实际返回。 */
    private static List<String> normalizeSellingPoints(List<String> raw) {
        List<String> clipped = new ArrayList<>();
        if (raw != null) {
            for (String s : raw) {
                if (s == null) {
                    continue;
                }
                String t = truncate(s, SELLING_POINT_MAX);
                if (!t.isEmpty()) {
                    clipped.add(t);
                }
            }
        }
        // 去重并保持出现顺序
        LinkedHashSet<String> distinct = new LinkedHashSet<>(clipped);
        List<String> out = new ArrayList<>(distinct);
        if (out.size() > SELLING_POINTS_MAX) {
            out = new ArrayList<>(out.subList(0, SELLING_POINTS_MAX));
        }
        // SELLING_POINTS_MIN 仅作为提示词的目标下限；实际不足时不补，按条数返回
        return out;
    }

    /** 绝对化用语替换：命中即替换为合规表达，并把命中词记入 {@code hits}。 */
    private static String sanitize(String text, Set<String> hits) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        String result = text;
        for (String[] rule : BANNED_RULES) {
            if (result.contains(rule[0])) {
                result = result.replace(rule[0], rule[1]);
                hits.add(rule[0]);
            }
        }
        return result;
    }

    private static int subtitleLimit(String channel) {
        return CHANNEL_HOME_CARD.equals(channel) ? SUBTITLE_MAX_HOME_CARD : SUBTITLE_MAX;
    }

    /** 正文长度上限；{@code HOME_CARD} 等无硬性上限（提示词要求「可短」），返回 null 表示不夹取。 */
    private static Integer bodyLimit(String channel) {
        return switch (channel) {
            case CHANNEL_DETAIL -> 300;
            case CHANNEL_PUSH -> 60;
            case CHANNEL_SOCIAL -> 120;
            default -> null;
        };
    }

    /** 按字数截断（先 trim）；不切断代理对（emoji）。 */
    private static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        if (t.length() <= max) {
            return t;
        }
        int end = max;
        if (end > 0 && Character.isHighSurrogate(t.charAt(end - 1))) {
            end--;
        }
        return t.substring(0, end);
    }

    // =====================================================================
    //  小工具
    // =====================================================================

    private static Map<String, String> dict(String code, String label) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("code", code);
        m.put("label", label);
        return m;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static boolean notBlank(String s) {
        return !isBlank(s);
    }

    private static String nvl(String s) {
        return s == null ? "" : s.trim();
    }

    private static String firstNonBlank(String a, String b) {
        if (notBlank(a)) return a.trim();
        if (notBlank(b)) return b.trim();
        return null;
    }

    private static String fmtNumber(Double d) {
        if (d == null) {
            return "";
        }
        if (d == Math.rint(d) && !d.isInfinite()) {
            return String.valueOf(d.longValue());
        }
        return String.valueOf(d);
    }

    private static String safe(String s) {
        if (s == null) {
            return "";
        }
        return s.length() > 200 ? s.substring(0, 200) + "..." : s;
    }
}
