package com.example.myapplication.fragment;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import com.example.myapplication.activity.BannerActivity;
import com.example.myapplication.activity.MineFunctionActivity;
import com.example.myapplication.activity.ProfileEditActivity;
import com.example.myapplication.adapter.FeatureAdapter;
import com.example.myapplication.entity.FeatureItem;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.tabs.TabLayout;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;
import com.example.myapplication.R;
import com.example.myapplication.activity.AddressListActivity;
import com.example.myapplication.activity.CsChatActivity;
import com.example.myapplication.activity.LoginActivity;
import com.example.myapplication.activity.MyCouponsActivity;
import com.example.myapplication.activity.WalletActivity;
import com.example.myapplication.data.AddressRepository;
import com.example.myapplication.data.CouponRepository;
import com.example.myapplication.data.OrderRepository;
import com.example.myapplication.data.RepositoryProvider;
import com.example.myapplication.data.UserRepository;
import com.example.myapplication.entity.OrderInfo;
import com.example.myapplication.entity.OrderStatus;
import com.example.myapplication.entity.UserInfo;
import com.example.myapplication.remote.ApiCallback;
import com.example.myapplication.remote.ApiResponse;
import com.example.myapplication.remote.RetrofitClient;
import com.example.myapplication.remote.SessionManager;
import com.example.myapplication.remote.dto.AddressDTO;
import com.example.myapplication.remote.dto.UserDTO;
import com.example.myapplication.adapter.MineGridAdapter;
import com.example.myapplication.entity.GridItem;
import com.example.myapplication.fragment.HomeListFragment;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * 我的页面 Fragment
 *
 * 主要内容:
 * 1. 用户信息头部(头像/用户名/会员等级/积分/余额/优惠券)
 * 2. 我的订单区块(待发货/待收货/待评价/退款售后 + 角标 + 查看全部订单)
 * 3. 功能列表(促销海报/开发票/送货到家/自助收银/出示购物卡/出示会员卡)
 * 4. 商品推荐 Tab(推荐/热门/最新)
 */
public class MineFragment extends Fragment {

    private static final String TAG = "MineFragment";

    private RecyclerView mineGridList;
    private MineGridAdapter gridAdapter;
    private List<GridItem> gridData = new ArrayList<>();
    private TabLayout mineTabLayout;
    private ViewPager2 mineViewPager;
    private List<Fragment> fragmentList = new ArrayList<>();
    private List<String> tabTitleList = new ArrayList<>();

    // 用户信息头部控件
    private ImageView ivAvatar;
    private TextView tvUsername;
    private TextView tvMemberLevel;
    private TextView tvPoints;
    private TextView tvBalance;
    private TextView tvCoupons;
    private TextView tvViewAllOrders;
    /** 默认地址卡副标题（无地址时作为「点此添加」的引导文案）。 */
    private TextView tvDefaultAddress;

    // 退出登录
    private MaterialButton btnLogout;

    // ListView 相关
    private ListView featureListView;
    private FeatureAdapter featureAdapter;
    private List<FeatureItem> featureData = new ArrayList<>();

    // 数据仓储
    private UserRepository userRepository;
    private OrderRepository orderRepository;
    private AddressRepository addressRepository;
    private CouponRepository couponRepository;
    private SharedPreferences mSharedPreferences;
    private int mUserId = -1;

    /** 远程资料请求去重标志：避免 onResume 高频触发时并发重复请求 */
    private boolean mProfileRequesting;

    public MineFragment() {
        // Required empty public constructor
    }

