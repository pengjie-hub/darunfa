package com.example.myapplication.view;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.example.myapplication.R;
import com.example.myapplication.activity.PayActivity;
import com.example.myapplication.remote.SessionManager;
import com.google.android.material.button.MaterialButton;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

/**
 * 聊天消息"结构化附件"渲染器。
 * <p>
 * 渲染方式：把每个 attachment 按 type 分发到对应 inflate 函数。
 * <p>
 * 安全要点：
 * <ul>
 *   <li>渲染前清空容器（避免复用残留）；</li>
 *   <li>所有字段访问都用 try/catch 兜底，绝不让后端字段缺失时崩溃；</li>
 *   <li>真实下单等敏感动作永远经过用户点击确认 —— AI 不会自动调用。</li>
 * </ul>
 */
final class AttachmentCardBinder {

    private AttachmentCardBinder() {}

    /** 渲染入口：按 attachments 顺序填充父容器。 */
    static void render(LinearLayout container,
                       @NonNull List<ChatAttachment> attachments,
                       @NonNull Context ctx) {
        // 清掉旧卡片
        container.removeAllViews();
        if (attachments.isEmpty()) {
            container.setVisibility(View.GONE);
            return;
        }
        container.setVisibility(View.VISIBLE);
        LayoutInflater inflater = LayoutInflater.from(ctx);
        for (ChatAttachment a : attachments) {
            try {
                int layoutId;
                switch (a.type) {
                    case PRODUCTS:        layoutId = R.layout.item_chat_attachment_products; break;
                    case COMPARISON:      layoutId = R.layout.item_chat_attachment_comparison; break;
                    case LOGISTICS:       layoutId = R.layout.item_chat_attachment_logistics; break;
                    case ORDER_PREVIEW:   layoutId = R.layout.item_chat_attachment_order_preview; break;
                    default: continue;
                }
                View card = inflater.inflate(layoutId, container, false);
                switch (a.type) {
                    case PRODUCTS:        bindProducts(card, a.data, ctx); break;
                    case COMPARISON:      bindComparison(card, a.data, inflater); break;
                    case LOGISTICS:       bindLogistics(card, a.data, inflater); break;
                    case ORDER_PREVIEW:   bindOrderPreview(card, a.data, inflater, ctx); break;
                }
                container.addView(card);
            } catch (Exception e) {
                // 单张卡片渲染失败不要搞坏整条 AI 消息
                // android.util.Log.w("ChatAssistant", "render attachment failed", e);
            }
        }
    }

    // ============================================================
    //  1. 找商品结果
    // ============================================================

    private static void bindProducts(View card, JSONObject data, Context ctx) {
        TextView tvTitle = card.findViewById(R.id.tv_chat_card_title);
        LinearLayout row = card.findViewById(R.id.ll_chat_products_row);
        // 标题：若 count 为 0 显示未找到
        int count = data.optInt("count", 0);
        if (count == 0) {
            tvTitle.setText("未找到符合条件的商品");
            row.removeAllViews();
            // 仍展示一个提示卡片
            TextView empty = new TextView(ctx);
            empty.setText("换个关键词试试，或告诉我你想买的东西大类。");
            empty.setPadding(8, 8, 8, 8);
            empty.setTextColor(ctx.getResources().getColor(R.color.chat_text_ai_muted));
            empty.setTextSize(12);
            row.addView(empty);
            return;
        }
        tvTitle.setText("为你找到 " + count + " 款商品");
        JSONArray arr = data.optJSONArray("products");
        if (arr == null) return;
        LayoutInflater inflater = LayoutInflater.from(ctx);
        for (int i = 0; i < arr.length(); i++) {
            JSONObject p = arr.optJSONObject(i);
            if (p == null) continue;
            View item = inflater.inflate(R.layout.item_chat_attachment_product_item, row, false);
            bindProductItem(item, p, ctx);
            row.addView(item);
        }
    }

