package com.example.backend.ai.tools;

import com.example.backend.ai.memory.JpaChatMemoryStore;
import com.example.backend.ai.memory.LongTermMemoryStore;
import com.example.backend.dto.AddressDTO;
import com.example.backend.entity.Address;
import com.example.backend.entity.CartItem;
import com.example.backend.entity.Order;
import com.example.backend.entity.Product;
import com.example.backend.entity.Shipment;
import com.example.backend.repository.AddressRepository;
import com.example.backend.repository.CartItemRepository;
import com.example.backend.repository.OrderRepository;
import com.example.backend.repository.ProductRepository;
import com.example.backend.repository.ShipmentRepository;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolMemoryId;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 「小发」（前台购物助手）可调用的工具集：找商品 / 比参数 / 查物流 / 准备下单 / 记偏好。
 *
 * <h3>userId 是怎么进到工具里的</h3>
 * 改造前靠自定义的 {@code AiToolFunction.executeWithUser(userId, argsJson)} 把当前登录用户透传进来。
 * 现在改为 LangChain4j 的 {@link ToolMemoryId}：把参数标注为 {@code @ToolMemoryId}，
 * 框架会把当前请求的 {@code @MemoryId} 原样注入 —— <b>该参数不会出现在给模型的 Schema 里</b>，
 * 模型永远看不到、也无法伪造用户身份。这比原先「自己传 userId」更安全也更简洁。
 *
 * <h3>越权防护</h3>
 * 查物流 / 读购物车 / 下单预览一律强制校验「资源属于当前用户」，
 * 命中越权宁可整单拒绝并返回 {@code {"error":"..."}}，也不泄露他人数据。
 */
@Component
public class ShopTools {

    private static final DateTimeFormatter MINUTE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final ShipmentRepository shipmentRepository;
    private final CartItemRepository cartItemRepository;
    private final AddressRepository addressRepository;
    private final LongTermMemoryStore longTermMemoryStore;

    public ShopTools(ProductRepository productRepository,
                     OrderRepository orderRepository,
                     ShipmentRepository shipmentRepository,
                     CartItemRepository cartItemRepository,
                     AddressRepository addressRepository,
                     LongTermMemoryStore longTermMemoryStore) {
        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
        this.shipmentRepository = shipmentRepository;
        this.cartItemRepository = cartItemRepository;
        this.addressRepository = addressRepository;
        this.longTermMemoryStore = longTermMemoryStore;
    }

    // =====================================================================
    //  1. 找商品（只返回在售 + 有货，按销量降序）
    // =====================================================================

    @Tool(name = "search_user_products", value = """
            按关键词搜在售商品（自动过滤库存为 0 或已下架）。\
            输入必填 keyword（商品名片段），可选 categoryId、limit（默认 6, 最大 12）。\
            用户说『找一下 XX』『XX 类有啥在卖的』『给我推荐几款 XX』时调用。""")
    public String searchUserProducts(
            @P("搜索关键词") String keyword,
            @P(value = "类目 ID（可选）", required = false) Long categoryId,
            @P(value = "返回条数 1~12，默认 6", required = false) Integer limit) {
        try {
            String kw = keyword == null ? "" : keyword.trim();
            if (kw.isEmpty()) return "{\"error\":\"keyword 不能为空\"}";
            int size = limit == null ? 6 : Math.min(Math.max(limit, 1), 12);

            List<Product> hits = new ArrayList<>();
            for (Product p : productRepository.findByStatusOrderByIdDesc(1)) {
                if (p.getStock() == null || p.getStock() <= 0) continue;
                boolean kwHit = (p.getName() != null && p.getName().contains(kw))
                        || (p.getSubtitle() != null && p.getSubtitle().contains(kw));
                if (!kwHit) continue;
                if (categoryId != null && (p.getCategoryId() == null || !p.getCategoryId().equals(categoryId))) continue;
                hits.add(p);
                if (hits.size() > 50) break;
            }
            // 按销量降序，更接近用户真实偏好
            hits.sort((a, b) -> Integer.compare(
                    b.getSales() == null ? 0 : b.getSales(),
                    a.getSales() == null ? 0 : a.getSales()));

            StringBuilder sb = new StringBuilder("[");
            int n = 0;
            for (Product p : hits) {
                if (n >= size) break;
                if (n > 0) sb.append(",");
                sb.append("{")
                        .append("\"id\":").append(p.getId())
                        .append(",\"name\":\"").append(safe(p.getName())).append("\"")
                        .append(",\"subtitle\":\"").append(safe(p.getSubtitle())).append("\"")
                        .append(",\"price\":\"").append(money(p.getPrice())).append("\"")
                        .append(",\"originalPrice\":\"").append(money(p.getOriginalPrice())).append("\"")
                        .append(",\"stock\":").append(p.getStock() == null ? 0 : p.getStock())
                        .append(",\"sales\":").append(p.getSales() == null ? 0 : p.getSales())
                        .append(",\"unit\":\"").append(safe(p.getUnit())).append("\"")
                        .append(",\"image\":\"").append(safe(p.getImageUrl())).append("\"")
                        .append("}");
                n++;
            }
            sb.append("]");
            return "{\"count\":" + n + ",\"products\":" + sb + "}";
        } catch (Exception e) {
            return error("查询失败", e);
        }
    }

