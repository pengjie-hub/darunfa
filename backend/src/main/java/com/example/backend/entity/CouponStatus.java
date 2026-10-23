package com.example.backend.entity;

/**
 * 用户优惠券的持券状态。
 * <p>
 * 只落库「未使用 / 已使用」两态；「已过期」是**派生状态**（由 {@code expireTime}
 * 与当前时间比较得出），不入库 —— 否则每天都要跑定时任务刷状态，
 * 而且一旦定时任务没跑，展示就会说谎。
 */
public enum CouponStatus {

    UNUSED("未使用"),
    USED("已使用");

    private final String label;

    CouponStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
