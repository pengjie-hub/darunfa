package com.example.myapplication.remote;

import com.example.myapplication.R;
import com.example.myapplication.entity.CarInfo;
import com.example.myapplication.entity.Goods;
import com.example.myapplication.entity.OrderInfo;
import com.example.myapplication.entity.ReviewInfo;
import com.example.myapplication.entity.ReviewReply;
import com.example.myapplication.remote.dto.CartItemDTO;
import com.example.myapplication.remote.dto.OrderDTO;
import com.example.myapplication.remote.dto.OrderItemDTO;
import com.example.myapplication.remote.dto.ProductDTO;
import com.example.myapplication.remote.dto.ReviewDTO;
import com.example.myapplication.remote.dto.ReviewReplyDTO;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 后端 DTO → 本地实体 的映射工具。
 * <p>
 * 说明：后端种子商品暂未配置真实图片 URL，因此统一使用品牌占位图
 * {@code R.drawable.darunfa_icon}。后续后端补充 imageUrl 后可在此改为
 * 传入真实图片（配合图片加载库）。
 */
public final class DtoMapper {

    /** 占位图片资源（后端暂无商品图时的兜底） */
    private static final int DEFAULT_IMG = R.drawable.darunfa_icon;

    /** 商品图片默认高度（px） */
    private static final int DEFAULT_IMG_HEIGHT = 220;

    private static final String DEFAULT_MERCHANT = "大润发官方旗舰店";
    private static final String DEFAULT_ADDRESS = "上海市浦东新区大润发总部";

    private DtoMapper() {
    }

    // ---------- 商品 ----------

    public static Goods toGoods(ProductDTO d) {
        Goods g = new Goods();
        g.setGoods_id((int) d.id);
        g.setImg(resolveDrawable(d.imageUrl));
        g.setTitle(d.name);
        g.setPrice(formatPrice(d.price));
        g.setOriginalPrice(d.originalPrice);
        g.setTag(d.subtitle == null ? "" : d.subtitle);
        g.setHeight(DEFAULT_IMG_HEIGHT);
        g.setProductdetailsbanner(buildBannerPics(d.imageUrl));
        g.setProductdetailsrvitem(buildDetailPics());
        g.setStock(d.stock);
        g.setSales(d.sales);
        g.setPriceValue(d.price);
        g.setDescription(d.description == null ? "" : d.description);
        // 后端 status: 0=下架, 1=在售; 缺省/异常值也用 0（保守起见视为下架，让前端做兜底拦截）
        g.setStatus(d.status == 1 ? 1 : 0);
        g.setMerchantName(DEFAULT_MERCHANT);
        g.setMerchantAddress(DEFAULT_ADDRESS);
        return g;
    }

    public static List<Goods> toGoodsList(List<ProductDTO> list) {
        List<Goods> out = new ArrayList<>();
        if (list != null) {
            for (ProductDTO d : list) out.add(toGoods(d));
        }
        return out;
    }

    // ---------- 购物车 ----------

    public static CarInfo toCarInfo(CartItemDTO d) {
        CarInfo c = new CarInfo();
        c.setCartItemId(d.id);
        c.setProduct_id((int) d.productId);
        c.setProduct_title(d.productName);
        c.setProduct_price(formatPrice(d.price));
        c.setProduct_count(d.quantity);
        c.setProduct_img(resolveDrawable(d.productImage));
        c.setImagePath(d.productImage);
        c.setSelected(d.selected);
        c.setUser_id((int) d.userId);
        c.setPrice(d.price);
        return c;
    }

    public static List<CarInfo> toCarInfoList(List<CartItemDTO> list) {
        List<CarInfo> out = new ArrayList<>();
        if (list != null) {
            for (CartItemDTO d : list) out.add(toCarInfo(d));
        }
        return out;
    }

    // ---------- 订单 ----------

    /**
     * 订单（含多明细）展开为「每明细一条 OrderInfo」，订单号/状态/收货人等信息一并带上。
     * 适配现有 UI 的单商品一行的订单列表。
     */
    public static List<OrderInfo> toOrderInfoList(List<OrderDTO> orders) {
        List<OrderInfo> out = new ArrayList<>();
        if (orders == null) return out;
        for (OrderDTO o : orders) {
            List<OrderItemDTO> items = o.items;
            if (items == null || items.isEmpty()) {
                OrderInfo oi = new OrderInfo();
                oi.setOrder_id((int) o.id);
                oi.setUser_id((int) o.userId);
                oi.setProduct_title("");
                oi.setProduct_price(formatPrice(o.totalAmount));
                oi.setProduct_count(1);
                oi.setProduct_img(DEFAULT_IMG);
                oi.setPrice(o.totalAmount);
                applyOrderFields(oi, o);
                out.add(oi);
                continue;
            }
            for (OrderItemDTO item : items) {
                OrderInfo oi = new OrderInfo();
                oi.setOrder_id((int) o.id);
                oi.setUser_id((int) o.userId);
                oi.setProduct_id((int) item.productId);
                oi.setProduct_title(item.productName);
                oi.setProduct_price(formatPrice(item.price));
                oi.setProduct_count(item.quantity);
                oi.setProduct_img(resolveDrawable(item.productImage));
                oi.setImagePath(item.productImage);
                oi.setPrice(item.price);
                applyOrderFields(oi, o);
                // 逐件优先：前台按「一行一件商品」展示，行级 reviewed 才让评过的行立刻消失；
                // 后端未下发时回退到订单级标记（老接口兼容）
                oi.setReviewed(item.reviewed != null ? item.reviewed : Boolean.TRUE.equals(o.reviewed));
                out.add(oi);
            }
        }
        return out;
    }

