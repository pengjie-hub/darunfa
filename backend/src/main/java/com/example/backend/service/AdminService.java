package com.example.backend.service;

import com.example.backend.common.BusinessException;
import com.example.backend.dto.DashboardChartsDTO;
import com.example.backend.dto.DashboardDTO;
import com.example.backend.entity.Category;
import com.example.backend.entity.Order;
import com.example.backend.entity.OrderStatus;
import com.example.backend.entity.Payment;
import com.example.backend.entity.Product;
import com.example.backend.entity.User;
import com.example.backend.entity.UserRole;
import com.example.backend.repository.CategoryRepository;
import com.example.backend.repository.OrderRepository;
import com.example.backend.repository.PaymentRepository;
import com.example.backend.repository.ProductRepository;
import com.example.backend.repository.ReviewRepository;
import com.example.backend.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 后台管理服务：统计看板 + 分类/会员管理。
 */
@Service
public class AdminService {

    /** 安全库存阈值（公开，便于后台弹窗/AI 助手等模块共用同一阈值）。 */
    public static final int LOW_STOCK_THRESHOLD = 10;

    /**
     * 发货时效 SLA（天）：订单付款后超过这个天数仍未发货，即视为「超时未发货」。
     * <p>仪表盘的超时提示与任何后续的时效统计都必须引用本常量，
     * 不要在别处再写一遍「2」——否则改 SLA 时会出现两套口径。</p>
     */
    public static final int SLA_SHIP_DAYS = 2;

    /** 仪表盘超时未发货明细最多回传几条（完整排查请走订单管理页）。 */
    private static final int OVERDUE_SHIP_PREVIEW = 5;

    /**
     * 「有效销售额 / 有效订单」的唯一口径 —— <b>全项目只此一处定义</b>。
     * <p>
     * 含义：钱已经真实收到、且尚未退还给买家的订单。
     * <ul>
     *   <li>{@code PAID}（待发货）、{@code SHIPPED}（待收货）、{@code COMPLETED}（已完成）—— 正常已收款；</li>
     *   <li>{@code REFUNDING}（退款中）—— 货款仍在平台侧，退款申请尚未被商家同意，计入；</li>
     *   <li>排除 {@code PENDING_PAYMENT}（没付钱）、{@code CANCELLED}（没成交）、
     *       {@code REFUNDED}（钱已退回，计入会虚增销售额）。</li>
     * </ul>
     * 仪表盘、AI 助手（小运）、任何新的销售统计都必须引用本集合，
     * <b>不要各自再写一遍判断条件</b>——历史上正是因为它被写了两遍（一处白名单、一处黑名单）
     * 才导致仪表盘与小运对同一笔「已退款」订单给出不同结果。
     */
    public static final Set<OrderStatus> EFFECTIVE_SALES_STATUSES =
            Set.of(OrderStatus.PAID, OrderStatus.SHIPPED, OrderStatus.COMPLETED, OrderStatus.REFUNDING);

    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final CategoryRepository categoryRepository;
    private final ReviewRepository reviewRepository;
    private final PaymentRepository paymentRepository;

    public AdminService(UserRepository userRepository,
                        ProductRepository productRepository,
                        OrderRepository orderRepository,
                        CategoryRepository categoryRepository,
                        ReviewRepository reviewRepository,
                        PaymentRepository paymentRepository) {
        this.userRepository = userRepository;
        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
        this.categoryRepository = categoryRepository;
        this.reviewRepository = reviewRepository;
        this.paymentRepository = paymentRepository;
    }

