package com.example.myapplication.activity;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.R;
import com.example.myapplication.data.RepositoryProvider;
import com.example.myapplication.data.WalletRepository;
import com.example.myapplication.remote.ApiCallback;
import com.example.myapplication.remote.RetrofitClient;
import com.example.myapplication.remote.dto.BalanceTransactionDTO;
import com.example.myapplication.remote.dto.WalletDTO;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * 「我的余额」页：展示账户余额、充值（预设金额 + 自定义）与余额明细。
 *
 * <h3>为什么要独立成页</h3>
 * 收银台的「余额支付」需要一个能「充值 + 查明细」的去处；同时「我的」页头部与
 * 「我的购物卡」页原先读的是本地 {@code SharedPreferences("shopping_card")} 的假余额（默认 ¥1000），
 * 与后端账户余额必然打架，本次统一改为读后端真实余额，本页即是余额的唯一权威展示与充值入口。
 *
 * <h3>充值幂等（{@link #pendingRequestId} 的三条规则，务必看）</h3>
 * <ol>
 *   <li><b>点「确认充值」时若没有 pendingRequestId 就新建一个</b>，与本次金额一起提交——
 *       用于标识「这一次充值意图」。</li>
 *   <li><b>请求成功 → 置空</b>：本次意图已兑现，下一次充值属于新的意图，应该用新的幂等键。</li>
 *   <li><b>请求失败 → 保留</b>：用户再点一次是「重试同一次充值」，复用同一个 id，
 *       服务端据此幂等去重，即使上一次其实已经入账（只是响应丢了）也不会重复入账。</li>
 *   <li><b>金额输入框内容变化 → 置空</b>：金额变了就是新的意图。否则服务端会把「新金额」
 *       当成「旧请求的重放」而拒绝入账，用户会看到「提示成功但余额没变」。</li>
 * </ol>
 */
public class WalletActivity extends AppCompatActivity {

    /** 单笔充值上限，与后端 WalletService 的校验保持一致。 */
    private static final double MAX_RECHARGE_AMOUNT = 50000.0;

    private WalletRepository walletRepository;

    private TextView tvBalance;
    private TextInputEditText etAmount;
    private MaterialButton btnRecharge;
    private RecyclerView rvTransactions;
    private TextView tvEmpty;

    private final List<BalanceTransactionDTO> transactions = new ArrayList<>();
    private TransactionAdapter adapter;

    /**
     * 当前「充值意图」的幂等键；null 表示没有未完成的充值意图。
     * 生成 / 复用 / 重置规则见类注释「充值幂等」。
     */
    private String pendingRequestId;

    /** 静态入口：从「我的」页 / 「我的购物卡」页 / 收银台余额不足引导进入本页。 */
    public static void start(Context ctx) {
        if (ctx == null) return;
        ctx.startActivity(new Intent(ctx, WalletActivity.class));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_wallet);

        // 项目铁律：必须早于任何网络调用初始化 Retrofit（本项目没有自定义 Application）
        RetrofitClient.init(getApplicationContext());
        walletRepository = RepositoryProvider.getInstance(this).wallet();

        bindViews();
        setupPresetChips();
        setupAmountWatcher();

        loadWallet();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 从收银台支付/退款返回时余额可能已变，回页即刷新
        loadWallet();
    }

    private void bindViews() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        tvBalance = findViewById(R.id.tv_wallet_balance);
        etAmount = findViewById(R.id.et_recharge_amount);
        btnRecharge = findViewById(R.id.btn_recharge);
        rvTransactions = findViewById(R.id.rv_transactions);
        tvEmpty = findViewById(R.id.tv_tx_empty);

        btnRecharge.setOnClickListener(v -> onRechargeClicked());

        adapter = new TransactionAdapter();
        rvTransactions.setLayoutManager(new LinearLayoutManager(this));
        rvTransactions.setAdapter(adapter);
    }

    /** 预设金额点击 → 填入输入框（由用户再点「确认充值」提交，避免误触直接扣款）。 */
    private void setupPresetChips() {
        bindPresetChip(R.id.chip_amount_10, 10);
        bindPresetChip(R.id.chip_amount_50, 50);
        bindPresetChip(R.id.chip_amount_100, 100);
        bindPresetChip(R.id.chip_amount_200, 200);
    }

    private void bindPresetChip(int chipId, final int amount) {
        Chip chip = findViewById(chipId);
        chip.setOnClickListener(v -> {
            if (etAmount != null) {
                etAmount.setText(String.valueOf(amount));
                etAmount.setSelection(etAmount.getText() == null ? 0 : etAmount.getText().length());
            }
        });
    }

    /**
     * 金额输入框内容变化 → 丢弃旧的幂等键（新金额 = 新意图）。
     * 详见类注释「充值幂等」第 4 条。
     */
    private void setupAmountWatcher() {
        etAmount.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                pendingRequestId = null;
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
    }

    /** 拉取最新钱包并渲染。 */
    private void loadWallet() {
        if (walletRepository == null) return;
        walletRepository.getWallet(null, new ApiCallback<WalletDTO>() {
            @Override
            public void onSuccess(WalletDTO data) {
                if (isFinishing() || isDestroyed()) return;
                renderWallet(data);
            }

            @Override
            public void onError(int code, String message) {
                if (isFinishing() || isDestroyed()) return;
                Toast.makeText(WalletActivity.this,
                        message == null ? ("加载余额失败 HTTP " + code) : message,
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    /** 用钱包快照渲染余额与其明细。 */
    private void renderWallet(WalletDTO data) {
        double balance = data == null ? 0.0 : data.balance;
        if (tvBalance != null) {
            tvBalance.setText(money(balance));
        }

        transactions.clear();
        if (data != null && data.transactions != null) {
            transactions.addAll(data.transactions);
        }
        adapter.notifyDataSetChanged();

        boolean empty = transactions.isEmpty();
        if (tvEmpty != null) tvEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        if (rvTransactions != null) rvTransactions.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    /** 「确认充值」：校验金额 → 复用/新建幂等键 → 提交。 */
    private void onRechargeClicked() {
        String raw = etAmount.getText() == null ? "" : etAmount.getText().toString().trim();
        if (TextUtils.isEmpty(raw)) {
            Toast.makeText(this, "请输入充值金额", Toast.LENGTH_SHORT).show();
            return;
        }
        double amount;
        try {
            amount = Double.parseDouble(raw);
        } catch (NumberFormatException e) {
            Toast.makeText(this, "金额格式不正确", Toast.LENGTH_SHORT).show();
            return;
        }
        if (amount <= 0) {
            Toast.makeText(this, "充值金额必须大于 0", Toast.LENGTH_SHORT).show();
            return;
        }
        if (amount > MAX_RECHARGE_AMOUNT) {
            Toast.makeText(this, "单笔充值不能超过 ¥50000", Toast.LENGTH_SHORT).show();
            return;
        }
        // 最多 2 位小数（与后端校验一致）
        int dot = raw.indexOf('.');
        if (dot >= 0 && raw.length() - dot - 1 > 2) {
            Toast.makeText(this, "金额最多保留 2 位小数", Toast.LENGTH_SHORT).show();
            return;
        }

        // 规则①：没有未完成的充值意图时新建幂等键
        if (pendingRequestId == null) {
            pendingRequestId = UUID.randomUUID().toString();
        }
        final String requestId = pendingRequestId;

        setRecharging(true);
        walletRepository.recharge(amount, requestId, new ApiCallback<WalletDTO>() {
            @Override
            public void onSuccess(WalletDTO data) {
                if (isFinishing() || isDestroyed()) return;
                setRecharging(false);
                // 规则②：成功 → 本次意图结束，下一次充值是新的意图
                pendingRequestId = null;
                renderWallet(data);
                double balance = data == null ? 0.0 : data.balance;
                Toast.makeText(WalletActivity.this,
                        "充值成功，当前余额 " + money(balance), Toast.LENGTH_LONG).show();
                if (etAmount != null) etAmount.setText("");
            }

            @Override
            public void onError(int code, String message) {
                if (isFinishing() || isDestroyed()) return;
                setRecharging(false);
                // 规则③：失败 → 保留 pendingRequestId，用户再点是「重试同一次充值」
                Toast.makeText(WalletActivity.this,
                        message == null ? ("充值失败 HTTP " + code) : message,
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    private void setRecharging(boolean recharging) {
        if (btnRecharge != null) {
            btnRecharge.setEnabled(!recharging);
            btnRecharge.setText(recharging ? "充值中…" : "确认充值");
        }
    }

    private String money(double v) {
        return "¥" + String.format(Locale.getDefault(), "%.2f", v);
    }

    /**
     * 余额流水适配器。
     * <p>金额配色按中文习惯：入账（+）用主色红，出账（-）用深灰；不用绿色表示减少。
     */
    private class TransactionAdapter extends RecyclerView.Adapter<TransactionAdapter.VH> {

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_balance_transaction, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            BalanceTransactionDTO tx = transactions.get(position);

            String typeLabel = TextUtils.isEmpty(tx.typeLabel) ? safeType(tx.type) : tx.typeLabel;
            holder.tvTypeLabel.setText(typeLabel);

            if (TextUtils.isEmpty(tx.remark)) {
                holder.tvRemark.setVisibility(View.GONE);
            } else {
                holder.tvRemark.setVisibility(View.VISIBLE);
                holder.tvRemark.setText(tx.remark);
            }

            holder.tvTime.setText(tx.createdAt == null ? "" : tx.createdAt);

            // 有符号金额：正 → "+" + 主色红；负 → "-" + 深灰
            boolean income = tx.amount >= 0;
            String sign = income ? "+" : "-";
            holder.tvAmount.setText(sign + money(Math.abs(tx.amount)));
            holder.tvAmount.setTextColor(getColor(
                    income ? R.color.rt_primary : R.color.rt_text_primary));
        }

        /** typeLabel 缺失时的兜底中文（正常后端都会给 typeLabel）。 */
        private String safeType(String type) {
            if (type == null) return "余额变动";
            switch (type) {
                case "INIT":
                    return "初始赠送";
                case "RECHARGE":
                    return "充值";
                case "PAY":
                    return "余额支付";
                case "REFUND":
                    return "退款返现";
                default:
                    return "余额变动";
            }
        }

        @Override
        public int getItemCount() {
            return transactions.size();
        }

        class VH extends RecyclerView.ViewHolder {
            final TextView tvTypeLabel;
            final TextView tvRemark;
            final TextView tvTime;
            final TextView tvAmount;

            VH(@NonNull View itemView) {
                super(itemView);
                tvTypeLabel = itemView.findViewById(R.id.tv_tx_type_label);
                tvRemark = itemView.findViewById(R.id.tv_tx_remark);
                tvTime = itemView.findViewById(R.id.tv_tx_time);
                tvAmount = itemView.findViewById(R.id.tv_tx_amount);
            }
        }
    }
}
