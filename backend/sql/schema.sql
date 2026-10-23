-- =============================================================
--  大润发风格电商管理系统  数据库建表脚本
--  Database: darunfa
--  Charset : utf8mb4
--  Engine  : InnoDB
--  说明    : 前端(Android) 与 后端(Spring Boot) 共用本 MySQL 库
-- =============================================================

CREATE DATABASE IF NOT EXISTS darunfa
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_general_ci;

USE darunfa;

-- -------------------------------------------------------------
-- 1. 用户表 users
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS users (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '用户ID',
    username    VARCHAR(32)  NOT NULL COMMENT '登录用户名',
    password    VARCHAR(100) NOT NULL COMMENT 'BCrypt 加密后的密码',
    nickname    VARCHAR(32)  DEFAULT NULL COMMENT '昵称',
    phone       VARCHAR(20)  DEFAULT NULL COMMENT '手机号',
    email       VARCHAR(64)  DEFAULT NULL COMMENT '邮箱',
    avatar      VARCHAR(255) DEFAULT NULL COMMENT '头像 URL',
    balance     DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '账户余额(元)',
    role        VARCHAR(16)  NOT NULL DEFAULT 'USER' COMMENT '角色: USER/ADMIN',
    status      TINYINT      NOT NULL DEFAULT 1 COMMENT '1启用 0禁用',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_username (username)
) ENGINE=InnoDB COMMENT='用户表';

-- -------------------------------------------------------------
-- 2. 商品分类表 categories（支持二级分类）
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS categories (
    id          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '分类ID',
    name        VARCHAR(32) NOT NULL COMMENT '分类名称',
    parent_id   BIGINT      NOT NULL DEFAULT 0 COMMENT '父分类ID, 0 表示一级分类',
    sort_order  INT         NOT NULL DEFAULT 0 COMMENT '排序权重(越小越靠前)',
    icon        VARCHAR(255) DEFAULT NULL COMMENT '分类图标 URL',
    status      TINYINT     NOT NULL DEFAULT 1 COMMENT '1启用 0禁用',
    created_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_categories_parent (parent_id)
) ENGINE=InnoDB COMMENT='商品分类表';

-- -------------------------------------------------------------
-- 3. 商品表 products
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS products (
    id             BIGINT        NOT NULL AUTO_INCREMENT COMMENT '商品ID',
    category_id    BIGINT        NOT NULL COMMENT '所属分类ID',
    name           VARCHAR(128)  NOT NULL COMMENT '商品名称',
    subtitle       VARCHAR(255)  DEFAULT NULL COMMENT '副标题/卖点',
    description    TEXT          COMMENT '商品详情描述',
    price          DECIMAL(10,2) NOT NULL COMMENT '现价(元)',
    original_price DECIMAL(10,2) DEFAULT NULL COMMENT '原价(元)',
    stock          INT           NOT NULL DEFAULT 0 COMMENT '库存',
    sales          INT           NOT NULL DEFAULT 0 COMMENT '销量',
    unit           VARCHAR(16)   DEFAULT '件' COMMENT '计量单位',
    image_url      VARCHAR(255)  DEFAULT NULL COMMENT '主图 URL',
    images         TEXT          COMMENT '轮播图 URL 列表(JSON 数组)',
    status         TINYINT       NOT NULL DEFAULT 1 COMMENT '1上架 0下架',
    created_at     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_products_category (category_id),
    KEY idx_products_status (status),
    KEY idx_products_name (name)
) ENGINE=InnoDB COMMENT='商品表';

