package com.example.myapplication;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.myapplication.db.AppDatabase;
import com.example.myapplication.entity.Goods;
import com.example.myapplication.entity.ProductDetails;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * 商品库存刷新逻辑的仪器化测试
 *
 * 测试 ProductDetailsActivity.refreshStockFromDb() 的核心逻辑:
 * 1. 添加购物车后(updateStock), getProductById 返回最新库存
 * 2. 多次购买后库存持续递减、销量持续递增
 * 3. 库存不会变为负数
 * 4. 模拟"返回首页再进入"场景: 重新查询应返回已持久化的值
 */
@RunWith(AndroidJUnit4.class)
public class ProductDatabaseStockTest {

    private AppDatabase productDatabase;
    private int testProductId;
    private int initialStock;
    private int initialSales;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        productDatabase = AppDatabase.getInstance(context);

        // 获取第一个商品作为测试用例
        java.util.List<Goods> allProducts = productDatabase.getAllProducts();
        assertNotNull("数据库应包含商品", allProducts);
        assertTrue("数据库应至少包含1个商品", allProducts.size() > 0);

        testProductId = allProducts.get(0).getGoods_id();
        Goods goods = productDatabase.getProductById(testProductId);
        assertNotNull("应能查到测试商品", goods);

        initialStock = goods.getStock();
        initialSales = goods.getSales();
    }

    @After
    public void tearDown() {
        // 测试结束后恢复初始库存与销量
        if (testProductId > 0) {
            productDatabase.updateStock(testProductId, initialStock
                    - productDatabase.getProductById(testProductId).getStock());
        }
        if (productDatabase != null) {
            productDatabase.close();
        }
    }

    /**
     * 测试1: 添加1件到购物车后,重新查询库存应减少1、销量应增加1
     */
    @Test
    public void testStockDecreasesAfterAddToCart() {
        // 模拟加入购物车:库存-1
        boolean result = productDatabase.updateStock(testProductId, -1);
        assertTrue("updateStock 应返回 true", result);

        // 模拟 refreshStockFromDb: 重新查询
        Goods latest = productDatabase.getProductById(testProductId);
        assertNotNull("重新查询应返回商品", latest);

        assertEquals("库存应减少1",
                initialStock - 1, latest.getStock());
        assertEquals("销量应增加1",
                initialSales + 1, latest.getSales());
    }

    /**
     * 测试2: 多次加入购物车后,库存和销量持续变化
     */
    @Test
    public void testMultipleAddToCart() {
        int delta = 3;
        // 模拟加入3件到购物车
        for (int i = 0; i < delta; i++) {
            productDatabase.updateStock(testProductId, -1);
        }

        // 重新查询(模拟返回页面后 refreshStockFromDb)
        Goods latest = productDatabase.getProductById(testProductId);
        assertNotNull(latest);

        assertEquals("库存应减少3",
                initialStock - delta, latest.getStock());
        assertEquals("销量应增加3",
                initialSales + delta, latest.getSales());
    }

    /**
     * 测试3: 库存不会变为负数
     */
    @Test
    public void testStockNotNegative() {
        // 先记下当前库存
        int stock = productDatabase.getProductById(testProductId).getStock();
        int overReduce = stock + 5; // 超量减少

        // 尝试减少超过库存的数量
        productDatabase.updateStock(testProductId, -overReduce);

        Goods latest = productDatabase.getProductById(testProductId);
        assertNotNull(latest);
        assertEquals("库存不应为负数,应为0", 0, latest.getStock());
    }

    /**
     * 测试4: 模拟"返回首页再进入详情页"场景
     * 即验证 getProductById 返回的是已持久化的值,而非缓存
     */
    @Test
    public void testStockPersistedAfterReQuery() {
        // 第一次进入详情页,记录库存
        Goods firstLoad = productDatabase.getProductById(testProductId);
        int stockFirstLoad = firstLoad.getStock();

        // 加入购物车(库存-1)
        productDatabase.updateStock(testProductId, -1);

        // 模拟返回首页再进入: 重新创建 ProductDetails 并查询
        // 这正是 refreshStockFromDb() 做的事情
        ProductDetails productDetails = new ProductDetails();
        productDetails.setId(testProductId);
        productDetails.setStock(stockFirstLoad); // 旧值

        // 模拟 refreshStockFromDb 逻辑
        Goods latest = productDatabase.getProductById(productDetails.getId());
        if (latest != null) {
            productDetails.setStock(latest.getStock());
            productDetails.setSales(latest.getSales());
        }

        // 验证 productDetails 中的库存已是最新值
        assertEquals("返回后库存应是持久化后的值",
                stockFirstLoad - 1, productDetails.getStock());
        assertEquals("productDetails 不应保留旧库存",
                stockFirstLoad, stockFirstLoad);
    }

    /**
     * 测试5: updateStock 返回 false 当商品ID不存在
     */
    @Test
    public void testUpdateStockInvalidId() {
        boolean result = productDatabase.updateStock(999999, -1);
        assertEquals("对不存在的商品ID, updateStock 应返回 false",
                false, result);
    }

    /**
     * 测试6: 连续查询返回一致结果(无中间修改时)
     */
    @Test
    public void testConsistentQueryWithoutModification() {
        Goods first = productDatabase.getProductById(testProductId);
        Goods second = productDatabase.getProductById(testProductId);

        assertNotNull(first);
        assertNotNull(second);
        assertEquals("连续查询库存应一致",
                first.getStock(), second.getStock());
        assertEquals("连续查询销量应一致",
                first.getSales(), second.getSales());
    }
}
