package com.example.myapplication.data;

import com.example.myapplication.entity.ReviewInfo;
import com.example.myapplication.remote.ApiCallback;
import com.example.myapplication.remote.ApiUtils;
import com.example.myapplication.remote.DtoMapper;
import com.example.myapplication.remote.RetrofitClient;
import com.example.myapplication.remote.dto.AddReplyRequest;
import com.example.myapplication.remote.dto.AddReviewRequest;
import com.example.myapplication.remote.dto.ReviewDTO;
import com.example.myapplication.remote.dto.ReviewReplyDTO;
import com.example.myapplication.remote.dto.ReviewStatsDTO;

import java.util.List;

/**
 * 评论数据访问的远程实现。
 * <p>
 * 后端评论与追评分属不同接口，这里在查询商品评论时自动聚合每条评论的追评列表。
 */
public class RemoteReviewRepository implements ReviewRepository {

    @Override
    public void addReview(int productId, double rating, String content, final ApiCallback<Boolean> cb) {
        AddReviewRequest req = new AddReviewRequest();
        req.productId = productId;
        req.rating = rating;
        req.content = content;
        ApiUtils.enqueue(RetrofitClient.api().addReview(req),
                new ApiCallback<ReviewDTO>() {
                    @Override
                    public void onSuccess(ReviewDTO review) {
                        cb.onSuccess(true);
                    }

                    @Override
                    public void onError(int code, String message) {
                        cb.onError(code, message);
                    }
                });
    }

    @Override
    public void getReviewsByProduct(int productId, final ApiCallback<List<ReviewInfo>> cb) {
        ApiUtils.enqueue(RetrofitClient.api().reviewsByProduct(productId),
                new ApiCallback<List<ReviewDTO>>() {
                    @Override
                    public void onSuccess(List<ReviewDTO> reviews) {
                        final List<ReviewInfo> result = DtoMapper.toReviewList(reviews);
                        if (result.isEmpty()) {
                            cb.onSuccess(result);
                            return;
                        }
                        final int[] pending = {result.size()};
                        for (final ReviewInfo ri : result) {
                            ApiUtils.enqueue(RetrofitClient.api().reviewReplies(ri.getId()),
                                    new ApiCallback<List<ReviewReplyDTO>>() {
                                        @Override
                                        public void onSuccess(List<ReviewReplyDTO> replies) {
                                            ri.setReplies(DtoMapper.toReplyList(replies));
                                            if (--pending[0] == 0) {
                                                cb.onSuccess(result);
                                            }
                                        }

                                        @Override
                                        public void onError(int code, String message) {
                                            ri.setReplies(DtoMapper.toReplyList(null));
                                            if (--pending[0] == 0) {
                                                cb.onSuccess(result);
                                            }
                                        }
                                    });
                        }
                    }

                    @Override
                    public void onError(int code, String message) {
                        cb.onError(code, message);
                    }
                });
    }

    @Override
    public void getReviewCount(int productId, final ApiCallback<Integer> cb) {
        ApiUtils.enqueue(RetrofitClient.api().reviewStats(productId),
                new ApiCallback<ReviewStatsDTO>() {
                    @Override
                    public void onSuccess(ReviewStatsDTO stats) {
                        int count = (stats != null && stats.count != null) ? stats.count : 0;
                        cb.onSuccess(count);
                    }

                    @Override
                    public void onError(int code, String message) {
                        cb.onError(code, message);
                    }
                });
    }

    @Override
    public void getAverageRating(int productId, final ApiCallback<Double> cb) {
        ApiUtils.enqueue(RetrofitClient.api().reviewStats(productId),
                new ApiCallback<ReviewStatsDTO>() {
                    @Override
                    public void onSuccess(ReviewStatsDTO stats) {
                        double avg = (stats != null && stats.averageRating != null) ? stats.averageRating : 0.0;
                        cb.onSuccess(avg);
                    }

                    @Override
                    public void onError(int code, String message) {
                        cb.onError(code, message);
                    }
                });
    }

    @Override
    public void addReviewReply(int reviewId, String content, final ApiCallback<Boolean> cb) {
        ApiUtils.enqueue(RetrofitClient.api().addReply(reviewId, new AddReplyRequest(content)),
                new ApiCallback<ReviewReplyDTO>() {
                    @Override
                    public void onSuccess(ReviewReplyDTO reply) {
                        cb.onSuccess(true);
                    }

                    @Override
                    public void onError(int code, String message) {
                        cb.onError(code, message);
                    }
                });
    }
}