    public DashboardDTO dashboard() {
        DashboardDTO dto = new DashboardDTO();
        dto.setUserCount(userRepository.count());
        dto.setProductCount(productRepository.count());
        dto.setOrderCount(orderRepository.count());
        dto.setPendingShipCount(orderRepository.countByStatus(OrderStatus.PAID));
        dto.setPendingPaymentCount(orderRepository.countByStatus(OrderStatus.PENDING_PAYMENT));
        dto.setShippedCount(orderRepository.countByStatus(OrderStatus.SHIPPED));
        dto.setCompletedCount(orderRepository.countByStatus(OrderStatus.COMPLETED));
        dto.setReviewCount(reviewRepository.count());
        dto.setLowStockCount(productRepository.countByStockLessThanEqual(LOW_STOCK_THRESHOLD));
        BigDecimal sales = orderRepository.sumPayAmountByStatusIn(EFFECTIVE_SALES_STATUSES);
        dto.setTotalSales(sales == null ? BigDecimal.ZERO : sales);

        // ---- 超时未发货：付款后超过 SLA_SHIP_DAYS 天（=48 小时）仍是「待发货」----
        // 起算时间优先取 paidAt（商家能发货的起点），历史脏数据里 paidAt 可能缺失，退化为 createdAt。
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime deadline = now.minusDays(SLA_SHIP_DAYS);
        List<Order> overdueOrders = new ArrayList<>();
        for (Order o : orderRepository.findByStatusOrderByCreatedAtDesc(OrderStatus.PAID)) {
            LocalDateTime base = shipCountdownBase(o);
            if (base != null && base.isBefore(deadline)) overdueOrders.add(o);
        }
        // 等待最久的排最前；并列时按订单号，保证同一份数据每次返回顺序一致
        overdueOrders.sort(Comparator.comparing(AdminService::shipCountdownBase)
                .thenComparing(Order::getOrderNo, Comparator.nullsLast(Comparator.naturalOrder())));

        Map<Long, String> names = new HashMap<>();
        if (!overdueOrders.isEmpty()) {
            // 一次性取用户昵称，避免逐单查库
            for (User u : userRepository.findAllById(
                    overdueOrders.stream().map(Order::getUserId).distinct().toList())) {
                names.put(u.getId(), u.getNickname() == null || u.getNickname().isBlank()
                        ? u.getUsername() : u.getNickname());
            }
        }

        BigDecimal overdueAmount = BigDecimal.ZERO;
        long oldestDays = 0;
        List<DashboardDTO.OverdueShipment> overdue = new ArrayList<>();
        for (Order o : overdueOrders) {
            LocalDateTime base = shipCountdownBase(o);
            long days = ChronoUnit.DAYS.between(base, now);
            oldestDays = Math.max(oldestDays, days);
            BigDecimal amount = o.getPayAmount() == null ? BigDecimal.ZERO : o.getPayAmount();
            overdueAmount = overdueAmount.add(amount);
            if (overdue.size() < OVERDUE_SHIP_PREVIEW) {   // 仪表盘只做提示，明细最多几条
                overdue.add(new DashboardDTO.OverdueShipment(o.getOrderNo(),
                        names.getOrDefault(o.getUserId(), "-"), amount, days, base));
            }
        }
        dto.setOverdueShipCount(overdueOrders.size());
        dto.setOverdueShipAmount(overdueAmount);
        dto.setOldestOverdueDays(oldestDays);
        dto.setOverdueShips(overdue);

        return dto;
    }

    /** 发货倒计时起点：付款时间为准，缺失时退化为下单时间（可为 null）。 */
    private static LocalDateTime shipCountdownBase(Order o) {
        return o.getPaidAt() != null ? o.getPaidAt() : o.getCreatedAt();
    }

    /**
     * 把任意层级的分类上卷到它所属的「一级分类」（{@code parentId == 0}）。
     * 传 null 返回 null；父分类在数据里缺失时退化为当前分类，避免整条商品统计丢失。
     */
    private static Category topLevelCategoryOf(Category c, Map<Long, Category> byId) {
        if (c == null) return null;
        Category cur = c;
        for (int guard = 0; guard < 5; guard++) {   // 层级上限保护，脏数据成环也不会死循环
            Long pid = cur.getParentId();
            if (pid == null || pid == 0L) return cur;
            Category parent = byId.get(pid);
            if (parent == null) return cur;
            cur = parent;
        }
        return cur;
    }