    private static void bindProductItem(View item, JSONObject p, Context ctx) {
        ImageView iv = item.findViewById(R.id.iv_chat_product_image);
        TextView tvName = item.findViewById(R.id.tv_chat_product_name);
        TextView tvPrice = item.findViewById(R.id.tv_chat_product_price);
        TextView tvMeta = item.findViewById(R.id.tv_chat_product_meta);

        tvName.setText(p.optString("name", "未命名商品"));
        tvPrice.setText("¥" + p.optString("price", "0.00"));
        int stock = p.optInt("stock", 0);
        int sales = p.optInt("sales", 0);
        tvMeta.setText("库存 " + stock + " / 售 " + sales);
        tvName.setTextColor(ctx.getResources().getColor(R.color.chat_text_ai));

        // 图片：URL 是 drawable 资源名（如 product_1），用 getIdentifier 拿；找不到 → 默认头像
        String imageKey = p.optString("image", "").trim();
        iv.setImageResource(R.drawable.avatar_1);
        if (!imageKey.isEmpty()) {
            int resId = ctx.getResources().getIdentifier(imageKey, "drawable", ctx.getPackageName());
            if (resId != 0) iv.setImageResource(resId);
        }

        // 点击商品卡 → 跳详情（演示用：把商品 id 通过 click tag 暴露给宿主）
        long pid = p.optLong("id", -1);
        item.setOnClickListener(v -> {
            if (pid <= 0) return;
            IntentDispatcher.openProductDetail(ctx, pid);
        });
    }

    // ============================================================
    //  2. 参数对比
    // ============================================================

