package com.example.myapplication.entity;

import java.io.Serializable;
import java.util.List;

public class ProductDetails implements Serializable {
    private int id;
    private String name;
    private String price;
    private Integer pic;
    private String introduce;
    private List<Integer> bannerpics;
    private List<Integer> rvitempics;
    // 新增字段
    private int stock;                    // 商品库存
    private String merchantName;          // 商家名称
    private String merchantAddress;       // 商家地址
    private String description;           // 商品介绍
    private double priceValue;            // 数值价格
    private double originalPrice;         // 原价(用于划线展示优惠); 0 或 <= priceValue 表示无优惠
    private String category;             // 商品分类
    private int sales;                    // 销量
    /** 商品状态：1=在售、0=下架；null=未知（按在售处理）。 */
    private Integer status;

    public ProductDetails(String name, String price, Integer pic, String introduce, List<Integer> bannerpics,List<Integer> rvitempics) {
        this.name = name;
        this.price = price;
        this.pic = pic;
        this.introduce = introduce;
        this.bannerpics = bannerpics;
        this.rvitempics = rvitempics;
    }
    public ProductDetails(int id, String name, String price, Integer pic, String introduce, List<Integer> bannerpics,List<Integer> rvitempics) {
        this.id = id;
        this.name = name;
        this.price = price;
        this.pic = pic;
        this.introduce = introduce;
        this.bannerpics = bannerpics;
        this.rvitempics = rvitempics;
    }

    public ProductDetails() {
    }

    public List<Integer> getBannerpics() {
        return bannerpics;
    }

    public void setBannerpics(List<Integer> bannerpics) {
        this.bannerpics = bannerpics;
    }

    public List<Integer> getRvitempics() {
        return rvitempics;
    }

    public void setRvitempics(List<Integer> rvitempics) {
        this.rvitempics = rvitempics;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getIntroduce() {
        return introduce;
    }

    public void setIntroduce(String introduce) {
        this.introduce = introduce;
    }

    public String getPrice() {
        return price;
    }

    public void setPrice(String price) {
        this.price = price;
    }

    public Integer getPic() {
        return pic;
    }

    public void setPic(Integer pic) {
        this.pic = pic;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    // ===== 新增字段 getter/setter =====
    public int getStock() {
        return stock;
    }

    public void setStock(int stock) {
        this.stock = stock;
    }

    public String getMerchantName() {
        return merchantName;
    }

    public void setMerchantName(String merchantName) {
        this.merchantName = merchantName;
    }

    public String getMerchantAddress() {
        return merchantAddress;
    }

    public void setMerchantAddress(String merchantAddress) {
        this.merchantAddress = merchantAddress;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public double getPriceValue() {
        return priceValue;
    }

    public void setPriceValue(double priceValue) {
        this.priceValue = priceValue;
    }

    /** 原价（划线展示优惠用）。0 或 <= 现价表示无优惠。 */
    public double getOriginalPrice() {
        return originalPrice;
    }

    public void setOriginalPrice(double originalPrice) {
        this.originalPrice = originalPrice;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public int getSales() {
        return sales;
    }

    public void setSales(int sales) {
        this.sales = sales;
    }

    /** 商品状态：1=在售、0=下架；null=未知（按在售处理）。 */
    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    /** 便捷判断：是否在售（status 为 null 或 1 视为在售；仅 0 视为下架）。 */
    public boolean isOnSale() {
        return status == null || status == 1;
    }
}
