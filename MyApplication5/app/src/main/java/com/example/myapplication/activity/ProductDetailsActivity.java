package com.example.myapplication.activity;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Paint;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RatingBar;
import android.widget.TextView;
import android.widget.Toolbar;
import android.widget.Toast;

import com.example.myapplication.data.BrowseHistoryRepository;
import com.example.myapplication.data.CartRepository;
import com.example.myapplication.data.ProductRepository;
import com.example.myapplication.data.RepositoryProvider;
import com.example.myapplication.data.ReviewRepository;
import com.example.myapplication.entity.Goods;
import com.example.myapplication.entity.ReviewInfo;
import com.example.myapplication.adapter.ReviewAdapter;
import com.example.myapplication.remote.ApiCallback;
import com.example.myapplication.remote.ApiUtils;
import com.example.myapplication.remote.RetrofitClient;
import com.example.myapplication.remote.dto.AiSummaryRequest;
import com.example.myapplication.remote.dto.AiSummaryResponse;
import com.google.android.material.button.MaterialButton;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.example.myapplication.R;
import com.example.myapplication.adapter.BannerAdapter;
import com.example.myapplication.adapter.ProductImageAdapter;
import com.example.myapplication.entity.ProductDetails;
import com.example.myapplication.util.PriceUtil;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

/**
 * 商品详情页 Activity
 *
 * 主要功能:
 * 1. 商品轮播图 + 详情图展示
 * 2. 商品价格、库存、销量、商家信息展示
 * 3. 加入购物车(库存由后端管理)
 * 4. 用户评论区: 查看评论、发表评论、评分、追评
 * 5. 评论总结功能(调用后端 AI 生成评论总结)
 */
public class ProductDetailsActivity extends AppCompatActivity {

    /**
     * 商品 ID 的 Intent extra key。
     * <p>
     * 首页/搜索/订单/促销等多处都会写入这个 key，<b>务必统一走这个常量</b>，
     * 避免多处硬编码字符串导致 key 写错却静默取到默认值。
     */
    public static final String EXTRA_PRODUCT_ID = "product_id";

    private ViewPager2 viewPager2;
    private LinearLayout indicatorLayout;
    private List<Integer> picList = new ArrayList<>();
    private int currentPosition = 0;
    private Timer timer;
    private Handler handler = new Handler(Looper.getMainLooper());
    private TextView product_name;
    private TextView product_price;
    private TextView tvOriginalPrice;   // 原价（划线）
    private TextView tvDiscount;        // 折扣标签（如 7.7折）
    private View llDiscount;            // 原价 + 折扣 行容器
    private ProductDetails productDetails;
    private RecyclerView rvPdItems;
    private ProductImageAdapter rvAdapter;
    private MaterialButton ivAddToCart;
    private ImageButton btnGoToCart;
    private CartRepository carRepository;
    private ProductRepository productRepository;
    private ReviewRepository reviewRepository;
    private BrowseHistoryRepository browseHistoryRepository;
    private SharedPreferences mSharedPreferences;
    private int mUserId = -1;

    private TextView tvStock;
    private TextView tvSales;
    private TextView tvMerchantName;
    private TextView tvMerchantAddress;