    // =====================================================================
    //  2. 比参数（行=参数名、列=商品 的二维结构，前端直接渲染表格）
    // =====================================================================

    @Tool(name = "compare_user_products", value = """
            对比若干商品的关键参数（按用户需求：名字 / 当前价 / 原价 / 库存 / 销量 / 单位）。\
            输入 productIds 数组（2~4 个商品 ID）。\
            用户问『这两个哪个划算』『这几款比一比』『XX 对比 YY』时调用。""")
    public String compareUserProducts(
            @P("商品 ID 列表，2~4 个") List<Long> productIds) {
        try {
            if (productIds == null || productIds.size() < 2) {
                return "{\"error\":\"productIds 至少 2 个\"}";
            }
            List<Long> ids = productIds.size() > 4 ? productIds.subList(0, 4) : productIds;
            List<Product> products = productRepository.findAllById(ids);
            if (products.size() < 2) return "{\"error\":\"productIds 至少 2 个有效商品\"}";

            // 字段顺序固定：名字 / 当前价 / 原价 / 库存 / 销量 / 单位
            String[] labels = {"商品", "当前价", "原价", "库存", "销量", "单位"};

            StringBuilder headers = new StringBuilder("[");
            for (int i = 0; i < products.size(); i++) {
                if (i > 0) headers.append(",");
                Product p = products.get(i);
                headers.append("{")
                        .append("\"id\":").append(p.getId())
                        .append(",\"name\":\"").append(safe(p.getName())).append("\"")
                        .append(",\"image\":\"").append(safe(p.getImageUrl())).append("\"")
                        .append("}");
            }
            headers.append("]");

            String[][] cells = new String[labels.length][products.size()];
            for (int i = 0; i < products.size(); i++) {
                Product p = products.get(i);
                cells[0][i] = p.getName() == null ? "" : p.getName();
                cells[1][i] = "¥" + money(p.getPrice());
                cells[2][i] = p.getOriginalPrice() == null ? "-" : "¥" + money(p.getOriginalPrice());
                cells[3][i] = String.valueOf(p.getStock() == null ? 0 : p.getStock());
                cells[4][i] = String.valueOf(p.getSales() == null ? 0 : p.getSales());
                cells[5][i] = p.getUnit() == null ? "" : p.getUnit();
            }

            StringBuilder rows = new StringBuilder("[");
            for (int r = 0; r < labels.length; r++) {
                if (r > 0) rows.append(",");
                rows.append("{\"label\":\"").append(labels[r]).append("\",\"values\":[");
                for (int c = 0; c < products.size(); c++) {
                    if (c > 0) rows.append(",");
                    rows.append("\"").append(safe(cells[r][c])).append("\"");
                }
                rows.append("]}");
            }
            rows.append("]");
            return "{\"headers\":" + headers + ",\"rows\":" + rows + "}";
        } catch (Exception e) {
            return error("查询失败", e);
        }
    }

    // =====================================================================
    //  3. 查物流（仅限自己的订单）
    // =====================================================================

