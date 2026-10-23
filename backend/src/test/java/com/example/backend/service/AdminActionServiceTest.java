package com.example.backend.service;

import com.example.backend.ai.action.ActionCatalog;
import com.example.backend.ai.context.AdminActionContext;
import com.example.backend.common.BusinessException;
import com.example.backend.dto.ActionProposal;
import com.example.backend.entity.AdminActionLog;
import com.example.backend.repository.AdminActionLogRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link AdminActionService} 编译链路的纯单元测试：<b>不起 Spring、不连库</b>。
 *
 * <p>仓储用一个动态代理假冒（{@link Proxy}）：只记录 {@code save} 的入参、让
 * {@code findByStatusAndExpiresAtBefore} 返回空列表。这样就把「工具返回串 → 提案 + 落库行」
 * 这条链路的真实行为钉住了，而不是只测一个纯函数。
 *
 * <h3>覆盖什么</h3>
 * <ol>
 *   <li>{@code ok=false} 的工具结果<b>不生成提案</b>（预检不通过不出卡片）；</li>
 *   <li>未注册动作抛 {@link IllegalArgumentException}（由上层丢弃，绝不渲染无 confirm 的卡片）；</li>
 *   <li>正向提案的 {@code confirm.path} 来自服务端白名单，幂等键按动作规则渲染；</li>
 *   <li>落库行是 {@code PROPOSED} + {@code entry=CHAT} + 真实 operatorId + paramsDigest；</li>
 *   <li>身份上下文缺失时<b>拒绝执行</b>（{@code CONTEXT_MISSING}），不降级跳过审计。</li>
 * </ol>
 */
class AdminActionServiceTest {

    private final ActionCatalog catalog = new ActionCatalog();

    // =====================================================================
    //  正向
    // =====================================================================

    @Test
    @DisplayName("新建活动：confirm.path 来自白名单，幂等键是内容指纹，落库 PROPOSED")
    void compilePromotionProposal() {
        List<AdminActionLog> saved = new ArrayList<>();
        AdminActionService service = new AdminActionService(catalog, fakeRepository(saved));

        try (AdminActionContext ctx = AdminActionContext.bind(
                2L, "OPS", Set.of("promotion:manage"), AdminActionContext.ENTRY_CHAT, 99L, "10.0.0.1", "junit")) {

            ActionProposal proposal = service.compileProposal(promotionToolJson(), 99L);

            assertNotNull(proposal);
            assertEquals("PROMOTION_CREATE", proposal.action());
            assertEquals("新建优惠活动", proposal.actionLabel());
            assertEquals("PROMOTION", proposal.targetType());
            assertNull(proposal.targetId(), "活动尚未创建，没有主键");
            assertEquals("/api/admin/promotions", proposal.confirm().path());
            assertEquals("POST", proposal.confirm().method());
            assertTrue(proposal.confirm().requiresHeader().containsKey("Idempotency-Key"));
            assertTrue(proposal.idempotencyKey().startsWith("promotion:"));
            assertTrue(proposal.expiresAt().endsWith("+08:00"), proposal.expiresAt());
            assertTrue(proposal.proposalId().startsWith("pa_"));
            assertEquals(10, proposal.proposalId().length() - "pa_".length());
            assertFalse(proposal.fields().isEmpty(), "卡片字段必须由服务端算好");
            assertFalse(proposal.precheck().ok() == false, "预检通过才能出卡片");

            assertEquals(1, saved.size(), "出卡片必须落一行审计");
            AdminActionLog row = saved.get(0);
            assertEquals(AdminActionLog.Status.PROPOSED, row.getStatus());
            assertEquals("CHAT", row.getEntry());
            assertEquals(2L, row.getOperatorId());
            assertEquals("OPS", row.getOperatorRole());
            assertEquals(99L, row.getChatMessageId());
            assertEquals(proposal.proposalId(), row.getProposalId());
            // PROPOSED 行不写幂等键：同一动作可被多次预检，键由执行器在 CONFIRMED 时抢占
            // （`uk_aal_idem` 是 UNIQUE，PROPOSED 就写键会让第二次预检撞键、卡片被静默丢弃）
            assertNull(row.getIdempotencyKey(), "PROPOSED 行的幂等键必须为 NULL");
            assertNotNull(row.getParamsDigest());
            assertNotNull(row.getExpiresAt());

            // 参数指纹与 ActionCatalog 的同一实现一致（执行器复核时用的是同一个函数）
            assertEquals(ActionCatalog.paramsDigest(proposal.params()), row.getParamsDigest());
        }
    }

