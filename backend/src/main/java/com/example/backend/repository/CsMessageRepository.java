package com.example.backend.repository;

import com.example.backend.entity.CsMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CsMessageRepository extends JpaRepository<CsMessage, Long> {

    /** 某个会话的全部消息，时间正序。 */
    List<CsMessage> findByConversationIdOrderByIdAsc(Long conversationId);

    /** 最近 N 条（倒序，用于拼 LLM 上下文时取尾部）。 */
    List<CsMessage> findTop20ByConversationIdOrderByIdDesc(Long conversationId);

    long countByConversationId(Long conversationId);
}