    public static MineFragment newInstance() {
        return new MineFragment();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onResume() {
        super.onResume();
        // 从其他页面返回时刷新用户信息与订单角标
        if (tvUsername != null) {
            refreshUserInfo();
        }
    }

    /**
     * 页签 Fragment 用 add/hide/show 管理，切换页签不会走 onResume；
     * 因此「重新可见时刷新」必须同时挂 onHiddenChanged。
     * 从「我的余额」页返回后余额可能已变，这里重新拉资料即可刷新头部余额。
     */
    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (!hidden && tvUsername != null) {
            refreshUserInfo();
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_mine, container, false);

        // 初始化数据仓储与 SharedPreferences
        if (getActivity() != null) {
            userRepository = RepositoryProvider.getInstance(getActivity()).user();
            orderRepository = RepositoryProvider.getInstance(getActivity()).order();
            addressRepository = RepositoryProvider.getInstance(getActivity()).address();
            couponRepository = RepositoryProvider.getInstance(getActivity()).coupon();
            mSharedPreferences = getActivity().getSharedPreferences("user", android.content.Context.MODE_PRIVATE);
            mUserId = mSharedPreferences.getInt("user_id", -1);
        }

        // ====== 1. 绑定用户信息头部控件 ======
        ivAvatar = rootView.findViewById(R.id.iv_avatar);
        tvUsername = rootView.findViewById(R.id.tv_username);
        tvMemberLevel = rootView.findViewById(R.id.tv_member_level);
        tvPoints = rootView.findViewById(R.id.tv_points);
        tvBalance = rootView.findViewById(R.id.tv_balance);
        tvCoupons = rootView.findViewById(R.id.tv_coupons);
        tvViewAllOrders = rootView.findViewById(R.id.tv_view_all_orders);
        btnLogout = rootView.findViewById(R.id.btn_logout);

        // 退出登录(需二次确认)
        btnLogout.setOnClickListener(v -> confirmLogout());

        // 在线客服入口。
        // 注意：这里原来挂的是「小发智能购物助手」卡片，和首页悬浮球（AssistantFab）重复，
        // 已按需求移除；小发只保留悬浮球入口，本位置改为客服入口。
        View cardCsService = rootView.findViewById(R.id.card_cs_service);
        if (cardCsService != null) {
            cardCsService.setOnClickListener(v -> {
                if (mUserId == -1) {
                    Toast.makeText(getActivity(), "请先登录", Toast.LENGTH_SHORT).show();
                    startActivity(new Intent(getActivity(), LoginActivity.class));
                    return;
                }
                startActivity(new Intent(getActivity(), CsChatActivity.class));
            });
        }

        // 默认地址卡 -> 地址管理页（未登录先登录）
        tvDefaultAddress = rootView.findViewById(R.id.tv_default_address);
        View cardDefaultAddress = rootView.findViewById(R.id.card_default_address);
        if (cardDefaultAddress != null) {
            cardDefaultAddress.setOnClickListener(v -> {
                if (mUserId == -1) {
                    Toast.makeText(getActivity(), "请先登录", Toast.LENGTH_SHORT).show();
                    startActivity(new Intent(getActivity(), LoginActivity.class));
                    return;
                }
                startActivity(new Intent(getActivity(), AddressListActivity.class));
            });
        }

        // 顶部「优惠券」摘要 -> 我的优惠券
        View couponSummary = rootView.findViewById(R.id.coupon_summary);
        if (couponSummary != null) {
            couponSummary.setOnClickListener(v -> {
                if (mUserId == -1) {
                    Toast.makeText(getActivity(), "请先登录", Toast.LENGTH_SHORT).show();
                    startActivity(new Intent(getActivity(), LoginActivity.class));
                    return;
                }
                startActivity(new Intent(getActivity(), MyCouponsActivity.class));
            });
        }

        // 顶部「余额」摘要 -> 我的余额（余额展示 + 充值与预设金额 + 余额明细）。
        // 余额读的是后端真实账户余额，与收银台「余额支付」、购物卡页为同一账户。
        View balanceSummary = rootView.findViewById(R.id.balance_summary);
        if (balanceSummary != null) {
            balanceSummary.setOnClickListener(v -> {
                if (mUserId == -1) {
                    Toast.makeText(getActivity(), "请先登录", Toast.LENGTH_SHORT).show();
                    startActivity(new Intent(getActivity(), LoginActivity.class));
                    return;
                }
                WalletActivity.start(getActivity());
            });
        }

        // 头像点击 -> 未登录则跳转登录
        ivAvatar.setOnClickListener(v -> {
            if (mUserId == -1) {
                startActivity(new Intent(getActivity(), LoginActivity.class));
            }
        });

        // 设置图标点击 -> 修改个人信息
        ImageView ivSettings = rootView.findViewById(R.id.iv_settings);
        if (ivSettings != null) {
            ivSettings.setOnClickListener(v -> {
                if (mUserId == -1) {
                    Toast.makeText(getActivity(), "请先登录", Toast.LENGTH_SHORT).show();
                    return;
                }
                startActivity(new Intent(getActivity(), ProfileEditActivity.class));
            });
        }

        // 用户名点击也可以编辑个人信息
        tvUsername.setOnClickListener(v -> {
            if (mUserId == -1) {
                startActivity(new Intent(getActivity(), LoginActivity.class));
                return;
            }
            startActivity(new Intent(getActivity(), ProfileEditActivity.class));
        });

        // "查看全部订单" 点击 -> 跳转到退款售后(展示所有订单)
        tvViewAllOrders.setOnClickListener(v -> {
            if (mUserId == -1) {
                Toast.makeText(getActivity(), "请先登录", Toast.LENGTH_SHORT).show();
                return;
            }
            Intent intent = new Intent(getActivity(), MineFunctionActivity.class);
            intent.putExtra(MineFunctionActivity.EXTRA_FUNCTION, MineFunctionActivity.FUNC_RETURN);
            intent.putExtra(MineFunctionActivity.EXTRA_TITLE, "全部订单");
            startActivity(intent);
        });

        // ====== 2. 初始化订单状态网格 ======
        mineGridList = rootView.findViewById(R.id.mine_grid_list);
        GridLayoutManager gridLayoutManager = new GridLayoutManager(getActivity(), 4, RecyclerView.VERTICAL, false);
        mineGridList.setLayoutManager(gridLayoutManager);
        initGridData();
        gridAdapter = new MineGridAdapter(gridData);
        mineGridList.setAdapter(gridAdapter);

        // 网格项点击 -> 跳转到对应功能页
        gridAdapter.setOnItemClickListener((item, position) -> {
            if (mUserId == -1) {
                Toast.makeText(getActivity(), "请先登录", Toast.LENGTH_SHORT).show();
                return;
            }
            int functionCode;
            switch (position) {
                case 0:
                    functionCode = MineFunctionActivity.FUNC_PENDING_SHIP;
                    break;
                case 1:
                    functionCode = MineFunctionActivity.FUNC_PENDING_RECEIVE;
                    break;
                case 2:
                    functionCode = MineFunctionActivity.FUNC_PENDING_REVIEW;
                    break;
                case 3:
                    functionCode = MineFunctionActivity.FUNC_RETURN;
                    break;
                default:
                    return;
            }
            Intent intent = new Intent(getActivity(), MineFunctionActivity.class);
            intent.putExtra(MineFunctionActivity.EXTRA_FUNCTION, functionCode);
            intent.putExtra(MineFunctionActivity.EXTRA_TITLE, item.getText());
            startActivity(intent);
        });

        // ====== 3. 初始化功能列表 ======
        initFeatureData();
        featureListView = rootView.findViewById(R.id.feature_list_view);
        featureAdapter = new FeatureAdapter(getActivity(), featureData);
        featureListView.setAdapter(featureAdapter);
        featureAdapter.setOnItemClickListener((item, position) -> {
            if (position == 0) {
                // 促销海报 -> 轮播图页面
                startActivity(new Intent(getActivity(), BannerActivity.class));
                return;
            }
            int functionCode;
            switch (position) {
                case 1:
                    functionCode = MineFunctionActivity.FUNC_INVOICE;
                    break;
                case 2:
                    functionCode = MineFunctionActivity.FUNC_DELIVERY;
                    break;
                case 3:
                    functionCode = MineFunctionActivity.FUNC_SELF_CHECKOUT;
                    break;
                case 4:
                    functionCode = MineFunctionActivity.FUNC_SHOPPING_CARD;
                    break;
                case 5:
                    functionCode = MineFunctionActivity.FUNC_MEMBER_CARD;
                    break;
                default:
                    Toast.makeText(getActivity(), "点击了: " + item.getText(), Toast.LENGTH_SHORT).show();
                    return;
            }
            Intent intent = new Intent(getActivity(), MineFunctionActivity.class);
            intent.putExtra(MineFunctionActivity.EXTRA_FUNCTION, functionCode);
            intent.putExtra(MineFunctionActivity.EXTRA_TITLE, item.getText());
            startActivity(intent);
        });

        // ====== 4. 初始化商品推荐 ViewPager + TabLayout ======
        initViewPagerAndTabLayout(rootView);

        // ====== 5. 刷新用户信息与订单角标 ======
        refreshUserInfo();

        return rootView;
    }

