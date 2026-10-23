package com.example.myapplication.activity;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.R;
import com.example.myapplication.data.OrderRepository;
import com.example.myapplication.data.RepositoryProvider;
import com.example.myapplication.entity.OrderStatus;
import com.example.myapplication.entity.OrderInfo;
import com.example.myapplication.remote.ApiCallback;
import com.example.myapplication.remote.RetrofitClient;
import com.example.myapplication.remote.dto.WalletDTO;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 我的页面功能统一入口 Activity
 * 处理 8 个功能模块:
 *  - 待发货(0)/待收货(1)/待评价(2)/退款售后(3): 按订单状态展示订单并提供操作
 *    （待付款订单无论在哪个列表里，操作按钮都是「去支付」→ 收银台 PayActivity）
 *  - 开发票(4): 提交发票申请
 *  - 送货到家(5): 展示送货上门服务说明
 *  - 自助收银(6): 展示自助收银说明与扫码入口
 *  - 出示购物卡(7): 展示购物卡二维码占位
 *  - 出示会员卡(8): 展示会员卡二维码占位
 */
public class MineFunctionActivity extends AppCompatActivity {

    public static final String EXTRA_FUNCTION = "extra_function";
    public static final String EXTRA_TITLE = "extra_title";

    // 订单状态
    public static final int FUNC_PENDING_SHIP = 0;
    public static final int FUNC_PENDING_RECEIVE = 1;
    public static final int FUNC_PENDING_REVIEW = 2;
    public static final int FUNC_RETURN = 3;
    public static final int FUNC_INVOICE = 4;
    public static final int FUNC_DELIVERY = 5;
    public static final int FUNC_SELF_CHECKOUT = 6;
    public static final int FUNC_SHOPPING_CARD = 7;
    public static final int FUNC_MEMBER_CARD = 8;

    /** 请求码：唤起收银台补付「待付款」订单，支付成功后回来刷新列表。 */
    private static final int REQ_PAY = 1001;

    /** 退款申请被商家拒绝（与后端 {@code RefundStatus.REJECTED} 对应）。 */
    private static final String REFUND_REJECTED = "REJECTED";

    private Toolbar toolbar;
    private RecyclerView rvOrders;
    private LinearLayout infoContainer;
    private ScrollViewInfo scrollViewInfo;
    private LinearLayout emptyLayout;
    private TextView tvEmptyText;

    private OrderRepository orderRepository;
    private int mUserId;
    private int mFunction;
    private OrderAdapter mAdapter;

    /**
     * 后端下发的订单「平铺行」：一单有几件商品就有几行（见 {@code DtoMapper.toOrderInfoList}）。
     * <p>
     * 它是**数据源**，不直接决定列表行数 —— 列表按 {@link #mGroups} 渲染。
     * 各页面的筛选逻辑（待评价去已评、退款售后去已退款）都在这份平铺列表上做，
     * 因此筛选口径完全不受本次分组改造影响。
     */
    private List<OrderInfo> mOrderList = new ArrayList<>();

    /**
     * 列表的**展示单位**：同一订单号的多件商品合并成一组，一组 = 一张卡片。
     * <p>
     * 由 {@link #rebuildGroups()} 从 {@link #mOrderList} 派生，两者必须同步更新
     * （改完 mOrderList 一定要 rebuildGroups()，否则卡片会与数据脱节）。
     */
    private List<OrderGroup> mGroups = new ArrayList<>();

