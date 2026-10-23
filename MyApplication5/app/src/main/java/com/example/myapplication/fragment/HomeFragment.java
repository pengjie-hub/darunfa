package com.example.myapplication.fragment;


import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;


import com.example.myapplication.R;
import com.example.myapplication.activity.SearchActivity;
import com.example.myapplication.data.ProductRepository;
import com.example.myapplication.data.RepositoryProvider;
import com.example.myapplication.remote.ApiCallback;
import com.example.myapplication.util.HomeHeaderBinder;
import com.example.myapplication.view.SwipeAwareNestedScrollView;
import com.google.android.material.tabs.TabLayout;

import java.util.ArrayList;
import java.util.List;

/**
 * 首页 Fragment。
 * <p>
 * 结构（2026-09-14 调整）：整页只有一条纵向滚动链（{@code SwipeAwareNestedScrollView}），
 * 自上而下为 搜索栏 → 分类网格 → 横向轮播 → 促销活动 → TabLayout(导航栏) → 商品区。
 * <ul>
 *   <li>分类网格 / 横向轮播 / 促销活动只创建一份、位于 TabLayout 之上，
 *       切换分类时<b>保持不变</b>（既不重建、也不随商品区横移）；</li>
 *   <li>商品区只是 {@code goods_host} 这个容器，各分类的 Fragment 按需 add/hide，
 *       所以只有商品卡片会跟着分类变；</li>
 *   <li>商品列表自身不滚动（wrap_content），滚动统一交给外层容器，不存在嵌套滚动冲突。</li>
 * </ul>
 * <p>
 * 为什么不用 ViewPager2 承载分类页：它是 final 类无法子类化，且嵌在外层滚动容器里
 * 只能靠 wrap_content 量高 —— 切页后不会重新量高度，商品少的分类下方会留一大片空白（实测复现）。
 * 因此改成「FrameLayout + 横滑手势」。
 * <p>
 * <b>页签是动态的</b>：第一个固定为「精选」（全部商品），其余页签 = 后端
 * {@code GET /api/categories} 返回的全部一级分类，一个分类一个页签。
 */
public class HomeFragment extends Fragment {

    /**
     * 分类页 Fragment 的 tag 前缀，完整 tag = 前缀 + 分类名（如 {@code home_tab_精选}）。
     * <p>
     * 用<b>分类名</b>而不是页签下标做 tag：后台停用 / 新增 / 排序变化会导致下标整体前移，
     * 那时按下标复用就会把「A 分类的 Fragment」显示在「B 分类」的页签下。
     */
    private static final String TAG_TAB_FRAGMENT = "home_tab_";

    private SwipeAwareNestedScrollView homeScroll;
    private TabLayout tabLayout;
    /** 首页可滚动头部（分类网格 / 轮播 / 促销），刷新分类网格入口时需要用到。 */
    private View headerView;

