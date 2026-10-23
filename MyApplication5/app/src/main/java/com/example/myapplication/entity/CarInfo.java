package com.example.myapplication.entity;

/**
 * 购物车商品信息模型类
 */
public class CarInfo {
    private long cartItemId;       // 购物车项ID(后端购物车表主键, 用于下单)
    private int product_id;        // 商品ID
    private String product_title;  // 商品名称
    private String product_price;  // 商品价格
    private int product_count;     // 商品数量
    private int product_img;       // 商品图片
    private String imagePath;      // 商品图片URL路径
    private boolean selected;      // 商品是否被选中
    private int user_id;           // 用户ID
    private String phone;          // 电话
    private String address;        // 地址
    
    // 商品原始价格（用于计算总价）
    private double price;          // 商品价格（数值类型）

    public CarInfo() {
        this.selected = true;      // 默认选中状态
    }

    public CarInfo(int product_id, String product_title, String product_price, int product_count, int product_img, int user_id) {
        this.product_id = product_id;
        this.product_title = product_title;
        this.product_price = product_price;
        this.product_count = product_count;
        this.product_img = product_img;
        this.user_id = user_id;
        this.selected = true;      // 默认选中状态
        
        // 尝试将价格字符串转换为double类型
        try {
            this.price = Double.parseDouble(product_price);
        } catch (NumberFormatException e) {
            this.price = 0.0;
        }
    }
    
    public CarInfo(int product_id, String product_title, String product_price, int product_count, int product_img, String imagePath, boolean selected, int user_id, String phone, String address) {
        this.product_id = product_id;
        this.product_title = product_title;
        this.product_price = product_price;
        this.product_count = product_count;
        this.product_img = product_img;
        this.imagePath = imagePath;
        this.selected = selected;
        this.user_id = user_id;
        this.phone = phone;
        this.address = address;
        
        // 尝试将价格字符串转换为double类型
        try {
            this.price = Double.parseDouble(product_price);
        } catch (NumberFormatException e) {
            this.price = 0.0;
        }
    }




    public long getCartItemId() {
        return cartItemId;
    }

    public void setCartItemId(long cartItemId) {
        this.cartItemId = cartItemId;
    }

    public int getProduct_id() {
        return product_id;
    }

    public void setProduct_id(int product_id) {
        this.product_id = product_id;
    }

    public String getProduct_title() {
        return product_title;
    }

    public void setProduct_title(String product_title) {
        this.product_title = product_title;
    }

    public String getProduct_price() {
        return product_price;
    }

    public void setProduct_price(String product_price) {
        this.product_price = product_price;
        
        // 尝试将价格字符串转换为double类型
        try {
            this.price = Double.parseDouble(product_price);
        } catch (NumberFormatException e) {
            this.price = 0.0;
        }
    }

    public int getProduct_count() {
        return product_count;
    }

    public void setProduct_count(int product_count) {
        this.product_count = product_count;
    }

    public int getProduct_img() {
        return product_img;
    }

    public void setProduct_img(int product_img) {
        this.product_img = product_img;
    }
    
    public String getImagePath() {
        return imagePath;
    }
    
    public void setImagePath(String imagePath) {
        this.imagePath = imagePath;
    }
    
    public boolean isSelected() {
        return selected;
    }
    
    public void setSelected(boolean selected) {
        this.selected = selected;
    }
    
    public int getUser_id() {
        return user_id;
    }
    
    public void setUser_id(int user_id) {
        this.user_id = user_id;
    }
    
    public double getPrice() {
        return price;
    }
    
    /**
     * 以数值设置单价。
     * <p>
     * 与 {@code OrderInfo#setPrice(double)} 同一约定：{@code product_price} 统一为
     * 两位小数、不带货币符号，避免出现「¥45.9」这类缺口的小数格式。
     */
    public void setPrice(double price) {
        this.price = price;
        this.product_price = com.example.myapplication.util.PriceUtil.formatAmount(price);
    }
    
    public String getPhone() {
        return phone;
    }
    
    public void setPhone(String phone) {
        this.phone = phone;
    }
    
    public String getAddress() {
        return address;
    }
    
    public void setAddress(String address) {
        this.address = address;
    }
    
    // 获取商品总价（数量*单价）
    public double getTotalPrice() {
        return this.price * this.product_count;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        
        CarInfo carInfo = (CarInfo) o;
        return product_id == carInfo.product_id;
    }
    
    @Override
    public int hashCode() {
        return Integer.hashCode(product_id);
    }
}