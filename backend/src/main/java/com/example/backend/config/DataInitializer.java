package com.example.backend.config;

import com.example.backend.entity.Category;
import com.example.backend.entity.Menu;
import com.example.backend.entity.Order;
import com.example.backend.entity.OrderStatus;
import com.example.backend.entity.Payment;
import com.example.backend.entity.PaymentMethod;
import com.example.backend.entity.PaymentStatus;
import com.example.backend.entity.Permission;
import com.example.backend.entity.Product;
import com.example.backend.entity.Promotion;
import com.example.backend.entity.PromotionType;
import com.example.backend.entity.Review;
import com.example.backend.entity.RiskLevel;
import com.example.backend.entity.RiskScore;
import com.example.backend.entity.Role;
import com.example.backend.entity.RolePermission;
import com.example.backend.entity.Shipment;
import com.example.backend.entity.ShipmentStatus;
import com.example.backend.entity.User;
import com.example.backend.entity.UserRole;
import com.example.backend.repository.BalanceTransactionRepository;
import com.example.backend.repository.CategoryRepository;
import com.example.backend.repository.MenuRepository;
import com.example.backend.repository.OrderRepository;
import com.example.backend.repository.PaymentRepository;
import com.example.backend.repository.PermissionRepository;
import com.example.backend.repository.ProductRepository;
import com.example.backend.repository.PromotionRepository;
import com.example.backend.repository.ReviewRepository;
import com.example.backend.repository.RiskScoreRepository;
import com.example.backend.repository.RolePermissionRepository;
import com.example.backend.repository.RoleRepository;
import com.example.backend.repository.ShipmentRepository;
import com.example.backend.repository.UserRepository;
import com.example.backend.service.WalletService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 启动时初始化：内置管理员 + 演示商品/分类 + RBAC（角色/权限/菜单）种子数据。
 * <p>
 * 设计原则：每个表都做"空表才 seed"检查，幂等。多次启动不会重复写入。
 * 这样开发期可以放心地 truncate 表重新跑也不会出错。
 */
