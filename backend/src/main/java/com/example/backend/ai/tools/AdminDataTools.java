package com.example.backend.ai.tools;

import com.example.backend.entity.Order;
import com.example.backend.entity.OrderStatus;
import com.example.backend.entity.Product;
import com.example.backend.entity.Promotion;
import com.example.backend.entity.User;
import com.example.backend.repository.OrderRepository;
import com.example.backend.repository.ProductRepository;
import com.example.backend.repository.PromotionRepository;
import com.example.backend.repository.ReviewRepository;
import com.example.backend.repository.UserRepository;
import com.example.backend.service.AdminService;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 「小运」（后台运营助手）可调用的只读数据工具集。
 *
 * <h3>从手写 Schema 到注解</h3>
 * 改造前每个工具都要实现 {@code name()} / {@code description()} / {@code parameters()}
 * 三个方法，用 JSON 手工拼出 OpenAI Function Calling 的 Schema（全部 8 个工具合计 645 行）。
 * 现在改为在方法上标注 {@link Tool} 与 {@link P}，<b>Schema 由 LangChain4j 反射生成</b>，
 * 方法签名即是契约 —— 改参数只改签名，不会再出现「Schema 与实际解析代码不一致」的漂移。
 *
 * <h3>约束</h3>
 * <ul>
 *   <li><b>全部只读</b>：没有任何写库操作，满足系统提示中「严禁写操作」的承诺；</li>
 *   <li><b>结果紧凑</b>：返回 JSON 字符串，明细最多 5~30 条，避免 prompt 膨胀；</li>
 *   <li><b>失败不抛异常</b>：统一返回 {@code {"error":"..."}}，让模型有机会向用户口语化解释，
 *       而不是让整条对话链路中断。</li>
 * </ul>
 */
@Component
public class AdminDataTools {

    private static final DateTimeFormatter MINUTE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final Pattern LAST_N_DAYS = Pattern.compile("^LAST_(\\d+)_DAYS?$");

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final PromotionRepository promotionRepository;
    private final ReviewRepository reviewRepository;
    private final AdminService adminService;

