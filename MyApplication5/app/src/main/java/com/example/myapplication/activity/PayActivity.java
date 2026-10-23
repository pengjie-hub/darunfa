package com.example.myapplication.activity;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.example.myapplication.R;
import com.example.myapplication.data.AddressRepository;
import com.example.myapplication.data.CouponRepository;
import com.example.myapplication.data.OrderRepository;
import com.example.myapplication.data.RepositoryProvider;
import com.example.myapplication.data.WalletRepository;
import com.example.myapplication.entity.OrderBrief;
import com.example.myapplication.entity.OrderStatus;
import com.example.myapplication.remote.ApiCallback;
import com.example.myapplication.remote.RetrofitClient;
import com.example.myapplication.remote.dto.AddressDTO;
import com.example.myapplication.remote.dto.CheckoutPreviewDTO;
import com.example.myapplication.remote.dto.CouponDTO;
import com.example.myapplication.remote.dto.WalletDTO;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 收银台：全站唯一的支付页（旧的 {@code dialog_order_confirm} 弹窗已被本页取代）。
 * <p>
 * 背景：下单（{@code POST /api/orders}）只把订单建成「待付款」，必须由用户在本页点
 * 「确认支付」调 {@code PUT /api/orders/{id}/pay}，订单才流转到「待发货」——
 * 也就是「支付这一步不允许被跳过」。
 * <p>
 * 本页支持两种模式（由 intent 携带的 extra 决定）：
 * <ul>
 *   <li><b>支付模式</b>：只传 {@link #EXTRA_ORDER_ID}。订单已存在，金额与状态向后端取。</li>
 *   <li><b>下单模式</b>：传 {@link #EXTRA_CART_ITEM_IDS} + {@link #EXTRA_TOTAL_AMOUNT}。
 *       页内显示费用明细、可选优惠券、收货信息（自动带出默认地址），
 *       点「确认支付」时才 {@code createOrder}（带上选用的券）随后立即 {@code pay}。</li>
 * </ul>
 *
 * <h3>支付方式与余额支付</h3>
 * 页面上的 {@code rg_pay_method} 单选组真正接到支付请求里（历史上这段单选框是纯装饰）：
 * {@code rb_wechat→WECHAT、rb_alipay→ALIPAY、rb_bank_card→BANK、rb_balance→BALANCE}。
 * 「余额支付」选项的可用性与文案由 {@link #refreshBalanceOption()} 依据「当前应付金额」与
 * 「账户余额」运行时刷新：余额不足时置灰、自动切回微信，并给出「去充值」入口。
 * <p>
 * 余额一律以后端为准（{@code GET /api/wallet}），本页不做任何本地扣款——
 * 余额支付成功后由后端在同一事务里扣钱并写余额流水。
 *
 * <h3>优惠是怎么算出来的</h3>
 * 进页后调 {@code GET /api/promotions/calc} 做<b>试算</b>，把
 * 「商品总额 −（活动优惠 + 券优惠）= 应付」显示出来；换券时重新试算。
 * 下单时后端用<b>同一段算法</b>再算一次，所以看到多少就扣多少，
 * 不会出现「页面显示原价、订单里偷偷减钱」。
 */
public class PayActivity extends AppCompatActivity {

    /** 支付模式：订单 id（long）。 */
    public static final String EXTRA_ORDER_ID = "extra_order_id";
    /** 下单模式：购物车项 id 列表（long[]），长度 > 0 即代表下单模式。 */
    public static final String EXTRA_CART_ITEM_IDS = "extra_cart_item_ids";
    /** 下单模式：本地合计金额（double），仅作试算入参，最终以后端为准。 */
    public static final String EXTRA_TOTAL_AMOUNT = "extra_total_amount";
    /** 下单模式：收货人姓名（String），缺省回退本地登录用户名。 */
    public static final String EXTRA_RECEIVER_NAME = "extra_receiver_name";

    /** 金额比较容差：浮点比较用，避免 1000.00 与 1000.0 被判成不足。 */
    private static final double EPSILON = 0.0001;

    private long orderId;
    private boolean checkoutMode;
    private long[] cartItemIds;
    private String receiverName;
    private double localTotal;

    /** 下单模式下 createOrder 成功后记下订单 id；重试支付时只补支付，绝不重复下单。 */
    private long createdOrderId = -1;

    private OrderRepository orderRepository;
    private CouponRepository couponRepository;
    private AddressRepository addressRepository;
    private WalletRepository walletRepository;

    private TextView tvAmount;
    private TextView tvOrderNo;
    private TextView tvTip;
    private MaterialButton btnConfirmPay;

    private LinearLayout llReceipt;
    private TextView tvGoodsTotal;
    private LinearLayout rowPromoDiscount;
    private TextView tvPromoLabel;
    private TextView tvPromoDiscount;
    private LinearLayout rowCouponDiscount;
    private TextView tvCouponLabel;
    private TextView tvCouponDiscount;
    private TextView tvTotalDiscount;
    private TextView tvPromoHint;

    private LinearLayout llCouponPicker;
    private TextView tvCouponChoice;

    private LinearLayout llReceiver;
    private TextView tvDefaultAddressTag;
    private TextInputLayout tilPhone;
    private TextInputLayout tilAddress;
    private TextInputEditText etPhone;
    private TextInputEditText etAddress;

    // ---- 支付方式 ----
    private RadioGroup rgPayMethod;
    private RadioButton rbWechat;
    private RadioButton rbBalance;
    private TextView tvGoRecharge;

    /** 账户余额（元），来自 {@code GET /api/wallet}。 */
    private double balance;
    /** 是否已成功拉到过一次余额（未拉到前不给选余额，避免误选）。 */
    private boolean balanceLoaded;
    /** 当前应付金额，所有金额变化处都要同步更新它（余额足够性判断依赖它）。 */
    private double currentPayAmount;
    /** 首次 onResume 紧随 onCreate，跳过以免重复拉余额。 */
    private boolean firstResumeDone;
    /** 「因余额不足被自动切回微信」的提示只弹一次，避免反复刷新时刷屏。 */
    private boolean balanceFallbackToastShown;

    /** 当前可用券（已按优惠额从高到低）。 */
    private final List<CouponDTO> availableCoupons = new ArrayList<>();
    /** 当前选用的券 id，null 表示不使用。 */
    private Long selectedCouponId;

    private boolean paid;

    /** 支付模式入口：打开收银台支付一笔已存在的订单（支付成功返回 RESULT_OK）。 */
    public static void start(Context ctx, long orderId) {
        Intent intent = new Intent(ctx, PayActivity.class);
        intent.putExtra(EXTRA_ORDER_ID, orderId);
        ctx.startActivity(intent);
    }

    /**
     * 下单模式入口：把「下单 + 支付」合并到收银台一次完成。
     * <p>
     * 注意：调用本方法**不会**立刻下单，订单只在用户点「确认支付」时才创建，
     * 因此用户中途返回不会留下垃圾「待付款」订单。
     *
     * @param totalAmount 本地合计（仅作试算入参），下单成功后以后端金额为准
     */
    public static void startCheckout(Context ctx, List<Long> cartItemIds, double totalAmount,
                                     String receiverName) {
        Intent intent = new Intent(ctx, PayActivity.class);
        long[] ids = new long[cartItemIds == null ? 0 : cartItemIds.size()];
        for (int i = 0; i < ids.length; i++) {
            Long v = cartItemIds.get(i);
            ids[i] = v == null ? 0L : v;
        }
        intent.putExtra(EXTRA_CART_ITEM_IDS, ids);
        intent.putExtra(EXTRA_TOTAL_AMOUNT, totalAmount);
        intent.putExtra(EXTRA_RECEIVER_NAME, receiverName);
        ctx.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pay);

        // 兜底初始化（本项目没有自定义 Application，见 MainActivity 同名调用）
        RetrofitClient.init(getApplicationContext());

        Intent intent = getIntent();
        orderId = intent.getLongExtra(EXTRA_ORDER_ID, -1);
        cartItemIds = intent.getLongArrayExtra(EXTRA_CART_ITEM_IDS);
        checkoutMode = cartItemIds != null && cartItemIds.length > 0;
        localTotal = intent.getDoubleExtra(EXTRA_TOTAL_AMOUNT, 0);
        receiverName = intent.getStringExtra(EXTRA_RECEIVER_NAME);
        if (TextUtils.isEmpty(receiverName)) {
            SharedPreferences sp = getSharedPreferences("user", Context.MODE_PRIVATE);
            receiverName = sp.getString("username", "收货人");
        }

        RepositoryProvider provider = RepositoryProvider.getInstance(this);
        orderRepository = provider.order();
        couponRepository = provider.coupon();
        addressRepository = provider.address();
        walletRepository = provider.wallet();

        bindViews();

        // 拉一次余额（onResume 里还会再拉，覆盖「从充值页返回」的情况）
        loadWallet();

        // 返回 = 放弃支付：
        //   下单模式且订单还没创建 → 什么都没发生，静默退出；
        //   其余情况订单已存在（待付款），提示可稍后补付，所以只提示不报错。
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (!paid && !(checkoutMode && createdOrderId <= 0)) {
                    Toast.makeText(PayActivity.this,
                            "订单已生成（待付款），可在「我的 → 查看全部订单」继续支付",
                            Toast.LENGTH_LONG).show();
                }
                finish();
            }
        });

        if (checkoutMode) {
            setupCheckoutMode();
        } else {
            if (orderId <= 0) {
                Toast.makeText(this, "订单信息缺失", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }
            loadOrder();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 首次 onResume 紧随 onCreate（已拉过），跳过；之后每次回页（如从充值页返回）都刷新余额。
        if (!firstResumeDone) {
            firstResumeDone = true;
            return;
        }
        loadWallet();
    }

    private void bindViews() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());

        tvAmount = findViewById(R.id.tv_pay_amount);
        tvOrderNo = findViewById(R.id.tv_pay_order_no);
        tvTip = findViewById(R.id.tv_pay_tip);
        btnConfirmPay = findViewById(R.id.btn_confirm_pay);
        btnConfirmPay.setOnClickListener(v -> confirmPay());

        llReceipt = findViewById(R.id.ll_receipt);
        tvGoodsTotal = findViewById(R.id.tv_goods_total);
        rowPromoDiscount = findViewById(R.id.row_promo_discount);
        tvPromoLabel = findViewById(R.id.tv_promo_label);
        tvPromoDiscount = findViewById(R.id.tv_promo_discount);
        rowCouponDiscount = findViewById(R.id.row_coupon_discount);
        tvCouponLabel = findViewById(R.id.tv_coupon_label);
        tvCouponDiscount = findViewById(R.id.tv_coupon_discount);
        tvTotalDiscount = findViewById(R.id.tv_total_discount);
        tvPromoHint = findViewById(R.id.tv_promo_hint);

        llCouponPicker = findViewById(R.id.ll_coupon_picker);
        tvCouponChoice = findViewById(R.id.tv_coupon_choice);
        llCouponPicker.setOnClickListener(v -> showCouponPicker());

        llReceiver = findViewById(R.id.ll_receiver);
        tvDefaultAddressTag = findViewById(R.id.tv_default_address_tag);
        tilPhone = findViewById(R.id.til_phone);
        tilAddress = findViewById(R.id.til_address);
        etPhone = findViewById(R.id.et_phone);
        etAddress = findViewById(R.id.et_address);

        rgPayMethod = findViewById(R.id.rg_pay_method);
        rbWechat = findViewById(R.id.rb_wechat);
        rbBalance = findViewById(R.id.rb_balance);
        tvGoRecharge = findViewById(R.id.tv_go_recharge);
        if (tvGoRecharge != null) {
            tvGoRecharge.setOnClickListener(v -> WalletActivity.start(this));
        }
        // 初始化余额选项的可用状态（此时余额还没回来，会显示「加载中」并置灰）
        refreshBalanceOption();
    }

    /* ==================== 余额 ==================== */

    /** 拉取账户余额（失败时保持「不可选余额」的保守状态，不放开余额支付）。 */
    private void loadWallet() {
        if (walletRepository == null) return;
        walletRepository.getWallet(null, new ApiCallback<WalletDTO>() {
            @Override
            public void onSuccess(WalletDTO data) {
                if (isFinishing() || isDestroyed()) return;
                balance = data == null ? 0.0 : data.balance;
                balanceLoaded = true;
                refreshBalanceOption();
            }

            @Override
            public void onError(int code, String message) {
                // 静默：拿不到余额就不放开余额支付即可，不打断主流程
            }
        });
    }

    /**
     * 依据「当前余额」与「当前应付金额」刷新「余额支付」选项。
     * <ul>
     *   <li>余额充足 → 可选，文案「余额支付（可用 ¥x.xx）」；</li>
     *   <li>余额不足 → 置灰，文案「余额支付（余额不足 ¥x.xx）」，并显示「去充值」入口；</li>
     *   <li>若选项当前是选中的却变成不足 → 自动切回微信并提示一次。</li>
     * </ul>
     * 每次 {@code balance} 或 {@code currentPayAmount} 变化后都应调用本方法。
     */
    private void refreshBalanceOption() {
        if (rbBalance == null) return;

        if (!balanceLoaded) {
            rbBalance.setEnabled(false);
            rbBalance.setText("余额支付（余额加载中…）");
            if (tvGoRecharge != null) tvGoRecharge.setVisibility(View.GONE);
            // 未加载完不给选余额：若此时被选中（极端情况）切回微信
            if (rbBalance.isChecked() && rbWechat != null) rbWechat.setChecked(true);
            return;
        }

        boolean enough = balance + EPSILON >= currentPayAmount;
        if (enough) {
            rbBalance.setEnabled(true);
            rbBalance.setText("余额支付（可用 " + money(balance) + "）");
        } else {
            rbBalance.setEnabled(false);
            rbBalance.setText("余额支付（余额不足 " + money(balance) + "）");
        }

        if (tvGoRecharge != null) {
            tvGoRecharge.setVisibility(enough ? View.GONE : View.VISIBLE);
        }

        // 选中的选项变成不可用 → 切回微信，避免用户点「确认支付」被后端拒绝
        if (!enough && rbBalance.isChecked() && rbWechat != null) {
            rbWechat.setChecked(true);
            if (!balanceFallbackToastShown) {
                Toast.makeText(this, "余额不足，已为你切换为微信支付，可先去充值", Toast.LENGTH_LONG).show();
                balanceFallbackToastShown = true;
            }
        }
    }

    /** 当前选中的支付方式 → 后端枚举名。 */
    private String currentMethod() {
        int checked = rgPayMethod == null ? View.NO_ID : rgPayMethod.getCheckedRadioButtonId();
        if (checked == R.id.rb_alipay) return "ALIPAY";
        if (checked == R.id.rb_bank_card) return "BANK";
        if (checked == R.id.rb_balance) return "BALANCE";
        return "WECHAT";
    }

    /* ==================== 下单模式 ==================== */

    /** 下单模式：显示明细/券/收货三块，带出默认地址，试算优惠，拉可用券。 */
    private void setupCheckoutMode() {
        llReceipt.setVisibility(View.VISIBLE);
        llCouponPicker.setVisibility(View.VISIBLE);
        llReceiver.setVisibility(View.VISIBLE);

        tvGoodsTotal.setText(money(localTotal));
        tvAmount.setText(money(localTotal));
        tvOrderNo.setText("共 " + cartItemIds.length + " 项商品，确认支付后生成订单");
        tvTip.setText("确认支付后将立即生成订单并完成支付。");
        btnConfirmPay.setText("确认下单并支付");
        btnConfirmPay.setEnabled(true);

        currentPayAmount = localTotal;
        refreshBalanceOption();

        prefillDefaultAddress();
        loadAvailableCoupons();
    }

    /**
     * 带出默认收货地址。
     * <p>
     * 这是「不用每次手填手机号和地址」的关键：用户在「我的 → 默认地址」设一次，
     * 之后结算自动填好，仍可当场改（改了只影响这一单，不会动默认地址）。
     */
    private void prefillDefaultAddress() {
        addressRepository.getDefault(new ApiCallback<AddressDTO>() {
            @Override
            public void onSuccess(AddressDTO data) {
                if (isFinishing() || isDestroyed() || data == null) return;
                if (!TextUtils.isEmpty(data.receiverPhone)) {
                    etPhone.setText(data.receiverPhone);
                }
                if (!TextUtils.isEmpty(data.fullAddress)) {
                    etAddress.setText(data.fullAddress);
                }
                if (!TextUtils.isEmpty(data.receiverName)) {
                    receiverName = data.receiverName;
                }
                tvDefaultAddressTag.setVisibility(View.VISIBLE);
            }

            @Override
            public void onError(int code, String message) {
                // 没有默认地址或未登录：静默，用户自己填即可，不用打扰
            }
        });
    }

    /** 拉取本单可用券，并默认选中优惠最大的那张。 */
    private void loadAvailableCoupons() {
        couponRepository.available(localTotal, new ApiCallback<List<CouponDTO>>() {
            @Override
            public void onSuccess(List<CouponDTO> data) {
                if (isFinishing() || isDestroyed()) return;
                availableCoupons.clear();
                if (data != null) availableCoupons.addAll(data);

                // 默认自动选最优券：用户不操作也能享受到最大优惠；
                // 想换或不用，点这一行进选择弹窗即可。
                selectedCouponId = null;
                if (!availableCoupons.isEmpty()) {
                    selectedCouponId = availableCoupons.get(0).id;
                }
                renderCouponChoice();
                refreshPreview();
            }

            @Override
            public void onError(int code, String message) {
                // 券拉取失败不影响支付主流程，静默降级为「暂无可用券」
                renderCouponChoice();
                refreshPreview();
            }
        });
    }

    /** 重新向服务端试算并刷新明细（换券后立刻调用）。 */
    private void refreshPreview() {
        couponRepository.preview(localTotal, selectedCouponId, new ApiCallback<CheckoutPreviewDTO>() {
            @Override
            public void onSuccess(CheckoutPreviewDTO p) {
                if (isFinishing() || isDestroyed() || p == null) return;
                renderPreview(p);
            }

            @Override
            public void onError(int code, String message) {
                // 试算失败就退回「本地合计」，至少不显示错误金额
                tvAmount.setText(money(localTotal));
                currentPayAmount = localTotal;
                refreshBalanceOption();
            }
        });
    }

    /** 把试算结果渲染到界面上。 */
    private void renderPreview(CheckoutPreviewDTO p) {
        tvAmount.setText(money(p.payAmount));
        tvGoodsTotal.setText(money(p.totalAmount));

        // 应付金额变了 → 同步余额足够性判断
        currentPayAmount = p.payAmount;
        refreshBalanceOption();

        // 活动优惠：只有真的减到钱才显示这一行，否则隐藏（避免「优惠 ¥0」的噪音）
        if (p.promotionDiscount > 0) {
            rowPromoDiscount.setVisibility(View.VISIBLE);
            tvPromoLabel.setText(TextUtils.isEmpty(p.promotionName)
                    ? "活动优惠" : "活动优惠（" + p.promotionName + "）");
            tvPromoDiscount.setText("-" + money(p.promotionDiscount));
        } else {
            rowPromoDiscount.setVisibility(View.GONE);
        }

        if (p.couponDiscount > 0) {
            rowCouponDiscount.setVisibility(View.VISIBLE);
            tvCouponLabel.setText(TextUtils.isEmpty(p.couponName)
                    ? "优惠券" : "优惠券（" + p.couponName + "）");
            tvCouponDiscount.setText("-" + money(p.couponDiscount));
        } else {
            rowCouponDiscount.setVisibility(View.GONE);
        }

        tvTotalDiscount.setText("-" + money(p.discountAmount));

        // 未达门槛提示（如「再买 ¥50.5 可享满100减10」）
        if (!TextUtils.isEmpty(p.hint)) {
            tvPromoHint.setVisibility(View.VISIBLE);
            tvPromoHint.setText(p.hint);
        } else {
            tvPromoHint.setVisibility(View.GONE);
        }
    }

    private void renderCouponChoice() {
        if (availableCoupons.isEmpty()) {
            tvCouponChoice.setText("暂无可用券");
            return;
        }
        CouponDTO chosen = findCoupon(selectedCouponId);
        if (chosen == null) {
            tvCouponChoice.setText("不使用优惠券（" + availableCoupons.size() + " 张可用）");
        } else {
            tvCouponChoice.setText(chosen.ruleText);
        }
    }

    private CouponDTO findCoupon(Long id) {
        if (id == null) return null;
        for (CouponDTO c : availableCoupons) {
            if (c.id == id) return c;
        }
        return null;
    }

    /** 选券弹窗：第一项是「不使用」，其余按优惠额从高到低。 */
    private void showCouponPicker() {
        if (availableCoupons.isEmpty()) {
            Toast.makeText(this, "当前订单没有可用的优惠券", Toast.LENGTH_SHORT).show();
            return;
        }
        List<String> labels = new ArrayList<>();
        labels.add("不使用优惠券");
        for (CouponDTO c : availableCoupons) {
            String rule = TextUtils.isEmpty(c.ruleText) ? "优惠券" : c.ruleText;
            labels.add(rule + "（" + c.name + "）");
        }

        new AlertDialog.Builder(this)
                .setTitle("选择优惠券")
                .setItems(labels.toArray(new String[0]), (dialog, which) -> {
                    selectedCouponId = which == 0 ? null : availableCoupons.get(which - 1).id;
                    renderCouponChoice();
                    refreshPreview();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /* ==================== 支付模式 ==================== */

    /** 拉订单详情：金额与当前状态都以服务端为准。 */
    private void loadOrder() {
        btnConfirmPay.setEnabled(false);
        orderRepository.getOrderDetail(orderId, new ApiCallback<OrderBrief>() {
            @Override
            public void onSuccess(OrderBrief data) {
                if (data == null) {
                    Toast.makeText(PayActivity.this, "订单不存在", Toast.LENGTH_SHORT).show();
                    finish();
                    return;
                }
                tvAmount.setText(money(data.getPayAmount()));
                tvOrderNo.setText("订单号 " + (data.getOrderNo() == null ? "" : data.getOrderNo()));

                // 应付金额变了 → 同步余额足够性判断
                currentPayAmount = data.getPayAmount();
                refreshBalanceOption();

                // 已经是待付款之外的状态 → 没必要再支付（例如从旧列表点进来）
                if (data.getStatus() != null && !data.getStatus().isEmpty()
                        && !OrderStatus.STATUS_PENDING_PAYMENT.equals(data.getStatus())) {
                    paid = true;
                    tvTip.setText("该订单当前状态：" + data.getStatus() + "，无需重复支付。");
                    btnConfirmPay.setEnabled(false);
                    btnConfirmPay.setText("无需支付");
                    Toast.makeText(PayActivity.this,
                            "该订单已支付（" + data.getStatus() + "）", Toast.LENGTH_SHORT).show();
                    return;
                }
                btnConfirmPay.setEnabled(true);
                if (data.getDiscountAmount() > 0) {
                    tvTip.setText("本单已优惠 " + money(data.getDiscountAmount())
                            + "，支付完成后订单进入「待发货」。");
                }
            }

            @Override
            public void onError(int code, String message) {
                Toast.makeText(PayActivity.this,
                        message == null ? ("加载订单失败 HTTP " + code) : message,
                        Toast.LENGTH_SHORT).show();
                finish();
            }
        });
    }

    /* ==================== 确认支付 ==================== */

    /** 用户确认支付：这一步之后订单才算完成。 */
    private void confirmPay() {
        // 客户端预校验：选中余额但余额不足就别发请求（服务端仍会二次校验，这里只为体验）
        if ("BALANCE".equals(currentMethod())) {
            if (!balanceLoaded || balance + EPSILON < currentPayAmount) {
                Toast.makeText(this, "余额不足，请先充值或更换支付方式", Toast.LENGTH_LONG).show();
                return;
            }
        }

        if (checkoutMode) {
            // 订单已创建（上一次支付失败）→ 只补支付，绝不再下一单
            if (createdOrderId > 0) {
                doPay(createdOrderId);
                return;
            }
            if (!validateReceiver()) return;
            doCreateOrderThenPay();
        } else {
            doPay(orderId);
        }
    }

    /** 收货信息校验，规则对齐原购物车结算弹窗：手机号 11 位且不以 10/11/12 开头、地址非空。 */
    private boolean validateReceiver() {
        tilPhone.setError(null);
        tilAddress.setError(null);
        String phone = etPhone.getText() == null ? "" : etPhone.getText().toString().trim();
        String address = etAddress.getText() == null ? "" : etAddress.getText().toString().trim();
        boolean ok = true;
        if (TextUtils.isEmpty(phone)) {
            tilPhone.setError("请输入电话号码");
            ok = false;
        } else if (phone.length() != 11) {
            tilPhone.setError("电话号码必须为11位");
            ok = false;
        } else if (phone.startsWith("10") || phone.startsWith("11") || phone.startsWith("12")) {
            tilPhone.setError("电话号码不能以10,11或12开头");
            ok = false;
        }
        if (TextUtils.isEmpty(address)) {
            tilAddress.setError("请输入收货地址");
            ok = false;
        }
        return ok;
    }

    /** 下单模式：先创建订单（带上选用的券），成功后立刻支付。 */
    private void doCreateOrderThenPay() {
        btnConfirmPay.setEnabled(false);
        btnConfirmPay.setText("下单中…");
        tvTip.setText("正在生成订单，请稍候…");

        List<Long> ids = new ArrayList<>();
        for (long id : cartItemIds) ids.add(id);
        String phone = etPhone.getText().toString().trim();
        String address = etAddress.getText().toString().trim();

        orderRepository.createOrder(ids, receiverName, phone, address, selectedCouponId,
                new ApiCallback<OrderBrief>() {
                    @Override
                    public void onSuccess(OrderBrief order) {
                        if (order == null || order.getOrderId() <= 0) {
                            onError(-1, "下单返回异常");
                            return;
                        }
                        // 记下来：即使随后支付失败，重试也只会补支付，不会重复下单
                        createdOrderId = order.getOrderId();
                        tvAmount.setText(money(order.getPayAmount()));
                        tvOrderNo.setText("订单号 " + (order.getOrderNo() == null ? "" : order.getOrderNo()));
                        if (order.getDiscountAmount() > 0) {
                            tvTotalDiscount.setText("-" + money(order.getDiscountAmount()));
                        }
                        // 下单成功后以服务端金额为准，同步余额足够性判断
                        currentPayAmount = order.getPayAmount();
                        refreshBalanceOption();
                        doPay(order.getOrderId());
                    }

                    @Override
                    public void onError(int code, String message) {
                        btnConfirmPay.setEnabled(true);
                        btnConfirmPay.setText("确认下单并支付");
                        tvTip.setText("下单失败，请重试。");
                        Toast.makeText(PayActivity.this,
                                message == null ? ("下单失败 HTTP " + code) : message,
                                Toast.LENGTH_LONG).show();
                        // 券可能刚被别处用掉/过期 → 重新拉券并试算，让界面回到真实状态
                        loadAvailableCoupons();
                    }
                });
    }

    /** 真正调用支付接口（把用户选中的支付方式一起带上）。 */
    private void doPay(long targetOrderId) {
        btnConfirmPay.setEnabled(false);
        btnConfirmPay.setText("支付中…");
        tvTip.setText("正在支付，请稍候…");
        orderRepository.pay(targetOrderId, currentMethod(), new ApiCallback<Boolean>() {
            @Override
            public void onSuccess(Boolean data) {
                paid = true;
                Toast.makeText(PayActivity.this, "支付成功，订单已进入「待发货」", Toast.LENGTH_SHORT).show();
                setResult(RESULT_OK);
                finish();
            }

            @Override
            public void onError(int code, String message) {
                btnConfirmPay.setEnabled(true);
                btnConfirmPay.setText(checkoutMode ? "重试支付" : "确认支付");
                tvTip.setText("支付失败，可重试；订单仍为「待付款」。");
                Toast.makeText(PayActivity.this,
                        message == null ? ("支付失败 HTTP " + code) : message,
                        Toast.LENGTH_LONG).show();
                // 余额不足（业务错误 code=400）→ 再拉一次余额，让选项回到真实状态
                // （可能刚被别的端扣走，或用户还没充值）
                if (code == 400 && message != null && message.contains("余额不足")) {
                    loadWallet();
                }
            }
        });
    }

    private String money(double v) {
        return "¥" + String.format(Locale.getDefault(), "%.2f", v);
    }
}
