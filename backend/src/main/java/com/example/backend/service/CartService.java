package com.example.backend.service;

import com.example.backend.common.BusinessException;
import com.example.backend.dto.AddCartRequest;
import com.example.backend.dto.UpdateCartRequest;
import com.example.backend.entity.CartItem;
import com.example.backend.entity.Product;
import com.example.backend.repository.CartItemRepository;
import com.example.backend.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 购物车服务。
 */
@Service
public class CartService {

    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;

    public CartService(CartItemRepository cartItemRepository, ProductRepository productRepository) {
        this.cartItemRepository = cartItemRepository;
        this.productRepository = productRepository;
    }

    public List<CartItem> list(Long userId) {
        return cartItemRepository.findByUserIdOrderByIdDesc(userId);
    }

    @Transactional
    public CartItem add(Long userId, AddCartRequest req) {
        Product product = productRepository.findById(req.getProductId())
                .orElseThrow(() -> new BusinessException(404, "商品不存在"));
        if (product.getStatus() != null && product.getStatus() == 0) {
            throw new BusinessException(400, "商品已下架");
        }

        int qty = req.getQuantity() == null ? 1 : req.getQuantity();
        return cartItemRepository.findByUserIdAndProductId(userId, req.getProductId())
                .map(item -> {
                    int newQty = item.getQuantity() + qty;
                    if (newQty > product.getStock()) {
                        throw new BusinessException(400, "库存不足，最多可加购 " + product.getStock() + " 件");
                    }
                    item.setQuantity(newQty);
                    item.setPrice(product.getPrice());
                    return cartItemRepository.save(item);
                })
                .orElseGet(() -> {
                    if (qty > product.getStock()) {
                        throw new BusinessException(400, "库存不足，最多可加购 " + product.getStock() + " 件");
                    }
                    CartItem item = new CartItem();
                    item.setUserId(userId);
                    item.setProductId(product.getId());
                    item.setProductName(product.getName());
                    item.setProductImage(product.getImageUrl());
                    item.setPrice(product.getPrice());
                    item.setQuantity(qty);
                    item.setSelected(true);
                    return cartItemRepository.save(item);
                });
    }

    @Transactional
    public CartItem update(Long userId, Long productId, UpdateCartRequest req) {
        CartItem item = cartItemRepository.findByUserIdAndProductId(userId, productId)
                .orElseThrow(() -> new BusinessException(404, "购物车中不存在该商品"));
        if (req.getQuantity() != null) {
            if (req.getQuantity() < 1) {
                throw new BusinessException(400, "数量至少为 1");
            }
            Product product = productRepository.findById(productId)
                    .orElseThrow(() -> new BusinessException(404, "商品不存在"));
            if (req.getQuantity() > product.getStock()) {
                throw new BusinessException(400, "库存不足，最多可购买 " + product.getStock() + " 件");
            }
            item.setQuantity(req.getQuantity());
        }
        if (req.getSelected() != null) {
            item.setSelected(req.getSelected());
        }
        return cartItemRepository.save(item);
    }

    @Transactional
    public void remove(Long userId, Long productId) {
        cartItemRepository.deleteByUserIdAndProductId(userId, productId);
    }

    @Transactional
    public void removeBatch(Long userId, List<Long> productIds) {
        cartItemRepository.deleteByUserIdAndProductIdIn(userId, productIds);
    }

    @Transactional
    public void selectAll(Long userId, boolean selected) {
        cartItemRepository.updateAllSelected(userId, selected);
    }

    @Transactional
    public void clear(Long userId) {
        cartItemRepository.deleteByUserId(userId);
    }
}
