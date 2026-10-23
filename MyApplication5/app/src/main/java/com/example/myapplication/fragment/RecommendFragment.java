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
 * 推荐商品 Fragment - 已改为从后端加载 section=recommend 的商品
 */
public class RecommendFragment extends Fragment {
    private View rootView;
    private androidx.recyclerview.widget.RecyclerView rvGoods;
    private GoodsAdapter adapter;
    private List<Goods> goodsList = new ArrayList<>();
    private CartRepository carRepository;
    private ProductRepository productRepository;
    private SharedPreferences mSharedPreferences;
    private int mUserId;

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, Bundle savedInstanceState) {
        rootView = inflater.inflate(R.layout.fragment_recommend, container, false);
        rvGoods = rootView.findViewById(R.id.rv_goods);
        // 本 Fragment 只被首页使用（getChildFragmentManager 的第 0 页），外层是
        // SwipeAwareNestedScrollView，所以列表自身必须不滚动、滚动统一交给外层。
        // ⚠️ HomeListFragment 被首页和「我的」页共用，那边是按宿主参数设置的（ARG_NESTED_SCROLLING），
        //    别把这里的写死逻辑照搬过去。
        rvGoods.setNestedScrollingEnabled(false);

        carRepository = RepositoryProvider.getInstance(getActivity()).cart();
        productRepository = RepositoryProvider.getInstance(getActivity()).product();
        mSharedPreferences = getActivity().getSharedPreferences("user", getActivity().MODE_PRIVATE);
        mUserId = mSharedPreferences.getInt("user_id", -1);

        adapter = new GoodsAdapter(goodsList);

        StaggeredGridLayoutManager layoutManager =
                new StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL);
        layoutManager.setGapStrategy(StaggeredGridLayoutManager.GAP_HANDLING_MOVE_ITEMS_BETWEEN_SPANS);
        rvGoods.setLayoutManager(layoutManager);

        // 共享头部（分类网格 / 横向轮播 / 促销活动）已上移到 fragment_home.xml，
        // 位于 TabLayout 之上、由 HomeFragment 统一绑定一次；
        // 因此这里不再往列表里插 header item —— 否则切分类时头部会跟着重建/横移。
        // 注意：StaggeredGridLayoutManager 没有 SpanSizeLookup（截至 recyclerview 1.3.2 都没有），
        // 让某个 item 占满整行的正确做法是给它设置 full-span 的 LayoutParams。

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
        productRepository.getProductsBySection("recommend", new ApiCallback<List<Goods>>() {
            @Override
            public void onSuccess(List<Goods> data) {
                goodsList.clear();
                if (data != null) goodsList.addAll(data);
                adapter.notifyDataSetChanged();
                // 商品是异步到的：列表高度变了，往外冒泡一次 requestLayout，
                // 让宿主（首页的外层 NestedScrollView）重新测量，否则会沿用空列表时的高度把商品裁掉。
                if (rvGoods != null) rvGoods.requestLayout();
            }

            @Override
            public void onError(int code, String message) {
                Toast.makeText(getActivity(), message, Toast.LENGTH_SHORT).show();
            }
        });
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
