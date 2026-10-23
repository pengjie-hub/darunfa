package com.example.myapplication.data;

import com.example.myapplication.entity.ReviewInfo;
import com.example.myapplication.remote.ApiCallback;

import java.util.List;

/**
 * 评论数据访问接口（远程版，异步回调）。
 * <p>
 * 职责边界：只负责「商品评论与追评」的读写——
 * 发表评论、按商品查询评论（含追评）、评论数量与平均评分、发表追评。
 * 后端在发表评论时校验「同一用户同一商品仅一条」，前端据此在 UI 上做提示。
 * 不涉及商品、订单、用户。
 */
public interface ReviewRepository {

    void addReview(int productId, double rating, String content, ApiCallback<Boolean> cb);

    /** 查询商品全部评论，每条评论已附加其追评列表 */
    void getReviewsByProduct(int productId, ApiCallback<List<ReviewInfo>> cb);

    void getReviewCount(int productId, ApiCallback<Integer> cb);

    void getAverageRating(int productId, ApiCallback<Double> cb);

    void addReviewReply(int reviewId, String content, ApiCallback<Boolean> cb);
}
