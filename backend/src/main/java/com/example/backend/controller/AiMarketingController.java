package com.example.backend.controller;

import com.example.backend.ai.service.MarketingCopyService;
import com.example.backend.common.ApiResponse;
import com.example.backend.dto.MarketingCopyRequest;
import com.example.backend.dto.MarketingCopyResponse;
import com.example.backend.security.RequirePermission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 后台「AI 营销文案生成」接口（商品文案 / 活动文案共用）。
 *
 * <ul>
 *   <li>{@code POST /api/admin/ai/marketing/copy}   生成营销文案（请求体见 {@link MarketingCopyRequest}）</li>
 *   <li>{@code GET  /api/admin/ai/marketing/scenes} 场景 / 渠道 / 促销类型字典（供前端下拉，避免硬编码）</li>
 * </ul>
 *
 * <p>权限：{@code @RequirePermission({"product:edit", "promotion:manage"})} —— 多权限码为 OR 语义，
 * 与「商品 / 促销编辑」语义一致，同时覆盖商品文案与活动文案两个入口；不新增权限码。
 * <p>全部接口均无业务副作用（不落库）。
 */
@RestController
@RequestMapping("/api/admin/ai/marketing")
public class AiMarketingController {

    private final MarketingCopyService marketingCopyService;

    public AiMarketingController(MarketingCopyService marketingCopyService) {
        this.marketingCopyService = marketingCopyService;
    }

    /**
     * 生成营销文案。
     * <p>{@code required = false} 允许请求体缺失进来后由服务层统一抛 400 人话，
     * 而不是交给框架抛一个不可控的反序列化异常。
     */
    @PostMapping("/copy")
    @RequirePermission({"product:edit", "promotion:manage"})
    public ApiResponse<MarketingCopyResponse> copy(@RequestBody(required = false) MarketingCopyRequest request) {
        return ApiResponse.ok(marketingCopyService.generate(request));
    }

    /** 场景 / 渠道 / 促销类型字典。 */
    @GetMapping("/scenes")
    @RequirePermission({"product:edit", "promotion:manage"})
    public ApiResponse<Map<String, Object>> scenes() {
        return ApiResponse.ok(marketingCopyService.dictionaries());
    }
}