    /**
     * 仪表盘可视化聚合数据：近 7 天趋势、订单状态分布、分类商品分布、支付方式分布。
     * 均在内存中聚合，避免依赖数据库方言的日期函数。
     */
    public DashboardChartsDTO charts() {
        DashboardChartsDTO dto = new DashboardChartsDTO();

        // ---- 1. 近 7 天销售趋势（含今天） ----
        LocalDate today = LocalDate.now();
        LocalDate start = today.minusDays(6);
        Map<LocalDate, Integer> dateIndex = new HashMap<>();
        List<DashboardChartsDTO.TrendPoint> trend = new ArrayList<>();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MM-dd");
        for (int i = 0; i < 7; i++) {
            LocalDate d = start.plusDays(i);
            dateIndex.put(d, i);
            trend.add(new DashboardChartsDTO.TrendPoint(d.format(fmt), 0L, BigDecimal.ZERO));
        }
        Set<OrderStatus> paidStatuses = EFFECTIVE_SALES_STATUSES;
        for (Order o : orderRepository.findByCreatedAtAfterOrderByCreatedAtAsc(start.atStartOfDay())) {
            Integer i = dateIndex.get(o.getCreatedAt().toLocalDate());
            if (i == null) continue;
            DashboardChartsDTO.TrendPoint tp = trend.get(i);
            tp.setOrderCount(tp.getOrderCount() + 1);
            if (paidStatuses.contains(o.getStatus())) {
                tp.setSalesAmount(tp.getSalesAmount().add(o.getPayAmount()));
            }
        }
        dto.setSalesTrend(trend);

        // ---- 2. 订单状态分布 ----
        for (OrderStatus s : OrderStatus.values()) {
            dto.getOrderStatusDist().add(new DashboardChartsDTO.DistPoint(s.getLabel(), orderRepository.countByStatus(s)));
        }

        // ---- 3. 分类商品数量分布（按「一级分类」上卷统计）----
        // 商品实际挂在二级分类上（categories 是「一级 / 二级」两级树，商品的 categoryId 只填二级 id），
        // 所以必须向上卷到一级分类再统计。原实现直接按「商品所在分类」分组，导致：
        //   ① 图上出现「水果类 / 纸品 / 米面」这类二级分类名，和「分类管理」页看到的一级分类对不上；
        //   ② 12 个二级分类被硬截成 top8，另外 4 个塞进「其他」；而 1 个商品的并列分类太多，
        //      「其他」反而成了最长的一条，4 个真实分类被藏起来，看起来像假数据；
        //   ③ 并列排序依赖 HashMap 迭代顺序，同一份数据两次刷新「其他」的组成可能不一样。
        // 现在一律上卷到一级分类：8 个一级分类即可覆盖全部商品，不需要「其他」，
        // 排序固定为「商品数降序 → sortOrder 升序 → 名称升序」，结果稳定可复现。
        Map<Long, Category> catById = new HashMap<>();
        for (Category c : categoryRepository.findAll()) catById.put(c.getId(), c);

        record CatAgg(String name, int sortOrder, long count) {}

        Map<Long, Long> topCount = new HashMap<>();
        long uncategorized = 0;
        for (Product p : productRepository.findAll()) {
            Category top = topLevelCategoryOf(catById.get(p.getCategoryId()), catById);
            if (top == null) uncategorized++;
            else topCount.merge(top.getId(), 1L, Long::sum);
        }

        List<CatAgg> aggs = new ArrayList<>();
        for (Map.Entry<Long, Long> e : topCount.entrySet()) {
            Category top = catById.get(e.getKey());
            aggs.add(new CatAgg(top.getName(),
                    top.getSortOrder() == null ? 0 : top.getSortOrder(), e.getValue()));
        }
        if (uncategorized > 0) aggs.add(new CatAgg("未分类", Integer.MAX_VALUE, uncategorized));
        aggs.sort(Comparator.comparingLong(CatAgg::count).reversed()
                .thenComparingInt(CatAgg::sortOrder)
                .thenComparing(CatAgg::name));

        int topN = 10;
        long others = 0;
        for (int i = 0; i < aggs.size(); i++) {
            if (i < topN) {
                dto.getCategoryDist().add(new DashboardChartsDTO.DistPoint(aggs.get(i).name(), aggs.get(i).count()));
            } else {
                others += aggs.get(i).count();
            }
        }
        // 「其他」固定排在末尾，不会再伪装成最大的分类
        if (others > 0) dto.getCategoryDist().add(new DashboardChartsDTO.DistPoint("其他", others));

        // ---- 4. 支付方式分布 ----
        Map<String, Long> payCount = new HashMap<>();
        for (Payment p : paymentRepository.findAll()) {
            payCount.merge(p.getMethod().getLabel(), 1L, Long::sum);
        }
        List<DashboardChartsDTO.DistPoint> payDist = new ArrayList<>();
        for (Map.Entry<String, Long> e : payCount.entrySet()) {
            payDist.add(new DashboardChartsDTO.DistPoint(e.getKey(), e.getValue()));
        }
        payDist.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
        dto.setPaymentDist(payDist);

        return dto;
    }

    public List<User> listUsers() {
        return userRepository.findAll();
    }

