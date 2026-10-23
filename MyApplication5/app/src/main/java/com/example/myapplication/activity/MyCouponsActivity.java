package com.example.myapplication.activity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.R;
import com.example.myapplication.adapter.CouponAdapter;
import com.example.myapplication.data.CouponRepository;
import com.example.myapplication.data.RepositoryProvider;
import com.example.myapplication.remote.ApiCallback;
import com.example.myapplication.remote.RetrofitClient;
import com.example.myapplication.remote.dto.CouponDTO;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.tabs.TabLayout;

import java.util.ArrayList;
import java.util.List;

/**
 * 「我的优惠券」页。
 * <p>
 * 设计要点：**只发一次请求**（{@code GET /api/coupons/mine} 返回全部券），
 * 三个页签在本地按 {@code status} 过滤。为「已使用 / 已过期」各发一次请求毫无收益，
 * 反而容易出现三个 Tab 数据不一致。
 */
public class MyCouponsActivity extends AppCompatActivity implements CouponAdapter.Listener {

    private static final String[] TAB_TITLES = {"未使用", "已使用", "已过期"};
    /** 与后端 CouponDTO.status 的三态一一对应。 */
    private static final String[] STATUSES = {"UNUSED", "USED", "EXPIRED"};

    private TabLayout tabCoupons;
    private RecyclerView rvCoupons;
    private View stateEmpty;
    private View stateLoading;
    private TextView tvEmptyTitle;
    private TextView tvEmptyDesc;
    private MaterialButton btnGotoPromotions;

    private CouponAdapter adapter;
    private CouponRepository couponRepository;

    /** 全量券（一次拉取），页签切换只做本地过滤。 */
    private final List<CouponDTO> all = new ArrayList<>();

    private int currentTab = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_my_coupons);

        RetrofitClient.init(getApplicationContext());
        couponRepository = RepositoryProvider.getInstance(this).coupon();

        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        tabCoupons = findViewById(R.id.tab_coupons);
        rvCoupons = findViewById(R.id.rv_coupons);
        stateEmpty = findViewById(R.id.state_empty);
        stateLoading = findViewById(R.id.state_loading);
        tvEmptyTitle = findViewById(R.id.tv_empty_title);
        tvEmptyDesc = findViewById(R.id.tv_empty_desc);
        btnGotoPromotions = findViewById(R.id.btn_goto_promotions);

        btnGotoPromotions.setOnClickListener(v ->
                startActivity(new Intent(this, PromotionListActivity.class)));

        adapter = new CouponAdapter();
        adapter.setListener(this);
        rvCoupons.setLayoutManager(new LinearLayoutManager(this));
        rvCoupons.setAdapter(adapter);

        for (String title : TAB_TITLES) {
            tabCoupons.addTab(tabCoupons.newTab().setText(title));
        }
        tabCoupons.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                currentTab = tab.getPosition();
                applyFilter(currentTab);
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
            }
        });

        loadCoupons();
    }

    private void loadCoupons() {
        showLoading();
        couponRepository.mine(new ApiCallback<List<CouponDTO>>() {
            @Override
            public void onSuccess(List<CouponDTO> data) {
                all.clear();
                if (data != null) all.addAll(data);
                applyFilter(currentTab);
            }

            @Override
            public void onError(int code, String message) {
                if (code == 401) {
                    // 券是登录态数据，未登录没什么可展示的
                    Toast.makeText(MyCouponsActivity.this, "请先登录", Toast.LENGTH_SHORT).show();
                    finish();
                    return;
                }
                all.clear();
                applyFilter(currentTab);
                if (message != null && !message.isEmpty()) {
                    Toast.makeText(MyCouponsActivity.this, message, Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    /** 按当前页签过滤并决定显示列表还是空状态。 */
    private void applyFilter(int tabIndex) {
        String want = STATUSES[Math.max(0, Math.min(tabIndex, STATUSES.length - 1))];
        List<CouponDTO> filtered = new ArrayList<>();
        for (CouponDTO c : all) {
            if (want.equals(c.status)) filtered.add(c);
        }
        adapter.setData(filtered);

        if (filtered.isEmpty()) {
            applyEmptyCopy(want);
            showEmpty();
        } else {
            showList();
        }
    }

    /** 空状态文案随页签变化；只有「未使用」才给出「去领券」的出路。 */
    private void applyEmptyCopy(String status) {
        if ("USED".equals(status)) {
            tvEmptyTitle.setText("还没有已使用的券");
            tvEmptyDesc.setText("结算时抵扣过的券会出现在这里");
            btnGotoPromotions.setVisibility(View.GONE);
        } else if ("EXPIRED".equals(status)) {
            tvEmptyTitle.setText("没有过期的券");
            tvEmptyDesc.setText("过了有效期的券会出现在这里");
            btnGotoPromotions.setVisibility(View.GONE);
        } else {
            tvEmptyTitle.setText("还没有可用的优惠券");
            tvEmptyDesc.setText("去促销活动页领券，结算时可抵扣");
            btnGotoPromotions.setVisibility(View.VISIBLE);
        }
    }

    private void showLoading() {
        stateLoading.setVisibility(View.VISIBLE);
        stateEmpty.setVisibility(View.GONE);
        rvCoupons.setVisibility(View.GONE);
    }

    private void showList() {
        stateLoading.setVisibility(View.GONE);
        stateEmpty.setVisibility(View.GONE);
        rvCoupons.setVisibility(View.VISIBLE);
    }

    private void showEmpty() {
        stateLoading.setVisibility(View.GONE);
        rvCoupons.setVisibility(View.GONE);
        stateEmpty.setVisibility(View.VISIBLE);
    }

    @Override
    public void onUse(CouponDTO coupon) {
        // 收银台会自动挑选「当前订单里最省的那张券」，所以这里不需要把券带过去，
        // 只要把用户送回购物路径即可（关掉本页 = 回到「我的」）。
        Toast.makeText(this, "选好商品后，结算时收银台会自动为你选用最优惠的券",
                Toast.LENGTH_LONG).show();
        finish();
    }
}