    @Test
    @DisplayName("补货：幂等键含 expectedStock，body 白名单只带 delta/expectedStock")
    void compileRestockProposal() {
        List<AdminActionLog> saved = new ArrayList<>();
        AdminActionService service = new AdminActionService(catalog, fakeRepository(saved));

        try (AdminActionContext ctx = AdminActionContext.bind(
                7L, "OPS", Set.of("product:edit"), AdminActionContext.ENTRY_CHAT, null, null, null)) {

            ActionProposal proposal = service.compileProposal(restockToolJson(7), null);

            assertEquals("restock:1:7:100", proposal.idempotencyKey());
            assertEquals("/api/admin/products/1/stock", proposal.confirm().path());
            assertEquals("PUT", proposal.confirm().method());

            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> body = (java.util.Map<String, Object>) proposal.confirm().body();
            assertEquals(100, body.get("delta"));
            assertEquals(7, body.get("expectedStock"));

            // 同一份快照重复编译 → 键相同（幂等的前提）；快照变了 → 键不同（第二次合法补货不被误拦）
            assertEquals("restock:1:7:100", catalog.renderIdempotencyKey(
                    com.example.backend.ai.action.AdminAction.STOCK_REPLENISH, 1L,
                    java.util.Map.of("delta", 100), java.util.Map.of("expectedStock", 7)));
        }
    }

    @Test
    @DisplayName("同一动作预检两次：两行 PROPOSED、proposal_id 不同、幂等键均为 NULL（不会撞 uk_aal_idem）")
    void duplicatePreparesDoNotCollideOnUniqueKey() {
        List<AdminActionLog> saved = new ArrayList<>();
        AdminActionService service = new AdminActionService(catalog, fakeRepository(saved));

        try (AdminActionContext ctx = AdminActionContext.bind(
                2L, "OPS", Set.of("promotion:manage"), AdminActionContext.ENTRY_CHAT, null, null, null)) {

            ActionProposal first = service.compileProposal(promotionToolJson(), null);
            ActionProposal second = service.compileProposal(promotionToolJson(), null);

            assertEquals(2, saved.size(), "说两遍同一件事必须落两行 PROPOSED（可回答「AI 建议过什么」）");
            assertNotEquals(first.proposalId(), second.proposalId(), "两次预检必须是两张不同的卡片");
            assertEquals(first.idempotencyKey(), second.idempotencyKey(),
                    "同一内容的幂等键必须一致（前端与执行器都靠它去重）");
            assertNull(saved.get(0).getIdempotencyKey(),
                    "PROPOSED 行不写键，否则第二次预检会撞 UNIQUE 被静默丢弃");
            assertNull(saved.get(1).getIdempotencyKey());
        }
    }

    // =====================================================================
    //  拒绝路径
    // =====================================================================

    @Test
    @DisplayName("ok=false（预检不通过）不生成提案，也不落库")
    void precheckFailedProducesNoProposal() {
        List<AdminActionLog> saved = new ArrayList<>();
        AdminActionService service = new AdminActionService(catalog, fakeRepository(saved));

        try (AdminActionContext ctx = AdminActionContext.bind(
                2L, "CS", Set.of("order:view"), AdminActionContext.ENTRY_CHAT, null, null, null)) {

            String json = """
                    {"ok":false,"subCode":"PERM_DENIED","message":"补货需要「编辑商品」权限",
                     "precheck":{"ok":false,"checkedAt":"2026-10-22T14:30:12","checks":[{"code":"PERMISSION","ok":false,"message":"缺少 product:edit"}]}}
                    """;
            assertNull(service.compileProposal(json, null));
            assertTrue(saved.isEmpty(), "预检不通过不应产生审计行的 PROPOSED");
        }
    }

    @Test
    @DisplayName("未注册动作（P1 批量）抛 IllegalArgumentException，由上层丢弃")
    void unregisteredActionRejected() {
        AdminActionService service = new AdminActionService(catalog, fakeRepository(new ArrayList<>()));

        try (AdminActionContext ctx = AdminActionContext.bind(
                2L, "OPS", Set.of("risk:scan"), AdminActionContext.ENTRY_CHAT, null, null, null)) {

            String json = """
                    {"ok":true,"action":"ORDER_SHIP_BATCH","targetType":"ORDER_BATCH","targetId":1,
                     "params":{},"snapshot":{},"precheck":{"ok":true,"checkedAt":"x","checks":[]}}
                    """;
            assertThrows(IllegalArgumentException.class, () -> service.compileProposal(json, null));
        }
    }

    @Test
    @DisplayName("precheck.ok=false 但工具误报 ok=true：拒绝生成提案")
    void inconsistentToolResultRejected() {
        AdminActionService service = new AdminActionService(catalog, fakeRepository(new ArrayList<>()));

        try (AdminActionContext ctx = AdminActionContext.bind(
                2L, "OPS", Set.of("order:ship"), AdminActionContext.ENTRY_CHAT, null, null, null)) {

            String json = """
                    {"ok":true,"action":"ORDER_SHIP","targetType":"ORDER","targetId":5,
                     "params":{"company":"顺丰速运","trackingNo":"SF1234567890"},"snapshot":{},
                     "precheck":{"ok":false,"checkedAt":"x","checks":[{"code":"ORDER_STATE","ok":false,"message":"订单已发货"}]}}
                    """;
            assertThrows(IllegalArgumentException.class, () -> service.compileProposal(json, null));
        }
    }