    /** 刷新用户信息与订单角标(从登录/订单页返回时也会调用) */
    private void refreshUserInfo() {
        if (getActivity() == null || mSharedPreferences == null) return;
        mUserId = mSharedPreferences.getInt("user_id", -1);
        String username = mSharedPreferences.getString("username", null);

        if (mUserId == -1 || username == null) {
            tvUsername.setText("未登录");
            tvMemberLevel.setText("点击登录");
            tvPoints.setText("0");
            tvBalance.setText("¥0.00");
            tvCoupons.setText("0");
            // 未登录使用默认头像
            ivAvatar.setImageResource(R.drawable.avatar_1);
            if (tvDefaultAddress != null) tvDefaultAddress.setText("还没有地址，点此添加");
            // 未登录不显示"退出登录"
            if (btnLogout != null) btnLogout.setVisibility(View.GONE);
        } else {
            tvUsername.setText(username);
            tvMemberLevel.setText("金卡会员");
            // 已登录显示"退出登录"
            if (btnLogout != null) btnLogout.setVisibility(View.VISIBLE);
            // 积分仍读本地 SP（会员体系未接入后端）；
            // 余额不再读本地 SharedPreferences("shopping_card") 的假数据（默认 ¥1000），
            // 改由 refreshProfileFromRemote() 回调里的 UserDTO.balance 回填——本处不重置，避免闪烁。
            SharedPreferences memSp = getActivity().getSharedPreferences("member", android.content.Context.MODE_PRIVATE);
            int points = memSp.getInt("points_" + mUserId, 1888);
            tvPoints.setText(String.valueOf(points));
            // 券数改由后端真实统计（原来写死的 6 张是假数据）
            tvCoupons.setText("0");

            // 1) 先用本地缓存秒开头像(避免网络返回前闪烁默认图)
            applyAvatarByName(resolveLocalAvatarName());
            // 2) 再异步拉取后端权威资料(头像 / 昵称), 成功后覆盖显示并回写本地缓存
            refreshProfileFromRemote();

            // 3) 券数与默认地址同样以后端为准
            refreshCouponCount();
            refreshDefaultAddress();
        }

        // 刷新订单角标
        refreshOrderBadges();
    }

