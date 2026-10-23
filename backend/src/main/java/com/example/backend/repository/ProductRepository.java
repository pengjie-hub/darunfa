package com.example.backend.repository;

import com.example.backend.entity.Product;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    /** 悲观写锁：下单扣库存时锁定行，防止并发超卖。 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.id = :id")
    Optional<Product> findByIdForUpdate(@Param("id") Long id);


    Optional<Product> findByName(String name);

    List<Product> findByCategoryIdAndStatusOrderByIdDesc(Long categoryId, Integer status);

    List<Product> findByStatusOrderByIdDesc(Integer status);

    Page<Product> findByStatusOrderByIdDesc(Integer status, Pageable pageable);

    Page<Product> findByCategoryIdAndStatusOrderByIdDesc(Long categoryId, Integer status, Pageable pageable);


    @Query("select count(p) from Product p where p.stock <= :threshold")
    long countByStockLessThanEqual(@Param("threshold") int threshold);

    List<Product> findByStockLessThanEqualOrderByStockAsc(int threshold);
}
