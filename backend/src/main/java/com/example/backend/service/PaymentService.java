package com.example.backend.service;

import com.example.backend.common.BusinessException;
import com.example.backend.entity.Payment;
import com.example.backend.entity.PaymentMethod;
import com.example.backend.entity.PaymentStatus;
import com.example.backend.repository.PaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 支付流水服务：查询与后台退款/补记。
 */
@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final WalletService walletService;

    public PaymentService(PaymentRepository paymentRepository, WalletService walletService) {
        this.paymentRepository = paymentRepository;
        this.walletService = walletService;
    }

    public List<Payment> listAll() {
        return paymentRepository.findAllByOrderByCreatedAtDesc();
    }


    /**
     * 后台退款：将某笔支付标记为已退款。
     * <p>
     * ⚠️ <b>不变式</b>：任何把支付流水置为 {@link PaymentStatus#REFUNDED} 的路径，都必须先把钱退回用户。
     * 因此这里在置为已退款之前先调 {@link WalletService#refundOrderToBalance} ——
     * 余额支付的单子退回余额；微信/支付宝/现金返回 0，不产生影响（原路退回由支付渠道处理）。
     * 顺序不能颠倒：一旦先把流水置为 REFUNDED，返现额就查不到 SUCCESS 流水、会算成 0，钱就丢了。
     */
    @Transactional
    public Payment refund(Long paymentId) {
        Payment p = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new BusinessException(404, "支付流水不存在"));
        if (p.getStatus() != PaymentStatus.SUCCESS) {
            throw new BusinessException(400, "仅支付成功的流水可退款");
        }
        // ⚠️ 不变式：任何把流水置为「已退款」的路径都必须先把钱退回用户。
        //    余额支付的单子要退回余额；微信/支付宝/现金返回 0，不产生影响（原路退回由渠道处理）。
        walletService.refundOrderToBalance(p.getOrderId(), p.getUserId());
        p.setStatus(PaymentStatus.REFUNDED);
        return paymentRepository.save(p);
    }

    /** 后台补记一笔支付（用于线下收款等场景）。 */
    @Transactional
    public Payment record(Long orderId, String orderNo, Long userId,
                          BigDecimal amount, PaymentMethod method) {
        Payment p = new Payment();
        p.setOrderId(orderId);
        p.setOrderNo(orderNo);
        p.setUserId(userId);
        p.setAmount(amount);
        p.setMethod(method == null ? PaymentMethod.CASH : method);
        p.setStatus(PaymentStatus.SUCCESS);
        p.setPaidAt(LocalDateTime.now());
        return paymentRepository.save(p);
    }
}
