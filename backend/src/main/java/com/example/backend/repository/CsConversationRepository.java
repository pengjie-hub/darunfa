package com.example.backend.repository;

import com.example.backend.entity.CsConversation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CsConversationRepository extends JpaRepository<CsConversation, Long> {

    Optional<CsConversation> findByUserId(Long userId);

    /** 后台会话列表（最近有消息的排前面）。 */
    List<CsConversation> findAllByOrderByLastMessageAtDescIdDesc();
}
