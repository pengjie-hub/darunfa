package com.example.backend.service;

import com.example.backend.common.BusinessException;
import com.example.backend.dto.PromotionRequest;
import com.example.backend.entity.Promotion;
import com.example.backend.entity.PromotionType;
import com.example.backend.repository.PromotionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 促销活动服务：CRUD + 下单时的优惠计算。
 */
@Service
public class PromotionService {

    private final PromotionRepository promotionRepository;

    public PromotionService(PromotionRepository promotionRepository) {
        this.promotionRepository = promotionRepository;
    }

    public List<Promotion> listAll() {
        return promotionRepository.findAllByOrderByCreatedAtDesc();
    }

    @Transactional
    public Promotion create(PromotionRequest req) {
        validate(req);
        Promotion p = new Promotion();
        applyRequest(p, req);
        return promotionRepository.save(p);
    }

    @Transactional
    public Promotion update(Long id, PromotionRequest req) {
        Promotion p = promotionRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "促销活动不存在"));
        validate(req);
        applyRequest(p, req);
        return promotionRepository.save(p);
    }

    @Transactional
    public void delete(Long id) {
        if (!promotionRepository.existsById(id)) {
            throw new BusinessException(404, "促销活动不存在");
        }
        promotionRepository.deleteById(id);
    }

    /**
     * 计算某商品/全店在当前时刻可享的促销优惠。
     *
     * @param totalAmount 订单商品总额
     * @return 优惠结果（含减免金额与命中的促销，可能为空）
     */
    public Optional<PromotionHit> computeDiscount(BigDecimal totalAmount) {
        LocalDateTime now = LocalDateTime.now();
        List<Promotion> actives = promotionRepository.findByStatusOrderByCreatedAtDesc(1).stream()
                .filter(p -> isActive(p, now))
                .toList();

        Promotion best = null;
        BigDecimal bestReduce = BigDecimal.ZERO;

        for (Promotion p : actives) {
            if (totalAmount.compareTo(p.getThreshold()) < 0) {
                continue; // 未达门槛
            }
            BigDecimal reduce;
            if (p.getType() == PromotionType.FULL_REDUCTION) {
                reduce = p.getDiscount();
            } else { // DISCOUNT：减免 = 总额 * (1 - 折扣率)
                BigDecimal rate = p.getDiscount();
                reduce = totalAmount.multiply(BigDecimal.ONE.subtract(rate))
                        .setScale(2, RoundingMode.HALF_UP);
            }
            if (reduce.compareTo(bestReduce) > 0) {
                bestReduce = reduce;
                best = p;
            }
        }

        if (best == null) {
            return Optional.empty();
        }
        if (bestReduce.compareTo(totalAmount) > 0) {
            bestReduce = totalAmount; // 优惠不得超过总额
        }
        return Optional.of(new PromotionHit(best, bestReduce));
    }

    /**
     * 领券用：取出「可领取」的活动。
     * <p>
     * 「可领取」= 存在 + 状态为启用 + 处于有效期内。
     * 把这条判断收在促销服务里，避免领券模块自己再写一遍时间窗逻辑而出现分歧。
     */
    public Promotion getClaimable(Long promotionId) {
        Promotion p = promotionRepository.findById(promotionId)
                .orElseThrow(() -> new BusinessException(404, "活动不存在"));
        if (p.getStatus() == null || p.getStatus() != 1) {
            throw new BusinessException(400, "该活动已停用，暂时不能领取");
        }
        if (!isActive(p, LocalDateTime.now())) {
            throw new BusinessException(400, "该活动不在有效期内，暂时不能领取");
        }
        return p;
    }

    /**
     * 「再买多少可享优惠」提示。
     * <p>
     * 用途：购物车/收银台在**未达门槛**时给用户一个加购理由。
     * 比如总额 ¥49.50、活动是「满 100 减 10」，就返回
     * 「再买 ¥50.5 可享「满 100 减 10（全场通用）」」。
     * <p>
     * 多个未达门槛的满减活动时，取**离得最近**的那个（差额最小），
     * 因为那是用户最容易达成的。
     *
     * @return 提示文案；已达门槛、或没有任何未达标的满减活动时返回 null
     */
    public String nextTierHint(BigDecimal totalAmount) {
        if (totalAmount == null) return null;
        LocalDateTime now = LocalDateTime.now();

        Promotion best = null;
        BigDecimal bestGap = null;

        for (Promotion p : promotionRepository.findByStatusOrderByCreatedAtDesc(1)) {
            if (!isActive(p, now)) continue;
            // 只对满减做提示：折扣类没有「差多少到门槛」的概念，门槛通常也是 0
            if (p.getType() != PromotionType.FULL_REDUCTION) continue;
            BigDecimal th = p.getThreshold();
            if (th == null || th.compareTo(BigDecimal.ZERO) <= 0) continue;
            if (totalAmount.compareTo(th) >= 0) continue;   // 已达标，不用提示

            BigDecimal gap = th.subtract(totalAmount);
            if (bestGap == null || gap.compareTo(bestGap) < 0) {
                bestGap = gap;
                best = p;
            }
        }
        if (best == null) return null;
        return "再买 ¥" + bestGap.stripTrailingZeros().toPlainString()
                + " 可享「" + best.getName() + "」";
    }

    private boolean isActive(Promotion p, LocalDateTime now) {
        if (p.getStartTime() != null && now.isBefore(p.getStartTime())) return false;
        if (p.getEndTime() != null && now.isAfter(p.getEndTime())) return false;
        return true;
    }

    /**
     * 字段校验（<b>可复用版本</b>，供「对话式建活动」的预检直接调用）。
     *
     * <h3>为什么必须抽出来，而不是让工具自己再写一套</h3>
     * 预检与创建如果各写一份校验，两套规则一定会漂移：表现为「卡片说参数合法、点了确认才报错」
     * 或更糟的「卡片拦住了本来合法的活动」。抽出本方法后，
     * {@code create/update → validate → validateForPreview}，
     * 小运的 {@code prepare_promotion → validateForPreview}，
     * <b>物理上只有一份实现</b>，口径不可能分叉。
     *
     * <p>文案与改造前逐字一致（前端展示依赖它）；本次仅补上细化错误码 {@code subCode}，
     * 通过两参构造的历史抛点行为不变。
     *
     * @throws BusinessException 400 + {@code PROMOTION_DISCOUNT_INVALID} 折扣率/满减金额非法；
     *                           400 + {@code PROMOTION_TIME_INVALID} 时间窗非法
     */
    public void validateForPreview(PromotionRequest req) {
        if (req == null) {
            throw new BusinessException(400, "PARAM_MISSING", "活动参数不能为空");
        }
        if (req.getType() == PromotionType.DISCOUNT) {
            BigDecimal d = req.getDiscount();
            // 注意这里是 compareTo(ONE) > 0，不是 > 1。
            // BigDecimal.compareTo 只返回 -1 / 0 / 1，写成 `> 1` 永远为 false，
            // 于是 1.5（150% 折扣，等于倒贴钱）也能建成功 —— 这是既有缺陷（QA Q-4）。
            // 正确语义：折扣率必须落在 (0, 1] 区间，> 1 一律拒绝。
            if (d == null || d.compareTo(BigDecimal.ZERO) <= 0 || d.compareTo(BigDecimal.ONE) > 0) {
                throw new BusinessException(400, "PROMOTION_DISCOUNT_INVALID",
                        "折扣率必须在 (0, 1] 之间，如 0.9 表示 9 折");
            }
        } else {
            BigDecimal d = req.getDiscount();
            if (d == null || d.compareTo(BigDecimal.ZERO) < 0) {
                throw new BusinessException(400, "PROMOTION_DISCOUNT_INVALID", "满减金额不能为负");
            }
        }
        if (req.getEndTime() != null && req.getStartTime() != null
                && req.getEndTime().isBefore(req.getStartTime())) {
            throw new BusinessException(400, "PROMOTION_TIME_INVALID", "结束时间不能早于开始时间");
        }
    }

    /**
     * 创建/更新前的校验。<b>本方法故意只做委托</b> —— 见 {@link #validateForPreview(PromotionRequest)}
     * 的说明：两份实现就是两次漂移的机会，这里连一句校验都不许多写。
     */
    private void validate(PromotionRequest req) {
        validateForPreview(req);
    }

    /**
     * 同源查重：找出与待创建活动「参数相同且时间窗重叠」的既有活动。
     *
     * <h3>为什么只警告不拦截</h3>
     * 同源活动在结算时由 {@code DiscountService} 按「同源只算一次、取更优一档」处理，
     * 因此重复创建<b>不会让用户多占便宜</b>，后果只是后台多一条脏数据。
     * 直接硬拦会把「我就是想再建一条做对比」这种合理需求堵死，所以设计上是
     * 「预检标记 + 卡片二次确认」（见技术设计 5.4）。
     *
     * <p>「同源」判定与结算口径一致：同 {@code type + threshold + discount + productId}；
     * 在此基础上要求时间窗重叠（首尾相接不算重叠，因为结算时两者不会同时命中同一笔订单）。
     *
     * @param req 待创建的活动
     * @return 命中的既有活动（可能为空）；参数不全时返回空列表（此时应由校验先报错）
     */
    public List<Promotion> findDuplicates(PromotionRequest req) {
        if (req == null || req.getType() == null || req.getDiscount() == null) {
            return List.of();
        }
        BigDecimal threshold = req.getThreshold() == null ? BigDecimal.ZERO : req.getThreshold();
        List<Promotion> sameSource = promotionRepository
                .findByTypeAndThresholdAndDiscountAndProductId(
                        req.getType(), threshold, req.getDiscount(), req.getProductId());
        if (sameSource == null || sameSource.isEmpty()) {
            return List.of();
        }
        return sameSource.stream()
                // 停用的活动不参与结算，自然不会造成重复优惠，无需提醒
                .filter(p -> p.getStatus() == null || p.getStatus() == 1)
                .filter(p -> timeOverlaps(p.getStartTime(), p.getEndTime(), req.getStartTime(), req.getEndTime()))
                .toList();
    }

    /**
     * 两个时间窗是否重叠；任一端为 null 视为「不限」，即与另一方必然重叠。
     */
    private static boolean timeOverlaps(LocalDateTime aStart, LocalDateTime aEnd,
                                        LocalDateTime bStart, LocalDateTime bEnd) {
        boolean leftOk = (aEnd == null) || (bStart == null) || !bStart.isAfter(aEnd);
        boolean rightOk = (aStart == null) || (bEnd == null) || !bEnd.isBefore(aStart);
        return leftOk && rightOk;
    }

    private void applyRequest(Promotion p, PromotionRequest req) {
        p.setName(req.getName());
        p.setType(req.getType());
        p.setThreshold(req.getThreshold() == null ? BigDecimal.ZERO : req.getThreshold());
        p.setDiscount(req.getDiscount());
        p.setProductId(req.getProductId());
        p.setImageUrl(req.getImageUrl());
        p.setDescription(req.getDescription());
        p.setLink(req.getLink());
        p.setStartTime(req.getStartTime());
        p.setEndTime(req.getEndTime());
        p.setStatus(req.getStatus() == null ? 1 : req.getStatus());
    }

    // ==================== 前台展示 ====================

    /**
     * 列出当前"生效中"的促销活动，供前台展示。
     * <p>
     * 过滤条件（两者同时满足）：
     * <ol>
     *   <li>{@code status == 1}（已启用）；</li>
     *   <li>当前时间落在 {@code [startTime, endTime]} 区间内（两端为 null 视为不限）。</li>
     * </ol>
     * 排序：优先按结束时间升序（快结束的排前面，制造紧迫感），
     * 无结束时间的排最后，其次按创建时间倒序。
     */
    public List<Promotion> listActive() {
        LocalDateTime now = LocalDateTime.now();
        return promotionRepository.findByStatusOrderByCreatedAtDesc(1).stream()
                .filter(p -> isActive(p, now))
                .sorted((a, b) -> {
                    LocalDateTime ea = a.getEndTime();
                    LocalDateTime eb = b.getEndTime();
                    if (ea == null && eb == null) {
                        return Long.compare(
                                b.getId() == null ? 0 : b.getId(),
                                a.getId() == null ? 0 : a.getId());
                    }
                    if (ea == null) return 1;
                    if (eb == null) return -1;
                    return ea.compareTo(eb);
                })
                .toList();
    }

    /**
     * 优惠计算结果：命中的促销 + 减免金额。
     */
    public record PromotionHit(Promotion promotion, BigDecimal reduceAmount) {
    }
}
