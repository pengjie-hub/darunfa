package com.example.backend.service;

import com.example.backend.common.BusinessException;
import com.example.backend.dto.BalanceTransactionDTO;
import com.example.backend.dto.WalletDTO;
import com.example.backend.entity.BalanceTransaction;
import com.example.backend.entity.BalanceTransactionType;
import com.example.backend.entity.Payment;
import com.example.backend.entity.PaymentMethod;
import com.example.backend.entity.PaymentStatus;
import com.example.backend.entity.User;
import com.example.backend.repository.BalanceTransactionRepository;
import com.example.backend.repository.PaymentRepository;
import com.example.backend.repository.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 钱包服务：所有余额写操作的<b>唯一入口</b>。
 *
 * <h3>四条铁律（改动本类前务必读懂）</h3>
 * <ol>
 *   <li><b>唯一入口</b>：所有余额写操作必须走本类，禁止在任何其它地方直接改
 *       {@code user.balance}（本项目用 grep {@code setBalance} 应只命中 User.java 与本类）。</li>
 *   <li><b>悲观写锁</b>：先 {@link #lockUser(Long)}（{@code SELECT ... FOR UPDATE}）再读改写。
 *       悲观锁<b>必须在事务内</b>才生效，因此 {@code payOrder}/{@code refundOrderToBalance}/
 *       {@code recharge}/{@code initBalance} 都标 {@code @Transactional}，且必须<b>跨 Bean 调用</b>
 *       （{@code OrderService} → {@code WalletService}）才走 Spring 代理。</li>
 *   <li><b>幂等</b>：余额流水表 {@code biz_no} 唯一索引是最终保证。取值固定为：
 *       初始赠送 {@code INIT-{userId}}、充值 {@code RECHARGE-{requestId}}、
 *       余额支付 {@code ORDER-{orderId}-PAY}、退款返现 {@code ORDER-{orderId}-REFUND}。</li>
 *   <li><b>原子性</b>：余额、订单状态、支付流水必须在<b>同一事务</b>提交或一起回滚
 *       （由调用方 {@code @Transactional} + 本类的 {@code @Transactional} 合并为同一事务）。</li>
 * </ol>
 *
 * <h3>加锁顺序约定（防未来死锁）</h3>
 * 资金事务统一<b>先锁用户行，再改订单/流水，不锁商品行</b>。当前 {@code pay()} 不触碰商品；
 * {@code cancel()} 虽会回补商品，但资金路径与下单（先锁商品）分处不同事务、不交叉持有，故无成环风险。
 */
@Service
public class WalletService {

    /** 单笔充值上限（元）。 */
    public static final BigDecimal MAX_RECHARGE = new BigDecimal("50000.00");
    /** 钱包流水默认返回条数。 */
    public static final int DEFAULT_LIMIT = 20;
    /** 钱包流水最大返回条数。 */
    public static final int MAX_LIMIT = 100;

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final UserRepository userRepository;
    private final BalanceTransactionRepository balanceTransactionRepository;
    private final PaymentRepository paymentRepository;

    public WalletService(UserRepository userRepository,
                         BalanceTransactionRepository balanceTransactionRepository,
                         PaymentRepository paymentRepository) {
        this.userRepository = userRepository;
        this.balanceTransactionRepository = balanceTransactionRepository;
        this.paymentRepository = paymentRepository;
    }

    // ==================== 只读查询 ====================

    /**
     * 查询钱包：当前余额 + 最近 {@code limit} 条流水。
     *
     * @param limit 条数上限；空或 &lt;1 视为 {@value #DEFAULT_LIMIT}，&gt;{@value #MAX_LIMIT} 截断为 {@value #MAX_LIMIT}
     */
    @Transactional(readOnly = true)
    public WalletDTO getWallet(Long userId, Integer limit) {
        int n = normalizeLimit(limit);
        User u = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(404, "用户不存在"));
        BigDecimal balance = nz(u.getBalance());
        List<BalanceTransaction> rows =
                balanceTransactionRepository.findByUserIdOrderByIdDesc(userId, PageRequest.of(0, n));
        List<BalanceTransactionDTO> txs = new ArrayList<>();
        for (BalanceTransaction t : rows) {
            txs.add(toDTO(t));
        }
        return new WalletDTO(balance, txs);
    }

    // ==================== 写操作（均需事务 + 行锁） ====================

    /**
     * 充值：给用户余额加钱并落一条 {@code RECHARGE} 流水（幂等）。
     * <p>
     * 顺序很关键：<b>先锁用户行</b>（同时把同一用户的并发充值串行化），<b>再</b>做幂等判定。
     * 不在同事务内捕获 {@code DataIntegrityViolationException} —— 唯一键冲突会把事务标记成
     * rollback-only，之后的读也会失败。串行化交给用户行锁，{@code biz_no} 唯一索引退化为纯兜底。
     *
     * @param requestId 客户端幂等键；为空则服务端生成 UUID 兜底
     * @return 充值后的最新钱包
     */
    @Transactional
    public WalletDTO recharge(Long userId, BigDecimal amount, String requestId) {
        // 1) 金额校验（统一在此，避免与 @Valid 两处文案不一致）
        if (amount == null) {
            throw new BusinessException(400, "请输入充值金额");
        }
        if (amount.signum() <= 0) {
            throw new BusinessException(400, "充值金额必须大于 0");
        }
        if (amount.scale() > 2) {
            throw new BusinessException(400, "金额最多保留 2 位小数");
        }
        if (amount.compareTo(MAX_RECHARGE) > 0) {
            throw new BusinessException(400, "单笔充值不能超过 ¥50000");
        }
        amount = amount.setScale(2, RoundingMode.HALF_UP);

        // 2) requestId 归一化
        String rid = (requestId == null || requestId.isBlank())
                ? UUID.randomUUID().toString()
                : requestId.trim();
        String bizNo = "RECHARGE-" + rid;

        // 3) 先锁行，再幂等判定（重放直接返回当前钱包，不再入账）
        User u = lockUser(userId);
        if (balanceTransactionRepository.existsByBizNo(bizNo)) {
            return getWallet(userId, DEFAULT_LIMIT);
        }

        // 4) 入账 + 流水
        u.setBalance(nz(u.getBalance()).add(amount));
        userRepository.save(u);
        record(u, BalanceTransactionType.RECHARGE, amount, bizNo, null, "账户充值（模拟）");

        return getWallet(userId, DEFAULT_LIMIT);
    }

    /**
     * 余额支付扣款（由 {@code OrderService.pay} 在同一事务内调用）。
     * <p>
     * 幂等键 {@code ORDER-{orderId}-PAY}；余额不足抛 400，使整个事务回滚 ——
     * 订单留在「待付款」、余额不动、不产生支付流水。
     */
    @Transactional
    public void payOrder(Long userId, Long orderId, BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new BusinessException(400, "订单金额异常");
        }

        User u = lockUser(userId);

        String bizNo = "ORDER-" + orderId + "-PAY";
        if (balanceTransactionRepository.existsByBizNo(bizNo)) {
            // 幂等：该订单已扣过款，不再扣一次
            return;
        }

        BigDecimal balance = nz(u.getBalance());
        if (balance.compareTo(amount) < 0) {
            throw new BusinessException(400, "余额不足，当前余额 ¥" + balance.setScale(2)
                    + "，本单应付 ¥" + amount.setScale(2) + "，请先充值或更换支付方式");
        }

        BigDecimal after = balance.subtract(amount);
        u.setBalance(after);
        userRepository.save(u);
        record(u, BalanceTransactionType.PAY, amount.negate(), bizNo, orderId, "订单 " + orderId + " 余额支付");
    }

    /**
     * 把该订单「余额支付且成功」的金额退回余额，返回<b>实际退回金额</b>。
     * <p>
     * <b>本方法是「钱退回用户」的唯一出口，也是下面这条不变式的落点</b>：
     * <blockquote>
     * 任何把支付流水置为 {@link PaymentStatus#REFUNDED} 的路径，都必须<b>先</b>调用本方法把钱退回用户。
     * </blockquote>
     * 当前调用点共三处：{@code RefundService.approve}、{@code OrderService.cancel}、
     * {@code PaymentService.refund}。新增任何「置流水为已退款」的入口时，务必同样先调用本方法，
     * 否则余额支付的单子退款后钱会凭空消失。
     * <p>
     * 非余额支付的订单（微信/支付宝/现金）返回 {@code BigDecimal.ZERO}，不动余额，与现状一致。
     * <p>
     * ⚠️ <b>必须在本笔余额流水被置为 {@code REFUNDED} 之前调用</b> —— 一旦被置为 REFUNDED，
     * 第 1 步就查不到 SUCCESS 流水、返现金额会算成 0，钱就丢了。
     * 幂等键 {@code ORDER-{orderId}-REFUND}。
     * <p>
     * 加锁顺序与另两个资金入口（{@code recharge}/{@code payOrder}）保持一致：<b>先锁用户行，再判重</b>，
     * 使幂等判定同样处于行锁保护之下，不依赖「调用方已用状态机挡住重复调用」这一外部假设。
     */
    @Transactional
    public BigDecimal refundOrderToBalance(Long orderId, Long userId) {
        BigDecimal sum = paymentRepository.findByOrderIdOrderByCreatedAtDesc(orderId).stream()
                .filter(p -> p.getMethod() == PaymentMethod.BALANCE && p.getStatus() == PaymentStatus.SUCCESS)
                .map(Payment::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 非余额支付：不锁行、不记账，尽早返回（非余额订单不该白锁一行）
        if (sum.signum() <= 0) {
            return BigDecimal.ZERO;
        }

        String bizNo = "ORDER-" + orderId + "-REFUND";
        User u = lockUser(userId);                               // ← 先锁
        if (balanceTransactionRepository.existsByBizNo(bizNo)) { // ← 再判重（在锁保护之下）
            return BigDecimal.ZERO;
        }

        u.setBalance(nz(u.getBalance()).add(sum));
        userRepository.save(u);
        record(u, BalanceTransactionType.REFUND, sum, bizNo, orderId, "订单退款返现到余额");
        return sum;
    }

    /**
     * 初始赠送：给新用户发放初始余额（幂等，幂等键 {@code INIT-{userId}}）。
     * <p>
     * 必须走本方法而非直接改 {@code user.balance}，否则余额增加没有流水凭据，「账实相符」就破了。
     */
    @Transactional
    public void initBalance(Long userId, BigDecimal amount) {
        String bizNo = "INIT-" + userId;
        if (balanceTransactionRepository.existsByBizNo(bizNo)) {
            return;
        }
        User u = lockUser(userId);
        u.setBalance(nz(u.getBalance()).add(amount));
        userRepository.save(u);
        record(u, BalanceTransactionType.INIT, amount, bizNo, null, "新用户初始余额");
    }

    // ==================== 内部工具 ====================

    /** 悲观写锁：加锁用户行（{@code SELECT ... FOR UPDATE}），用户不存在抛 404。 */
    private User lockUser(Long userId) {
        return userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new BusinessException(404, "用户不存在"));
    }

    /** null 安全：把 null 余额视为 0。 */
    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    /** 写一条余额流水；{@code balanceAfter} 取用户当前余额（必须在锁内调用）。 */
    private void record(User u, BalanceTransactionType type, BigDecimal amount,
                        String bizNo, Long orderId, String remark) {
        BalanceTransaction t = new BalanceTransaction();
        t.setUserId(u.getId());
        t.setType(type);
        t.setAmount(amount);
        t.setBalanceAfter(nz(u.getBalance()));
        t.setBizNo(bizNo);
        t.setOrderId(orderId);
        t.setRemark(remark);
        balanceTransactionRepository.save(t);
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null || limit < 1) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private BalanceTransactionDTO toDTO(BalanceTransaction t) {
        BalanceTransactionDTO d = new BalanceTransactionDTO();
        d.setId(t.getId());
        d.setType(t.getType() == null ? null : t.getType().name());
        d.setTypeLabel(t.getType() == null ? null : t.getType().getLabel());
        d.setAmount(t.getAmount());
        d.setBalanceAfter(t.getBalanceAfter());
        d.setOrderId(t.getOrderId());
        d.setRemark(t.getRemark());
        LocalDateTime created = t.getCreatedAt();
        d.setCreatedAt(created == null ? null : created.format(TIME_FMT));
        return d;
    }
}
