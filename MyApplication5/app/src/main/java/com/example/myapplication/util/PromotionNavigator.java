package com.example.myapplication.util;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;

import com.example.myapplication.R;
import com.example.myapplication.activity.LoginActivity;
import com.example.myapplication.activity.ProductDetailsActivity;
import com.example.myapplication.data.RepositoryProvider;
import com.example.myapplication.remote.ApiCallback;
import com.example.myapplication.remote.RetrofitClient;
import com.example.myapplication.remote.SessionManager;
import com.example.myapplication.remote.dto.CouponDTO;
import com.example.myapplication.remote.dto.PromotionDTO;
import com.google.android.material.button.MaterialButton;

/**
 * 促销活动点击跳转的统一入口（首页板块与促销列表页共用）。
 * <p>
 * 跳转优先级：
 * <ol>
 *   <li>{@code link} 以 {@code http(s)://} 开头 → 用系统浏览器打开外链；</li>
 *   <li>{@code link} 是纯数字 → 视为商品 ID，跳商品详情；</li>
 *   <li>{@code link} 为空但 {@code productId} 有值 → 跳商品详情；</li>
 *   <li>以上都不满足（如「全场满减」类无具体跳转目标）→ 弹出活动详情对话框兜底，
 *       避免"点了没反应"的糟糕体验。</li>
 * </ol>
 */
public final class PromotionNavigator {

    private static final String TAG = "PromotionNavigator";

    private PromotionNavigator() {
    }

    public static void open(Context ctx, PromotionDTO p) {
        if (ctx == null || p == null) return;
        String link = p.link == null ? "" : p.link.trim();

        // 1. 外链
        if (link.startsWith("http://") || link.startsWith("https://")) {
            openExternal(ctx, link);
            return;
        }

        // 2. link 为纯数字 → 商品 ID
        if (link.matches("\\d+")) {
            openProductDetail(ctx, parseLong(link, -1));
            return;
        }

        // 3. 兜底用 productId
        if (p.productId != null && p.productId > 0) {
            openProductDetail(ctx, p.productId);
            return;
        }

        // 4. 无跳转目标 → 详情弹窗
        showDetailDialog(ctx, p);
    }