    public List<Product> listAllProducts() {
        return productRepository.findAll();
    }

    public List<Product> listLowStockProducts() {
        return productRepository.findByStockLessThanEqualOrderByStockAsc(LOW_STOCK_THRESHOLD);
    }

    // ---------- 分类管理 ----------


    public List<Category> listAllCategories() {
        return categoryRepository.findAll();
    }

    @Transactional
    public Category createCategory(String name, Long parentId, Integer sortOrder) {
        if (name == null || name.isBlank()) {
            throw new BusinessException(400, "分类名称不能为空");
        }
        Category c = new Category();
        c.setName(name);
        c.setParentId(parentId == null ? 0L : parentId);
        c.setSortOrder(sortOrder == null ? 0 : sortOrder);
        return categoryRepository.save(c);
    }

    @Transactional
    public Category updateCategory(Long id, String name, Integer sortOrder, Integer status) {
        Category c = categoryRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "分类不存在"));
        if (name != null && !name.isBlank()) c.setName(name);
        if (sortOrder != null) c.setSortOrder(sortOrder);
        if (status != null) c.setStatus(status);
        return categoryRepository.save(c);
    }

    @Transactional
    public void deleteCategory(Long id) {
        if (!categoryRepository.existsById(id)) {
            throw new BusinessException(404, "分类不存在");
        }
        categoryRepository.deleteById(id);
    }

    // ---------- 会员管理 ----------

    /**
     * 启用 / 禁用用户。
     *
     * <p>禁用是高危操作，必须拦住两种会把系统锁死的情况：
     * <ol>
     *   <li><b>禁用自己</b>：禁用后当前账号立即登不进来（{@code AuthService} 登录时判 403
     *       「账号已被禁用」），若此人又是唯一管理员，就没有任何在线入口可以恢复。</li>
     *   <li><b>禁用最后一名可用管理员</b>：与 {@link #updateUserRole} 的「至少保留 1 名 ADMIN」
     *       对称。这里用 countByRoleAndStatus 只统计状态正常的 ADMIN —— 若只统计角色总数，
     *       已经被禁用的管理员也会被算作"还留着人"，保护形同虚设。</li>
     * </ol>
     *
     * @param operatorId 当前操作者 id（来自 JWT），用于「不能禁用自己」校验
     */
    @Transactional
    public User updateUserStatus(Long id, Integer status, Long operatorId) {
        User u = userRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "用户不存在"));
        int target = (status == null || status != 0) ? 1 : 0;
        boolean currentlyEnabled = (u.getStatus() == null || u.getStatus() != 0);
        String label = (u.getUsername() == null ? "该用户" : u.getUsername());

        // 仅对「把正常账号改成禁用」这一转变做校验；重复禁用 / 启用不再拦截（幂等）
        if (target == 0 && currentlyEnabled) {
            if (operatorId != null && operatorId.equals(u.getId())) {
                throw new BusinessException(400,
                        "不能禁用自己的账号。禁用后将立即无法登录，如确需停用请由其他管理员操作。");
            }
            if (u.getRole() == UserRole.ADMIN
                    && userRepository.countByRoleAndStatus(UserRole.ADMIN, 1) <= 1) {
                throw new BusinessException(400, "系统至少需保留 1 名可用管理员，无法禁用「"
                        + label + "」。请先指定其他管理员后再操作。");
            }
        }

        u.setStatus(target);
        return userRepository.save(u);
    }

    @Transactional
    public User updateUserRole(Long id, String role) {
        User u = userRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "用户不存在"));
        UserRole newRole;
        try {
            newRole = UserRole.valueOf(role);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(400, "非法角色: " + role);
        }
        // 自降级 / 至少保留 1 名管理员保护：若该用户原为 ADMIN 且新角色不是 ADMIN，
        // 必须保证系统中仍至少存在 1 名 ADMIN，否则拒绝。
        if (u.getRole() == UserRole.ADMIN && newRole != UserRole.ADMIN) {
            long adminCount = userRepository.countByRole(UserRole.ADMIN);
            if (adminCount <= 1) {
                throw new BusinessException(400, "系统至少需保留 1 名管理员，无法将「"
                        + (u.getUsername() == null ? "该用户" : u.getUsername())
                        + "」降级。请先指定其他管理员后再操作。");
            }
        }
        u.setRole(newRole);
        return userRepository.save(u);
    }
}
