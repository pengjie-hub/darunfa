package com.example.backend.controller;

import com.example.backend.ai.action.AdminAction;
import com.example.backend.common.ApiResponse;
import com.example.backend.common.BusinessException;
import com.example.backend.dto.RiskScoreDTO;
import com.example.backend.entity.Order;
import com.example.backend.entity.RiskLevel;
import com.example.backend.entity.RiskScore;
import com.example.backend.repository.OrderRepository;
import com.example.backend.repository.RiskScoreRepository;
import com.example.backend.security.AuthInterceptor;
import com.example.backend.security.RequirePermission;
import com.example.backend.service.AdminActionExecutor;
import com.example.backend.service.RiskDetectionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 风险监控管理接口（方案 #4：异常订单/账户风险检测）。
 *
 * <p>路由：
 * <ul>
 *   <li>{@code POST /api/admin/risk/orders/{id}/scan}      手动触发单订单扫描（立即返回同步结果）</li>
 *   <li>{@code POST /api/admin/risk/orders/batch-scan?limit=50} 批量扫描最近未评分订单</li>
 *   <li>{@code GET  /api/admin/risk/scores?level=HIGH|MEDIUM|LOW|ALL} 列出评分（默认全部按分降序）</li>
 *   <li>{@code PUT  /api/admin/risk/scores/{id}/read}     标记已读</li>
 *   <li>{@code GET  /api/admin/risk/summary}               概览（各级别计数 + 未读数）</li>
 *   <li>{@code GET  /api/admin/risk/scores/by-order/{id}}  取某订单最新评分</li>
 * </ul>
 * </p>
 *
 * <p>权限注解：{@code risk:view} 看，{@code risk:scan} 触发扫描。
 * 已在 {@code DataInitializer} 中把 ADMIN 与 OPS 关联上这两个权限。</p>
 */
@RestController
@RequestMapping("/api/admin/risk")
public class RiskController {

    private final RiskDetectionService riskDetectionService;
    private final RiskScoreRepository riskScoreRepository;
    private final OrderRepository orderRepository;

    /**
     * 写操作执行器。
     * <p>「手动扫描」会往 {@code risk_scores} 插一条评分并回写订单风险字段，属于写操作，
     * 因此与另外几个写接口一样纳入幂等与审计（对话卡片连点两次只产生一次评分）。
     */
    private final AdminActionExecutor actionExecutor;

    public RiskController(RiskDetectionService riskDetectionService,
                          RiskScoreRepository riskScoreRepository,
                          OrderRepository orderRepository,
                          AdminActionExecutor actionExecutor) {
        this.riskDetectionService = riskDetectionService;
        this.riskScoreRepository = riskScoreRepository;
        this.orderRepository = orderRepository;
        this.actionExecutor = actionExecutor;
    }

