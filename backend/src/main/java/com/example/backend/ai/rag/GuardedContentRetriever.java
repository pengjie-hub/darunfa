package com.example.backend.ai.rag;

import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 给任意检索器套上一层 {@link IntentGuard} 闸门（P2）。
 *
 * <h3>为什么用「装饰器」而不是把判断写进助手</h3>
 * 闸门一旦散落在助手里，就会出现「小发拦了、客服没拦」这种不一致；
 * 而 {@code @AiService(contentRetriever = ...)} 引用的本来就是<b>一个 Bean</b>，
 * 把闸门做进这个 Bean，就保证了「凡是挂顾客侧检索器的助手，行为完全一致」。
 * <p>
 * 更实际的收益是<b>可观测</b>：{@link RagKnowledgeService} 先查这个包装器拿到拦截原因，
 * 管理页就能把「这句被拦了」和「拦它的是哪条规则」直接显示出来 ——
 * 否则用户只会看到一个空结果，分不清是「知识库里没有」还是「被闸门拦了」。
 */
public class GuardedContentRetriever implements ContentRetriever {

    private static final Logger log = LoggerFactory.getLogger(GuardedContentRetriever.class);

    private final ContentRetriever delegate;

    public GuardedContentRetriever(ContentRetriever delegate) {
        this.delegate = delegate;
    }

    @Override
    public List<Content> retrieve(Query query) {
        String reason = IntentGuard.matchedRule(query.text());
        if (!reason.isEmpty()) {
            log.debug("[RAG] 意图闸门拦截，跳过知识库检索：reason={} query={}", reason, query.text());
            return List.of();
        }
        return delegate.retrieve(query);
    }

    /**
     * 外部（管理页）查询「这句话会不会被闸门拦掉」，不触发实际检索。
     *
     * @return 未拦截返回空串；拦截返回「规则名：原因」
     */
    public String guardReason(String query) {
        return IntentGuard.explain(query);
    }
}
