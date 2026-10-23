package com.example.backend.repository;

import com.example.backend.entity.BalanceTransaction;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BalanceTransactionRepository extends JpaRepository<BalanceTransaction, Long> {


    /** 幂等键是否存在（余额操作前判定是否已处理，避免重复入账/扣款）。 */
    boolean existsByBizNo(String bizNo);

    /** 某用户的余额流水，最新在前（按自增 id 倒序）。 */
    List<BalanceTransaction> findByUserIdOrderByIdDesc(Long userId, Pageable pageable);
}
