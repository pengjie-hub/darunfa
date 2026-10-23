package com.example.myapplication.fragment;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.R;
import com.example.myapplication.activity.ProductDetailsActivity;
import com.example.myapplication.adapter.GridAdapter;
import com.example.myapplication.adapter.LeftCategoryAdapter;
import com.example.myapplication.adapter.RightCategoryAdapter;
import com.example.myapplication.data.CartRepository;
import com.example.myapplication.data.ProductRepository;
import com.example.myapplication.data.RepositoryProvider;
import com.example.myapplication.entity.Goods;
import com.example.myapplication.entity.GridItem;
import com.example.myapplication.entity.ProductDetails;
import com.example.myapplication.remote.ApiCallback;

import java.util.ArrayList;
import java.util.List;

/**
 * 分类页面 Fragment - 三级分类结构，数据源已切换为远程后端。
 */
public class CategoryFragment extends Fragment {
    private RecyclerView rvTopCategory;
    private RecyclerView rvLeftCategory;
    private RecyclerView rvRightCategory;

    private GridAdapter gridAdapter;
    private LeftCategoryAdapter leftCategoryAdapter;
    private RightCategoryAdapter rightCategoryAdapter;

    private List<GridItem> gridData = new ArrayList<>();
    private List<String> leftCategoryData = new ArrayList<>();

    private int currentTopPosition = 0;
    private int currentLeftPosition = 0;

    /** 待选中的一级分类名：分类列表尚未返回时先记下，数据到达后自动选中（见 selectCategoryByName）。 */
    private String pendingSelectName;

    /**
     * 当前「应当展示」的一级分类名。
     * <p>
     * 它与 {@code currentTopPosition} 的区别很关键：下标只对「某一版分类列表」有意义，
     * 而分类列表会被随时刷新 —— 后台停用一个分类，后面的下标就整体前移了。
     * 因此跨刷新（尤其是请求飞行途中用户又点了别的分类）一律用<b>名字</b>作为唯一依据。
     */
    private String desiredTopName;

    private CartRepository carRepository;
    private ProductRepository productRepository;
    private SharedPreferences mSharedPreferences;

    private final int[] topIcons = {
            R.drawable.ic_menu_1, R.drawable.ic_menu_2, R.drawable.ic_menu_3,
            R.drawable.ic_menu_4, R.drawable.ic_menu_5, R.drawable.ic_menu_6,
            R.drawable.ic_menu_7, R.drawable.ic_menu_8, R.drawable.ic_menu_9,
            R.drawable.ic_menu_10, R.drawable.ic_menu_11, R.drawable.ic_menu_12,
            R.drawable.ic_menu_13, R.drawable.ic_menu_14, R.drawable.ic_menu_15
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_category, container, false);

        rvTopCategory = rootView.findViewById(R.id.rv_top_category);
        rvLeftCategory = rootView.findViewById(R.id.rv_left_category);
        rvRightCategory = rootView.findViewById(R.id.rv_right_category);

        mSharedPreferences = getActivity().getSharedPreferences("user", getActivity().MODE_PRIVATE);
        carRepository = RepositoryProvider.getInstance(getActivity()).cart();
        productRepository = RepositoryProvider.getInstance(getActivity()).product();

        // 顶部分类(水平)
        LinearLayoutManager topLm = new LinearLayoutManager(getActivity(), LinearLayoutManager.HORIZONTAL, false);
        rvTopCategory.setLayoutManager(topLm);
        gridAdapter = new GridAdapter(gridData);
        rvTopCategory.setAdapter(gridAdapter);
        gridAdapter.setOnItemClickListener(item -> {
            int position = gridData.indexOf(item);
            if (position < 0) return;
            currentTopPosition = position;
            desiredTopName = item.getText() == null ? null : item.getText().trim();
            pendingSelectName = null;   // 用户手动点了顶部，之前从首页带过来的待选作废
            loadLeftCategory(position);
        });

        // 左侧分类
        rvLeftCategory.setLayoutManager(new LinearLayoutManager(getActivity()));
        leftCategoryAdapter = new LeftCategoryAdapter(leftCategoryData);
        rvLeftCategory.setAdapter(leftCategoryAdapter);
        leftCategoryAdapter.setOnItemClickListener(position -> {
            currentLeftPosition = position;
            loadRightProducts();
        });

        // 右侧商品列表
        rvRightCategory.setLayoutManager(new LinearLayoutManager(getActivity()));

