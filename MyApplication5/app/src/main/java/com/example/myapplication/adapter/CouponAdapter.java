package com.example.myapplication.adapter;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.R;
import com.example.myapplication.remote.dto.CouponDTO;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 优惠券列表适配器（我的优惠券页）。
 * <p>
 * 只负责渲染，不判断「现在能不能用」—— 三态由后端算好放在 {@code status} 里，
 * 前端照着显示即可，否则「列表说未使用、结算说已过期」这类口径不一致会反复出现。
 */
public class CouponAdapter extends RecyclerView.Adapter<CouponAdapter.VH> {

    public interface Listener {
        /** 点「去使用」，一般用于把用户导到可下单的地方。 */
        void onUse(CouponDTO coupon);
    }

    private final List<CouponDTO> data = new ArrayList<>();
    private Listener listener;

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void setData(List<CouponDTO> list) {
        data.clear();
        if (list != null) data.addAll(list);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_coupon, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        CouponDTO c = data.get(position);
        boolean unused = c.isUnused();

        h.tvAmount.setText(amountText(c));
        h.tvThreshold.setText(thresholdText(c));
        h.tvName.setText(c.name == null ? "" : c.name);
        h.tvRule.setText(c.ruleText == null ? "" : c.ruleText);

        if (TextUtils.isEmpty(c.expireTime)) {
            h.tvExpire.setVisibility(View.GONE);
        } else {
            h.tvExpire.setText("有效期至 " + c.expireTime);
            h.tvExpire.setVisibility(View.VISIBLE);
        }

        if (unused) {
            h.tvAction.setText("去使用");
            h.tvAction.setTextColor(h.itemView.getContext().getColor(R.color.rt_primary));
            h.tvAction.setEnabled(true);
            h.tvAction.setOnClickListener(v -> {
                if (listener != null) listener.onUse(c);
            });
        } else {
            // 已使用 / 已过期：按钮位改显示状态，点不动
            h.tvAction.setText(c.statusLabel == null ? "" : c.statusLabel);
            h.tvAction.setTextColor(h.itemView.getContext().getColor(R.color.rt_text_hint));
            h.tvAction.setEnabled(false);
            h.tvAction.setOnClickListener(null);
        }

        // 不可用的券整体压暗，一眼区分
        h.itemView.setAlpha(unused ? 1f : 0.55f);
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    /** 券面主数字：满减显示「¥10」，折扣显示「9折」。 */
    private static String amountText(CouponDTO c) {
        if ("DISCOUNT".equalsIgnoreCase(c.type)) {
            return num(c.discount * 10) + "折";
        }
        return "¥" + num(c.discount);
    }

    /** 门槛文案：有门槛写「满 ¥100 可用」，没有则「无门槛」。 */
    private static String thresholdText(CouponDTO c) {
        if (c.threshold > 0) {
            return "满 ¥" + num(c.threshold) + " 可用";
        }
        return "无门槛";
    }

    /** 去掉多余小数：10.0 → "10"，9.5 → "9.5"。 */
    private static String num(double v) {
        if (Math.abs(v - Math.rint(v)) < 0.001) {
            return String.valueOf((long) Math.rint(v));
        }
        String s = String.format(Locale.CHINA, "%.2f", v);
        while (s.endsWith("0")) s = s.substring(0, s.length() - 1);
        if (s.endsWith(".")) s = s.substring(0, s.length() - 1);
        return s;
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView tvAmount;
        final TextView tvThreshold;
        final TextView tvName;
        final TextView tvRule;
        final TextView tvExpire;
        final TextView tvAction;

        VH(@NonNull View itemView) {
            super(itemView);
            tvAmount = itemView.findViewById(R.id.tv_coupon_amount);
            tvThreshold = itemView.findViewById(R.id.tv_coupon_threshold);
            tvName = itemView.findViewById(R.id.tv_coupon_name);
            tvRule = itemView.findViewById(R.id.tv_coupon_rule);
            tvExpire = itemView.findViewById(R.id.tv_coupon_expire);
            tvAction = itemView.findViewById(R.id.tv_coupon_action);
        }
    }
}
