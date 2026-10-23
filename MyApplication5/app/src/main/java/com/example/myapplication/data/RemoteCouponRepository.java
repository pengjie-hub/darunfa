package com.example.myapplication.data;

import com.example.myapplication.remote.ApiCallback;
import com.example.myapplication.remote.ApiUtils;
import com.example.myapplication.remote.RetrofitClient;
import com.example.myapplication.remote.dto.CheckoutPreviewDTO;
import com.example.myapplication.remote.dto.ClaimCouponRequest;
import com.example.myapplication.remote.dto.CouponDTO;

import java.util.List;

/**
 * 优惠券与优惠试算的远程实现。
 */
public class RemoteCouponRepository implements CouponRepository {

    @Override
    public void claim(long promotionId, final ApiCallback<CouponDTO> cb) {
        ApiUtils.enqueue(RetrofitClient.api().claimCoupon(new ClaimCouponRequest(promotionId)), cb);
    }

    @Override
    public void mine(final ApiCallback<List<CouponDTO>> cb) {
        ApiUtils.enqueue(RetrofitClient.api().myCoupons(), cb);
    }

    @Override
    public void available(double amount, final ApiCallback<List<CouponDTO>> cb) {
        ApiUtils.enqueue(RetrofitClient.api().availableCoupons(amount), cb);
    }

    @Override
    public void count(final ApiCallback<Long> cb) {
        ApiUtils.enqueue(RetrofitClient.api().couponCount(), cb);
    }

    @Override
    public void preview(double amount, Long couponId, final ApiCallback<CheckoutPreviewDTO> cb) {
        ApiUtils.enqueue(RetrofitClient.api().calcDiscount(amount, couponId), cb);
    }
}