-- -------------------------------------------------------------
-- 4. 购物车表 cart_items
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS cart_items (
    id            BIGINT        NOT NULL AUTO_INCREMENT COMMENT '购物车项ID',
    user_id       BIGINT        NOT NULL COMMENT '用户ID',
    product_id    BIGINT        NOT NULL COMMENT '商品ID',
    product_name  VARCHAR(128)  NOT NULL COMMENT '商品名快照',
    product_image VARCHAR(255)  DEFAULT NULL COMMENT '商品图快照',
    price         DECIMAL(10,2) NOT NULL COMMENT '加入时单价快照',
    quantity      INT           NOT NULL DEFAULT 1 COMMENT '数量',
    selected      TINYINT       NOT NULL DEFAULT 1 COMMENT '是否勾选 1是 0否',
    created_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_cart_user_product (user_id, product_id),
    KEY idx_cart_user (user_id)
) ENGINE=InnoDB COMMENT='购物车表';

-- -------------------------------------------------------------
-- 5. 订单表 orders
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS orders (
    id              BIGINT        NOT NULL AUTO_INCREMENT COMMENT '订单ID',
    order_no        VARCHAR(32)   NOT NULL COMMENT '订单号(业务唯一)',
    user_id         BIGINT        NOT NULL COMMENT '下单用户ID',
    total_amount    DECIMAL(10,2) NOT NULL COMMENT '商品总额',
    pay_amount      DECIMAL(10,2) NOT NULL COMMENT '实付金额(含运费折扣等)',
    discount_amount DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '促销优惠金额',
    promotion_id    BIGINT        DEFAULT NULL COMMENT '使用的促销活动ID',
    status          VARCHAR(24)   NOT NULL DEFAULT 'PENDING_PAYMENT' COMMENT '状态: PENDING_PAYMENT/PAID/SHIPPED/COMPLETED/CANCELLED',
    receiver_name   VARCHAR(32)   DEFAULT NULL COMMENT '收货人姓名',
    receiver_phone  VARCHAR(20)   DEFAULT NULL COMMENT '收货人电话',
    receiver_address VARCHAR(255) DEFAULT NULL COMMENT '收货地址',
    remark          VARCHAR(255)  DEFAULT NULL COMMENT '订单备注',
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    paid_at         DATETIME      DEFAULT NULL COMMENT '支付时间',
    shipped_at      DATETIME      DEFAULT NULL COMMENT '发货时间',
    completed_at    DATETIME      DEFAULT NULL COMMENT '完成时间',
    cancelled_at    DATETIME      DEFAULT NULL COMMENT '取消时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_orders_order_no (order_no),
    KEY idx_orders_user (user_id),
    KEY idx_orders_status (status)
) ENGINE=InnoDB COMMENT='订单表';

-- -------------------------------------------------------------
-- 6. 订单明细表 order_items
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS order_items (
    id            BIGINT        NOT NULL AUTO_INCREMENT COMMENT '明细ID',
    order_id      BIGINT        NOT NULL COMMENT '所属订单ID',
    product_id    BIGINT        NOT NULL COMMENT '商品ID',
    product_name  VARCHAR(128)  NOT NULL COMMENT '商品名快照',
    product_image VARCHAR(255)  DEFAULT NULL COMMENT '商品图快照',
    price         DECIMAL(10,2) NOT NULL COMMENT '成交单价快照',
    quantity      INT           NOT NULL DEFAULT 1 COMMENT '购买数量',
    subtotal      DECIMAL(10,2) NOT NULL COMMENT '小计 = price * quantity',
    PRIMARY KEY (id),
    KEY idx_order_items_order (order_id),
    KEY idx_order_items_product (product_id)
) ENGINE=InnoDB COMMENT='订单明细表';

-- -------------------------------------------------------------
-- 7. 收货地址表 addresses
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS addresses (
    id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '地址ID',
    user_id         BIGINT       NOT NULL COMMENT '用户ID',
    receiver_name   VARCHAR(32)  NOT NULL COMMENT '收货人姓名',
    receiver_phone  VARCHAR(20)  NOT NULL COMMENT '收货人电话',
    province        VARCHAR(32)  DEFAULT NULL COMMENT '省',
    city            VARCHAR(32)  DEFAULT NULL COMMENT '市',
    district        VARCHAR(32)  DEFAULT NULL COMMENT '区/县',
    detail          VARCHAR(255) NOT NULL COMMENT '详细地址',
    is_default      TINYINT      NOT NULL DEFAULT 0 COMMENT '是否默认 1是 0否',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_addresses_user (user_id)
) ENGINE=InnoDB COMMENT='收货地址表';

