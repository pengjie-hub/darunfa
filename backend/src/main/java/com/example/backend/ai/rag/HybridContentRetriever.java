package com.example.backend.ai.rag;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.ContentMetadata;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 分域 + 词面加权的检索器，替代直接 new 出来的 {@code EmbeddingStoreContentRetriever}。
 *
 * <h3>它比框架自带的那一个多做了三件事</h3>
 * <ol>
 *   <li><b>分域过滤（P0）</b>：检索前先按 {@link RagScope#KEY} 元数据筛掉不属于本助手受众的小节。
 *       {@code EmbeddingStoreContentRetriever} 本身也支持 {@code .filter(...)}，
 *       这里自己实现是因为下面两件事需要拿到「候选全集」，框架那一个在返回前已经把低分候选丢掉了。</li>
 *   <li><b>词面加权（P3）</b>：向量分之外再加一路 {@link LexicalTitleBooster} 的词面证据，
 *       用来救「怎么转人工？」（向量 0.769，字面几乎逐字命中标题）这类被短问句压低的真问题。</li>
 *   <li><b>可解释</b>：每条结果的<b>纯向量分</b>会写回片段元数据的 {@value #VECTOR_SCORE_KEY} 字段，
 *       管理页据此能分辨一条结果是「向量召回的」还是「词面加权顶上来的」。
 *       注意这个字段只用于观测 —— 注入给模型的只有片段正文，不含元数据。</li>
 * </ol>
 *
 * <h3>候选集要放大，但门槛不放松</h3>
 * 取 {@code max(auto, candidateK)} 条候选（默认 10）再重排、再过滤、最后截取 topK。
 * 如果先按 topK 截断再算词面分，就会出现「真正该救的那条根本没进候选」的情况 ——
 * 加权就白做了。<b>放大的是候选，不是门槛</b>：没达到 min-score 的照样一条都不给。
 *
 * <h3>关闭词面加权即是原行为</h3>
 * {@code ai.rag.hybrid-enabled=false} 时增量恒为 0，本类退化成「带过滤的普通向量检索」，
 * 因此可以拿它做严格的 A/B 对照，而不是靠感觉判断加权有没有用。
 */
public class HybridContentRetriever implements ContentRetriever {

    /** 片段元数据里记录纯向量分的键（仅供管理页观测）。 */
    public static final String VECTOR_SCORE_KEY = "vscore";

    private final EmbeddingStore<TextSegment> embeddingStore;
    private final EmbeddingModel embeddingModel;
    private final Filter scopeFilter;
    private final RagProperties ragProperties;
    private final double minScore;
    private final String displayName;

    /**
     * @param scopeFilter      受众域过滤器；{@code null} 表示不过滤（全量）
     * @param minScore         本检索器的相似度门槛（分域后各域门槛不同，因此单独传入而不从全局取）
     * @param displayName      便于日志与状态接口区分是谁
     */
    public HybridContentRetriever(EmbeddingStore<TextSegment> embeddingStore,
                                  EmbeddingModel embeddingModel,
                                  Filter scopeFilter,
                                  RagProperties ragProperties,
                                  double minScore,
                                  String displayName) {
        this.embeddingStore = embeddingStore;
        this.embeddingModel = embeddingModel;
        this.scopeFilter = scopeFilter;
        this.ragProperties = ragProperties;
        this.minScore = minScore;
        this.displayName = displayName;
    }

    @Override
    public List<Content> retrieve(Query query) {
        String text = query.text() == null ? "" : query.text().trim();
        if (text.isEmpty()) {
            return List.of();
        }

        int candidates = Math.max(ragProperties.getTopK(), ragProperties.getHybridCandidateK());
        EmbeddingSearchRequest.EmbeddingSearchRequestBuilder request = EmbeddingSearchRequest.builder()
                .queryEmbedding(embeddingModel.embed(text).content())
                .maxResults(candidates)
                .minScore(0.0);
        if (scopeFilter != null) {
            request.filter(scopeFilter);
        }

        List<EmbeddingMatch<TextSegment>> matches =
                embeddingStore.search(request.build()).matches();
        if (matches.isEmpty()) {
            return List.of();
        }

        boolean hybrid = ragProperties.isHybridEnabled();
        Set<String> queryBigrams = hybrid
                ? LexicalTitleBooster.contentBigrams(text)
                : Set.of();

        List<Content> scored = new ArrayList<>(matches.size());
        for (EmbeddingMatch<TextSegment> match : matches) {
            TextSegment segment = match.embedded();
            double vectorScore = match.score();
            double finalScore = vectorScore;
            if (hybrid) {
                String title = segment.metadata().getString(RagKnowledgeService.SECTION_KEY);
                String body = stripTitlePrefix(segment.text(), title);
                double bonus = LexicalTitleBooster.bonus(queryBigrams, title, body,
                        ragProperties.getHybridTitleBoost(), ragProperties.getHybridBodyBoost());
                // 加项只用于「把本已接近门槛的真问题顶过去」，因此上限截到 1.0，避免出现 >1 的分数
                finalScore = Math.min(1.0, vectorScore + bonus);
            }
            if (finalScore < minScore) {
                continue;
            }
            Map<ContentMetadata, Object> metadata = new LinkedHashMap<>();
            metadata.put(ContentMetadata.SCORE, finalScore);
            scored.add(Content.from(withVectorScore(segment, vectorScore), metadata));
        }

        scored.sort(Comparator.comparingDouble(HybridContentRetriever::scoreOf).reversed());
        return scored.size() > ragProperties.getTopK()
                ? new ArrayList<>(scored.subList(0, ragProperties.getTopK()))
                : scored;
    }

    /** 检索器标识（日志/状态接口用）。 */
    public String displayName() {
        return displayName;
    }

    /** 本检索器生效的相似度门槛。 */
    public double minScore() {
        return minScore;
    }

    private static double scoreOf(Content content) {
        Object score = content.metadata().get(ContentMetadata.SCORE);
        return score instanceof Number number ? number.doubleValue() : 0d;
    }

    /**
     * 复制一份片段，把纯向量分写进元数据供管理页观测。
     * <p>{@code Metadata} 是可变的，而 {@code InMemoryEmbeddingStore} 返回的是<b>库里的同一个对象</b>，
     * 所以必须先 {@code Metadata.from(map)} 拷一份再写，绝不能就地 put —— 那会把库里的数据改脏。
     */
    private static TextSegment withVectorScore(TextSegment segment, double vectorScore) {
        Metadata copy = Metadata.from(segment.metadata().toMap());
        copy.put(VECTOR_SCORE_KEY, Math.round(vectorScore * 1000d) / 1000d);
        return TextSegment.from(segment.text(), copy);
    }

    /**
     * 去掉片段开头由 {@code RagKnowledgeService} 写回的「【小节标题】」前缀，
     * 避免标题里的词在「正文重合度」里被重复计算一次（体现实证：标题与正文分别用各自的权重）。
     */
    private static String stripTitlePrefix(String text, String title) {
        if (title == null || title.isEmpty()) {
            return text;
        }
        String prefix = "【" + title + "】";
        return text.startsWith(prefix) ? text.substring(prefix.length()).trim() : text;
    }
}
