package com.example.backend.repository;

import com.example.backend.entity.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {

    List<CartItem> findByUserIdOrderByIdDesc(Long userId);

    Optional<CartItem> findByUserIdAndProductId(Long userId, Long productId);


    void deleteByUserIdAndProductId(Long userId, Long productId);

    @Modifying
    @Transactional
    @Query("delete from CartItem c where c.userId = :userId and c.productId in :productIds")
    void deleteByUserIdAndProductIdIn(@Param("userId") Long userId, @Param("productIds") Collection<Long> productIds);

    @Modifying
    @Transactional
    @Query("update CartItem c set c.selected = :selected where c.userId = :userId")
    void updateAllSelected(@Param("userId") Long userId, @Param("selected") Boolean selected);

    void deleteByUserId(Long userId);
}