    /** 拉取「还能用的券」张数，填充头部摘要（失败保持 0，不打扰用户）。 */
    private void refreshCouponCount() {
        if (couponRepository == null || mUserId == -1) return;
        couponRepository.count(new ApiCallback<Long>() {
            @Override
            public void onSuccess(Long data) {
                if (tvCoupons == null) return;
                tvCoupons.setText(String.valueOf(data == null ? 0L : data));
            }

            @Override
            public void onError(int code, String message) {
                // 静默：摘要位不值得为一次失败弹提示
            }
        });
    }

    /** 拉取默认地址，填充「默认收货地址」卡副标题。 */
    private void refreshDefaultAddress() {
        if (addressRepository == null || tvDefaultAddress == null || mUserId == -1) return;
        addressRepository.getDefault(new ApiCallback<AddressDTO>() {
            @Override
            public void onSuccess(AddressDTO data) {
                if (tvDefaultAddress == null) return;
                if (data == null) {
                    tvDefaultAddress.setText("还没有地址，点此添加");
                    return;
                }
                String name = data.receiverName == null ? "" : data.receiverName;
                String phone = data.receiverPhone == null ? "" : data.receiverPhone;
                String addr = data.fullAddress == null ? "" : data.fullAddress;
                tvDefaultAddress.setText(name + "  " + phone + "  " + addr);
            }

            @Override
            public void onError(int code, String message) {
                // 静默：无地址时后端会返回成功 + data=null，这里出错多为未登录/网络抖动
            }
        });
    }

