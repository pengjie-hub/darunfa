package com.example.myapplication.fragment;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;

import com.example.myapplication.R;
import com.example.myapplication.activity.ProductDetailsActivity;
import com.example.myapplication.adapter.GoodsAdapter;
import com.example.myapplication.data.CartRepository;
import com.example.myapplication.data.ProductRepository;
import com.example.myapplication.data.RepositoryProvider;
import com.example.myapplication.entity.Goods;
import com.example.myapplication.entity.ProductDetails;
import com.example.myapplication.remote.ApiCallback;

import java.util.ArrayList;
import java.util.List;

/**
 * 商品列表 Fragment（两个结构完全相反的宿主共用）
 * <p>
 * 通过 Bundle 中的 section / category / nested_scrolling 参数决定加载哪类商品、
 * 以及列表自身是否参与滚动：
 * <ul>
 *   <li>{@link #newInstanceByCategory(String)} —— 首页分类页：外层是 NestedScrollView，
 *       列表自身不滚动（{@code nested_scrolling=false}）；</li>
 *   <li>{@link #newInstance(String)} —— 「我的」页商品 Tab：外层是 AppBarLayout + ViewPager2，
 *       列表参与嵌套滚动（true），否则顶部信息卡收起后拉不回来。</li>
 * </ul>
 * section: recommend / hot / new；category: 指定分类时优先按分类加载。
 * 数据源已切换为远程后端。
 */
public class HomeListFragment extends Fragment {
    private View rootView;
    private androidx.recyclerview.widget.RecyclerView rvGoods;
    private GoodsAdapter adapter;
    private List<Goods> goodsList = new ArrayList<>();
    private CartRepository carRepository;
    private ProductRepository productRepository;
    private SharedPreferences mSharedPreferences;
    private int mUserId;

    private String mSection;     // recommend / hot / new
    private String mCategory;    // 优先按分类加载

    private static final String ARG_SECTION = "section";
    private static final String ARG_CATEGORY = "category";
    /**
     * 商品列表自身是否参与嵌套滚动。
     * <p>
     * 这个 Fragment 被两个结构完全相反的宿主复用，必须按宿主区分：
     * <ul>
     *   <li><b>首页</b>（{@code newInstanceByCategory}）：外层是一条 NestedScrollView，
     *       列表自身必须 <b>关掉</b>滚动（false），否则内外两层滚动会打架；</li>
     *   <li><b>我的页</b>（{@code newInstance}）：外层是 AppBarLayout + ViewPager2，
     *       折叠/展开顶部信息卡完全依赖列表的嵌套滚动回调，必须 <b>打开</b>（true），
     *       否则顶部信息卡收起后就再也拉不回来。</li>
     * </ul>
     */
    private static final String ARG_NESTED_SCROLLING = "nested_scrolling";

    /** 供首页使用：外层是 NestedScrollView，列表自身不滚动。 */
    public static HomeListFragment newInstanceByCategory(String category) {
        HomeListFragment fragment = new HomeListFragment();
        Bundle args = new Bundle();
        args.putString(ARG_CATEGORY, category);
        args.putBoolean(ARG_NESTED_SCROLLING, false);
        fragment.setArguments(args);
        return fragment;
    }

    /** 供「我的」页使用：外层是 AppBarLayout + ViewPager2，列表必须参与嵌套滚动。 */
    public static HomeListFragment newInstance(String section) {
        HomeListFragment fragment = new HomeListFragment();
        Bundle args = new Bundle();
        args.putString(ARG_SECTION, section);
        args.putBoolean(ARG_NESTED_SCROLLING, true);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, Bundle savedInstanceState) {
        rootView = inflater.inflate(R.layout.fragment_recommend, container, false);
        rvGoods = rootView.findViewById(R.id.rv_goods);

        Bundle args = getArguments();
        if (args != null) {
            mSection = args.getString(ARG_SECTION);
            mCategory = args.getString(ARG_CATEGORY);
        }

        // 是否让列表自己滚动由宿主结构决定（见 ARG_NESTED_SCROLLING 注释）。
        // 默认 true —— 走 AppBarLayout 那一套；首页会用 false 显式覆盖。
        rvGoods.setNestedScrollingEnabled(
                args == null || args.getBoolean(ARG_NESTED_SCROLLING, true));

        StaggeredGridLayoutManager layoutManager =
                new StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL);
        layoutManager.setGapStrategy(StaggeredGridLayoutManager.GAP_HANDLING_MOVE_ITEMS_BETWEEN_SPANS);
        rvGoods.setLayoutManager(layoutManager);