    /**
     * 把「整单维度」的字段灌进行级实体：一个订单的多条明细共享同一份订单号/状态/物流/退款信息。
     */
    private static void applyOrderFields(OrderInfo oi, OrderDTO o) {
        oi.setOrderNo(o.orderNo);
        oi.setPhone(o.receiverPhone);
        oi.setAddress(o.receiverAddress);
        oi.setOrder_time(o.createdAt);
        oi.setStatus(o.statusLabel == null ? "" : o.statusLabel);
        oi.setReviewed(Boolean.TRUE.equals(o.reviewed));
        // 物流：未发货时后端两字段都是 null，前台据 hasShipment() 决定是否显示运单号那一行
        oi.setShipmentCompany(o.shipmentCompany);
        oi.setTrackingNo(o.trackingNo);
        // 退款：从未申请时全为 null
        oi.setRefundStatus(o.refundStatus);
        oi.setRefundStatusLabel(o.refundStatusLabel);
        oi.setRefundReason(o.refundReason);
        oi.setRefundRejectReason(o.refundRejectReason);
    }

    // ---------- 评论 ----------

    public static ReviewInfo toReview(ReviewDTO d) {
        ReviewInfo r = new ReviewInfo();
        r.setId((int) d.id);
        r.setProductId((int) d.productId);
        r.setUserId((int) d.userId);
        r.setUserName(d.userName);
        r.setRating(d.rating);
        r.setContent(d.content);
        r.setCreatedAt(formatTime(d.createdAt));
        return r;
    }

    public static List<ReviewInfo> toReviewList(List<ReviewDTO> list) {
        List<ReviewInfo> out = new ArrayList<>();
        if (list != null) {
            for (ReviewDTO d : list) out.add(toReview(d));
        }
        return out;
    }

    public static ReviewReply toReply(ReviewReplyDTO d) {
        ReviewReply r = new ReviewReply();
        r.setId((int) d.id);
        r.setReviewId((int) d.reviewId);
        r.setUserId((int) d.userId);
        r.setUserName(d.userName);
        r.setContent(d.content);
        r.setCreatedAt(formatTime(d.createdAt));
        // 旧数据没有 replyType（后端该列是后加的），此时按「用户追评」处理
        r.setReplyType(d.replyType == null ? "USER" : d.replyType);
        return r;
    }

    public static List<ReviewReply> toReplyList(List<ReviewReplyDTO> list) {
        List<ReviewReply> out = new ArrayList<>();
        if (list != null) {
            for (ReviewReplyDTO d : list) out.add(toReply(d));
        }
        return out;
    }

    // ---------- 工具 ----------

    private static String formatPrice(double price) {
        return String.format(Locale.CHINA, "¥%.2f", price);
    }

    /** 后端 LocalDateTime 序列化为 ISO（含 T），这里统一成空格分隔，便于展示 */
    private static String formatTime(String time) {
        if (time == null) return "";
        return time.replace('T', ' ');
    }

    // ---------- 图片资源解析 ----------

    /**
     * 把后端下发的「图片资源名」（如 p002 / yt_b_1）解析为本地 drawable 资源 ID。
     * <p>
     * 后端 {@code imageUrl} 当前存的是 Android 端打包的 drawable 资源名（非网络 URL），
     * 通过反射从 R.drawable 中查找；找不到或为空时回退到品牌占位图。
     */
    private static int resolveDrawable(String name) {
        if (name == null || name.isEmpty()) {
            return DEFAULT_IMG;
        }
        // 容错：如果配置里带扩展名（如 "p002.png"）则去掉
        int dot = name.indexOf('.');
        String fieldName = (dot > 0) ? name.substring(0, dot) : name;
        try {
            Class<?> drawableClass = Class.forName("com.example.myapplication.R$drawable");
            java.lang.reflect.Field field = drawableClass.getField(fieldName);
            int resId = field.getInt(null);
            if (resId != 0) return resId;
        } catch (Exception ignored) {
            // 找不到该资源，走兜底
        }
        return DEFAULT_IMG;
    }

    /**
     * 轮播图：按商品图片键生成3张专属轮播图（如 p002 → p002_s1 / p002_s2 / p002_s3）。
     * 无图片键时回退到共享轮播图（yt_b_1 ~ yt_b_3）。
     */
    private static List<Integer> buildBannerPics(String imageKey) {
        List<Integer> pics = new ArrayList<>();
        if (imageKey == null || imageKey.isEmpty()) {
            pics.add(resolveDrawable("yt_b_1"));
            pics.add(resolveDrawable("yt_b_2"));
            pics.add(resolveDrawable("yt_b_3"));
            return pics;
        }
        pics.add(resolveDrawable(imageKey + "_s1"));
        pics.add(resolveDrawable(imageKey + "_s2"));
        pics.add(resolveDrawable(imageKey + "_s3"));
        return pics;
    }

    /** 详情图（沿用原本地版配置：yingtao1 ~ yingtao7） */
    private static List<Integer> buildDetailPics() {
        List<Integer> pics = new ArrayList<>();
        pics.add(resolveDrawable("yingtao1"));
        pics.add(resolveDrawable("yingtao2"));
        pics.add(resolveDrawable("yingtao3"));
        pics.add(resolveDrawable("yingtao4"));
        pics.add(resolveDrawable("yingtao5"));
        pics.add(resolveDrawable("yingtao6"));
        pics.add(resolveDrawable("yingtao7"));
        return pics;
    }
}
