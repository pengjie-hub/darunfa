package com.example.backend.ai.action;

import com.example.backend.ai.context.AdminActionContext;
import com.example.backend.dto.ActionProposal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ActionCatalog} 与 {@link AdminActionContext} 的纯单元测试（不起 Spring、不连库）。
 *
 * <h3>为什么这两条必须有单测，而不是「人工点一下」</h3>
 * {@code ActionCatalog} 是整套「模型无写权限」的安全命门，它一旦退化（比如未注册动作返回 null
 * 而不是抛异常），漏洞是静默的：页面看起来正常，卡片照样渲染，只是能被诱导着拼出别的接口。
 * 因此「未注册动作必须抛异常」与「path 只能由模板 + targetId 渲染」这两条用测试钉住。
 */
class ActionCatalogTest {

    private final ActionCatalog catalog = new ActionCatalog();

    // =====================================================================
    //  安全命门
    // =====================================================================

    @Test
    @DisplayName("6 个 P0 动作的 confirm.path 全部以 /api/ 开头，且 targetId 正确内插")
    void renderConfirmPathsAreApiPrefixed() {
        Map<AdminAction, Long> p0 = new LinkedHashMap<>();
        p0.put(AdminAction.REFUND_APPROVE, 8L);
        p0.put(AdminAction.REFUND_REJECT, 8L);
        p0.put(AdminAction.RISK_SCAN, 101L);
        p0.put(AdminAction.STOCK_REPLENISH, 1L);
        p0.put(AdminAction.PROMOTION_CREATE, null);
        p0.put(AdminAction.ORDER_SHIP, 202L);

        for (Map.Entry<AdminAction, Long> e : p0.entrySet()) {
            AdminAction action = e.getKey();
            Long targetId = e.getValue();
            Map<String, Object> params = paramsFor(action);
            Map<String, Object> snapshot = snapshotFor(action);

            ActionProposal.Confirm confirm = catalog.renderConfirm(action, targetId, params, snapshot);

            assertNotNull(confirm, "renderConfirm 绝不能返回 null: " + action);
            assertNotNull(confirm.path(), "path 不能为空: " + action);
            assertTrue(confirm.path().startsWith("/api/"), "path 必须以 /api/ 开头: " + confirm.path());
            assertTrue(confirm.requiresHeader().containsKey("Idempotency-Key"),
                    "confirm 必须带 Idempotency-Key: " + action);
            if (targetId != null) {
                assertTrue(confirm.path().contains(String.valueOf(targetId)),
                        "path 必须内插 targetId: " + confirm.path());
            }
            // 路径里不允许出现任何未渲染的占位符
            assertFalse(confirm.path().contains("{"), "path 不能被残留占位符: " + confirm.path());
        }
    }

    @Test
    @DisplayName("未注册动作（含 P1 批量）renderConfirm 必须抛异常，绝不返回 null")
    void unregisteredActionThrows() {
        IllegalArgumentException batch = assertThrows(IllegalArgumentException.class,
                () -> catalog.renderConfirm(AdminAction.RISK_SCAN_BATCH, 1L, Map.of(), Map.of()));
        assertTrue(batch.getMessage().contains("未注册的动作"), batch.getMessage());

        assertThrows(IllegalArgumentException.class,
                () -> catalog.renderConfirm(AdminAction.ORDER_SHIP_BATCH, 1L, Map.of(), Map.of()));

        assertThrows(IllegalArgumentException.class,
                () -> catalog.renderConfirm(null, 1L, Map.of(), Map.of()));

        assertFalse(catalog.isRegistered(AdminAction.RISK_SCAN_BATCH));
        assertTrue(catalog.isRegistered(AdminAction.ORDER_SHIP));
    }

