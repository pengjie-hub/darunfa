package com.example.backend.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * AI 运营助手答复。
 *
 * <h3>本次增量（T04）：多了两样东西</h3>
 * <ul>
 *   <li>{@link #proposals}：本轮「待确认提案」列表 —— 前端据此渲染确认卡片
 *       （退款 / 补货 / 活动 / 发货 / 风控）。<b>只来自框架的 {@code toolExecutions()}</b>，
 *       即本轮真实发生过的工具调用，不是模型自述；</li>
 *   <li>{@link #needsClarification}：模型在等运营补充槽位（如没给运单号）时为 {@code true}，
 *       前端据此把输入框聚焦、不显示「已准备好」态。</li>
 * </ul>
 *
 * <h3>兼容承诺：既有字段一字不差</h3>
 * 两个新字段都标 {@link JsonInclude.Include#NON_NULL}，为 {@code null} 时<b>不出现在 JSON 里</b>。
 * 因此「只读问答」的响应体仍是 {@code {"answer":"..."}} —— 后台页面 {@code admin.html}
 * 与 App 侧无需任何改动即可继续工作；单参构造 {@link #AiChatResponse(String)} 原样保留，
 * 既有调用点零改动。这条与 {@code ApiResponse.subCode} 用的是同一套做法。
 */
public class AiChatResponse {

    private String answer;

    /** 待确认提案；无提案时为 null（不序列化）。 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private List<ActionProposal> proposals;

    /** 是否在向运营追问信息（缺槽位）；null 时不序列化。 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Boolean needsClarification;

    public AiChatResponse() {}

    /** 旧构造：只有答复，行为与改造前完全一致。 */
    public AiChatResponse(String answer) {
        this.answer = answer;
    }

    public AiChatResponse(String answer, List<ActionProposal> proposals, Boolean needsClarification) {
        this.answer = answer;
        this.proposals = (proposals == null || proposals.isEmpty()) ? null : proposals;
        this.needsClarification = needsClarification;
    }

    public String getAnswer() { return answer; }
    public void setAnswer(String answer) { this.answer = answer; }

    public List<ActionProposal> getProposals() { return proposals; }
    public void setProposals(List<ActionProposal> proposals) { this.proposals = proposals; }

    public Boolean getNeedsClarification() { return needsClarification; }
    public void setNeedsClarification(Boolean needsClarification) { this.needsClarification = needsClarification; }
}
