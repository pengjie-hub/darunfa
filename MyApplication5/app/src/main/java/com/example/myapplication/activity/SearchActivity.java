package com.example.myapplication.activity;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SearchView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;

import com.example.myapplication.R;
import com.example.myapplication.adapter.GoodsAdapter;
import com.example.myapplication.adapter.SearchTagAdapter;
import com.example.myapplication.data.CartRepository;
import com.example.myapplication.data.ProductRepository;
import com.example.myapplication.data.RepositoryProvider;
import com.example.myapplication.data.SearchHistoryRepository;
import com.example.myapplication.entity.Goods;
import com.example.myapplication.remote.ApiCallback;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 商品搜索页（独立页面）。
 * <p>
 * 交互设计：
 * <ul>
 *   <li>首页搜索框「点击即跳转」到本页，进入时展示「发现面板」（搜索历史 + 热门搜索）。</li>
 *   <li>顶部输入框可输入关键词：输入过程 300ms 防抖实时搜索，清空输入回到发现面板。</li>
 *   <li>提交搜索（点「搜索」/键盘回车 / 点击历史词 / 点击热门词）→ 执行搜索 + 写入历史 + 展示结果。</li>
 *   <li>「清空」一键清除全部搜索历史；历史词胶囊带「×」可单条删除。</li>
 * </ul>
 * <p>
 * 数据存储：搜索历史与搜索次数统计用 SharedPreferences（{@link SearchHistoryRepository}）；
 * 热门搜索优先按「搜索次数」取 Top N，无搜索数据时按商品销量取商品名，最后回退静态词表。
 * 商品检索复用 {@link ProductRepository#searchProducts}。
 */
public class SearchActivity extends AppCompatActivity {

    /** 兼容入口：外部可携带关键词直接搜索（当前首页已改为无关键词跳转） */
    public static final String EXTRA_KEYWORD = "keyword";

    /** 热门搜索兜底静态词表（DB 异常或为空时使用） */
    private static final List<String> HOT_KEYWORDS = Arrays.asList(
            "车厘子", "紫皮大蒜", "榴莲", "茅台", "抽纸",
            "牛奶", "洗衣液", "五常大米", "苹果", "冲锋衣");

    /** 热门搜索展示条数 */
    private static final int HOT_KEYWORD_COUNT = 8;

    private SearchView searchView;
    private TextView tvSearchBtn;
    private TextView tvResultCount;
    private TextView tvEmpty;
    private RecyclerView rvResult;

    // 发现面板相关
    private ScrollView svDiscover;
    private LinearLayout llHistory;
    private RecyclerView rvHistory;
    private RecyclerView rvHot;
    private TextView tvHistoryEmpty;
    private TextView tvClearHistory;

    private GoodsAdapter adapter;
    private final List<Goods> goodsList = new ArrayList<>();

    private SearchTagAdapter historyAdapter;
    private SearchTagAdapter hotAdapter;

    private ProductRepository productRepository;
    private CartRepository carRepository;
    private SearchHistoryRepository historyRepository;
    private SharedPreferences mSharedPreferences;
    private int mUserId = -1;

    /** 当前结果对应的关键词，用于避免重复查询 */
    private String currentKeyword = "";

    // 输入防抖(本地 SQLite 查询很快, 但避免每次按键都全量查库)
    private final Handler debounceHandler = new Handler(Looper.getMainLooper());
    private Runnable searchRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search);

        // ====== 初始化仓储与登录态 ======
        productRepository = RepositoryProvider.getInstance(this).product();
        carRepository = RepositoryProvider.getInstance(this).cart();
        historyRepository = RepositoryProvider.getInstance(this).searchHistory();
        mSharedPreferences = getSharedPreferences("user", MODE_PRIVATE);
        mUserId = mSharedPreferences.getInt("user_id", -1);

        // ====== 初始化控件 ======
        ImageView ivBack = findViewById(R.id.iv_back);
        searchView = findViewById(R.id.search_view);
        tvSearchBtn = findViewById(R.id.tv_search_btn);
        tvResultCount = findViewById(R.id.tv_result_count);
        tvEmpty = findViewById(R.id.tv_empty);
        rvResult = findViewById(R.id.rv_search_result);

        svDiscover = findViewById(R.id.sv_discover);
        llHistory = findViewById(R.id.ll_history);
        rvHistory = findViewById(R.id.rv_history);
        rvHot = findViewById(R.id.rv_hot);
        tvHistoryEmpty = findViewById(R.id.tv_history_empty);
        tvClearHistory = findViewById(R.id.tv_clear_history);

        // ====== 结果列表 ======
        StaggeredGridLayoutManager layoutManager =
                new StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL);
        layoutManager.setGapStrategy(StaggeredGridLayoutManager.GAP_HANDLING_MOVE_ITEMS_BETWEEN_SPANS);
        rvResult.setLayoutManager(layoutManager);
        adapter = new GoodsAdapter(goodsList);
        rvResult.setAdapter(adapter);

        // 点击商品跳转详情(详情页支持按 product_id 从数据库加载)
        adapter.setOnItemClickListener(goods -> {
            if (!goods.isOnSale()) {
                Toast.makeText(SearchActivity.this, "该商品已下架", Toast.LENGTH_SHORT).show();
                return;
            }
            Intent intent = new Intent(SearchActivity.this, ProductDetailsActivity.class);
            intent.putExtra(ProductDetailsActivity.EXTRA_PRODUCT_ID, goods.getGoods_id());
            startActivity(intent);
        });

        // 加入购物车
        adapter.setOnAddToCartClickListener(this::addToCart);

        // ====== 搜索历史标签(可单条删除) ======
        historyAdapter = new SearchTagAdapter(false, true);
        rvHistory.setLayoutManager(new GridLayoutManager(this, 2));
        rvHistory.setAdapter(historyAdapter);
        historyAdapter.setOnTagClickListener(this::onTagClick);
        historyAdapter.setOnTagDeleteListener(keyword -> {
            historyRepository.removeHistory(keyword);
            refreshHistory();
        });

        // ====== 热门搜索标签(按销量动态) ======
        hotAdapter = new SearchTagAdapter(true, false);
        rvHot.setLayoutManager(new GridLayoutManager(this, 3));
        rvHot.setAdapter(hotAdapter);
        hotAdapter.setOnTagClickListener(this::onTagClick);

        // ====== 搜索监听 ======
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                startSearch(query);
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                String kw = newText == null ? "" : newText.trim();
                if (kw.isEmpty()) {
                    showDiscovery();
                    return true;
                }
                if (kw.equals(currentKeyword)) {
                    return true; // 已是当前结果, 跳过重复查询
                }
                debounceSearch(kw);
                return true;
            }
        });

        // 搜索按钮
        tvSearchBtn.setOnClickListener(v -> startSearch(searchView.getQuery().toString()));

        // 清空搜索历史
        tvClearHistory.setOnClickListener(v -> {
            historyRepository.clearHistory();
            refreshHistory();
            Toast.makeText(this, "已清空搜索历史", Toast.LENGTH_SHORT).show();
        });

        // 返回键
        ivBack.setOnClickListener(v -> finish());

        // ====== 初始状态 ======
        String keyword = getIntent().getStringExtra(EXTRA_KEYWORD);
        if (!TextUtils.isEmpty(keyword)) {
            // 外部携带关键词：直接搜索
            searchView.setQuery(keyword, false);
            startSearch(keyword);
        } else {
            showDiscovery();
        }
    }

    /** 进入发现面板：展示历史 + 热门，隐藏结果区 */
    private void showDiscovery() {
        cancelDebounce();
        currentKeyword = "";
        goodsList.clear();
        adapter.notifyDataSetChanged();
        svDiscover.setVisibility(View.VISIBLE);
        rvResult.setVisibility(View.GONE);
        tvEmpty.setVisibility(View.GONE);
        tvResultCount.setVisibility(View.GONE);
        refreshHistory();
        loadHotKeywords();
    }

    /** 展示结果区 */
    private void showResult() {
        svDiscover.setVisibility(View.GONE);
        rvResult.setVisibility(View.VISIBLE);
    }

    /** 刷新搜索历史标签与占位提示 */
    private void refreshHistory() {
        List<String> history = historyRepository.getHistory(10);
        boolean hasHistory = !history.isEmpty();
        llHistory.setVisibility(hasHistory ? View.VISIBLE : View.GONE);
        tvHistoryEmpty.setVisibility(hasHistory ? View.GONE : View.VISIBLE);
        historyAdapter.setItems(history);
    }

    /**
     * 加载热门搜索词：优先按搜索次数取 Top N；无搜索数据时按销量取商品名；最后回退静态词表。
     */
    private void loadHotKeywords() {
        List<String> hot = new ArrayList<>(historyRepository.getHotKeywords(HOT_KEYWORD_COUNT));
        if (!hot.isEmpty()) {
            hotAdapter.setItems(hot);
            return;
        }
        productRepository.getAllProducts(new ApiCallback<List<Goods>>() {
            @Override
            public void onSuccess(List<Goods> top) {
                List<String> names = new ArrayList<>();
                int n = Math.min(HOT_KEYWORD_COUNT, top == null ? 0 : top.size());
                for (int i = 0; i < n; i++) {
                    String name = top.get(i).getTitle();
                    if (!TextUtils.isEmpty(name)) {
                        names.add(name);
                    }
                }
                hotAdapter.setItems(names.isEmpty() ? HOT_KEYWORDS : names);
            }

            @Override
            public void onError(int code, String message) {
                hotAdapter.setItems(HOT_KEYWORDS);
            }
        });
    }

    /** 点击历史词/热门词：回填输入框并直接搜索 */
    private void onTagClick(String keyword) {
        if (TextUtils.isEmpty(keyword)) return;
        searchView.setQuery(keyword, false); // 回填输入框(不重复触发 submit)
        startSearch(keyword);
    }

    /** 防抖搜索: 延迟 300ms, 连续输入时只执行最后一次(实时预览, 不写历史) */
    private void debounceSearch(final String keyword) {
        cancelDebounce();
        searchRunnable = () -> doSearch(keyword);
        debounceHandler.postDelayed(searchRunnable, 300);
    }

    private void cancelDebounce() {
        if (searchRunnable != null) {
            debounceHandler.removeCallbacks(searchRunnable);
            searchRunnable = null;
        }
    }

    /** 提交搜索：执行查询 + 写入历史 + 累计搜索次数 + 展示结果 */
    private void startSearch(String keyword) {
        String kw = keyword == null ? "" : keyword.trim();
        if (kw.isEmpty()) {
            Toast.makeText(this, "请输入搜索关键词", Toast.LENGTH_SHORT).show();
            return;
        }
        cancelDebounce();
        doSearch(kw);
        historyRepository.addHistory(kw);
        historyRepository.recordSearch(kw);
        refreshHistory();
    }

    /** 执行搜索并刷新列表 */
    private void doSearch(String keyword) {
        String kw = keyword == null ? "" : keyword.trim();
        currentKeyword = kw;
        // 一旦执行搜索即离开发现面板，避免「历史/热门」与「结果/空状态」同屏叠加
        svDiscover.setVisibility(View.GONE);
        goodsList.clear();
        adapter.setHighlightKeyword(kw);
        if (kw.isEmpty()) {
            adapter.notifyDataSetChanged();
            updateEmptyState(kw);
            return;
        }
        productRepository.searchProducts(kw, new ApiCallback<List<Goods>>() {
            @Override
            public void onSuccess(List<Goods> data) {
                if (!kw.equals(currentKeyword)) return; // 已输入新词，丢弃过期结果
                goodsList.addAll(data == null ? new ArrayList<>() : data);
                adapter.notifyDataSetChanged();
                updateEmptyState(kw);
            }

            @Override
            public void onError(int code, String message) {
                if (!kw.equals(currentKeyword)) return;
                goodsList.clear();
                adapter.notifyDataSetChanged();
                updateEmptyState(kw);
            }
        });
    }

    /** 根据结果更新空状态与结果数量提示 */
    private void updateEmptyState(String keyword) {
        boolean hasKeyword = !TextUtils.isEmpty(keyword);
        boolean hasResult = !goodsList.isEmpty();

        if (hasResult) {
            showResult();
            tvEmpty.setVisibility(View.GONE);
            tvResultCount.setVisibility(View.VISIBLE);
            tvResultCount.setText("共 " + goodsList.size() + " 件相关商品");
        } else {
            rvResult.setVisibility(View.GONE);
            tvEmpty.setVisibility(View.VISIBLE);
            tvResultCount.setVisibility(View.GONE);
            tvEmpty.setText(hasKeyword ? "未找到相关商品" : "输入关键词搜索商品");
        }
    }

    /** 加入购物车(与首页列表逻辑一致: 校验登录态与库存, 成功后扣减库存) */
    private void addToCart(Goods goods) {
        if (mUserId == -1) {
            Toast.makeText(this, "请先登录", Toast.LENGTH_SHORT).show();
            return;
        }
        if (goods.getStock() <= 0) {
            Toast.makeText(this, "商品已售罄", Toast.LENGTH_SHORT).show();
            return;
        }
        carRepository.addCar(goods.getGoods_id(), 1, new ApiCallback<Boolean>() {
            @Override
            public void onSuccess(Boolean data) {
                Toast.makeText(SearchActivity.this, "已加入购物车", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(int code, String message) {
                Toast.makeText(SearchActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }
}
