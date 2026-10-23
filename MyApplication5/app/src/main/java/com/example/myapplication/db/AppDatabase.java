package com.example.myapplication.db;

import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.text.TextUtils;
import android.util.Log;

import com.example.myapplication.entity.CarInfo;
import com.example.myapplication.entity.Goods;
import com.example.myapplication.entity.OrderInfo;
import com.example.myapplication.entity.ReviewInfo;
import com.example.myapplication.entity.ReviewReply;
import com.example.myapplication.entity.UserInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * 统一数据库管理类 app.db
 * <p>
 * 将原先分散在 user.db / product_db / car_db / order_db 四个独立文件中的
 * 用户、商品、评论、购物车、订单数据合并到同一个数据库文件中,
 * 并新增浏览历史表(browse_history)。
 * <p>
 * 优化点:
 * 1. 单例 + ApplicationContext, 避免重复打开数据库
 * 2. 首次 seed 数据使用事务批量插入, 提升初始化速度
 * 3. 所有写操作加 synchronized, 保证线程安全
 * 4. 高频查询字段建立索引
 * 5. 浏览历史自动去重(同用户同商品只更新时间)并限制保留条数
 */
public class AppDatabase extends SQLiteOpenHelper {
    private static final String TAG = "AppDatabase";
    private static final String DB_NAME = "app.db";
    private static final int VERSION = 8;

    // ==================== 订单状态常量(原 OrderDatabase) ====================
    public static final String STATUS_PENDING_SHIP = "待发货";
    public static final String STATUS_PENDING_RECEIVE = "待收货";
    public static final String STATUS_PENDING_REVIEW = "待评价";
    public static final String STATUS_DONE = "已完成";
    public static final String STATUS_RETURNED = "已退货";

    // ==================== 表名 ====================
    private static final String TABLE_USER = "user_table";
    private static final String TABLE_PRODUCT = "product_table";
    private static final String TABLE_REVIEW = "review_table";
    private static final String TABLE_REVIEW_REPLY = "review_reply_table";
    private static final String TABLE_CAR = "car_table";
    private static final String TABLE_ORDER = "order_table";
    private static final String TABLE_BROWSE = "browse_history";

    // ==================== 单例 ====================
    private static volatile AppDatabase sInstance;

