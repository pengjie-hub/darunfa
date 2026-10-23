package com.example.myapplication.remote;

import com.example.myapplication.remote.dto.AddCartRequest;
import com.example.myapplication.remote.dto.AddReplyRequest;
import com.example.myapplication.remote.dto.AddReviewRequest;
import com.example.myapplication.remote.dto.AddressDTO;
import com.example.myapplication.remote.dto.AddressRequest;
import com.example.myapplication.remote.dto.AiSummaryRequest;
import com.example.myapplication.remote.dto.AiSummaryResponse;
import com.example.myapplication.remote.dto.CartItemDTO;
import com.example.myapplication.remote.dto.CategoryDTO;
import com.example.myapplication.remote.dto.CheckoutPreviewDTO;
import com.example.myapplication.remote.dto.ClaimCouponRequest;
import com.example.myapplication.remote.dto.CouponDTO;
import com.example.myapplication.remote.dto.CreateOrderRequest;
import com.example.myapplication.remote.dto.CsConversationDTO;
import com.example.myapplication.remote.dto.CsMessageDTO;
import com.example.myapplication.remote.dto.CsSendRequest;
import com.example.myapplication.remote.dto.CsSendResponse;
import com.example.myapplication.remote.dto.LoginRequest;
import com.example.myapplication.remote.dto.LoginResponse;
import com.example.myapplication.remote.dto.OrderDTO;
import com.example.myapplication.remote.dto.PayRequest;
import com.example.myapplication.remote.dto.ProductDTO;
import com.example.myapplication.remote.dto.PromotionDTO;
import com.example.myapplication.remote.dto.RechargeRequest;
import com.example.myapplication.remote.dto.RefundApplyRequest;
import com.example.myapplication.remote.dto.RefundDTO;
import com.example.myapplication.remote.dto.RegisterRequest;
import com.example.myapplication.remote.dto.ReviewDTO;
import com.example.myapplication.remote.dto.ReviewReplyDTO;
import com.example.myapplication.remote.dto.ReviewStatsDTO;
import com.example.myapplication.remote.dto.UpdateCartRequest;
import com.example.myapplication.remote.dto.UpdatePasswordRequest;
import com.example.myapplication.remote.dto.UpdateProfileRequest;
import com.example.myapplication.remote.dto.UserDTO;
import com.example.myapplication.remote.dto.WalletDTO;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Path;
import retrofit2.http.Query;

/**
 * 后端 RESTful API 接口定义（与 Spring Boot 后端一一对应）。
 * 所有接口统一返回 {@code ApiResponse<T>} 包装。
 */
public interface ApiService {

    // ---------- 认证 ----------
    @POST("api/auth/register")
    Call<ApiResponse<LoginResponse>> register(@Body RegisterRequest req);

    @POST("api/auth/login")
    Call<ApiResponse<LoginResponse>> login(@Body LoginRequest req);

    // ---------- 用户 ----------
    @GET("api/user/profile")
    Call<ApiResponse<UserDTO>> profile();

    @PUT("api/user/profile")
    Call<ApiResponse<UserDTO>> updateProfile(@Body UpdateProfileRequest req);

    @PUT("api/user/password")
    Call<ApiResponse<Void>> updatePassword(@Body UpdatePasswordRequest req);

    // ---------- 商品 / 分类 ----------
    @GET("api/categories")
    Call<ApiResponse<List<CategoryDTO>>> categories();

    @GET("api/products")
    Call<ApiResponse<List<ProductDTO>>> products(@Query("categoryId") Long categoryId,
                                                 @Query("keyword") String keyword);

    @GET("api/products/{id}")
    Call<ApiResponse<ProductDTO>> productDetail(@Path("id") long id);

    // ---------- 促销活动（前台公开，仅返回启用且在有效期内的） ----------
    @GET("api/promotions")
    Call<ApiResponse<List<PromotionDTO>>> promotions();

    /**
     * 优惠试算：给定商品总额（可选一张券），返回优惠明细与应付金额。
     * <p>
     * 路径不在后端公开名单里（公开的只有 {@code GET api/promotions} 本身），
     * 因为要按 token 校验券归属，必须登录。
     */
    @GET("api/promotions/calc")
    Call<ApiResponse<CheckoutPreviewDTO>> calcDiscount(@Query("amount") double amount,
                                                       @Query("couponId") Long couponId);

    // ---------- 优惠券（需登录） ----------
    @POST("api/coupons/claim")
    Call<ApiResponse<CouponDTO>> claimCoupon(@Body ClaimCouponRequest req);

    @GET("api/coupons/mine")
    Call<ApiResponse<List<CouponDTO>>> myCoupons();

    /** 给定金额下可用的券（按优惠额从高到低）。 */
    @GET("api/coupons/available")
    Call<ApiResponse<List<CouponDTO>>> availableCoupons(@Query("amount") double amount);

    /** 我手上还能用的券张数。 */
    @GET("api/coupons/count")
    Call<ApiResponse<Long>> couponCount();

    // ---------- 收货地址（需登录，按 token 隔离） ----------
    @GET("api/addresses")
    Call<ApiResponse<List<AddressDTO>>> addresses();

    /** 默认地址；一条都没有时 data 为 null。 */
    @GET("api/addresses/default")
    Call<ApiResponse<AddressDTO>> defaultAddress();

