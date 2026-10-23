package com.example.myapplication.entity;

/**
 * 订单信息模型类
 */
public class OrderInfo {
    private int order_id;          // 订单ID
    private int user_id;           // 用户ID
    private int product_id;        // 商品ID
    private String product_title;  // 商品名称
    private String product_price;  // 商品价格
    private int product_count;     // 商品数量
    private int product_img;       // 商品图片
    private String imagePath;      // 商品图片URL路径
    private String phone;          // 电话
    private String address;        // 地址
    private String order_time;     // 订单时间
    private String status = "待发货";  // 订单状态

    // 商品原始价格（用于计算总价）
    private double price;          // 商品价格（数值类型）

    public OrderInfo() {
    }

    public OrderInfo(int user_id, int product_id, String product_title, String product_price, 
                     int product_count, int product_img, String imagePath, String phone, String address) {
        this.user_id = user_id;
        this.product_id = product_id;
        this.product_title = product_title;
        this.product_price = product_price;
        this.product_count = product_count;
        this.product_img = product_img;
        this.imagePath = imagePath;
        this.phone = phone;
        this.address = address;
        this.order_time = System.currentTimeMillis() + "";
        
        // 尝试将价格字符串转换为double类型
        try {
            this.price = Double.parseDouble(product_price);
        } catch (NumberFormatException e) {
            this.price = 0.0;
        }
    }

    public int getOrder_id() {
        return order_id;
    }

    public void setOrder_id(int order_id) {
        this.order_id = order_id;
    }

    public int getUser_id() {
        return user_id;
    }

    public void setUser_id(int user_id) {
        this.user_id = user_id;
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

    /**
     * 以字符串设置单价。
     * <p>
     * 数据层统一存「无货币符号的两位小数」：先剥离历史脏数据里可能自带的 ¥/￥
     * （如 DtoMapper 早期用 formatPrice 产出的 "¥45.90"），能解析成数字时再归一化为 "45.90"。
     * 这样无论赋值顺序如何，{@code getProduct_price()} 的形态都一致。
     */
    public void setProduct_price(String product_price) {
        if (product_price == null) {
            this.product_price = null;
            this.price = 0.0;
            return;
        }
        String stripped = com.example.myapplication.util.PriceUtil.stripSymbol(product_price);
        this.product_price = stripped;

        // 尝试将价格字符串转换为double类型
        try {
            this.price = Double.parseDouble(stripped);
            this.product_price = com.example.myapplication.util.PriceUtil.formatAmount(this.price);
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

    public String getOrder_time() {
        return order_time;
    }

    public void setOrder_time(String order_time) {
        this.order_time = order_time;
    }

    public double getPrice() {
        return price;
    }

    /**
     * 以数值设置单价。
     * <p>
     * 注意：{@code product_price} 是给 UI 直接展示的字符串，若用 {@code String.valueOf(price)}
     * 会把 45.9 原样写成 "45.9"，导致订单卡片出现「¥45.9」这种少一位小数的格式。
     * 这里统一格式化为两位小数（不带货币符号，符号由 UI 层补，约定见 {@code PriceUtil}）。
     */
    public void setPrice(double price) {
        this.price = price;
        this.product_price = com.example.myapplication.util.PriceUtil.formatAmount(price);
    }

    // 获取商品总价（数量*单价）
    public double getTotalPrice() {
        return this.price * this.product_count;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    /**
     * 该订单是否已被当前用户评价过（由后端 {@code OrderDTO.reviewed} 下发）。
     * <p>
     * 「待评价」列表与「我的」页角标都靠它把已评价的订单排除掉，
     * 否则用户评完再回到待评价页仍会看到「去评价」的提示。
     */
    private boolean reviewed;

    /**
     * 后端真实订单号（如 DR20260915221312345），由 {@code OrderDTO.orderNo} 下发。
     * <p>
     * 注意与 {@link #order_id} 的区别：order_id 是数据库自增主键，只用于调接口；
     * 用户看到的「订单号」必须是 orderNo —— 客服/商家查单时认的也是它。
     */
    private String orderNo;

    /** 物流公司名（发货后台填写），未发货为空。 */
    private String shipmentCompany;

    /** 运单号（发货后台填写），未发货为空。「已发货/待收货」列表要展示它。 */
    private String trackingNo;

    /** 最新一条退款申请状态：PENDING / APPROVED / REJECTED，从未申请为 null。 */
    private String refundStatus;

    /** 退款状态中文标签（待处理 / 已同意 / 已拒绝）。 */
    private String refundStatusLabel;

    /** 用户填写的退款原因。 */
    private String refundReason;

    /** 商家拒绝时填写的理由。 */
    private String refundRejectReason;

    public boolean isReviewed() {
        return reviewed;
    }

    public void setReviewed(boolean reviewed) {
        this.reviewed = reviewed;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public String getShipmentCompany() {
        return shipmentCompany;
    }

    public void setShipmentCompany(String shipmentCompany) {
        this.shipmentCompany = shipmentCompany;
    }

    public String getTrackingNo() {
        return trackingNo;
    }

    public void setTrackingNo(String trackingNo) {
        this.trackingNo = trackingNo;
    }

    public String getRefundStatus() {
        return refundStatus;
    }

    public void setRefundStatus(String refundStatus) {
        this.refundStatus = refundStatus;
    }

    public String getRefundStatusLabel() {
        return refundStatusLabel;
    }

    public void setRefundStatusLabel(String refundStatusLabel) {
        this.refundStatusLabel = refundStatusLabel;
    }

    public String getRefundReason() {
        return refundReason;
    }

    public void setRefundReason(String refundReason) {
        this.refundReason = refundReason;
    }

    public String getRefundRejectReason() {
        return refundRejectReason;
    }

    public void setRefundRejectReason(String refundRejectReason) {
        this.refundRejectReason = refundRejectReason;
    }

    /** 展示用订单号：优先后端真实订单号，缺失时回退到 #{order_id}（老数据兜底）。 */
    public String getDisplayOrderNo() {
        if (orderNo != null && !orderNo.isEmpty()) return orderNo;
        return "#" + order_id;
    }

    /** 是否已发货（有运单号或物流公司即视为已发货）。 */
    public boolean hasShipment() {
        return (trackingNo != null && !trackingNo.isEmpty())
                || (shipmentCompany != null && !shipmentCompany.isEmpty());
    }
}