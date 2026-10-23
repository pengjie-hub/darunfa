package com.example.backend.controller;

import com.example.backend.ai.rag.RagKnowledgeService;
import com.example.backend.common.ApiResponse;
import com.example.backend.security.RequireAdmin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 后台「AI 知识库（RAG）」管理与调试接口。
 *
 * <p>RAG 对使用者是透明的 —— 助手回答时会自动带着平台规则。但一旦回答不准确，
 * 排查必须能落到具体环节：是<b>知识库里没有</b>这条规则，还是<b>有但没被召回</b>，
 * 还是<b>召回了但模型没用</b>？这三种情况的修法完全不同（改文档 / 调切分与阈值 / 改提示词）。
 * 因此这里提供几个观测口径：
 * <ul>
 *   <li>{@code GET /status} —— 知识库装了多少片段（分顾客侧/运营侧）、向量维度、
 *       两个相似度门槛各是多少、词面加权是否开启、意图闸门有哪些规则；</li>
 *   <li>{@code GET /search?q=...&scope=all|customer|ops} —— <b>手动执行一次检索</b>，
 *       看到召回的片段、所属域、相似度（含纯向量分）与是否被闸门拦下。
 *       这是最有用的一条：同一个问题在三个域里各跑一遍，
 *       就能一眼看出「顾客为什么答不上来」到底是知识缺失、被标成了运营侧、还是被闸门拦了；</li>
 *   <li>{@code GET /source} —— 知识库原文，便于核对平台规则的表述是否过时。</li>
 * </ul>
 * 另提供 {@code POST /rebuild}：改完知识文本后不必重启后端，重建一次即可生效。
 *
 * <p>仅管理员可访问 —— 知识库内容属于平台对外口径，改动会影响所有用户看到的答案。
 * <b>检索测试的三个域用的是助手实际在用的那两个 Bean</b>（见 {@code RagConfig#retrieverFor}），
 * 因此这里看到的结果就是助手真实的行为，不是另造一套近似实现。
 */
@RestController
@RequestMapping("/api/admin/ai/knowledge")
@RequireAdmin
public class AiKnowledgeController {

    private final RagKnowledgeService ragKnowledgeService;

    public AiKnowledgeController(RagKnowledgeService ragKnowledgeService) {
        this.ragKnowledgeService = ragKnowledgeService;
    }

    /** 知识库状态：片段数（分域）、向量维度、门槛、词面加权参数、闸门规则、最近播种时间。 */
    @GetMapping("/status")
    public ApiResponse<Map<String, Object>> status() {
        return ApiResponse.ok(ragKnowledgeService.status());
    }

    /**
     * 检索测试：输入一句话，返回被召回的规则片段及其相似度。
     * <p>低于该域 {@code min-score} 的片段不会出现在结果里 —— 与助手实际行为一致。
     *
     * @param scope {@code all}（全量，默认）/ {@code customer}（顾客侧）/ {@code ops}（运营侧）
     */
    @GetMapping("/search")
    public ApiResponse<Map<String, Object>> search(@RequestParam(name = "q", required = false) String query,
                                                   @RequestParam(name = "scope", required = false) String scope) {
        if (query == null || query.isBlank()) {
            return ApiResponse.error(400, "请输入要检索的问题");
        }
        try {
            return ApiResponse.ok(ragKnowledgeService.retrieve(query, scope));
        } catch (Exception e) {
            String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            return ApiResponse.error(500, "检索失败: " + msg);
        }
    }

    /**
     * 重建知识库（清空 → 重新切分 → 重新向量化）。
     * <p>知识文本变更后调用即可生效，无需重启后端。
     */
    @PostMapping("/rebuild")
    public ApiResponse<Map<String, Object>> rebuild() {
        try {
            return ApiResponse.ok(ragKnowledgeService.rebuild());
        } catch (Exception e) {
            String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            return ApiResponse.error(500, "重建失败: " + msg);
        }
    }

    /** 知识库原文（Markdown）。 */
    @GetMapping("/source")
    public ApiResponse<String> source() {
        return ApiResponse.ok(ragKnowledgeService.sourceText());
    }
}
