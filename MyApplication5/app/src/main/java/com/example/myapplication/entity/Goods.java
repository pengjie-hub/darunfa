package com.example.myapplication.entity;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.List;

public class Goods implements Parcelable {
    private Integer goods_id;
    private Integer img;
    private String title;
    private String price;
    private String tag;
    private int height;
    private List<Integer> productdetailsbanner;
    private List<Integer> productdetailsrvitem;
    // 新增字段: 商家信息、库存、分类、描述等
    private String category;
    private String subcategory;
    private String section;
    private int stock;             // 商品库存数量
    private int sales;             // 销量
    private double priceValue;     // 数值价格(用于计算)
    private double originalPrice;  // 原价(用于划线展示优惠); 0 或 <= priceValue 表示无优惠
    private String merchantName;        // 商家名称
    private String merchantAddress;     // 商家地址
    private String description;         // 商品介绍
    /** 商品状态：1=在售、0=下架；null=未知/本地种子（当作在售处理）。 */
    private Integer status;

    public Goods() {
    }

    public Goods(Integer goods_id, Integer img, String title, String price, String tag, int height, List<Integer> productdetailsbanner, List<Integer> productdetailsrvitem) {
        this.goods_id = goods_id;
        this.img = img;
        this.title = title;
        this.price = price;
        this.tag = tag;
        this.height = height;
        this.productdetailsbanner = productdetailsbanner;
        this.productdetailsrvitem = productdetailsrvitem;
    }

    public Goods(Integer goods_id, Integer img, String title , String price, String tag, List<Integer> productdetailsbanner, List<Integer> productdetailsrvitem) {
        this.productdetailsbanner = productdetailsbanner;
        this.goods_id = goods_id;
        this.img = img;
        this.tag = tag;
        this.price = price;
        this.title = title;
        this.productdetailsrvitem = productdetailsrvitem;
    }
    // 1. 从 Parcel 中读取数据并赋值给成员变量（顺序需与 writeToParcel 一致）
    protected Goods(Parcel in) {
        goods_id = in.readInt();
        img = in.readInt();
        title = in.readString();
        price = in.readString();
        tag = in.readString();
        height = in.readInt();
        productdetailsbanner = in.readArrayList(Integer.class.getClassLoader());
        productdetailsrvitem = in.readArrayList(Integer.class.getClassLoader());
        // 新增字段读取
        category = in.readString();
        subcategory = in.readString();
        section = in.readString();
        stock = in.readInt();
        sales = in.readInt();
        priceValue = in.readDouble();
        originalPrice = in.readDouble();
        merchantName = in.readString();
        merchantAddress = in.readString();
        description = in.readString();
        // status 用 -1 作为 null 哨兵：null 表示未知/本地种子（按在售处理），0=下架，1=在售
        int statusRaw = in.readInt();
        status = statusRaw == -1 ? null : statusRaw;
    }

    // 2. 必须实现的 Creator 接口，用于反序列化（创建 Goods 对象）
    public static final Creator<Goods> CREATOR = new Creator<Goods>() {
        @Override
        public Goods createFromParcel(Parcel in) {
            return new Goods(in);
        }

        @Override
        public Goods[] newArray(int size) {
            return new Goods[size];
        }
    };

    // 3. 描述内容（几乎所有情况返回 0）
    @Override
    public int describeContents() {
        return 0;
    }

    // 4. 将成员变量写入 Parcel（序列化）
    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(goods_id);
        dest.writeInt(img);
        dest.writeString(title);
        dest.writeString(price);
        dest.writeString(tag);
        dest.writeInt(height);
        dest.writeList(productdetailsbanner);
        dest.writeList(productdetailsrvitem);
        // 新增字段写入
        dest.writeString(category);
        dest.writeString(subcategory);
        dest.writeString(section);
        dest.writeInt(stock);
        dest.writeInt(sales);
        dest.writeDouble(priceValue);
        dest.writeDouble(originalPrice);
        dest.writeString(merchantName);
        dest.writeString(merchantAddress);
        dest.writeString(description);
        dest.writeInt(status == null ? -1 : status);
    }

    public List<Integer> getProductdetailsbanner() {
        return productdetailsbanner;
    }

    public void setProductdetailsbanner(List<Integer> productdetailsbanner) {
        this.productdetailsbanner = productdetailsbanner;
    }

    public List<Integer> getProductdetailsrvitem() {
        return productdetailsrvitem;
    }

    public void setProductdetailsrvitem(List<Integer> productdetailsrvitem) {
        this.productdetailsrvitem = productdetailsrvitem;
    }

    public Integer getImg() {
        return img;
    }

    public void setImg(Integer img) {
        this.img = img;
    }

    public String getTitle() {
        return title;
    }
    public Integer getGoods_id() {
        return goods_id;
    }

    public void setGoods_id(Integer goods_id) {
        this.goods_id = goods_id;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getPrice() {
        return price;
    }

    public void setPrice(String price) {
        this.price = price;
    }

    public int getHeight() {
        return height;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public String getTag() {
        return tag;
    }

    public void setTag(String tag) {
        this.tag = tag;
    }

    // ===== 新增字段 getter/setter =====
    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getSubcategory() {
        return subcategory;
    }

    public void setSubcategory(String subcategory) {
        this.subcategory = subcategory;
    }

    public String getSection() {
        return section;
    }

    public void setSection(String section) {
        this.section = section;
    }

    public int getStock() {
        return stock;
    }

    public void setStock(int stock) {
        this.stock = stock;
    }

    public int getSales() {
        return sales;
    }

    public void setSales(int sales) {
        this.sales = sales;
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

    /** 商品状态：1=在售、0=下架；null=未知/本地种子（按在售处理）。 */
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