    public AdminDataTools(OrderRepository orderRepository,
                          ProductRepository productRepository,
                          UserRepository userRepository,
                          PromotionRepository promotionRepository,
                          ReviewRepository reviewRepository,
                          AdminService adminService) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
        this.promotionRepository = promotionRepository;
        this.reviewRepository = reviewRepository;
        this.adminService = adminService;
    }

    // =====================================================================
    //  1. 订单聚合统计
    // =====================================================================

    @Tool(name = "query_order_stats", value = """
            查询订单统计。输入可选 status(枚举值之一：PENDING_PAYMENT/待付款 / PAID/待发货 / \
            SHIPPED/待收货 / COMPLETED/已完成 / CANCELLED/已取消)、scope(时间范围) 与 date(指定某一天)。\
            scope 取值：TODAY=今天(默认，自然日 00:00 起) / YESTERDAY=昨天 / LAST_7_DAYS=近 7 个自然日(含今天) / \
            LAST_30_DAYS=近 30 个自然日(含今天) / ALL=全部时段。\
            date 格式 yyyy-MM-dd，用于精确查询某一天（优先级最高，如『9月12日卖了多少』就传 date=2026-09-12）。\
            返回订单数量与总销售额。用户问『今天/今日 GMV』『最新日销售额』时用默认的 TODAY；\
            问『这周成交多少』『这月 GMV』『待发货有多少单』时传对应 scope。""")
    public String queryOrderStats(
            @P(value = "订单状态枚举名或中文，不传=全部", required = false) String status,
            @P(value = "时间范围：TODAY(默认)/YESTERDAY/LAST_7_DAYS/LAST_30_DAYS/ALL", required = false) String scope,
            @P(value = "指定自然日，格式 yyyy-MM-dd（优先于 scope/days）；用于查询「某一天」的销售额", required = false) String date,
            @P(value = "（兼容旧入参）近 N 个自然日，含今天；等价于 LAST_N_DAYS。优先使用 scope。", required = false) Integer days) {
        try {
            // ---- 时间窗：一律按「自然日」切分，不用 now()-N 天的滑动 24 小时 ----
            // 滑动窗口会把昨天晚上的订单算进「今天」，这正是仪表盘与小运对不上的原因之一。
            LocalDate today = LocalDate.now();
            LocalDate fromDate;
            LocalDate toDate = today;
            String scopeName;

            LocalDate dateOnly = parseDate(date);
            if (dateOnly != null) {
                fromDate = dateOnly;
                toDate = dateOnly;
                scopeName = "DATE";
            } else {
                String scopeArg = (scope == null || scope.isBlank()) ? null : scope.trim().toUpperCase();
                if (scopeArg == null && days != null) {
                    scopeArg = "LAST_" + Math.max(1, days) + "_DAYS";
                }
                if (scopeArg == null) scopeArg = "TODAY";

                switch (scopeArg) {
                    case "ALL" -> fromDate = null;
                    case "YESTERDAY" -> {
                        fromDate = today.minusDays(1);
                        toDate = fromDate;
                    }
                    case "LAST_7_DAYS" -> fromDate = today.minusDays(6);
                    case "LAST_30_DAYS" -> fromDate = today.minusDays(29);
                    case "TODAY" -> fromDate = today;
                    default -> {
                        Matcher m = LAST_N_DAYS.matcher(scopeArg);
                        if (m.matches()) {
                            fromDate = today.minusDays(Math.max(1, Integer.parseInt(m.group(1))) - 1L);
                        } else {
                            fromDate = today;
                            scopeArg = "TODAY";
                        }
                    }
                }
                scopeName = scopeArg;
            }

            LocalDateTime after = fromDate == null ? null : fromDate.atStartOfDay();
            LocalDateTime before = toDate.plusDays(1).atStartOfDay(); // 次日 00:00，开区间上界

            OrderStatus st = parseStatus(status);
            // 统一口径：钱已收到且未退回（见 AdminService.EFFECTIVE_SALES_STATUSES）
            Set<OrderStatus> effective = AdminService.EFFECTIVE_SALES_STATUSES;

            List<Order> orders = (st != null)
                    ? orderRepository.findByStatusOrderByCreatedAtDesc(st)
                    : orderRepository.findAll();

            int orderCount = 0;
            BigDecimal sum = BigDecimal.ZERO;
            for (Order o : orders) {
                LocalDateTime created = o.getCreatedAt();
                if (created == null) continue;
                if (after != null && created.isBefore(after)) continue;
                if (created.isAfter(before) || created.isEqual(before)) continue;
                orderCount++;
                if (o.getPayAmount() != null && effective.contains(o.getStatus())) {
                    sum = sum.add(o.getPayAmount());
                }
            }

            String rangeLabel = (fromDate == null)
                    ? "全部时段"
                    : (fromDate.equals(toDate) ? fromDate.toString() : fromDate + " 至 " + toDate);

            return "{\"orderCount\":" + orderCount
                    + ",\"totalSales\":\"" + sum.setScale(2, RoundingMode.HALF_UP) + "\""
                    + ",\"scope\":\"" + scopeName + "\""
                    + ",\"range\":\"" + rangeLabel + "\""
                    + ",\"salesScope\":\"已收款且未退款(" + effective.size() + "种状态)\""
                    + ",\"status\":\"" + (st == null ? "ALL" : st.name()) + "\"}";
        } catch (Exception e) {
            return error(e);
        }
    }

    // =====================================================================
    //  2. 最近订单列表
    // =====================================================================

    @Tool(name = "list_recent_orders", value = """
            列出最近的订单。输入可选 status(枚举或中文) 与 limit(默认 10, 最大 30)。\
            返回订单号、用户ID、金额、状态、创建时间。用户问『最近几单』『待发货订单有哪些』时调用。""")
    public String listRecentOrders(
            @P(value = "订单状态，可不传=全部", required = false) String status,
            @P(value = "返回条数(1~30, 默认 10)", required = false) Integer limit) {
        try {
            int size = limit == null ? 10 : Math.min(Math.max(limit, 1), 30);
            OrderStatus st = parseStatus(status);
            List<Order> orders = (st != null)
                    ? orderRepository.findByStatusOrderByCreatedAtDesc(st)
                    : orderRepository.findAll();

            StringBuilder sb = new StringBuilder("[");
            int count = 0;
            for (Order o : orders) {
                if (count >= size) break;
                if (count > 0) sb.append(",");
                sb.append("{")
                        .append("\"orderNo\":\"").append(safe(o.getOrderNo())).append("\"")
                        .append(",\"userId\":").append(o.getUserId() == null ? 0 : o.getUserId())
                        .append(",\"amount\":\"").append(money(o.getPayAmount())).append("\"")
                        .append(",\"status\":\"").append(o.getStatus() == null ? "" : o.getStatus().name()).append("\"")
                        .append(",\"createdAt\":\"").append(o.getCreatedAt() == null ? "" : o.getCreatedAt().format(MINUTE_FMT)).append("\"")
                        .append("}");
                count++;
            }
            sb.append("]");
            return "{\"count\":" + count + ",\"orders\":" + sb + "}";
        } catch (Exception e) {
            return error(e);
        }
    }

    // =====================================================================
    //  3. 商品搜索（后台视角：含下架与零库存）
    // =====================================================================

    @Tool(name = "search_products", value = """
            按关键词搜索商品。输入必填 keyword(商品名/副标题片段)，可选 categoryId(类目ID)、limit(默认 8, 最大 20)。\
            用户问『有没有卖XX的』『XX类目有哪几款』时调用。""")
    public String searchProducts(
            @P("搜索关键词") String keyword,
            @P(value = "类目ID", required = false) Long categoryId,
            @P(value = "返回条数(1~20, 默认 8)", required = false) Integer limit) {
        try {
            String kw = keyword == null ? "" : keyword.trim();
            if (kw.isEmpty()) return "{\"error\":\"keyword 不能为空\"}";
            int size = limit == null ? 8 : Math.min(Math.max(limit, 1), 20);

            List<Product> products;
            if (categoryId != null) {
                products = productRepository.findByCategoryIdAndStatusOrderByIdDesc(categoryId, 1);
                products.removeIf(p -> !hitsKeyword(p, kw));
            } else {
                products = new ArrayList<>();
                for (Product p : productRepository.findByStatusOrderByIdDesc(1)) {
                    if (hitsKeyword(p, kw)) products.add(p);
                    if (products.size() >= size) break;
                }
            }

            StringBuilder sb = new StringBuilder("[");
            int count = 0;
            for (Product p : products) {
                if (count >= size) break;
                if (count > 0) sb.append(",");
                sb.append("{")
                        .append("\"id\":").append(p.getId())
                        .append(",\"name\":\"").append(safe(p.getName())).append("\"")
                        .append(",\"price\":\"").append(money(p.getPrice())).append("\"")
                        .append(",\"stock\":").append(p.getStock() == null ? 0 : p.getStock())
                        .append(",\"sales\":").append(p.getSales() == null ? 0 : p.getSales())
                        .append("}");
                count++;
            }
            sb.append("]");
            return "{\"count\":" + count + ",\"products\":" + sb + "}";
        } catch (Exception e) {
            return error(e);
        }
    }

    // =====================================================================
    //  4. 低库存预警
    // =====================================================================

    @Tool(name = "list_low_stock_products", value =
            "列出当前库存低于预警阈值的商品。用户问『哪些商品快没货了』『库存预警』时调用。")
    public String listLowStockProducts(
            @P(value = "返回条数, 默认 20", required = false) Integer limit) {
        try {
            int size = limit == null ? 20 : Math.min(Math.max(limit, 1), 50);
            int threshold = AdminService.LOW_STOCK_THRESHOLD;
            List<Product> items = adminService.listLowStockProducts();

            StringBuilder sb = new StringBuilder("[");
            int count = 0;
            for (Product p : items) {
                if (count >= size) break;
                if (count > 0) sb.append(",");
                int stock = p.getStock() == null ? 0 : p.getStock();
                sb.append("{")
                        .append("\"id\":").append(p.getId())
                        .append(",\"name\":\"").append(safe(p.getName())).append("\"")
                        .append(",\"stock\":").append(stock)
                        .append(",\"threshold\":").append(threshold)
                        .append(",\"shortage\":").append(Math.max(0, threshold - stock))
                        .append("}");
                count++;
            }
            sb.append("]");
            return "{\"threshold\":" + threshold + ",\"count\":" + count + ",\"products\":" + sb + "}";
        } catch (Exception e) {
            return error(e);
        }
    }

    // =====================================================================
    //  5. 单商品库存 / 价格
    // =====================================================================

    @Tool(name = "get_product_stock", value = """
            根据商品名查询某商品的当前库存与价格。输入必填 keyword(商品名片段)。\
            用户问『XX还有多少库存』『XX多少钱』时调用。""")
    public String getProductStock(@P("商品名片段") String keyword) {
        try {
            String kw = keyword == null ? "" : keyword.trim();
            if (kw.isEmpty()) return "{\"error\":\"keyword 不能为空\"}";

            List<Product> hits = new ArrayList<>();
            for (Product p : productRepository.findByStatusOrderByIdDesc(1)) {
                if (p.getName() != null && p.getName().contains(kw)) {
                    hits.add(p);
                    if (hits.size() >= 5) break;
                }
            }
            if (hits.isEmpty()) return "{\"count\":0,\"products\":[]}";

            StringBuilder sb = new StringBuilder("[");
            int i = 0;
            for (Product p : hits) {
                if (i > 0) sb.append(",");
                sb.append("{")
                        .append("\"id\":").append(p.getId())
                        .append(",\"name\":\"").append(safe(p.getName())).append("\"")
                        .append(",\"price\":\"").append(money(p.getPrice())).append("\"")
                        .append(",\"stock\":").append(p.getStock() == null ? 0 : p.getStock())
                        .append(",\"sales\":").append(p.getSales() == null ? 0 : p.getSales())
                        .append("}");
                i++;
            }
            sb.append("]");
            return "{\"count\":" + hits.size() + ",\"products\":" + sb + "}";
        } catch (Exception e) {
            return error(e);
        }
    }

    // =====================================================================
    //  6. 会员统计
    // =====================================================================

    @Tool(name = "get_user_stats", value = """
            查询会员统计。用户问『有多少用户』『有多少管理员』『谁是管理员』时调用。\
            返回总用户数、近 7 天新增、管理员数、几个示例用户。""")
    public String getUserStats() {
        try {
            long total = userRepository.count();
            long adminCount = 0;
            long recent7 = 0;
            LocalDateTime sevenAgo = LocalDateTime.now().minusDays(7);

            StringBuilder samples = new StringBuilder("[");
            int sampleCount = 0;
            for (User u : userRepository.findAll()) {
                if (u.getRole() != null && "ADMIN".equals(u.getRole().name())) adminCount++;
                if (u.getCreatedAt() != null && u.getCreatedAt().isAfter(sevenAgo)) recent7++;
                if (sampleCount < 5) {
                    if (sampleCount > 0) samples.append(",");
                    samples.append("{\"id\":").append(u.getId())
                            .append(",\"username\":\"").append(safe(u.getUsername())).append("\"")
                            .append(",\"role\":\"").append(u.getRole() == null ? "" : u.getRole().name()).append("\"")
                            .append("}");
                    sampleCount++;
                }
            }
            samples.append("]");
            return "{\"totalUsers\":" + total
                    + ",\"adminCount\":" + adminCount
                    + ",\"recent7DaysNew\":" + recent7
                    + ",\"samples\":" + samples + "}";
        } catch (Exception e) {
            return error(e);
        }
    }

    // =====================================================================
    //  7. 评论统计
    // =====================================================================

    /**
     * 评论统计。
     * <p>
     * <b>顺带修掉一个旧缺陷</b>：改造前的实现里 {@code ReviewRepository} 恒为 {@code null}
     * （构造函数收下两个参数却赋 null），导致该工具永远返回
     * {@code {"total":0,...,"note":"评论仓储未注入(简化版)"}} —— 对管理员来说等于「评论数永远是 0」。
     * 现在从容器正常注入，并按描述补齐「最近 7 天新增」与示例评论。
     */
    @Tool(name = "get_review_stats", value = """
            查询评论统计概览。返回总评论数、最近 7 天新增评论数、最近评论的几个示例。\
            用户问『最近用户怎么评价的』『评论怎么样』『有多少评论』时调用。""")
    public String getReviewStats() {
        try {
            var all = reviewRepository.findAll();
            long total = all.size();
            LocalDateTime sevenAgo = LocalDateTime.now().minusDays(7);
            long recent7 = all.stream()
                    .filter(r -> r.getCreatedAt() != null && r.getCreatedAt().isAfter(sevenAgo))
                    .count();

            // 最近 5 条评论的摘要（按 id 倒序即最新在前）
            List<?> recent = all.stream()
                    .sorted((a, b) -> Long.compare(idOf(b), idOf(a)))
                    .limit(5)
                    .toList();
            StringBuilder samples = new StringBuilder("[");
            int i = 0;
            for (Object o : recent) {
                var r = (com.example.backend.entity.Review) o;
                if (i > 0) samples.append(",");
                samples.append("{\"rating\":").append(r.getRating() == null ? 0 : r.getRating())
                        .append(",\"content\":\"").append(safe(truncate(r.getContent(), 60))).append("\"")
                        .append(",\"sentiment\":\"").append(safe(r.getAiSentiment())).append("\"")
                        .append("}");
                i++;
            }
            samples.append("]");
            return "{\"total\":" + total + ",\"recent7days\":" + recent7 + ",\"samples\":" + samples + "}";
        } catch (Exception e) {
            return error(e);
        }
    }

    // =====================================================================
    //  8. 生效中的促销
    // =====================================================================

    @Tool(name = "list_active_promotions", value =
            "列出当前生效中的促销活动。用户问『现在有什么活动』『满减/折扣在跑哪些』时调用。")
    public String listActivePromotions() {
        try {
            LocalDateTime now = LocalDateTime.now();
            StringBuilder sb = new StringBuilder("[");
            int count = 0;
            for (Promotion p : promotionRepository.findAllByOrderByCreatedAtDesc()) {
                if (p.getStatus() == null || p.getStatus() != 1) continue;
                if (p.getStartTime() != null && now.isBefore(p.getStartTime())) continue;
                if (p.getEndTime() != null && now.isAfter(p.getEndTime())) continue;
                if (count > 0) sb.append(",");
                sb.append("{")
                        .append("\"id\":").append(p.getId())
                        .append(",\"name\":\"").append(safe(p.getName())).append("\"")
                        .append(",\"type\":\"").append(p.getType() == null ? "" : p.getType().name()).append("\"")
                        .append(",\"threshold\":\"").append(money(p.getThreshold())).append("\"")
                        .append(",\"discount\":\"").append(p.getDiscount() == null ? "0" : p.getDiscount().toPlainString()).append("\"")
                        .append(",\"productId\":").append(p.getProductId() == null ? "null" : p.getProductId())
                        .append("}");
                count++;
            }
            sb.append("]");
            return "{\"count\":" + count + ",\"promotions\":" + sb + "}";
        } catch (Exception e) {
            return error(e);
        }
    }

    // =====================================================================
    //  共用辅助
    // =====================================================================

    private static boolean hitsKeyword(Product p, String kw) {
        if (p.getName() != null && p.getName().contains(kw)) return true;
        return p.getSubtitle() != null && p.getSubtitle().contains(kw);
    }

    /** 容错解析订单状态：支持枚举名与中文标签，非法值返回 null（=全部）。 */
    private static OrderStatus parseStatus(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String s = raw.trim();
        try {
            return OrderStatus.valueOf(s.toUpperCase());
        } catch (Exception ignore) {
            for (OrderStatus os : OrderStatus.values()) {
                if (os.getLabel().equals(s) || os.name().equalsIgnoreCase(s)) return os;
            }
            return null;
        }
    }

    /** 容错解析日期：支持 yyyy-MM-dd 与 yyyy/MM/dd，失败返回 null。 */
    private static LocalDate parseDate(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String s = raw.trim();
        try {
            return LocalDate.parse(s);
        } catch (Exception ignore) {
            try {
                return LocalDate.parse(s.replace('/', '-'));
            } catch (Exception ignore2) {
                return null;
            }
        }
    }

    private static long idOf(Object review) {
        try {
            return ((com.example.backend.entity.Review) review).getId();
        } catch (Exception e) {
            return 0L;
        }
    }

    private static String money(BigDecimal v) {
        return v == null ? "0.00" : v.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        String t = s.trim();
        return t.length() <= max ? t : t.substring(0, max);
    }

    /** 内联 JSON 文本转义：把双引号换成单引号并防 null，避免手工拼串出现非法 JSON。 */
    private static String safe(String s) {
        return s == null ? "" : s.replace("\"", "'");
    }

    private static String error(Exception e) {
        String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        return "{\"error\":\"查询失败: " + safe(msg) + "\"}";
    }
}