-- -------------------------------------------------------------
-- 8. 商品评论表 reviews
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS reviews (
    id         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '评论ID',
    product_id BIGINT       NOT NULL COMMENT '商品ID',
    user_id    BIGINT       NOT NULL COMMENT '评论用户ID',
    user_name  VARCHAR(32)  DEFAULT NULL COMMENT '评论用户名快照',
    rating     DECIMAL(2,1) NOT NULL DEFAULT 5.0 COMMENT '评分 0.0-5.0',
    content    VARCHAR(1000) DEFAULT NULL COMMENT '评论内容',
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_reviews_product (product_id),
    KEY idx_reviews_user (user_id)
) ENGINE=InnoDB COMMENT='商品评论表';

-- -------------------------------------------------------------
-- 9. 追评表 review_replies
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS review_replies (
    id         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '追评ID',
    review_id  BIGINT       NOT NULL COMMENT '关联评论ID',
    user_id    BIGINT       NOT NULL COMMENT '追评用户ID',
    user_name  VARCHAR(32)  DEFAULT NULL COMMENT '追评用户名快照',
    content    VARCHAR(1000) NOT NULL COMMENT '追评内容',
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_replies_review (review_id)
) ENGINE=InnoDB COMMENT='评论追评表';

-- -------------------------------------------------------------
-- 10. 促销活动表 promotions
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS promotions (
    id          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '促销ID',
    name        VARCHAR(64)   NOT NULL COMMENT '活动名称',
    type        VARCHAR(24)   NOT NULL DEFAULT 'FULL_REDUCTION' COMMENT '类型: FULL_REDUCTION满减/DISCOUNT折扣',
    threshold   DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '门槛金额(满减=满多少, 折扣=满多少享折扣; 0无门槛)',
    discount    DECIMAL(10,4) NOT NULL DEFAULT 0 COMMENT '满减=减免金额, 折扣=折扣率0~1',
    product_id  BIGINT        DEFAULT NULL COMMENT '指定商品ID(空=全店通用)',
    start_time  DATETIME      DEFAULT NULL COMMENT '开始时间',
    end_time    DATETIME      DEFAULT NULL COMMENT '结束时间',
    status      TINYINT       NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
    created_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_promotions_status (status),
    KEY idx_promotions_product (product_id)
) ENGINE=InnoDB COMMENT='促销活动表';

-- -------------------------------------------------------------
-- 11. 支付流水表 payments
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS payments (
    id          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '支付ID',
    order_id    BIGINT        NOT NULL COMMENT '订单ID',
    order_no    VARCHAR(32)   DEFAULT NULL COMMENT '订单号',
    user_id     BIGINT        NOT NULL COMMENT '支付用户ID',
    amount      DECIMAL(10,2) NOT NULL COMMENT '支付金额',
    method      VARCHAR(24)   NOT NULL DEFAULT 'WECHAT' COMMENT '支付方式: WECHAT/ALIPAY/BANK/CASH/BALANCE',
    trade_no    VARCHAR(64)   DEFAULT NULL COMMENT '第三方流水号',
    status      VARCHAR(24)   NOT NULL DEFAULT 'SUCCESS' COMMENT '状态: SUCCESS/REFUNDED/FAILED',
    paid_at     DATETIME      DEFAULT NULL COMMENT '支付时间',
    created_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_payments_order (order_id),
    KEY idx_payments_user (user_id)
) ENGINE=InnoDB COMMENT='支付流水表';