    private static void bindComparison(View card, JSONObject data, LayoutInflater inflater) {
        // 容器：表头（横向 LinearLayout）+ 各行
        LinearLayout headerRow = card.findViewById(R.id.ll_chat_compare_root);
        LinearLayout rowsContainer = card.findViewById(R.id.ll_chat_compare_rows);
        headerRow.removeAllViews();
        rowsContainer.removeAllViews();

        // 表头：第一列空（参数名占位），后面每列一个商品名
        TextView blankHeader = (TextView) inflater.inflate(
                R.layout.item_chat_attachment_comparison_header, headerRow, false);
        blankHeader.setText("参数");
        blankHeader.setTextColor(headerRow.getResources().getColor(R.color.chat_text_ai_muted));
        headerRow.addView(blankHeader);

        JSONArray headers = data.optJSONArray("headers");
        if (headers != null) {
            for (int i = 0; i < headers.length(); i++) {
                JSONObject h = headers.optJSONObject(i);
                if (h == null) continue;
                TextView cell = (TextView) inflater.inflate(
                        R.layout.item_chat_attachment_comparison_header, headerRow, false);
                cell.setText(h.optString("name", "商品 " + (i + 1)));
                cell.setTextColor(headerRow.getResources().getColor(R.color.chat_text_ai));
                headerRow.addView(cell);
            }
        }

        // 各行：label + values
        JSONArray rows = data.optJSONArray("rows");
        if (rows == null) return;
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.optJSONObject(i);
            if (row == null) continue;
            View rowView = inflater.inflate(R.layout.item_chat_attachment_comparison_row, rowsContainer, false);
            TextView tvLabel = rowView.findViewById(R.id.tv_chat_compare_row_label);
            LinearLayout valuesBox = rowView.findViewById(R.id.ll_chat_compare_row_values);
            tvLabel.setText(row.optString("label", ""));
            JSONArray values = row.optJSONArray("values");
            if (values != null) {
                for (int j = 0; j < values.length(); j++) {
                    TextView cell = new TextView(rowView.getContext());
                    String v = values.optString(j, "-");
                    cell.setText(v);
                    cell.setTextSize(12);
                    // 价格用品牌红高亮，其它字段用默认 AI 文字色
                    String labelText = tvLabel.getText().toString();
                    int valueColor = "当前价".equals(labelText) || "原价".equals(labelText)
                            ? R.color.rt_primary
                            : R.color.chat_text_ai;
                    cell.setTextColor(rowView.getResources().getColor(valueColor));
                    if (valueColor == R.color.rt_primary) {
                        cell.setTypeface(null, android.graphics.Typeface.BOLD);
                    }
                    cell.setPadding(8, 4, 8, 4);
                    // 让每个单元格等宽对齐
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(120, LinearLayout.LayoutParams.WRAP_CONTENT);
                    cell.setLayoutParams(lp);
                    valuesBox.addView(cell);
                }
            }
            // 整行加底分隔线
            if (i < rows.length() - 1) {
                View divider = new View(rowView.getContext());
                divider.setLayoutParams(new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, 1));
                divider.setBackgroundColor(rowView.getResources().getColor(R.color.chat_divider));
                rowsContainer.addView(divider);
            }
            rowsContainer.addView(rowView);
        }
    }

    // ============================================================
    //  3. 查物流（订单 + 物流时间线）
    // ============================================================

    private static void bindLogistics(View card, JSONObject data, LayoutInflater inflater) {
        TextView tvOrderNo = card.findViewById(R.id.tv_chat_logistics_orderno);
        TextView tvStatus = card.findViewById(R.id.tv_chat_logistics_status);
        TextView tvCompany = card.findViewById(R.id.tv_chat_logistics_company);
        TextView tvNoRecord = card.findViewById(R.id.tv_chat_logistics_no_record);
        LinearLayout timeline = card.findViewById(R.id.ll_chat_logistics_timeline);

        tvOrderNo.setText("订单号 " + data.optString("orderNo", ""));
        tvStatus.setText(data.optString("orderStatusLabel", ""));

        // 物流概要
        JSONObject shipment = data.optJSONObject("shipment");
        boolean hasShipment = shipment != null;
        if (hasShipment) {
            String company = shipment.optString("company", "").trim();
            String tracking = shipment.optString("trackingNo", "").trim();
            String stat = shipment.optString("statusLabel", "");
            tvCompany.setText(company + (tracking.isEmpty() ? "" : " · " + tracking) + "（" + stat + "）");
        } else {
            tvCompany.setText("尚未发货");
        }

        // 时间线节点：从 paidAt/shippedAt/deliveredAt + shipment.remark 拼
        timeline.removeAllViews();
        if (!hasShipment) {
            tvNoRecord.setVisibility(View.VISIBLE);
            tvNoRecord.setText("尚未发货，请耐心等待商家安排出库。");
            return;
        }
        tvNoRecord.setVisibility(View.GONE);

        // 节点 1：下单
        addTimelineNode(inflater, timeline, "已下单", data.optString("createdAt", ""), "");
        String paidAt = data.optString("paidAt", "");
        if (!paidAt.isEmpty()) {
            addTimelineNode(inflater, timeline, "已支付", paidAt, "");
        }
        String shippedAt = data.optString("shippedAt", "");
        if (!shippedAt.isEmpty()) {
            addTimelineNode(inflater, timeline, "商家已发货", shippedAt,
                    shipment.optString("company", "") + " 揽收成功");
        }
        String deliveredAt = data.optString("deliveredAt", "");
        String shipmentRemark = shipment.optString("remark", "");
        if (!deliveredAt.isEmpty() || !shipmentRemark.isEmpty()) {
            addTimelineNode(inflater, timeline, "已签收",
                    deliveredAt.isEmpty() ? shipment.optString("shippedAt", "") : deliveredAt,
                    shipmentRemark.isEmpty() ? "派送中" : shipmentRemark);
        }
    }

    private static void addTimelineNode(LayoutInflater inflater, LinearLayout parent,
                                        String status, String time, String remark) {
        View node = inflater.inflate(R.layout.item_chat_attachment_logistics_node, parent, false);
        TextView tvStatus = node.findViewById(R.id.tv_chat_logistics_node_status);
        TextView tvTime = node.findViewById(R.id.tv_chat_logistics_node_time);
        TextView tvRemark = node.findViewById(R.id.tv_chat_logistics_node_remark);
        TextView tvDot = node.findViewById(R.id.tv_chat_logistics_node_dot);
        tvStatus.setText(status);
        tvTime.setText(time);
        tvRemark.setText(remark);
        // 圆点：用背景图形，文字设为空
        tvDot.setText("");
        parent.addView(node);
    }

    // ============================================================
    //  4. 订单预览：点确认下单按钮 → POST /api/orders
    // ============================================================

    private static void bindOrderPreview(View card, JSONObject data, LayoutInflater inflater, Context ctx) {
        LinearLayout itemsBox = card.findViewById(R.id.ll_chat_order_items);
        TextView tvTotal = card.findViewById(R.id.tv_chat_order_total);
        TextView tvAddress = card.findViewById(R.id.tv_chat_order_address);
        MaterialButton btnConfirm = card.findViewById(R.id.btn_chat_order_confirm);

        itemsBox.removeAllViews();
        JSONArray items = data.optJSONArray("items");
        if (items != null) {
            for (int i = 0; i < items.length(); i++) {
                JSONObject it = items.optJSONObject(i);
                if (it == null) continue;
                View row = inflater.inflate(R.layout.item_chat_attachment_order_item, itemsBox, false);
                TextView tvName = row.findViewById(R.id.tv_chat_order_item_name);
                TextView tvQty = row.findViewById(R.id.tv_chat_order_item_qty);
                TextView tvSub = row.findViewById(R.id.tv_chat_order_item_subtotal);
                tvName.setText(it.optString("productName", ""));
                tvQty.setText("x " + it.optInt("quantity", 0));
                tvSub.setText("¥" + it.optString("subtotal", "0.00"));
                itemsBox.addView(row);
            }
        }
        tvTotal.setText("¥" + data.optString("totalAmount", "0.00"));

        // 默认收货地址
        JSONObject addr = data.optJSONObject("defaultAddress");
        if (addr != null) {
            tvAddress.setText("默认收货：" + addr.optString("name", "") + " " +
                    addr.optString("phone", "") + " · " + addr.optString("address", ""));
        } else {
            tvAddress.setText(R.string.chat_attachment_order_no_default_address);
        }

        // 确认下单：AI 不会自动调；必须由用户点击按钮进入收银台
        btnConfirm.setEnabled(true);
        final JSONArray cartIdsArr = data.optJSONArray("cartItemIds");
        // 解析成收银台「下单模式」需要的入参（购物车项 id 列表 + 本地合计金额）
        final List<Long> cartIds = new java.util.ArrayList<>();
        if (cartIdsArr != null) {
            for (int i = 0; i < cartIdsArr.length(); i++) {
                try {
                    cartIds.add(cartIdsArr.getLong(i));
                } catch (JSONException ignored) {
                }
            }
        }
        final double totalAmount = parseAmount(data.optString("totalAmount", "0"));
        // 收货人：优先用卡片带来的默认地址（与收银台同源，取地址簿默认地址），其次回退到本地登录用户名
        final String profileName = ctx.getSharedPreferences("user", Context.MODE_PRIVATE)
                .getString("username", "收货人");
        final String receiverName = (addr == null || addr.optString("name", "").isEmpty())
                ? profileName : addr.optString("name", "");
        btnConfirm.setOnClickListener(v -> {
            if (!SessionManager.isLoggedIn()) {
                Toast.makeText(ctx, R.string.chat_attachment_need_login, Toast.LENGTH_SHORT).show();
                return;
            }
            if (cartIds.isEmpty()) {
                Toast.makeText(ctx, "购物车项已失效，请重试", Toast.LENGTH_SHORT).show();
                return;
            }
            // 一律进收银台「下单模式」，不再分「有没有默认地址」两条路：
            // 地址/电话由收银台自动带出默认地址（可当场改），还能选优惠券、看费用明细，
            // 点「确认支付」才真正下单 + 支付。
            // 好处：小发不会替用户下单；中途返回也不会留下「待付款」垃圾订单。
            PayActivity.startCheckout(ctx, cartIds, totalAmount, receiverName);
        });
    }

    /** 宽松解析金额字符串（后端偶尔给 "89.9" / "" / "0.00"）。 */
    private static double parseAmount(String raw) {
        if (raw == null || raw.isEmpty()) return 0d;
        try {
            return Double.parseDouble(raw.trim());
        } catch (NumberFormatException e) {
            return 0d;
        }
    }
}