    @POST("api/addresses")
    Call<ApiResponse<AddressDTO>> createAddress(@Body AddressRequest req);

    @PUT("api/addresses/{id}")
    Call<ApiResponse<AddressDTO>> updateAddress(@Path("id") long id, @Body AddressRequest req);

    @DELETE("api/addresses/{id}")
    Call<ApiResponse<Void>> deleteAddress(@Path("id") long id);

    @PUT("api/addresses/{id}/default")
    Call<ApiResponse<AddressDTO>> setDefaultAddress(@Path("id") long id);

    // ---------- 购物车 ----------
    @GET("api/cart")
    Call<ApiResponse<List<CartItemDTO>>> cart();

    @POST("api/cart")
    Call<ApiResponse<CartItemDTO>> addCart(@Body AddCartRequest req);

    @PUT("api/cart/{productId}")
    Call<ApiResponse<CartItemDTO>> updateCart(@Path("productId") long productId,
                                              @Body UpdateCartRequest req);

    @DELETE("api/cart/{productId}")
    Call<ApiResponse<Void>> removeCart(@Path("productId") long productId);

    @POST("api/cart/remove-batch")
    Call<ApiResponse<Void>> removeCartBatch(@Body List<Long> productIds);

    @PUT("api/cart/select-all")
    Call<ApiResponse<Void>> selectAll(@Query("selected") boolean selected);

    @DELETE("api/cart")
    Call<ApiResponse<Void>> clearCart();

    // ---------- 订单 ----------
    @POST("api/orders")
    Call<ApiResponse<OrderDTO>> createOrder(@Body CreateOrderRequest req);

    @GET("api/orders")
    Call<ApiResponse<List<OrderDTO>>> orders(@Query("status") String status);

    @GET("api/orders/{id}")
    Call<ApiResponse<OrderDTO>> orderDetail(@Path("id") long id);

    /**
     * 支付：可指定支付方式。body 省略时后端按 WECHAT 处理（向后兼容）。
     * <p>
     * {@code method=BALANCE} 时后端扣账户余额并写余额流水；余额不足返回业务错误（HTTP 200 + code=400）。
     */
    @PUT("api/orders/{id}/pay")
    Call<ApiResponse<OrderDTO>> pay(@Path("id") long id, @Body PayRequest req);

    @PUT("api/orders/{id}/cancel")
    Call<ApiResponse<OrderDTO>> cancel(@Path("id") long id);

    @PUT("api/orders/{id}/confirm")
    Call<ApiResponse<OrderDTO>> confirm(@Path("id") long id);

    /**
     * 申请退款：只提交申请，订单进入「退款中」，需商家在后台同意后才真正退回。
     * 与 cancel 的区别：cancel 是取消未发货订单（立即生效），本接口用于已付款订单退钱。
     */
    @POST("api/orders/{id}/refund")
    Call<ApiResponse<RefundDTO>> applyRefund(@Path("id") long id, @Body RefundApplyRequest req);

    // ---------- 钱包（账户余额 / 充值，需登录） ----------
    /** 查询钱包（余额 + 最近流水）。{@code limit} 为 null 时省略该参数，由后端取默认值 20。 */
    @GET("api/wallet")
    Call<ApiResponse<WalletDTO>> wallet(@Query("limit") Integer limit);

    /** 充值：成功与幂等重放都返回完整钱包。 */
    @POST("api/wallet/recharge")
    Call<ApiResponse<WalletDTO>> recharge(@Body RechargeRequest req);

    // ---------- 评论 ----------
    @POST("api/reviews")
    Call<ApiResponse<ReviewDTO>> addReview(@Body AddReviewRequest req);

    @GET("api/reviews/product/{productId}")
    Call<ApiResponse<List<ReviewDTO>>> reviewsByProduct(@Path("productId") long productId);

    @GET("api/reviews/product/{productId}/stats")
    Call<ApiResponse<ReviewStatsDTO>> reviewStats(@Path("productId") long productId);

    @GET("api/reviews/{reviewId}/replies")
    Call<ApiResponse<List<ReviewReplyDTO>>> reviewReplies(@Path("reviewId") long reviewId);

    @POST("api/reviews/{reviewId}/reply")
    Call<ApiResponse<ReviewReplyDTO>> addReply(@Path("reviewId") long reviewId,
                                               @Body AddReplyRequest req);

    // ---------- AI 评论总结 ----------
    @POST("api/ai/summary")
    Call<ApiResponse<AiSummaryResponse>> aiSummary(@Body AiSummaryRequest req);

    // ---------- 在线客服（需登录，会话按 token 隔离） ----------
    /** 取（或创建）我的客服会话。 */
    @GET("api/cs/session")
    Call<ApiResponse<CsConversationDTO>> csSession();

    /** 我的会话消息（时间正序）。 */
    @GET("api/cs/session/messages")
    Call<ApiResponse<List<CsMessageDTO>>> csMessages();

    /** 发一条消息 → 服务端调用大模型生成自动回复并一并返回。 */
    @POST("api/cs/session/messages")
    Call<ApiResponse<CsSendResponse>> csSend(@Body CsSendRequest req);

    /** 主动结束会话。 */
    @POST("api/cs/session/close")
    Call<ApiResponse<Void>> csClose();
}
