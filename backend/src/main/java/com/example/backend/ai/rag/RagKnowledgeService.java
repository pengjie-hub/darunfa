package com.example.backend.ai.rag;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.ContentMetadata;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 知识库的「播种 / 重建 / 观测」服务。
 *
 * <h3>知识库的生命周期</h3>
 * <pre>
 *   启动完成 → 读取 PlatformKnowledgeBase 文本
 *            → 按 "## 小节" 粗切（保证每个片段只讲一条规则，同时剥掉受众域标记）
 *            → 按 chunkSize/chunkOverlap 细切（保证片段长度可控）
 *            → 批量向量化 → 写入内存向量库
 *            → 记录片段数、受众域分布与播种时间，供管理页显示
 * </pre>
 * 向量库是内存实现，因此<b>每次重启都会重新播种</b>。这既是缺点（启动多花一两秒）
 * 也是优点（改了知识文本重启即生效，不存在「库和文档不一致」的脏状态）。
 *
 * <h3>为什么切成两级</h3>
 * 只用固定长度硬切，会把「退款要走哪两步」和「七天无理由退货」拦腰拼进同一个片段 ——
 * 检索命中后模型读到的是半条规则加上另半条无关规则，比不给资料还糟。
 * 所以先按 {@code ##} 小节切，再在<b>小节内部</b>按长度切，
 * 并给每个片段打上两个元数据：{@code section}（来自哪条规则，供管理页展示与词面加权用）
 * 与 {@code scope}（受众域，供分域检索过滤用，见 {@link RagScope}）。
 *
 * <h3>为什么不缓冲到数据库</h3>
 * 内存实现意味着重启即重建，不需要考虑「知识文档改了但向量库还是旧的」这类一致性问题。
 * 代价只在启动时付出一次，换来的是零迁移脚本、零清理任务。
 */
@Service
public class RagKnowledgeService {

    private static final Logger log = LoggerFactory.getLogger(RagKnowledgeService.class);

    /** 片段所属小节标题的元数据键。 */
    public static final String SECTION_KEY = "section";

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 二级小节的 Markdown 前缀。 */
    private static final String SECTION_PREFIX = "## ";

    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final ContentRetriever consoleContentRetriever;
    private final ContentRetriever customerContentRetriever;
    private final RagProperties ragProperties;

    /** 已入库片段数（{@code -1} 表示尚未播种）。 */
    private volatile int segmentCount = -1;

    /** 按受众域统计的片段数（顾客侧 / 运营侧），供管理页展示分域是否生效。 */
    private volatile Map<String, Integer> segmentCountByScope = Map.of();

    /** 最近一次播种完成时间。 */
    private volatile String loadedAt = "";

    public RagKnowledgeService(EmbeddingModel aiEmbeddingModel,
                               EmbeddingStore<TextSegment> aiEmbeddingStore,
                               @Qualifier(RagConfig.CONSOLE_RETRIEVER) ContentRetriever aiConsoleContentRetriever,
                               @Qualifier(RagConfig.CUSTOMER_RETRIEVER) ContentRetriever aiCustomerContentRetriever,
                               RagProperties ragProperties) {
        this.embeddingModel = aiEmbeddingModel;
        this.embeddingStore = aiEmbeddingStore;
        this.consoleContentRetriever = aiConsoleContentRetriever;
        this.customerContentRetriever = aiCustomerContentRetriever;
        this.ragProperties = ragProperties;
    }

    /**
     * 应用启动完成后播种知识库。
     *
     * <p><b>刻意把异常吞掉只记日志</b>：RAG 是「增强」不是「必需」。
     * 若因为嵌入模型加载失败就让整个后端起不来，等于把一个可选功能升级成了单点故障 ——
     * 助手退化成「会话记忆 + 工具调用」仍然可用，此时应当照常提供服务并留下告警日志。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void seedOnStartup() {
        if (!ragProperties.isEnabled()) {
            log.warn("[RAG] 向量检索已关闭（ai.rag.enabled=false），助手将退化为「会话记忆 + 工具调用」");
            return;
        }
        try {
            Map<String, Object> result = rebuild();
            log.info("[RAG] 知识库播种完成：{} 个片段（顾客侧 {} / 运营侧 {}），维度 {}，门槛 全量={} 顾客侧={}，耗时约 {}ms",
                    result.get("segmentCount"),
                    result.get("customerSegments"), result.get("opsSegments"),
                    result.get("dimension"),
                    result.get("minScore"), result.get("minScoreCustomer"),
                    result.get("elapsedMs"));
        } catch (Exception e) {
            log.error("[RAG] 知识库播种失败，助手将退化为「会话记忆 + 工具调用」：{}", e.toString(), e);
        }
    }

    /**
     * 重建知识库：清空向量库 → 重新切分 → 重新向量化 → 重新入库。
     *
     * <p>用 {@code synchronized} 串行化：重建期间若并发检索，会读到「清空后、写入前」的空库。
     * 加锁的代价可以忽略（重建是低频的管理动作），换来的是检索永远看到完整的一代数据。
     *
     * @return 重建结果摘要，供管理页直接展示
     */
    public synchronized Map<String, Object> rebuild() {
        long start = System.currentTimeMillis();
        embeddingStore.removeAll();

        String markdown = PlatformKnowledgeBase.document();
        List<TextSegment> segments = splitIntoSegments(markdown);
        if (segments.isEmpty()) {
            throw new IllegalStateException("知识库切分结果为空，请检查 PlatformKnowledgeBase 内容");
        }

        // 一次性批量向量化：比逐条调用少一轮轮网络/推理开销，也让进度可控
        List<Embedding> vectors = embeddingModel.embedAll(segments).content();
        if (vectors.size() != segments.size()) {
            throw new IllegalStateException(
                    "向量数与片段数不一致：vector=" + vectors.size() + ", segment=" + segments.size());
        }
        embeddingStore.addAll(vectors, segments);

        this.segmentCount = segments.size();
        this.segmentCountByScope = countByScope(segments);
        this.loadedAt = LocalDateTime.now().format(TIME_FMT);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("segmentCount", segments.size());
        result.put("customerSegments", segmentCountByScope.getOrDefault(RagScope.CUSTOMER, 0));
        result.put("opsSegments", segmentCountByScope.getOrDefault(RagScope.OPS, 0));
        result.put("docChars", markdown.length());
        result.put("dimension", vectors.isEmpty() ? 0 : vectors.get(0).dimension());
        result.put("minScore", ragProperties.getMinScore());
        result.put("minScoreCustomer", ragProperties.minScoreFor(RagScope.CUSTOMER));
        result.put("elapsedMs", System.currentTimeMillis() - start);
        result.put("loadedAt", loadedAt);
        return result;
    }

    /** 知识库运行状态，用于管理页显示「知识库是否就绪、装了多少条、分域是否生效、门槛是多少」。 */
    public Map<String, Object> status() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("enabled", ragProperties.isEnabled());
        status.put("ready", segmentCount > 0);
        status.put("segmentCount", Math.max(segmentCount, 0));
        status.put("storeSize", storeSize());
        status.put("customerSegments", segmentCountByScope.getOrDefault(RagScope.CUSTOMER, 0));
        status.put("opsSegments", segmentCountByScope.getOrDefault(RagScope.OPS, 0));
        status.put("loadedAt", loadedAt);
        status.put("docChars", PlatformKnowledgeBase.document().length());
        status.put("topK", ragProperties.getTopK());
        status.put("minScore", ragProperties.getMinScore());
        status.put("minScoreCustomer", ragProperties.minScoreFor(RagScope.CUSTOMER));
        status.put("chunkSize", ragProperties.getChunkSize());
        status.put("chunkOverlap", ragProperties.getChunkOverlap());
        status.put("hybridEnabled", ragProperties.isHybridEnabled());
        status.put("hybridCandidateK", ragProperties.getHybridCandidateK());
        status.put("hybridTitleBoost", ragProperties.getHybridTitleBoost());
        status.put("hybridBodyBoost", ragProperties.getHybridBodyBoost());
        status.put("guardRules", IntentGuard.rules());
        status.putAll(modelInfo());
        return status;
    }

    /** 知识库原文（管理页「查看知识库」用），便于运营核对平台规则的最新表述。 */
    public String sourceText() {
        return PlatformKnowledgeBase.document();
    }

    /**
     * 执行一次检索（管理页「检索测试」用）。
     * <p>这是排查 RAG 问题最有效的手段：输入一句用户会问的话，直接看召回了哪些片段、相似度多少，
     * 以及<b>是在哪个域里找的</b>——
     * <ul>
     *   <li>在「顾客侧」找不到 → 这条规则要么没写进知识库，要么被标成了运营侧（检查标题行的
     *       {@code &lt;!-- ops --&gt;} 标记）；</li>
     *   <li>在「全量」找得到、在「顾客侧」找不到 → 正是分域在起作用（后台口径没有漏给顾客）；</li>
     *   <li>被 {@code blocked=true} 拦掉 → 命中了意图闸门，说明这句话问的是数字/统计而不是规则。</li>
     * </ul>
     *
     * @param query 用户会说的话
     * @param scope 受众域：{@code all} / {@code customer} / {@code ops}，留空按 {@code all}
     * @return 形如 {@code {query, scope, minScore, blocked, blockReason, items:[{section,scope,score,vectorScore,text}]}}
     */
    public Map<String, Object> retrieve(String query, String scope) {
        String normalized = RagScope.normalize(scope);
        String text = query == null ? "" : query.trim();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("query", text);
        result.put("scope", normalized);
        result.put("minScore", ragProperties.minScoreFor(normalized));
        result.put("topK", ragProperties.getTopK());
        result.put("hybridEnabled", ragProperties.isHybridEnabled());

        List<Map<String, Object>> items = new ArrayList<>();
        String blockReason = "";
        if (!text.isEmpty() && ragProperties.isEnabled()) {
            ContentRetriever retriever = retrieverFor(normalized);
            if (retriever instanceof GuardedContentRetriever guarded) {
                blockReason = guarded.guardReason(text);
            }
            if (blockReason.isEmpty()) {
                for (Content content : retriever.retrieve(Query.from(text))) {
                    items.add(toItem(content));
                }
            }
        }
        result.put("blocked", !blockReason.isEmpty());
        result.put("blockReason", blockReason);
        result.put("items", items);
        return result;
    }

    // ==================== 内部实现 ====================

    /**
     * 取本域对应的检索器。
     * <p>{@code all} / {@code customer} 直接复用助手在用的那两个 Bean（保证「页面上看到的」＝
     * 「助手实际用的」）；只有 {@code ops} 是按同一份构造规则现造的 —— 没有助手只用运营侧内容，
     * 为它单独定义一个 Bean 只会多一个没人引用的对象。
     */
    private ContentRetriever retrieverFor(String scope) {
        return switch (scope) {
            case RagScope.CUSTOMER -> customerContentRetriever;
            case RagScope.OPS -> RagConfig.retrieverFor(embeddingStore, embeddingModel, ragProperties, RagScope.OPS);
            default -> consoleContentRetriever;
        };
    }

    private Map<String, Object> toItem(Content content) {
        TextSegment segment = content.textSegment();
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("section", segment.metadata().getString(SECTION_KEY));
        item.put("scope", segment.metadata().getString(RagScope.KEY));
        item.put("score", roundScore(content.metadata().get(ContentMetadata.SCORE)));
        item.put("vectorScore", segment.metadata().containsKey(HybridContentRetriever.VECTOR_SCORE_KEY)
                ? roundScore(segment.metadata().getDouble(HybridContentRetriever.VECTOR_SCORE_KEY))
                : null);
        item.put("text", segment.text());
        return item;
    }

    /**
     * 把整份知识文本切成带元数据的片段：先按 {@code ##} 粗切（同时剥掉受众域标记），
     * 再在每节内部按长度细切。
     */
    private List<TextSegment> splitIntoSegments(String markdown) {
        DocumentSplitter splitter = DocumentSplitters.recursive(
                ragProperties.getChunkSize(), ragProperties.getChunkOverlap());

        List<TextSegment> segments = new ArrayList<>();
        for (Section section : parseSections(markdown)) {
            // 把小节标题以【】形式写回正文：向量模型据此能感知「这段在讲什么」，
            // 检索「退款几天到账」时更容易命中标题里带「退款」的那一节。
            String body = "【" + section.title() + "】\n" + section.body();
            Document document = Document.from(body, Metadata.from(Map.of(
                    SECTION_KEY, section.title(),
                    RagScope.KEY, section.scope())));
            segments.addAll(splitter.split(document));
        }
        return segments;
    }

    /**
     * 按 {@code ## } 行切分小节。一级标题（{@code # }）作为文档名不进入正文，
     * 否则它会被拼进第一个片段，白白占掉上下文。
     * <p>标题行末尾若带 {@link RagScope#OPS_MARKER}，说明该小节属于运营侧：
     * 标记会被<b>剥掉</b>（不进正文、不影响向量），只体现为 {@code scope} 元数据。
     */
    private List<Section> parseSections(String markdown) {
        List<Section> sections = new ArrayList<>();
        String title = null;
        String scope = RagScope.CUSTOMER;
        StringBuilder buffer = new StringBuilder();

        for (String line : markdown.split("\n", -1)) {
            if (line.startsWith(SECTION_PREFIX)) {
                appendSection(sections, title, scope, buffer);
                String heading = line.substring(SECTION_PREFIX.length()).trim();
                scope = RagScope.CUSTOMER;
                if (heading.endsWith(RagScope.OPS_MARKER)) {
                    scope = RagScope.OPS;
                    heading = heading.substring(0, heading.length() - RagScope.OPS_MARKER.length()).trim();
                }
                title = heading;
                buffer.setLength(0);
            } else if (line.startsWith("# ")) {
                continue;
            } else {
                buffer.append(line).append('\n');
            }
        }
        appendSection(sections, title, scope, buffer);
        return sections;
    }

    private void appendSection(List<Section> sections, String title, String scope, StringBuilder buffer) {
        if (title == null) {
            return;
        }
        String body = buffer.toString().trim();
        if (!body.isEmpty()) {
            sections.add(new Section(title, body, scope));
        }
    }

    /** 按受众域统计片段数；顺带发现「标了 ops 却没人认领」这类问题。 */
    private Map<String, Integer> countByScope(List<TextSegment> segments) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put(RagScope.CUSTOMER, 0);
        counts.put(RagScope.OPS, 0);
        for (TextSegment segment : segments) {
            String scope = segment.metadata().getString(RagScope.KEY);
            if (scope == null) {
                scope = RagScope.CUSTOMER;
            }
            counts.merge(scope, 1, Integer::sum);
        }
        return counts;
    }

    /** 用向量库自身的 size 做一次交叉校验，避免「以为播种成功其实没写进去」。 */
    private int storeSize() {
        return embeddingStore instanceof InMemoryEmbeddingStore<?> memoryStore
                ? memoryStore.size()
                : Math.max(segmentCount, 0);
    }

    /**
     * 嵌入模型的「Java 类名 → 人类可读产品名」映射。
     *
     * <p>LangChain4j 的<b>本地 ONNX 模型</b>（本项目用的是 {@code BgeSmallZhV15EmbeddingModel}）
     * 没有覆写 {@code modelName()}，基类一律返回 {@code "unknown"}。
     * 若直接展示，管理页「检索参数 → 嵌入模型」就会露出 {@code BgeSmallZhV15EmbeddingModel}
     * 这种 Java 类名，运营看不懂。因此这里按类名补全产品名；
     * 将来换模型只需往这张表里加一行，展示层无需改动。
     */
    private static final Map<String, String> EMBEDDING_MODEL_LABELS = Map.of(
            "BgeSmallZhV15EmbeddingModel", "BGE-small-zh-v1.5",
            "BgeSmallZhV15QuantizedEmbeddingModel", "BGE-small-zh-v1.5（量化）",
            "BgeSmallEnV15EmbeddingModel", "BGE-small-en-v1.5",
            "BgeSmallEnV15QuantizedEmbeddingModel", "BGE-small-en-v1.5（量化）",
            "AllMiniLmL6V2EmbeddingModel", "all-MiniLM-L6-v2",
            "AllMiniLmL6V2QuantizedEmbeddingModel", "all-MiniLM-L6-v2（量化）",
            "E5SmallV2EmbeddingModel", "e5-small-v2"
    );

    /**
     * 把嵌入模型的类名翻译成产品名。
     *
     * @param simpleClassName 模型的 {@code Class#getSimpleName()}
     * @return 映射表中的产品名；未登记的模型原样返回类名，保证换了新模型也不会显示成空白
     */
    private static String embeddingModelLabel(String simpleClassName) {
        return EMBEDDING_MODEL_LABELS.getOrDefault(simpleClassName, simpleClassName);
    }

    /**
     * 模型信息只做展示用途；万一模型尚未加载，也不能让状态接口跟着挂掉。
     * <p>{@code modelName()} 对本地 ONNX 模型返回的是 {@code "unknown"}
     * （基类没有覆写模型名），因此这里先按 {@link #EMBEDDING_MODEL_LABELS} 把类名翻成产品名，
     * 仍查不到时才退回类名本身。
     */
    private Map<String, Object> modelInfo() {
        Map<String, Object> info = new LinkedHashMap<>();
        try {
            String name = embeddingModel.modelName();
            if (name == null || name.isBlank() || "unknown".equalsIgnoreCase(name)) {
                name = embeddingModelLabel(embeddingModel.getClass().getSimpleName());
            }
            info.put("embeddingModel", name);
            info.put("dimension", embeddingModel.dimension());
        } catch (Exception e) {
            info.put("embeddingModel", "BGE-small-zh-v1.5（未加载）");
            info.put("dimension", 0);
        }
        return info;
    }

    private double roundScore(Object raw) {
        if (raw instanceof Number number) {
            return Math.round(number.doubleValue() * 1000d) / 1000d;
        }
        return 0d;
    }

    /** 一个 Markdown 小节。 */
    private record Section(String title, String body, String scope) {
    }
}