    private static void openProductDetail(Context ctx, long productId) {
        if (productId <= 0) {
            Toast.makeText(ctx, "该活动暂无关联商品", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(ctx, ProductDetailsActivity.class);
        // 统一用 ProductDetailsActivity.EXTRA_PRODUCT_ID，避免 key / 类型在多处漂移
        intent.putExtra(ProductDetailsActivity.EXTRA_PRODUCT_ID, productId);
        ctx.startActivity(intent);
    }

    private static void openExternal(Context ctx, String url) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            ctx.startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Log.w(TAG, "没有可打开该链接的应用: " + url, e);
            Toast.makeText(ctx, "无法打开该链接", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 无跳转目标时的兜底：把活动信息完整展示出来。
     * <p>
     * 使用自定义紧凑布局（{@code dialog_promotion_detail}）而非系统默认 AlertDialog——
     * 默认弹框的"大标题 + 大留白"会显得臃肿突兀，这里把高度压到约 190dp。
     * <p>
     * 控高策略（保证任何屏幕尺寸下都不溢出、不遮挡过多内容）：
     * <ol>
     *   <li>宽度固定为屏宽的 86%，两侧留安全边距；</li>
     *   <li>高度先按内容自然撑开；</li>
     *   <li>若自然高度超过屏高的 55%，则把超出部分<b>只从描述滚动区扣除</b>，
     *       标题 / 时间 / 按钮始终完整可见，长描述改为弹框内部滚动。</li>
     * </ol>
     */
    private static void showDetailDialog(Context ctx, PromotionDTO p) {
        View content = LayoutInflater.from(ctx)
                .inflate(R.layout.dialog_promotion_detail, null, false);

        LinearLayout header = content.findViewById(R.id.layout_promo_header);
        TextView tvType = content.findViewById(R.id.tv_dialog_type);
        TextView tvTag = content.findViewById(R.id.tv_dialog_tag);
        TextView tvName = content.findViewById(R.id.tv_dialog_name);
        TextView tvTime = content.findViewById(R.id.tv_dialog_time);
        TextView tvDesc = content.findViewById(R.id.tv_dialog_desc);
        ScrollView svBody = content.findViewById(R.id.sv_dialog_body);

        // 头部：类型标签 + 优惠角标合并成一行，省掉独立标题块
        tvType.setText(TextUtils.isEmpty(p.typeLabel) ? "促销" : p.typeLabel);
        tvTag.setText(TextUtils.isEmpty(p.tagText) ? "优惠活动" : p.tagText);
        header.setBackground(headerBackground(ctx, p));

        // 标题为空时整行隐藏，不留空白
        if (TextUtils.isEmpty(p.name)) {
            tvName.setVisibility(View.GONE);
        } else {
            tvName.setText(p.name);
        }

        // 时间：两端都可能为空，只有一端时另一侧显示「不限」；都没有则整行隐藏
        boolean hasStart = !TextUtils.isEmpty(p.startTime);
        boolean hasEnd = !TextUtils.isEmpty(p.endTime);
        if (hasStart || hasEnd) {
            tvTime.setText("活动时间：" + (hasStart ? p.startTime : "不限")
                    + " 至 " + (hasEnd ? p.endTime : "不限"));
        } else {
            tvTime.setVisibility(View.GONE);
        }

        tvDesc.setText(TextUtils.isEmpty(p.description) ? "暂无更多活动说明" : p.description);

        final AlertDialog dialog = new AlertDialog.Builder(ctx)
                .setView(content, 0, 0, 0, 0)
                .create();

        final float density = ctx.getResources().getDisplayMetrics().density;
        // 宽度：屏宽 86%，但在平板/大屏上最多 360dp，避免弹框被拉得过宽
        final int width = Math.min(
                Math.round(ctx.getResources().getDisplayMetrics().widthPixels * 0.86f),
                Math.round(360 * density));
        // 高度上限：屏高 55%（横屏等矮屏下仍保证标题/时间/按钮可见）
        final int maxHeight = Math.round(ctx.getResources().getDisplayMetrics().heightPixels * 0.55f);
        // 描述滚动区的最小可见高度，避免被压成一条缝
        final int minBodyHeight = Math.round(72 * density);

        dialog.setOnShowListener(d -> {
            Window win = dialog.getWindow();
            if (win == null) return;
            // 透明窗口背景：圆角完全交给 bg_dialog_sheet，避免系统白底方角在圆角外露边
            win.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            win.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);

            final View decor = win.getDecorView();
            decor.post(() -> {
                int natural = decor.getHeight();
                if (natural <= maxHeight) return;

                // 除描述区以外的固定高度（标题 / 时间 / 按钮 / 内边距）
                int bodyHeight = svBody.getHeight();
                if (bodyHeight <= 0) {
                    // 极端情况下描述区尚未完成布局：按"整窗减去一个最小描述区"估算，避免反推出更大的高度
                    bodyHeight = Math.max(0, natural - minBodyHeight);
                }
                int others = natural - bodyHeight;

                int target = Math.max(minBodyHeight, maxHeight - others);
                // 只在确实需要压缩时才改，绝不把描述区改高
                if (target >= bodyHeight) return;

                ViewGroup.LayoutParams lp = svBody.getLayoutParams();
                lp.height = target;
                svBody.setLayoutParams(lp);
                Window w = dialog.getWindow();
                if (w != null) {
                    w.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
                }
            });
        });

        content.findViewById(R.id.btn_dialog_ok).setOnClickListener(v -> dialog.dismiss());

        // 券型活动（有满减/折扣规则）才给「领取优惠券」，避免对纯展示活动露出无意义的按钮
        MaterialButton btnClaim = content.findViewById(R.id.btn_dialog_claim);
        if (isClaimable(p)) {
            btnClaim.setVisibility(View.VISIBLE);
            btnClaim.setOnClickListener(v -> claimCoupon(ctx, p, btnClaim));
        } else {
            btnClaim.setVisibility(View.GONE);
        }

        dialog.show();
    }

    /** 能领券 = 有实际优惠规则（满减门槛/减免额都大于 0）。 */
    private static boolean isClaimable(PromotionDTO p) {
        return p != null && p.threshold > 0 && p.discount > 0;
    }

    /**
     * 领取该活动对应的优惠券。
     * <p>
     * 领取必须登录（后端按 token 记账），所以未登录时直接引导去登录页 ——
     * 而不是发一个注定 401 的请求再弹后端文案。
     * 成功后按钮置灰为「已领取」，避免重复点（后端也会拦「同一活动限领一张」）。
     */
    private static void claimCoupon(Context ctx, PromotionDTO p, MaterialButton btn) {
        try {
            RetrofitClient.init(ctx.getApplicationContext());
        } catch (Exception e) {
            Log.w(TAG, "网络层初始化失败", e);
        }
        if (!SessionManager.isLoggedIn()) {
            Toast.makeText(ctx, "请先登录后再领取优惠券", Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(ctx, LoginActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(intent);
            return;
        }

        btn.setEnabled(false);
        btn.setText("领取中…");
        RepositoryProvider.getInstance(ctx).coupon().claim(p.id, new ApiCallback<CouponDTO>() {
            @Override
            public void onSuccess(CouponDTO data) {
                btn.setText("已领取");
                Toast.makeText(ctx, "领取成功，可在「我的 - 优惠券」查看", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(int code, String message) {
                btn.setEnabled(true);
                btn.setText("领取优惠券");
                Toast.makeText(ctx, message == null ? "领取失败，请稍后重试" : message,
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * 弹框头部色带背景：满减走品牌红、折扣走橙色；左右渐变 + <b>仅顶部圆角</b>，
     * 与弹出的圆角底板严丝合缝。
     */
    private static GradientDrawable headerBackground(Context ctx, PromotionDTO p) {
        boolean discount = p != null && "DISCOUNT".equalsIgnoreCase(p.type);
        int start = ContextCompat.getColor(ctx,
                discount ? R.color.rt_promo_orange_light : R.color.rt_primary_light);
        int end = ContextCompat.getColor(ctx,
                discount ? R.color.rt_promo_orange : R.color.rt_primary_dark);
        GradientDrawable d = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT, new int[]{start, end});
        float r = 16f * ctx.getResources().getDisplayMetrics().density;
        d.setCornerRadii(new float[]{r, r, r, r, 0f, 0f, 0f, 0f});
        return d;
    }

    private static long parseLong(String s, long def) {
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    /** 卡片配图占位：折扣用橙色，其余(满减)用品牌红。 */
    public static int placeholderRes(PromotionDTO p) {
        return (p != null && "DISCOUNT".equalsIgnoreCase(p.type))
                ? R.drawable.bg_promo_orange
                : R.drawable.bg_promo_red;
    }
}