    /** onCreate 是否已完成首次渲染（onResume 靠它区分「首次进入」与「从别处返回」）。 */
    private boolean mInitialRenderDone;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mine_function);

        mFunction = getIntent().getIntExtra(EXTRA_FUNCTION, 0);
        String title = getIntent().getStringExtra(EXTRA_TITLE);
        if (title == null) title = "功能";

        // 项目铁律：必须早于任何网络调用初始化 Retrofit（本 Activity 会拉订单/余额）
        RetrofitClient.init(getApplicationContext());

        SharedPreferences sp = getSharedPreferences("user", MODE_PRIVATE);
        mUserId = sp.getInt("user_id", -1);

        initViews(title);
        orderRepository = RepositoryProvider.getInstance(this).order();

        renderByFunction(mFunction);
        mInitialRenderDone = true;
    }

    /**
     * 从商品详情页评价完返回时刷新待评价列表。
     * <p>
     * 评价动作发生在商品详情页（{@code startActivity}，不回调结果），所以只能靠 onResume
     * 重新拉一次：已评价的订单会因 {@code reviewed=true} 被过滤掉，列表里的「去评价」提示随之消失。
     * 只对「待评价」这一页这么做 —— 其他功能页（开发票/送货到家等）有用户正在填的表单，重渲染会清空输入。
     */
    @Override
    protected void onResume() {
        super.onResume();
        if (!mInitialRenderDone) return;   // 首次进入由 onCreate 负责，避免重复请求
        if (mFunction == FUNC_PENDING_REVIEW) {
            renderByFunction(mFunction);
        }
    }

    /**
     * 从收银台回来：支付成功后订单已从「待付款」变为「待发货」，
     * 按当前功能页重新拉一次，避免列表还显示旧状态。
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_PAY && resultCode == RESULT_OK) {
            renderByFunction(mFunction);
        }
    }

    private void initViews(String title) {
        toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle(title);
        toolbar.setNavigationOnClickListener(v -> finish());

        rvOrders = findViewById(R.id.rv_orders);
        infoContainer = findViewById(R.id.info_container);
        scrollViewInfo = new ScrollViewInfo(findViewById(R.id.sv_info));
        emptyLayout = findViewById(R.id.empty_layout);
        tvEmptyText = findViewById(R.id.tv_empty_text);

        rvOrders.setLayoutManager(new LinearLayoutManager(this));
        mAdapter = new OrderAdapter();
        rvOrders.setAdapter(mAdapter);
    }

    /** 根据功能类型渲染页面 */
    private void renderByFunction(int function) {
        switch (function) {
            case FUNC_PENDING_SHIP:
                loadOrders(OrderStatus.STATUS_PENDING_SHIP, "待发货");
                break;
            case FUNC_PENDING_RECEIVE:
                loadOrders(OrderStatus.STATUS_PENDING_RECEIVE, "待收货");
                break;
            case FUNC_PENDING_REVIEW:
                loadPendingReviewOrders();
                break;
            case FUNC_RETURN:
                loadAllOrdersForReturn();
                break;
            case FUNC_INVOICE:
                renderInvoiceForm();
                break;
            case FUNC_DELIVERY:
                renderDeliveryInfo();
                break;
            case FUNC_SELF_CHECKOUT:
                renderSelfCheckoutInfo();
                break;
            case FUNC_SHOPPING_CARD:
                renderShoppingCard();
                break;
            case FUNC_MEMBER_CARD:
                renderMemberCard();
                break;
            default:
                Toast.makeText(this, "功能未实现", Toast.LENGTH_SHORT).show();
                finish();
        }
    }

    // ===== 订单相关 =====

    private void loadOrders(String status, String emptyHint) {
        if (mUserId == -1) {
            showEmpty("请先登录");
            return;
        }
        orderRepository.getOrdersByStatus(status, new ApiCallback<List<OrderInfo>>() {
            @Override
            public void onSuccess(List<OrderInfo> data) {
                mOrderList = data != null ? data : new ArrayList<>();
                if (mOrderList.isEmpty()) {
                    showEmpty("暂无" + emptyHint + "订单");
                } else {
                    showOrders();
                    rebuildGroups();
                }
            }

            @Override
            public void onError(int code, String message) {
                showEmpty(message);
            }
        });
    }

    /**
     * 待评价列表。
     * <p>
     * 判定口径 = 订单状态「已完成」<b>且</b>该用户还没评价过订单内的商品。
     * 之所以不能只按状态筛（历史上就是只筛 COMPLETED），是因为用户评价完订单状态不会变，
     * 结果评完再进这个页面还是会看到同一单，点「去评价」又跳去已评过的商品详情页。
     * 后端在 {@code OrderDTO.reviewed} 上给出该标记。
     */
    private void loadPendingReviewOrders() {
        if (mUserId == -1) {
            showEmpty("请先登录");
            return;
        }
        orderRepository.getOrdersByStatus(OrderStatus.STATUS_DONE, new ApiCallback<List<OrderInfo>>() {
            @Override
            public void onSuccess(List<OrderInfo> data) {
                List<OrderInfo> pending = new ArrayList<>();
                if (data != null) {
                    for (OrderInfo o : data) {
                        if (!o.isReviewed()) pending.add(o);
                    }
                }
                mOrderList = pending;
                if (mOrderList.isEmpty()) {
                    showEmpty("暂无待评价订单");
                } else {
                    showOrders();
                    rebuildGroups();
                }
            }

            @Override
            public void onError(int code, String message) {
                showEmpty(message);
            }
        });
    }

    private void loadAllOrdersForReturn() {
        if (mUserId == -1) {
            showEmpty("请先登录");
            return;
        }
        orderRepository.getAllOrders(new ApiCallback<List<OrderInfo>>() {
            @Override
            public void onSuccess(List<OrderInfo> data) {
                // 排除「已退款」「已退货」：钱/货已了结，没有再申请售后的意义。
                // 「退款中」要保留，让用户在这里看得到申请进度（按钮已隐藏，避免重复申请）。
                // 判定口径统一走 OrderStatus.isAfterSaleVisible，与「我的」页角标同源。
                List<OrderInfo> filtered = new ArrayList<>();
                if (data != null) {
                    for (OrderInfo o : data) {
                        if (!OrderStatus.isAfterSaleVisible(o.getStatus())) {
                            continue;
                        }
                        filtered.add(o);
                    }
                }
                mOrderList = filtered;
                if (mOrderList.isEmpty()) {
                    showEmpty("暂无可申请售后的订单");
                } else {
                    showOrders();
                    rebuildGroups();
                }
            }

            @Override
            public void onError(int code, String message) {
                showEmpty(message);
            }
        });
    }

    /**
     * 把组内商品压成一行摘要，供退款弹窗展示。
     * <p>
     * 只列前 2 件、其余折成「等 N 件商品」，避免弹窗被长文案撑满。
     * 件数用**明细行数**（用户视角的「几样东西」），与卡片底部按购买数量累加的「共 N 件」口径不同，
     * 这里刻意如此：退款问的是「退哪些东西」，不是「共几件」。
     */
    private static String describeGoods(OrderGroup group) {
        StringBuilder sb = new StringBuilder();
        int shown = Math.min(group.items.size(), 2);
        for (int i = 0; i < shown; i++) {
            if (i > 0) sb.append("、");
            String title = group.items.get(i).getProduct_title();
            sb.append(title == null || title.trim().isEmpty() ? "商品" : title.trim());
        }
        if (group.items.size() > shown) {
            sb.append(" 等 ").append(group.items.size()).append(" 件商品");
        }
        return sb.toString();
    }

    /**
     * 申请退款弹窗：让用户填写退款原因后提交。
     * <p>
     * 提交成功只是「申请」，订单变成「退款中」，钱要等商家在后台点「同意退款」才会原路退回 ——
     * 所以提示语必须说清「等待商家审核」，不能让用户误以为钱马上到账。
     * <p>
     * 入参是整张卡片（一组）而不是单件商品：退款是**按订单**发起的
     * （{@code POST /api/orders/{id}/refund}，整单一起退），一单多商品时必须把商品列全，
     * 否则用户以为只退这一件。
     */
    private void showRefundDialog(OrderGroup group) {
        float density = getResources().getDisplayMetrics().density;
        int pad = (int) (density * 20);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(pad, pad / 2, pad, 0);

        TextView tvTip = new TextView(this);
        tvTip.setText("订单号：" + group.orderNo
                + "\n商品：" + describeGoods(group)
                + "\n\n提交后需等商家审核同意，退款才会原路退回。");
        tvTip.setTextSize(13f);
        tvTip.setTextColor(getResources().getColor(R.color.rt_text_secondary));
        tvTip.setPadding(0, 0, 0, pad / 2);
        box.addView(tvTip);

        EditText etReason = new EditText(this);
        etReason.setHint("请填写退款原因（选填）");
        etReason.setMinLines(2);
        etReason.setBackground(getResources().getDrawable(R.drawable.bg_rounded_corner_b));
        etReason.setPadding(pad, pad, pad, pad);
        box.addView(etReason);

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("申请退款")
                .setView(box)
                .setPositiveButton("提交申请", (d, w) ->
                        orderRepository.applyRefund(group.first().getOrder_id(),
                                etReason.getText().toString().trim(),
                                new ApiCallback<Boolean>() {
                                    @Override
                                    public void onSuccess(Boolean data) {
                                        Toast.makeText(MineFunctionActivity.this,
                                                "退款申请已提交，请等待商家审核", Toast.LENGTH_LONG).show();
                                        // 订单状态已变成「退款中」，重新拉一次让状态与按钮同步
                                        loadAllOrdersForReturn();
                                    }

                                    @Override
                                    public void onError(int code, String message) {
                                        Toast.makeText(MineFunctionActivity.this, message, Toast.LENGTH_SHORT).show();
                                    }
                                }))
                .setNegativeButton("取消", null)
                .show();
    }

    private void showOrders() {
        rvOrders.setVisibility(View.VISIBLE);
        scrollViewInfo.setVisibility(View.GONE);
        emptyLayout.setVisibility(View.GONE);
    }

    private void showEmpty(String text) {
        rvOrders.setVisibility(View.GONE);
        scrollViewInfo.setVisibility(View.GONE);
        emptyLayout.setVisibility(View.VISIBLE);
        tvEmptyText.setText(text);
    }

    private void showInfo() {
        rvOrders.setVisibility(View.GONE);
        scrollViewInfo.setVisibility(View.VISIBLE);
        emptyLayout.setVisibility(View.GONE);
    }

    // ===== 开发票 =====
    private void renderInvoiceForm() {
        showInfo();
        infoContainer.removeAllViews();
        infoContainer.addView(buildTitle("电子发票申请"));
        infoContainer.addView(buildDescription("填写以下信息后点击申请,系统将为您开具电子发票,1-3个工作日内发送至邮箱。"));

        // 发票类型选择
        infoContainer.addView(buildSectionLabel("发票类型"));
        RadioGroup rgType = new RadioGroup(this);
        rgType.setOrientation(RadioGroup.HORIZONTAL);
        RadioButton rbNormal = buildRadio("普通发票(电子)");
        RadioButton rbSpecial = buildRadio("增值税专用发票");
        rgType.addView(rbNormal);
        rgType.addView(rbSpecial);
        rgType.check(rbNormal.getId());
        infoContainer.addView(rgType);

        // 抬头类型
        infoContainer.addView(buildSectionLabel("抬头类型"));
        RadioGroup rgTitle = new RadioGroup(this);
        rgTitle.setOrientation(RadioGroup.HORIZONTAL);
        RadioButton rbPerson = buildRadio("个人");
        RadioButton rbCompany = buildRadio("企业");
        rgTitle.addView(rbPerson);
        rgTitle.addView(rbCompany);
        rgTitle.check(rbPerson.getId());
        infoContainer.addView(rgTitle);

        // 抬头名称
        EditText etTitle = buildEditText("请输入发票抬头(如:个人姓名/企业全称)");
        infoContainer.addView(etTitle);

        // 税号(企业必填)
        EditText etTaxNo = buildEditText("请输入纳税人识别号(企业必填)");
        etTaxNo.setInputType(InputType.TYPE_CLASS_TEXT);
        infoContainer.addView(etTaxNo);

        // 邮箱
        EditText etEmail = buildEditText("请输入接收发票的邮箱");
        etEmail.setInputType(InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        infoContainer.addView(etEmail);

        // 发票内容明细(模拟: 取最近一笔订单商品)
        infoContainer.addView(buildSectionLabel("发票内容明细"));
        TextView tvDetail = buildDescription("加载中...");
        infoContainer.addView(tvDetail);
        orderRepository.getAllOrders(new ApiCallback<List<OrderInfo>>() {
            @Override
            public void onSuccess(List<OrderInfo> recent) {
                String detail = "暂无消费记录";
                if (recent != null && !recent.isEmpty()) {
                    OrderInfo o = recent.get(0);
                    detail = o.getProduct_title() + "  x" + o.getProduct_count()
                            + "     ¥" + o.getProduct_price();
                }
                tvDetail.setText(detail);
            }

            @Override
            public void onError(int code, String message) {
                tvDetail.setText("暂无消费记录");
            }
        });

        Button btn = buildActionButton("提交申请");
        btn.setOnClickListener(v -> {
            String titleStr = etTitle.getText().toString().trim();
            String email = etEmail.getText().toString().trim();
            String taxNo = etTaxNo.getText().toString().trim();
            boolean isCompany = rbCompany.isChecked();

            if (titleStr.isEmpty()) {
                Toast.makeText(this, "请输入发票抬头", Toast.LENGTH_SHORT).show();
                return;
            }
            if (email.isEmpty()) {
                Toast.makeText(this, "请输入接收邮箱", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                Toast.makeText(this, "邮箱格式不正确", Toast.LENGTH_SHORT).show();
                return;
            }
            if (isCompany && taxNo.isEmpty()) {
                Toast.makeText(this, "企业发票需填写税号", Toast.LENGTH_SHORT).show();
                return;
            }

            String type = rbSpecial.isChecked() ? "增值税专用发票" : "普通发票(电子)";
            new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("确认发票信息")
                    .setMessage("发票类型: " + type + "\n抬头: " + titleStr +
                            (isCompany ? "\n税号: " + taxNo : "") + "\n邮箱: " + email)
                    .setPositiveButton("确认提交", (d, w) -> {
                        Toast.makeText(this, "发票申请已提交,将发送至 " + email, Toast.LENGTH_LONG).show();
                        finish();
                    })
                    .setNegativeButton("取消", null)
                    .show();
        });
        infoContainer.addView(btn);
    }

    // ===== 送货到家 =====
    private void renderDeliveryInfo() {
        showInfo();
        infoContainer.removeAllViews();
        infoContainer.addView(buildTitle("送货到家"));
        infoContainer.addView(buildDescription("填写配送信息,选择配送时段,最快当日送达。"));

        // 收货人姓名
        EditText etName = buildEditText("收货人姓名");
        infoContainer.addView(etName);

        // 联系电话
        EditText etPhone = buildEditText("联系电话");
        etPhone.setInputType(InputType.TYPE_CLASS_PHONE);
        infoContainer.addView(etPhone);

        // 收货地址
        EditText etAddr = buildEditText("详细收货地址");
        infoContainer.addView(etAddr);

        // 配送时间选择
        infoContainer.addView(buildSectionLabel("配送时间"));
        String[] timeSlots = {"今日 14:00-16:00", "今日 16:00-18:00", "今日 18:00-20:00",
                "明日 09:00-11:00", "明日 14:00-16:00"};
        final int[] selectedSlot = {0};
        RadioGroup rgTime = new RadioGroup(this);
        rgTime.setOrientation(RadioGroup.VERTICAL);
        RadioButton firstSlotRb = null;
        for (int i = 0; i < timeSlots.length; i++) {
            RadioButton rb = buildRadio(timeSlots[i]);
            final int idx = i;
            rb.setOnClickListener(v -> selectedSlot[0] = idx);
            rgTime.addView(rb);
            if (i == 0) firstSlotRb = rb;
        }
        if (firstSlotRb != null) rgTime.check(firstSlotRb.getId());
        infoContainer.addView(rgTime);

        // 配送费用说明
        infoContainer.addView(buildSectionLabel("配送费用"));
        infoContainer.addView(buildInfoRow("基础规则", "满35元免基础配送费(10kg内)"));
        infoContainer.addView(buildInfoRow("超重加收", "每超出1kg加收2元"));
        infoContainer.addView(buildInfoRow("服务范围", "门店周边5公里"));

        Button btn = buildActionButton("确认下单配送");
        btn.setOnClickListener(v -> {
            String name = etName.getText().toString().trim();
            String phone = etPhone.getText().toString().trim();
            String addr = etAddr.getText().toString().trim();

            if (name.isEmpty()) { Toast.makeText(this, "请输入收货人姓名", Toast.LENGTH_SHORT).show(); return; }
            if (phone.isEmpty() || !phone.matches("^1[3-9]\\d{9}$")) {
                Toast.makeText(this, "请输入有效的手机号", Toast.LENGTH_SHORT).show(); return; }
            if (addr.isEmpty()) { Toast.makeText(this, "请输入收货地址", Toast.LENGTH_SHORT).show(); return; }

            new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("确认配送订单")
                    .setMessage("收货人: " + name + "\n电话: " + phone + "\n地址: " + addr
                            + "\n配送时段: " + timeSlots[selectedSlot[0]])
                    .setPositiveButton("确认", (d, w) -> {
                        Toast.makeText(this, "下单成功,预计" + timeSlots[selectedSlot[0]] + "送达", Toast.LENGTH_LONG).show();
                        finish();
                    })
                    .setNegativeButton("取消", null)
                    .show();
        });
        infoContainer.addView(btn);
    }

    // ===== 自助收银 =====
    private void renderSelfCheckoutInfo() {
        showInfo();
        infoContainer.removeAllViews();
        infoContainer.addView(buildTitle("自助收银"));
        infoContainer.addView(buildDescription("扫码或手动录入商品,选择支付方式后一键结算,生成电子小票。"));

        // 已扫商品列表
        infoContainer.addView(buildSectionLabel("已选商品"));
        List<OrderInfo> cartList = new ArrayList<>();
        LinearLayout cartLayout = new LinearLayout(this);
        cartLayout.setOrientation(LinearLayout.VERTICAL);
        infoContainer.addView(cartLayout);

        // 手动添加商品
        EditText etItem = buildEditText("输入商品名称");
        infoContainer.addView(etItem);
        EditText etPrice = buildEditText("输入单价(元)");
        etPrice.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        infoContainer.addView(etPrice);

        Button btnAdd = buildActionButton("添加商品");
        btnAdd.setOnClickListener(v -> {
            String name = etItem.getText().toString().trim();
            String priceStr = etPrice.getText().toString().trim();
            if (name.isEmpty() || priceStr.isEmpty()) {
                Toast.makeText(this, "请输入商品名称和价格", Toast.LENGTH_SHORT).show();
                return;
            }
            try {
                double price = Double.parseDouble(priceStr);
                OrderInfo item = new OrderInfo();
                item.setProduct_title(name);
                item.setProduct_price(priceStr);
                item.setProduct_count(1);
                item.setPrice(price);
                cartList.add(item);
                etItem.setText("");
                etPrice.setText("");
                refreshCartDisplay(cartLayout, cartList);
            } catch (NumberFormatException e) {
                Toast.makeText(this, "价格格式不正确", Toast.LENGTH_SHORT).show();
            }
        });
        infoContainer.addView(btnAdd);

        // 支付方式选择
        infoContainer.addView(buildSectionLabel("支付方式"));
        String[] payMethods = {"微信支付", "支付宝", "银行卡", "购物卡"};
        final int[] selectedPay = {0};
        RadioGroup rgPay = new RadioGroup(this);
        rgPay.setOrientation(RadioGroup.HORIZONTAL);
        RadioButton firstPayRb = null;
        for (int i = 0; i < payMethods.length; i++) {
            RadioButton rb = buildRadio(payMethods[i]);
            final int idx = i;
            rb.setOnClickListener(v -> selectedPay[0] = idx);
            rgPay.addView(rb);
            if (i == 0) firstPayRb = rb;
        }
        if (firstPayRb != null) rgPay.check(firstPayRb.getId());
        infoContainer.addView(rgPay);

        Button btnCheckout = buildActionButton("确认结算");
        btnCheckout.setOnClickListener(v -> {
            if (cartList.isEmpty()) {
                Toast.makeText(this, "请先添加商品", Toast.LENGTH_SHORT).show();
                return;
            }
            double total = 0;
            StringBuilder sb = new StringBuilder();
            for (OrderInfo o : cartList) {
                total += o.getTotalPrice();
                sb.append(o.getProduct_title()).append(" x").append(o.getProduct_count())
                        .append("  ¥").append(o.getProduct_price()).append("\n");
            }
            sb.append("合计: ¥").append(String.format(Locale.getDefault(), "%.2f", total));
            new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("电子小票")
                    .setMessage(sb.toString() + "\n支付方式: " + payMethods[selectedPay[0]]
                            + "\n\n感谢您的惠顾,欢迎再次光临!")
                    .setPositiveButton("完成", (d, w) -> finish())
                    .show();
        });
        infoContainer.addView(btnCheckout);
    }

    /** 刷新自助收银商品列表显示 */
    private void refreshCartDisplay(LinearLayout layout, List<OrderInfo> list) {
        layout.removeAllViews();
        double total = 0;
        for (OrderInfo o : list) {
            total += o.getTotalPrice();
            layout.addView(buildInfoRow(o.getProduct_title(),
                    "x" + o.getProduct_count() + "  ¥" + o.getProduct_price()));
        }
        TextView tvTotal = new TextView(this);
        tvTotal.setText("合计: ¥" + String.format(Locale.getDefault(), "%.2f", total));
        tvTotal.setTextSize(16f);
        tvTotal.setTextColor(getResources().getColor(R.color.rt_primary));
        tvTotal.setPadding(0, 12, 0, 12);
        layout.addView(tvTotal);
    }

    // ===== 出示购物卡 =====
    /**
     * 「我的购物卡」页。
     * <p>
     * 余额改为读<b>后端真实账户余额</b>（{@code GET /api/wallet}，与「我的余额」页、收银台余额支付同一账户）；
     * 原先这里读本地 {@code SharedPreferences("shopping_card")} 的假余额（默认 ¥1000），
     * 并提供一个「模拟购物卡支付」（输入金额直接改本地数字，不产生订单、不调后端）——
     * 假支付会造成「扣了余额但没有交易」的账实不符，已删除，改为「去充值」入口。
     */
    private void renderShoppingCard() {
        showInfo();
        infoContainer.removeAllViews();
        infoContainer.addView(buildTitle("我的购物卡"));
        infoContainer.addView(buildDescription("出示下方二维码至收银台，可使用账户余额支付。余额与「我的余额」为同一账户。"));

        ImageView ivQr = new ImageView(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(400, 400);
        lp.gravity = android.view.Gravity.CENTER_HORIZONTAL;
        lp.topMargin = 40;
        ivQr.setLayoutParams(lp);
        ivQr.setImageResource(R.drawable.gouwucheanniu);
        ivQr.setScaleType(ImageView.ScaleType.FIT_CENTER);
        ivQr.setBackground(getResources().getDrawable(R.drawable.bg_rounded_corner_b));
        ivQr.setPadding(40, 40, 40, 40);
        infoContainer.addView(ivQr);

        infoContainer.addView(buildInfoRow("卡号", "RT-MART-" + String.format(Locale.getDefault(), "%08d", mUserId > 0 ? mUserId : 0)));
        View rowBalance = buildInfoRow("余额", "¥--");
        TextView tvCardBalance = (TextView) ((LinearLayout) rowBalance).getChildAt(1);
        infoContainer.addView(rowBalance);
        infoContainer.addView(buildInfoRow("有效期", "长期有效"));
        infoContainer.addView(buildInfoRow("状态", "正常"));

        // 余额读后端真实值（异步回填；失败显示 --）
        loadCardBalance(tvCardBalance);

        // 用「去充值」替代原来的「模拟购物卡支付」：充值后余额可供收银台「余额支付」使用
        Button btnRecharge = buildActionButton("去充值");
        btnRecharge.setOnClickListener(v -> WalletActivity.start(this));
        infoContainer.addView(btnRecharge);
    }

    /** 拉取后端账户余额并回填到「余额」行。 */
    private void loadCardBalance(TextView tv) {
        if (tv == null) return;
        RetrofitClient.init(getApplicationContext());
        RepositoryProvider.getInstance(this).wallet().getWallet(null, new ApiCallback<WalletDTO>() {
            @Override
            public void onSuccess(WalletDTO data) {
                if (isFinishing() || isDestroyed()) return;
                double bal = data == null ? 0.0 : data.balance;
                tv.setText("¥" + String.format(Locale.getDefault(), "%.2f", bal));
            }

            @Override
            public void onError(int code, String message) {
                tv.setText("¥--");
            }
        });
    }

    // ===== 出示会员卡 =====
    private void renderMemberCard() {
        showInfo();
        infoContainer.removeAllViews();
        infoContainer.addView(buildTitle("我的会员卡"));
        infoContainer.addView(buildDescription("大润发尊享会员,出示会员二维码享受会员专属优惠。"));

        ImageView ivQr = new ImageView(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(400, 400);
        lp.gravity = android.view.Gravity.CENTER_HORIZONTAL;
        lp.topMargin = 40;
        ivQr.setLayoutParams(lp);
        ivQr.setImageResource(R.drawable.ic_member_card);
        ivQr.setScaleType(ImageView.ScaleType.FIT_CENTER);
        ivQr.setBackground(getResources().getDrawable(R.drawable.bg_rounded_corner_b));
        ivQr.setPadding(40, 40, 40, 40);
        infoContainer.addView(ivQr);

        infoContainer.addView(buildInfoRow("会员等级", "金卡会员"));
        infoContainer.addView(buildInfoRow("会员号", "VIP-" + String.format(Locale.getDefault(), "%06d", mUserId > 0 ? mUserId : 0)));

        // 积分查询(用 SP 持久化)
        SharedPreferences memSp = getSharedPreferences("member", MODE_PRIVATE);
        int points = memSp.getInt("points_" + mUserId, 1888);
        infoContainer.addView(buildInfoRow("当前积分", points + " 分"));

        Button btnQuery = buildActionButton("查询积分明细");
        btnQuery.setOnClickListener(v -> new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("积分明细")
                .setMessage("当前积分: " + points + " 分\n\n积分规则:\n• 消费1元累计1积分\n• 生日当月消费双倍积分\n• 100积分可抵1元")
                .setPositiveButton("知道了", null)
                .show());
        infoContainer.addView(btnQuery);

        // 会员权益
        infoContainer.addView(buildSectionLabel("会员权益"));
        infoContainer.addView(buildInfoRow("购物折扣", "全场商品9.5折"));
        infoContainer.addView(buildInfoRow("积分特权", "消费1元=1积分,100积分抵1元"));
        infoContainer.addView(buildInfoRow("生日礼遇", "生日当月双倍积分+专属优惠券"));
        infoContainer.addView(buildInfoRow("优先配送", "送货到家优先调度"));
        infoContainer.addView(buildInfoRow("专属客服", "7×24小时VIP客服专线"));
    }

    // ===== UI 工具方法 =====
    private TextView buildTitle(String text) {
        TextView tv = new TextView(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        tv.setLayoutParams(lp);
        tv.setText(text);
        tv.setTextSize(20f);
        tv.setTextColor(getResources().getColor(R.color.rt_text_primary));
        tv.setPadding(0, 20, 0, 20);
        return tv;
    }

    private TextView buildDescription(String text) {
        TextView tv = new TextView(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = 24;
        tv.setLayoutParams(lp);
        tv.setText(text);
        tv.setTextSize(14f);
        tv.setTextColor(getResources().getColor(R.color.rt_text_secondary));
        return tv;
    }

    private View buildInfoRow(String key, String value) {
        LinearLayout row = new LinearLayout(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = 16;
        row.setLayoutParams(lp);
        row.setOrientation(LinearLayout.HORIZONTAL);

        TextView tvKey = new TextView(this);
        LinearLayout.LayoutParams klp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        tvKey.setLayoutParams(klp);
        tvKey.setText(key);
        tvKey.setTextSize(14f);
        tvKey.setTextColor(getResources().getColor(R.color.rt_text_secondary));

        TextView tvVal = new TextView(this);
        LinearLayout.LayoutParams vlp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 2);
        tvVal.setLayoutParams(vlp);
        tvVal.setText(value);
        tvVal.setTextSize(14f);
        tvVal.setTextColor(getResources().getColor(R.color.rt_text_primary));

        row.addView(tvKey);
        row.addView(tvVal);
        return row;
    }

    /** 小节标题(加粗、上边距) */
    private TextView buildSectionLabel(String text) {
        TextView tv = new TextView(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = 20;
        lp.bottomMargin = 8;
        tv.setLayoutParams(lp);
        tv.setText(text);
        tv.setTextSize(15f);
        tv.setTextColor(getResources().getColor(R.color.rt_text_primary));
        tv.setTypeface(null, android.graphics.Typeface.BOLD);
        return tv;
    }

    /** 单选按钮(不在此处设置 checked,需在加入 RadioGroup 后通过 rg.check(id) 设置,否则互斥失效) */
    private RadioButton buildRadio(String text) {
        RadioButton rb = new RadioButton(this);
        rb.setId(View.generateViewId());
        rb.setText(text);
        rb.setTextSize(14f);
        return rb;
    }

    private EditText buildEditText(String hint) {
        EditText et = new EditText(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = 16;
        et.setLayoutParams(lp);
        et.setHint(hint);
        et.setBackground(getResources().getDrawable(R.drawable.bg_rounded_corner_b));
        et.setPadding(20, 20, 20, 20);
        return et;
    }

    private Button buildActionButton(String text) {
        Button btn = new Button(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = 32;
        btn.setLayoutParams(lp);
        btn.setText(text);
        btn.setTextColor(getResources().getColor(R.color.rt_text_on_primary));
        btn.setBackground(getResources().getDrawable(R.drawable.bg_pay));
        return btn;
    }

    /** 用于切换 ScrollView 可见性的简单包装 */
    private static class ScrollViewInfo {
        final View scrollView;
        ScrollViewInfo(View v) { scrollView = v; }
        void setVisibility(int v) { scrollView.setVisibility(v); }
    }

    // ===== 订单分组（一个订单号 = 一张卡片） =====

    /**
     * 一个订单的展示单元：订单号相同的若干「平铺行」归为一组。
     * <p>
     * 组内共享订单级信息（订单号 / 状态 / 下单时间 / 物流 / 退款），这些字段一单一份，
     * 由 {@code DtoMapper.applyOrderFields} 灌进了该单的每一行；商品明细则各自独立。
     */
    private static class OrderGroup {
        /** 后端真实订单号，同时是分组键（展示用，已含老数据兜底）。 */
        final String orderNo;
        final List<OrderInfo> items = new ArrayList<>();

        OrderGroup(String orderNo) {
            this.orderNo = orderNo;
        }

        /** 组内首件：订单级字段的取值来源。 */
        OrderInfo first() {
            return items.get(0);
        }

        /** 本单商品总件数（按购买数量累加，不是明细行数）。 */
        int totalQuantity() {
            int n = 0;
            for (OrderInfo o : items) {
                n += Math.max(o.getProduct_count(), 0);
            }
            return n;
        }
    }

    /**
     * 按订单号把平铺行折叠成卡片分组。
     * <p>
     * 用 {@link LinkedHashMap} 保持后端返回的先后顺序（订单列表按时间倒序），
     * 组内保持明细原有顺序 —— 同一订单的多行在 {@code DtoMapper.toOrderInfoList} 里
     * 本来就是连续产生的，所以「首次出现」即为该单的正确位置。
     * <p>
     * 分组键取 {@link OrderInfo#getDisplayOrderNo()}：订单号缺失时它会回退成 {@code #{order_id}}，
     * 仍是一单一键，不会把 orderNo 为空的老数据错并成一组。
     * <p>
     * 凡是改过 {@link #mOrderList} 的地方都要调本方法，它同时负责通知适配器刷新 ——
     * 别在外部再单独调一次 notifyDataSetChanged()。
     */
    private void rebuildGroups() {
        mGroups = new ArrayList<>();
        Map<String, OrderGroup> byOrderNo = new LinkedHashMap<>();
        for (OrderInfo o : mOrderList) {
            String key = o.getDisplayOrderNo();
            OrderGroup group = byOrderNo.get(key);
            if (group == null) {
                group = new OrderGroup(key);
                byOrderNo.put(key, group);
                mGroups.add(group);
            }
            group.items.add(o);
        }
        if (mAdapter != null) {
            mAdapter.notifyDataSetChanged();
        }
    }

    // ===== 订单列表适配器 =====
    private class OrderAdapter extends RecyclerView.Adapter<OrderAdapter.VH> {

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_mine_order, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            // 列表的每一行 = 一个订单（一组），不再是「一件商品一行」
            OrderGroup group = mGroups.get(position);
            // 订单级字段一单一份，取组内首件即可（DtoMapper.applyOrderFields 把同一份灌进了该单每一行）
            OrderInfo head = group.first();

            // 订单号必须显示后端真实订单号（如 DR20260915203028793）。
            // order_id 只是数据库自增主键，用户拿它问客服 / 查物流都对不上号。
            holder.tvOrderId.setText("订单号: " + group.orderNo);
            holder.tvOrderStatus.setText(head.getStatus() == null ? "待发货" : head.getStatus());
            // 纯展示：给状态文字套上语义色 chip（不改动上面的取值来源，也不影响状态判断分支）
            applyStatusChip(holder.tvOrderStatus, head.getStatus());

            // 商品区：整单商品都渲染在同一个卡片里 —— 这就是「同一订单号合并展示」的落点。
            // 必须先清空：ViewHolder 会被复用，不清干净会把上一单的商品行串到这一单上。
            holder.llGoods.removeAllViews();
            LayoutInflater goodsInflater = LayoutInflater.from(holder.itemView.getContext());
            for (int i = 0; i < group.items.size(); i++) {
                // 相邻两件之间垫一条分隔线，让「同一单的多件」与「多张卡片」在视觉上区分开
                if (i > 0) {
                    holder.llGoods.addView(goodsInflater.inflate(
                            R.layout.item_order_goods_divider, holder.llGoods, false));
                }
                View goodsRow = goodsInflater.inflate(
                        R.layout.item_order_goods_row, holder.llGoods, false);
                bindGoodsRow(goodsRow, group.items.get(i));
                holder.llGoods.addView(goodsRow);
            }

            // 时间格式化
            String timeStr = head.getOrder_time();
            try {
                long ts = Long.parseLong(timeStr);
                timeStr = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date(ts));
            } catch (Exception ignored) {
            }
            // 一单多商品会让卡片明显变高，把「共 N 件」标出来解释这个高度，用户才知道下面列了几件
            holder.tvOrderTime.setText(timeStr + " · 共 " + group.totalQuantity() + " 件");

            // 运单号：只有发过货的订单才有值（后台发货时填写），没值就把整行收起来
            if (head.hasShipment()) {
                String company = head.getShipmentCompany() == null ? "" : head.getShipmentCompany().trim();
                String no = head.getTrackingNo() == null ? "" : head.getTrackingNo().trim();
                if (no.isEmpty()) no = "-";
                holder.tvTrackingNo.setText(company.isEmpty() ? "运单号：" + no : company + "　运单号：" + no);
                holder.tvTrackingNo.setVisibility(View.VISIBLE);
            } else {
                holder.tvTrackingNo.setVisibility(View.GONE);
            }

            // 退款被拒时给出理由；「退款中 / 已退款」状态列已经写清楚了，这里不重复
            if (REFUND_REJECTED.equals(head.getRefundStatus())) {
                String why = head.getRefundRejectReason();
                holder.tvRefundHint.setText(
                        (why == null || why.trim().isEmpty()) ? "退款申请未通过商家审核" : "退款未通过：" + why);
                holder.tvRefundHint.setVisibility(View.VISIBLE);
            } else {
                holder.tvRefundHint.setVisibility(View.GONE);
            }

            // 配置操作按钮（订单级操作，整组共用）
            configureActionButton(holder, group);
        }

        /**
         * 绑定组内的一个商品行。
         * <p>
         * 「去评价」只出现在待评价页，且**跟着商品行走**而不是放在卡片级 ——
         * 评价是「每人每商品一条」，一单多商品时放卡片级只能评到其中一件。
         */
        private void bindGoodsRow(View goodsRow, OrderInfo item) {
            ImageView img = goodsRow.findViewById(R.id.iv_goods_img);
            TextView title = goodsRow.findViewById(R.id.tv_goods_title);
            TextView price = goodsRow.findViewById(R.id.tv_goods_price);
            TextView count = goodsRow.findViewById(R.id.tv_goods_count);
            Button review = goodsRow.findViewById(R.id.btn_goods_review);

            img.setImageResource(item.getProduct_img());
            // 明细为空的老接口会给出空标题，兜一句「商品」总比空白行好
            String t = item.getProduct_title();
            title.setText(t == null || t.trim().isEmpty() ? "商品" : t);
            price.setText("¥" + item.getProduct_price());
            count.setText("x" + item.getProduct_count());

            if (mFunction == FUNC_PENDING_REVIEW) {
                review.setVisibility(View.VISIBLE);
                review.setOnClickListener(v -> {
                    // 跳转到商品详情页进行评价(传 product_id, 详情页从数据库加载)
                    Intent intent = new Intent(MineFunctionActivity.this, ProductDetailsActivity.class);
                    intent.putExtra(ProductDetailsActivity.EXTRA_PRODUCT_ID, item.getProduct_id());
                    startActivity(intent);
                });
            } else {
                // GONE 而不是 INVISIBLE：其他功能页不该为此在右侧留一片空白
                review.setVisibility(View.GONE);
                review.setOnClickListener(null);
            }
        }

        private void configureActionButton(VH holder, OrderGroup group) {
            // 一个订单只有一个状态，组内任取一件都一样（订单级字段由同一份数据灌入）
            OrderInfo order = group.first();
            String status = order.getStatus();
            String btnText = "";
            View.OnClickListener listener = null;

            if (OrderStatus.STATUS_PENDING_PAYMENT.equals(status)) {
                // 待付款优先给「去支付」：未付款的订单不该显示「申请退货」之类后续动作。
                // 这一分支放在最前，所以任何列表（含全部订单）里的待付款订单都能补付。
                btnText = "去支付";
                listener = v -> {
                    Intent intent = new Intent(MineFunctionActivity.this, PayActivity.class);
                    intent.putExtra(PayActivity.EXTRA_ORDER_ID, (long) order.getOrder_id());
                    startActivityForResult(intent, REQ_PAY);
                };
            } else if (OrderStatus.STATUS_REFUNDING.equals(status)) {
                // 退款申请已提交、等商家审核：不给任何操作入口，避免用户重复申请
                btnText = "";
                listener = null;
            } else if (mFunction == FUNC_PENDING_SHIP) {
                // 待发货页面:不显示操作按钮(去掉模拟发货)
                btnText = "";
                listener = null;
            } else if (mFunction == FUNC_PENDING_RECEIVE) {
                // 待收货页面:确认收货 —— 确认的是「整张订单」，成功后整组一起消失
                btnText = "确认收货";
                listener = v -> {
                    int pos = holder.getAdapterPosition();
                    if (pos < 0 || pos >= mGroups.size()) return;
                    orderRepository.confirmReceive(order.getOrder_id(), new ApiCallback<Boolean>() {
                        @Override
                        public void onSuccess(Boolean data) {
                            Toast.makeText(MineFunctionActivity.this, "已确认收货,快去评价吧", Toast.LENGTH_SHORT).show();
                            // 移除的必须是「整组」：同时把组内的平铺行从数据源摘掉，
                            // 否则下一次 rebuildGroups() 又会把它们折回同一张卡片
                            mOrderList.removeAll(mGroups.get(pos).items);
                            mGroups.remove(pos);
                            notifyItemRemoved(pos);
                            if (mGroups.isEmpty()) showEmpty("暂无待收货订单");
                        }

                        @Override
                        public void onError(int code, String message) {
                            Toast.makeText(MineFunctionActivity.this, message, Toast.LENGTH_SHORT).show();
                        }
                    });
                };
            } else if (mFunction == FUNC_PENDING_REVIEW) {
                // 待评价页：「去评价」已下移到组内每个商品行（一单多商品必须逐件评），
                // 卡片级不再给按钮 —— 留着会让人以为点一次就能把整单评完。
                btnText = "";
                listener = null;
            } else if (mFunction == FUNC_RETURN) {
                // 退款售后页面：申请退款（提交后必须等商家在后台同意才会真正退回）。
                // 退款按订单发起，所以要传整组而不是单件。
                btnText = "申请退款";
                listener = v -> showRefundDialog(group);
            }

            if (listener != null) {
                holder.btnAction.setText(btnText);
                holder.btnAction.setOnClickListener(listener);
                holder.btnAction.setVisibility(View.VISIBLE);
            } else {
                holder.btnAction.setVisibility(View.GONE);
            }
        }

        /**
         * 依据订单状态给「状态标签」着色，把它从裸文字升级为语义色 chip。
         * <p>纯展示逻辑：只设置 {@code tv_order_status} 的文字颜色与背景 drawable，
         * 不读取订单数据、不参与任何状态判断分支，因此不影响业务行为。</p>
         *
         * @param statusView 状态 TextView（来自 {@link VH#tvOrderStatus}）
         * @param status     订单状态字符串，可能为 {@code null}（此时与文案一致按「待发货」着色）
         */
        private void applyStatusChip(TextView statusView, String status) {
            if (statusView == null) {
                return;
            }
            String s = (status == null || status.trim().isEmpty())
                    ? OrderStatus.STATUS_PENDING_SHIP : status.trim();
            int textColorRes;
            int backgroundRes;
            switch (s) {
                case OrderStatus.STATUS_PENDING_PAYMENT:
                case OrderStatus.STATUS_PENDING_SHIP:
                    // 待付款 / 待发货：主色系（需用户关注 / 处理）。深红字 rt_primary_dark + 淡红底，对比度 5.75:1
                    textColorRes = R.color.rt_primary_dark;
                    backgroundRes = R.drawable.bg_status_chip_primary;
                    break;
                case OrderStatus.STATUS_PENDING_RECEIVE:
                case OrderStatus.STATUS_PENDING_REVIEW:
                    // 待收货：信息蓝（深蓝字 rt_info_dark + 淡紫底），对比度 4.90:1。
                    // 注：「待评价」(STATUS_PENDING_REVIEW) 在真实数据下不会命中——后端无「待评价」状态，
                    //     待评价页展示的是状态为「已完成」的订单，故该页 chip 实际走下面的绿色分支。
                    textColorRes = R.color.rt_info_dark;
                    backgroundRes = R.drawable.bg_status_chip_info;
                    break;
                case OrderStatus.STATUS_DONE:
                    // 已完成：成功绿（终态），深绿字 + 淡绿底，对比度 4.56:1
                    textColorRes = R.color.rt_success_dark;
                    backgroundRes = R.drawable.bg_status_chip_success;
                    break;
                case OrderStatus.STATUS_CANCELLED:
                    // 已取消：中性灰。显式分支（不再依赖 default 兜底），避免未来新增状态时静默退化为「近白 chip」。
                    // 对比度 7.61:1
                    textColorRes = R.color.rt_text_tertiary;
                    backgroundRes = R.drawable.bg_status_chip_neutral;
                    break;
                case OrderStatus.STATUS_REFUNDING:
                case OrderStatus.STATUS_REFUNDED:
                case OrderStatus.STATUS_RETURNED:
                    // 退款中 / 已退款：警示橙（售后流程），深橙字 + 淡橙底，对比度 5.11:1。
                    // 注：「已退货」(STATUS_RETURNED) 后端无此状态，真实数据下不会命中，保留仅为兼容旧代码。
                    textColorRes = R.color.rt_tag_highlight_dark;
                    backgroundRes = R.drawable.bg_status_chip_warning;
                    break;
                default:
                    // 未知 / 兜底状态：中性灰（与「已取消」同色，保证任何新状态都不会退化为近白 chip）
                    textColorRes = R.color.rt_text_tertiary;
                    backgroundRes = R.drawable.bg_status_chip_neutral;
                    break;
            }
            statusView.setTextColor(statusView.getContext().getColor(textColorRes));
            statusView.setBackgroundResource(backgroundRes);
        }

        @Override
        public int getItemCount() {
            // 行数 = 订单数（按订单号分组后），不再是商品明细行数
            return mGroups.size();
        }

        class VH extends RecyclerView.ViewHolder {
            TextView tvOrderId, tvOrderStatus, tvOrderTime, tvTrackingNo, tvRefundHint;
            /** 组内商品行的容器：一个订单几件商品就装几行，绑定时动态填充 */
            LinearLayout llGoods;
            Button btnAction;

            VH(@NonNull View itemView) {
                super(itemView);
                tvOrderId = itemView.findViewById(R.id.tv_order_id);
                tvOrderStatus = itemView.findViewById(R.id.tv_order_status);
                tvOrderTime = itemView.findViewById(R.id.tv_order_time);
                tvTrackingNo = itemView.findViewById(R.id.tv_tracking_no);
                tvRefundHint = itemView.findViewById(R.id.tv_refund_hint);
                llGoods = itemView.findViewById(R.id.ll_goods);
                btnAction = itemView.findViewById(R.id.btn_action);
            }
        }
    }
}