    /** 从本地 SQLite 缓存读取头像资源名(失败/无数据回退 avatar_1) */
    private String resolveLocalAvatarName() {
        try {
            UserInfo user = userRepository.getUserById(mUserId);
            if (user != null && !TextUtils.isEmpty(user.getAvatar())) {
                return user.getAvatar();
            }
        } catch (Exception e) {
            Log.w(TAG, "读取本地头像缓存失败, 使用默认头像", e);
        }
        return "avatar_1";
    }

    /** 按资源名渲染头像; 资源不存在时回退默认头像 */
    private void applyAvatarByName(String avatarName) {
        if (getActivity() == null || ivAvatar == null) return;
        int res = getResources().getIdentifier(
                TextUtils.isEmpty(avatarName) ? "avatar_1" : avatarName,
                "drawable", getActivity().getPackageName());
        ivAvatar.setImageResource(res != 0 ? res : R.drawable.avatar_1);
    }

    /**
     * 从后端拉取权威用户资料(头像 / 昵称 / 手机号), 成功后覆盖显示并回写本地缓存。
     * <p>
     * 之所以不能只查本地 SQLite: 登录走的是后端账号体系, 本地 user_table 里
     * 可能根本没有该 userId 的记录(详见 ProfileEditActivity 的同类修复)。
     * <p>
     * 请求失败静默处理 —— 界面已用本地缓存渲染, 不算错误。
     */
    private void refreshProfileFromRemote() {
        if (mProfileRequesting) return;   // 去重: onResume 高频触发时只保留一个在途请求
        mProfileRequesting = true;

        try {
            RetrofitClient.init(getActivity().getApplicationContext());
            RetrofitClient.api().profile().enqueue(new Callback<ApiResponse<UserDTO>>() {
                @Override
                public void onResponse(Call<ApiResponse<UserDTO>> call,
                                       Response<ApiResponse<UserDTO>> response) {
                    mProfileRequesting = false;
                    if (!isAdded() || getActivity() == null || getView() == null) return;
                    ApiResponse<UserDTO> body = response.body();
                    if (!response.isSuccessful() || body == null || !body.isSuccess()
                            || body.data == null) {
                        return;   // 静默: 已用本地缓存渲染
                    }
                    UserDTO dto = body.data;

                    // 头像: 后端可能为 null → 回退默认
                    applyAvatarByName(TextUtils.isEmpty(dto.avatar) ? "avatar_1" : dto.avatar);

                    // 显示名: 优先 nickname, 为空回退 username
                    String display = TextUtils.isEmpty(dto.nickname) ? dto.username : dto.nickname;
                    if (!TextUtils.isEmpty(display) && tvUsername != null
                            && mSharedPreferences != null) {
                        tvUsername.setText(display);
                        mSharedPreferences.edit().putString("username", display).apply();
                    }

                    // 余额: 以服务端 UserDTO.balance 为准（与「我的余额」页、收银台余额支付同一账户）
                    if (tvBalance != null) {
                        tvBalance.setText(String.format(java.util.Locale.CHINA, "¥%.2f", dto.balance));
                    }

                    // 回写本地缓存(幂等, 失败仅记日志)
                    try {
                        userRepository.upsertUser((int) dto.id,
                                TextUtils.isEmpty(dto.username) ? display : dto.username, "");
                        if (!TextUtils.isEmpty(dto.avatar)) {
                            userRepository.updateAvatar((int) dto.id, dto.avatar);
                        }
                        if (!TextUtils.isEmpty(dto.phone)) {
                            userRepository.updatePhone((int) dto.id, dto.phone);
                        }
                    } catch (Exception e) {
                        Log.w(TAG, "回写用户资料缓存失败(不影响显示)", e);
                    }
                }

                @Override
                public void onFailure(Call<ApiResponse<UserDTO>> call, Throwable t) {
                    mProfileRequesting = false;
                    // 静默失败: 已用本地缓存渲染, 不打扰用户
                    Log.w(TAG, "远程资料拉取失败, 继续使用本地缓存", t);
                }
            });
        } catch (Exception e) {
            mProfileRequesting = false;
            Log.w(TAG, "远程资料请求发起失败, 继续使用本地缓存", e);
        }
    }

