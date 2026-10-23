package com.example.backend.ai.rag;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.bgesmallzhv15.BgeSmallZhV15EmbeddingModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.MetadataFilterBuilder;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RAG（检索增强生成）装配。
 *
 * <h3>RAG 在这里到底补了什么</h3>
 * 光有「模型 + 工具」，助手能查数据库，却答不了「平台规则」这类问题 ——
 * 「退款几天到账」「为什么我的优惠券没叠加」「发货几天算超时」。
 * 因为答案是<b>制度</b>不是<b>数据</b>：它不在任何一张表里，模型也没在训练里见过我们这个平台。
 * 于是模型只能编，或者含糊其辞。
 * <p>
 * RAG 的做法是：把平台规则写成文档 → 切成片段 → 预先算成向量存进向量库；
 * 每次用户提问时，先用同一个向量模型把问题也算成向量，捞出语义最接近的几个片段，
 * 在调用大模型之前把这几段文字塞进上下文。模型于是「看着资料回答」，而不是「凭印象回答」。
 *
 * <h3>四层结构</h3>
 * <pre>
 *   PlatformKnowledgeBase（知识：平台规则文本，用 &lt;!-- ops --&gt; 声明受众域）
 *            │ 切分 DocumentSplitters.recursive（打上 section / scope 元数据）
 *            ▼
 *   EmbeddingModel（BGE-small-zh-v1.5，本地 ONNX 推理，中文语义）
 *            │ 向量化
 *            ▼
 *   HybridContentRetriever（按 scope 过滤 + 向量分 + 词面加权 + 阈值）
 *            │ 顾客侧再套一层 GuardedContentRetriever（意图闸门）
 *            ▼
 *   AdminAssistant（全量） / ShopAssistant · CsAgent（仅对外规则）
 * </pre>
 *
 * <h3>为什么要分成两个检索器</h3>
 * 原先三个助手<b>共用同一个检索器</b>，于是顾客问「今天有多少订单」时会召回
 * 「按天统计的时间范围怎么算」这类后台口径。实测下来这直接导致
 * <b>正例最低分（0.769）反而低于负例最高分（0.777）</b> —— 分数带交错，
 * 任何单一阈值都不可能同时「救回真问题」和「挡住无关问题」。分成两域后各自校准阈值，
 * 才是这个问题的结构性修法（详见 {@link RagScope} 与 {@link RagProperties}）。
 * <p>
 * 注意两个检索器是<b>同一个 Bean 构造方法</b>（{@link #retrieverFor}）造出来的，
 * 只有「域」这一个参数不同 —— 这样管理页的「分域检索测试」与助手实际用的检索器
 * 一定是同一份行为，不会出现「页面上好好的、助手却不一样」。
 *
 * <h3>为什么用本地嵌入模型而不是调 API</h3>
 * <ul>
 *   <li><b>零成本、零配额</b>：嵌入发生在启动时和每次提问时，调用量比对话大得多，
 *       走外部 API 会显著增加费用与延迟；</li>
 *   <li><b>离线可用</b>：模型权重打包在 jar 里（约 95MB），断网也能跑，教学演示不会因为网络抖动失败；</li>
 *   <li><b>检索质量够用</b>：BGE-small-zh 是中文语义检索里同体积表现最好的小模型之一，
 *       平台知识只有几十个片段，用大模型属于杀鸡用牛刀。</li>
 * </ul>
 * 代价是首次依赖下载约 160MB（含 onnxruntime 推理运行时），之后完全本地。
 *
 * <h3>为什么向量库用内存实现而不是新建一张表</h3>
 * 知识库是<b>静态规则</b>，几十个片段、几百 KB 量级，启动时重新播种只需一两秒；
 * 为此引入一张向量表、一套建表/迁移/清理流程，收益远小于维护成本。
 * {@code InMemoryEmbeddingStore} 与「复用现有表」的既定原则一致 —— 不新增数据库对象。
 * 若将来知识量增长到需要持久化，只需把这里的 Bean 换成基于表的实现，其余代码一行不用改。
 *
 * <h3>关于 {@code ai.rag.enabled} 这个开关</h3>
 * 开关<b>只控制知识库是否播种</b>，不控制 Bean 是否创建。原因是：
 * {@code @AiService(contentRetriever = RagConfig.CUSTOMER_RETRIEVER)} 是<b>编译期的固定引用</b>，
 * 一旦 Bean 缺席，所有助手都会在启动时装配失败。让 Bean 恒在、知识库可空，
 * 关掉开关后检索自然返回 0 条，助手安静地退化成「纯记忆 + 工具调用」，
 * 既满足「可关闭」，又不会把整个应用拖下水。
 */
@Configuration
@EnableConfigurationProperties(RagProperties.class)
public class RagConfig {

    /** 嵌入模型 Bean 名。 */
    public static final String EMBEDDING_MODEL = "aiEmbeddingModel";

    /** 向量库 Bean 名（注意不是工具）：命名为 {@code aiEmbeddingStore} 以便与数据库仓储区分。 */
    public static final String EMBEDDING_STORE = "aiEmbeddingStore";

    /** 后台（全量）检索器 Bean 名，供「小运」使用。 */
    public static final String CONSOLE_RETRIEVER = "aiConsoleContentRetriever";

    /** 顾客侧检索器 Bean 名（仅对外规则 + 意图闸门），供「小发」与 AI 客服使用。 */
    public static final String CUSTOMER_RETRIEVER = "aiCustomerContentRetriever";

    /**
     * 嵌入模型：BGE-small-zh-v1.5。
     * <p>单例 —— 模型加载需把 95MB 的 ONNX 权重读进内存并初始化 tokenizer，
     * 每次请求都新建会直接拖垮响应时间。
     */
    @Bean(EMBEDDING_MODEL)
    public EmbeddingModel aiEmbeddingModel() {
        return new BgeSmallZhV15EmbeddingModel();
    }

    /**
     * 向量库：进程内内存实现，内容由 {@code RagKnowledgeService} 在启动时播种，重启后重建。
     */
    @Bean(EMBEDDING_STORE)
    public EmbeddingStore<TextSegment> aiEmbeddingStore() {
        return new InMemoryEmbeddingStore<>();
    }

    /** 后台（全量）检索器：「小运」既能查顾客规则，也能查后台口径。 */
    @Bean(CONSOLE_RETRIEVER)
    public ContentRetriever aiConsoleContentRetriever(EmbeddingStore<TextSegment> aiEmbeddingStore,
                                                      EmbeddingModel aiEmbeddingModel,
                                                      RagProperties ragProperties) {
        return retrieverFor(aiEmbeddingStore, aiEmbeddingModel, ragProperties, RagScope.ALL);
    }

    /** 顾客侧检索器：「小发」与 AI 客服只能看到对外规则，且先过意图闸门。 */
    @Bean(CUSTOMER_RETRIEVER)
    public ContentRetriever aiCustomerContentRetriever(EmbeddingStore<TextSegment> aiEmbeddingStore,
                                                       EmbeddingModel aiEmbeddingModel,
                                                       RagProperties ragProperties) {
        return retrieverFor(aiEmbeddingStore, aiEmbeddingModel, ragProperties, RagScope.CUSTOMER);
    }

    /**
     * 按受众域构造检索器 —— <b>Bean 定义与管理页检索测试共用的唯一构造入口</b>。
     *
     * <p>把它抽出来是为了杜绝一类很难查的偏差：管理页自己 new 一个检索器来做「检索测试」，
     * 和助手真正用的那个各自演化，最后页面上看到的结果与助手的实际行为对不上，
     * 调试就变成了自欺欺人。这里只有「域」一个变量。
     *
     * @param scope {@link RagScope#ALL} / {@link RagScope#CUSTOMER} / {@link RagScope#OPS}
     */
    public static ContentRetriever retrieverFor(EmbeddingStore<TextSegment> embeddingStore,
                                                EmbeddingModel embeddingModel,
                                                RagProperties ragProperties,
                                                String scope) {
        String normalized = RagScope.normalize(scope);
        Filter scopeFilter = RagScope.ALL.equals(normalized)
                ? null
                : MetadataFilterBuilder.metadataKey(RagScope.KEY).isEqualTo(normalized);

        HybridContentRetriever retriever = new HybridContentRetriever(
                embeddingStore, embeddingModel, scopeFilter, ragProperties,
                ragProperties.minScoreFor(normalized), label(normalized));

        // 只有顾客侧挂意图闸门：后台问数据是本职，拦掉反而坏事
        return RagScope.CUSTOMER.equals(normalized)
                ? new GuardedContentRetriever(retriever)
                : retriever;
    }

    private static String label(String scope) {
        return switch (scope) {
            case RagScope.CUSTOMER -> "顾客侧（仅对外规则）";
            case RagScope.OPS -> "运营侧（仅后台口径）";
            default -> "全量知识库";
        };
    }
}
