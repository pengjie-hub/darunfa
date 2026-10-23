package com.example.myapplication.data;

import java.util.List;

/**
 * 浏览历史数据访问接口。
 * <p>
 * 职责边界：只负责「用户浏览历史」的读写——
 * 记录浏览、查询最近浏览的商品 id、清空浏览历史。
 * 不涉及商品、订单、用户。
 */
public interface BrowseHistoryRepository {

    boolean addBrowseHistory(int userId, int productId, String browseTime);

    List<Integer> getBrowseHistoryProductIds(int userId, int limit);

    boolean clearBrowseHistory(int userId);
}