    @Test
    @DisplayName("身份上下文缺失：拒绝编译（CONTEXT_MISSING），绝不降级跳过审计")
    void missingContextRejected() {
        AdminActionService service = new AdminActionService(catalog, fakeRepository(new ArrayList<>()));

        // 刻意不绑定 AdminActionContext
        BusinessException e = assertThrows(BusinessException.class,
                () -> service.compileProposal(promotionToolJson(), null));
        assertEquals("CONTEXT_MISSING", e.getSubCode());
        assertEquals(500, e.getCode());
    }

    @Test
    @DisplayName("buildProposals：一条脏数据不影响其它提案，且 ok=false 的被跳过")
    void buildProposalsIsolatesFailures() {
        List<AdminActionLog> saved = new ArrayList<>();
        AdminActionService service = new AdminActionService(catalog, fakeRepository(saved));

        try (AdminActionContext ctx = AdminActionContext.bind(
                2L, "ADMIN", Set.of("promotion:manage", "product:edit"), AdminActionContext.ENTRY_CHAT, null, null, null)) {

            List<String> toolResults = List.of(
                    "{\"ok\":false,\"subCode\":\"PERM_DENIED\"}",
                    "this is not json",
                    promotionToolJson(),
                    restockToolJson(7));

            List<ActionProposal> proposals = service.buildProposals(toolResults, null);
            assertEquals(2, proposals.size(), "两条坏数据必须被丢弃，两条正常提案必须保留");
            assertEquals("PROMOTION_CREATE", proposals.get(0).action());
            assertEquals("STOCK_REPLENISH", proposals.get(1).action());
        }
    }

    // =====================================================================
    //  测试替身与样例数据
    // =====================================================================

    /**
     * 动态代理假冒仓储：只实现本测试会用到的三个方法，其余返回类型安全的默认值。
     */
    private static AdminActionLogRepository fakeRepository(List<AdminActionLog> saved) {
        return (AdminActionLogRepository) Proxy.newProxyInstance(
                AdminActionLogRepository.class.getClassLoader(),
                new Class<?>[]{AdminActionLogRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "save" -> {
                        saved.add((AdminActionLog) args[0]);
                        yield args[0];
                    }
                    case "saveAll" -> args[0];
                    case "findByStatusAndExpiresAtBefore" -> List.of();
                    case "countByOperatorIdAndAction", "countByTargetTypeAndTargetIdAndAction" -> 0L;
                    case "toString" -> "FakeAdminActionLogRepository";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> null;
                });
    }

    /** 与 {@code AdminActionTools.preparePromotion} 的真实返回结构一致。 */
    private static String promotionToolJson() {
        return """
                {"ok":true,"action":"PROMOTION_CREATE","targetType":"PROMOTION","targetId":null,
                 "targetLabel":"新建优惠活动 · 双十一满减",
                 "params":{"name":"双十一满减","type":"FULL_REDUCTION","threshold":"200","discount":"30",
                           "productId":null,"startTime":"2026-11-11T00:00:00","endTime":"2026-11-12T23:59:59",
                           "description":"全场满200减30"},
                 "precheck":{"ok":true,"checkedAt":"2026-10-22T14:30:12","checks":[
                            {"code":"PERMISSION","ok":true,"message":"具备 promotion:manage"},
                            {"code":"FIELD_VALID","ok":true,"message":"字段校验通过"}]},
                 "snapshot":{"name":"双十一满减","type":"FULL_REDUCTION","threshold":"200","discount":"30",
                             "productId":null,"startTime":"2026-11-11T00:00:00","endTime":"2026-11-12T23:59:59",
                             "duplicateCount":0,"duplicates":[]},
                 "fields":[{"label":"活动名称","value":"双十一满减","tone":"normal","mono":false}],
                 "risk":{"level":"LOW","reasons":[],"requiresSecondConfirm":false,
                         "secondConfirmHint":null,"irreversible":false,"note":null}}
                """;
    }

    /** 与 {@code AdminActionTools.prepareRestock} 的真实返回结构一致。 */
    private static String restockToolJson(int expectedStock) {
        return """
                {"ok":true,"action":"STOCK_REPLENISH","targetType":"PRODUCT","targetId":1,
                 "targetLabel":"补货 · 可口可乐 330ml",
                 "params":{"mode":"DELTA","delta":100,"note":"AI 对话补货"},
                 "precheck":{"ok":true,"checkedAt":"2026-10-22T14:30:12","checks":[
                            {"code":"PERMISSION","ok":true,"message":"具备 product:edit"},
                            {"code":"QTY","ok":true,"message":"补货 100 件"}]},
                 "snapshot":{"productId":1,"productName":"可口可乐 330ml","expectedStock":%d,
                             "stockBefore":%d,"stockAfter":%d,"delta":100,"unit":"瓶","threshold":10,"sales":58},
                 "fields":[{"label":"库存变化","value":"%d → %d（+100）","tone":"normal","mono":false}],
                 "risk":{"level":"LOW","reasons":[],"requiresSecondConfirm":false,
                         "secondConfirmHint":null,"irreversible":false,"note":null}}
                """.formatted(expectedStock, expectedStock, expectedStock + 100, expectedStock, expectedStock + 100);
    }
}
