package com.example.backend.repository;

import com.example.backend.entity.AiChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * AI 对话历史消息仓储。
 */
public interface AiChatMessageRepository extends JpaRepository<AiChatMessage, Long> {

    /**
     * 取某用户最近 20 条消息（按 id 降序，id 自增即可近似代表时间顺序），
     * 后续在代码层逆序还原为时间正序并截取所需轮数。
     */
    List<AiChatMessage> findTop20ByUserIdOrderByIdDesc(Long userId);

    /** 清空某用户的全部对话历史（写操作需事务）。 */
    @Modifying
    @Transactional
    @Query("delete from AiChatMessage m where m.userId = :userId")
    void deleteByUserId(@Param("userId") Long userId);

}