    @Test
    @DisplayName("需要 targetId 的动作，targetId 缺失/非法时必须抛异常（不允许拼出 /refunds//approve）")
    void invalidTargetIdThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> catalog.renderConfirm(AdminAction.REFUND_APPROVE, null, Map.of(), Map.of()));
        assertThrows(IllegalArgumentException.class,
                () -> catalog.renderConfirm(AdminAction.REFUND_APPROVE, 0L, Map.of(), Map.of()));
    }

    @Test
    @DisplayName("补货幂等键含 expectedStock：(1,7,100) 与 (1,107,100) 不同，重复调用相同")
    void restockIdempotencyKeyDependsOnExpectedStock() {
        Map<String, Object> params = Map.of("delta", 100);

        String first = catalog.renderIdempotencyKey(AdminAction.STOCK_REPLENISH, 1L, params,
                Map.of("expectedStock", 7));
        String again = catalog.renderIdempotencyKey(AdminAction.STOCK_REPLENISH, 1L, params,
                Map.of("expectedStock", 7));
        String afterRestock = catalog.renderIdempotencyKey(AdminAction.STOCK_REPLENISH, 1L, params,
                Map.of("expectedStock", 107));

        assertEquals("restock:1:7:100", first);
        assertEquals(first, again, "同一快照下重复调用必须得到同一个键（幂等的前提）");
        assertNotEquals(first, afterRestock, "库存快照变了，键必须不同（否则第二次合法补货会被误拦）");
        assertEquals("restock:1:107:100", afterRestock);
    }

    @Test
    @DisplayName("新建活动没有 targetId：path 无占位符，幂等键是内容指纹（同内容相同、改内容不同）")
    void promotionHasNoTargetIdAndUsesContentDigest() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name", "双十一满减");
        params.put("type", "FULL_REDUCTION");
        params.put("threshold", "200.00");
        params.put("discount", "30");
        params.put("productId", null);
        params.put("startTime", "2026-11-11T00:00:00");
        params.put("endTime", "2026-11-12T23:59:59");

        ActionProposal.Confirm confirm = catalog.renderConfirm(AdminAction.PROMOTION_CREATE, null, params, Map.of());
        assertEquals("/api/admin/promotions", confirm.path());
        assertEquals("POST", confirm.method());
        assertNotNull(confirm.body(), "新建活动需要 body");

        String key1 = catalog.renderIdempotencyKey(AdminAction.PROMOTION_CREATE, null, params, Map.of());
        String key2 = catalog.renderIdempotencyKey(AdminAction.PROMOTION_CREATE, null, params, Map.of());
        assertEquals(key1, key2);

        Map<String, Object> changed = new LinkedHashMap<>(params);
        changed.put("discount", "50");
        String key3 = catalog.renderIdempotencyKey(AdminAction.PROMOTION_CREATE, null, changed, Map.of());
        assertNotEquals(key1, key3, "优惠值变了，内容指纹必须变");
        assertTrue(key1.startsWith("promotion:"));
    }

    @Test
    @DisplayName("bodyBuilder 只取白名单字段：模型多塞的参数进不了 body")
    void shipBodyOnlyWhitelisted() {
        Map<String, Object> params = new HashMap<>();
        params.put("company", "顺丰速运");
        params.put("trackingNo", "SF1234567890");
        params.put("remark", "放门口");
        params.put("orderId", 999L);        // 模型多塞的键
        params.put("status", "COMPLETED");  // 模型多塞的键

        ActionProposal.Confirm confirm = catalog.renderConfirm(AdminAction.ORDER_SHIP, 202L, params, Map.of());
        assertEquals("/api/admin/orders/202/ship", confirm.path());
        assertEquals("PUT", confirm.method());

        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) confirm.body();
        assertEquals(Set.of("company", "trackingNo", "remark"), body.keySet(),
                "body 只允许白名单字段，多塞的 orderId/status 必须被丢弃");
        assertEquals("SF1234567890", body.get("trackingNo"));
    }

    @Test
    @DisplayName("退款二次确认阈值：payAmount ≥ 500 才要求二次确认，且阈值由服务端算")
    void refundSecondConfirmThreshold() {
        assertFalse(catalog.requiresSecondConfirm(AdminAction.REFUND_APPROVE, 8L,
                Map.of("decision", "APPROVE"), Map.of("payAmount", "199.00")));
        assertTrue(catalog.requiresSecondConfirm(AdminAction.REFUND_APPROVE, 8L,
                Map.of("decision", "APPROVE"), Map.of("payAmount", "568.00")));
        assertTrue(catalog.secondConfirmHint(AdminAction.REFUND_APPROVE).contains("后 4 位"));
    }

    @Test
    @DisplayName("退款卡片带反向操作；非退款动作为 null")
    void reverseConfirmOnlyForRefund() {
        ActionProposal.Confirm reverse = catalog.renderReverse(AdminAction.REFUND_APPROVE, 8L,
                Map.of("decision", "APPROVE"), Map.of("payAmount", "199.00"));
        assertNotNull(reverse);
        assertEquals("/api/admin/refunds/8/reject", reverse.path());

        assertNull(catalog.renderReverse(AdminAction.ORDER_SHIP, 202L, Map.of(), Map.of()));
    }

    @Test
    @DisplayName("paramsDigest 与键顺序无关（否则运营没改参数也会被判 PROPOSAL_MISMATCH）")
    void paramsDigestIsOrderInsensitive() {
        Map<String, Object> a = new LinkedHashMap<>();
        a.put("a", 1);
        a.put("b", "2");
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("b", "2");
        b.put("a", 1);

        assertEquals(ActionCatalog.paramsDigest(a), ActionCatalog.paramsDigest(b));
        assertNotEquals(ActionCatalog.paramsDigest(a), ActionCatalog.paramsDigest(Map.of("a", 2, "b", "2")));
        assertNotEquals(ActionCatalog.paramsDigest(Map.of("discount", "0.80")),
                ActionCatalog.paramsDigest(Map.of("discount", "0.81")));
    }

    // =====================================================================
    //  身份上下文
    // =====================================================================

    @Test
    @DisplayName("bind → current → unbind 在同一线程有效，unbind 后为空；线程复跑不串")
    void contextLifecycle() {
        assertTrue(AdminActionContext.current().isEmpty(), "测试线程初始不应有上下文");

        try (AdminActionContext ctx = AdminActionContext.bind(
                2L, "ADMIN", Set.of("order:view", "risk:scan"), AdminActionContext.ENTRY_CHAT, 77L, "127.0.0.1", "junit")) {
            Optional<AdminActionContext> current = AdminActionContext.current();
            assertTrue(current.isPresent());
            assertEquals(2L, current.get().operatorId());
            assertEquals(AdminActionContext.ENTRY_CHAT, current.get().entry());
            assertEquals(77L, current.get().chatMessageId());
            assertTrue(current.get().hasAnyPerm("order:view", "cs:manage"), "OR 语义：命中其一即通过");
            assertFalse(current.get().hasAnyPerm("promotion:manage"), "未授予的码必须为 false");
            assertTrue(ctx.hasAllPerms("order:view", "risk:scan"));
        }
        assertTrue(AdminActionContext.current().isEmpty(), "try-with-resources 关闭后必须解绑");
    }

    @Test
    @DisplayName("线程复用 3 次：每次 bind 只影响自己那一轮，不会把身份漏给下一轮")
    void contextDoesNotLeakAcrossThreadReuse() throws Exception {
        AtomicReference<Exception> failure = new AtomicReference<>();
        AtomicBoolean anyRoundSawPrevious = new AtomicBoolean(false);

        Thread worker = new Thread(() -> {
            try {
                for (int i = 0; i < 3; i++) {
                    if (AdminActionContext.current().isPresent()) {
                        anyRoundSawPrevious.set(true);
                    }
                    try (AdminActionContext ctx = AdminActionContext.bind((long) (i + 1), "OPS",
                            Set.of("product:edit"), AdminActionContext.ENTRY_CHAT, null, null, null)) {
                        assertEquals((long) (i + 1), ctx.operatorId());
                        assertEquals((long) (i + 1), AdminActionContext.current().orElseThrow().operatorId());
                    }
                    if (AdminActionContext.current().isPresent()) {
                        anyRoundSawPrevious.set(true);
                    }
                }
            } catch (Exception e) {
                failure.set(e);
            }
        });
        worker.start();
        worker.join();

        assertNull(failure.get(), () -> "工作线程内断言失败: " + failure.get());
        assertFalse(anyRoundSawPrevious.get(), "上一轮的身份绝不能泄漏到下一轮（线程池复用场景）");
    }

    @Test
    @DisplayName("权限判定只看权限码，不认角色名（ADMIN 也不能绕过）")
    void hasPermissionIgnoresRoleName() {
        assertFalse(catalog.hasPermission(AdminAction.ORDER_SHIP, Set.of("order:view")));
        assertTrue(catalog.hasPermission(AdminAction.ORDER_SHIP, Set.of("order:ship")));
        // 退款是 OR 语义：order:view 或 cs:manage 均可
        assertTrue(catalog.hasPermission(AdminAction.REFUND_APPROVE, Set.of("cs:manage")));
        assertFalse(catalog.hasPermission(AdminAction.REFUND_APPROVE, Set.of()));
    }

    @Test
    @DisplayName("构造期自检：所有注册动作的模板合法（不抛异常即通过）")
    void catalogSelfCheckPasses() {
        assertDoesNotThrow(ActionCatalog::new);
        assertEquals(6, catalog.registeredActions().size(), "本期只注册 6 个 P0 动作，P1 批量刻意不注册");
        for (AdminAction action : List.of(AdminAction.values())) {
            if (!catalog.isRegistered(action)) continue;
            assertTrue(catalog.permissionCodes(action).length > 0);
            assertTrue(catalog.renderConfirm(action, action == AdminAction.PROMOTION_CREATE ? null : 1L,
                    paramsFor(action), snapshotFor(action)).path().startsWith("/api/"));
        }
    }

    // =====================================================================
    //  测试数据
    // =====================================================================

    private static Map<String, Object> paramsFor(AdminAction action) {
        return switch (action) {
            case REFUND_APPROVE -> Map.of("decision", "APPROVE");
            case REFUND_REJECT -> Map.of("decision", "REJECT", "rejectReason", "商品已发出");
            case RISK_SCAN -> Map.of("orderNo", "DR20261008120034123", "mode", "SINGLE");
            case STOCK_REPLENISH -> Map.of("delta", 100, "mode", "DELTA");
            case PROMOTION_CREATE -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("name", "双十一满减");
                m.put("type", "FULL_REDUCTION");
                m.put("threshold", "200");
                m.put("discount", "30");
                yield m;
            }
            case ORDER_SHIP -> Map.of("company", "顺丰速运", "trackingNo", "SF1234567890");
            default -> Map.of();
        };
    }

    private static Map<String, Object> snapshotFor(AdminAction action) {
        return switch (action) {
            case REFUND_APPROVE, REFUND_REJECT -> Map.of("payAmount", "199.00");
            case STOCK_REPLENISH -> Map.of("expectedStock", 7, "stockAfter", 107);
            default -> Map.of();
        };
    }
}