-- -------------------------------------------------------------
-- 12. 物流发货表 shipments
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS shipments (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '物流ID',
    order_id    BIGINT       NOT NULL COMMENT '订单ID',
    order_no    VARCHAR(32)  DEFAULT NULL COMMENT '订单号',
    company     VARCHAR(64)  DEFAULT NULL COMMENT '物流公司',
    tracking_no VARCHAR(64)  DEFAULT NULL COMMENT '运单号',
    status      VARCHAR(24)  NOT NULL DEFAULT 'SHIPPED' COMMENT '状态: SHIPPED/IN_TRANSIT/DELIVERED',
    shipped_at  DATETIME     DEFAULT NULL COMMENT '发货时间',
    delivered_at DATETIME    DEFAULT NULL COMMENT '送达时间',
    remark      VARCHAR(255) DEFAULT NULL COMMENT '备注',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_shipments_order (order_id),
    KEY idx_shipments_tracking (tracking_no)
) ENGINE=InnoDB COMMENT='物流发货表';

-- -------------------------------------------------------------
-- 13. 余额流水表 balance_transactions（余额支付 / 充值 / 退款返现 / 初始赠送）
--     所有余额变动都要落一条流水；users.balance 与 SUM(amount) 必须恒等。
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS balance_transactions (
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    user_id       BIGINT        NOT NULL,
    type          VARCHAR(16)   NOT NULL COMMENT 'INIT/RECHARGE/PAY/REFUND',
    amount        DECIMAL(12,2) NOT NULL COMMENT '有符号:正=入账,负=出账',
    balance_after DECIMAL(12,2) NOT NULL COMMENT '操作后余额快照',
    biz_no        VARCHAR(64)   NOT NULL COMMENT '业务幂等键',
    order_id      BIGINT        DEFAULT NULL,
    remark        VARCHAR(255)  DEFAULT NULL,
    created_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_bal_tx_bizno (biz_no),
    KEY idx_bal_tx_user (user_id, id)
) ENGINE=InnoDB COMMENT='余额流水表';

-- =============================================================
--  手工迁移 SQL（老库升级用；本项目 ddl-auto=update 会自动加列/建表，此处仅作文档）
-- =============================================================
-- ALTER TABLE users
--   ADD COLUMN balance DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '账户余额(元)';
-- （balance_transactions 建表语句同上，直接执行本文件的 CREATE TABLE 即可）

-- =============================================================
--  对账 SQL：校验「账户余额」与「流水累计」是否一致，必须返回空集
-- =============================================================
-- SELECT u.id, u.balance, COALESCE(SUM(t.amount),0) AS flow_sum
-- FROM users u LEFT JOIN balance_transactions t ON t.user_id = u.id
-- GROUP BY u.id HAVING u.balance <> COALESCE(SUM(t.amount),0);

