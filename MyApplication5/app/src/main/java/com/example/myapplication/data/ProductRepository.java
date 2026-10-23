package com.example.myapplication.data;

import com.example.myapplication.entity.Goods;
import com.example.myapplication.remote.ApiCallback;

import java.util.List;

/**
 * 商品数据访问接口（远程版，异步回调）。
 * <p>
 * 职责边界：只负责「商品目录与库存」的读取——
 * 按板块/分类/子分类/关键字查询商品、获取分类与子分类、按 id 查询商品详情。
 * 库存扣减由后端在下单/加购时原子完成，前端不再手动调整库存。
 * 不涉及评论、购物车、订单、用户。
 */
public interface ProductRepository {

    void getProductsBySection(String section, ApiCallback<List<Goods>> cb);

    void getAllProducts(ApiCallback<List<Goods>> cb);

    void getProductsByCategory(String category, ApiCallback<List<Goods>> cb);

    void getSubCategories(String category, ApiCallback<List<String>> cb);

    void getCategories(ApiCallback<List<String>> cb);

    void getProductsByCatAndSub(String category, String subCategory, ApiCallback<List<Goods>> cb);

    void getProductById(int id, ApiCallback<Goods> cb);

    void searchProducts(String keyword, ApiCallback<List<Goods>> cb);
}