    @Tool(name = "query_user_logistics", value = """
            查询当前登录用户自己的某个订单的物流状态。输入 orderNo（如 DR20260911...）。\
            返回承运商、运单号、发货时间、当前状态（待发货 / 运输中 / 已送达）。\
            用户问『我的 XX 订单到哪了』『快递查一下』时调用。""")
    public String queryUserLogistics(
            @ToolMemoryId String memoryKey,
            @P("订单号") String orderNo) {
        try {
            Long userId = JpaChatMemoryStore.resolveUserId(memoryKey);
            if (userId == null) return "{\"error\":\"未登录\"}";
            String no = orderNo == null ? "" : orderNo.trim();
            if (no.isEmpty()) return "{\"error\":\"orderNo 不能为空\"}";

            Order order = orderRepository.findByOrderNo(no).orElse(null);
            if (order == null) return "{\"error\":\"订单不存在\"}";
            if (order.getUserId() == null || !order.getUserId().equals(userId)) {
                // 关键安全：不让用户查到他人订单
                return "{\"error\":\"无权查看该订单\"}";
            }

            StringBuilder sb = new StringBuilder("{");
            sb.append("\"orderNo\":\"").append(safe(order.getOrderNo())).append("\"")
                    .append(",\"orderStatus\":\"").append(order.getStatus() == null ? "" : order.getStatus().name()).append("\"")
                    .append(",\"orderStatusLabel\":\"").append(order.getStatus() == null ? "" : order.getStatus().getLabel()).append("\"");
            sb.append(",\"createdAt\":\"").append(fmt(order.getCreatedAt())).append("\"")
                    .append(",\"paidAt\":\"").append(fmt(order.getPaidAt())).append("\"")
                    .append(",\"shippedAt\":\"").append(fmt(order.getShippedAt())).append("\"")
                    .append(",\"completedAt\":\"").append(fmt(order.getCompletedAt())).append("\"");

            Shipment shipment = shipmentRepository.findByOrderId(order.getId()).orElse(null);
            if (shipment != null) {
                sb.append(",\"shipment\":{")
                        .append("\"company\":\"").append(safe(shipment.getCompany())).append("\"")
                        .append(",\"trackingNo\":\"").append(safe(shipment.getTrackingNo())).append("\"")
                        .append(",\"status\":\"").append(shipment.getStatus() == null ? "" : shipment.getStatus().name()).append("\"")
                        .append(",\"statusLabel\":\"").append(shipment.getStatus() == null ? "" : shipment.getStatus().getLabel()).append("\"")
                        .append(",\"shippedAt\":\"").append(fmt(shipment.getShippedAt())).append("\"")
                        .append(",\"deliveredAt\":\"").append(fmt(shipment.getDeliveredAt())).append("\"")
                        .append(",\"remark\":\"").append(safe(shipment.getRemark())).append("\"")
                        .append("}");
            } else {
                sb.append(",\"shipment\":null");
            }
            sb.append("}");
            return sb.toString();
        } catch (Exception e) {
            return error("查询失败", e);
        }
    }

    // =====================================================================
    //  4. 读购物车（无需参数，自动识别当前用户）
    // =====================================================================

    @Tool(name = "list_user_cart", value = """
            读取当前登录用户的购物车内容（无需任何参数）。\
            返回商品名/单价/数量/小计、总价、以及各项的 cartItemId。\
            用户问『我购物车里有什么』『购物车多少钱』或准备下单前需要确认清单时调用。\
            注意：如果用户已经在说『帮我下单/结算』，可以直接调用 preview_user_order（它也会自动读购物车），不必先调本工具。""")
    public String listUserCart(@ToolMemoryId String memoryKey) {
        try {
            Long userId = JpaChatMemoryStore.resolveUserId(memoryKey);
            if (userId == null) return "{\"error\":\"未登录\"}";
            List<CartItem> items = loadCartItems(userId, null);
            if (items.isEmpty()) return "{\"error\":\"购物车是空的，先挑点商品加购吧\"}";
            return buildCartSnapshot(userId, items);
        } catch (SecurityException se) {
            return "{\"error\":\"" + safe(se.getMessage()) + "\"}";
        } catch (Exception e) {
            return error("查询失败", e);
        }
    }

    // =====================================================================
    //  5. 准备下单（只预览，绝不写库）
    // =====================================================================