-- -------------------------------------------------------------
-- 14. 后台写操作审计与幂等表 admin_action_logs
--     一张表同时承担两件事：
--       ① 「这笔钱是谁批的」（审计：谁/何时/哪个入口/什么参数/结果如何）
--       ② 「不要重复执行」（幂等：uk_aal_idem 唯一键 + 状态机）
--     运行时由 spring.jpa.hibernate.ddl-auto=update 从实体补建；
--     本段供「全新建库」时一次性建好，与 AdminActionLog 实体逐列保持一致。
--
--     【关键】idempotency_key 必须允许 NULL：
--     页面入口（entry='PAGE'）没有幂等键，MySQL 的 UNIQUE 索引允许多行 NULL，
--     因此页面多次操作不会互相撞键；若填 ''（空串），第二次页面操作就会直接 500。
--     【关键】params_json / precheck_json / snapshot_json / result_json 用 TEXT 而非 JSON：
--     实体侧字段类型是 String，与 RiskScore.reasons 的存法一致；换 JSON 列需要
--     @JdbcTypeCode(SqlTypes.JSON)，与 ddl-auto=update 易冲突。
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS admin_action_logs (
    id               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    proposal_id      VARCHAR(32)  DEFAULT NULL COMMENT '提案ID(pa_xxxxxxxxxx)；页面入口为空',
    action           VARCHAR(40)  NOT NULL COMMENT '动作枚举',
    target_type      VARCHAR(24)  NOT NULL COMMENT 'REFUND_REQUEST/ORDER/PRODUCT/PROMOTION/ORDER_BATCH',
    target_id        BIGINT       DEFAULT NULL COMMENT '对象主键；批量主记录或尚未创建的对象为空',
    params_json      TEXT         DEFAULT NULL COMMENT '最终参数(JSON)',
    params_digest    VARCHAR(40)  DEFAULT NULL COMMENT '参数指纹 sha1，用于 PROPOSAL_MISMATCH 校验',
    precheck_json    TEXT         DEFAULT NULL COMMENT '预检结论(JSON)，证明"当时是合法的"',
    snapshot_json    TEXT         DEFAULT NULL COMMENT '预检时刻关键快照(JSON)，含 expectedStock',
    idempotency_key  VARCHAR(128) DEFAULT NULL COMMENT '幂等键；PROPOSED 行与页面入口均为 NULL(UNIQUE 允许多个 NULL)，CONFIRMED 时由执行器抢占',
    status           VARCHAR(16)  NOT NULL COMMENT 'PROPOSED/CONFIRMED/EXECUTED/FAILED/EXPIRED/CANCELLED',
    operator_id      BIGINT       NOT NULL COMMENT '操作人 users.id',
    operator_role    VARCHAR(16)  DEFAULT NULL COMMENT '实时查库得到的角色 ADMIN/OPS/CS',
    entry            VARCHAR(16)  NOT NULL DEFAULT 'API' COMMENT 'CHAT/PAGE/API 入口区分',
    chat_message_id  BIGINT       DEFAULT NULL COMMENT '关联 ai_chat_messages.id，可回放到那句话',
    result_json      TEXT         DEFAULT NULL COMMENT '执行结果(DTO 或批量 items[])',
    error_code       VARCHAR(40)  DEFAULT NULL COMMENT '失败 subCode',
    error_message    VARCHAR(512) DEFAULT NULL COMMENT '失败说明',
    attempt          INT          NOT NULL DEFAULT 1 COMMENT '重试次数',
    expires_at       DATETIME     DEFAULT NULL COMMENT '提案有效期(默认 +5min)，仅 PROPOSED 有意义',
    ip               VARCHAR(45)  DEFAULT NULL COMMENT '来源 IP',
    user_agent       VARCHAR(255) DEFAULT NULL COMMENT 'UA',
    created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    confirmed_at     DATETIME     DEFAULT NULL,
    executed_at      DATETIME     DEFAULT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_aal_idem      (idempotency_key),
    KEY idx_aal_proposal       (proposal_id),
    KEY idx_aal_operator_time  (operator_id, created_at),
    KEY idx_aal_target_time    (target_type, target_id, created_at),
    KEY idx_aal_status_time    (status, created_at),
    KEY idx_aal_action_time    (action, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='后台写操作审计与幂等';

-- -------------------------------------------------------------
-- 数据清理口径（不做 TTL 重建，见技术设计 10.2 D1）
-- 幂等键保留 30 天：仅作为「数据清理」口径，由运维手工执行，
-- 应用内不加 @Scheduled（项目未开启 @EnableScheduling）。
-- 注意：必须在「同一内容重做必然产生新键」成立的前提下才能清理；
-- 5 个动作的键均与内容相关（补货含 expectedStock、活动是内容指纹），成立。
-- -------------------------------------------------------------
-- DELETE FROM admin_action_logs
--  WHERE created_at < NOW() - INTERVAL 30 DAY
--    AND status IN ('EXECUTED','FAILED','EXPIRED','CANCELLED');

-- =============================================================
--  种子数据（分类 + 示例商品）
-- =============================================================

-- 分类（一级）
INSERT INTO categories (id, name, parent_id, sort_order, icon) VALUES
  (1, '新鲜果蔬', 0, 1, NULL),
  (2, '肉禽蛋品', 0, 2, NULL),
  (3, '海鲜水产', 0, 3, NULL),
  (4, '粮油调味', 0, 4, NULL),
  (5, '乳品烘焙', 0, 5, NULL),
  (6, '休闲零食', 0, 6, NULL),
  (7, '酒水饮料', 0, 7, NULL),
  (8, '个护清洁', 0, 8, NULL)
ON DUPLICATE KEY UPDATE name = VALUES(name);

-- 分类（二级）
INSERT INTO categories (id, name, parent_id, sort_order) VALUES
  (101, '叶菜类', 1, 1),
  (102, '水果类', 1, 2),
  (201, '猪肉', 2, 1),
  (202, '禽类', 2, 2),
  (301, '鱼类', 3, 1),
  (302, '虾蟹', 3, 2),
  (401, '米面', 4, 1),
  (402, '食用油', 4, 2),
  (501, '牛奶', 5, 1),
  (502, '面包糕点', 5, 2),
  (601, '饼干', 6, 1),
  (602, '坚果炒货', 6, 2),
  (701, '饮用水', 7, 1),
  (702, '碳酸饮料', 7, 2),
  (801, '纸品', 8, 1),
  (802, '洗护用品', 8, 2)
ON DUPLICATE KEY UPDATE name = VALUES(name);

-- 示例商品
INSERT INTO products
  (id, category_id, name, subtitle, description, price, original_price, stock, sales, unit, image_url, images, status)
VALUES
  (1, 102, '红富士苹果', '脆甜多汁 产地直供', '精选山东烟台红富士苹果，个大皮薄，果肉脆甜。', 9.90, 12.90, 500, 328, '斤', NULL, NULL, 1),
  (2, 102, '赣南脐橙', '皮薄肉嫩 汁水丰盈', '江西赣南脐橙，酸甜可口，富含维生素C。', 6.80, 8.80, 800, 456, '斤', NULL, NULL, 1),
  (3, 101, '有机菠菜', '有机认证 现摘现发', '有机种植菠菜，鲜嫩无农药残留。', 4.50, 5.50, 300, 210, '把', NULL, NULL, 1),
  (4, 201, '五花肉', '肥瘦相间 红烧首选', '当日现宰五花肉，肥瘦均匀。', 19.90, 23.90, 200, 189, '斤', NULL, NULL, 1),
  (5, 202, '农家土鸡蛋', '散养土鸡 蛋黄饱满', '农家散养土鸡蛋，营养丰富。', 15.90, 19.90, 400, 267, '盒', NULL, NULL, 1),
  (6, 302, '基围虾', '鲜活直送 肉质Q弹', '鲜活基围虾，当天捕捞当天送。', 39.90, 49.90, 150, 98, '斤', NULL, NULL, 1),
  (7, 401, '东北大米', '五常稻花香 颗粒饱满', '五常稻花香大米，香糯可口。', 59.90, 69.90, 600, 412, '袋', NULL, NULL, 1),
  (8, 402, '花生油', '物理压榨 香浓醇厚', '一级压榨花生油，非转基因。', 89.90, 99.90, 300, 156, '桶', NULL, NULL, 1),
  (9, 501, '纯牛奶', '牧场直供 3.6g蛋白', '高品质纯牛奶，每100ml含3.6g优质蛋白。', 45.90, 55.90, 500, 389, '箱', NULL, NULL, 1),
  (10, 601, '曲奇饼干', '黄油曲奇 酥脆香甜', '丹麦风味黄油曲奇饼干。', 19.90, 25.90, 700, 534, '盒', NULL, NULL, 1),
  (11, 701, '天然矿泉水', '弱碱性 深层岩层水', '深层岩层天然矿泉水，弱碱性。', 2.00, 2.50, 2000, 980, '瓶', NULL, NULL, 1),
  (12, 801, '抽纸', '原生木浆 柔软亲肤', '原生木浆抽纸，3层加厚。', 12.90, 16.90, 900, 645, '提', NULL, NULL, 1)
ON DUPLICATE KEY UPDATE name = VALUES(name);
