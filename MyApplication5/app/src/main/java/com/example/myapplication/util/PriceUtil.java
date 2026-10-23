package com.example.myapplication.util;

import java.util.Locale;

/**
 * 价格展示工具类。
 *
 * <p>背景：项目中价格字符串存在两种来源，格式约定不一致——
 * <ul>
 *   <li>本地 SQLite 商品表：{@code price_display} 形如 "29.9/斤"（无货币符号，可能带单位）；</li>
 *   <li>后端接口：{@code DtoMapper.formatPrice()} 产出的形如 "¥9.90"（自带货币符号）。</li>
 * </ul>
 * 若 UI 布局再硬编码一个货币符号，就会出现 "¥¥9.90" 的重复符号。
 *
 * <p>统一约定：<b>货币符号一律由 UI 层提供，数据层字符串展示前先剥离符号</b>。
 * 本类提供剥离与格式化能力，供商品卡片、详情页等展示型 UI 复用。
 */
public final class PriceUtil {

    private PriceUtil() {
    }

    /** 统一展示用货币符号（半角）。 */
    public static final String CURRENCY = "¥";

    /**
     * 去掉价格字符串开头的货币符号（半角 ¥ 或全角 ￥），保留数值与单位。
     * <p>循环剥离以兼容 "¥¥9.90" 这类历史脏数据。
     * 例："¥9.90" → "9.90"；"￥5.0" → "5.0"；"29.9/斤" → "29.9/斤"。
     */
    public static String stripSymbol(String price) {
        if (price == null) {
            return "";
        }
        String s = price.trim();
        while (s.startsWith("¥") || s.startsWith("￥")) {
            s = s.substring(1).trim();
        }
        return s;
    }

    /** 数值金额格式化（两位小数，不带符号），用于现价/原价的数值展示。 */
    public static String formatAmount(double value) {
        return String.format(Locale.CHINA, "%.2f", value);
    }

    /**
     * 提取价格字符串中的单位后缀（剥离符号后，数值部分之后的内容）。
     * <p>例："29.9/斤" → "/斤"；"¥9.90" → ""；"5/瓶" → "/瓶"。
     * 用于让原价展示与现价保持相同单位，如 "¥29.9/斤" 对应 "¥38.90/斤"。
     */
    public static String extractSuffix(String price) {
        String s = stripSymbol(price);
        int i = 0;
        while (i < s.length() && (Character.isDigit(s.charAt(i)) || s.charAt(i) == '.')) {
            i++;
        }
        return s.substring(i);
    }

    /**
     * 计算折扣文案，例如 9.9 / 12.9 → "7.7折"。
     *
     * @return 折扣文案；当无有效优惠（原价 ≤ 0 或 原价 ≤ 现价）时返回 {@code null}，
     *         UI 应据此隐藏折扣标签。
     */
    public static String formatDiscount(double price, double originalPrice) {
        if (!hasDiscount(price, originalPrice)) {
            return null;
        }
        double ratio = price / originalPrice * 10.0;
        String s = String.format(Locale.CHINA, "%.1f", ratio);
        // 去掉无意义的小数位：8.0 → 8
        if (s.endsWith(".0")) {
            s = s.substring(0, s.length() - 2);
        }
        return s + "折";
    }

    /** 是否存在有效优惠：原价 > 0 且 原价 > 现价。 */
    public static boolean hasDiscount(double price, double originalPrice) {
        return originalPrice > 0 && price > 0 && originalPrice > price;
    }
}