    @Tool(name = "preview_user_order", value = """
            准备一份下单预览（不真实下单、不扣款）。\
            参数 cartItemIds 可以省略：省略时会自动读取当前用户购物车的全部商品（推荐用法）。\
            只有当用户明确表示『只结算其中某几件』时才传 cartItemIds。\
            返回商品名/数量/单价/小计、总价、以及最近一次下单的收货地址摘要（供复用）。\
            用户说『帮我下单』『把购物车结算一下』『这些帮我付款』时调用。\
            【重要】绝对不要向用户索要购物车项 ID，也不要让他自己去查 id —— 你直接调用本工具即可。\
            调用完后明确告诉用户：点下方卡片里的『确认下单』按钮即可创建订单（不会自动扣款）。""")
    public String previewUserOrder(
            @ToolMemoryId String memoryKey,
            @P(value = "要结算的购物车项 ID；省略则自动结算整个购物车", required = false) List<Long> cartItemIds) {
        try {
            Long userId = JpaChatMemoryStore.resolveUserId(memoryKey);
            if (userId == null) return "{\"error\":\"未登录\"}";

            // cartItemIds 可省略：省略（或空数组）时自动读取整个购物车。
            // 这是「小发」能自主下单的关键 —— 用户不必知道、也不用报购物车项 ID。
            List<Long> ids = (cartItemIds == null || cartItemIds.isEmpty()) ? null : cartItemIds;
            List<CartItem> items = loadCartItems(userId, ids);
            if (items.isEmpty()) return "{\"error\":\"购物车是空的，先挑点商品加购吧\"}";
            return buildCartSnapshot(userId, items);
        } catch (SecurityException se) {
            return "{\"error\":\"" + safe(se.getMessage()) + "\"}";
        } catch (Exception e) {
            return error("查询失败", e);
        }
    }

    // =====================================================================
    //  6. 记住用户偏好（写入长期记忆）
    // =====================================================================

    @Tool(name = "remember_user_fact", value = """
            把用户长期有效的偏好或关键信息写入长期记忆（下次对话仍能记得）。\
            输入 key（简短的记忆名，建议用 pref: 或 context: 前缀，如 pref:口味、context:收货地址）与 value（具体内容）。\
            当用户明确说『记住…』『以后都…』『我一般…』，或主动透露了稳定的收货人/电话/地址、口味与品牌偏好、忌口或过敏、常购品类时调用。\
            【不要】记录一次性的、与购物无关的琐碎内容。""")
    public String rememberUserFact(
            @ToolMemoryId String memoryKey,
            @P("记忆名，简短，建议 pref: 或 context: 前缀，如 pref:口味偏好") String key,
            @P("记忆内容，如『不吃香菜，偏好低糖』") String value) {
        try {
            Long userId = JpaChatMemoryStore.resolveUserId(memoryKey);
            if (userId == null) return "{\"error\":\"未登录\"}";
            String k = key == null ? "" : key.trim();
            String v = value == null ? "" : value.trim();
            if (k.isEmpty() || v.isEmpty()) return "{\"error\":\"key 与 value 都不能为空\"}";
            longTermMemoryStore.save(userId, k, v);
            return "{\"ok\":true,\"key\":\"" + safe(k) + "\"}";
        } catch (Exception e) {
            return error("保存记忆失败", e);
        }
    }

    // =====================================================================
    //  共用辅助
    // =====================================================================

    /**
     * 读取购物车项。
     *
     * @param userId 当前登录用户
     * @param ids    指定要读取的购物车项 ID；为 {@code null} 或空表示「整车」
     * @throws SecurityException 指定了 ids 但其中某项不属于该用户时（横向越权，整单拒绝）
     */
    private List<CartItem> loadCartItems(Long userId, List<Long> ids) {
        if (userId == null) return List.of();
        if (ids == null || ids.isEmpty()) {
            List<CartItem> all = cartItemRepository.findByUserIdOrderByIdDesc(userId);
            return all == null ? List.of() : all;
        }
        List<CartItem> picked = new ArrayList<>(ids.size());
        for (Long cid : ids) {
            if (cid == null) continue;
            CartItem ci = cartItemRepository.findById(cid).orElse(null);
            if (ci == null) continue;
            if (!userId.equals(ci.getUserId())) {
                throw new SecurityException("购物车项不属于当前账号");
            }
            picked.add(ci);
        }
        return picked;
    }