        return rootView;
    }

    /**
     * 页面「可见」时刷新分类。
     * <p>
     * 为什么必须在这里刷新：{@code MainActivity} 用 {@code add/hide/show} 管理四个页签，
     * 分类 Fragment 只会被创建一次，{@code onCreateView} 不会因为切页签而重跑。
     * 后台把某个分类改成「停用」后，前端若不重新拉取，页面上就会一直留着这个已停用的分类。
     * 与之配套，{@code RemoteProductRepository} 也不再缓存分类列表（见该类注释）。
     */
    @Override
    public void onResume() {
        super.onResume();
        refreshIfVisible();
    }

    /** 从别的页签切回分类页时触发（hide/show 不会走 onResume）。 */
    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (!hidden) refreshIfVisible();
    }

    /** 仅当页面真正可见时才刷新，避免隐藏状态下发无用的网络请求。 */
    private void refreshIfVisible() {
        if (isAdded() && !isHidden()) initData();
    }

    /**
     * 加载顶部分类数据（远程），并尽量保留当前选中的一级 / 二级分类。
     * <p>
     * 目标分类的选择顺序：<b>首页带过来的待选中分类 → 当前已选中的分类 → 第一个分类</b>。
     * 字符串是在清空 {@code gridData} <b>之前</b>取的，因为用户完全可能在本次请求
     * 飞行途中就点了别的分类 —— 那时 {@code currentTopPosition} 指向的正是「旧列表里的新选择」，
     * 这样读出来的名字才是用户最新想要的那个，不会被本次刷新的「还原」逻辑覆盖掉。
     */
    private void initData() {
        if (rvTopCategory == null || gridAdapter == null) return;   // 视图已销毁

        // 「当前正在展示的」一 / 二级分类：数据回来后用它判断二级分类能否沿用
        final String shownTop = currentTopName();
        final String shownSub = currentSubName();

        productRepository.getCategories(new ApiCallback<List<String>>() {
            @Override
            public void onSuccess(List<String> categories) {
                if (gridAdapter == null) return;   // 视图已销毁
                gridData.clear();
                if (categories != null) {
                    for (int i = 0; i < categories.size() && i < topIcons.length; i++) {
                        gridData.add(new GridItem(topIcons[i], categories.get(i)));
                    }
                }

                if (gridData.isEmpty()) {
                    // 后端一个「启用」的一级分类都没有：清空下方两级列表，别残留旧数据
                    desiredTopName = null;
                    pendingSelectName = null;
                    currentTopPosition = 0;
                    gridAdapter.notifyDataSetChanged();
                    clearBelow();
                    return;
                }

                // 目标分类在「响应到达时」才决定 —— 请求飞行途中用户完全可能又点了别的分类，
                // 或从首页分类入口带着一个分类名进来。这里必须尊重最新的那个，
                // 否则迟到的响应会把用户刚选好的分类顶掉（左列二级分类和右列商品就会对不上）。
                String want = (pendingSelectName != null) ? pendingSelectName : desiredTopName;
                if (want == null) want = shownTop;

                int target = indexOfTop(want);
                if (target < 0) {
                    if (pendingSelectName != null) {
                        // 用户刚从首页点进来，但该分类已被停用 / 改名 → 明确提示，别静默跳到别的分类
                        String missing = pendingSelectName;
                        pendingSelectName = null;
                        desiredTopName = null;
                        gridAdapter.notifyDataSetChanged();
                        if (getActivity() != null) {
                            Toast.makeText(getActivity(), "未找到分类: " + missing, Toast.LENGTH_SHORT).show();
                        }
                        return;
                    }
                    // 原来选中的分类被停用了 → 退回第一个分类
                    target = 0;
                    String firstName = gridData.get(0).getText();
                    want = firstName == null ? null : firstName.trim();
                }

                pendingSelectName = null;
                desiredTopName = want;
                currentTopPosition = target;
                gridAdapter.notifyDataSetChanged();

                // 二级分类只在「目标分类就是原来正在展示的那个」时才沿用：
                // 否则那份二级列表属于另一个一级分类，传过去只会误选 / 串数据
                boolean sameTop = shownTop != null && shownTop.equals(desiredTopName);
                loadLeftCategory(target, sameTop ? shownSub : null);
            }

            @Override
            public void onError(int code, String message) {
                if (getActivity() == null) return;
                Toast.makeText(getActivity(), message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    /** 当前选中的一级分类名（未选中/越界返回 null）。 */
    private String currentTopName() {
        return (currentTopPosition >= 0 && currentTopPosition < gridData.size())
                ? gridData.get(currentTopPosition).getText().trim() : null;
    }

    /** 当前选中的二级分类名（未选中/越界返回 null）。 */
    private String currentSubName() {
        return (currentLeftPosition >= 0 && currentLeftPosition < leftCategoryData.size())
                ? leftCategoryData.get(currentLeftPosition).trim() : null;
    }

    /** 在一级分类列表里按名称找下标，找不到返回 -1。 */
    private int indexOfTop(String name) {
        if (name == null) return -1;
        for (int i = 0; i < gridData.size(); i++) {
            if (name.equals(gridData.get(i).getText().trim())) return i;
        }
        return -1;
    }

    /** 顶部分类为空时，把下方二级分类与商品列表一并清空。 */
    private void clearBelow() {
        leftCategoryData = new ArrayList<>();
        currentLeftPosition = 0;
        leftCategoryAdapter = new LeftCategoryAdapter(leftCategoryData);
        rvLeftCategory.setAdapter(leftCategoryAdapter);
        rightCategoryAdapter = new RightCategoryAdapter(new ArrayList<>());
        rvRightCategory.setAdapter(rightCategoryAdapter);
    }

    private void loadLeftCategory(int topPosition) {
        loadLeftCategory(topPosition, null);
    }

    /**
     * 加载某个一级分类下的二级分类。
     *
     * @param preferSub 数据回来后优先选中的二级分类名（找不到或为 null 时选第一个）
     */
    private void loadLeftCategory(int topPosition, final String preferSub) {
        if (gridData.isEmpty() || topPosition < 0 || topPosition >= gridData.size()) return;
        String topCategory = gridData.get(topPosition).getText();
        productRepository.getSubCategories(topCategory, new ApiCallback<List<String>>() {
            @Override
            public void onSuccess(List<String> subs) {
                if (rvLeftCategory == null) return;   // 视图已销毁
                leftCategoryData = (subs != null) ? subs : new ArrayList<>();
                int restoredSub = -1;
                if (preferSub != null) {
                    for (int i = 0; i < leftCategoryData.size(); i++) {
                        if (preferSub.equals(leftCategoryData.get(i))) {
                            restoredSub = i;
                            break;
                        }
                    }
                }
                currentLeftPosition = (restoredSub >= 0) ? restoredSub : 0;
                leftCategoryAdapter = new LeftCategoryAdapter(leftCategoryData);
                rvLeftCategory.setAdapter(leftCategoryAdapter);
                leftCategoryAdapter.setOnItemClickListener(position -> {
                    currentLeftPosition = position;
                    loadRightProducts();
                });
                loadRightProducts();
            }

            @Override
            public void onError(int code, String message) {
                if (getActivity() == null) return;
                Toast.makeText(getActivity(), message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadRightProducts() {
        if (gridData.isEmpty() || leftCategoryData.isEmpty()) return;
        String topCategory = gridData.get(currentTopPosition).getText();
        String subCategory = leftCategoryData.get(currentLeftPosition);
        productRepository.getProductsByCatAndSub(topCategory, subCategory, new ApiCallback<List<Goods>>() {
            @Override
            public void onSuccess(List<Goods> products) {
                rightCategoryAdapter = new RightCategoryAdapter(products);
                rvRightCategory.setAdapter(rightCategoryAdapter);
                bindRightAdapterListeners();
            }

            @Override
            public void onError(int code, String message) {
                Toast.makeText(getActivity(), message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void bindRightAdapterListeners() {
        rightCategoryAdapter.setOnItemClickListener(goods -> {
            if (!goods.isOnSale()) {
                Toast.makeText(getContext(), "该商品已下架", Toast.LENGTH_SHORT).show();
                return;
            }
            Intent intent = new Intent(getActivity(), ProductDetailsActivity.class);
            intent.putExtra("product_data", buildProductDetails(goods));
            startActivity(intent);
        });
        rightCategoryAdapter.setOnAddToCartClickListener(this::addToCart);
    }

    /**
     * 选中指定名称的一级分类（供首页分类入口点击后跳转过来调用）。
     * <p>
     * 注意这里是<b>异步</b>的：调用方在切到本页签后立刻调用，而顶部分类列表往往还在路上。
     * 因此列表未就绪时先把名字记进 {@code pendingSelectName}，
     * 由 {@link #initData()} 拿到数据后再兑现 —— 否则首次从首页点进来必然弹「未找到分类」。
     */
    public void selectCategoryByName(String categoryName) {
        if (categoryName == null || categoryName.trim().isEmpty()) return;
        final String want = categoryName.trim();
        desiredTopName = want;      // 以名字为准记录「用户想看哪个分类」
        pendingSelectName = null;

        if (gridData.isEmpty()) {
            // 列表还没回来：记下来，等 initData 拿到数据后兑现
            pendingSelectName = want;
            return;
        }

        int target = indexOfTop(want);
        if (target < 0) {
            // 列表里确实没有这个名字（例如该分类刚被后台停用）→ 提示后放弃
            desiredTopName = null;
            if (getActivity() != null) {
                Toast.makeText(getActivity(), "未找到分类: " + want, Toast.LENGTH_SHORT).show();
            }
            return;
        }
        currentTopPosition = target;
        loadLeftCategory(target);
    }

    private void addToCart(Goods goods) {
        int userId = mSharedPreferences.getInt("user_id", -1);
        if (userId == -1) {
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
