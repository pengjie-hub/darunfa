package com.example.myapplication.data;

import android.content.Context;

/**
 * 数据访问接口的服务定位器（Service Locator）。
 * <p>
 * 在 Android 中，Activity/Fragment 由系统反射构造，无法直接做构造注入；
 * 因此这里采用「字段注入」：UI 层在 onCreate/onViewCreated 中调用
 * {@code RepositoryProvider.getInstance(context).xxx()} 获取对应的仓储接口。
 * <p>
 * 商品 / 购物车 / 订单 / 评论 / 钱包已切换到远程后端；用户资料与浏览历史仍走本地 SQLite。
 */
public final class RepositoryProvider {

    private static volatile RepositoryProvider sInstance;

    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final ReviewRepository reviewRepository;
    private final CartRepository cartRepository;
    private final OrderRepository orderRepository;
    private final AddressRepository addressRepository;
    private final CouponRepository couponRepository;
    private final CsRepository csRepository;
    private final WalletRepository walletRepository;
    private final BrowseHistoryRepository browseHistoryRepository;
    private final SearchHistoryRepository searchHistoryRepository;

    private RepositoryProvider(Context context) {
        Context app = context.getApplicationContext();
        SqliteDataRepository local = new SqliteDataRepository(app);
        this.userRepository = local;
        this.browseHistoryRepository = local;
        this.searchHistoryRepository = new SharedPreferencesSearchHistoryRepository(app);

        // 以下各类数据统一走远程后端（前后端共用 MySQL）
        this.productRepository = new RemoteProductRepository();
        this.reviewRepository = new RemoteReviewRepository();
        this.cartRepository = new RemoteCartRepository();
        this.orderRepository = new RemoteOrderRepository();
        this.addressRepository = new RemoteAddressRepository();
        this.couponRepository = new RemoteCouponRepository();
        this.csRepository = new RemoteCsRepository();
        this.walletRepository = new RemoteWalletRepository();
    }

    public static RepositoryProvider getInstance(Context context) {
        if (sInstance == null) {
            synchronized (RepositoryProvider.class) {
                if (sInstance == null) {
                    sInstance = new RepositoryProvider(context);
                }
            }
        }
        return sInstance;
    }

    public UserRepository user() {
        return userRepository;
    }

    public ProductRepository product() {
        return productRepository;
    }

    public ReviewRepository review() {
        return reviewRepository;
    }

    public CartRepository cart() {
        return cartRepository;
    }

    public OrderRepository order() {
        return orderRepository;
    }

    public AddressRepository address() {
        return addressRepository;
    }

    public CouponRepository coupon() {
        return couponRepository;
    }

    public CsRepository cs() {
        return csRepository;
    }

    public WalletRepository wallet() {
        return walletRepository;
    }

    public BrowseHistoryRepository browseHistory() {
        return browseHistoryRepository;
    }

    public SearchHistoryRepository searchHistory() {
        return searchHistoryRepository;
    }
}