    /**
     * 组装订单预览快照（<b>不写库、不创建订单</b>）。
     * <p>
     * {@code list_user_cart} 与 {@code preview_user_order} 共用同一结构，前端复用同一张卡片：
     * <pre>
     * { totalCount, totalAmount, items[], cartItemIds[], defaultAddress | null }
     * </pre>
     * 其中 {@code defaultAddress} 取<b>地址簿里的默认地址</b>，与收银台
     * （{@code PayActivity.prefillDefaultAddress} → {@code GET /api/addresses/default}）
     * 同源 —— 保证「卡片上确认的地址」就是「真正下单会用的地址」；无默认地址时为 {@code null}。
     */
    private String buildCartSnapshot(Long userId, List<CartItem> items) {
        BigDecimal total = BigDecimal.ZERO;
        StringBuilder itemsJson = new StringBuilder("[");
        StringBuilder idsJson = new StringBuilder("[");
        int count = 0;
        for (CartItem ci : items) {
            BigDecimal price = ci.getPrice() == null ? BigDecimal.ZERO : ci.getPrice();
            int qty = ci.getQuantity() == null ? 0 : ci.getQuantity();
            BigDecimal subtotal = price.multiply(BigDecimal.valueOf(qty));
            total = total.add(subtotal);
            if (count > 0) {
                itemsJson.append(",");
                idsJson.append(",");
            }
            itemsJson.append("{")
                    .append("\"cartItemId\":").append(ci.getId())
                    .append(",\"productId\":").append(ci.getProductId())
                    .append(",\"productName\":\"").append(safe(ci.getProductName())).append("\"")
                    .append(",\"productImage\":\"").append(safe(ci.getProductImage())).append("\"")
                    .append(",\"price\":\"").append(money(price)).append("\"")
                    .append(",\"quantity\":").append(qty)
                    .append(",\"subtotal\":\"").append(money(subtotal)).append("\"")
                    .append("}");
            idsJson.append(ci.getId());
            count++;
        }
        itemsJson.append("]");
        idsJson.append("]");

        // 收货地址取「地址簿默认地址」，而不是「最近一次下单用过的地址」。
        //
        // 为什么不复用历史订单：收银台（PayActivity.prefillDefaultAddress）是用
        // GET /api/addresses/default 回填收货人/电话/地址的。若预览卡取历史订单、收银台取地址簿，
        // 用户可能在卡片上确认了 A 地址、点下去却按 B 地址下单 —— 显示与实际不一致。
        // 两边同源之后，卡片上看到的就是「点确认后真正会用的那一份」。
        Address defaultAddress = addressRepository.findByUserIdAndIsDefaultTrue(userId).orElse(null);

        StringBuilder resp = new StringBuilder("{");
        resp.append("\"totalCount\":").append(count)
                .append(",\"totalAmount\":\"").append(money(total)).append("\"")
                .append(",\"items\":").append(itemsJson)
                .append(",\"cartItemIds\":").append(idsJson);
        if (defaultAddress != null) {
            // fullAddress 复用 AddressDTO.join()，与 /api/addresses/default 的拼法保持一致
            resp.append(",\"defaultAddress\":{")
                    .append("\"name\":\"").append(safe(defaultAddress.getReceiverName())).append("\"")
                    .append(",\"phone\":\"").append(safe(defaultAddress.getReceiverPhone())).append("\"")
                    .append(",\"address\":\"")
                    .append(safe(AddressDTO.join(defaultAddress.getProvince(), defaultAddress.getDetail())))
                    .append("\"")
                    .append("}");
        } else {
            // 没有默认地址：收银台同样留空让用户当场填，这里也不编造
            resp.append(",\"defaultAddress\":null");
        }
        resp.append("}");
        return resp.toString();
    }

    private static String fmt(java.time.LocalDateTime t) {
        return t == null ? "" : t.format(MINUTE_FMT);
    }

    private static String money(BigDecimal v) {
        return v == null ? "0.00" : v.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    /** 内联 JSON 文本转义：把双引号换成单引号并防 null，避免手工拼串出现非法 JSON。 */
    private static String safe(String s) {
        return s == null ? "" : s.replace("\"", "'");
    }

    private static String error(String prefix, Exception e) {
        String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        return "{\"error\":\"" + prefix + ": " + safe(msg) + "\"}";
    }
}