    public static synchronized AppDatabase getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new AppDatabase(context.getApplicationContext());
        }
        return sInstance;
    }

    private AppDatabase(Context context) {
        super(context, DB_NAME, null, VERSION);
    }

    // ==================== 建表 ====================
    @Override
    public void onCreate(SQLiteDatabase db) {
        // 用户表
        db.execSQL("CREATE TABLE " + TABLE_USER + " (" +
                "user_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "username TEXT UNIQUE, " +
                "password TEXT, " +
                "phone TEXT, " +
                "avatar TEXT)");

        // 商品表
        db.execSQL("CREATE TABLE " + TABLE_PRODUCT + " (" +
                "_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "name TEXT, " +
                "price REAL, " +
                "price_display TEXT, " +
                "original_price REAL, " +
                "image TEXT, " +
                "tag TEXT, " +
                "category TEXT, " +
                "subcategory TEXT, " +
                "section TEXT, " +
                "stock INTEGER, " +
                "sales INTEGER, " +
                "merchant_name TEXT, " +
                "merchant_address TEXT, " +
                "description TEXT)");

        // 评论表(初始评论): 同一用户对同一商品仅允许一条初始评论
        db.execSQL("CREATE TABLE " + TABLE_REVIEW + " (" +
                "_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "product_id INTEGER, " +
                "user_id INTEGER, " +
                "user_name TEXT, " +
                "rating REAL, " +
                "content TEXT, " +
                "created_at TEXT, " +
                "UNIQUE(user_id, product_id))");

        // 追评表: 关联初始评论(_id), 同一用户可对自己的初始评论追评不限次数
        db.execSQL("CREATE TABLE " + TABLE_REVIEW_REPLY + " (" +
                "_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "review_id INTEGER, " +
                "user_id INTEGER, " +
                "user_name TEXT, " +
                "content TEXT, " +
                "created_at TEXT)");

        // 购物车表
        db.execSQL("CREATE TABLE " + TABLE_CAR + " (" +
                "_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "product_id INTEGER, " +
                "product_title TEXT, " +
                "product_price TEXT, " +
                "product_count INTEGER, " +
                "product_img INTEGER, " +
                "imagePath TEXT, " +
                "selected INTEGER DEFAULT 1, " +
                "user_id INTEGER, " +
                "phone TEXT, " +
                "address TEXT)");

        // 订单表
        db.execSQL("CREATE TABLE " + TABLE_ORDER + " (" +
                "order_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "user_id INTEGER, " +
                "product_id INTEGER, " +
                "product_title TEXT, " +
                "product_price TEXT, " +
                "product_count INTEGER, " +
                "product_img INTEGER, " +
                "imagePath TEXT, " +
                "phone TEXT, " +
                "address TEXT, " +
                "order_time TEXT, " +
                "status TEXT DEFAULT '" + STATUS_PENDING_SHIP + "')");

        // 浏览历史表
        db.execSQL("CREATE TABLE " + TABLE_BROWSE + " (" +
                "_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "user_id INTEGER, " +
                "product_id INTEGER, " +
                "browse_time TEXT, " +
                "UNIQUE(user_id, product_id))");

        // ==================== 索引(性能优化) ====================
        db.execSQL("CREATE INDEX idx_product_category ON " + TABLE_PRODUCT + "(category)");
        db.execSQL("CREATE INDEX idx_product_section ON " + TABLE_PRODUCT + "(section)");
        db.execSQL("CREATE INDEX idx_product_subcategory ON " + TABLE_PRODUCT + "(subcategory)");
        db.execSQL("CREATE INDEX idx_review_product ON " + TABLE_REVIEW + "(product_id)");
        db.execSQL("CREATE INDEX idx_review_reply_review ON " + TABLE_REVIEW_REPLY + "(review_id)");
        db.execSQL("CREATE INDEX idx_car_user ON " + TABLE_CAR + "(user_id)");
        db.execSQL("CREATE INDEX idx_order_user ON " + TABLE_ORDER + "(user_id)");
        db.execSQL("CREATE INDEX idx_order_status ON " + TABLE_ORDER + "(status)");
        db.execSQL("CREATE INDEX idx_browse_user ON " + TABLE_BROWSE + "(user_id)");

        // 初始种子数据(事务批量插入)
        seedProducts(db);
        seedDiscounts(db);
        seedReviews(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // v6 之前的旧版本(分库结构): 需要删表重建
        if (oldVersion < 6) {
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_USER);
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_PRODUCT);
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_REVIEW);
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_REVIEW_REPLY);
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_CAR);
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_ORDER);
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_BROWSE);
            onCreate(db);
            return;
        }

        // v6 → v7: 增量插入紫皮大蒜(p055)的种子评论, 保留已有用户/购物车/订单等数据
        if (oldVersion < 7) {
            seedP055Reviews(db);
        }

        // v7 → v8: 商品表新增 original_price 列(原价, 用于前台展示划线原价与折扣), 保留已有数据
        if (oldVersion < 8) {
            try {
                db.execSQL("ALTER TABLE " + TABLE_PRODUCT + " ADD COLUMN original_price REAL");
            } catch (Exception e) {
                Log.w(TAG, "添加 original_price 列失败(可能已存在): " + e.getMessage());
            }
            // 给本地种子商品补充演示用原价(仅填补空值, 不覆盖已有配置)
            seedDiscounts(db);
        }
    }

    // ==================== 种子数据 ====================
    private void seedProducts(SQLiteDatabase db) {
        Object[][] data = {
                {"智利车厘子", 49.8, "49.8/盒", "p001", "时令", "水果鲜花", "爆款推荐", "recommend", 200, 1560, "大润发生鲜自营店", "上海市浦东新区大润发总部", "智利进口车厘子,新鲜直达,J级大果"},
                {"烟台红富士苹果", 10.0, "10/斤", "p002", "推荐", "水果鲜花", "爆款推荐", "recommend", 500, 2300, "山东烟台果农直供", "山东省烟台市栖霞市", "烟台正宗红富士,脆甜可口"},
                {"赣南脐橙", 19.9, "19.9/箱", "p003", "热销", "水果鲜花", "上新了", "hot", 300, 980, "江西赣州果园", "江西省赣州市信丰县", "赣南正宗脐橙,皮薄多汁"},
                {"云南红玫瑰", 39.9, "39.9/束", "p004", "新品", "水果鲜花", "水果礼盒", "new", 100, 320, "云南鲜花直供", "云南省昆明市斗南花市", "云南高原红玫瑰,20支装"},
                {"海南金钻凤梨", 29.9, "29.9/个", "p005", "热销", "水果鲜花", "爆款推荐", "hot", 250, 760, "海南热带水果店", "海南省海口市", "海南金钻凤梨,免削皮免泡盐水"},
                {"突尼斯软籽石榴", 35.8, "35.8/箱", "p006", "新品", "水果鲜花", "上新了", "new", 180, 410, "四川会理石榴园", "四川省凉山州会理县", "突尼斯软籽石榴,籽软可食"},
                {"有机白菜", 3.5, "3.5/斤", "p007", "推荐", "蔬菜豆制品", "爆款推荐", "recommend", 800, 3200, "本地蔬菜合作社", "上海市崇明区", "崇明本地有机白菜"},
                {"嫩豆腐", 4.0, "4/盒", "p008", "推荐", "蔬菜豆制品", "上新了", "recommend", 400, 1800, "豆制品作坊", "上海市闵行区", "现做嫩豆腐,豆香浓郁"},
                {"新土豆", 2.5, "2.5/斤", "p009", "热销", "蔬菜豆制品", "爆款推荐", "hot", 1000, 2500, "河北土豆基地", "河北省张家口市", "新土豆,淀粉含量高"},
                {"有机西红柿", 5.5, "5.5/斤", "p010", "新品", "蔬菜豆制品", "健康轻食", "new", 600, 980, "本地农场", "上海市青浦区", "自然成熟西红柿"},
                {"澳洲和牛上脑", 169.3, "169.3/份", "p011", "热销", "肉蛋水产", "爆款推荐", "hot", 80, 560, "进口肉类专营", "上海市浦东新区冷链仓", "澳洲M5级和牛上脑,3kg装"},
                {"散养土鸡蛋", 25.0, "25/箱", "p012", "推荐", "肉蛋水产", "上新了", "recommend", 300, 2100, "苏北散养鸡场", "江苏省盐城市", "30枚装散养土鸡蛋"},
                {"大连海参", 199.0, "199/只", "p013", "新品", "肉蛋水产", "健康轻食", "new", 50, 180, "大连海鲜直供", "辽宁省大连市", "大连即食海参,单人装"},
                {"阳澄湖大闸蟹", 59.9, "59.9/只", "p014", "时令", "肉蛋水产", "爆款推荐", "hot", 120, 880, "阳澄湖蟹农直供", "江苏省苏州市阳澄湖", "阳澄湖正宗大闸蟹,公蟹4两"},
                {"特仑苏纯牛奶", 65.0, "65/箱", "p015", "推荐", "乳品烘焙", "爆款推荐", "recommend", 250, 1800, "蒙牛乳业", "内蒙古呼和浩特市", "特仑苏纯牛奶整箱装"},
                {"现烤吐司面包", 12.0, "12/袋", "p016", "热销", "乳品烘焙", "上新了", "hot", 200, 980, "烘焙工坊", "上海市闵行区", "每日现烤吐司,松软可口"},
                {"法式生日蛋糕", 168.0, "168/个", "p017", "新品", "乳品烘焙", "上新了", "new", 30, 150, "蛋糕工坊", "上海市徐汇区", "8寸法式生日蛋糕,可定制"},
                {"手工鲜肉包", 8.0, "8/4个", "p018", "推荐", "熟食面点", "招牌必点", "recommend", 300, 2200, "面点工坊", "上海市浦东新区", "手工制作鲜肉包,4个装"},
                {"黑猪肉饺子", 18.0, "18/袋", "p019", "热销", "熟食面点", "上新了", "hot", 200, 1200, "饺子工坊", "上海市闵行区", "黑猪肉馅饺子,30只装"},
                {"红糖馒头", 6.0, "6/袋", "p020", "新品", "熟食面点", "新品尝鲜", "new", 400, 980, "面点工坊", "上海市闵行区", "红糖馒头,4个装"},
                {"速冻水饺", 15.9, "15.9/袋", "p021", "推荐", "速冻鲜食", "爆款推荐", "recommend", 500, 3200, "速冻食品厂", "上海市松江区", "速冻水饺,500g装"},
                {"哈根达斯冰淇淋", 89.0, "89/桶", "p022", "热销", "速冻鲜食", "上新了", "hot", 200, 1500, "进口食品", "上海市浦东新区", "哈根达斯冰淇淋,473ml"},
                {"法国干红葡萄酒", 199.0, "199/瓶", "p023", "热销", "酒水饮料", "爆款推荐", "hot", 150, 980, "酒类专营", "上海市黄浦区", "法国进口AOC干红葡萄酒"},
                {"茅台飞天", 1499.0, "1499/瓶", "p024", "推荐", "酒水饮料", "润发甄选", "recommend", 50, 320, "贵州茅台直营", "贵州省仁怀市茅台镇", "53度飞天茅台500ml"},
                {"汇源橙汁", 12.5, "12.5/瓶", "p025", "新品", "酒水饮料", "上新了", "new", 600, 1800, "汇源果汁", "北京市顺义区", "汇源100%橙汁1L装"},
                {"混合坚果大礼包", 49.9, "49.9/箱", "p026", "热销", "休闲零食", "爆款推荐", "hot", 300, 2300, "零食工坊", "上海市闵行区", "每日坚果7日装"},
                {"进口薯片", 19.9, "19.9/箱", "p027", "新品", "休闲零食", "上新了", "new", 500, 1200, "进口零食", "上海市浦东新区", "进口原味薯片大包装"},
                {"手撕面包", 9.9, "9.9/袋", "p028", "推荐", "休闲零食", "上新了", "recommend", 800, 3500, "烘焙食品厂", "上海市松江区", "手撕面包,800g装"},
                {"五常大米", 59.9, "59.9/袋", "p029", "推荐", "粮油调味", "爆款推荐", "recommend", 400, 2800, "五常大米直供", "黑龙江省五常市", "五常稻花香大米10斤装"},
                {"金龙鱼食用油", 79.9, "79.9/桶", "p030", "热销", "粮油调味", "爆款推荐", "hot", 300, 1900, "粮油公司", "上海市浦东新区", "金龙鱼5L非转基因食用油"},
                {"海天老抽酱油", 12.0, "12/瓶", "p031", "新品", "粮油调味", "上新了", "new", 600, 1500, "海天调味", "广东省佛山市", "海天老抽王1.9L装"},
                {"立白洗衣液", 39.9, "39.9/袋", "p032", "推荐", "家清纸品", "爆款推荐", "recommend", 350, 2100, "立白日化", "广东省广州市", "立白洗衣液12斤装"},
                {"清风原木抽纸", 29.9, "29.9/箱", "p033", "热销", "家清纸品", "上新了", "hot", 500, 2300, "清风纸业", "上海市浦东新区", "清风原木抽纸整箱装"},
                {"Arcteryx始祖鸟冲锋衣", 4099.99, "4099.99/件", "p034", "热销", "独家专卖", "润发甄选", "hot", 20, 150, "始祖鸟中国", "上海市静安区", "Arcteryx Macai刺绣冲锋衣"},
                {"小米17 Pro Max", 5689.0, "5689/台", "p035", "新品", "独家专卖", "上新了", "new", 50, 880, "小米科技", "北京市海淀区", "小米17 Pro Max 16+512G"},
                {"Apple iPhone Air", 7999.0, "7999/台", "p036", "热销", "独家专卖", "润发甄选", "hot", 80, 1200, "Apple中国", "上海市浦东新区", "Apple iPhone Air eSIM版"},
                {"Sony Vlog相机", 3452.7, "3452.7/台", "p037", "推荐", "独家专卖", "润发甄选", "recommend", 30, 410, "索尼中国", "上海市徐汇区", "Sony ZV-1口袋Vlog相机"},
                {"雪花肥牛卷", 39.9, "39.9/盒", "p038", "热销", "火锅到家", "爆款推荐", "hot", 200, 1800, "火锅食材店", "上海市闵行区", "雪花肥牛卷250g装"},
                {"海底捞火锅底料", 19.9, "19.9/袋", "p039", "推荐", "火锅到家", "上新了", "recommend", 500, 2300, "海底捞食品", "四川省简阳市", "海底捞番茄味火锅底料"},
                {"真皮手提包", 599.0, "599/个", "p040", "推荐", "家纺鞋服", "润发甄选", "recommend", 60, 320, "皮具工坊", "上海市闵行区", "头层牛皮手提包"},
                {"时尚单肩包", 459.0, "459/个", "p041", "新品", "家纺鞋服", "上新了", "new", 80, 280, "箱包店", "上海市静安区", "时尚单肩包,多色可选"},
                {"情侣戒指对装", 69.8, "69.8/对", "p042", "热销", "家纺鞋服", "满两件79折", "hot", 100, 580, "饰品工坊", "上海市黄浦区", "日月星辰情侣戒指对装"},
                {"沉积炉", 29998.0, "29998/台", "p043", "新品", "百货家电", "上新了", "new", 10, 30, "工业设备", "上海市浦东新区", "工业沉积炉设备"},
                {"Canon照片打印机", 1079.82, "1079.82/台", "p044", "推荐", "百货家电", "润发甄选", "recommend", 40, 180, "佳能中国", "上海市浦东新区", "Canon CP1500照片打印机"},
                {"大疆Pocket3云台相机", 2565.0, "2565/台", "p045", "热销", "百货家电", "爆款推荐", "hot", 30, 320, "大疆创新", "广东省深圳市", "DJI Pocket3一英寸口袋云台"},
                {"小米平板", 594.0, "594/台", "p046", "新品", "百货家电", "上新了", "new", 80, 580, "小米科技", "北京市海淀区", "小米Pad 11英寸影音娱乐平板"},
                {"进口巧克力礼盒", 89.0, "89/盒", "p047", "热销", "全球购", "爆款推荐", "hot", 200, 1200, "进口食品", "上海市浦东新区", "比利时进口巧克力礼盒"},
                {"进口宠物安抚衣", 199.0, "199/件", "p048", "新品", "全球购", "上新了", "new", 100, 320, "宠物用品店", "上海市闵行区", "进口宠物安抚衣"},
                {"进口猫窝", 159.0, "159/个", "p049", "推荐", "全球购", "润发甄选", "recommend", 150, 480, "宠物用品店", "上海市闵行区", "进口保暖猫窝"},
                {"进口空气清新剂", 39.9, "39.9/瓶", "p050", "推荐", "全球购", "润发甄选", "recommend", 300, 980, "进口日化", "上海市浦东新区", "进口香氛空气清新剂"},
                // ===== 新增商品数据 =====
                {"新疆和田大枣", 29.9, "29.9/斤", "p051", "热销", "水果鲜花", "爆款推荐", "hot", 600, 2400, "新疆果农直供", "新疆和田市", "新疆和田大枣,肉厚核小"},
                {"泰国金枕榴莲", 89.0, "89/斤", "p052", "时令", "水果鲜花", "上新了", "hot", 100, 680, "泰国进口水果", "上海市浦东新区冷链仓", "泰国金枕榴莲,香甜软糯"},
                {"广西沃柑", 15.8, "15.8/斤", "p053", "推荐", "水果鲜花", "爆款推荐", "recommend", 500, 1900, "广西沃柑基地", "广西武鸣区", "广西武鸣沃柑,皮薄多汁"},
                {"蓝莓鲜果", 49.9, "49.9/盒", "p054", "新品", "水果鲜花", "健康轻食", "new", 300, 880, "云南蓝莓园", "云南澄江市", "云南高山蓝莓,125g装"},
                {"紫皮大蒜", 8.9, "8.9/斤", "p055", "推荐", "蔬菜豆制品", "爆款推荐", "recommend", 1000, 3600, "山东金乡", "山东省金乡县", "山东金乡紫皮大蒜"},
                {"老姜", 6.5, "6.5/斤", "p056", "热销", "蔬菜豆制品", "上新了", "hot", 800, 2100, "本地蔬菜基地", "上海市崇明区", "老姜,辛辣味浓"},
                {"黄瓜", 4.5, "4.5/斤", "p057", "推荐", "蔬菜豆制品", "爆款推荐", "recommend", 700, 2600, "本地农场", "上海市青浦区", "本地黄瓜,清脆爽口"},
                {"三文鱼刺身", 79.0, "79/份", "p058", "新品", "肉蛋水产", "健康轻食", "new", 80, 420, "进口海鲜", "上海市浦东新区冷链仓", "挪威进口三文鱼刺身,200g"},
                {"黑虎虾", 59.9, "59.9/盒", "p059", "热销", "肉蛋水产", "爆款推荐", "hot", 150, 880, "越南进口", "上海市浦东新区冷链仓", "越南黑虎虾,400g装"},
                {"光明鲜牛奶", 18.9, "18.9/瓶", "p060", "推荐", "乳品烘焙", "爆款推荐", "recommend", 400, 2200, "光明乳业", "上海市闵行区", "光明优倍鲜牛奶,950ml"},
                {"提拉米苏蛋糕", 58.0, "58/个", "p061", "新品", "乳品烘焙", "上新了", "new", 100, 560, "蛋糕工坊", "上海市徐汇区", "意式提拉米苏,6寸"},
                {"流沙包", 12.0, "12/4个", "p062", "热销", "熟食面点", "招牌必点", "hot", 300, 1600, "面点工坊", "上海市浦东新区", "广式流沙包,4个装"},
                {"葱油饼", 9.9, "9.9/袋", "p063", "推荐", "熟食面点", "上新了", "recommend", 400, 1800, "面点工坊", "上海市闵行区", "葱油饼,5片装"},
                {"湾仔码头云吞", 25.9, "25.9/袋", "p064", "热销", "速冻鲜食", "爆款推荐", "hot", 350, 1700, "湾仔码头", "上海市松江区", "湾仔码头鲜虾云吞,600g"},
                {"梦龙冰淇淋", 39.9, "39.9/盒", "p065", "新品", "速冻鲜食", "上新了", "new", 300, 1400, "联合利华", "上海市浦东新区", "梦龙松露巧克力冰淇淋,4支装"},
                {"青岛啤酒", 5.5, "5.5/瓶", "p066", "推荐", "酒水饮料", "爆款推荐", "recommend", 800, 3500, "青岛啤酒", "山东省青岛市", "青岛经典啤酒500ml"},
                {"元气森林气泡水", 5.0, "5/瓶", "p067", "热销", "酒水饮料", "上新了", "hot", 600, 2100, "元气森林", "北京市顺义区", "元气森林白桃味气泡水480ml"},
                {"良品铺子猪肉脯", 29.9, "29.9/袋", "p068", "推荐", "休闲零食", "爆款推荐", "recommend", 500, 2400, "良品铺子", "湖北省武汉市", "良品铺子猪肉脯,200g"},
                {"三只松鼠夏威夷果", 39.9, "39.9/袋", "p069", "新品", "休闲零食", "上新了", "new", 400, 1600, "三只松鼠", "安徽省芜湖市", "三只松鼠夏威夷果,265g"},
                {"金龙鱼大米", 49.9, "49.9/袋", "p070", "热销", "粮油调味", "爆款推荐", "hot", 350, 2000, "金龙鱼", "上海市浦东新区", "金龙鱼东北大米5kg"},
                {"老干妈辣椒酱", 11.9, "11.9/瓶", "p071", "推荐", "粮油调味", "爆款推荐", "recommend", 500, 2800, "老干妈", "贵州省贵阳市", "老干妈风味豆豉油辣椒,280g"},
                {"维达抽纸", 39.9, "39.9/箱", "p072", "热销", "家清纸品", "爆款推荐", "hot", 400, 1900, "维达纸业", "广东省江门市", "维达超韧抽纸整箱装"},
                {"蓝月亮洗衣液", 49.9, "49.9/瓶", "p073", "推荐", "家清纸品", "上新了", "recommend", 300, 1500, "蓝月亮", "广东省广州市", "蓝月亮深层洁净洗衣液,3kg"},
                {"Nike运动鞋", 699.0, "699/双", "p074", "热销", "家纺鞋服", "润发甄选", "hot", 100, 980, "Nike中国", "上海市静安区", "Nike Air Max运动鞋"},
                {"三枪保暖内衣", 159.0, "159/套", "p075", "新品", "家纺鞋服", "上新了", "new", 200, 680, "三枪内衣", "上海市浦东新区", "三枪纯棉保暖内衣套装"},
                {"美的电饭煲", 399.0, "399/台", "p076", "热销", "百货家电", "爆款推荐", "hot", 120, 760, "美的电器", "广东省佛山市", "美的智能电饭煲4L"},
                {"苏泊尔炒锅", 199.0, "199/个", "p077", "推荐", "百货家电", "润发甄选", "recommend", 150, 540, "苏泊尔", "浙江省杭州市", "苏泊尔不粘炒锅,32cm"},
                {"乐高积木玩具", 299.0, "299/套", "p078", "新品", "母婴玩具", "上新了", "new", 100, 420, "乐高中国", "上海市浦东新区", "乐高经典创意积木套装"},
                {"费雪早教机", 199.0, "199/台", "p079", "热销", "母婴玩具", "爆款推荐", "hot", 120, 380, "费雪", "广东省深圳市", "费雪双语早教学习机"},
                {"进口橄榄油", 89.0, "89/瓶", "p080", "推荐", "全球购", "润发甄选", "recommend", 250, 1100, "西班牙进口", "上海市浦东新区", "西班牙特级初榨橄榄油,750ml"},
                {"进口红酒礼盒", 268.0, "268/盒", "p081", "热销", "全球购", "爆款推荐", "hot", 100, 560, "法国进口", "上海市黄浦区", "法国波尔多红酒礼盒,双支装"}
        };

        db.beginTransaction();
        try {
            for (Object[] row : data) {
                ContentValues cv = new ContentValues();
                cv.put("name", (String) row[0]);
                cv.put("price", (Double) row[1]);
                cv.put("price_display", (String) row[2]);
                cv.put("image", (String) row[3]);
                cv.put("tag", (String) row[4]);
                cv.put("category", (String) row[5]);
                cv.put("subcategory", (String) row[6]);
                cv.put("section", (String) row[7]);
                cv.put("stock", (Integer) row[8]);
                cv.put("sales", (Integer) row[9]);
                cv.put("merchant_name", (String) row[10]);
                cv.put("merchant_address", (String) row[11]);
                cv.put("description", (String) row[12]);
                db.insert(TABLE_PRODUCT, null, cv);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    /**
     * 给本地种子商品补充"原价"，用于前台展示优惠（划线原价 + 折扣标签）。
     *
     * <p>仅在 {@code original_price} 为空或 ≤0 时写入，幂等且不覆盖已配置的值；
     * 未列入下表配置的商品保持无优惠（前台自动隐藏原价与折扣行）。
     */
    private void seedDiscounts(SQLiteDatabase db) {
        // {商品 _id, 原价}
        Object[][] discounts = {
                {1, 69.8},    // 智利车厘子      49.8 → 7.1折
                {2, 12.9},    // 烟台红富士苹果  10.0 → 7.8折
                {3, 25.9},    // 赣南脐橙        19.9 → 7.7折
                {5, 39.9},    // 海南金钻凤梨    29.9 → 7.5折
                {6, 45.8},    // 突尼斯软籽石榴  35.8 → 7.8折
                {11, 219.0},  // 澳洲和牛上脑    169.3 → 7.7折
                {13, 259.0},  // 大连海参        199.0 → 7.7折
                {15, 79.0},   // 特仑苏纯牛奶    65.0 → 8.2折
                {17, 199.0},  // 法式生日蛋糕    168.0 → 8.4折
                {22, 109.0},  // 哈根达斯冰淇淋  89.0 → 8.2折
                {24, 1699.0}, // 茅台飞天        1499.0 → 8.8折
                {26, 59.9},   // 混合坚果大礼包  49.9 → 8.3折
                {57, 5.9},    // 黄瓜            4.5 → 7.6折
                {60, 23.9},   // 光明鲜牛奶      18.9 → 7.9折
                {74, 899.0},  // Nike运动鞋      699.0 → 7.8折
        };
        db.beginTransaction();
        try {
            for (Object[] row : discounts) {
                ContentValues cv = new ContentValues();
                cv.put("original_price", (Double) row[1]);
                db.update(TABLE_PRODUCT, cv,
                        "_id = ? AND (original_price IS NULL OR original_price <= 0)",
                        new String[]{String.valueOf(row[0])});
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    private void seedReviews(SQLiteDatabase db) {
        Object[][] data = {
                {1, 1, "张三", 5.0, "车厘子很新鲜,个头大,甜度高,包装也很好,下次还会回购!", "2026-08-15 14:23"},
                {1, 2, "李四", 4.5, "整体不错,就是物流稍微慢了点,果子还是很新鲜的。", "2026-08-16 09:15"},
                {1, 3, "王五", 5.0, "比超市便宜很多,品质有保障,家人都爱吃。", "2026-08-18 19:42"},
                {2, 1, "张三", 4.0, "苹果很脆,但是有的有点酸,总体还可以。", "2026-08-10 11:20"},
                {2, 4, "赵六", 5.0, "正宗烟台苹果,水分足,运输包装也很好。", "2026-08-12 16:35"},
                {3, 2, "李四", 5.0, "脐橙很甜,皮薄多汁,家里小孩抢着吃。", "2026-08-20 10:00"},
                {11, 5, "钱七", 4.5, "和牛品质很好,煎出来的口感一级棒,价格略贵但值。", "2026-08-22 18:30"},
                {34, 1, "张三", 5.0, "始祖鸟冲锋衣做工精致,防水性能一流,值得入手。", "2026-08-05 13:10"},
                {36, 4, "赵六", 4.0, "iPhone Air很轻薄,eSIM有点不方便,其他都很好。", "2026-08-25 20:00"},
                {44, 2, "李四", 5.0, "打印机效果不错,色彩还原度高,适合家庭使用。", "2026-08-19 15:45"},
                // ===== 紫皮大蒜(p055, product_id=55) =====
                {55, 6, "孙八", 5.0, "紫皮大蒜很新鲜,蒜瓣饱满,辣味足,炒菜炝锅特别香,下次还回购。", "2026-09-01 10:20"},
                {55, 7, "周九", 4.5, "蒜头个头不算大但很实,皮薄好剥,就是物流慢了两天,整体满意。", "2026-09-02 15:40"},
                {55, 8, "吴十", 5.0, "正宗金乡紫皮蒜,比超市的香多了,做蒜蓉蒸虾味道绝了。", "2026-09-03 09:15"},
                {55, 9, "郑十一", 4.0, "质量不错,有个别蒜瓣偏干,性价比可以,会回购。", "2026-09-03 20:05"}
        };
        db.beginTransaction();
        try {
            for (Object[] row : data) {
                ContentValues cv = new ContentValues();
                cv.put("product_id", (Integer) row[0]);
                cv.put("user_id", (Integer) row[1]);
                cv.put("user_name", (String) row[2]);
                cv.put("rating", (Double) row[3]);
                cv.put("content", (String) row[4]);
                cv.put("created_at", (String) row[5]);
                db.insert(TABLE_REVIEW, null, cv);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    /**
     * v6 → v7 增量种子评论: 紫皮大蒜(p055, product_id=55)
     * <p>
     * 仅在数据库升级时调用一次, 用于给已安装用户补齐新评论, 不清空其它数据。
     * 使用 INSERT OR IGNORE, 幂等(重复执行不会插入重复评论)。
     */
    private void seedP055Reviews(SQLiteDatabase db) {
        Object[][] data = {
                {55, 6, "孙八", 5.0, "紫皮大蒜很新鲜,蒜瓣饱满,辣味足,炒菜炝锅特别香,下次还回购。", "2026-09-01 10:20"},
                {55, 7, "周九", 4.5, "蒜头个头不算大但很实,皮薄好剥,就是物流慢了两天,整体满意。", "2026-09-02 15:40"},
                {55, 8, "吴十", 5.0, "正宗金乡紫皮蒜,比超市的香多了,做蒜蓉蒸虾味道绝了。", "2026-09-03 09:15"},
                {55, 9, "郑十一", 4.0, "质量不错,有个别蒜瓣偏干,性价比可以,会回购。", "2026-09-03 20:05"}
        };
        db.beginTransaction();
        try {
            for (Object[] row : data) {
                ContentValues cv = new ContentValues();
                cv.put("product_id", (Integer) row[0]);
                cv.put("user_id", (Integer) row[1]);
                cv.put("user_name", (String) row[2]);
                cv.put("rating", (Double) row[3]);
                cv.put("content", (String) row[4]);
                cv.put("created_at", (String) row[5]);
                db.insertWithOnConflict(TABLE_REVIEW, null, cv, SQLiteDatabase.CONFLICT_IGNORE);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    // ====================================================================
    // ======================== 用户模块 (原 UserDbHelper) =================
    // ====================================================================

    /**
     * 同步后端用户到本地 user_table(幂等写入)。
     * <p>
     * 用于登录/注册成功后, 把后端返回的 userId + username 落到本地,
     * 这样 {@link #getUserById(int)} 才能查到该用户, 避免
     * ProfileEditActivity 等页面出现"用户信息加载失败"。
     * <p>
     * 实现要点:
     * <ul>
     *   <li>使用 {@code INSERT OR IGNORE} + 显式 user_id, 已存在则跳过;</li>
     *   <li>phone/avatar 留空(由后续 updatePhone/updateAvatar 填充);</li>
     *   <li>password 接受调用方传入(本地登录态保持兼容)。</li>
     * </ul>
     *
     * @return true 表示写入成功或已存在; false 表示写入异常
     */
    public synchronized boolean upsertUser(int userId, String username, String password) {
        if (userId <= 0 || TextUtils.isEmpty(username)) {
            Log.w(TAG, "upsertUser: 参数非法 userId=" + userId + ", username=" + username);
            return false;
        }
        SQLiteDatabase db = getWritableDatabase();
        try {
            ContentValues values = new ContentValues();
            values.put("user_id", userId);
            values.put("username", username);
            if (!TextUtils.isEmpty(password)) {
                values.put("password", password);
            }
            long rowId = db.insertWithOnConflict(TABLE_USER, null, values,
                    SQLiteDatabase.CONFLICT_IGNORE);
            // rowId=-1 表示主键冲突(已存在, 也是成功); rowId>=0 表示新插入成功
            return rowId != -1L || db.query(TABLE_USER, new String[]{"user_id"},
                    "user_id = ?", new String[]{String.valueOf(userId)},
                    null, null, null).getCount() > 0;
        } catch (Exception e) {
            Log.e(TAG, "upsertUser failed", e);
            return false;
        } finally {
            db.close();
        }
    }

    /** 注册 */
    public synchronized int register(String username, String password) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("username", username);
        values.put("password", password);
        int row = (int) db.insert(TABLE_USER, null, values);
        db.close();
        return row;
    }

    /** 登录:根据用户名查询 */
    @SuppressWarnings("Range")
    public synchronized UserInfo login(String username) {
        SQLiteDatabase db = getReadableDatabase();
        UserInfo userInfo = null;
        Cursor cursor = db.rawQuery("select user_id,username,password,phone,avatar from " + TABLE_USER + " where username=?",
                new String[]{username});
        if (cursor.moveToNext()) {
            int user_id = cursor.getInt(cursor.getColumnIndex("user_id"));
            String name = cursor.getString(cursor.getColumnIndex("username"));
            String password = cursor.getString(cursor.getColumnIndex("password"));
            String phone = cursor.getString(cursor.getColumnIndex("phone"));
            String avatar = cursor.getString(cursor.getColumnIndex("avatar"));
            userInfo = new UserInfo(user_id, name, password, phone, avatar);
        }
        cursor.close();
        db.close();
        return userInfo;
    }

    /** 根据 user_id 获取用户信息 */
    @SuppressWarnings("Range")
    public synchronized UserInfo getUserById(int userId) {
        SQLiteDatabase db = getReadableDatabase();
        UserInfo userInfo = null;
        Cursor cursor = db.query(TABLE_USER, null, "user_id = ?",
                new String[]{String.valueOf(userId)}, null, null, null);
        if (cursor != null && cursor.moveToNext()) {
            int id = cursor.getInt(cursor.getColumnIndex("user_id"));
            String name = cursor.getString(cursor.getColumnIndex("username"));
            String password = cursor.getString(cursor.getColumnIndex("password"));
            String phone = cursor.getString(cursor.getColumnIndex("phone"));
            String avatar = cursor.getString(cursor.getColumnIndex("avatar"));
            userInfo = new UserInfo(id, name, password, phone, avatar);
        }
        if (cursor != null) cursor.close();
        db.close();
        return userInfo;
    }

    /** 更新用户名(检查新用户名是否已存在) */
    public synchronized boolean updateUsername(int userId, String newUsername) {
        SQLiteDatabase db = getWritableDatabase();
        try {
            Cursor cursor = db.query(TABLE_USER, new String[]{"user_id"},
                    "username = ? AND user_id != ?",
                    new String[]{newUsername, String.valueOf(userId)},
                    null, null, null);
            if (cursor != null && cursor.getCount() > 0) {
                cursor.close();
                return false;
            }
            if (cursor != null) cursor.close();
            ContentValues values = new ContentValues();
            values.put("username", newUsername);
            int rows = db.update(TABLE_USER, values, "user_id = ?",
                    new String[]{String.valueOf(userId)});
            return rows > 0;
        } catch (Exception e) {
            return false;
        } finally {
            db.close();
        }
    }

    /** 更新密码 */
    public synchronized boolean updatePassword(int userId, String newPassword) {
        SQLiteDatabase db = getWritableDatabase();
        try {
            ContentValues values = new ContentValues();
            values.put("password", newPassword);
            int rows = db.update(TABLE_USER, values, "user_id = ?",
                    new String[]{String.valueOf(userId)});
            return rows > 0;
        } catch (Exception e) {
            return false;
        } finally {
            db.close();
        }
    }

    /** 更新手机号 */
    public synchronized boolean updatePhone(int userId, String newPhone) {
        SQLiteDatabase db = getWritableDatabase();
        try {
            ContentValues values = new ContentValues();
            values.put("phone", newPhone);
            int rows = db.update(TABLE_USER, values, "user_id = ?",
                    new String[]{String.valueOf(userId)});
            return rows > 0;
        } catch (Exception e) {
            Log.e(TAG, "updatePhone failed", e);
            return false;
        } finally {
            db.close();
        }
    }

    /** 更新头像(存储头像资源名) */
    public synchronized boolean updateAvatar(int userId, String newAvatar) {
        SQLiteDatabase db = getWritableDatabase();
        try {
            ContentValues values = new ContentValues();
            values.put("avatar", newAvatar);
            int rows = db.update(TABLE_USER, values, "user_id = ?",
                    new String[]{String.valueOf(userId)});
            return rows > 0;
        } catch (Exception e) {
            Log.e(TAG, "updateAvatar failed", e);
            return false;
        } finally {
            db.close();
        }
    }

    // ====================================================================
    // ======================== 商品模块 (原 ProductDatabase) ==============
    // ====================================================================

    public synchronized List<Goods> getProductsBySection(String section) {
        List<Goods> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.query(TABLE_PRODUCT, null, "section = ?",
                    new String[]{section}, null, null, "sales DESC");
            while (cursor.moveToNext()) {
                list.add(cursorToGoods(cursor));
            }
        } catch (Exception e) {
            Log.e(TAG, "getProductsBySection failed", e);
        } finally {
            if (cursor != null) cursor.close();
            db.close();
        }
        return list;
    }

    public synchronized List<Goods> getAllProducts() {
        List<Goods> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.query(TABLE_PRODUCT, null, null, null, null, null, "sales DESC");
            while (cursor.moveToNext()) {
                list.add(cursorToGoods(cursor));
            }
        } catch (Exception e) {
            Log.e(TAG, "getAllProducts failed", e);
        } finally {
            if (cursor != null) cursor.close();
            db.close();
        }
        return list;
    }

    public synchronized List<Goods> getProductsByCategory(String category) {
        List<Goods> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.query(TABLE_PRODUCT, null, "category = ?",
                    new String[]{category}, null, null, "sales DESC");
            while (cursor.moveToNext()) {
                list.add(cursorToGoods(cursor));
            }
        } catch (Exception e) {
            Log.e(TAG, "getProductsByCategory failed", e);
        } finally {
            if (cursor != null) cursor.close();
            db.close();
        }
        return list;
    }

    public synchronized List<String> getSubCategories(String category) {
        List<String> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.query(true, TABLE_PRODUCT, new String[]{"subcategory"},
                    "category = ?", new String[]{category},
                    "subcategory", null, "subcategory", null);
            while (cursor.moveToNext()) {
                list.add(cursor.getString(cursor.getColumnIndex("subcategory")));
            }
        } catch (Exception e) {
            Log.e(TAG, "getSubCategories failed", e);
        } finally {
            if (cursor != null) cursor.close();
            db.close();
        }
        return list;
    }

    public synchronized List<String> getCategories() {
        List<String> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.query(true, TABLE_PRODUCT, new String[]{"category"},
                    null, null, "category", null, "category ASC", null);
            while (cursor.moveToNext()) {
                list.add(cursor.getString(cursor.getColumnIndex("category")));
            }
        } catch (Exception e) {
            Log.e(TAG, "getCategories failed", e);
        } finally {
            if (cursor != null) cursor.close();
            db.close();
        }
        return list;
    }

    public synchronized List<Goods> getProductsByCatAndSub(String category, String subCategory) {
        List<Goods> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.query(TABLE_PRODUCT, null,
                    "category = ? AND subcategory = ?",
                    new String[]{category, subCategory},
                    null, null, "sales DESC");
            while (cursor.moveToNext()) {
                list.add(cursorToGoods(cursor));
            }
        } catch (Exception e) {
            Log.e(TAG, "getProductsByCatAndSub failed", e);
        } finally {
            if (cursor != null) cursor.close();
            db.close();
        }
        return list;
    }

    public synchronized Goods getProductById(int id) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.query(TABLE_PRODUCT, null, "_id = ?",
                    new String[]{String.valueOf(id)}, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                return cursorToGoods(cursor);
            }
        } catch (Exception e) {
            Log.e(TAG, "getProductById failed", e);
        } finally {
            if (cursor != null) cursor.close();
            db.close();
        }
        return null;
    }

    public synchronized List<Goods> searchProducts(String keyword) {
        List<Goods> list = new ArrayList<>();
        if (keyword == null || keyword.trim().isEmpty()) return list;
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.query(TABLE_PRODUCT, null,
                    "name LIKE ? OR description LIKE ? OR tag LIKE ?",
                    new String[]{"%" + keyword + "%", "%" + keyword + "%", "%" + keyword + "%"},
                    null, null, "sales DESC");
            while (cursor.moveToNext()) {
                list.add(cursorToGoods(cursor));
            }
        } catch (Exception e) {
            Log.e(TAG, "searchProducts failed", e);
        } finally {
            if (cursor != null) cursor.close();
            db.close();
        }
        return list;
    }

    /**
     * 更新库存
     *
     * @param delta 库存变动量。delta<0 表示扣减库存(同时增加销量), delta>0 表示恢复/增加库存。
     * @return true 表示更新成功; false 表示更新失败或库存不足(扣减时剩余库存不够)。
     */
    public synchronized boolean updateStock(int productId, int delta) {
        SQLiteDatabase db = getWritableDatabase();
        try {
            Cursor cursor = db.query(TABLE_PRODUCT, new String[]{"stock", "sales"},
                    "_id = ?", new String[]{String.valueOf(productId)},
                    null, null, null);
            int currentStock = 0, currentSales = 0;
            if (cursor != null && cursor.moveToFirst()) {
                currentStock = cursor.getInt(cursor.getColumnIndex("stock"));
                currentSales = cursor.getInt(cursor.getColumnIndex("sales"));
                cursor.close();
            }
            int newStock = currentStock + delta;

            // 防止超卖: 扣减库存时(delta<0), 若剩余库存不足则拒绝操作
            if (delta < 0 && newStock < 0) {
                Log.w(TAG, "updateStock: 库存不足, productId=" + productId
                        + ", 当前库存=" + currentStock + ", 扣减量=" + delta);
                return false;
            }

            ContentValues cv = new ContentValues();
            cv.put("stock", newStock);
            if (delta < 0) {
                cv.put("sales", currentSales + Math.abs(delta));
            }
            int rows = db.update(TABLE_PRODUCT, cv, "_id = ?",
                    new String[]{String.valueOf(productId)});
            boolean success = rows > 0;
            Log.d(TAG, "updateStock: productId=" + productId
                    + ", 库存 " + currentStock + " → " + newStock
                    + ", 结果=" + (success ? "成功" : "失败"));
            return success;
        } catch (Exception e) {
            Log.e(TAG, "updateStock failed", e);
            return false;
        } finally {
            db.close();
        }
    }

    // ==================== 评论 ====================

    public synchronized boolean addReview(ReviewInfo review) {
        if (review == null) return false;
        SQLiteDatabase db = getWritableDatabase();
        try {
            ContentValues cv = new ContentValues();
            cv.put("product_id", review.getProductId());
            cv.put("user_id", review.getUserId());
            cv.put("user_name", review.getUserName());
            cv.put("rating", review.getRating());
            cv.put("content", review.getContent());
            cv.put("created_at", review.getCreatedAt());
            long id = db.insert(TABLE_REVIEW, null, cv);
            return id != -1;
        } catch (Exception e) {
            Log.e(TAG, "addReview failed", e);
            return false;
        } finally {
            db.close();
        }
    }

    public synchronized List<ReviewInfo> getReviewsByProduct(int productId) {
        List<ReviewInfo> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.query(TABLE_REVIEW, null, "product_id = ?",
                    new String[]{String.valueOf(productId)},
                    null, null, "created_at DESC");
            while (cursor.moveToNext()) {
                ReviewInfo r = cursorToReview(cursor);
                // 加载该评论的追评列表(按时间正序)
                r.setReplies(getRepliesByReviewIdInternal(db, r.getId()));
                list.add(r);
            }
        } catch (Exception e) {
            Log.e(TAG, "getReviewsByProduct failed", e);
        } finally {
            if (cursor != null) cursor.close();
            db.close();
        }
        return list;
    }

    /**
     * 获取某用户对某商品的初始评论(用于查重与判断是否可追评)
     *
     * @return 该用户对该商品的初始评论, 若无则返回 null
     */
    public synchronized ReviewInfo getReviewByUserAndProduct(int userId, int productId) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.query(TABLE_REVIEW, null, "user_id = ? AND product_id = ?",
                    new String[]{String.valueOf(userId), String.valueOf(productId)},
                    null, null, null);
            if (cursor.moveToFirst()) {
                ReviewInfo r = cursorToReview(cursor);
                r.setReplies(getRepliesByReviewIdInternal(db, r.getId()));
                return r;
            }
        } catch (Exception e) {
            Log.e(TAG, "getReviewByUserAndProduct failed", e);
        } finally {
            if (cursor != null) cursor.close();
            db.close();
        }
        return null;
    }

    /**
     * 判断某用户是否已对某商品发表过初始评论
     */
    public synchronized boolean hasReviewed(int userId, int productId) {
        return getReviewByUserAndProduct(userId, productId) != null;
    }

    /**
     * 添加追评
     * 业务层需先校验: 追评者必须是初始评论的发布者
     *
     * @return 插入成功返回 true, 失败返回 false
     */
    public synchronized boolean addReviewReply(ReviewReply reply) {
        if (reply == null) return false;
        SQLiteDatabase db = getWritableDatabase();
        try {
            ContentValues cv = new ContentValues();
            cv.put("review_id", reply.getReviewId());
            cv.put("user_id", reply.getUserId());
            cv.put("user_name", reply.getUserName());
            cv.put("content", reply.getContent());
            cv.put("created_at", reply.getCreatedAt());
            long id = db.insert(TABLE_REVIEW_REPLY, null, cv);
            return id != -1;
        } catch (Exception e) {
            Log.e(TAG, "addReviewReply failed", e);
            return false;
        } finally {
            db.close();
        }
    }

    /**
     * 获取某条初始评论的所有追评(按时间正序)
     */
    public synchronized List<ReviewReply> getRepliesByReviewId(int reviewId) {
        SQLiteDatabase db = getReadableDatabase();
        try {
            return getRepliesByReviewIdInternal(db, reviewId);
        } finally {
            db.close();
        }
    }

    private List<ReviewReply> getRepliesByReviewIdInternal(SQLiteDatabase db, int reviewId) {
        List<ReviewReply> list = new ArrayList<>();
        Cursor cursor = null;
        try {
            cursor = db.query(TABLE_REVIEW_REPLY, null, "review_id = ?",
                    new String[]{String.valueOf(reviewId)},
                    null, null, "created_at ASC");
            while (cursor.moveToNext()) {
                list.add(cursorToReviewReply(cursor));
            }
        } catch (Exception e) {
            Log.e(TAG, "getRepliesByReviewId failed", e);
        } finally {
            if (cursor != null) cursor.close();
        }
        return list;
    }

    public synchronized int getReviewCount(int productId) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.query(TABLE_REVIEW, new String[]{"COUNT(*)"},
                    "product_id = ?", new String[]{String.valueOf(productId)},
                    null, null, null);
            if (cursor.moveToFirst()) {
                return cursor.getInt(0);
            }
        } catch (Exception e) {
            Log.e(TAG, "getReviewCount failed", e);
        } finally {
            if (cursor != null) cursor.close();
            db.close();
        }
        return 0;
    }

    public synchronized double getAverageRating(int productId) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.query(TABLE_REVIEW, new String[]{"AVG(rating)"},
                    "product_id = ?", new String[]{String.valueOf(productId)},
                    null, null, null);
            if (cursor.moveToFirst()) {
                double avg = cursor.getDouble(0);
                return Math.round(avg * 10) / 10.0;
            }
        } catch (Exception e) {
            Log.e(TAG, "getAverageRating failed", e);
        } finally {
            if (cursor != null) cursor.close();
            db.close();
        }
        return 0.0;
    }

    // ====================================================================
    // ======================== 购物车模块 (原 CarDatabase) ================
    // ====================================================================

    public synchronized boolean addCar(CarInfo carInfo) {
        if (carInfo == null) {
            Log.e(TAG, "addCar: carInfo is null");
            return false;
        }
        SQLiteDatabase db = getWritableDatabase();
        Cursor cursor = null;
        try {
            String selection = "product_id = ? AND user_id = ?";
            String[] selectionArgs = {String.valueOf(carInfo.getProduct_id()), String.valueOf(carInfo.getUser_id())};
            cursor = db.query(TABLE_CAR, new String[]{"product_count"}, selection, selectionArgs, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int currentCount = cursor.getInt(cursor.getColumnIndex("product_count"));
                int newCount = currentCount + carInfo.getProduct_count();
                ContentValues values = new ContentValues();
                values.put("product_count", newCount);
                int rows = db.update(TABLE_CAR, values, selection, selectionArgs);
                return rows > 0;
            } else {
                ContentValues values = new ContentValues();
                values.put("product_id", carInfo.getProduct_id());
                values.put("product_title", carInfo.getProduct_title());
                values.put("product_price", carInfo.getProduct_price());
                values.put("product_count", carInfo.getProduct_count());
                values.put("product_img", carInfo.getProduct_img());
                values.put("imagePath", carInfo.getImagePath());
                values.put("selected", carInfo.isSelected() ? 1 : 0);
                values.put("user_id", carInfo.getUser_id());
                long id = db.insert(TABLE_CAR, null, values);
                return id != -1;
            }
        } catch (Exception e) {
            Log.e(TAG, "添加商品到购物车失败", e);
            return false;
        } finally {
            if (cursor != null) cursor.close();
            db.close();
        }
    }

    public synchronized boolean removeCar(int productId, int userId) {
        SQLiteDatabase db = getWritableDatabase();
        try {
            int rows = db.delete(TABLE_CAR, "product_id = ? AND user_id = ?",
                    new String[]{String.valueOf(productId), String.valueOf(userId)});
            return rows > 0;
        } catch (Exception e) {
            Log.e(TAG, "从购物车中删除商品失败", e);
            return false;
        } finally {
            db.close();
        }
    }

    public synchronized boolean removeCars(List<Integer> productIds, int userId) {
        if (productIds == null || productIds.isEmpty()) return false;
        SQLiteDatabase db = getWritableDatabase();
        try {
            db.beginTransaction();
            for (Integer productId : productIds) {
                db.delete(TABLE_CAR, "product_id = ? AND user_id = ?",
                        new String[]{String.valueOf(productId), String.valueOf(userId)});
            }
            db.setTransactionSuccessful();
            return true;
        } catch (Exception e) {
            Log.e(TAG, "从购物车中批量删除商品失败", e);
            return false;
        } finally {
            db.endTransaction();
            db.close();
        }
    }

    public synchronized boolean updateProductQuantity(int productId, int quantity, int userId) {
        SQLiteDatabase db = getWritableDatabase();
        try {
            ContentValues values = new ContentValues();
            values.put("product_count", quantity);
            int rows = db.update(TABLE_CAR, values, "product_id = ? AND user_id = ?",
                    new String[]{String.valueOf(productId), String.valueOf(userId)});
            return rows > 0;
        } catch (Exception e) {
            Log.e(TAG, "更新购物车中商品的数量失败", e);
            return false;
        } finally {
            db.close();
        }
    }

    public synchronized boolean updateProductSelected(int productId, boolean selected, int userId) {
        SQLiteDatabase db = getWritableDatabase();
        try {
            ContentValues values = new ContentValues();
            values.put("selected", selected ? 1 : 0);
            int rows = db.update(TABLE_CAR, values, "product_id = ? AND user_id = ?",
                    new String[]{String.valueOf(productId), String.valueOf(userId)});
            return rows > 0;
        } catch (Exception e) {
            Log.e(TAG, "更新购物车中商品的选择状态失败", e);
            return false;
        } finally {
            db.close();
        }
    }

    public synchronized boolean updateAllProductsSelected(boolean selected, int userId) {
        SQLiteDatabase db = getWritableDatabase();
        try {
            ContentValues values = new ContentValues();
            values.put("selected", selected ? 1 : 0);
            int rows = db.update(TABLE_CAR, values, "user_id = ?", new String[]{String.valueOf(userId)});
            return rows >= 0;
        } catch (Exception e) {
            Log.e(TAG, "更新所有商品的选择状态失败", e);
            return false;
        } finally {
            db.close();
        }
    }

    public synchronized boolean updateProductContactInfo(int productId, String phone, String address, int userId) {
        SQLiteDatabase db = getWritableDatabase();
        try {
            ContentValues values = new ContentValues();
            values.put("phone", phone);
            values.put("address", address);
            int rows = db.update(TABLE_CAR, values, "product_id = ? AND user_id = ?",
                    new String[]{String.valueOf(productId), String.valueOf(userId)});
            return rows > 0;
        } catch (Exception e) {
            Log.e(TAG, "更新商品的电话和地址信息失败", e);
            return false;
        } finally {
            db.close();
        }
    }

    public synchronized List<CarInfo> getAllCars(int userId) {
        List<CarInfo> carInfoList = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.query(TABLE_CAR, null, "user_id = ?",
                    new String[]{String.valueOf(userId)}, null, null, null);
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    carInfoList.add(cursorToCarInfo(cursor));
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "获取购物车中所有商品失败", e);
        } finally {
            if (cursor != null) cursor.close();
            db.close();
        }
        return carInfoList;
    }

    public synchronized List<CarInfo> getSelectedCars(int userId) {
        List<CarInfo> selectedList = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.query(TABLE_CAR, null, "selected = ? AND user_id = ?",
                    new String[]{"1", String.valueOf(userId)}, null, null, null);
            while (cursor.moveToNext()) {
                selectedList.add(cursorToCarInfo(cursor));
            }
        } catch (Exception e) {
            Log.e(TAG, "获取购物车中已选择的商品失败", e);
        } finally {
            if (cursor != null) cursor.close();
            db.close();
        }
        return selectedList;
    }

    public synchronized double calculateTotalPrice(int userId) {
        double totalPrice = 0.0;
        for (CarInfo carInfo : getSelectedCars(userId)) {
            totalPrice += carInfo.getTotalPrice();
        }
        return totalPrice;
    }

    public synchronized int getTotalCount(int userId) {
        int totalCount = 0;
        for (CarInfo carInfo : getAllCars(userId)) {
            totalCount += carInfo.getProduct_count();
        }
        return totalCount;
    }

    public synchronized boolean clearCar(int userId) {
        SQLiteDatabase db = getWritableDatabase();
        try {
            int rows = db.delete(TABLE_CAR, "user_id = ?", new String[]{String.valueOf(userId)});
            return rows >= 0;
        } catch (Exception e) {
            Log.e(TAG, "清空购物车失败", e);
            return false;
        } finally {
            db.close();
        }
    }

    public synchronized boolean isEmpty(int userId) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.query(TABLE_CAR, new String[]{"COUNT(*)"}, "user_id = ?",
                    new String[]{String.valueOf(userId)}, null, null, null);
            if (cursor.moveToFirst()) {
                int count = cursor.getInt(0);
                return count == 0;
            }
            return true;
        } catch (Exception e) {
            Log.e(TAG, "检查购物车是否为空失败", e);
            return true;
        } finally {
            if (cursor != null) cursor.close();
            db.close();
        }
    }

    // ====================================================================
    // ======================== 订单模块 (原 OrderDatabase) ================
    // ====================================================================

    public synchronized boolean updateOrderStatus(int orderId, String status) {
        SQLiteDatabase db = getWritableDatabase();
        try {
            ContentValues cv = new ContentValues();
            cv.put("status", status);
            int rows = db.update(TABLE_ORDER, cv, "order_id = ?",
                    new String[]{String.valueOf(orderId)});
            return rows > 0;
        } catch (Exception e) {
            Log.e(TAG, "updateOrderStatus failed", e);
            return false;
        } finally {
            db.close();
        }
    }

    public synchronized List<OrderInfo> getOrdersByStatus(int userId, String status) {
        List<OrderInfo> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.query(TABLE_ORDER, null,
                    "user_id = ? AND status = ?",
                    new String[]{String.valueOf(userId), status},
                    null, null, "order_time DESC");
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    list.add(cursorToOrderInfo(cursor));
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "getOrdersByStatus failed", e);
        } finally {
            if (cursor != null) cursor.close();
            db.close();
        }
        return list;
    }

    public synchronized boolean removeOrder(int orderId) {
        SQLiteDatabase db = getWritableDatabase();
        try {
            int rows = db.delete(TABLE_ORDER, "order_id = ?",
                    new String[]{String.valueOf(orderId)});
            return rows > 0;
        } catch (Exception e) {
            Log.e(TAG, "removeOrder failed", e);
            return false;
        } finally {
            db.close();
        }
    }

    /** 重载:按 productId + userId 删除(保留与原 OrderDatabase 一致签名) */
    public synchronized boolean removeOrder(int productId, int userId) {
        SQLiteDatabase db = getWritableDatabase();
        try {
            int rows = db.delete(TABLE_ORDER, "product_id = ? AND user_id = ?",
                    new String[]{String.valueOf(productId), String.valueOf(userId)});
            return rows > 0;
        } catch (Exception e) {
            Log.e(TAG, "removeOrder(productId,userId) failed", e);
            return false;
        } finally {
            db.close();
        }
    }

    public synchronized boolean hasPurchased(int userId, int productId) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.query(TABLE_ORDER, new String[]{"COUNT(*)"},
                    "user_id = ? AND product_id = ? AND status != ?",
                    new String[]{String.valueOf(userId), String.valueOf(productId), STATUS_RETURNED},
                    null, null, null);
            if (cursor.moveToFirst()) {
                return cursor.getInt(0) > 0;
            }
        } catch (Exception e) {
            Log.e(TAG, "hasPurchased failed", e);
        } finally {
            if (cursor != null) cursor.close();
            db.close();
        }
        return false;
    }

    public synchronized boolean markOrderAsReviewed(int userId, int productId) {
        SQLiteDatabase db = getWritableDatabase();
        try {
            ContentValues cv = new ContentValues();
            cv.put("status", STATUS_DONE);
            int rows = db.update(TABLE_ORDER, cv,
                    "user_id = ? AND product_id = ? AND status = ?",
                    new String[]{String.valueOf(userId), String.valueOf(productId), STATUS_PENDING_REVIEW});
            return rows > 0;
        } catch (Exception e) {
            Log.e(TAG, "markOrderAsReviewed failed", e);
            return false;
        } finally {
            db.close();
        }
    }

    public synchronized boolean addOrder(OrderInfo orderInfo) {
        if (orderInfo == null) {
            Log.e(TAG, "addOrder: orderInfo is null");
            return false;
        }
        SQLiteDatabase db = getWritableDatabase();
        try {
            ContentValues values = new ContentValues();
            values.put("user_id", orderInfo.getUser_id());
            values.put("product_id", orderInfo.getProduct_id());
            values.put("product_title", orderInfo.getProduct_title());
            values.put("product_price", orderInfo.getProduct_price());
            values.put("product_count", orderInfo.getProduct_count());
            values.put("product_img", orderInfo.getProduct_img());
            values.put("imagePath", orderInfo.getImagePath());
            values.put("phone", orderInfo.getPhone());
            values.put("address", orderInfo.getAddress());
            values.put("order_time", orderInfo.getOrder_time());
            long id = db.insert(TABLE_ORDER, null, values);
            return id != -1;
        } catch (Exception e) {
            Log.e(TAG, "添加商品到订单失败", e);
            return false;
        } finally {
            db.close();
        }
    }

    public synchronized List<OrderInfo> getAllOrders(int userId) {
        List<OrderInfo> orderInfoList = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.query(TABLE_ORDER, null, "user_id = ?",
                    new String[]{String.valueOf(userId)}, null, null, "order_time DESC");
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    orderInfoList.add(cursorToOrderInfo(cursor));
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "获取订单中所有商品失败", e);
        } finally {
            if (cursor != null) cursor.close();
            db.close();
        }
        return orderInfoList;
    }

    // ====================================================================
    // ======================== 浏览历史模块 (新增) ========================
    // ====================================================================

    /** 记录浏览历史:同用户同商品已存在则更新时间,否则插入 */
    public synchronized boolean addBrowseHistory(int userId, int productId, String browseTime) {
        SQLiteDatabase db = getWritableDatabase();
        try {
            // 先尝试更新(UNIQUE 冲突时更新时间)
            ContentValues cv = new ContentValues();
            cv.put("browse_time", browseTime);
            int rows = db.update(TABLE_BROWSE, cv,
                    "user_id = ? AND product_id = ?",
                    new String[]{String.valueOf(userId), String.valueOf(productId)});
            if (rows == 0) {
                cv.put("user_id", userId);
                cv.put("product_id", productId);
                long id = db.insert(TABLE_BROWSE, null, cv);
                if (id == -1) return false;
            }
            // 限制保留最近 50 条
            db.execSQL("DELETE FROM " + TABLE_BROWSE + " WHERE user_id = ? AND _id NOT IN " +
                            "(SELECT _id FROM " + TABLE_BROWSE + " WHERE user_id = ? ORDER BY browse_time DESC LIMIT 50)",
                    new String[]{String.valueOf(userId), String.valueOf(userId)});
            return true;
        } catch (Exception e) {
            Log.e(TAG, "addBrowseHistory failed", e);
            return false;
        } finally {
            db.close();
        }
    }

    /** 获取用户浏览历史(productId 列表, 按时间倒序) */
    public synchronized List<Integer> getBrowseHistoryProductIds(int userId, int limit) {
        List<Integer> ids = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.query(TABLE_BROWSE, new String[]{"product_id"},
                    "user_id = ?", new String[]{String.valueOf(userId)},
                    null, null, "browse_time DESC", String.valueOf(limit));
            while (cursor.moveToNext()) {
                ids.add(cursor.getInt(0));
            }
        } catch (Exception e) {
            Log.e(TAG, "getBrowseHistoryProductIds failed", e);
        } finally {
            if (cursor != null) cursor.close();
            db.close();
        }
        return ids;
    }

    /** 清空某用户浏览历史 */
    public synchronized boolean clearBrowseHistory(int userId) {
        SQLiteDatabase db = getWritableDatabase();
        try {
            int rows = db.delete(TABLE_BROWSE, "user_id = ?", new String[]{String.valueOf(userId)});
            return rows >= 0;
        } catch (Exception e) {
            Log.e(TAG, "clearBrowseHistory failed", e);
            return false;
        } finally {
            db.close();
        }
    }

    // ====================================================================
    // ======================== Cursor 转换 ===============================
    // ====================================================================

    @SuppressLint("Range")
    private Goods cursorToGoods(Cursor cursor) {
        int id = cursor.getInt(cursor.getColumnIndex("_id"));
        String name = cursor.getString(cursor.getColumnIndex("name"));
        double price = cursor.getDouble(cursor.getColumnIndex("price"));
        String priceDisplay = cursor.getString(cursor.getColumnIndex("price_display"));
        double originalPrice = cursor.getDouble(cursor.getColumnIndex("original_price"));
        String image = cursor.getString(cursor.getColumnIndex("image"));
        String tag = cursor.getString(cursor.getColumnIndex("tag"));
        String category = cursor.getString(cursor.getColumnIndex("category"));
        String subcategory = cursor.getString(cursor.getColumnIndex("subcategory"));
        String section = cursor.getString(cursor.getColumnIndex("section"));
        int stock = cursor.getInt(cursor.getColumnIndex("stock"));
        int sales = cursor.getInt(cursor.getColumnIndex("sales"));
        String merchant = cursor.getString(cursor.getColumnIndex("merchant_name"));
        String merchantAddr = cursor.getString(cursor.getColumnIndex("merchant_address"));
        String desc = cursor.getString(cursor.getColumnIndex("description"));

        int height = (id % 3 == 0) ? 720 : (id % 3 == 1 ? 600 : 660);

        List<Integer> bannerPics = new ArrayList<>();
        bannerPics.add(getImageResByName("yt_b_1"));
        bannerPics.add(getImageResByName("yt_b_2"));
        bannerPics.add(getImageResByName("yt_b_3"));
        bannerPics.add(getImageResByName("yt_b_4"));
        bannerPics.add(getImageResByName("yt_b_5"));

        List<Integer> detailPics = new ArrayList<>();
        detailPics.add(getImageResByName("yingtao1"));
        detailPics.add(getImageResByName("yingtao2"));
        detailPics.add(getImageResByName("yingtao3"));
        detailPics.add(getImageResByName("yingtao4"));
        detailPics.add(getImageResByName("yingtao5"));
        detailPics.add(getImageResByName("yingtao6"));
        detailPics.add(getImageResByName("yingtao7"));

        Goods goods = new Goods(id, getImageResByName(image), name, priceDisplay, tag, height, bannerPics, detailPics);
        goods.setCategory(category);
        goods.setSubcategory(subcategory);
        goods.setSection(section);
        goods.setStock(stock);
        goods.setSales(sales);
        goods.setMerchantName(merchant);
        goods.setMerchantAddress(merchantAddr);
        goods.setDescription(desc);
        goods.setPriceValue(price);
        goods.setOriginalPrice(originalPrice);
        return goods;
    }

    @SuppressLint("Range")
    private ReviewInfo cursorToReview(Cursor cursor) {
        ReviewInfo r = new ReviewInfo();
        r.setId(cursor.getInt(cursor.getColumnIndex("_id")));
        r.setProductId(cursor.getInt(cursor.getColumnIndex("product_id")));
        r.setUserId(cursor.getInt(cursor.getColumnIndex("user_id")));
        r.setUserName(cursor.getString(cursor.getColumnIndex("user_name")));
        r.setRating(cursor.getDouble(cursor.getColumnIndex("rating")));
        r.setContent(cursor.getString(cursor.getColumnIndex("content")));
        r.setCreatedAt(cursor.getString(cursor.getColumnIndex("created_at")));
        return r;
    }

    @SuppressLint("Range")
    private ReviewReply cursorToReviewReply(Cursor cursor) {
        ReviewReply r = new ReviewReply();
        r.setId(cursor.getInt(cursor.getColumnIndex("_id")));
        r.setReviewId(cursor.getInt(cursor.getColumnIndex("review_id")));
        r.setUserId(cursor.getInt(cursor.getColumnIndex("user_id")));
        r.setUserName(cursor.getString(cursor.getColumnIndex("user_name")));
        r.setContent(cursor.getString(cursor.getColumnIndex("content")));
        r.setCreatedAt(cursor.getString(cursor.getColumnIndex("created_at")));
        return r;
    }

    @SuppressLint("Range")
    private CarInfo cursorToCarInfo(Cursor cursor) {
        CarInfo carInfo = new CarInfo();
        carInfo.setProduct_id(cursor.getInt(cursor.getColumnIndex("product_id")));
        carInfo.setProduct_title(cursor.getString(cursor.getColumnIndex("product_title")));
        carInfo.setProduct_price(cursor.getString(cursor.getColumnIndex("product_price")));
        carInfo.setProduct_count(cursor.getInt(cursor.getColumnIndex("product_count")));
        carInfo.setProduct_img(cursor.getInt(cursor.getColumnIndex("product_img")));
        carInfo.setImagePath(cursor.getString(cursor.getColumnIndex("imagePath")));
        carInfo.setSelected(cursor.getInt(cursor.getColumnIndex("selected")) == 1);
        carInfo.setUser_id(cursor.getInt(cursor.getColumnIndex("user_id")));
        carInfo.setPhone(cursor.getString(cursor.getColumnIndex("phone")));
        carInfo.setAddress(cursor.getString(cursor.getColumnIndex("address")));
        return carInfo;
    }

    @SuppressLint("Range")
    private OrderInfo cursorToOrderInfo(Cursor cursor) {
        OrderInfo orderInfo = new OrderInfo();
        orderInfo.setOrder_id(cursor.getInt(cursor.getColumnIndex("order_id")));
        orderInfo.setUser_id(cursor.getInt(cursor.getColumnIndex("user_id")));
        orderInfo.setProduct_id(cursor.getInt(cursor.getColumnIndex("product_id")));
        orderInfo.setProduct_title(cursor.getString(cursor.getColumnIndex("product_title")));
        orderInfo.setProduct_price(cursor.getString(cursor.getColumnIndex("product_price")));
        orderInfo.setProduct_count(cursor.getInt(cursor.getColumnIndex("product_count")));
        orderInfo.setProduct_img(cursor.getInt(cursor.getColumnIndex("product_img")));
        orderInfo.setImagePath(cursor.getString(cursor.getColumnIndex("imagePath")));
        orderInfo.setPhone(cursor.getString(cursor.getColumnIndex("phone")));
        orderInfo.setAddress(cursor.getString(cursor.getColumnIndex("address")));
        orderInfo.setOrder_time(cursor.getString(cursor.getColumnIndex("order_time")));
        int statusIdx = cursor.getColumnIndex("status");
        if (statusIdx >= 0) {
            String status = cursor.getString(statusIdx);
            if (status != null) orderInfo.setStatus(status);
        }
        return orderInfo;
    }

    // ====================================================================
    // ======================== 图片资源工具 (静态) =========================
    // ====================================================================

    public static int getImageResByName(String name) {
        if (name == null || name.isEmpty()) {
            return getImageResByName("yingtao1");
        }
        try {
            Class<?> drawableClass = Class.forName("com.example.myapplication.R$drawable");
            java.lang.reflect.Field field = drawableClass.getField(name);
            int resId = field.getInt(null);
            if (resId != 0) return resId;
        } catch (Exception e) {
            Log.w(TAG, "drawable 资源 [" + name + "] 不存在,使用默认图 yingtao1");
        }
        try {
            Class<?> drawableClass = Class.forName("com.example.myapplication.R$drawable");
            java.lang.reflect.Field field = drawableClass.getField("yingtao1");
            return field.getInt(null);
        } catch (Exception e2) {
            Log.e(TAG, "默认图 yingtao1 也找不到!", e2);
            return 0;
        }
    }

}
