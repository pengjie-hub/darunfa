package com.example.myapplication.util;

import android.util.Log;
import android.view.View;
import android.widget.TextView;

import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.R;
import com.example.myapplication.activity.MainActivity;
import com.example.myapplication.activity.PromotionListActivity;
import com.example.myapplication.adapter.GridAdapter;
import com.example.myapplication.adapter.HorizontalBannerAdapter;
import com.example.myapplication.adapter.PromotionAdapter;
import com.example.myapplication.data.RepositoryProvider;
import com.example.myapplication.entity.GridItem;
import com.example.myapplication.entity.HorizontalBannerItem;
import com.example.myapplication.remote.ApiCallback;
import com.example.myapplication.remote.ApiResponse;
import com.example.myapplication.remote.RetrofitClient;
import com.example.myapplication.remote.dto.PromotionDTO;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * 首页「可滚动头部」（{@code layout_home_header.xml}）的绑定器。
 * <p>
 * 头部包含三块内容：分类网格入口、横向推荐横幅、促销活动板块。
 * 它们作为「精选」列表的第 0 个 item 跟随商品列表一起滚动，
 * 因此与商品列表天然同屏、互不遮挡（详见 {@code fragment_home.xml} 顶部说明）。
 * <p>
 * 之所以抽成独立的绑定器而不是塞进 Fragment：
 * <ol>
 *   <li>让 {@code RecommendFragment} 只关心商品列表本身；</li>
 *   <li>头部将来若要在别的 Tab 复用，直接调用 {@link #bind} 即可。</li>
 * </ol>
 */
public final class HomeHeaderBinder {

    private static final String TAG = "HomeHeaderBinder";

    /** 首页促销板块最多展示的条数，其余引导去列表页查看 */
    private static final int PROMO_PREVIEW_LIMIT = 3;

    private HomeHeaderBinder() {
    }

    /**
     * 绑定整个首页头部。
     *
     * @param host   宿主 Fragment（用于拿 Context / Activity 与做生命周期判断）
     * @param header 已 inflate 的 {@code layout_home_header} 视图
     */
    public static void bind(Fragment host, View header) {
        if (host == null || header == null) return;
        bindCategoryGrid(host, header);
        bindHorizontalBanner(host, header);
        bindPromotionSection(host, header);
    }

    /**
     * 只刷新「分类网格入口」。
     * <p>
     * 首页每次可见时调用（后台可能刚停用 / 新增 / 改名了分类）。
     * 只重建网格的 Adapter，不会重复注册监听；分类名没变化时内部会跳过重绘。
     * 轮播与促销板块不在这里刷新 —— 它们有自动轮播动画，重复绑定会打断动画。
     */
    public static void refreshCategoryGrid(Fragment host, View header) {
        if (host == null || header == null) return;
        bindCategoryGrid(host, header);
    }

    /** 网格图标池：按后端分类顺序取用，最多支持 15 个一级分类。 */
    private static final int[] GRID_ICONS = {
            R.drawable.ic_menu_1, R.drawable.ic_menu_2, R.drawable.ic_menu_3,
            R.drawable.ic_menu_4, R.drawable.ic_menu_5, R.drawable.ic_menu_6,
            R.drawable.ic_menu_7, R.drawable.ic_menu_8, R.drawable.ic_menu_9,
            R.drawable.ic_menu_10, R.drawable.ic_menu_11, R.drawable.ic_menu_12,
            R.drawable.ic_menu_13, R.drawable.ic_menu_14, R.drawable.ic_menu_15
    };

    /**
     * 分类网格入口：点击跳到底部导航的分类页。
     * <p>
     * 分类名<b>直接来自后端 {@code GET /api/categories}</b>（该接口只返回 {@code parentId=0}
     * 的一级分类），不再写死在前端 —— 后端新增 / 改名的分类，首页自动跟上。
     * <p>
     * 这样就彻底消除了「前端写死的分类名在后端不存在 → 点进去弹『未找到分类』」这类问题
     * （历史上先后用过 15 个旧本地 SQLite 的营销名，只有 4 个能命中）。
     */
    private static void bindCategoryGrid(final Fragment host, final View header) {
        final RecyclerView gridRecyclerView = header.findViewById(R.id.grid_recycler_view);
        if (gridRecyclerView == null || host.getContext() == null) return;

        // 先放好布局管理器，数据回来再 setAdapter，避免列表闪一下旧内容
        gridRecyclerView.setLayoutManager(new GridLayoutManager(host.getContext(), 5));

        RepositoryProvider.getInstance(host.getContext()).product()
                .getCategories(new ApiCallback<List<String>>() {
                    @Override
                    public void onSuccess(List<String> names) {
                        if (!host.isAdded() || gridRecyclerView == null) return;

                        // 已经按同一份分类名渲染过 → 跳过，避免每次回到首页都重绘一次
                        Object rendered = gridRecyclerView.getTag(R.id.grid_recycler_view);
                        if (rendered != null && rendered.equals(names)) return;
                        gridRecyclerView.setTag(R.id.grid_recycler_view,
                                names == null ? null : new ArrayList<>(names));

                        List<GridItem> gridData = new ArrayList<>();
                        if (names != null) {
                            for (int i = 0; i < names.size() && i < GRID_ICONS.length; i++) {
                                gridData.add(new GridItem(GRID_ICONS[i], names.get(i)));
                            }
                        }

                        GridAdapter gridAdapter = new GridAdapter(gridData);
                        gridRecyclerView.setAdapter(gridAdapter);
                        gridAdapter.setOnItemClickListener(item -> {
                            if (host.getContext() == null) return;
                            // 切换到底部导航的分类页
                            if (host.getActivity() instanceof MainActivity) {
                                ((MainActivity) host.getActivity()).switchToCategory(item.getText());
                            }
                        });
                    }

                    @Override
                    public void onError(int code, String message) {
                        Log.w(TAG, "首页分类入口加载失败，跳过网格渲染: " + message);
                    }
                });
    }

    /** 横向推荐横幅：4 列，每格是一个可左右切换的小轮播。 */
    private static void bindHorizontalBanner(Fragment host, View header) {
        RecyclerView horizontalRecyclerView = header.findViewById(R.id.horizontal_recycler_view);
        if (horizontalRecyclerView == null) return;

        horizontalRecyclerView.setLayoutManager(new GridLayoutManager(host.getContext(), 4));

        List<HorizontalBannerItem> horizontalData = new ArrayList<>();
        horizontalData.add(new HorizontalBannerItem(Arrays.asList(
                R.drawable.banner1_1, R.drawable.banner1_2, R.drawable.banner1_3),
                "精选商品", "品质优选"));
        horizontalData.add(new HorizontalBannerItem(Arrays.asList(
                R.drawable.banner2_1, R.drawable.banner2_2, R.drawable.banner2_3),
                "限时折扣", "低至5折"));
        horizontalData.add(new HorizontalBannerItem(Arrays.asList(
                R.drawable.banner3_1, R.drawable.banner3_2, R.drawable.banner3_3),
                "新品上市", "抢先体验"));
        horizontalData.add(new HorizontalBannerItem(Arrays.asList(
                R.drawable.banner4_1, R.drawable.banner4_2, R.drawable.banner4_3),
                "热销榜单", "销量TOP10"));

        horizontalRecyclerView.setAdapter(new HorizontalBannerAdapter(horizontalData));
    }

    // ==================== 促销活动板块 ====================

    /**
     * 初始化促销板块：横向卡片列表 + "查看更多"入口 + 空状态。
     * <p>
     * 数据来自 {@code GET /api/promotions}，后端已过滤「启用 + 在有效期内」。
     */
    private static void bindPromotionSection(Fragment host, View header) {
        final View promotionSection = header.findViewById(R.id.layout_promotion_section);
        final RecyclerView rvPromotions = header.findViewById(R.id.rv_promotions);
        final TextView tvPromotionEmpty = header.findViewById(R.id.tv_promotion_empty);
        View tvMore = header.findViewById(R.id.tv_promotion_more);
        if (promotionSection == null || rvPromotions == null) return;

        final PromotionAdapter promotionAdapter = new PromotionAdapter();
        promotionAdapter.setHorizontal(true);
        promotionAdapter.setOnItemClickListener(item -> {
            if (host.getActivity() != null) {
                PromotionNavigator.open(host.getActivity(), item);
            }
        });

        rvPromotions.setLayoutManager(
                new LinearLayoutManager(host.getContext(), RecyclerView.HORIZONTAL, false));
        rvPromotions.setAdapter(promotionAdapter);

        if (tvMore != null) {
            tvMore.setOnClickListener(v ->
                    host.startActivity(new android.content.Intent(host.getActivity(), PromotionListActivity.class)));
        }

        loadPromotions(host, promotionSection, rvPromotions, tvPromotionEmpty, promotionAdapter);
    }

    /**
     * 拉取促销活动并渲染。
     * <p>
     * 三种结果的呈现策略：
     * <ul>
     *   <li>有数据 → 展示板块（最多 {@link #PROMO_PREVIEW_LIMIT} 条，其余进列表页）；</li>
     *   <li>返回空数组 → 仍展示板块，但显示友好空状态文案；</li>
     *   <li>请求失败 → 整个板块隐藏（首页非核心内容，静默降级，不打扰用户）。</li>
     * </ul>
     */
    private static void loadPromotions(final Fragment host, final View promotionSection,
                                       final RecyclerView rvPromotions, final TextView tvPromotionEmpty,
                                       final PromotionAdapter promotionAdapter) {
        try {
            RetrofitClient.init(host.requireContext());
            RetrofitClient.api().promotions()
                    .enqueue(new Callback<ApiResponse<List<PromotionDTO>>>() {
                        @Override
                        public void onResponse(Call<ApiResponse<List<PromotionDTO>>> call,
                                               Response<ApiResponse<List<PromotionDTO>>> response) {
                            if (!host.isAdded() || host.getActivity() == null || promotionSection == null) return;

                            ApiResponse<List<PromotionDTO>> body = response.body();
                            if (!response.isSuccessful() || body == null || !body.isSuccess()) {
                                // 静默降级：不显示板块
                                promotionSection.setVisibility(View.GONE);
                                return;
                            }

                            List<PromotionDTO> list = body.data;
                            promotionSection.setVisibility(View.VISIBLE);
                            if (list == null || list.isEmpty()) {
                                rvPromotions.setVisibility(View.GONE);
                                if (tvPromotionEmpty != null) {
                                    tvPromotionEmpty.setVisibility(View.VISIBLE);
                                }
                                promotionAdapter.setData(null);
                            } else {
                                rvPromotions.setVisibility(View.VISIBLE);
                                if (tvPromotionEmpty != null) {
                                    tvPromotionEmpty.setVisibility(View.GONE);
                                }
                                promotionAdapter.setData(
                                        list.size() > PROMO_PREVIEW_LIMIT
                                                ? list.subList(0, PROMO_PREVIEW_LIMIT)
                                                : list);
                            }
                        }

                        @Override
                        public void onFailure(Call<ApiResponse<List<PromotionDTO>>> call, Throwable t) {
                            Log.w(TAG, "促销活动加载失败，隐藏板块", t);
                            if (promotionSection != null) {
                                promotionSection.setVisibility(View.GONE);
                            }
                        }
                    });
        } catch (Exception e) {
            Log.w(TAG, "促销板块请求发起失败，隐藏板块", e);
            if (promotionSection != null) {
                promotionSection.setVisibility(View.GONE);
            }
        }
    }
}
