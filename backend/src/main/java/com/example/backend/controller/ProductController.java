package com.example.backend.controller;

import com.example.backend.common.ApiResponse;
import com.example.backend.entity.Category;
import com.example.backend.entity.Product;
import com.example.backend.service.ProductService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 商品与分类浏览接口（公开）。
 */
@RestController
@RequestMapping("/api")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping("/categories")
    public ApiResponse<List<Category>> categories() {
        return ApiResponse.ok(productService.listCategories());
    }

    @GetMapping("/products")
    public ApiResponse<List<Product>> products(@RequestParam(required = false) Long categoryId,
                                               @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(productService.listProducts(categoryId, keyword));
    }

    @GetMapping("/products/{id}")
    public ApiResponse<Product> productDetail(@PathVariable Long id) {
        return ApiResponse.ok(productService.getProduct(id));
    }
}