@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    /**
     * 演示商品的图片键映射（商品名 → Android 端 drawable 资源名）。
     * <p>
     * Android 前端本地打包了商品图（p001~p081 等 drawable），后端无法直接托管图片，
     * 因此这里把「图片资源名」作为 imageUrl 下发，前端再据此映射到本地 drawable。
     * 与前端 {@code DtoMapper} 的解析逻辑配套。
     */
    private static final Map<String, String> PRODUCT_IMAGES = Map.ofEntries(
            Map.entry("红富士苹果", "p002"),
            Map.entry("赣南脐橙", "p003"),
            Map.entry("有机菠菜", "p007"),
            Map.entry("五花肉", "p011"),
            Map.entry("农家土鸡蛋", "p012"),
            Map.entry("基围虾", "p059"),
            Map.entry("东北大米", "p029"),
            Map.entry("花生油", "p030"),
            Map.entry("纯牛奶", "p015"),
            Map.entry("曲奇饼干", "p016"),
            Map.entry("天然矿泉水", "p067"),
            Map.entry("维达超韧抽纸", "p072"),
            Map.entry("清风原木抽纸", "p033"),
            Map.entry("蓝月亮洗衣液", "p073")
    );

    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final MenuRepository menuRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final OrderRepository orderRepository;
    private final ReviewRepository reviewRepository;
    private final PromotionRepository promotionRepository;
    private final PaymentRepository paymentRepository;
    private final ShipmentRepository shipmentRepository;
    private final RiskScoreRepository riskScoreRepository;
    private final PasswordEncoder passwordEncoder;
    private final WalletService walletService;
    private final BalanceTransactionRepository balanceTransactionRepository;

    @Value("${app.admin.username}")
    private String adminUsername;

    @Value("${app.admin.password}")
    private String adminPassword;

    @Value("${app.admin.nickname}")
    private String adminNickname;

    public DataInitializer(UserRepository userRepository,
                           CategoryRepository categoryRepository,
                           ProductRepository productRepository,
                           RoleRepository roleRepository,
                           PermissionRepository permissionRepository,
                           MenuRepository menuRepository,
                           RolePermissionRepository rolePermissionRepository,
                           OrderRepository orderRepository,
                           ReviewRepository reviewRepository,
                           PromotionRepository promotionRepository,
                           PaymentRepository paymentRepository,
                           ShipmentRepository shipmentRepository,
                           RiskScoreRepository riskScoreRepository,
                           PasswordEncoder passwordEncoder,
                           WalletService walletService,
                           BalanceTransactionRepository balanceTransactionRepository) {
        this.userRepository = userRepository;
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.menuRepository = menuRepository;
        this.rolePermissionRepository = rolePermissionRepository;
        this.orderRepository = orderRepository;
        this.reviewRepository = reviewRepository;
        this.promotionRepository = promotionRepository;
        this.paymentRepository = paymentRepository;
        this.shipmentRepository = shipmentRepository;
        this.riskScoreRepository = riskScoreRepository;
        this.passwordEncoder = passwordEncoder;
        this.walletService = walletService;
        this.balanceTransactionRepository = balanceTransactionRepository;
    }

    @Override
    @Transactional
    public void run(String... args) {
        initAdmin();
        // 目录种子改为「按名幂等」：不再用 count()==0 整批守卫。
        // 旧守卫会让后续新增/改名的分类、商品在已有库里【永远写不进去】，是
        // 「分类/商品不生效」类问题的根源（详见项目约定：种子必须按唯一键幂等）。
        seedCatalog();
        ensureProductImages();

        // RBAC 种子（仅当对应表为空时执行）
        seedRoles();
        seedPermissions();
        seedRolePermissions();
        seedMenus();
        seedDemoUsers();
        // 初始赠送余额（幂等，见 seedBalances）：必须放在用户种子之后，否则 testuser 还不存在
        seedBalances();

        // 业务种子：促销 → 订单 → 评论/支付/物流
        seedPromotions();
        seedOrders();
        seedReviews();
        seedPayments();
        seedShipments();
        seedHighRiskOrderDemo();
    }

    private void initAdmin() {
        if (userRepository.existsByUsername(adminUsername)) {
            return;
        }
        User admin = new User();
        admin.setUsername(adminUsername);
        admin.setPassword(passwordEncoder.encode(adminPassword));
        admin.setNickname(adminNickname);
        admin.setRole(UserRole.ADMIN);
        userRepository.save(admin);
        log.info("已创建内置管理员账号: {}", adminUsername);
    }

    private void seedCatalog() {
        // 一级分类
        Category fruit = saveCategory("新鲜果蔬", 0L, 1);
        Category meat = saveCategory("肉禽蛋品", 0L, 2);
        Category seafood = saveCategory("海鲜水产", 0L, 3);
        Category grain = saveCategory("粮油调味", 0L, 4);
        Category dairy = saveCategory("乳品烘焙", 0L, 5);
        Category snack = saveCategory("休闲零食", 0L, 6);
        Category drink = saveCategory("酒水饮料", 0L, 7);
        Category personalCare = saveCategory("个护清洁", 0L, 8);

        // 二级分类
        Category fruitCls = saveCategory("水果类", fruit.getId(), 1);
        Category vegCls = saveCategory("叶菜类", fruit.getId(), 2);
        Category porkCls = saveCategory("猪肉", meat.getId(), 1);
        Category poultryCls = saveCategory("禽类", meat.getId(), 2);
        saveCategory("鱼类", seafood.getId(), 1);
        Category shrimpCls = saveCategory("虾蟹", seafood.getId(), 2);
        Category riceCls = saveCategory("米面", grain.getId(), 1);
        Category oilCls = saveCategory("食用油", grain.getId(), 2);
        Category milkCls = saveCategory("牛奶", dairy.getId(), 1);
        saveCategory("面包糕点", dairy.getId(), 2);
        Category biscuitCls = saveCategory("饼干", snack.getId(), 1);
        saveCategory("坚果炒货", snack.getId(), 2);
        Category waterCls = saveCategory("饮用水", drink.getId(), 1);
        saveCategory("碳酸饮料", drink.getId(), 2);
        Category paperCls = saveCategory("纸品", personalCare.getId(), 1);
        Category washCls = saveCategory("洗护用品", personalCare.getId(), 2);

        // 商品
        seedProduct(fruitCls.getId(), "红富士苹果", "脆甜多汁 产地直供", "精选山东烟台红富士苹果，个大皮薄，果肉脆甜。", "9.90", "12.90", 500, "斤");
        seedProduct(fruitCls.getId(), "赣南脐橙", "皮薄肉嫩 汁水丰盈", "江西赣南脐橙，酸甜可口，富含维生素C。", "6.80", "8.80", 800, "斤");
        seedProduct(vegCls.getId(), "有机菠菜", "有机认证 现摘现发", "有机种植菠菜，鲜嫩无农药残留。", "4.50", "5.50", 300, "把");
        seedProduct(porkCls.getId(), "五花肉", "肥瘦相间 红烧首选", "当日现宰五花肉，肥瘦均匀。", "19.90", "23.90", 200, "斤");
        seedProduct(poultryCls.getId(), "农家土鸡蛋", "散养土鸡 蛋黄饱满", "农家散养土鸡蛋，营养丰富。", "15.90", "19.90", 400, "盒");
        seedProduct(shrimpCls.getId(), "基围虾", "鲜活直送 肉质Q弹", "鲜活基围虾，当天捕捞当天送。", "39.90", "49.90", 150, "斤");
        seedProduct(riceCls.getId(), "东北大米", "五常稻花香 颗粒饱满", "五常稻花香大米，香糯可口。", "59.90", "69.90", 600, "袋");
        seedProduct(oilCls.getId(), "花生油", "物理压榨 香浓醇厚", "一级压榨花生油，非转基因。", "89.90", "99.90", 300, "桶");
        seedProduct(milkCls.getId(), "纯牛奶", "牧场直供 3.6g蛋白", "高品质纯牛奶，每100ml含3.6g优质蛋白。", "45.90", "55.90", 500, "箱");
        seedProduct(biscuitCls.getId(), "曲奇饼干", "黄油曲奇 酥脆香甜", "丹麦风味黄油曲奇饼干。", "19.90", "25.90", 700, "盒");
        seedProduct(waterCls.getId(), "天然矿泉水", "弱碱性 深层岩层水", "深层岩层天然矿泉水，弱碱性。", "2.00", "2.50", 2000, "瓶");

        // 个护清洁（纸品 / 洗护用品）：原先这两个子分类下**一件商品都没有**，
        // 导致「个护清洁」在首页分类网格和页签里点进去永远是空列表，这里补齐演示商品。
        seedProduct(paperCls.getId(), "维达超韧抽纸", "3层加厚 整箱装", "维达超韧系列抽纸，3层加厚不易破，整箱装。", "39.90", "49.90", 400, "箱");
        seedProduct(paperCls.getId(), "清风原木抽纸", "原生木浆 整箱装", "清风原木纯品抽纸，100%原生木浆，亲肤柔韧。", "29.90", "36.90", 500, "箱");
        seedProduct(washCls.getId(), "蓝月亮洗衣液", "深层洁净 3kg", "蓝月亮深层洁净洗衣液，低泡易漂，护色护衣。", "49.90", "59.90", 300, "瓶");
    }

    private Category saveCategory(String name, Long parentId, int sort) {
        // 按名幂等：已存在则直接复用，避免重复插入，也保证老库能补上新分类。
        Optional<Category> existing = categoryRepository.findByName(name);
        if (existing.isPresent()) {
            return existing.get();
        }
        Category c = new Category();
        c.setName(name);
        c.setParentId(parentId);
        c.setSortOrder(sort);
        return categoryRepository.save(c);
    }

    private void seedProduct(Long categoryId, String name, String subtitle,
                             String desc, String price, String original, int stock, String unit) {
        // 按名幂等：同名商品已存在则跳过，便于给老库增量补种而不重复。
        if (productRepository.findByName(name).isPresent()) {
            return;
        }
        Product p = new Product();
        p.setCategoryId(categoryId);
        p.setName(name);
        p.setSubtitle(subtitle);
        p.setDescription(desc);
        p.setPrice(new BigDecimal(price));
        p.setOriginalPrice(new BigDecimal(original));
        p.setStock(stock);
        p.setUnit(unit);
        p.setImageUrl(PRODUCT_IMAGES.get(name));
        productRepository.save(p);
    }

    /**
     * 幂等回填：给已存在于数据库中的演示商品补上图片键（imageUrl）。
     * <p>
     * 仅当 imageUrl 为空时才更新，不覆盖运营/后台后续可能手动设置的图片地址；
     * 因此无论库是新种子的还是老库升级上来的，都能保证演示商品有正确的图片。
     */
    private void ensureProductImages() {
        for (Map.Entry<String, String> e : PRODUCT_IMAGES.entrySet()) {
            productRepository.findByName(e.getKey()).ifPresent(p -> {
                if (p.getImageUrl() == null || p.getImageUrl().isBlank()) {
                    p.setImageUrl(e.getValue());
                    productRepository.save(p);
                }
            });
        }
    }

    // ============================================================
    // RBAC 种子：角色 / 权限 / 菜单
    // ============================================================

    /** 写入 4 个基础角色：超级管理员、运营、客服、普通用户。 */
    private void seedRoles() {
        if (roleRepository.count() > 0) return;
        roleRepository.save(role("ADMIN", "超级管理员", "拥有系统全部权限，可管理角色/权限/菜单", 1, "ALL"));
        roleRepository.save(role("OPS",   "运营专员", "负责商品上下架、订单发货、促销配置等日常运营", 2, "ALL"));
        roleRepository.save(role("CS",    "客服",    "处理用户评论、回复工单、退款审核", 3, "ALL"));
        roleRepository.save(role("USER",  "普通用户", "前台消费者账号（与用户枚举 USER 对应）", 4, "SELF"));
        log.info("已写入 4 个基础角色");
    }

    private Role role(String code, String name, String desc, int sort, String dataScope) {
        Role r = new Role();
        r.setCode(code);
        r.setName(name);
        r.setDescription(desc);
        r.setStatus(1);
        r.setSortOrder(sort);
        r.setDataScope(dataScope);
        return r;
    }

    /**
     * 写入 10 个基础权限（按模块分组，编码格式：{@code 资源:操作}）。
     * <p>
     * 全部为 {@code MENU} 类型，因为本版本主要做菜单可见性控制；
     * 后续扩展按钮/API 权限时新增 {@code BUTTON}/{@code API} 类型即可。
     * </p>
     *
     * <p><b>幂等</b>：按 {@code code} 判重，已存在则跳过。新增权限时无需手动清表。
     * 旧版用 {@code count()>0 return} 守卫导致后续新增的权限不会自动写入（已修复）。</p>
     */
    private void seedPermissions() {
        int added = 0;
        for (String[] def : new String[][] {
                {"product:view",     "查看商品",  "商品"},
                {"product:edit",     "编辑商品",  "商品"},
                {"order:view",       "查看订单",  "订单"},
                {"order:ship",       "订单发货",  "订单"},
                {"member:view",      "查看会员",  "会员"},
                {"promotion:manage", "促销管理",  "促销"},
                {"review:reply",     "评论回复",  "评论"},
                {"review:analyze",   "AI 情感分析","评论"},
                {"cs:manage",        "客服管理",  "客服"},
                {"risk:view",        "风险监控",  "风控"},
                {"risk:scan",        "风险扫描",  "风控"}
        }) {
            String code = def[0];
            if (permissionRepository.findByCode(code).isPresent()) continue;
            int sort = ++added + (int) permissionRepository.count();
            permissionRepository.save(perm(code, def[1], def[2], sort));
        }
        if (added > 0) log.info("已新增 {} 个基础权限", added);
    }

    private Permission perm(String code, String name, String module, int sort) {
        Permission p = new Permission();
        p.setCode(code);
        p.setName(name);
        p.setModule(module);
        p.setType(Permission.Type.MENU);
        p.setParentId(0L);
        p.setStatus(1);
        p.setSortOrder(sort);
        return p;
    }

    /** 写入角色-权限关联：ADMIN 全有，OPS 商品/订单/促销/风控，CS 评论/会员，USER 无。
     * <p><b>幂等</b>：按 (roleId, permissionId) 判重补齐，已存在跳过。ADMIN/OPS/CS 的权限属于后台可运营配置
     * （「角色管理」页面可改），因此只增不删，改一处期望集合只需在本方法补一行。
     * <p>唯一例外是 USER：种子语义恒为空集，每次启动强制清空 —— 普通用户只要拿到任何一个后台权限码
     * （例如 order:view 就能调退款审核接口），都会直接触达资金接口，属于越权。
     */
    private void seedRolePermissions() {
        Role admin = roleRepository.findByCode("ADMIN").orElseThrow();
        Role ops   = roleRepository.findByCode("OPS").orElseThrow();
        Role cs    = roleRepository.findByCode("CS").orElseThrow();

        // 全部权限（11 个，含风控与客服）
        List<Long> all = permissionIdsByCodes("product:view","product:edit","order:view","order:ship",
                "member:view","promotion:manage","review:reply","review:analyze",
                "cs:manage","risk:view","risk:scan");
        // 运营：商品/订单/促销 + 风控（运营需要监控异常订单）+ 客服（统一对外口径）
        List<Long> opsPerm = permissionIdsByCodes("product:view","product:edit","order:view","order:ship","promotion:manage","cs:manage","risk:view","risk:scan");
        // 客服：评论 + 会员查看 + 在线客服（不涉及风控）
        List<Long> csPerm  = permissionIdsByCodes("review:reply","review:analyze","member:view","cs:manage");

        int added = 0;
        added += saveIfAbsent(admin, all);
        added += saveIfAbsent(ops,   opsPerm);
        added += saveIfAbsent(cs,    csPerm);
        if (added > 0) log.info("已新增 {} 条角色-权限关联（ADMIN {} / OPS {} / CS {}）",
                added, all.size(), opsPerm.size(), csPerm.size());

        // 反向清理（自愈）：保证 USER 角色恒为空集。
        // 背景 —— saveIfAbsent 只增不删，所以只要有人（或越权请求）往 role_permissions 插了 USER 的行，
        // 它会永久留在库里，重启也带不走。实测后果很严重：USER 角色曾多出 order:view，
        // 而 /api/admin/refunds/{id}/approve 的权限要求是 {order:view, cs:manage}（OR 语义），
        // 于是普通用户可以直接调后台接口通过退款 —— 真金白银会动。
        // 这里只收敛 USER，不动 ADMIN/OPS/CS：那三个角色的权限属于「后台可运营的配置」，
        // 管理员在「角色管理」里改了就该持久生效，重启不能被代码无条件覆盖。
        Role user = roleRepository.findByCode("USER").orElse(null);
        if (user != null) {
            List<RolePermission> dirty = rolePermissionRepository.findAllByRoleId(user.getId());
            if (!dirty.isEmpty()) {
                rolePermissionRepository.deleteAllByRoleId(user.getId());
                log.warn("已清理 USER 角色 {} 条不该存在的权限关联（普通用户必须无任何后台权限，防止越权）",
                        dirty.size());
            }
        }
    }

    /** 单条 upsert：若 (roleId, permissionId) 已存在则跳过，避免重复键异常。 */
    private int saveIfAbsent(Role role, List<Long> permissionIds) {
        int n = 0;
        for (Long pid : permissionIds) {
            if (rolePermissionRepository.existsByRoleIdAndPermissionId(role.getId(), pid)) continue;
            rolePermissionRepository.save(new RolePermission(role.getId(), pid));
            n++;
        }
        return n;
    }

    private List<Long> permissionIdsByCodes(String... codes) {
        List<Long> ids = new ArrayList<>();
        for (String c : codes) {
            permissionRepository.findByCode(c).ifPresent(p -> ids.add(p.getId()));
        }
        return ids;
    }


    /**
     * 写入后台菜单。
     * <p>
     * 字段说明：{@code parentId=0} 表示顶级；{@code component} 与 admin.html 的
     * MODULES key 对应（前端按此分发渲染）；{@code permissionId} 关联权限码，
     * 后续做可见性控制时使用。
     * <p>
     * <b>幂等</b>：按 {@code component} 判重（不是「空表才写」）—— 早期版本用
     * {@code count()>0 return} 守卫，导致后加的「客服管理」菜单在已部署环境里永远写不进去。
     */
    private void seedMenus() {
        // 先把权限 id 查出来映射
        Map<String, Long> permMap = new HashMap<>();
        for (Permission p : permissionRepository.findAll()) {
            permMap.put(p.getCode(), p.getId());
        }
        int added = 0;
        if (seedMenu(0L, "商品管理", "/admin/products",   "🛒", "products",   permMap.get("product:view"),     1)) added++;
        if (seedMenu(0L, "订单管理", "/admin/orders",     "📦", "orders",     permMap.get("order:view"),       2)) added++;
        if (seedMenu(0L, "会员管理", "/admin/members",    "👥", "members",    permMap.get("member:view"),      3)) added++;
        if (seedMenu(0L, "评论管理", "/admin/reviews",    "💬", "reviews",    permMap.get("review:reply"),     4)) added++;
        if (seedMenu(0L, "促销管理", "/admin/promotions", "🏷️", "promotions", permMap.get("promotion:manage"), 5)) added++;
        if (seedMenu(0L, "物流管理", "/admin/shipments",  "🚚", "shipments",  permMap.get("order:ship"),       6)) added++;
        if (seedMenu(0L, "客服管理", "/admin/cs",         "🎧", "cs",         permMap.get("cs:manage"),        7)) added++;
        if (seedMenu(0L, "退款管理", "/admin/refunds",    "💰", "refunds",    permMap.get("order:view"),        8)) added++;
        // AI 知识库：不绑定权限码（传 null）—— 后端 /api/admin/ai/knowledge/** 标注的是 @RequireAdmin，
        // 只有超级管理员能访问，因此这里没有「某个权限码可见」的语义，与前端导航的 __ADMIN_ONLY__ 对应。
        if (seedMenu(0L, "AI 知识库", "/admin/knowledge", "📚", "knowledge",  null,                             9)) added++;
        if (added > 0) log.info("已新增 {} 个后台菜单", added);
    }

    /** 按 component 幂等写入一个顶级菜单，已存在返回 false。 */
    private boolean seedMenu(Long parentId, String name, String path, String icon, String component,
                             Long permissionId, int sort) {
        if (menuRepository.findByComponent(component).isPresent()) return false;
        menuRepository.save(menu(parentId, name, path, icon, component, Menu.Type.MENU, permissionId, sort));
        return true;
    }

    private Menu menu(Long parentId, String name, String path, String icon, String component,
                      Menu.Type type, Long permissionId, int sort) {
        Menu m = new Menu();
        m.setParentId(parentId);
        m.setName(name);
        m.setPath(path);
        m.setIcon(icon);
        m.setComponent(component);
        m.setType(type);
        m.setPermissionId(permissionId);
        m.setStatus(1);
        m.setSortOrder(sort);
        return m;
    }

    /**
     * 补充示例用户：在已存在 admin 之外再插入 opsuser/csuser/testuser 3 个，
     * 用于演示不同角色的菜单可见性。
     * <p>
     * 仅当总用户数小于 4 时才执行，避免重复 seed。
     */
    private void seedDemoUsers() {
        if (userRepository.count() >= 4) return;
        saveUser("opsuser",  "运营小王", "13800000001", "ops@darunfa.com",  UserRole.OPS);
        saveUser("csuser",   "客服小李", "13800000002", "cs@darunfa.com",   UserRole.CS);
        saveUser("testuser", "测试用户", "13800000003", "test@darunfa.com", UserRole.USER);
        log.info("已写入 3 个示例用户（opsuser / csuser / testuser）");
    }

    /**
     * 初始赠送余额（幂等：以余额流水 INIT-{userId} 为唯一键，重复启动不会重复发放）。
     * 只给 testuser 发：riskdemo 刻意保持 0 余额，用于覆盖「余额不足 / 先充值」路径。
     * <p>
     * ⚠️ 必须走 {@link WalletService#initBalance}，不要直接改 {@code user.balance} ——
     * 否则余额增加没有流水凭据，「账实相符」就破了。
     */
    private void seedBalances() {
        userRepository.findByUsername("testuser").ifPresent(u -> {
            if (!balanceTransactionRepository.existsByBizNo("INIT-" + u.getId())) {
                walletService.initBalance(u.getId(), new BigDecimal("1000.00"));
                log.info("已为 testuser(id={}) 发放初始余额 1000.00", u.getId());
            }
        });
    }

    private void saveUser(String username, String nickname, String phone, String email, UserRole role) {
        if (userRepository.existsByUsername(username)) return;
        User u = new User();
        u.setUsername(username);
        u.setPassword(passwordEncoder.encode("admin123"));
        u.setNickname(nickname);
        u.setPhone(phone);
        u.setEmail(email);
        u.setRole(role);
        u.setStatus(1);
        userRepository.save(u);
    }

    // ==================== 业务种子：促销 / 订单 / 评论 / 支付 / 物流 ====================

    /**
     * 促销种子（3 条）：覆盖满减与折扣两种类型，部分限定商品。
     * 仅当 promotions 表为空时执行。
     */
    private void seedPromotions() {
        if (promotionRepository.count() > 0) return;
        // 1. 满减：满 100 减 10（全场通用）
        Promotion p1 = new Promotion();
        p1.setName("满 100 减 10（全场通用）");
        p1.setType(PromotionType.FULL_REDUCTION);
        p1.setThreshold(new BigDecimal("100.00"));
        p1.setDiscount(new BigDecimal("10.00"));
        p1.setStartTime(LocalDateTime.now().minusDays(3));
        p1.setEndTime(LocalDateTime.now().plusDays(20));
        p1.setStatus(1);
        promotionRepository.save(p1);

        // 2. 满减：满 50 立减 5（新人专享）
        Promotion p2 = new Promotion();
        p2.setName("新人立减 5 元（满 50 可用）");
        p2.setType(PromotionType.FULL_REDUCTION);
        p2.setThreshold(new BigDecimal("50.00"));
        p2.setDiscount(new BigDecimal("5.00"));
        p2.setStartTime(LocalDateTime.now().minusDays(1));
        p2.setEndTime(LocalDateTime.now().plusDays(30));
        p2.setStatus(1);
        promotionRepository.save(p2);

        // 3. 折扣：东北大米 8 折（限定商品 id=7）
        Product daomi = productRepository.findById(7L).orElse(null);
        if (daomi != null) {
            Promotion p3 = new Promotion();
            p3.setName("东北大米限时 8 折");
            p3.setType(PromotionType.DISCOUNT);
            p3.setThreshold(BigDecimal.ZERO);
            p3.setDiscount(new BigDecimal("0.8000"));
            p3.setProductId(daomi.getId());
            p3.setStartTime(LocalDateTime.now().minusDays(2));
            p3.setEndTime(LocalDateTime.now().plusDays(7));
            p3.setStatus(1);
            promotionRepository.save(p3);
        }
        log.info("已写入 3 条促销种子（满减/满减/折扣）");
    }

    /**
     * 订单种子（5 条）：覆盖全部 4 种业务状态（待付款/待发货/待收货/已完成/已取消），
     * 并关联真实存在的商品与用户，便于支付、物流、评论做外键关联。
     * 仅当 orders 表为空时执行。
     */
    private void seedOrders() {
        if (orderRepository.count() > 0) return;
        Product pingguo = productRepository.findById(1L).orElse(null);   // 红富士苹果
        Product wuhuarou = productRepository.findById(4L).orElse(null);  // 五花肉
        Product dami = productRepository.findById(7L).orElse(null);       // 东北大米
        Product jiwei = productRepository.findById(6L).orElse(null);      // 基围虾
        Product huashengyou = productRepository.findById(8L).orElse(null);// 花生油
        if (pingguo == null || wuhuarou == null || dami == null || jiwei == null || huashengyou == null) {
            log.warn("订单种子跳过：商品数据未就绪");
            return;
        }

        // 订单 1：待付款
        Order o1 = new Order();
        o1.setOrderNo("O20260908001");
        o1.setUserId(5L); // testuser
        o1.setTotalAmount(new BigDecimal("49.50"));
        o1.setPayAmount(new BigDecimal("49.50"));
        o1.setDiscountAmount(BigDecimal.ZERO);
        o1.setStatus(OrderStatus.PENDING_PAYMENT);
        o1.setReceiverName("测试用户");
        o1.setReceiverPhone("13800000003");
        o1.setReceiverAddress("北京市朝阳区望京 SOHO T2 1801");
        o1.setRemark("苹果 5 斤，请尽快发货");
        o1.setCreatedAt(LocalDateTime.now().minusHours(2));
        orderRepository.save(o1);

        // 订单 2：待发货（已支付）
        Order o2 = new Order();
        o2.setOrderNo("O20260908002");
        o2.setUserId(2L); // 12
        o2.setTotalAmount(new BigDecimal("79.60"));
        o2.setPayAmount(new BigDecimal("79.60"));
        o2.setDiscountAmount(BigDecimal.ZERO);
        o2.setStatus(OrderStatus.PAID);
        o2.setReceiverName("张三");
        o2.setReceiverPhone("13800000010");
        o2.setReceiverAddress("上海市浦东新区世纪大道 100 号 3 楼");
        o2.setRemark("五花肉 4 斤，分两份打包");
        o2.setCreatedAt(LocalDateTime.now().minusHours(20));
        o2.setPaidAt(LocalDateTime.now().minusHours(19));
        orderRepository.save(o2);

        // 订单 3：待收货（已发货，使用了大米 8 折促销）
        Order o3 = new Order();
        o3.setOrderNo("O20260908003");
        o3.setUserId(4L); // csuser
        o3.setTotalAmount(new BigDecimal("59.90"));
        o3.setPayAmount(new BigDecimal("47.92")); // 8 折
        o3.setDiscountAmount(new BigDecimal("11.98"));
        o3.setPromotionId(findPromotionIdByName("东北大米限时 8 折"));
        o3.setStatus(OrderStatus.SHIPPED);
        o3.setReceiverName("客服小李");
        o3.setReceiverPhone("13800000002");
        o3.setReceiverAddress("广州市天河区珠江新城兴民路 222 号");
        o3.setCreatedAt(LocalDateTime.now().minusDays(1).minusHours(8));
        o3.setPaidAt(LocalDateTime.now().minusDays(1).minusHours(7));
        o3.setShippedAt(LocalDateTime.now().minusHours(10));
        orderRepository.save(o3);

        // 订单 4：已完成（基围虾）
        Order o4 = new Order();
        o4.setOrderNo("O20260908004");
        o4.setUserId(3L); // opsuser
        o4.setTotalAmount(new BigDecimal("79.80"));
        o4.setPayAmount(new BigDecimal("79.80"));
        o4.setDiscountAmount(BigDecimal.ZERO);
        o4.setStatus(OrderStatus.COMPLETED);
        o4.setReceiverName("运营小王");
        o4.setReceiverPhone("13800000001");
        o4.setReceiverAddress("深圳市南山区科技园南区 9 号楼");
        o4.setCreatedAt(LocalDateTime.now().minusDays(4));
        o4.setPaidAt(LocalDateTime.now().minusDays(4).plusMinutes(5));
        o4.setShippedAt(LocalDateTime.now().minusDays(3));
        o4.setCompletedAt(LocalDateTime.now().minusDays(1));
        orderRepository.save(o4);

        // 订单 5：待收货（运输中，花生油）
        Order o5 = new Order();
        o5.setOrderNo("O20260908005");
        o5.setUserId(2L);
        o5.setTotalAmount(new BigDecimal("89.90"));
        o5.setPayAmount(new BigDecimal("89.90"));
        o5.setDiscountAmount(BigDecimal.ZERO);
        o5.setStatus(OrderStatus.SHIPPED);
        o5.setReceiverName("张三");
        o5.setReceiverPhone("13800000010");
        o5.setReceiverAddress("上海市浦东新区世纪大道 100 号 3 楼");
        o5.setRemark("花生油 1 桶，请轻拿轻放");
        o5.setCreatedAt(LocalDateTime.now().minusDays(2));
        o5.setPaidAt(LocalDateTime.now().minusDays(2).plusMinutes(10));
        o5.setShippedAt(LocalDateTime.now().minusDays(1));
        orderRepository.save(o5);

        log.info("已写入 5 条订单种子（覆盖 PENDING/PAID/SHIPPED/COMPLETED）");
    }

    /**
     * 高风险演示订单种子（独立检查，幂等）。
     * <p>设计要点：</p>
     * <ul>
     *   <li>新建用户 {@code riskdemo}：7 天内首次下单，触发 FIRST_BIG（首单大额 +25）</li>
     *   <li>下单时间为今天凌晨 02:30（0-5 点），触发 LATE_NIGHT（+10）</li>
     *   <li>收货地址含敏感词「快递柜」，触发 ADDRESS_SUSPICIOUS（+15）</li>
     *   <li>订单金额 ¥1888 > 500，触发 FIRST_BIG</li>
     *   <li>3 条同地址小额订单（30 分钟内连下）：触发 BURST_ORDERS（+35，第 3 条时计数达 3）</li>
     * </ul>
     * <p>前 3 条短时多单让第 3 条自然得到 BURST_ORDERS + 地址敏感 + 凌晨时段（≈ +60 分，HIGH 边缘）；
     * 主单（订单 4）再叠加首单大额使其必为 HIGH。最终在订单表风险列直接显示 HIGH，
     * 在风险监控页表也对应出现 HIGH 评分。</p>
     * <p>预置 {@code riskLevel}/{@code riskScore}/{@code riskScannedAt} 与对应 {@code risk_scores}
     * 行，使前端无需触发异步扫描也能立即看到结果。</p>
     */
    private void seedHighRiskOrderDemo() {
        final String HIGH_RISK_NO = "O20260908086"; // 便于幂等判定
        if (orderRepository.findByOrderNo(HIGH_RISK_NO).isPresent()) {
            return;
        }

        // 1) 新建 "riskdemo" 用户（当日注册 → 触发 FIRST_BIG）
        User riskUser;
        if (userRepository.existsByUsername("riskdemo")) {
            riskUser = userRepository.findByUsername("riskdemo").orElseThrow();
        } else {
            User u = new User();
            u.setUsername("riskdemo");
            u.setPassword(passwordEncoder.encode("admin123"));
            u.setNickname("风险测试账户");
            u.setPhone("13900000086");
            u.setEmail("risk@darunfa.com");
            u.setRole(UserRole.USER);
            u.setStatus(1);
            riskUser = userRepository.save(u);
        }
        Long riskUserId = riskUser.getId();

        // 2) 三条小额同地址订单（30 分钟内连下），为最后一条触发 BURST_ORDERS 提供数据
        LocalDateTime today2am = LocalDateTime.now().toLocalDate().atTime(2, 0);
        Order burst1 = new Order();
        burst1.setOrderNo("O20260908083");
        burst1.setUserId(riskUserId);
        burst1.setTotalAmount(new BigDecimal("99.00"));
        burst1.setPayAmount(new BigDecimal("99.00"));
        burst1.setStatus(OrderStatus.PENDING_PAYMENT);
        burst1.setReceiverName("风险测试账户");
        burst1.setReceiverPhone("13900000086");
        burst1.setReceiverAddress("北京市朝阳区望京 SOHO T3 楼下快递柜");
        burst1.setRemark("(高风险演示)");
        burst1.setCreatedAt(today2am.plusMinutes(0));
        orderRepository.save(burst1);

        Order burst2 = new Order();
        burst2.setOrderNo("O20260908084");
        burst2.setUserId(riskUserId);
        burst2.setTotalAmount(new BigDecimal("58.00"));
        burst2.setPayAmount(new BigDecimal("58.00"));
        burst2.setStatus(OrderStatus.PENDING_PAYMENT);
        burst2.setReceiverName("风险测试账户");
        burst2.setReceiverPhone("13900000086");
        burst2.setReceiverAddress("北京市朝阳区望京 SOHO T3 楼下菜鸟驿站");
        burst2.setRemark("(高风险演示)");
        burst2.setCreatedAt(today2am.plusMinutes(15));
        orderRepository.save(burst2);

        Order burst3 = new Order();
        burst3.setOrderNo("O20260908085");
        burst3.setUserId(riskUserId);
        burst3.setTotalAmount(new BigDecimal("128.00"));
        burst3.setPayAmount(new BigDecimal("128.00"));
        burst3.setStatus(OrderStatus.PENDING_PAYMENT);
        burst3.setReceiverName("风险测试账户");
        burst3.setReceiverPhone("13900000086");
        burst3.setReceiverAddress("北京市朝阳区望京 SOHO T3 中转站");
        burst3.setRemark("(高风险演示)");
        burst3.setCreatedAt(today2am.plusMinutes(30));
        orderRepository.save(burst3);

        // 3) 主高风险单：凌晨 02:45 + 快递柜地址 + 大额 ¥1888，新用户首单
        Order hr = new Order();
        hr.setOrderNo(HIGH_RISK_NO);
        hr.setUserId(riskUserId);
        hr.setTotalAmount(new BigDecimal("1888.00"));
        hr.setPayAmount(new BigDecimal("1888.00"));
        hr.setDiscountAmount(BigDecimal.ZERO);
        hr.setStatus(OrderStatus.PENDING_PAYMENT);
        hr.setReceiverName("风险测试账户");
        hr.setReceiverPhone("13900000086");
        hr.setReceiverAddress("北京市朝阳区望京 SOHO T3 楼下快递柜（勿放丢失）");
        hr.setRemark("(高风险演示：大额+凌晨+敏感地址)");
        hr.setCreatedAt(today2am.plusMinutes(45));
        // 直接预置风险字段（与真实规则命中一致）
        hr.setRiskLevel(RiskLevel.HIGH);
        hr.setRiskScore(85);
        hr.setRiskScannedAt(LocalDateTime.now());
        orderRepository.save(hr);

        // 4) 写入对应的 risk_scores 评分记录（供风险监控页直接展示）
        RiskScore rs = new RiskScore();
        rs.setOrderId(hr.getId());
        rs.setScore(85);
        rs.setLevel(RiskLevel.HIGH);
        rs.setReasons(buildHighRiskReasonsJson());
        rs.setSuggestedAction("暂停发货");
        rs.setSource(RiskScore.Source.RULE);
        rs.setIsRead(Boolean.FALSE);
        rs.setCreatedAt(LocalDateTime.now());
        riskScoreRepository.save(rs);

        log.info("已写入 1 条高风险演示订单（订单号 {}，HIGH 85 分）及其评分记录", HIGH_RISK_NO);
    }

    /** 高风险演示订单的 reasons JSON（与规则引擎命中一致：FIRST_BIG + LATE_NIGHT + ADDRESS_SUSPICIOUS + BURST_ORDERS）。 */
    private String buildHighRiskReasonsJson() {
        try {
            ObjectMapper om = new ObjectMapper();
            Map<String, Object> root = new LinkedHashMap<>();
            root.put("ruleScore", 85);
            List<Map<String, Object>> signals = new ArrayList<>();
            signals.add(Map.of("code", "FIRST_BIG",
                    "message", "新账户首单金额 1888.00 > 500（首单大额）", "weight", 25));
            signals.add(Map.of("code", "LATE_NIGHT",
                    "message", "下单时间 02:45 属于凌晨时段（0-5 点）", "weight", 10));
            signals.add(Map.of("code", "ADDRESS_SUSPICIOUS",
                    "message", "收货地址含敏感词「快递柜」（疑似代收/快递柜）", "weight", 15));
            signals.add(Map.of("code", "BURST_ORDERS",
                    "message", "最近 1 小时内该用户已下 4 单", "weight", 35));
            root.put("signals", signals);
            return om.writeValueAsString(root);
        } catch (Exception e) {
            return "{\"ruleScore\":85}";
        }
    }

    /** 评论种子（4 条）：覆盖 5/4/3 三个评分等级 + AI 分析的正面/中性/负面。 */
    private void seedReviews() {
        if (reviewRepository.count() > 0) return;
        saveReview(1L, 5L, "testuser", 5, "脆甜好吃，果香浓郁，包装也很严实。已回购多次，强烈推荐！", "正面", "普通",
                "用户多次回购且表达强烈推荐，整体情感正面。", LocalDateTime.now().minusDays(3));
        saveReview(7L, 4L, "csuser",  4, "东北大米确实香，煮饭颗粒分明。物流稍微慢了一天。", "中性", "普通",
                "对商品品质满意但对物流时效存在轻微不满，整体中性。", LocalDateTime.now().minusDays(2));
        saveReview(4L, 2L, "12",       3, "五花肉肥瘦不太均匀，有一块肥肉偏多。", "负面", "需关注",
                "用户对肥瘦比例有明确不满，建议运营与供应商沟通品控。", LocalDateTime.now().minusDays(1).minusHours(6));
        saveReview(9L, 3L, "opsuser",  5, "纯牛奶味道不错，小孩很爱喝，日期新鲜。", "正面", "普通",
                "用户对口感与新鲜度均满意，且有持续购买意愿。", LocalDateTime.now().minusHours(12));
        log.info("已写入 4 条评论种子（含 3 条 AI 已分析）");
    }

    private void saveReview(Long productId, Long userId, String userName, int rating,
                            String content, String sentiment, String urgency, String reason,
                            LocalDateTime analyzedAt) {
        if (reviewRepository.existsByUserIdAndProductId(userId, productId)) return;
        Review r = new Review();
        r.setProductId(productId);
        r.setUserId(userId);
        r.setUserName(userName);
        r.setRating(BigDecimal.valueOf(rating));
        r.setContent(content);
        r.setAiSentiment(sentiment);
        r.setAiUrgency(urgency);
        r.setAiReason(reason);
        r.setAiAnalyzedAt(analyzedAt);
        reviewRepository.save(r);
    }

    /**
     * 支付种子（4 条）：覆盖 3 种支付方式 + 成功/失败/已退款 3 种状态。
     * 与订单 2/3/4/5 一一对应。
     */
    private void seedPayments() {
        if (paymentRepository.count() > 0) return;
        Order o2 = orderRepository.findByOrderNo("O20260908002").orElse(null);
        Order o3 = orderRepository.findByOrderNo("O20260908003").orElse(null);
        Order o4 = orderRepository.findByOrderNo("O20260908004").orElse(null);
        Order o5 = orderRepository.findByOrderNo("O20260908005").orElse(null);
        if (o2 == null || o3 == null || o4 == null || o5 == null) {
            log.warn("支付种子跳过：订单数据未就绪");
            return;
        }
        // 订单 2：微信支付成功
        savePayment(o2, 2L, PaymentMethod.WECHAT, PaymentStatus.SUCCESS, "WX20260908002001", o2.getPaidAt());
        // 订单 3：支付宝成功
        savePayment(o3, 4L, PaymentMethod.ALIPAY, PaymentStatus.SUCCESS, "ALI20260908003002", o3.getPaidAt());
        // 订单 4：银行卡成功（后又退款）
        savePayment(o4, 3L, PaymentMethod.BANK,   PaymentStatus.REFUNDED, "BANK20260908004003", o4.getPaidAt());
        // 订单 5：微信成功
        savePayment(o5, 2L, PaymentMethod.WECHAT, PaymentStatus.SUCCESS, "WX20260908005004", o5.getPaidAt());
        log.info("已写入 4 条支付种子（覆盖 WECHAT/ALIPAY/BANK + 成功/已退款）");
    }

    private void savePayment(Order order, Long userId, PaymentMethod method, PaymentStatus status,
                             String tradeNo, LocalDateTime paidAt) {
        Payment p = new Payment();
        p.setOrderId(order.getId());
        p.setOrderNo(order.getOrderNo());
        p.setUserId(userId);
        p.setAmount(order.getPayAmount());
        p.setMethod(method);
        p.setStatus(status);
        p.setTradeNo(tradeNo);
        p.setPaidAt(paidAt);
        paymentRepository.save(p);
    }

    /**
     * 物流种子（3 条）：顺丰/京东/中通 三家，覆盖已发货/运输中/已送达 3 种状态。
     * 对应订单 3（已发货）、订单 4（已送达）、订单 5（运输中）。
     */
    private void seedShipments() {
        if (shipmentRepository.count() > 0) return;
        Order o3 = orderRepository.findByOrderNo("O20260908003").orElse(null);
        Order o4 = orderRepository.findByOrderNo("O20260908004").orElse(null);
        Order o5 = orderRepository.findByOrderNo("O20260908005").orElse(null);
        if (o3 == null || o4 == null || o5 == null) {
            log.warn("物流种子跳过：订单数据未就绪");
            return;
        }
        saveShipment(o3, "顺丰速运", "SF" + System.currentTimeMillis() + "0001", ShipmentStatus.SHIPPED, o3.getShippedAt(), null,
                "北京市朝阳分拣中心→上海中转中心");
        saveShipment(o4, "中通快递", "ZT" + System.currentTimeMillis() + "0002", ShipmentStatus.DELIVERED,
                o4.getShippedAt(), o4.getCompletedAt(), "已由本人签收");
        saveShipment(o5, "京东物流", "JD" + System.currentTimeMillis() + "0003", ShipmentStatus.IN_TRANSIT,
                o5.getShippedAt(), null, "广州花都分拣中心发出");
        log.info("已写入 3 条物流种子（顺丰/中通/京东，覆盖 SHIPPED/DELIVERED/IN_TRANSIT）");
    }

    private void saveShipment(Order order, String company, String trackingNo, ShipmentStatus status,
                              LocalDateTime shippedAt, LocalDateTime deliveredAt, String remark) {
        Shipment s = new Shipment();
        s.setOrderId(order.getId());
        s.setOrderNo(order.getOrderNo());
        s.setCompany(company);
        s.setTrackingNo(trackingNo);
        s.setStatus(status);
        s.setShippedAt(shippedAt);
        s.setDeliveredAt(deliveredAt);
        s.setRemark(remark);
        shipmentRepository.save(s);
    }

    /** 按促销名称查找 ID（供订单 seed 关联外键）。 */
    private Long findPromotionIdByName(String name) {
        return promotionRepository.findAll().stream()
                .filter(p -> name.equals(p.getName()))
                .map(Promotion::getId)
                .findFirst().orElse(null);
    }
}