    // ==================== 退出登录 ====================

    /** 退出登录: 先二次确认, 避免误触 */
    private void confirmLogout() {
        if (getActivity() == null) return;
        new AlertDialog.Builder(getActivity())
                .setTitle("退出登录")
                .setMessage("确定要退出当前账号吗？\n退出后需重新登录才能查看订单与个人信息。")
                .setPositiveButton("退出登录", (dialog, which) -> doLogout())
                .setNegativeButton("取消", null)
                .show();
    }

    /**
     * 执行退出:
     * <ol>
     *   <li>清空本地登录态(SharedPreferences "user" 中的 user_id/username/password/is_login);</li>
     *   <li>清空会话 token(供 OkHttp 拦截器读取的 SessionManager);</li>
     *   <li>跳转登录页并清空 Activity 回退栈, 防止用户按返回键回到已登录页面。</li>
     * </ol>
     */
    private void doLogout() {
        // 1. 清本地登录态
        if (mSharedPreferences != null) {
            mSharedPreferences.edit()
                    .remove("user_id")
                    .remove("username")
                    .remove("password")
                    .remove("is_login")
                    .apply();
        }
        // 2. 清会话 token
        try {
            SessionManager.clear();
        } catch (Exception e) {
            Log.w(TAG, "清理会话令牌失败", e);
        }
        // 3. 回到登录页并清空回退栈
        if (getActivity() == null) return;
        Intent intent = new Intent(getActivity(), LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        getActivity().finish();
    }

    /**
     * 从后端查询订单，按<b>订单数</b>统计各状态数量并更新角标。
     * <p>
     * 口径：每个角标都必须等于「点进去看到的卡片数」。后端把「订单 × 商品」拍平成
     * 一行一个商品（同一订单号会返回多行），所以计数前一律<b>按订单号去重</b>——
     * 历史 bug 就是直接累加扁平列表，一单买 2 件商品角标就 +2，与列表里 1 张卡片对不上。
     * </p>
     */
    private void refreshOrderBadges() {
        if (orderRepository == null) return;
        if (mUserId == -1) {
            if (gridData.size() >= 4) {
                gridData.get(0).setBadge(0);
                gridData.get(1).setBadge(0);
                gridData.get(2).setBadge(0);
                gridData.get(3).setBadge(0);
                gridAdapter.updateData(gridData);
            }
            return;
        }
        orderRepository.getAllOrders(new ApiCallback<List<OrderInfo>>() {
            @Override
            public void onSuccess(List<OrderInfo> data) {
                // 用 Set 收集订单号做去重：同一订单多行商品只会进来一次。
                Set<String> pendingShip = new HashSet<>();
                Set<String> pendingReceive = new HashSet<>();
                Set<String> pendingReview = new HashSet<>();
                Set<String> afterSale = new HashSet<>();
                if (data != null) {
                    for (OrderInfo o : data) {
                        String s = o.getStatus();
                        String orderKey = o.getDisplayOrderNo();
                        if (OrderStatus.STATUS_PENDING_SHIP.equals(s)) {
                            pendingShip.add(orderKey);
                        } else if (OrderStatus.STATUS_PENDING_RECEIVE.equals(s)) {
                            pendingReceive.add(orderKey);
                        } else if (OrderStatus.STATUS_DONE.equals(s) && !o.isReviewed()) {
                            // 待评价必须排除已评价过的商品：用户评完角标就要减掉，
                            // 否则「我的」页会一直挂着一个消不掉的待评价红点（历史 bug）。
                            // 同一订单只要有 1 件商品还没评价，整单仍算「待评价订单」，
                            // 与待评价列表页一致（那边按订单号分组，一单只出一张卡片）。
                            pendingReview.add(orderKey);
                        }
                        // 「退款/售后」= 点进去看到的卡片数，过滤条件与列表页共用同一口径。
                        if (OrderStatus.isAfterSaleVisible(s)) {
                            afterSale.add(orderKey);
                        }
                    }
                }
                if (gridData.size() >= 4) {
                    gridData.get(0).setBadge(pendingShip.size());
                    gridData.get(1).setBadge(pendingReceive.size());
                    gridData.get(2).setBadge(pendingReview.size());
                    gridData.get(3).setBadge(afterSale.size());
                    gridAdapter.updateData(gridData);
                }
            }

            @Override
            public void onError(int code, String message) {
            }
        });
    }

    // 初始化 ViewPager2 和 TabLayout
    private void initViewPagerAndTabLayout(View rootView) {
        mineTabLayout = rootView.findViewById(R.id.mine_tab_layout);
        mineViewPager = rootView.findViewById(R.id.mine_view_pager);

        // 三个 Tab 分别加载 recommend/hot/new 商品
        fragmentList.add(HomeListFragment.newInstance("recommend"));
        fragmentList.add(HomeListFragment.newInstance("hot"));
        fragmentList.add(HomeListFragment.newInstance("new"));

        tabTitleList.add("推荐商品");
        tabTitleList.add("热门商品");
        tabTitleList.add("最新商品");

        MineViewPagerAdapter adapter = new MineViewPagerAdapter(getActivity());
        mineViewPager.setAdapter(adapter);

        for (String title : tabTitleList) {
            mineTabLayout.addTab(mineTabLayout.newTab().setText(title));
        }

        mineTabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                mineViewPager.setCurrentItem(tab.getPosition());
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
            }
        });

        mineViewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                mineTabLayout.selectTab(mineTabLayout.getTabAt(position));
            }
        });
    }

    // ViewPager2 适配器
    private class MineViewPagerAdapter extends FragmentStateAdapter {
        public MineViewPagerAdapter(@NonNull FragmentActivity fragmentActivity) {
            super(fragmentActivity);
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            return fragmentList.get(position);
        }

        @Override
        public int getItemCount() {
            return fragmentList.size();
        }
    }

    // 初始化订单状态网格数据
    private void initGridData() {
        int[] imageResIds = {
                R.drawable.daifahuo, R.drawable.daishouhuo, R.drawable.daipingjia, R.drawable.tuikuang
        };
        String[] itemNames = {
                "待发货", "待收货", "待评价", "退款/售后"
        };

        for (int i = 0; i < 4; i++) {
            gridData.add(new GridItem(imageResIds[i], itemNames[i], 0));
        }
    }

    /**
     * 初始化功能列表数据
     */
    private void initFeatureData() {
        featureData.add(new FeatureItem(R.drawable.ic_promotion, "促销海报", 0));
        featureData.add(new FeatureItem(R.drawable.ic_invoice, "开发票", 0));
        featureData.add(new FeatureItem(R.drawable.ic_delivery, "送货到家", 0));
        featureData.add(new FeatureItem(R.drawable.ic_self_checkout, "自助收银", 0));
        featureData.add(new FeatureItem(R.drawable.ic_shopping_card, "出示购物卡", 1));
        featureData.add(new FeatureItem(R.drawable.ic_member_card, "出示会员卡", 1));
    }
}
