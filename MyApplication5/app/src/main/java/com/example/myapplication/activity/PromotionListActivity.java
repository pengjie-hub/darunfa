package com.example.myapplication.activity;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.R;
import com.example.myapplication.adapter.PromotionAdapter;
import com.example.myapplication.remote.ApiResponse;
import com.example.myapplication.remote.RetrofitClient;
import com.example.myapplication.remote.dto.PromotionDTO;
import com.example.myapplication.util.PromotionNavigator;
import com.google.android.material.button.MaterialButton;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * 促销活动列表页。
 * <p>
 * 与首页板块使用同一个 {@link PromotionAdapter}，只是切换为纵向撑满模式。
 * 页面状态分四态：加载中 / 列表 / 空状态 / 加载失败（可重试）。
 */
public class PromotionListActivity extends AppCompatActivity {

    private static final String TAG = "PromotionListActivity";

    private RecyclerView rvPromotions;
    private View stateLoading;
    private View stateEmpty;
    private View stateError;
    private TextView tvErrorDesc;
    private MaterialButton btnRetry;
    private MaterialButton btnEmptyBack;

    private PromotionAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_promotion_list);

        bindViews();
        loadPromotions();
    }

    private void bindViews() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        rvPromotions = findViewById(R.id.rv_promotions);
        stateLoading = findViewById(R.id.state_loading);
        stateEmpty = findViewById(R.id.state_empty);
        stateError = findViewById(R.id.state_error);
        tvErrorDesc = findViewById(R.id.tv_error_desc);
        btnRetry = findViewById(R.id.btn_retry);
        btnEmptyBack = findViewById(R.id.btn_empty_back);

        btnRetry.setOnClickListener(v -> loadPromotions());
        btnEmptyBack.setOnClickListener(v -> finish());

        adapter = new PromotionAdapter();
        adapter.setHorizontal(false);
        adapter.setOnItemClickListener(item -> PromotionNavigator.open(this, item));
        rvPromotions.setLayoutManager(new LinearLayoutManager(this));
        rvPromotions.setAdapter(adapter);
    }

    /** 拉取生效中的促销活动（后端已过滤「启用 + 在有效期内」）。 */
    private void loadPromotions() {
        showLoading();
        try {
            RetrofitClient.init(this);
            RetrofitClient.api().promotions().enqueue(new Callback<ApiResponse<List<PromotionDTO>>>() {
                @Override
                public void onResponse(Call<ApiResponse<List<PromotionDTO>>> call,
                                       Response<ApiResponse<List<PromotionDTO>>> response) {
                    ApiResponse<List<PromotionDTO>> body = response.body();
                    if (response.isSuccessful() && body != null && body.isSuccess()) {
                        List<PromotionDTO> list = body.data;
                        adapter.setData(list);
                        if (list == null || list.isEmpty()) {
                            showEmpty();
                        } else {
                            showList();
                        }
                    } else {
                        String msg = (body != null && body.message != null)
                                ? body.message
                                : ("加载失败 (HTTP " + response.code() + ")");
                        showError(msg);
                    }
                }

                @Override
                public void onFailure(Call<ApiResponse<List<PromotionDTO>>> call, Throwable t) {
                    Log.w(TAG, "促销活动加载失败", t);
                    showError("无法连接服务器，请确认后端已启动后重试");
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "促销活动请求发起失败", e);
            showError("请求发起失败：" + e.getMessage());
        }
    }

    private void showLoading() {
        stateLoading.setVisibility(View.VISIBLE);
        stateEmpty.setVisibility(View.GONE);
        stateError.setVisibility(View.GONE);
        rvPromotions.setVisibility(View.GONE);
    }

    private void showList() {
        stateLoading.setVisibility(View.GONE);
        stateEmpty.setVisibility(View.GONE);
        stateError.setVisibility(View.GONE);
        rvPromotions.setVisibility(View.VISIBLE);
    }

    private void showEmpty() {
        stateLoading.setVisibility(View.GONE);
        stateError.setVisibility(View.GONE);
        rvPromotions.setVisibility(View.GONE);
        stateEmpty.setVisibility(View.VISIBLE);
    }

    private void showError(String desc) {
        stateLoading.setVisibility(View.GONE);
        stateEmpty.setVisibility(View.GONE);
        rvPromotions.setVisibility(View.GONE);
        stateError.setVisibility(View.VISIBLE);
        if (desc != null && !desc.isEmpty()) {
            tvErrorDesc.setText(desc);
        }
    }
}
