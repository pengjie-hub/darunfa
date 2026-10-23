package com.example.backend.repository;

import com.example.backend.entity.AiMemory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * AI 长期记忆（偏好/上下文键值对）仓储。
 */
public interface AiMemoryRepository extends JpaRepository<AiMemory, Long> {

    Optional<AiMemory> findByUserIdAndMemKey(Long userId, String memKey);

    List<AiMemory> findByUserIdOrderByUpdatedAtDesc(Long userId);

    /** 清空某用户的全部长期记忆（写操作需事务）。 */
    @Modifying
    @Transactional
    @Query("delete from AiMemory m where m.userId = :userId")
    void deleteByUserId(@Param("userId") Long userId);
}
