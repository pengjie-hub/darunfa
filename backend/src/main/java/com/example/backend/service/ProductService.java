package com.example.backend.service;

import com.example.backend.common.BusinessException;
import com.example.backend.dto.ProductRequest;
import com.example.backend.entity.Category;
import com.example.backend.entity.Product;
import com.example.backend.repository.CategoryRepository;
import com.example.backend.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 商品与分类服务。
 */
@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;

    public ProductService(ProductRepository productRepository, CategoryRepository categoryRepository) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
    }

    // ---------- 分类 ----------

    /**
     * 前台可见的分类 = <b>自身启用，且所有祖先分类也都启用</b>。
     * <p>
     * 只按 {@code status} 过滤是不够的：停用一个一级分类后，它下面的二级分类仍然是
     * {@code status=1}，于是会作为「孤儿」继续出现在 {@code /api/categories} 里。
     * 前台是把这份平铺列表按 {@code parentId} 组成两级树的，所以这些孤儿虽然因为
     * 「一级入口已经没了」而不会被展示，却一直裸露在接口返回中 ——
     * 任何按分类名反查 ID 的地方都可能误命中，语义上也不对。
     * <p>
     * 这里统一按「祖先链全启用」判定，停用一级分类即等于停用其整棵子树。
     */
    public List<Category> listCategories() {
        // 必须连「已停用」的分类一起拿，才能顺着 parentId 判断祖先的状态
        Map<Long, Category> byId = categoryRepository.findAll().stream()
                .collect(Collectors.toMap(Category::getId, c -> c, (a, b) -> a));
        return categoryRepository.findByStatusOrderBySortOrderAsc(1).stream()
                .filter(c -> ancestorsAllEnabled(c, byId))
                .toList();
    }

    /**
     * 该分类的所有祖先是否都处于启用状态。
     *
     * @param byId 全部分类（含已停用）的 id 索引
     * @return 祖先链完整且全启用为 true；出现断链（祖先已被删除）或祖先被停用为 false
     */
    private static boolean ancestorsAllEnabled(Category category, Map<Long, Category> byId) {
        Long parentId = category.getParentId();
        // 层级保护：最多往上走 10 层，避免脏数据形成环时死循环
        for (int depth = 0; depth < 10; depth++) {
            if (parentId == null || parentId <= 0) return true;   // 已到根，链路完整
            Category parent = byId.get(parentId);
            if (parent == null) return false;                     // 父级已不存在：断链，视为不可见
            if (parent.getStatus() == null || parent.getStatus() != 1) return false;
            parentId = parent.getParentId();
        }
        return false;   // 疑似成环
    }

    // ---------- 商品浏览 ----------

    public List<Product> listProducts(Long categoryId, String keyword) {
        List<Product> products;
        if (keyword != null && !keyword.isBlank()) {
            // 关键词搜索：查询库中全部上架商品，再按名称/副标题过滤（简单实现）
            products = productRepository.findByStatusOrderByIdDesc(1).stream()
                    .filter(p -> (p.getName() != null && p.getName().contains(keyword))
                            || (p.getSubtitle() != null && p.getSubtitle().contains(keyword)))
                    .toList();
        } else if (categoryId != null) {
            products = productRepository.findByCategoryIdAndStatusOrderByIdDesc(categoryId, 1);
        } else {
            products = productRepository.findByStatusOrderByIdDesc(1);
        }
        return products;
    }

    public Product getProduct(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "商品不存在"));
    }

    // ---------- 后台商品管理 ----------

    @Transactional
    public Product createProduct(ProductRequest req) {
        ensureCategoryExists(req.getCategoryId());
        Product p = new Product();
        applyRequest(p, req);
        return productRepository.save(p);
    }

    @Transactional
    public Product updateProduct(Long id, ProductRequest req) {
        Product p = getProduct(id);
        if (req.getCategoryId() != null) ensureCategoryExists(req.getCategoryId());
        applyRequest(p, req);
        return productRepository.save(p);
    }

    @Transactional
    public void deleteProduct(Long id) {
        if (!productRepository.existsById(id)) {
            throw new BusinessException(404, "商品不存在");
        }
        productRepository.deleteById(id);
    }

    /** 调整库存：delta 正为入库、负为出库。 */
    @Transactional
    public Product updateStock(Long id, int delta) {
        Product p = getProduct(id);
        int newStock = p.getStock() + delta;
        if (newStock < 0) {
            throw new BusinessException(400, "库存不足，无法扣减");
        }
        p.setStock(newStock);
        return productRepository.save(p);
    }

    /**
     * 调整库存（<b>带库存快照校验</b>）：给「对话补货卡片」与「后台批量入库」用。
     *
     * <h3>为什么不能只靠幂等键</h3>
     * 幂等键挡的是「同一张卡片点两次」。它挡不住「两个运营同时各自补 100 件」——
     * 那本来就是两次合法操作。真正要防的是<b>基于过期快照的补货</b>：
     * 运营看到「库存 7 件」点了补 100，但在他看卡片到点确认之间，订单已经把这 100 件卖掉了。
     * 于是这里用 {@code expectedStock} 做乐观并发控制：<b>当前库存必须等于用户看到的那个数</b>。
     *
     * <h3>为什么要 {@code findByIdForUpdate}</h3>
     * 「读库存 → 比对 → 写回」必须是原子的，否则两个并发请求可以同时通过比对。
     * 项目里下单扣库存已经用 {@code PESSIMISTIC_WRITE} 行锁（{@code findByIdForUpdate}），
     * 这里复用同一把锁，语义与下单扣减完全一致：<b>同一行库存的增减是串行的</b>。
     *
     * <h3>兼容承诺（验收 ⑳）</h3>
     * {@code expectedStock == null} 时<b>直接退回老方法</b>，行为与改造前逐字相同
     *（不锁行、不比对、错误文案一致）。老的前端/脚本无需任何改动。
     *
     * @param id            商品 ID
     * @param delta         增减量（正入库 / 负出库）
     * @param expectedStock 用户确认时看到的库存快照；null 表示不做并发校验（老行为）
     * @throws BusinessException 409 {@code CONFLICT_STOCK_CHANGED}：库存已变化
     *                           400 「库存不足，无法扣减」：扣减后为负
     *                           404 「商品不存在」
     */
    @Transactional
    public Product updateStock(Long id, int delta, Integer expectedStock) {
        if (expectedStock == null) {
            // 老路径：一字不改
            return updateStock(id, delta);
        }
        Product p = productRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new BusinessException(404, "TARGET_NOT_FOUND", "商品不存在"));

        int current = p.getStock() == null ? 0 : p.getStock();
        if (current != expectedStock) {
            // 让运营看到「他看到的是多少、现在是多少」，而不是一句干巴巴的冲突
            throw new BusinessException(409, "CONFLICT_STOCK_CHANGED",
                    "库存已变化（你确认时是 " + expectedStock + " 件，现在是 " + current
                            + " 件），为避免补错请刷新后重新确认。");
        }
        int newStock = current + delta;
        if (newStock < 0) {
            throw new BusinessException(400, "库存不足，无法扣减");
        }
        p.setStock(newStock);
        return productRepository.save(p);
    }

    private void applyRequest(Product p, ProductRequest req) {
        p.setCategoryId(req.getCategoryId());
        p.setName(req.getName());
        p.setSubtitle(req.getSubtitle());
        p.setDescription(req.getDescription());
        p.setPrice(req.getPrice());
        p.setOriginalPrice(req.getOriginalPrice());
        p.setStock(req.getStock() == null ? 0 : req.getStock());
        p.setUnit(req.getUnit());
        p.setImageUrl(req.getImageUrl());
        p.setImages(req.getImages());
        p.setStatus(req.getStatus() == null ? 1 : req.getStatus());
    }

    private void ensureCategoryExists(Long categoryId) {
        if (categoryId == null || !categoryRepository.existsById(categoryId)) {
            throw new BusinessException(400, "所属分类不存在");
        }
    }
}