    private ProductRepository productRepository;
    private final List<String> tabTitles = new ArrayList<>();
    private int currentPosition = -1;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {

        View rootView = inflater.inflate(R.layout.fragment_home, container, false);
        homeScroll = rootView.findViewById(R.id.home_scroll);
        tabLayout = rootView.findViewById(R.id.tab_layout);

        // 搜索栏: 点击即跳转到独立搜索页(无需先输入再回车)
        View searchBar = rootView.findViewById(R.id.search_bar);
        searchBar.setOnClickListener(v -> openSearchPage());

        // 共享头部（分类网格 / 横向轮播 / 促销活动）：整页只绑一次。
        // 它位于 TabLayout 之上，所以切分类时不会跟着变。
        headerView = rootView.findViewById(R.id.home_header);
        if (headerView != null) {
            HomeHeaderBinder.bind(this, headerView);
        }

        // 视图重建时子 Fragment 会被系统恢复成「全部可见」，先全部隐藏，
        // 等 loadTabs() 回来再按当前页签显示，避免多页商品叠在一起。
        FragmentManager fm = getChildFragmentManager();
        List<Fragment> restored = fm.getFragments();
        if (!restored.isEmpty()) {
            FragmentTransaction tx = fm.beginTransaction();
            for (Fragment f : restored) tx.hide(f);
            tx.commitNowAllowingStateLoss();
        }

        // 横向滑动切换分类：只在导航栏以下（也就是商品区域）响应，
        // 免得和头部那个横向轮播抢手势。
        homeScroll.setOnHorizontalFlingListener((direction, downRawY) -> {
            if (tabLayout == null) return;
            int[] loc = new int[2];
            tabLayout.getLocationOnScreen(loc);
            if (downRawY <= loc[1] + tabLayout.getHeight()) return;
            selectTab(currentPosition + direction);
        });

        // 点击页签切换分类
        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                showTab(tab.getPosition());
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
                // 再点当前分类 → 回到顶部
                if (homeScroll != null) homeScroll.smoothScrollTo(0, 0);
            }
        });

        return rootView;
    }

    /**
     * 首页「可见」时刷新分类。
     * <p>
     * 首页有两处展示分类：TabLayout 的页签、头部「分类入口」网格。二者都来自
     * {@code GET /api/categories}。后台停用 / 新增 / 改名分类后，如果不在页面可见时
     * 重新拉一次，用户就会看到早已停用的分类（这就是「停用不生效」在首页的表现）。
     */
    @Override
    public void onResume() {
        super.onResume();
        refreshIfVisible();
    }

    /** 从别的页签切回首页时触发（hide/show 不会走 onResume）。 */
    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (!hidden) refreshIfVisible();
    }

    private void refreshIfVisible() {
        if (!isAdded() || isHidden()) return;
        loadTabs();
        // 分类网格入口：内部会判断分类是否真的变了，没变就不重绘，避免每次返回都闪一下
        HomeHeaderBinder.refreshCategoryGrid(this, headerView);
    }

    /**
     * 拉取后端一级分类，构建「精选 + 各分类」页签。
     * <p>
     * 分类名必须来自后端：{@link HomeListFragment#newInstanceByCategory(String)} 会把名字
     * 原样交给 {@code getProductsByCategory} 去做「按名 → 分类 ID」解析，名字对不上就查不到商品。
     * 写死名字正是历史上「除精选外页签全空」的根因，所以这里改为动态获取。
     * <p>
     * 该方法会被反复调用（每次页面可见），因此：分类没变时直接返回，不重建页签
     * —— 重建会把用户当前选中的分类重置回「精选」。
     */
    private void loadTabs() {
        if (productRepository == null) {
            productRepository = RepositoryProvider.getInstance(requireContext()).product();
        }
        final String keepTitle = (currentPosition >= 0 && currentPosition < tabTitles.size())
                ? tabTitles.get(currentPosition) : null;
        productRepository.getCategories(new ApiCallback<List<String>>() {
            @Override
            public void onSuccess(List<String> names) {
                if (!isAdded() || tabLayout == null) return;
                if (sameAsCurrentTabs(names)) return;   // 分类没变化：不重建页签
                bindTabs(names);
                restoreTab(keepTitle);
            }

            @Override
            public void onError(int code, String message) {
                // 降级：分类拉取失败也要保证「精选」可用，不能整页空白。
                // 但已有页签时保持现状，别因为一次网络抖动把首页页签清空。
                if (!isAdded() || tabLayout == null) return;
                if (tabLayout.getTabCount() > 0) return;
                bindTabs(null);
            }
        });
    }

    /** 后端返回的一级分类是否与当前页签一致（忽略开头固定的「精选」）。 */
    private boolean sameAsCurrentTabs(List<String> names) {
        if (tabLayout == null || tabLayout.getTabCount() == 0) return false;   // 还没装配过
        List<String> current = new ArrayList<>(tabTitles);
        if (!current.isEmpty() && "精选".equals(current.get(0))) current.remove(0);
        List<String> incoming = (names != null) ? new ArrayList<>(names) : new ArrayList<String>();
        return current.equals(incoming);
    }

    /** 重建页签后，尽量选回原来的分类（该分类已不存在则保持「精选」）。 */
    private void restoreTab(String title) {
        if (title == null || tabLayout == null) return;
        for (int i = 0; i < tabTitles.size(); i++) {
            if (title.equals(tabTitles.get(i))) {
                selectTab(i);
                return;
            }
        }
    }

    /** 用给定的分类名装配页签；{@code categoryNames} 为空时退化为只有「精选」。 */
    private void bindTabs(@Nullable List<String> categoryNames) {
        tabTitles.clear();
        tabTitles.add("精选");
        if (categoryNames != null) {
            tabTitles.addAll(categoryNames);
        }

        // 分类名变了：先把已经不在列表里的旧分类页 Fragment 拆掉，
        // 否则它们会一直挂在 goods_host 上（页签里已看不到入口，但实例还在）。
        pruneStaleTabFragments();

        currentPosition = -1;
        tabLayout.removeAllTabs();
        for (String title : tabTitles) {
            // addTab 会自动选中第一个页签并触发 onTabSelected → showTab(0)
            tabLayout.addTab(tabLayout.newTab().setText(title));
        }
        if (currentPosition < 0 && tabLayout.getTabCount() > 0) {
            showTab(0);   // 兜底
        }
    }

    /**
     * 拆掉「已不属于当前页签集合」的分类页 Fragment。
     * <p>
     * 页签是按分类名与 Fragment 一一对应的（tag = 前缀 + 分类名），
     * 所以后台停用 / 改名一个分类后，旧分类的 Fragment 需要显式移除，
     * 否则会以隐藏状态一直留在容器里。
     */
    private void pruneStaleTabFragments() {
        FragmentManager fm = getChildFragmentManager();
        FragmentTransaction tx = null;
        for (Fragment f : fm.getFragments()) {
            String tag = f.getTag();
            if (tag == null || !tag.startsWith(TAG_TAB_FRAGMENT)) continue;
            String title = tag.substring(TAG_TAB_FRAGMENT.length());
            if (!tabTitles.contains(title)) {
                if (tx == null) tx = fm.beginTransaction();
                tx.remove(f);
            }
        }
        if (tx != null) tx.commitNowAllowingStateLoss();
    }

    /** 选中指定位置页签（越界忽略）。 */
    private void selectTab(int position) {
        if (tabLayout == null) return;
        if (position < 0 || position >= tabLayout.getTabCount()) return;
        TabLayout.Tab tab = tabLayout.getTabAt(position);
        if (tab != null) tab.select();
    }

    /** 显示指定分类页：隐藏其它，按需创建并 add（用 tag 复用，兼容视图重建）。 */
    private void showTab(int position) {
        if (tabLayout == null) return;
        if (position < 0 || position >= tabTitles.size()) return;
        if (position == currentPosition) return;

        final FragmentManager fm = getChildFragmentManager();
        FragmentTransaction tx = fm.beginTransaction();
        tx.setReorderingAllowed(true);
        for (Fragment f : fm.getFragments()) {
            tx.hide(f);
        }

        final String tag = TAG_TAB_FRAGMENT + tabTitles.get(position);
        Fragment target = fm.findFragmentByTag(tag);
        if (target == null) {
            target = createTabFragment(position);
            tx.add(R.id.goods_host, target, tag);
        } else {
            tx.show(target);
        }
        tx.commitNowAllowingStateLoss();
        currentPosition = position;

        // 商品区高度变了：让外层重新排版，并把滚动位置夹回合法范围
        // （从商品多的分类切到商品少的分类时，滚动位置可能超出新内容高度）
        if (homeScroll != null) {
            homeScroll.requestLayout();
            homeScroll.post(() -> {
                if (homeScroll == null) return;
                View content = homeScroll.getChildAt(0);
                if (content == null) return;
                int max = Math.max(0, content.getHeight() - homeScroll.getHeight());
                if (homeScroll.getScrollY() > max) homeScroll.scrollTo(0, max);
            });
        }
    }

    /** 第 0 个是「精选」（全部商品），其余按后端分类名创建。 */
    private Fragment createTabFragment(int position) {
        if (position == 0) return new RecommendFragment();
        return HomeListFragment.newInstanceByCategory(tabTitles.get(position));
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        homeScroll = null;
        tabLayout = null;
        headerView = null;
    }

    /** 跳转到独立搜索页(不携带关键词, 由搜索页展示历史与热门) */
    private void openSearchPage() {
        Intent intent = new Intent(getActivity(), SearchActivity.class);
        startActivity(intent);
    }
}