        carRepository = RepositoryProvider.getInstance(getActivity()).cart();
        productRepository = RepositoryProvider.getInstance(getActivity()).product();
        mSharedPreferences = getActivity().getSharedPreferences("user", getActivity().MODE_PRIVATE);
        mUserId = mSharedPreferences.getInt("user_id", -1);

        adapter = new GoodsAdapter(goodsList);
        rvGoods.setAdapter(adapter);

        adapter.setOnItemClickListener(goods -> {
            if (!goods.isOnSale()) {
                Toast.makeText(getContext(), "该商品已下架", Toast.LENGTH_SHORT).show();
                return;
            }
            Intent intent = new Intent(getActivity(), ProductDetailsActivity.class);
            intent.putExtra("product_data", buildProductDetails(goods));
            startActivity(intent);
        });

        adapter.setOnAddToCartClickListener(this::addToCart);

        loadData();

        return rootView;
    }

    private void loadData() {
        ApiCallback<List<Goods>> cb = new ApiCallback<List<Goods>>() {
            @Override
            public void onSuccess(List<Goods> data) {
                goodsList.clear();
                if (data != null) goodsList.addAll(data);
                adapter.notifyDataSetChanged();
                // 商品是异步到的：列表高度变了，往外冒泡一次 requestLayout，
                // 让宿主（首页的外层 NestedScrollView / 我的页的 ViewPager2）重新测量，
                // 否则会沿用空列表时的高度把商品裁掉。
                if (rvGoods != null) rvGoods.requestLayout();
            }

            @Override
            public void onError(int code, String message) {
                Toast.makeText(getActivity(), message, Toast.LENGTH_SHORT).show();
            }
        };

        if (mCategory != null && !mCategory.isEmpty()) {
            productRepository.getProductsByCategory(mCategory, cb);
        } else if (mSection != null && !mSection.isEmpty()) {
            productRepository.getProductsBySection(mSection, cb);
        } else {
            productRepository.getAllProducts(cb);
        }
    }

    private void addToCart(Goods goods) {
        if (mUserId == -1) {
            Toast.makeText(getActivity(), "请先登录", Toast.LENGTH_SHORT).show();
            return;
        }
        if (goods.getStock() <= 0) {
            Toast.makeText(getActivity(), "商品已售罄", Toast.LENGTH_SHORT).show();
            return;
        }
        carRepository.addCar(goods.getGoods_id(), 1, new ApiCallback<Boolean>() {
            @Override
            public void onSuccess(Boolean data) {
                Toast.makeText(getActivity(), "已加入购物车", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(int code, String message) {
                Toast.makeText(getActivity(), message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private ProductDetails buildProductDetails(Goods goods) {
        ProductDetails pd = new ProductDetails();
        pd.setId(goods.getGoods_id());
        pd.setName(goods.getTitle());
        pd.setPrice(goods.getPrice());
        pd.setPic(goods.getImg());
        pd.setIntroduce(goods.getDescription() != null ? goods.getDescription() : ("标签:" + goods.getTag()));
        pd.setBannerpics(goods.getProductdetailsbanner());
        pd.setRvitempics(goods.getProductdetailsrvitem());
        pd.setStock(goods.getStock());
        pd.setMerchantName(goods.getMerchantName());
        pd.setMerchantAddress(goods.getMerchantAddress());
        pd.setDescription(goods.getDescription());
        pd.setPriceValue(goods.getPriceValue());
        pd.setOriginalPrice(goods.getOriginalPrice());
        pd.setCategory(goods.getCategory());
        pd.setSales(goods.getSales());
        pd.setStatus(goods.getStatus());
        return pd;
    }
}