    private RecyclerView rvReviews;
    private ReviewAdapter reviewAdapter;
    private List<ReviewInfo> reviewList = new ArrayList<>();
    private List<ReviewInfo> fullReviewList = new ArrayList<>();
    private TextView tvReviewCount;
    private TextView tvAvgRating;
    private RatingBar rbReview;
    private EditText etReviewContent;
    private MaterialButton btnSubmitReview;
    private MaterialButton btnAiSummary;
    private MaterialButton btnViewAllReviews;
    private View aiSummaryContainer;
    private TextView tvAiSummary;
    private ProgressBar pbAiSummary;
    private TextView tvReviewHint;
    private View reviewInputContainer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_product_details);

        initViews();

        mSharedPreferences = getSharedPreferences("user", MODE_PRIVATE);
        mUserId = mSharedPreferences.getInt("user_id", -1);

        RepositoryProvider provider = RepositoryProvider.getInstance(this);
        carRepository = provider.cart();
        productRepository = provider.product();
        reviewRepository = provider.review();
        browseHistoryRepository = provider.browseHistory();

        productDetails = (ProductDetails) getIntent().getSerializableExtra("product_data");
        if (productDetails != null) {
            if (!ensureOnSaleOrFinish(productDetails)) return;
            continueInit();
        } else {
            long productId = readProductIdFromIntent();
            if (productId > 0 && productId <= Integer.MAX_VALUE) {
                productRepository.getProductById((int) productId, new ApiCallback<Goods>() {
                    @Override
                    public void onSuccess(Goods data) {
                        productDetails = goodsToProductDetails(data);
                        if (!ensureOnSaleOrFinish(productDetails)) return;
                        continueInit();
                    }

                    @Override
                    public void onError(int code, String message) {
                        Toast.makeText(ProductDetailsActivity.this, "商品信息加载失败", Toast.LENGTH_SHORT).show();
                        finish();
                    }
                });
            } else {
                Toast.makeText(this, "商品信息加载失败", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    /**
     * 读取 Intent 里的商品 ID，兼容 Integer / Long / String 三种写入方式。
     * <p>
     * <b>为什么不用 {@code getIntExtra}：</b>它不做类型转换，取到的对象不是 Integer 时
     * <b>不会抛异常</b>，只在 {@code Bundle.getInt} 里打一条 ClassCastException 警告日志
     * 然后返回默认值。于是调用方静默拿到 -1，最终表现为"商品信息加载失败"，
     * 而真正的类型错误被完全掩盖——极难定位（本次线上问题正是这样被藏的）。
     * <p>
     * 各调用方历史上写入类型并不一致（订单/购物车是 int，促销模块是 long），
     * 这里统一按 {@link Number} 取值，从根上消除类型不一致的隐患。
     *
     * @return 商品 ID；extra 缺失或无法解析时返回 -1
     */
    private long readProductIdFromIntent() {
        Intent intent = getIntent();
        Bundle extras = intent == null ? null : intent.getExtras();
        Object raw = extras == null ? null : extras.get(EXTRA_PRODUCT_ID);
        if (raw instanceof Number) {
            return ((Number) raw).longValue();
        }
        if (raw != null) {
            try {
                return Long.parseLong(String.valueOf(raw).trim());
            } catch (NumberFormatException ignored) {
                // 落到下面统一返回 -1
            }
        }
        return -1L;
    }

    /**
     * 兜底拦截下架商品：后台把商品下架后，前台若仍持有旧 ProductDetails（缓存/直链/系统推送），
     * 进入详情页时给用户友好提示并关闭页面，避免展示陈旧或越权数据，也避免依赖列表的过滤生效。
     *
     * @return true 表示商品可正常显示（继续 init）；false 表示已提示用户并 finish，调用方应停止后续流程。
     */
    private boolean ensureOnSaleOrFinish(ProductDetails pd) {
        if (pd == null) {
            Toast.makeText(this, "商品信息加载失败", Toast.LENGTH_SHORT).show();
            finish();
            return false;
        }
        if (!pd.isOnSale()) {
            Toast.makeText(this, "该商品已下架", Toast.LENGTH_SHORT).show();
            finish();
            return false;
        }
        return true;
    }

    /** 商品信息就绪后的统一初始化入口 */
    private void continueInit() {
        Toolbar toolbar = findViewById(R.id.tap_toolbar);
        toolbar.setOnClickListener(v -> finish());

        initData();
        initBanner();
        startAutoPlay();
        initRecyclerView();
        initReviewSection();

        ivAddToCart.setOnClickListener(v -> addToCart());
        btnGoToCart.setOnClickListener(v -> goToCart());
        btnSubmitReview.setOnClickListener(v -> submitReview());
        btnAiSummary.setOnClickListener(v -> triggerAiSummary());
        btnViewAllReviews.setOnClickListener(v -> {
            Intent intent = new Intent(ProductDetailsActivity.this, AllReviewsActivity.class);
            intent.putExtra(AllReviewsActivity.EXTRA_PRODUCT_ID, productDetails.getId());
            startActivity(intent);
        });
    }

    private void initViews() {
        viewPager2 = findViewById(R.id.viewPager2);
        indicatorLayout = findViewById(R.id.indocatorLayout);
        product_name = findViewById(R.id.product_name);
        product_price = findViewById(R.id.product_price);
        tvOriginalPrice = findViewById(R.id.tv_original_price);
        tvDiscount = findViewById(R.id.tv_discount);
        llDiscount = findViewById(R.id.ll_discount);
        rvPdItems = findViewById(R.id.rv_pd_items);
        ivAddToCart = findViewById(R.id.btn_add_to_car);
        btnGoToCart = findViewById(R.id.btn_go_to_cart);

        tvStock = findViewById(R.id.tv_stock);
        tvSales = findViewById(R.id.tv_sales);
        tvMerchantName = findViewById(R.id.tv_merchant_name);
        tvMerchantAddress = findViewById(R.id.tv_merchant_address);

        rvReviews = findViewById(R.id.rv_reviews);
        tvReviewCount = findViewById(R.id.tv_review_count);
        tvAvgRating = findViewById(R.id.tv_avg_rating);
        rbReview = findViewById(R.id.rb_review);
        etReviewContent = findViewById(R.id.et_review_content);
        btnSubmitReview = findViewById(R.id.btn_submit_review);
        btnAiSummary = findViewById(R.id.btn_ai_summary);
        btnViewAllReviews = findViewById(R.id.btn_view_all_reviews);
        aiSummaryContainer = findViewById(R.id.ai_summary_container);
        tvAiSummary = findViewById(R.id.tv_ai_summary);
        pbAiSummary = findViewById(R.id.pb_ai_summary);
        tvReviewHint = findViewById(R.id.tv_review_hint);
        reviewInputContainer = findViewById(R.id.review_input_container);
    }

    /** Goods → ProductDetails 映射 */
    private ProductDetails goodsToProductDetails(Goods g) {
        if (g == null) return null;
        ProductDetails pd = new ProductDetails();
        pd.setId(g.getGoods_id());
        pd.setName(g.getTitle());
        pd.setPrice(g.getPrice());
        pd.setPic(g.getImg());
        pd.setIntroduce(g.getDescription() != null ? g.getDescription() : ("标签:" + g.getTag()));
        pd.setBannerpics(g.getProductdetailsbanner());
        pd.setRvitempics(g.getProductdetailsrvitem());
        pd.setStock(g.getStock());
        pd.setMerchantName(g.getMerchantName());
        pd.setMerchantAddress(g.getMerchantAddress());
        pd.setDescription(g.getDescription());
        pd.setPriceValue(g.getPriceValue());
        pd.setOriginalPrice(g.getOriginalPrice());
        pd.setCategory(g.getCategory());
        pd.setSales(g.getSales());
        pd.setStatus(g.getStatus());
        return pd;
    }

    /**
     * 绑定价格区：现价先剥离数据中可能自带的货币符号（避免 "¥¥9.90"），
     * 由布局中的 "¥" 统一提供符号；原价以灰色划线与折扣标签展示，无优惠时隐藏整行。
     */
    private void bindPrice(ProductDetails pd) {
        String rawPrice = pd.getPrice();
        String suffix = PriceUtil.extractSuffix(rawPrice);
        product_price.setText(PriceUtil.stripSymbol(rawPrice));

        double priceValue = pd.getPriceValue();
        double originalPrice = pd.getOriginalPrice();
        if (PriceUtil.hasDiscount(priceValue, originalPrice)) {
            llDiscount.setVisibility(View.VISIBLE);
            tvOriginalPrice.setText(
                    PriceUtil.CURRENCY + PriceUtil.formatAmount(originalPrice) + suffix);
            tvOriginalPrice.setPaintFlags(
                    tvOriginalPrice.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            String discount = PriceUtil.formatDiscount(priceValue, originalPrice);
            tvDiscount.setText(discount == null ? "" : discount);
            tvDiscount.setVisibility(discount == null ? View.GONE : View.VISIBLE);
        } else {
            llDiscount.setVisibility(View.GONE);
        }
    }

    private void initBanner() {
        BannerAdapter bannerAdapter = new BannerAdapter(picList);
        viewPager2.setAdapter(bannerAdapter);
        createIndicators();

        viewPager2.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                currentPosition = position;
                updateIndicators();
            }
        });
    }

    private void createIndicators() {
        indicatorLayout.removeAllViews();
        if (picList.size() <= 1) return;

        for (int i = 0; i < picList.size(); i++) {
            ImageView indicator = new ImageView(this);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(10, 10);
            params.setMargins(5, 0, 5, 0);
            indicator.setLayoutParams(params);
            indicator.setBackgroundResource(i == currentPosition ? R.drawable.indicator_selected : R.drawable.indicator_normal);
            indicatorLayout.addView(indicator);
        }
    }

    private void updateIndicators() {
        for (int i = 0; i < indicatorLayout.getChildCount(); i++) {
            ImageView indicator = (ImageView) indicatorLayout.getChildAt(i);
            indicator.setBackgroundResource(i == currentPosition ? R.drawable.indicator_selected : R.drawable.indicator_normal);
        }
    }

    private void startAutoPlay() {
        if (picList.size() <= 1) return;

        timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                handler.post(() -> {
                    currentPosition = (currentPosition + 1) % picList.size();
                    viewPager2.setCurrentItem(currentPosition);
                });
            }
        }, 3000, 3000);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
        handler.removeCallbacksAndMessages(null);
    }

    private void initData() {
        setTitle(productDetails.getName());
        product_name.setText(productDetails.getName());
        bindPrice(productDetails);

        if (mUserId != -1 && productDetails != null) {
            String browseTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
            browseHistoryRepository.addBrowseHistory(mUserId, productDetails.getId(), browseTime);
        }

        tvStock.setText(String.valueOf(productDetails.getStock()));
        tvSales.setText(String.valueOf(productDetails.getSales()));

        String merchant = productDetails.getMerchantName();
        String merchantAddr = productDetails.getMerchantAddress();
        tvMerchantName.setText(merchant != null && !merchant.isEmpty() ? merchant : "大润发官方旗舰店");
        tvMerchantAddress.setText(merchantAddr != null && !merchantAddr.isEmpty() ? merchantAddr : "上海市浦东新区大润发总部");

        picList.clear();
        if (productDetails.getBannerpics() != null) {
            picList.addAll(productDetails.getBannerpics());
        }
    }

    /** 异步刷新最新库存与销量 */
    private void refreshStockFromDb() {
        if (productDetails == null) return;
        productRepository.getProductById(productDetails.getId(), new ApiCallback<Goods>() {
            @Override
            public void onSuccess(Goods data) {
                if (data != null) {
                    productDetails.setStock(data.getStock());
                    productDetails.setSales(data.getSales());
                }
                tvStock.setText(String.valueOf(productDetails.getStock()));
                tvSales.setText(String.valueOf(productDetails.getSales()));
            }

            @Override
            public void onError(int code, String message) {
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (productDetails != null) {
            refreshStockFromDb();
        }
    }

    private void initRecyclerView() {
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        rvPdItems.setLayoutManager(layoutManager);

        rvAdapter = new ProductImageAdapter(productDetails.getRvitempics());
        rvPdItems.setAdapter(rvAdapter);
    }

    private void initReviewSection() {
        LinearLayoutManager llm = new LinearLayoutManager(this);
        rvReviews.setLayoutManager(llm);
        rvReviews.setNestedScrollingEnabled(false);

        reviewAdapter = new ReviewAdapter(reviewList);
        reviewAdapter.setCurrentUserId(mUserId);
        reviewAdapter.setOnReplyClickListener(this::showReplyDialog);
        rvReviews.setAdapter(reviewAdapter);

        loadReviews();
    }

    /** 根据登录、评论状态显示/隐藏写评论区 */
    private void updateReviewInputVisibility() {
        if (mUserId == -1) {
            reviewInputContainer.setVisibility(View.GONE);
            tvReviewHint.setVisibility(View.GONE);
            return;
        }
        boolean reviewed = false;
        for (ReviewInfo r : fullReviewList) {
            if (r.getUserId() == mUserId) {
                reviewed = true;
                break;
            }
        }
        if (reviewed) {
            reviewInputContainer.setVisibility(View.GONE);
            tvReviewHint.setText("您已发表过评论, 可在评论区点击\"追评\"继续补充~");
            tvReviewHint.setVisibility(View.VISIBLE);
        } else {
            reviewInputContainer.setVisibility(View.VISIBLE);
            tvReviewHint.setVisibility(View.GONE);
        }
    }

    private void loadReviews() {
        int pid = productDetails.getId();
        reviewRepository.getReviewsByProduct(pid, new ApiCallback<List<ReviewInfo>>() {
            @Override
            public void onSuccess(List<ReviewInfo> all) {
                fullReviewList = (all != null) ? all : new ArrayList<>();

                reviewList.clear();
                int previewCount = Math.min(fullReviewList.size(), 3);
                for (int i = 0; i < previewCount; i++) {
                    reviewList.add(fullReviewList.get(i));
                }
                reviewAdapter.updateData(reviewList);

                int count = fullReviewList.size();
                double avg = 0;
                if (count > 0) {
                    double sum = 0;
                    for (ReviewInfo r : fullReviewList) sum += r.getRating();
                    avg = sum / count;
                }
                tvReviewCount.setText("(" + count + "条)");
                tvAvgRating.setText(String.format(Locale.getDefault(), "%.1f", avg));

                updateReviewInputVisibility();
            }

            @Override
            public void onError(int code, String message) {
                Toast.makeText(ProductDetailsActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void submitReview() {
        if (mUserId == -1) {
            Toast.makeText(this, "请先登录后再发表评论", Toast.LENGTH_SHORT).show();
            return;
        }

        String content = etReviewContent.getText().toString().trim();
        if (TextUtils.isEmpty(content)) {
            Toast.makeText(this, "请输入评论内容", Toast.LENGTH_SHORT).show();
            return;
        }

        float rating = rbReview.getRating();
        if (rating <= 0) {
            Toast.makeText(this, "请选择评分", Toast.LENGTH_SHORT).show();
            return;
        }

        reviewRepository.addReview(productDetails.getId(), rating, content, new ApiCallback<Boolean>() {
            @Override
            public void onSuccess(Boolean data) {
                Toast.makeText(ProductDetailsActivity.this, "评论发表成功", Toast.LENGTH_SHORT).show();
                etReviewContent.setText("");
                rbReview.setRating(5);
                loadReviews();
            }

            @Override
            public void onError(int code, String message) {
                Toast.makeText(ProductDetailsActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showReplyDialog(ReviewInfo review) {
        if (review == null) return;
        if (mUserId == -1 || review.getUserId() != mUserId) {
            Toast.makeText(this, "只有评论发布者才能追评", Toast.LENGTH_SHORT).show();
            return;
        }

        final EditText etReply = new EditText(this);
        etReply.setHint("请输入追评内容...");
        etReply.setMinLines(3);
        etReply.setGravity(android.view.Gravity.TOP);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        etReply.setPadding(pad, pad, pad, pad);

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("追评")
                .setView(etReply)
                .setPositiveButton("发表", (dialog, which) -> {
                    String content = etReply.getText().toString().trim();
                    if (TextUtils.isEmpty(content)) {
                        Toast.makeText(this, "追评内容不能为空", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    submitReply(review, content);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void submitReply(ReviewInfo review, String content) {
        if (review.getUserId() != mUserId) {
            Toast.makeText(this, "只有评论发布者才能追评", Toast.LENGTH_SHORT).show();
            return;
        }

        reviewRepository.addReviewReply(review.getId(), content, new ApiCallback<Boolean>() {
            @Override
            public void onSuccess(Boolean data) {
                Toast.makeText(ProductDetailsActivity.this, "追评成功", Toast.LENGTH_SHORT).show();
                loadReviews();
            }

            @Override
            public void onError(int code, String message) {
                Toast.makeText(ProductDetailsActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void triggerAiSummary() {
        pbAiSummary.setVisibility(View.VISIBLE);
        btnAiSummary.setEnabled(false);
        reviewRepository.getReviewsByProduct(productDetails.getId(), new ApiCallback<List<ReviewInfo>>() {
            @Override
            public void onSuccess(List<ReviewInfo> all) {
                if (all == null || all.isEmpty()) {
                    pbAiSummary.setVisibility(View.GONE);
                    btnAiSummary.setEnabled(true);
                    Toast.makeText(ProductDetailsActivity.this, "暂无评论, 无法生成总结", Toast.LENGTH_SHORT).show();
                    return;
                }

                // 组装评论文本列表，交给后端 AI 生成总结
                List<String> reviewTexts = new ArrayList<>();
                for (ReviewInfo r : all) {
                    reviewTexts.add(String.format(Locale.getDefault(), "【%.1f 星】%s", r.getRating(), r.getContent()));
                }

                ApiUtils.enqueue(
                        RetrofitClient.api().aiSummary(new AiSummaryRequest(productDetails.getName(), reviewTexts)),
                        new ApiCallback<AiSummaryResponse>() {
                            @Override
                            public void onSuccess(AiSummaryResponse data) {
                                pbAiSummary.setVisibility(View.GONE);
                                btnAiSummary.setEnabled(true);
                                String text = (data != null && data.summary != null)
                                        ? data.summary : "AI 未返回总结内容";
                                showAiSummary(text);
                            }

                            @Override
                            public void onError(int code, String message) {
                                pbAiSummary.setVisibility(View.GONE);
                                btnAiSummary.setEnabled(true);
                                Toast.makeText(ProductDetailsActivity.this, message, Toast.LENGTH_LONG).show();
                            }
                        });
            }

            @Override
            public void onError(int code, String message) {
                pbAiSummary.setVisibility(View.GONE);
                btnAiSummary.setEnabled(true);
                Toast.makeText(ProductDetailsActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showAiSummary(String text) {
        pbAiSummary.setVisibility(View.GONE);
        tvAiSummary.setText(text);
        aiSummaryContainer.setVisibility(View.VISIBLE);
    }

    private void addToCart() {
        if (productDetails == null) return;
        if (mUserId == -1) {
            Toast.makeText(this, "请先登录", Toast.LENGTH_SHORT).show();
            return;
        }

        int currentStock = productDetails.getStock();
        if (currentStock <= 0) {
            Toast.makeText(this, "商品已售罄", Toast.LENGTH_SHORT).show();
            return;
        }

        carRepository.addCar(productDetails.getId(), 1, new ApiCallback<Boolean>() {
            @Override
            public void onSuccess(Boolean data) {
                Toast.makeText(ProductDetailsActivity.this, "添加到购物车成功", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(int code, String message) {
                Toast.makeText(ProductDetailsActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void goToCart() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra("launch_cart", true);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }
}