    /**
     * 批量把 RiskScore 列表转 DTO 并补全 orderNo（避免前端按 ID 二次请求）。
     * 一次 SQL 拉所有订单号（默认情况下评分记录数远小于订单数）。
     */
    private List<RiskScoreDTO> toDTOs(List<RiskScore> list) {
        if (list.isEmpty()) return List.of();
        Set<Long> ids = new HashSet<>();
        for (RiskScore rs : list) ids.add(rs.getOrderId());
        Map<Long, String> noMap = orderRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Order::getId, Order::getOrderNo, (a, b) -> a));
        return list.stream().map(rs -> {
            RiskScoreDTO dto = RiskScoreDTO.from(rs);
            dto.setOrderNo(noMap.get(rs.getOrderId()));
            return dto;
        }).toList();
    }

    /**
     * 手动扫描单订单。
     * <p>
     * <b>本次改造（T04）</b>：扫描逻辑原样搬进 {@link AdminActionExecutor#run} 的 lambda，
     * 由执行器负责幂等与审计。HTTP 契约不变（路径/权限/返回体），只多两个可选请求头：
     * {@code Idempotency-Key}（风控的键是「分钟桶」，连点只扫一次）与
     * {@code X-Proposal-Id}（对话卡片回填，用于审计与参数校验）。
     */
    @PostMapping("/orders/{id}/scan")
    @RequirePermission("risk:scan")
    public ApiResponse<RiskScoreDTO> scanOrder(
            @PathVariable Long id,
            @RequestAttribute(AuthInterceptor.ATTR_USER_ID) Long operatorId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "X-Proposal-Id", required = false) String proposalId,
            @RequestAttribute(value = AuthInterceptor.ATTR_ROLE, required = false) String operatorRole) {
        RiskScoreDTO dto = actionExecutor.run(
                AdminActionExecutor.Command.of(AdminAction.RISK_SCAN, id,
                        AdminActionExecutor.Command.params("orderId", id),
                        proposalId, idempotencyKey, operatorId, operatorRole),
                RiskScoreDTO.class,
                () -> {
                    RiskScore rs = riskDetectionService.scan(id);
                    List<RiskScoreDTO> dtos = toDTOs(List.of(rs));
                    return dtos.get(0);
                });
        return ApiResponse.ok(dto);
    }

    /** 批量扫描最近 limit 条未评分订单。 */
    @PostMapping("/orders/batch-scan")
    @RequirePermission("risk:scan")
    public ApiResponse<Map<String, Object>> batchScan(@RequestParam(defaultValue = "30") int limit) {
        int n = riskDetectionService.batchScan(limit);
        return ApiResponse.ok(Map.of("scanned", n));
    }

    /** 列出评分。 */
    @GetMapping("/scores")
    @RequirePermission("risk:view")
    public ApiResponse<List<RiskScoreDTO>> listScores(
            @RequestParam(required = false) String level) {
        List<RiskScore> list;
        if (level == null || level.isEmpty() || "ALL".equalsIgnoreCase(level)) {
            list = riskScoreRepository.findAllByOrderByScoreDescCreatedAtDesc();
        } else {
            RiskLevel lvl;
            try {
                lvl = RiskLevel.valueOf(level.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new BusinessException(400, "非法风险等级: " + level);
            }
            list = riskScoreRepository.findByLevelOrderByScoreDescCreatedAtDesc(lvl);
        }
        return ApiResponse.ok(toDTOs(list));
    }

    /** 标记已读。 */
    @PutMapping("/scores/{id}/read")
    @RequirePermission("risk:view")
    public ApiResponse<Void> markRead(@PathVariable Long id) {
        RiskScore rs = riskScoreRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "评分记录不存在"));
        rs.setIsRead(Boolean.TRUE);
        riskScoreRepository.save(rs);
        return ApiResponse.ok();
    }

    /**
     * 批量置已读 —— 小运「风险消息一键已读」卡片的执行端。
     *
     * <p>与上面的单条接口并存，不改单条行为（页面上的「标已读」仍走那条）。
     * 本接口走 {@link AdminActionExecutor}，因此具备幂等与审计：
     * 同一张卡片连点两次只会有一条 EXECUTED 审计、只执行一次 UPDATE。
     *
     * <p>请求体形如 {@code {"level":"ALL"}}；省略或非法值为 400 {@code PARAM_INVALID}，
     * 不做「静默当成 ALL」的兜底 —— 那会让「只想读高风险」的请求把低风险也一起清掉。
     *
     * @return {@code affected} 本次实际标记行数、{@code remainingUnread} 剩余未读
     */
    @PutMapping("/scores/read-all")
    @RequirePermission("risk:view")
    @SuppressWarnings({"unchecked", "rawtypes"})
    public ApiResponse<Map<String, Object>> markAllRead(
            @RequestBody(required = false) Map<String, Object> body,
            @RequestAttribute(AuthInterceptor.ATTR_USER_ID) Long operatorId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "X-Proposal-Id", required = false) String proposalId,
            @RequestAttribute(value = AuthInterceptor.ATTR_ROLE, required = false) String operatorRole) {
        String levelText = normalizeLevelFilter(body == null ? null : body.get("level"));
        RiskLevel level = parseLevelFilter(levelText);

        Map<String, Object> result = actionExecutor.run(
                AdminActionExecutor.Command.of(AdminAction.RISK_READ_ALL, null,
                        AdminActionExecutor.Command.params("level", levelText),
                        proposalId, idempotencyKey, operatorId, operatorRole),
                Map.class,
                () -> {
                    int affected = riskDetectionService.markAllRead(level);
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("affected", affected);
                    m.put("remainingUnread", riskScoreRepository.countByIsReadFalse());
                    m.put("level", levelText);
                    m.put("message", affected == 0
                            ? "没有未读的风险消息"
                            : "已把 " + affected + " 条风险消息标记为已读");
                    return m;
                });
        return ApiResponse.ok(result);
    }

    /** 归一化范围参数：缺省 ALL，统一大写；不做合法性判断（交给 {@link #parseLevelFilter}）。 */
    private static String normalizeLevelFilter(Object raw) {
        String s = raw == null ? null : String.valueOf(raw).trim();
        return (s == null || s.isEmpty()) ? "ALL" : s.toUpperCase();
    }

    /** {@code ALL} → {@code null}（= 不限等级）；非法值直接 400。 */
    private static RiskLevel parseLevelFilter(String levelText) {
        if ("ALL".equals(levelText)) return null;
        try {
            return RiskLevel.valueOf(levelText);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(400, "PARAM_INVALID", "非法范围: " + levelText
                    + "（只能是 ALL / HIGH / MEDIUM / LOW）");
        }
    }

    /** 概览。 */
    @GetMapping("/summary")
    @RequirePermission("risk:view")
    public ApiResponse<Map<String, Object>> summary() {
        return ApiResponse.ok(riskDetectionService.summary());
    }

    /** 取某订单最新评分（用于订单详情面板）。 */
    @GetMapping("/scores/by-order/{orderId}")
    @RequirePermission("risk:view")
    public ApiResponse<RiskScoreDTO> byOrder(@PathVariable Long orderId) {
        RiskScore rs = riskScoreRepository.findLatestByOrderId(orderId)
                .orElseThrow(() -> new BusinessException(404, "该订单暂无风险评分"));
        List<RiskScoreDTO> dtos = toDTOs(List.of(rs));
        return ApiResponse.ok(dtos.get(0));
    }
}