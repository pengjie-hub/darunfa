package com.example.backend.repository;

import com.example.backend.entity.CouponStatus;
import com.example.backend.entity.UserCoupon;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserCouponRepository extends JpaRepository<UserCoupon, Long> {

    /** 我的全部券（新领的在前）。 */
    List<UserCoupon> findByUserIdOrderByClaimedAtDesc(Long userId);

    /** 按状态筛选（「可用券」列表用）。 */
    List<UserCoupon> findByUserIdAndStatusOrderByClaimedAtDesc(Long userId, CouponStatus status);

    /** 「同一活动限领一张」判重。 */
    Optional<UserCoupon> findByUserIdAndPromotionId(Long userId, Long promotionId);

    /** 越权防护：只能操作自己的券。 */
    Optional<UserCoupon> findByIdAndUserId(Long id, Long userId);

}
