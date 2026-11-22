/*
 Navicat Premium Dump SQL

 Source Server         : qq_db
 Source Server Type    : MySQL
 Source Server Version : 80034 (8.0.34)
 Source Host           : localhost:3306
 Source Schema         : darunfa

 Target Server Type    : MySQL
 Target Server Version : 80034 (8.0.34)
 File Encoding         : 65001

 Date: 01/11/2026 16:24:05
*/

-- 建库 + 切库：保证 `mysql -uroot -p < schema.sql` 直接可用，无需手动建库或指定库名
CREATE DATABASE IF NOT EXISTS `darunfa`
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_0900_ai_ci;
USE `darunfa`;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for addresses
-- ----------------------------
DROP TABLE IF EXISTS `addresses`;
CREATE TABLE `addresses`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `city` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `created_at` datetime(6) NULL DEFAULT NULL,
  `detail` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `district` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `is_default` bit(1) NOT NULL,
  `province` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `receiver_name` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `receiver_phone` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 5 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of addresses
-- ----------------------------
INSERT INTO `addresses` VALUES (2, NULL, '2026-09-15 20:25:37.765736', '建国路 88 号', NULL, b'1', '北京市朝阳区', '李四', '13900139000', 5);
INSERT INTO `addresses` VALUES (3, NULL, '2026-09-15 20:28:58.041411', 'Century Ave 100', NULL, b'0', 'Shanghai Pudong', 'Wang', '13700137000', 5);
INSERT INTO `addresses` VALUES (4, NULL, '2026-09-15 20:43:05.279944', '宝盖镇闽南理工学院宝盖校区', NULL, b'1', '福建省泉州市石狮市', '张三', '13107993087', 2);

-- ----------------------------
-- Table structure for admin_action_logs
-- ----------------------------
DROP TABLE IF EXISTS `admin_action_logs`;
CREATE TABLE `admin_action_logs`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `action` varchar(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `attempt` int NOT NULL,
  `chat_message_id` bigint NULL DEFAULT NULL,
  `confirmed_at` datetime(6) NULL DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `entry` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `error_code` varchar(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `error_message` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `executed_at` datetime(6) NULL DEFAULT NULL,
  `expires_at` datetime(6) NULL DEFAULT NULL,
  `idempotency_key` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `ip` varchar(45) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `operator_id` bigint NOT NULL,
  `operator_role` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `params_digest` varchar(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `params_json` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL,
  `precheck_json` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL,
  `proposal_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `result_json` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL,
  `snapshot_json` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL,
  `status` enum('CANCELLED','CONFIRMED','EXECUTED','EXPIRED','FAILED','PROPOSED') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `target_id` bigint NULL DEFAULT NULL,
  `target_type` varchar(24) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `user_agent` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `proposal_json` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_aal_idem`(`idempotency_key` ASC) USING BTREE,
  INDEX `idx_aal_proposal`(`proposal_id` ASC) USING BTREE,
  INDEX `idx_aal_operator_time`(`operator_id` ASC, `created_at` ASC) USING BTREE,
  INDEX `idx_aal_target_time`(`target_type` ASC, `target_id` ASC, `created_at` ASC) USING BTREE,
  INDEX `idx_aal_status_time`(`status` ASC, `created_at` ASC) USING BTREE,
  INDEX `idx_aal_action_time`(`action` ASC, `created_at` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 89 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of admin_action_logs
-- ----------------------------
INSERT INTO `admin_action_logs` VALUES (1, 'RISK_SCAN', 2, NULL, '2026-10-22 14:54:13.302669', '2026-10-22 14:54:13.303707', 'PAGE', 'TARGET_NOT_FOUND', '订单不存在: 999999', '2026-10-22 14:54:13.312275', NULL, NULL, NULL, 1, NULL, '7da298ed08f98f1f109a8efe3f9ecd40994dea10', '{\"orderId\":999999}', NULL, NULL, NULL, NULL, 'FAILED', 999999, 'ORDER', NULL, NULL);
INSERT INTO `admin_action_logs` VALUES (2, 'STOCK_REPLENISH', 1, NULL, '2026-10-22 14:54:29.162797', '2026-10-22 14:54:29.162797', 'PAGE', NULL, NULL, '2026-10-22 14:54:29.170319', NULL, NULL, NULL, 1, NULL, 'dcf1e6a20370d627c711941b30229cdbdc511bbf', '{\"delta\":3,\"expectedStock\":null}', NULL, NULL, '{\"categoryId\":16,\"createdAt\":\"2026-09-07T16:42:59.628768\",\"description\":\"一级压榨花生油，非转基因。\",\"id\":8,\"imageUrl\":\"p030\",\"images\":null,\"name\":\"花生油\",\"originalPrice\":99.90,\"price\":89.90,\"sales\":29,\"status\":1,\"stock\":274,\"subtitle\":\"物理压榨 香浓醇厚\",\"unit\":\"桶\",\"updatedAt\":\"2026-10-22T14:54:29.1676615\"}', NULL, 'EXECUTED', 8, 'PRODUCT', NULL, NULL);
INSERT INTO `admin_action_logs` VALUES (3, 'STOCK_REPLENISH', 1, NULL, '2026-10-22 14:54:34.796606', '2026-10-22 14:54:34.796606', 'PAGE', NULL, NULL, '2026-10-22 14:54:34.802817', NULL, NULL, NULL, 1, NULL, 'e489ba925cd13aedd73842c45d63aa28ea447a19', '{\"delta\":-3,\"expectedStock\":null}', NULL, NULL, '{\"categoryId\":16,\"createdAt\":\"2026-09-07T16:42:59.628768\",\"description\":\"一级压榨花生油，非转基因。\",\"id\":8,\"imageUrl\":\"p030\",\"images\":null,\"name\":\"花生油\",\"originalPrice\":99.90,\"price\":89.90,\"sales\":29,\"status\":1,\"stock\":271,\"subtitle\":\"物理压榨 香浓醇厚\",\"unit\":\"桶\",\"updatedAt\":\"2026-10-22T14:54:34.8013033\"}', NULL, 'EXECUTED', 8, 'PRODUCT', NULL, NULL);
INSERT INTO `admin_action_logs` VALUES (4, 'REFUND_APPROVE', 1, NULL, '2026-10-22 14:54:40.340469', '2026-10-22 14:54:40.340469', 'PAGE', NULL, NULL, '2026-10-22 14:54:40.359339', NULL, NULL, NULL, 1, NULL, '5f45383361cdebcc1935293825ddfdbd57ff24d8', '{\"decision\":\"APPROVE\"}', NULL, NULL, '{\"amount\":4.50,\"createdAt\":\"2026-09-16 12:12:33\",\"handledAt\":\"2026-10-22 14:54:40\",\"id\":12,\"orderId\":46,\"orderNo\":\"DR20260916121226127\",\"previousStatus\":\"PAID\",\"previousStatusLabel\":\"待发货\",\"products\":null,\"reason\":\"QA自审用例\",\"rejectReason\":null,\"status\":\"APPROVED\",\"statusLabel\":\"已同意\",\"userId\":4,\"userName\":\"客服小李\",\"userPhone\":\"13800000002\"}', NULL, 'EXECUTED', 12, 'REFUND_REQUEST', NULL, NULL);
INSERT INTO `admin_action_logs` VALUES (5, 'REFUND_APPROVE', 1, NULL, '2026-10-22 14:55:07.674259', '2026-10-22 14:54:55.888004', 'CHAT', NULL, NULL, '2026-10-22 14:55:07.687069', '2026-10-22 14:59:55.883657', 'refund:14:APPROVE', '0:0:0:0:0:0:0:1', 1, 'ADMIN', '64cbb8654539f949464b4e342640e84b107f82be', '{\"decision\":\"APPROVE\",\"rejectReason\":null}', '{\"ok\":true,\"checkedAt\":\"2026-10-22T14:54:52\",\"checks\":[{\"code\":\"PERMISSION\",\"ok\":true,\"message\":\"具备 order:view 或 cs:manage\"},{\"code\":\"PARAM\",\"ok\":true,\"message\":\"参数完整（决策=APPROVE）\"},{\"code\":\"TARGET\",\"ok\":true,\"message\":\"退款单 #14 已定位\"},{\"code\":\"REFUND_PENDING\",\"ok\":true,\"message\":\"退款单待处理\"},{\"code\":\"ORDER_REFUNDING\",\"ok\":true,\"message\":\"订单处于退款中\"},{\"code\":\"SELF_APPROVAL\",\"ok\":true,\"message\":\"审批人非买家\"}]}', 'pa_4441cb0d2c', '{\"amount\":4.50,\"createdAt\":\"2026-09-16 12:45:31\",\"handledAt\":\"2026-10-22 14:55:07\",\"id\":14,\"orderId\":53,\"orderNo\":\"DR20260916124527232\",\"previousStatus\":\"PAID\",\"previousStatusLabel\":\"待发货\",\"products\":null,\"reason\":\"QA复验自审\",\"rejectReason\":null,\"status\":\"APPROVED\",\"statusLabel\":\"已同意\",\"userId\":4,\"userName\":\"客服小李\",\"userPhone\":\"13800000002\"}', '{\"refundStatus\":\"PENDING\",\"orderStatus\":\"REFUNDING\",\"payAmount\":\"4.50\",\"orderPayAmount\":\"4.50\",\"userId\":4,\"userName\":\"客服小李\",\"orderNo\":\"DR20260916124527232\",\"refundReason\":\"QA复验自审\",\"itemCount\":1}', 'EXECUTED', 14, 'REFUND_REQUEST', 'curl/8.21.0', NULL);
INSERT INTO `admin_action_logs` VALUES (6, 'STOCK_REPLENISH', 2, NULL, '2026-10-22 14:55:27.407081', '2026-10-22 14:55:27.407081', 'PAGE', 'CONFLICT_STOCK_CHANGED', '库存已变化（你确认时是 9999 件，现在是 271 件），为避免补错请刷新后重新确认。', '2026-10-22 14:55:27.421278', NULL, NULL, NULL, 1, NULL, '85d456fa28da834cf3197a405c8c55784fbd5ffe', '{\"delta\":1,\"expectedStock\":9999}', NULL, NULL, NULL, NULL, 'FAILED', 8, 'PRODUCT', NULL, NULL);
INSERT INTO `admin_action_logs` VALUES (7, 'STOCK_REPLENISH', 1, NULL, '2026-10-22 14:55:27.769545', '2026-10-22 14:55:27.770546', 'PAGE', NULL, NULL, '2026-10-22 14:55:27.776217', NULL, NULL, NULL, 1, NULL, '345d8dbf18ce3774064a77f867528eb49439e3cf', '{\"delta\":1,\"expectedStock\":271}', NULL, NULL, '{\"categoryId\":16,\"createdAt\":\"2026-09-07T16:42:59.628768\",\"description\":\"一级压榨花生油，非转基因。\",\"id\":8,\"imageUrl\":\"p030\",\"images\":null,\"name\":\"花生油\",\"originalPrice\":99.90,\"price\":89.90,\"sales\":29,\"status\":1,\"stock\":272,\"subtitle\":\"物理压榨 香浓醇厚\",\"unit\":\"桶\",\"updatedAt\":\"2026-10-22T14:55:27.774713\"}', NULL, 'EXECUTED', 8, 'PRODUCT', NULL, NULL);
INSERT INTO `admin_action_logs` VALUES (8, 'STOCK_REPLENISH', 1, NULL, '2026-10-22 14:55:28.123575', '2026-10-22 14:55:28.123575', 'PAGE', NULL, NULL, '2026-10-22 14:55:28.127681', NULL, NULL, NULL, 1, NULL, '52d033aea0978430c7fdb55e18b89ec0a750c925', '{\"delta\":-1,\"expectedStock\":null}', NULL, NULL, '{\"categoryId\":16,\"createdAt\":\"2026-09-07T16:42:59.628768\",\"description\":\"一级压榨花生油，非转基因。\",\"id\":8,\"imageUrl\":\"p030\",\"images\":null,\"name\":\"花生油\",\"originalPrice\":99.90,\"price\":89.90,\"sales\":29,\"status\":1,\"stock\":271,\"subtitle\":\"物理压榨 香浓醇厚\",\"unit\":\"桶\",\"updatedAt\":\"2026-10-22T14:55:28.126081\"}', NULL, 'EXECUTED', 8, 'PRODUCT', NULL, NULL);
INSERT INTO `admin_action_logs` VALUES (9, 'REFUND_APPROVE', 1, NULL, '2026-10-22 15:06:30.190651', '2026-10-22 15:06:29.789012', 'CHAT', NULL, NULL, '2026-10-22 15:06:30.223941', '2026-10-22 15:11:29.784349', 'refund:6:APPROVE', '0:0:0:0:0:0:0:1', 1, 'ADMIN', '64cbb8654539f949464b4e342640e84b107f82be', '{\"decision\":\"APPROVE\",\"rejectReason\":null}', '{\"ok\":true,\"checkedAt\":\"2026-10-22T15:06:27\",\"checks\":[{\"code\":\"PERMISSION\",\"ok\":true,\"message\":\"具备 order:view 或 cs:manage\"},{\"code\":\"PARAM\",\"ok\":true,\"message\":\"参数完整（决策=APPROVE）\"},{\"code\":\"TARGET\",\"ok\":true,\"message\":\"退款单 #6 已定位\"},{\"code\":\"REFUND_PENDING\",\"ok\":true,\"message\":\"退款单待处理\"},{\"code\":\"ORDER_REFUNDING\",\"ok\":true,\"message\":\"订单处于退款中\"},{\"code\":\"SELF_APPROVAL\",\"ok\":true,\"message\":\"审批人非买家\"}]}', 'pa_563eb5c71d', '{\"amount\":79.60,\"createdAt\":\"2026-09-16 09:01:18\",\"handledAt\":\"2026-10-22 15:06:30\",\"id\":6,\"orderId\":2,\"orderNo\":\"O20260908002\",\"previousStatus\":\"SHIPPED\",\"previousStatusLabel\":\"待收货\",\"products\":null,\"reason\":\"发货太晚了\",\"rejectReason\":null,\"status\":\"APPROVED\",\"statusLabel\":\"已同意\",\"userId\":2,\"userName\":\"12\",\"userPhone\":null}', '{\"refundStatus\":\"PENDING\",\"orderStatus\":\"REFUNDING\",\"payAmount\":\"79.60\",\"orderPayAmount\":\"79.60\",\"userId\":2,\"userName\":\"12\",\"orderNo\":\"O20260908002\",\"refundReason\":\"发货太晚了\",\"itemCount\":0}', 'EXECUTED', 6, 'REFUND_REQUEST', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/148.0.0.0 Safari/537.36', NULL);
INSERT INTO `admin_action_logs` VALUES (10, 'RISK_SCAN', 1, NULL, NULL, '2026-10-22 15:09:58.804740', 'CHAT', NULL, NULL, NULL, '2026-10-22 15:14:58.803719', NULL, '0:0:0:0:0:0:0:1', 1, 'ADMIN', 'a7399dadad6247765daffb8b3762e9035a6e13fe', '{\"orderNo\":\"DR20260918103315261\",\"mode\":\"SINGLE\",\"rescan\":false}', '{\"ok\":true,\"checkedAt\":\"2026-10-22T15:09:56\",\"checks\":[{\"code\":\"PERMISSION\",\"ok\":true,\"message\":\"具备 risk:scan\"},{\"code\":\"MODE\",\"ok\":true,\"message\":\"单条扫描\"},{\"code\":\"PARAM\",\"ok\":true,\"message\":\"订单号已提供\"},{\"code\":\"TARGET\",\"ok\":true,\"message\":\"订单 DR20260918103315261 已定位\"},{\"code\":\"ORDER_STATE\",\"ok\":true,\"message\":\"订单状态允许分析（待发货）\"},{\"code\":\"ALREADY_SCANNED\",\"ok\":true,\"message\":\"该单尚未评分\"}]}', 'pa_959ddec66d', NULL, '{\"orderNo\":\"DR20260918103315261\",\"amount\":\"94.80\",\"status\":\"PAID\",\"paidAt\":\"2026-09-18T10:33:15\",\"currentScore\":null,\"currentLevel\":null,\"lastScannedAt\":null,\"waitedHours\":820}', 'EXPIRED', 58, 'ORDER', 'curl/8.21.0', NULL);
INSERT INTO `admin_action_logs` VALUES (11, 'RISK_SCAN', 1, NULL, '2026-10-22 15:15:38.883057', '2026-10-22 15:15:38.616799', 'CHAT', NULL, NULL, '2026-10-22 15:15:40.272137', '2026-10-22 15:20:38.612625', 'risk:58:202610221515', '0:0:0:0:0:0:0:1', 1, 'ADMIN', 'a7399dadad6247765daffb8b3762e9035a6e13fe', '{\"orderNo\":\"DR20260918103315261\",\"mode\":\"SINGLE\",\"rescan\":false}', '{\"ok\":true,\"checkedAt\":\"2026-10-22T15:15:36\",\"checks\":[{\"code\":\"PERMISSION\",\"ok\":true,\"message\":\"具备 risk:scan\"},{\"code\":\"MODE\",\"ok\":true,\"message\":\"单条扫描\"},{\"code\":\"PARAM\",\"ok\":true,\"message\":\"订单号已提供\"},{\"code\":\"TARGET\",\"ok\":true,\"message\":\"订单 DR20260918103315261 已定位\"},{\"code\":\"ORDER_STATE\",\"ok\":true,\"message\":\"订单状态允许分析（待发货）\"},{\"code\":\"ALREADY_SCANNED\",\"ok\":true,\"message\":\"该单尚未评分\"}]}', 'pa_c2521de8b8', '{\"createdAt\":\"2026-10-22T15:15:40.2552991\",\"id\":73,\"isRead\":false,\"level\":\"LOW\",\"llmAction\":\"放行\",\"llmSummary\":\"规则引擎没有命中任何风险信号；订单金额较低（实付94.80元），商品为常规日用品，数量正常；收货地址是高校校区，且下单时间在工作日白天，符合学生消费特征，没有明显异常。\",\"orderId\":58,\"orderNo\":\"DR20260918103315261\",\"ruleScore\":0,\"score\":12,\"signals\":[],\"source\":\"HYBRID\",\"suggestedAction\":\"放行\"}', '{\"orderNo\":\"DR20260918103315261\",\"amount\":\"94.80\",\"status\":\"PAID\",\"paidAt\":\"2026-09-18T10:33:15\",\"currentScore\":null,\"currentLevel\":null,\"lastScannedAt\":null,\"waitedHours\":820}', 'EXECUTED', 58, 'ORDER', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/148.0.0.0 Safari/537.36', NULL);
INSERT INTO `admin_action_logs` VALUES (12, 'STOCK_REPLENISH', 1, NULL, '2026-10-22 15:18:33.675164', '2026-10-22 15:18:33.675677', 'PAGE', NULL, NULL, '2026-10-22 15:18:33.680509', NULL, NULL, NULL, 1, 'ADMIN', 'f58ac08d822df3e8122b2446b5d887a3d4784429', '{\"delta\":1,\"expectedStock\":null}', NULL, NULL, '{\"categoryId\":16,\"createdAt\":\"2026-09-07T16:42:59.628768\",\"description\":\"一级压榨花生油，非转基因。\",\"id\":8,\"imageUrl\":\"p030\",\"images\":null,\"name\":\"花生油\",\"originalPrice\":99.90,\"price\":89.90,\"sales\":29,\"status\":1,\"stock\":272,\"subtitle\":\"物理压榨 香浓醇厚\",\"unit\":\"桶\",\"updatedAt\":\"2026-10-22T15:18:33.679483\"}', NULL, 'EXECUTED', 8, 'PRODUCT', NULL, NULL);
INSERT INTO `admin_action_logs` VALUES (13, 'STOCK_REPLENISH', 1, NULL, '2026-10-22 15:18:33.953453', '2026-10-22 15:18:33.954573', 'PAGE', NULL, NULL, '2026-10-22 15:18:33.958712', NULL, NULL, NULL, 1, 'ADMIN', '52d033aea0978430c7fdb55e18b89ec0a750c925', '{\"delta\":-1,\"expectedStock\":null}', NULL, NULL, '{\"categoryId\":16,\"createdAt\":\"2026-09-07T16:42:59.628768\",\"description\":\"一级压榨花生油，非转基因。\",\"id\":8,\"imageUrl\":\"p030\",\"images\":null,\"name\":\"花生油\",\"originalPrice\":99.90,\"price\":89.90,\"sales\":29,\"status\":1,\"stock\":271,\"subtitle\":\"物理压榨 香浓醇厚\",\"unit\":\"桶\",\"updatedAt\":\"2026-10-22T15:18:33.957084\"}', NULL, 'EXECUTED', 8, 'PRODUCT', NULL, NULL);
INSERT INTO `admin_action_logs` VALUES (14, 'RISK_SCAN', 1, NULL, '2026-10-22 15:20:47.619799', '2026-10-22 15:20:47.291672', 'CHAT', NULL, NULL, '2026-10-22 15:20:49.211992', '2026-10-22 15:25:47.286608', 'risk:54:202610221520', '0:0:0:0:0:0:0:1', 1, 'ADMIN', '61d12f89045f17eb0729ee03b1d3dd0635cd9626', '{\"orderNo\":\"DR20260916195351194\",\"mode\":\"SINGLE\",\"rescan\":false}', '{\"ok\":true,\"checkedAt\":\"2026-10-22T15:20:45\",\"checks\":[{\"code\":\"PERMISSION\",\"ok\":true,\"message\":\"具备 risk:scan\"},{\"code\":\"MODE\",\"ok\":true,\"message\":\"单条扫描\"},{\"code\":\"PARAM\",\"ok\":true,\"message\":\"订单号已提供\"},{\"code\":\"TARGET\",\"ok\":true,\"message\":\"订单 DR20260916195351194 已定位\"},{\"code\":\"ORDER_STATE\",\"ok\":true,\"message\":\"订单状态允许分析（待发货）\"},{\"code\":\"ALREADY_SCANNED\",\"ok\":true,\"message\":\"该单尚未评分\"}]}', 'pa_0601d72221', '{\"createdAt\":\"2026-10-22T15:20:49.1945772\",\"id\":74,\"isRead\":false,\"level\":\"LOW\",\"llmAction\":\"放行\",\"llmSummary\":\"规则引擎没有发现任何风险信号，订单金额较小且实付与应付之间的差额是由正常的优惠导致的；收货地址为高校校区，符合学生日常消费特征，没有发现异常迹象。\",\"orderId\":54,\"orderNo\":\"DR20260916195351194\",\"ruleScore\":0,\"score\":8,\"signals\":[],\"source\":\"HYBRID\",\"suggestedAction\":\"放行\"}', '{\"orderNo\":\"DR20260916195351194\",\"amount\":\"109.80\",\"status\":\"PAID\",\"paidAt\":\"2026-09-16T19:53:51\",\"currentScore\":null,\"currentLevel\":null,\"lastScannedAt\":null,\"waitedHours\":859}', 'EXECUTED', 54, 'ORDER', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/148.0.0.0 Safari/537.36', NULL);
INSERT INTO `admin_action_logs` VALUES (15, 'REFUND_APPROVE', 1, NULL, '2026-10-22 15:23:03.144982', '2026-10-22 15:22:53.171653', 'CHAT', NULL, NULL, '2026-10-22 15:23:03.183708', '2026-10-22 15:27:53.165995', 'refund:15:APPROVE', '127.0.0.1', 3, 'OPS', '64cbb8654539f949464b4e342640e84b107f82be', '{\"decision\":\"APPROVE\",\"rejectReason\":null}', '{\"ok\":true,\"checkedAt\":\"2026-10-22T15:22:51\",\"checks\":[{\"code\":\"PERMISSION\",\"ok\":true,\"message\":\"具备 order:view 或 cs:manage\"},{\"code\":\"PARAM\",\"ok\":true,\"message\":\"参数完整（决策=APPROVE）\"},{\"code\":\"TARGET\",\"ok\":true,\"message\":\"退款单 #15 已定位\"},{\"code\":\"REFUND_PENDING\",\"ok\":true,\"message\":\"退款单待处理\"},{\"code\":\"ORDER_REFUNDING\",\"ok\":true,\"message\":\"订单处于退款中\"},{\"code\":\"SELF_APPROVAL\",\"ok\":true,\"message\":\"审批人非买家\"}]}', 'pa_588c1a78cf', '{\"amount\":19.90,\"createdAt\":\"2026-10-22 15:22:24\",\"handledAt\":\"2026-10-22 15:23:03\",\"id\":15,\"orderId\":43,\"orderNo\":\"DR20260916112719998\",\"previousStatus\":\"PAID\",\"previousStatusLabel\":\"待发货\",\"products\":null,\"reason\":\"QA独立验证-退款审批链路\",\"rejectReason\":null,\"status\":\"APPROVED\",\"statusLabel\":\"已同意\",\"userId\":5,\"userName\":\"测试用户\",\"userPhone\":\"13800000003\"}', '{\"refundStatus\":\"PENDING\",\"orderStatus\":\"REFUNDING\",\"payAmount\":\"19.90\",\"orderPayAmount\":\"19.90\",\"userId\":5,\"userName\":\"测试用户\",\"orderNo\":\"DR20260916112719998\",\"refundReason\":\"QA独立验证-退款审批链路\",\"itemCount\":1}', 'EXECUTED', 15, 'REFUND_REQUEST', 'curl/8.21.0', NULL);
INSERT INTO `admin_action_logs` VALUES (16, 'STOCK_REPLENISH', 2, NULL, '2026-10-22 15:24:42.345008', '2026-10-22 15:24:42.345514', 'PAGE', 'CONFLICT_STOCK_CHANGED', '库存已变化（你确认时是 696 件，现在是 697 件），为避免补错请刷新后重新确认。', '2026-10-22 15:24:42.352752', NULL, NULL, NULL, 1, 'ADMIN', '96c463b2ae1d5f45f98c9f56eb5ae56322e1493a', '{\"delta\":100,\"expectedStock\":696}', NULL, NULL, NULL, NULL, 'FAILED', 10, 'PRODUCT', NULL, NULL);
INSERT INTO `admin_action_logs` VALUES (17, 'ORDER_SHIP', 2, NULL, '2026-10-22 15:24:42.861162', '2026-10-22 15:24:42.861162', 'PAGE', 'BUSINESS_ERROR', '仅待发货订单可发货', '2026-10-22 15:24:42.865692', NULL, NULL, NULL, 1, 'ADMIN', 'bd2701712391bf78617ec590302ecd0bdaa6abae', '{\"company\":\"顺丰速运\",\"trackingNo\":\"SF999000111\"}', NULL, NULL, NULL, NULL, 'FAILED', 43, 'ORDER', NULL, NULL);
INSERT INTO `admin_action_logs` VALUES (18, 'ORDER_SHIP', 2, NULL, '2026-10-22 15:24:43.405145', '2026-10-22 15:24:43.405145', 'PAGE', 'BUSINESS_ERROR', '仅待发货订单可发货', '2026-10-22 15:24:43.409176', NULL, NULL, NULL, 1, 'ADMIN', 'bd2701712391bf78617ec590302ecd0bdaa6abae', '{\"company\":\"顺丰速运\",\"trackingNo\":\"SF999000111\"}', NULL, NULL, NULL, NULL, 'FAILED', 3, 'ORDER', NULL, NULL);
INSERT INTO `admin_action_logs` VALUES (19, 'STOCK_REPLENISH', 1, NULL, '2026-10-22 15:25:18.198834', '2026-10-22 15:25:10.494246', 'CHAT', NULL, NULL, '2026-10-22 15:25:18.205639', '2026-10-22 15:30:10.493246', 'restock:2:798:100', '127.0.0.1', 1, 'ADMIN', 'e86cc65779bd323110b75afee110b0b9147dbaf8', '{\"mode\":\"DELTA\",\"delta\":100,\"note\":\"AI 对话补货\"}', '{\"ok\":true,\"checkedAt\":\"2026-10-22T15:25:08\",\"checks\":[{\"code\":\"PERMISSION\",\"ok\":true,\"message\":\"具备 product:edit\"},{\"code\":\"TARGET\",\"ok\":true,\"message\":\"商品「赣南脐橙」已定位\"},{\"code\":\"QTY\",\"ok\":true,\"message\":\"补货 100 件\"},{\"code\":\"RANGE\",\"ok\":true,\"message\":\"补后库存 898 件合法\"}]}', 'pa_c3b5f7a9b3', '{\"categoryId\":9,\"createdAt\":\"2026-09-07T16:42:59.623131\",\"description\":\"江西赣南脐橙，酸甜可口，富含维生素C。\",\"id\":2,\"imageUrl\":\"p003\",\"images\":null,\"name\":\"赣南脐橙\",\"originalPrice\":8.80,\"price\":6.80,\"sales\":2,\"status\":1,\"stock\":898,\"subtitle\":\"皮薄肉嫩 汁水丰盈\",\"unit\":\"斤\",\"updatedAt\":\"2026-10-22T15:25:18.2031808\"}', '{\"productId\":2,\"productName\":\"赣南脐橙\",\"expectedStock\":798,\"stockBefore\":798,\"stockAfter\":898,\"delta\":100,\"unit\":\"斤\",\"threshold\":10,\"sales\":2}', 'EXECUTED', 2, 'PRODUCT', 'curl/8.21.0', NULL);
INSERT INTO `admin_action_logs` VALUES (20, 'STOCK_REPLENISH', 1, NULL, NULL, '2026-10-22 15:25:32.548885', 'CHAT', NULL, NULL, NULL, '2026-10-22 15:30:32.548383', NULL, '127.0.0.1', 1, 'ADMIN', '70fbd2733a603404b777bd4c5a29c71ab72bdf03', '{\"mode\":\"DELTA\",\"delta\":10,\"note\":\"AI 对话补货\"}', '{\"ok\":true,\"checkedAt\":\"2026-10-22T15:25:31\",\"checks\":[{\"code\":\"PERMISSION\",\"ok\":true,\"message\":\"具备 product:edit\"},{\"code\":\"TARGET\",\"ok\":true,\"message\":\"商品「东北大米」已定位\"},{\"code\":\"QTY\",\"ok\":true,\"message\":\"补货 10 件\"},{\"code\":\"RANGE\",\"ok\":true,\"message\":\"补后库存 605 件合法\"}]}', 'pa_edbd663319', NULL, '{\"productId\":7,\"productName\":\"东北大米\",\"expectedStock\":595,\"stockBefore\":595,\"stockAfter\":605,\"delta\":10,\"unit\":\"袋\",\"threshold\":10,\"sales\":5}', 'EXPIRED', 7, 'PRODUCT', 'curl/8.21.0', NULL);
INSERT INTO `admin_action_logs` VALUES (22, 'STOCK_REPLENISH', 1, NULL, NULL, '2026-10-22 15:25:49.576863', 'CHAT', NULL, NULL, NULL, '2026-10-22 15:30:49.576863', NULL, '127.0.0.1', 1, 'ADMIN', '2942a37d949a379b493c8407a4d60b945a3b349f', '{\"mode\":\"DELTA\",\"delta\":20,\"note\":\"AI 对话补货\"}', '{\"ok\":true,\"checkedAt\":\"2026-10-22T15:25:47\",\"checks\":[{\"code\":\"PERMISSION\",\"ok\":true,\"message\":\"具备 product:edit\"},{\"code\":\"TARGET\",\"ok\":true,\"message\":\"商品「纯牛奶」已定位\"},{\"code\":\"QTY\",\"ok\":true,\"message\":\"补货 20 件\"},{\"code\":\"RANGE\",\"ok\":true,\"message\":\"补后库存 518 件合法\"}]}', 'pa_13ec27a987', NULL, '{\"productId\":9,\"productName\":\"纯牛奶\",\"expectedStock\":498,\"stockBefore\":498,\"stockAfter\":518,\"delta\":20,\"unit\":\"箱\",\"threshold\":10,\"sales\":2}', 'EXPIRED', 9, 'PRODUCT', 'curl/8.21.0', NULL);
INSERT INTO `admin_action_logs` VALUES (23, 'STOCK_REPLENISH', 1, NULL, '2026-10-22 15:25:58.245027', '2026-10-22 15:25:58.245027', 'PAGE', NULL, NULL, '2026-10-22 15:25:58.250646', NULL, NULL, NULL, 1, 'ADMIN', 'cfa368549dded52de1b9bc2f7b7502d16e517096', '{\"delta\":-100,\"expectedStock\":null}', NULL, NULL, '{\"categoryId\":9,\"createdAt\":\"2026-09-07T16:42:59.623131\",\"description\":\"江西赣南脐橙，酸甜可口，富含维生素C。\",\"id\":2,\"imageUrl\":\"p003\",\"images\":null,\"name\":\"赣南脐橙\",\"originalPrice\":8.80,\"price\":6.80,\"sales\":2,\"status\":1,\"stock\":798,\"subtitle\":\"皮薄肉嫩 汁水丰盈\",\"unit\":\"斤\",\"updatedAt\":\"2026-10-22T15:25:58.2496423\"}', NULL, 'EXECUTED', 2, 'PRODUCT', NULL, NULL);
INSERT INTO `admin_action_logs` VALUES (24, 'REFUND_REJECT', 1, NULL, '2026-10-22 15:25:58.813291', '2026-10-22 15:25:58.813836', 'PAGE', NULL, NULL, '2026-10-22 15:25:58.819933', NULL, NULL, NULL, 1, 'ADMIN', '349d679e745602184edd4346c6a810396f04dc6a', '{\"decision\":\"REJECT\",\"rejectReason\":\"QA验证后回滚-禁止自审用例\"}', NULL, NULL, '{\"amount\":47.92,\"createdAt\":\"2026-10-22 15:22:32\",\"handledAt\":\"2026-10-22 15:25:58\",\"id\":16,\"orderId\":3,\"orderNo\":\"O20260908003\",\"previousStatus\":\"SHIPPED\",\"previousStatusLabel\":\"待收货\",\"products\":null,\"reason\":\"QA独立验证-禁止自审\",\"rejectReason\":\"QA验证后回滚-禁止自审用例\",\"status\":\"REJECTED\",\"statusLabel\":\"已拒绝\",\"userId\":4,\"userName\":\"客服小李\",\"userPhone\":\"13800000002\"}', NULL, 'EXECUTED', 16, 'REFUND_REQUEST', NULL, NULL);
INSERT INTO `admin_action_logs` VALUES (25, 'ORDER_SHIP', 1, NULL, '2026-10-22 15:26:22.721194', '2026-10-22 15:26:22.721194', 'PAGE', NULL, NULL, '2026-10-22 15:26:22.731346', NULL, NULL, NULL, 1, 'ADMIN', 'dfeb49ec4b2ae72fcab241696dd7782df74a93d2', '{\"company\":\"顺丰速运\",\"trackingNo\":\"SFQA0000123456\"}', NULL, NULL, '{\"cancelledAt\":null,\"completedAt\":null,\"createdAt\":\"2026-09-16 19:53:51\",\"discountAmount\":20.00,\"id\":54,\"items\":[{\"id\":49,\"price\":39.90,\"productId\":6,\"productImage\":\"p059\",\"productName\":\"基围虾\",\"quantity\":1,\"reviewed\":null,\"subtotal\":39.90},{\"id\":50,\"price\":89.90,\"productId\":8,\"productImage\":\"p030\",\"productName\":\"花生油\",\"quantity\":1,\"reviewed\":null,\"subtotal\":89.90}],\"orderNo\":\"DR20260916195351194\",\"paidAt\":\"2026-09-16 19:53:51\",\"payAmount\":109.80,\"promotionId\":1,\"receiverAddress\":\"福建省泉州市石狮市 宝盖镇闽南理工学院宝盖校区\",\"receiverName\":\"张三\",\"receiverPhone\":\"13107993087\",\"refundReason\":null,\"refundRejectReason\":null,\"refundRequestedAt\":null,\"refundStatus\":null,\"refundStatusLabel\":null,\"remark\":null,\"reviewed\":null,\"riskLevel\":\"LOW\",\"riskScannedAt\":\"2026-10-22 15:20:49\",\"riskScore\":8,\"shipmentCompany\":\"顺丰速运\",\"shippedAt\":\"2026-10-22 15:26:22\",\"status\":\"SHIPPED\",\"statusLabel\":\"待收货\",\"totalAmount\":129.80,\"trackingNo\":\"SFQA0000123456\",\"userCouponId\":3,\"userId\":2}', NULL, 'EXECUTED', 54, 'ORDER', NULL, NULL);
INSERT INTO `admin_action_logs` VALUES (26, 'ORDER_SHIP', 2, NULL, '2026-10-22 15:26:30.561140', '2026-10-22 15:26:30.561140', 'PAGE', 'BUSINESS_ERROR', '仅待发货订单可发货', '2026-10-22 15:26:30.566303', NULL, NULL, NULL, 1, 'ADMIN', 'dfeb49ec4b2ae72fcab241696dd7782df74a93d2', '{\"company\":\"顺丰速运\",\"trackingNo\":\"SFQA0000123456\"}', NULL, NULL, NULL, NULL, 'FAILED', 54, 'ORDER', NULL, NULL);
INSERT INTO `admin_action_logs` VALUES (27, 'PROMOTION_CREATE', 1, NULL, '2026-10-22 15:26:31.067900', '2026-10-22 15:26:31.068901', 'PAGE', NULL, NULL, '2026-10-22 15:26:31.073983', NULL, NULL, NULL, 1, 'ADMIN', 'e0211bcd59c131562e8861fbd579a6843aa9b126', '{\"name\":\"QA回归-满100减10\",\"type\":\"FULL_REDUCTION\",\"threshold\":100,\"discount\":10,\"productId\":null}', NULL, NULL, '{\"createdAt\":\"2026-10-22T15:26:31.0719685\",\"description\":null,\"discount\":10,\"endTime\":null,\"id\":6,\"imageUrl\":null,\"link\":null,\"name\":\"QA回归-满100减10\",\"productId\":null,\"startTime\":null,\"status\":1,\"threshold\":100,\"type\":\"FULL_REDUCTION\"}', NULL, 'EXECUTED', NULL, 'PROMOTION', NULL, NULL);
INSERT INTO `admin_action_logs` VALUES (28, 'PROMOTION_CREATE', 1, NULL, '2026-10-22 15:26:31.644080', '2026-10-22 15:26:31.644080', 'PAGE', NULL, NULL, '2026-10-22 15:26:31.647606', NULL, NULL, NULL, 1, 'ADMIN', '087d1410404f47836193ab920a95359f80b414f7', '{\"name\":\"QA非法折扣\",\"type\":\"DISCOUNT\",\"threshold\":0,\"discount\":1.5,\"productId\":null}', NULL, NULL, '{\"createdAt\":\"2026-10-22T15:26:31.6466021\",\"description\":null,\"discount\":1.5,\"endTime\":null,\"id\":7,\"imageUrl\":null,\"link\":null,\"name\":\"QA非法折扣\",\"productId\":null,\"startTime\":null,\"status\":1,\"threshold\":0,\"type\":\"DISCOUNT\"}', NULL, 'EXECUTED', NULL, 'PROMOTION', NULL, NULL);
INSERT INTO `admin_action_logs` VALUES (29, 'STOCK_REPLENISH', 1, NULL, '2026-10-22 15:28:04.314609', '2026-10-22 15:28:03.989175', 'CHAT', NULL, NULL, '2026-10-22 15:28:04.319773', '2026-10-22 15:33:03.988164', 'restock:7:595:5', '0:0:0:0:0:0:0:1', 1, 'ADMIN', '0d10a92093c589417a547d38321a8e59afba657e', '{\"mode\":\"DELTA\",\"delta\":5,\"note\":\"AI 对话补货\"}', '{\"ok\":true,\"checkedAt\":\"2026-10-22T15:28:02\",\"checks\":[{\"code\":\"PERMISSION\",\"ok\":true,\"message\":\"具备 product:edit\"},{\"code\":\"TARGET\",\"ok\":true,\"message\":\"商品「东北大米」已定位\"},{\"code\":\"QTY\",\"ok\":true,\"message\":\"补货 5 件\"},{\"code\":\"RANGE\",\"ok\":true,\"message\":\"补后库存 600 件合法\"}]}', 'pa_a1b02c21fe', '{\"categoryId\":15,\"createdAt\":\"2026-09-07T16:42:59.627728\",\"description\":\"五常稻花香大米，香糯可口。\",\"id\":7,\"imageUrl\":\"p029\",\"images\":null,\"name\":\"东北大米\",\"originalPrice\":69.90,\"price\":59.90,\"sales\":5,\"status\":1,\"stock\":600,\"subtitle\":\"五常稻花香 颗粒饱满\",\"unit\":\"袋\",\"updatedAt\":\"2026-10-22T15:28:04.3186905\"}', '{\"productId\":7,\"productName\":\"东北大米\",\"expectedStock\":595,\"stockBefore\":595,\"stockAfter\":600,\"delta\":5,\"unit\":\"袋\",\"threshold\":10,\"sales\":5}', 'EXECUTED', 7, 'PRODUCT', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/148.0.0.0 Safari/537.36', NULL);
INSERT INTO `admin_action_logs` VALUES (30, 'STOCK_REPLENISH', 1, NULL, '2026-10-22 15:28:21.618494', '2026-10-22 15:28:21.618494', 'PAGE', NULL, NULL, '2026-10-22 15:28:21.624701', NULL, NULL, NULL, 1, 'ADMIN', 'a5d6e55eb51c7745eb62b99cc94780680eb59e38', '{\"delta\":-5,\"expectedStock\":null}', NULL, NULL, '{\"categoryId\":15,\"createdAt\":\"2026-09-07T16:42:59.627728\",\"description\":\"五常稻花香大米，香糯可口。\",\"id\":7,\"imageUrl\":\"p029\",\"images\":null,\"name\":\"东北大米\",\"originalPrice\":69.90,\"price\":59.90,\"sales\":5,\"status\":1,\"stock\":595,\"subtitle\":\"五常稻花香 颗粒饱满\",\"unit\":\"袋\",\"updatedAt\":\"2026-10-22T15:28:21.6235696\"}', NULL, 'EXECUTED', 7, 'PRODUCT', NULL, NULL);
INSERT INTO `admin_action_logs` VALUES (31, 'STOCK_REPLENISH', 1, NULL, '2026-10-22 15:32:57.521407', '2026-10-22 15:32:57.524485', 'API', NULL, NULL, '2026-10-22 15:32:57.575497', NULL, 'restock:4:198:100', NULL, 1, 'ADMIN', 'd5d573a0f8b60a7f161d90486d04cb01a90cae7c', '{\"delta\":100,\"expectedStock\":198}', NULL, NULL, '{\"categoryId\":11,\"createdAt\":\"2026-09-07T16:42:59.624633\",\"description\":\"当日现宰五花肉，肥瘦均匀。\",\"id\":4,\"imageUrl\":\"p011\",\"images\":null,\"name\":\"五花肉\",\"originalPrice\":23.90,\"price\":19.90,\"sales\":2,\"status\":1,\"stock\":298,\"subtitle\":\"肥瘦相间 红烧首选\",\"unit\":\"斤\",\"updatedAt\":\"2026-10-22T15:32:57.5678767\"}', NULL, 'EXECUTED', 4, 'PRODUCT', NULL, NULL);
INSERT INTO `admin_action_logs` VALUES (32, 'STOCK_REPLENISH', 1, NULL, '2026-10-22 15:33:11.610509', '2026-10-22 15:33:11.610509', 'API', NULL, NULL, '2026-10-22 15:33:11.616093', NULL, 'restock:4:298:-100', NULL, 1, 'ADMIN', '9dfc7cf227839f34faef986515c636ee8809c5da', '{\"delta\":-100,\"expectedStock\":298}', NULL, NULL, '{\"categoryId\":11,\"createdAt\":\"2026-09-07T16:42:59.624633\",\"description\":\"当日现宰五花肉，肥瘦均匀。\",\"id\":4,\"imageUrl\":\"p011\",\"images\":null,\"name\":\"五花肉\",\"originalPrice\":23.90,\"price\":19.90,\"sales\":2,\"status\":1,\"stock\":198,\"subtitle\":\"肥瘦相间 红烧首选\",\"unit\":\"斤\",\"updatedAt\":\"2026-10-22T15:33:11.6145803\"}', NULL, 'EXECUTED', 4, 'PRODUCT', NULL, NULL);
INSERT INTO `admin_action_logs` VALUES (33, 'ORDER_SHIP', 2, NULL, '2026-10-22 15:33:18.660063', '2026-10-22 15:33:18.661567', 'PAGE', 'ORDER_STATE_INVALID', '仅待发货订单可发货', '2026-10-22 15:33:18.670091', NULL, NULL, NULL, 1, 'ADMIN', '065bc24a7e8b2bf4a19db6eee8198a90f5f0da83', '{\"company\":\"SF\",\"trackingNo\":\"SFQA0000001\"}', NULL, NULL, NULL, NULL, 'FAILED', 49, 'ORDER', NULL, NULL);
INSERT INTO `admin_action_logs` VALUES (34, 'ORDER_SHIP', 2, NULL, '2026-10-22 15:33:18.930634', '2026-10-22 15:33:18.931643', 'PAGE', 'DUPLICATE_SHIPMENT', '这单已经发过了（运单号 YT9900112233），不要重复发。', '2026-10-22 15:33:18.935498', NULL, NULL, NULL, 1, 'ADMIN', 'ca2dfc95a4132e2c324597c468d9dc7b1a905df0', '{\"company\":\"YT\",\"trackingNo\":\"YT9900112233\"}', NULL, NULL, NULL, NULL, 'FAILED', 23, 'ORDER', NULL, NULL);
INSERT INTO `admin_action_logs` VALUES (35, 'PROMOTION_CREATE', 2, NULL, '2026-10-22 15:33:33.734496', '2026-10-22 15:33:33.735518', 'PAGE', 'PROMOTION_DISCOUNT_INVALID', '折扣率必须在 (0, 1] 之间，如 0.9 表示 9 折', '2026-10-22 15:33:33.739040', NULL, NULL, NULL, 1, 'ADMIN', '54b2f7265b3587cabce94ab9236dfd0cf32dad51', '{\"name\":\"QA-Q4-折扣越界\",\"type\":\"DISCOUNT\",\"threshold\":0,\"discount\":1.5,\"productId\":null}', NULL, NULL, NULL, NULL, 'FAILED', NULL, 'PROMOTION', NULL, NULL);
INSERT INTO `admin_action_logs` VALUES (36, 'PROMOTION_CREATE', 1, NULL, '2026-10-22 15:33:34.023015', '2026-10-22 15:33:34.023015', 'PAGE', NULL, NULL, '2026-10-22 15:33:34.027819', NULL, NULL, NULL, 1, 'ADMIN', '6cf12798723e280087b0a599576c0086756f7c05', '{\"name\":\"QA-Q4-合规折扣\",\"type\":\"DISCOUNT\",\"threshold\":0,\"discount\":0.85,\"productId\":null}', NULL, NULL, '{\"createdAt\":\"2026-10-22T15:33:34.0255233\",\"description\":null,\"discount\":0.85,\"endTime\":null,\"id\":8,\"imageUrl\":null,\"link\":null,\"name\":\"QA-Q4-合规折扣\",\"productId\":null,\"startTime\":null,\"status\":1,\"threshold\":0,\"type\":\"DISCOUNT\"}', NULL, 'EXECUTED', NULL, 'PROMOTION', NULL, NULL);
INSERT INTO `admin_action_logs` VALUES (37, 'STOCK_REPLENISH', 1, NULL, '2026-10-22 15:38:08.903660', '2026-10-22 15:38:08.908195', 'API', NULL, NULL, '2026-10-22 15:38:08.958407', NULL, 'restock:9:498:20', NULL, 1, 'ADMIN', '9afae861ca75aab71a6ae7f2866f1b79918978bd', '{\"delta\":20,\"expectedStock\":498}', NULL, NULL, '{\"categoryId\":17,\"createdAt\":\"2026-09-07T16:42:59.630271\",\"description\":\"高品质纯牛奶，每100ml含3.6g优质蛋白。\",\"id\":9,\"imageUrl\":\"p015\",\"images\":null,\"name\":\"纯牛奶\",\"originalPrice\":55.90,\"price\":45.90,\"sales\":2,\"status\":1,\"stock\":518,\"subtitle\":\"牧场直供 3.6g蛋白\",\"unit\":\"箱\",\"updatedAt\":\"2026-10-22T15:38:08.9498458\"}', NULL, 'EXECUTED', 9, 'PRODUCT', NULL, NULL);
INSERT INTO `admin_action_logs` VALUES (38, 'ORDER_SHIP', 2, NULL, '2026-10-22 15:38:18.538713', '2026-10-22 15:38:18.540173', 'PAGE', 'ORDER_STATE_INVALID', '仅待发货订单可发货', '2026-10-22 15:38:18.553251', NULL, NULL, NULL, 1, 'ADMIN', '5d8c7630d0341a2e331a015a2e9084467187c8ac', '{\"company\":\"顺丰速运\",\"trackingNo\":\"SFQA1\"}', NULL, NULL, NULL, NULL, 'FAILED', 43, 'ORDER', NULL, NULL);
INSERT INTO `admin_action_logs` VALUES (39, 'ORDER_SHIP', 2, NULL, '2026-10-22 15:38:19.282685', '2026-10-22 15:38:19.283686', 'PAGE', 'DUPLICATE_SHIPMENT', '这单已经发过了（运单号 YT9900112233），不要重复发。', '2026-10-22 15:38:19.289719', NULL, NULL, NULL, 1, 'ADMIN', '5d8c7630d0341a2e331a015a2e9084467187c8ac', '{\"company\":\"顺丰速运\",\"trackingNo\":\"SFQA1\"}', NULL, NULL, NULL, NULL, 'FAILED', 23, 'ORDER', NULL, NULL);
INSERT INTO `admin_action_logs` VALUES (40, 'PROMOTION_CREATE', 2, NULL, '2026-10-22 15:38:20.250284', '2026-10-22 15:38:20.251287', 'PAGE', 'PROMOTION_DISCOUNT_INVALID', '折扣率必须在 (0, 1] 之间，如 0.9 表示 9 折', '2026-10-22 15:38:20.254051', NULL, NULL, NULL, 1, 'ADMIN', '087d1410404f47836193ab920a95359f80b414f7', '{\"name\":\"QA非法折扣\",\"type\":\"DISCOUNT\",\"threshold\":0,\"discount\":1.5,\"productId\":null}', NULL, NULL, NULL, NULL, 'FAILED', NULL, 'PROMOTION', NULL, NULL);
INSERT INTO `admin_action_logs` VALUES (41, 'PROMOTION_CREATE', 2, NULL, '2026-10-22 15:38:20.774155', '2026-10-22 15:38:20.774155', 'PAGE', 'PROMOTION_DISCOUNT_INVALID', '折扣率必须在 (0, 1] 之间，如 0.9 表示 9 折', '2026-10-22 15:38:20.776166', NULL, NULL, NULL, 1, 'ADMIN', 'd7fa387fa74957159d6674b5f0d8047bc944e29f', '{\"name\":\"QA零折扣\",\"type\":\"DISCOUNT\",\"threshold\":0,\"discount\":0,\"productId\":null}', NULL, NULL, NULL, NULL, 'FAILED', NULL, 'PROMOTION', NULL, NULL);
INSERT INTO `admin_action_logs` VALUES (42, 'PROMOTION_CREATE', 1, NULL, '2026-10-22 15:38:21.304404', '2026-10-22 15:38:21.304909', 'PAGE', NULL, NULL, '2026-10-22 15:38:21.308872', NULL, NULL, NULL, 1, 'ADMIN', '189b77816d6ac37099d0452b01ea05f96ab505e1', '{\"name\":\"QA回归-85折\",\"type\":\"DISCOUNT\",\"threshold\":0,\"discount\":0.85,\"productId\":null}', NULL, NULL, '{\"createdAt\":\"2026-10-22T15:38:21.3074222\",\"description\":null,\"discount\":0.85,\"endTime\":null,\"id\":9,\"imageUrl\":null,\"link\":null,\"name\":\"QA回归-85折\",\"productId\":null,\"startTime\":null,\"status\":1,\"threshold\":0,\"type\":\"DISCOUNT\"}', NULL, 'EXECUTED', NULL, 'PROMOTION', NULL, NULL);
INSERT INTO `admin_action_logs` VALUES (43, 'ORDER_SHIP', 1, NULL, '2026-10-22 15:38:33.520804', '2026-10-22 15:38:33.520804', 'PAGE', NULL, NULL, '2026-10-22 15:38:33.541477', NULL, NULL, NULL, 1, 'ADMIN', 'd39ee5b665316687b902aa5e1ce41d6193321ef6', '{\"company\":\"中通快递\",\"trackingNo\":\"ZTQA00008888\"}', NULL, NULL, '{\"cancelledAt\":null,\"completedAt\":null,\"createdAt\":\"2026-09-16 19:53:51\",\"discountAmount\":20.00,\"id\":54,\"items\":[{\"id\":49,\"price\":39.90,\"productId\":6,\"productImage\":\"p059\",\"productName\":\"基围虾\",\"quantity\":1,\"reviewed\":null,\"subtotal\":39.90},{\"id\":50,\"price\":89.90,\"productId\":8,\"productImage\":\"p030\",\"productName\":\"花生油\",\"quantity\":1,\"reviewed\":null,\"subtotal\":89.90}],\"orderNo\":\"DR20260916195351194\",\"paidAt\":\"2026-09-16 19:53:51\",\"payAmount\":109.80,\"promotionId\":1,\"receiverAddress\":\"福建省泉州市石狮市 宝盖镇闽南理工学院宝盖校区\",\"receiverName\":\"张三\",\"receiverPhone\":\"13107993087\",\"refundReason\":null,\"refundRejectReason\":null,\"refundRequestedAt\":null,\"refundStatus\":null,\"refundStatusLabel\":null,\"remark\":null,\"reviewed\":null,\"riskLevel\":\"LOW\",\"riskScannedAt\":\"2026-10-22 15:20:49\",\"riskScore\":8,\"shipmentCompany\":\"中通快递\",\"shippedAt\":\"2026-10-22 15:38:33\",\"status\":\"SHIPPED\",\"statusLabel\":\"待收货\",\"totalAmount\":129.80,\"trackingNo\":\"ZTQA00008888\",\"userCouponId\":3,\"userId\":2}', NULL, 'EXECUTED', 54, 'ORDER', NULL, NULL);
INSERT INTO `admin_action_logs` VALUES (44, 'REFUND_REJECT', 1, NULL, '2026-10-22 15:38:36.123541', '2026-10-22 15:38:36.123541', 'PAGE', NULL, NULL, '2026-10-22 15:38:36.134662', NULL, NULL, NULL, 1, 'ADMIN', '47d21fe85293f59ecf18dee4ffa21ef46d1802b1', '{\"decision\":\"REJECT\",\"rejectReason\":\"QA2回归-拒绝并回滚\"}', NULL, NULL, '{\"amount\":879.00,\"createdAt\":\"2026-10-22 15:38:35\",\"handledAt\":\"2026-10-22 15:38:36\",\"id\":17,\"orderId\":42,\"orderNo\":\"DR20260916112224719\",\"previousStatus\":\"PAID\",\"previousStatusLabel\":\"待发货\",\"products\":null,\"reason\":\"QA2回归-退款拒绝\",\"rejectReason\":\"QA2回归-拒绝并回滚\",\"status\":\"REJECTED\",\"statusLabel\":\"已拒绝\",\"userId\":5,\"userName\":\"测试用户\",\"userPhone\":\"13800000003\"}', NULL, 'EXECUTED', 17, 'REFUND_REQUEST', NULL, NULL);
INSERT INTO `admin_action_logs` VALUES (45, 'REFUND_APPROVE', 1, NULL, '2026-10-22 15:39:05.988108', '2026-10-22 15:38:57.875997', 'CHAT', NULL, NULL, '2026-10-22 15:39:06.006953', '2026-10-22 15:43:57.870835', 'refund:18:APPROVE', '127.0.0.1', 3, 'OPS', '64cbb8654539f949464b4e342640e84b107f82be', '{\"decision\":\"APPROVE\",\"rejectReason\":null}', '{\"ok\":true,\"checkedAt\":\"2026-10-22T15:38:56\",\"checks\":[{\"code\":\"PERMISSION\",\"ok\":true,\"message\":\"具备 order:view 或 cs:manage\"},{\"code\":\"PARAM\",\"ok\":true,\"message\":\"参数完整（决策=APPROVE）\"},{\"code\":\"TARGET\",\"ok\":true,\"message\":\"退款单 #18 已定位\"},{\"code\":\"REFUND_PENDING\",\"ok\":true,\"message\":\"退款单待处理\"},{\"code\":\"ORDER_REFUNDING\",\"ok\":true,\"message\":\"订单处于退款中\"},{\"code\":\"SELF_APPROVAL\",\"ok\":true,\"message\":\"审批人非买家\"}]}', 'pa_2594aaae98', '{\"amount\":84.90,\"createdAt\":\"2026-10-22 15:38:47\",\"handledAt\":\"2026-10-22 15:39:06\",\"id\":18,\"orderId\":38,\"orderNo\":\"DR20260916112135320\",\"previousStatus\":\"PAID\",\"previousStatusLabel\":\"待发货\",\"products\":null,\"reason\":\"QA2回归-退款同意\",\"rejectReason\":null,\"status\":\"APPROVED\",\"statusLabel\":\"已同意\",\"userId\":5,\"userName\":\"测试用户\",\"userPhone\":\"13800000003\"}', '{\"refundStatus\":\"PENDING\",\"orderStatus\":\"REFUNDING\",\"payAmount\":\"84.90\",\"orderPayAmount\":\"84.90\",\"userId\":5,\"userName\":\"测试用户\",\"orderNo\":\"DR20260916112135320\",\"refundReason\":\"QA2回归-退款同意\",\"itemCount\":1}', 'EXECUTED', 18, 'REFUND_REQUEST', 'curl/8.21.0', NULL);
INSERT INTO `admin_action_logs` VALUES (46, 'STOCK_REPLENISH', 1, NULL, '2026-10-22 15:39:41.499883', '2026-10-22 15:39:41.499883', 'API', NULL, NULL, '2026-10-22 15:39:41.504654', NULL, 'restock:7:595:25', NULL, 1, 'ADMIN', '24f927aa42edb402dfe54cb8688835a79cf295d3', '{\"delta\":25,\"expectedStock\":595}', NULL, NULL, '{\"categoryId\":15,\"createdAt\":\"2026-09-07T16:42:59.627728\",\"description\":\"五常稻花香大米，香糯可口。\",\"id\":7,\"imageUrl\":\"p029\",\"images\":null,\"name\":\"东北大米\",\"originalPrice\":69.90,\"price\":59.90,\"sales\":5,\"status\":1,\"stock\":620,\"subtitle\":\"五常稻花香 颗粒饱满\",\"unit\":\"袋\",\"updatedAt\":\"2026-10-22T15:39:41.5036256\"}', NULL, 'EXECUTED', 7, 'PRODUCT', NULL, NULL);
INSERT INTO `admin_action_logs` VALUES (47, 'STOCK_REPLENISH', 1, NULL, '2026-10-22 15:40:13.260999', '2026-10-22 15:40:12.927086', 'CHAT', NULL, NULL, '2026-10-22 15:40:13.267404', '2026-10-22 15:45:12.926064', 'restock:9:518:7', '0:0:0:0:0:0:0:1', 1, 'ADMIN', '96ddae033eda03d5a52972de34968d550b3b82ab', '{\"mode\":\"DELTA\",\"delta\":7,\"note\":\"AI 对话补货\"}', '{\"ok\":true,\"checkedAt\":\"2026-10-22T15:40:10\",\"checks\":[{\"code\":\"PERMISSION\",\"ok\":true,\"message\":\"具备 product:edit\"},{\"code\":\"TARGET\",\"ok\":true,\"message\":\"商品「纯牛奶」已定位\"},{\"code\":\"QTY\",\"ok\":true,\"message\":\"补货 7 件\"},{\"code\":\"RANGE\",\"ok\":true,\"message\":\"补后库存 525 件合法\"}]}', 'pa_68d425bd9f', '{\"categoryId\":17,\"createdAt\":\"2026-09-07T16:42:59.630271\",\"description\":\"高品质纯牛奶，每100ml含3.6g优质蛋白。\",\"id\":9,\"imageUrl\":\"p015\",\"images\":null,\"name\":\"纯牛奶\",\"originalPrice\":55.90,\"price\":45.90,\"sales\":2,\"status\":1,\"stock\":525,\"subtitle\":\"牧场直供 3.6g蛋白\",\"unit\":\"箱\",\"updatedAt\":\"2026-10-22T15:40:13.2648828\"}', '{\"productId\":9,\"productName\":\"纯牛奶\",\"expectedStock\":518,\"stockBefore\":518,\"stockAfter\":525,\"delta\":7,\"unit\":\"箱\",\"threshold\":10,\"sales\":2}', 'EXECUTED', 9, 'PRODUCT', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/148.0.0.0 Safari/537.36', NULL);
INSERT INTO `admin_action_logs` VALUES (48, 'STOCK_REPLENISH', 1, NULL, '2026-10-22 15:40:27.661666', '2026-10-22 15:40:27.661666', 'PAGE', NULL, NULL, '2026-10-22 15:40:27.667262', NULL, NULL, NULL, 1, 'ADMIN', 'c147780598179cb773c3da42d98690949f70befa', '{\"delta\":-27,\"expectedStock\":null}', NULL, NULL, '{\"categoryId\":17,\"createdAt\":\"2026-09-07T16:42:59.630271\",\"description\":\"高品质纯牛奶，每100ml含3.6g优质蛋白。\",\"id\":9,\"imageUrl\":\"p015\",\"images\":null,\"name\":\"纯牛奶\",\"originalPrice\":55.90,\"price\":45.90,\"sales\":2,\"status\":1,\"stock\":498,\"subtitle\":\"牧场直供 3.6g蛋白\",\"unit\":\"箱\",\"updatedAt\":\"2026-10-22T15:40:27.6646875\"}', NULL, 'EXECUTED', 9, 'PRODUCT', NULL, NULL);
INSERT INTO `admin_action_logs` VALUES (49, 'STOCK_REPLENISH', 1, NULL, '2026-10-22 15:40:28.291606', '2026-10-22 15:40:28.292109', 'PAGE', NULL, NULL, '2026-10-22 15:40:28.296146', NULL, NULL, NULL, 1, 'ADMIN', 'dc692258af09d00f957401b66c2c63fa46f89299', '{\"delta\":-25,\"expectedStock\":null}', NULL, NULL, '{\"categoryId\":15,\"createdAt\":\"2026-09-07T16:42:59.627728\",\"description\":\"五常稻花香大米，香糯可口。\",\"id\":7,\"imageUrl\":\"p029\",\"images\":null,\"name\":\"东北大米\",\"originalPrice\":69.90,\"price\":59.90,\"sales\":5,\"status\":1,\"stock\":595,\"subtitle\":\"五常稻花香 颗粒饱满\",\"unit\":\"袋\",\"updatedAt\":\"2026-10-22T15:40:28.2951358\"}', NULL, 'EXECUTED', 7, 'PRODUCT', NULL, NULL);
INSERT INTO `admin_action_logs` VALUES (50, 'STOCK_REPLENISH', 1, NULL, '2026-10-22 15:42:00.132527', '2026-10-22 15:42:00.136558', 'API', NULL, NULL, '2026-10-22 15:42:00.194049', NULL, 'restock:2:798:50', NULL, 1, 'ADMIN', 'c76c883b01ea79d3f3bc6c329b78fc441efc0359', '{\"delta\":50,\"expectedStock\":798}', NULL, NULL, '{\"categoryId\":9,\"createdAt\":\"2026-09-07T16:42:59.623131\",\"description\":\"江西赣南脐橙，酸甜可口，富含维生素C。\",\"id\":2,\"imageUrl\":\"p003\",\"images\":null,\"name\":\"赣南脐橙\",\"originalPrice\":8.80,\"price\":6.80,\"sales\":2,\"status\":1,\"stock\":848,\"subtitle\":\"皮薄肉嫩 汁水丰盈\",\"unit\":\"斤\",\"updatedAt\":\"2026-10-22T15:42:00.1840962\"}', NULL, 'EXECUTED', 2, 'PRODUCT', NULL, NULL);
INSERT INTO `admin_action_logs` VALUES (51, 'STOCK_REPLENISH', 1, NULL, '2026-10-22 15:42:07.404059', '2026-10-22 15:42:07.404059', 'API', NULL, NULL, '2026-10-22 15:42:07.410809', NULL, 'restock:2:848:-50', NULL, 1, 'ADMIN', 'f7d0d72d8d18b212494e589b2dde8a48ef24b136', '{\"delta\":-50,\"expectedStock\":848}', NULL, NULL, '{\"categoryId\":9,\"createdAt\":\"2026-09-07T16:42:59.623131\",\"description\":\"江西赣南脐橙，酸甜可口，富含维生素C。\",\"id\":2,\"imageUrl\":\"p003\",\"images\":null,\"name\":\"赣南脐橙\",\"originalPrice\":8.80,\"price\":6.80,\"sales\":2,\"status\":1,\"stock\":798,\"subtitle\":\"皮薄肉嫩 汁水丰盈\",\"unit\":\"斤\",\"updatedAt\":\"2026-10-22T15:42:07.4088032\"}', NULL, 'EXECUTED', 2, 'PRODUCT', NULL, NULL);
INSERT INTO `admin_action_logs` VALUES (52, 'RISK_SCAN', 1, NULL, NULL, '2026-10-22 16:04:19.631188', 'CHAT', NULL, NULL, NULL, '2026-10-22 16:09:19.627678', NULL, '0:0:0:0:0:0:0:1', 1, 'ADMIN', '0e1fd3271e4c59800c3f728c9479786144529a8e', '{\"orderNo\":\"DR20260916121347646\",\"mode\":\"SINGLE\",\"rescan\":false}', '{\"ok\":true,\"checkedAt\":\"2026-10-22T16:04:17\",\"checks\":[{\"code\":\"PERMISSION\",\"ok\":true,\"message\":\"具备 risk:scan\"},{\"code\":\"MODE\",\"ok\":true,\"message\":\"单条扫描\"},{\"code\":\"PARAM\",\"ok\":true,\"message\":\"订单号已提供\"},{\"code\":\"TARGET\",\"ok\":true,\"message\":\"订单 DR20260916121347646 已定位\"},{\"code\":\"ORDER_STATE\",\"ok\":true,\"message\":\"订单状态允许分析（待发货）\"},{\"code\":\"ALREADY_SCANNED\",\"ok\":true,\"message\":\"该单尚未评分\"}]}', 'pa_a8c337f641', NULL, '{\"orderNo\":\"DR20260916121347646\",\"amount\":\"4.50\",\"status\":\"PAID\",\"paidAt\":\"2026-09-16T12:13:49\",\"currentScore\":null,\"currentLevel\":null,\"lastScannedAt\":null,\"waitedHours\":867}', 'EXPIRED', 51, 'ORDER', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/155.0.0.0 Safari/537.36 Edg/155.0.0.0', NULL);
INSERT INTO `admin_action_logs` VALUES (57, 'STOCK_REPLENISH', 1, NULL, '2026-10-22 16:24:05.895909', '2026-10-22 16:21:47.176372', 'CHAT', NULL, NULL, '2026-10-22 16:24:05.913726', '2026-10-22 16:26:47.166765', 'restock:2:798:30', '127.0.0.1', 1, 'ADMIN', 'ed6574adb617c45a04d6dc73290e0941864f80f6', '{\"mode\":\"DELTA\",\"delta\":30,\"note\":\"AI 对话补货\"}', '{\"ok\":true,\"checkedAt\":\"2026-10-22T16:21:44\",\"checks\":[{\"code\":\"PERMISSION\",\"ok\":true,\"message\":\"具备 product:edit\"},{\"code\":\"TARGET\",\"ok\":true,\"message\":\"商品「赣南脐橙」已定位\"},{\"code\":\"QTY\",\"ok\":true,\"message\":\"补货 30 件\"},{\"code\":\"RANGE\",\"ok\":true,\"message\":\"补后库存 828 件合法\"}]}', 'pa_f4ab153f17', '{\"categoryId\":9,\"createdAt\":\"2026-09-07T16:42:59.623131\",\"description\":\"江西赣南脐橙，酸甜可口，富含维生素C。\",\"id\":2,\"imageUrl\":\"p003\",\"images\":null,\"name\":\"赣南脐橙\",\"originalPrice\":8.80,\"price\":6.80,\"sales\":2,\"status\":1,\"stock\":828,\"subtitle\":\"皮薄肉嫩 汁水丰盈\",\"unit\":\"斤\",\"updatedAt\":\"2026-10-22T16:24:05.9122217\"}', '{\"productId\":2,\"productName\":\"赣南脐橙\",\"expectedStock\":798,\"stockBefore\":798,\"stockAfter\":828,\"delta\":30,\"unit\":\"斤\",\"threshold\":10,\"sales\":2}', 'EXECUTED', 2, 'PRODUCT', 'curl/8.21.0', '{\"proposalId\":\"pa_f4ab153f17\",\"action\":\"STOCK_REPLENISH\",\"actionLabel\":\"补货\",\"targetType\":\"PRODUCT\",\"targetId\":2,\"targetLabel\":\"补货 · 赣南脐橙\",\"params\":{\"mode\":\"DELTA\",\"delta\":30,\"note\":\"AI 对话补货\"},\"precheck\":{\"ok\":true,\"checkedAt\":\"2026-10-22T16:21:44\",\"checks\":[{\"code\":\"PERMISSION\",\"ok\":true,\"message\":\"具备 product:edit\"},{\"code\":\"TARGET\",\"ok\":true,\"message\":\"商品「赣南脐橙」已定位\"},{\"code\":\"QTY\",\"ok\":true,\"message\":\"补货 30 件\"},{\"code\":\"RANGE\",\"ok\":true,\"message\":\"补后库存 828 件合法\"}]},\"risk\":{\"level\":\"LOW\",\"reasons\":[],\"requiresSecondConfirm\":false,\"secondConfirmHint\":null,\"irreversible\":false,\"note\":\"补货不做库存流水，追溯依赖操作日志\"},\"idempotencyKey\":\"restock:2:798:30\",\"expiresAt\":\"2026-10-22T16:26:47.1667653+08:00\",\"snapshot\":{\"productId\":2,\"productName\":\"赣南脐橙\",\"expectedStock\":798,\"stockBefore\":798,\"stockAfter\":828,\"delta\":30,\"unit\":\"斤\",\"threshold\":10,\"sales\":2},\"confirm\":{\"method\":\"PUT\",\"path\":\"/api/admin/products/2/stock\",\"body\":{\"delta\":30,\"expectedStock\":798},\"requiresHeader\":{\"Idempotency-Key\":\"restock:2:798:30\",\"Content-Type\":\"application/json\"}},\"reject\":null,\"fields\":[{\"label\":\"商品\",\"value\":\"赣南脐橙（ID 2，单位 斤）\",\"tone\":\"normal\",\"mono\":false},{\"label\":\"库存变化\",\"value\":\"798 → 828（+30）\",\"tone\":\"normal\",\"mono\":false},{\"label\":\"安全阈值\",\"value\":\"10 件（补后已高于阈值，预警解除）\",\"tone\":\"normal\",\"mono\":false},{\"label\":\"累计销量\",\"value\":\"2 件\",\"tone\":\"normal\",\"mono\":false},{\"label\":\"当前库存快照\",\"value\":\"798 件（执行时会比对，变了就拒绝执行）\",\"tone\":\"normal\",\"mono\":false}]}');
INSERT INTO `admin_action_logs` VALUES (58, 'RISK_SCAN', 1, NULL, '2026-10-22 16:23:48.305802', '2026-10-22 16:22:16.763884', 'CHAT', NULL, NULL, '2026-10-22 16:23:50.047534', '2026-10-22 16:27:16.761858', 'risk:58:2026-10-22T15:15:40:12:false', '127.0.0.1', 1, 'ADMIN', 'a7399dadad6247765daffb8b3762e9035a6e13fe', '{\"orderNo\":\"DR20260918103315261\",\"mode\":\"SINGLE\",\"rescan\":false}', '{\"ok\":true,\"checkedAt\":\"2026-10-22T16:22:13\",\"checks\":[{\"code\":\"PERMISSION\",\"ok\":true,\"message\":\"具备 risk:scan\"},{\"code\":\"MODE\",\"ok\":true,\"message\":\"单条扫描\"},{\"code\":\"PARAM\",\"ok\":true,\"message\":\"订单号已提供\"},{\"code\":\"TARGET\",\"ok\":true,\"message\":\"订单 DR20260918103315261 已定位\"},{\"code\":\"ORDER_STATE\",\"ok\":true,\"message\":\"订单状态允许分析（待发货）\"},{\"code\":\"ALREADY_SCANNED\",\"ok\":true,\"message\":\"该单已有评分，本次为重新评估（不拦截）\"}]}', 'pa_f9ca3da7d3', '{\"createdAt\":\"2026-10-22T16:23:50.0342177\",\"id\":75,\"isRead\":false,\"level\":\"LOW\",\"llmAction\":\"放行\",\"llmSummary\":\"规则引擎未识别任何风险信号，订单金额较小（实付94.80元），商品为常规低价商品；收货地址为高校校区，属于学生网购常见场景，收货人姓名较通用，但无其他异常佐证。\",\"orderId\":58,\"orderNo\":\"DR20260918103315261\",\"ruleScore\":0,\"score\":12,\"signals\":[],\"source\":\"HYBRID\",\"suggestedAction\":\"放行\"}', '{\"orderNo\":\"DR20260918103315261\",\"amount\":\"94.80\",\"status\":\"PAID\",\"paidAt\":\"2026-09-18T10:33:15\",\"currentScore\":12,\"currentLevel\":\"LOW\",\"lastScannedAt\":\"2026-10-22T15:15:40\",\"waitedHours\":821}', 'EXECUTED', 58, 'ORDER', 'curl/8.21.0', '{\"proposalId\":\"pa_f9ca3da7d3\",\"action\":\"RISK_SCAN\",\"actionLabel\":\"风险分析\",\"targetType\":\"ORDER\",\"targetId\":58,\"targetLabel\":\"风险分析 · DR20260918103315261\",\"params\":{\"orderNo\":\"DR20260918103315261\",\"mode\":\"SINGLE\",\"rescan\":false},\"precheck\":{\"ok\":true,\"checkedAt\":\"2026-10-22T16:22:13\",\"checks\":[{\"code\":\"PERMISSION\",\"ok\":true,\"message\":\"具备 risk:scan\"},{\"code\":\"MODE\",\"ok\":true,\"message\":\"单条扫描\"},{\"code\":\"PARAM\",\"ok\":true,\"message\":\"订单号已提供\"},{\"code\":\"TARGET\",\"ok\":true,\"message\":\"订单 DR20260918103315261 已定位\"},{\"code\":\"ORDER_STATE\",\"ok\":true,\"message\":\"订单状态允许分析（待发货）\"},{\"code\":\"ALREADY_SCANNED\",\"ok\":true,\"message\":\"该单已有评分，本次为重新评估（不拦截）\"}]},\"risk\":{\"level\":\"LOW\",\"reasons\":[\"该单已有评分（12 分 / 2026-10-22 15:15），本次为重新评估\"],\"requiresSecondConfirm\":false,\"secondConfirmHint\":null,\"irreversible\":false,\"note\":\"分析会调用一次 AI 综合评分（2-8 秒）\"},\"idempotencyKey\":\"risk:58:2026-10-22T15:15:40:12:false\",\"expiresAt\":\"2026-10-22T16:27:16.7618584+08:00\",\"snapshot\":{\"orderNo\":\"DR20260918103315261\",\"amount\":\"94.80\",\"status\":\"PAID\",\"paidAt\":\"2026-09-18T10:33:15\",\"currentScore\":12,\"currentLevel\":\"LOW\",\"lastScannedAt\":\"2026-10-22T15:15:40\",\"waitedHours\":821},\"confirm\":{\"method\":\"POST\",\"path\":\"/api/admin/risk/orders/58/scan\",\"body\":null,\"requiresHeader\":{\"Idempotency-Key\":\"risk:58:2026-10-22T15:15:40:12:false\"}},\"reject\":null,\"fields\":[{\"label\":\"当前状态\",\"value\":\"待发货\",\"tone\":\"normal\",\"mono\":false},{\"label\":\"订单金额\",\"value\":\"¥94.80\",\"tone\":\"normal\",\"mono\":false},{\"label\":\"已等待\",\"value\":\"34 天 5 小时\",\"tone\":\"normal\",\"mono\":false},{\"label\":\"上次评分\",\"value\":\"12 分 / 2026-10-22 15:15\",\"tone\":\"normal\",\"mono\":false},{\"label\":\"说明\",\"value\":\"本操作只打分，不改变订单状态；高危单仍可发货（仅提示）\",\"tone\":\"normal\",\"mono\":false}]}');
INSERT INTO `admin_action_logs` VALUES (71, 'STOCK_REPLENISH', 2, NULL, '2026-10-23 12:47:20.911127', '2026-10-23 12:47:20.914230', 'PAGE', 'BUSINESS_ERROR', '库存不足，无法扣减', '2026-10-23 12:47:20.942054', NULL, NULL, NULL, 1, 'ADMIN', '7104f51efcbe8bd8a37b71f2cee3071d11cc2d50', '{\"delta\":-1000,\"expectedStock\":null}', NULL, NULL, NULL, NULL, 'FAILED', 1, 'PRODUCT', NULL, NULL);
INSERT INTO `admin_action_logs` VALUES (72, 'STOCK_REPLENISH', 1, NULL, '2026-10-23 12:49:55.007022', '2026-10-23 12:49:55.007022', 'PAGE', NULL, NULL, '2026-10-23 12:49:55.012707', NULL, NULL, NULL, 1, 'ADMIN', 'c40c7592f1db1b10daa8dfbb6b2a62ac0ca5bab2', '{\"delta\":-55,\"expectedStock\":null}', NULL, NULL, '{\"categoryId\":9,\"createdAt\":\"2026-09-07T16:42:59.619729\",\"description\":\"精选山东烟台红富士苹果，个大皮薄，果肉脆甜。\",\"id\":1,\"imageUrl\":\"p002\",\"images\":null,\"name\":\"红富士苹果\",\"originalPrice\":12.90,\"price\":9.90,\"sales\":0,\"status\":0,\"stock\":5,\"subtitle\":\"脆甜多汁 产地直供\",\"unit\":\"斤\",\"updatedAt\":\"2026-10-23T12:49:55.0117021\"}', NULL, 'EXECUTED', 1, 'PRODUCT', NULL, NULL);
INSERT INTO `admin_action_logs` VALUES (73, 'STOCK_REPLENISH', 1, NULL, '2026-10-23 12:55:28.249465', '2026-10-23 12:55:28.249465', 'PAGE', NULL, NULL, '2026-10-23 12:55:28.255394', NULL, NULL, NULL, 1, 'ADMIN', 'bc00f579d414a2791672d556e1db80bb2bdf1fc3', '{\"delta\":100,\"expectedStock\":null}', NULL, NULL, '{\"categoryId\":9,\"createdAt\":\"2026-09-07T16:42:59.619729\",\"description\":\"精选山东烟台红富士苹果，个大皮薄，果肉脆甜。\",\"id\":1,\"imageUrl\":\"p002\",\"images\":null,\"name\":\"红富士苹果\",\"originalPrice\":12.90,\"price\":9.90,\"sales\":0,\"status\":0,\"stock\":105,\"subtitle\":\"脆甜多汁 产地直供\",\"unit\":\"斤\",\"updatedAt\":\"2026-10-23T12:55:28.2533173\"}', NULL, 'EXECUTED', 1, 'PRODUCT', NULL, NULL);
INSERT INTO `admin_action_logs` VALUES (74, 'STOCK_REPLENISH', 1, NULL, '2026-10-23 12:55:54.650442', '2026-10-23 12:55:54.650442', 'PAGE', NULL, NULL, '2026-10-23 12:55:54.654676', NULL, NULL, NULL, 1, 'ADMIN', 'bead158608d124855a2685b51091cd2598a12a83', '{\"delta\":-45,\"expectedStock\":null}', NULL, NULL, '{\"categoryId\":9,\"createdAt\":\"2026-09-07T16:42:59.619729\",\"description\":\"精选山东烟台红富士苹果，个大皮薄，果肉脆甜。\",\"id\":1,\"imageUrl\":\"p002\",\"images\":null,\"name\":\"红富士苹果\",\"originalPrice\":12.90,\"price\":9.90,\"sales\":0,\"status\":0,\"stock\":60,\"subtitle\":\"脆甜多汁 产地直供\",\"unit\":\"斤\",\"updatedAt\":\"2026-10-23T12:55:54.6536727\"}', NULL, 'EXECUTED', 1, 'PRODUCT', NULL, NULL);
INSERT INTO `admin_action_logs` VALUES (75, 'PROMOTION_CREATE', 1, NULL, NULL, '2026-10-27 16:37:47.430425', 'CHAT', NULL, NULL, NULL, '2026-10-27 16:42:47.423849', NULL, '0:0:0:0:0:0:0:1', 1, 'ADMIN', '0737ec7c296653260b98dba620d38d66be07cc44', '{\"name\":\"满 80 立减 8 元（全场通用）\",\"type\":\"FULL_REDUCTION\",\"threshold\":\"80\",\"discount\":\"8\",\"productId\":null,\"startTime\":\"2026-10-27T00:00:00\",\"endTime\":\"2026-10-30T23:59:59\",\"description\":\"全场满 80 元减 8 元，全店通用，2026-10-27 起至 2026-10-30 止。\"}', '{\"ok\":true,\"checkedAt\":\"2026-10-27T16:37:45\",\"checks\":[{\"code\":\"PERMISSION\",\"ok\":true,\"message\":\"具备 promotion:manage\"},{\"code\":\"FIELD_VALID\",\"ok\":true,\"message\":\"字段校验通过（与创建接口同一份实现）\"},{\"code\":\"PRODUCT\",\"ok\":true,\"message\":\"全店通用\"},{\"code\":\"DUPLICATE\",\"ok\":true,\"message\":\"没有同源活动\"}]}', 'pa_8840c0006c', NULL, '{\"name\":\"满 80 立减 8 元（全场通用）\",\"type\":\"FULL_REDUCTION\",\"threshold\":\"80\",\"discount\":\"8\",\"productId\":null,\"startTime\":\"2026-10-27T00:00:00\",\"endTime\":\"2026-10-30T23:59:59\",\"duplicateCount\":0,\"duplicates\":[]}', 'EXPIRED', NULL, 'PROMOTION', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/156.0.0.0 Safari/537.36 Edg/156.0.0.0', '{\"proposalId\":\"pa_8840c0006c\",\"action\":\"PROMOTION_CREATE\",\"actionLabel\":\"新建优惠活动\",\"targetType\":\"PROMOTION\",\"targetId\":null,\"targetLabel\":\"新建优惠活动 · 满 80 立减 8 元（全场通用）\",\"params\":{\"name\":\"满 80 立减 8 元（全场通用）\",\"type\":\"FULL_REDUCTION\",\"threshold\":\"80\",\"discount\":\"8\",\"productId\":null,\"startTime\":\"2026-10-27T00:00:00\",\"endTime\":\"2026-10-30T23:59:59\",\"description\":\"全场满 80 元减 8 元，全店通用，2026-10-27 起至 2026-10-30 止。\"},\"precheck\":{\"ok\":true,\"checkedAt\":\"2026-10-27T16:37:45\",\"checks\":[{\"code\":\"PERMISSION\",\"ok\":true,\"message\":\"具备 promotion:manage\"},{\"code\":\"FIELD_VALID\",\"ok\":true,\"message\":\"字段校验通过（与创建接口同一份实现）\"},{\"code\":\"PRODUCT\",\"ok\":true,\"message\":\"全店通用\"},{\"code\":\"DUPLICATE\",\"ok\":true,\"message\":\"没有同源活动\"}]},\"risk\":{\"level\":\"LOW\",\"reasons\":[],\"requiresSecondConfirm\":false,\"secondConfirmHint\":null,\"irreversible\":false,\"note\":\"活动一旦生效会立即影响下单价格\"},\"idempotencyKey\":\"promotion:6b6ebaa89b6370304244f22b91767717f55ebf0f\",\"expiresAt\":\"2026-10-27T16:42:47.423849+08:00\",\"snapshot\":{\"name\":\"满 80 立减 8 元（全场通用）\",\"type\":\"FULL_REDUCTION\",\"threshold\":\"80\",\"discount\":\"8\",\"productId\":null,\"startTime\":\"2026-10-27T00:00:00\",\"endTime\":\"2026-10-30T23:59:59\",\"duplicateCount\":0,\"duplicates\":[]},\"confirm\":{\"method\":\"POST\",\"path\":\"/api/admin/promotions\",\"body\":{\"name\":\"满 80 立减 8 元（全场通用）\",\"type\":\"FULL_REDUCTION\",\"threshold\":\"80\",\"discount\":\"8\",\"startTime\":\"2026-10-27T00:00:00\",\"endTime\":\"2026-10-30T23:59:59\",\"status\":1,\"description\":\"全场满 80 元减 8 元，全店通用，2026-10-27 起至 2026-10-30 止。\"},\"requiresHeader\":{\"Content-Type\":\"application/json\",\"Idempotency-Key\":\"promotion:6b6ebaa89b6370304244f22b91767717f55ebf0f\"}},\"reject\":null,\"fields\":[{\"label\":\"活动名称\",\"value\":\"满 80 立减 8 元（全场通用）\",\"tone\":\"normal\",\"mono\":false},{\"label\":\"优惠规则\",\"value\":\"满 ¥80 减 ¥8（相当于 9 折）\",\"tone\":\"normal\",\"mono\":false},{\"label\":\"适用范围\",\"value\":\"全店通用\",\"tone\":\"normal\",\"mono\":false},{\"label\":\"时间窗\",\"value\":\"2026-10-27 00:00 至 2026-10-30 23:59（3 天）\",\"tone\":\"normal\",\"mono\":false},{\"label\":\"生效影响\",\"value\":\"创建后立即影响下单实付与 GMV 口径，不可自动撤销\",\"tone\":\"normal\",\"mono\":false}]}');
INSERT INTO `admin_action_logs` VALUES (76, 'PROMOTION_CREATE', 1, NULL, NULL, '2026-10-27 16:44:56.592076', 'CHAT', NULL, NULL, NULL, '2026-10-27 16:49:56.582526', NULL, '0:0:0:0:0:0:0:1', 1, 'ADMIN', '46d19b656a6e667ab4500d1e4b2cd8c3009fd5a8', '{\"name\":\"满 80 立减 8 元（全场通用）\",\"type\":\"FULL_REDUCTION\",\"threshold\":\"80\",\"discount\":\"8\",\"productId\":null,\"startTime\":\"2026-10-27T00:00:00\",\"endTime\":\"2026-10-30T23:59:59\",\"description\":\"全店通用满减活动：单笔订单满 80 元减 8 元。\"}', '{\"ok\":true,\"checkedAt\":\"2026-10-27T16:44:53\",\"checks\":[{\"code\":\"PERMISSION\",\"ok\":true,\"message\":\"具备 promotion:manage\"},{\"code\":\"FIELD_VALID\",\"ok\":true,\"message\":\"字段校验通过（与创建接口同一份实现）\"},{\"code\":\"PRODUCT\",\"ok\":true,\"message\":\"全店通用\"},{\"code\":\"DUPLICATE\",\"ok\":true,\"message\":\"没有同源活动\"}]}', 'pa_5e6b8df0e5', NULL, '{\"name\":\"满 80 立减 8 元（全场通用）\",\"type\":\"FULL_REDUCTION\",\"threshold\":\"80\",\"discount\":\"8\",\"productId\":null,\"startTime\":\"2026-10-27T00:00:00\",\"endTime\":\"2026-10-30T23:59:59\",\"duplicateCount\":0,\"duplicates\":[]}', 'EXPIRED', NULL, 'PROMOTION', 'curl/8.21.0', '{\"proposalId\":\"pa_5e6b8df0e5\",\"action\":\"PROMOTION_CREATE\",\"actionLabel\":\"新建优惠活动\",\"targetType\":\"PROMOTION\",\"targetId\":null,\"targetLabel\":\"新建优惠活动 · 满 80 立减 8 元（全场通用）\",\"params\":{\"name\":\"满 80 立减 8 元（全场通用）\",\"type\":\"FULL_REDUCTION\",\"threshold\":\"80\",\"discount\":\"8\",\"productId\":null,\"startTime\":\"2026-10-27T00:00:00\",\"endTime\":\"2026-10-30T23:59:59\",\"description\":\"全店通用满减活动：单笔订单满 80 元减 8 元。\"},\"precheck\":{\"ok\":true,\"checkedAt\":\"2026-10-27T16:44:53\",\"checks\":[{\"code\":\"PERMISSION\",\"ok\":true,\"message\":\"具备 promotion:manage\"},{\"code\":\"FIELD_VALID\",\"ok\":true,\"message\":\"字段校验通过（与创建接口同一份实现）\"},{\"code\":\"PRODUCT\",\"ok\":true,\"message\":\"全店通用\"},{\"code\":\"DUPLICATE\",\"ok\":true,\"message\":\"没有同源活动\"}]},\"risk\":{\"level\":\"LOW\",\"reasons\":[],\"requiresSecondConfirm\":false,\"secondConfirmHint\":null,\"irreversible\":false,\"note\":\"活动一旦生效会立即影响下单价格\"},\"idempotencyKey\":\"promotion:6b6ebaa89b6370304244f22b91767717f55ebf0f\",\"expiresAt\":\"2026-10-27T16:49:56.5825263+08:00\",\"snapshot\":{\"name\":\"满 80 立减 8 元（全场通用）\",\"type\":\"FULL_REDUCTION\",\"threshold\":\"80\",\"discount\":\"8\",\"productId\":null,\"startTime\":\"2026-10-27T00:00:00\",\"endTime\":\"2026-10-30T23:59:59\",\"duplicateCount\":0,\"duplicates\":[]},\"confirm\":{\"method\":\"POST\",\"path\":\"/api/admin/promotions\",\"body\":{\"name\":\"满 80 立减 8 元（全场通用）\",\"type\":\"FULL_REDUCTION\",\"threshold\":\"80\",\"discount\":\"8\",\"startTime\":\"2026-10-27T00:00:00\",\"endTime\":\"2026-10-30T23:59:59\",\"status\":1,\"description\":\"全店通用满减活动：单笔订单满 80 元减 8 元。\"},\"requiresHeader\":{\"Idempotency-Key\":\"promotion:6b6ebaa89b6370304244f22b91767717f55ebf0f\",\"Content-Type\":\"application/json\"}},\"reject\":null,\"fields\":[{\"label\":\"活动名称\",\"value\":\"满 80 立减 8 元（全场通用）\",\"tone\":\"normal\",\"mono\":false},{\"label\":\"优惠规则\",\"value\":\"满 ¥80 减 ¥8（相当于 9 折）\",\"tone\":\"normal\",\"mono\":false},{\"label\":\"适用范围\",\"value\":\"全店通用\",\"tone\":\"normal\",\"mono\":false},{\"label\":\"时间窗\",\"value\":\"2026-10-27 00:00 至 2026-10-30 23:59（3 天）\",\"tone\":\"normal\",\"mono\":false},{\"label\":\"生效影响\",\"value\":\"创建后立即影响下单实付与 GMV 口径，不可自动撤销\",\"tone\":\"normal\",\"mono\":false}]}');
INSERT INTO `admin_action_logs` VALUES (77, 'PROMOTION_CREATE', 1, NULL, NULL, '2026-10-27 16:48:01.882894', 'CHAT', NULL, NULL, NULL, '2026-10-27 16:53:01.876963', NULL, '127.0.0.1', 1, 'ADMIN', '0b12906bec4b3098c6717a21bbed9c199e2a7be5', '{\"name\":\"满 80 立减 8 元（全场通用）\",\"type\":\"FULL_REDUCTION\",\"threshold\":\"80\",\"discount\":\"8\",\"productId\":null,\"startTime\":\"2026-10-27T00:00:00\",\"endTime\":\"2026-10-30T23:59:59\",\"description\":\"全店通用满减：满 80 元减 8 元\"}', '{\"ok\":true,\"checkedAt\":\"2026-10-27T16:47:59\",\"checks\":[{\"code\":\"PERMISSION\",\"ok\":true,\"message\":\"具备 promotion:manage\"},{\"code\":\"FIELD_VALID\",\"ok\":true,\"message\":\"字段校验通过（与创建接口同一份实现）\"},{\"code\":\"PRODUCT\",\"ok\":true,\"message\":\"全店通用\"},{\"code\":\"DUPLICATE\",\"ok\":true,\"message\":\"没有同源活动\"}]}', 'pa_5e4fe3f318', NULL, '{\"name\":\"满 80 立减 8 元（全场通用）\",\"type\":\"FULL_REDUCTION\",\"threshold\":\"80\",\"discount\":\"8\",\"productId\":null,\"startTime\":\"2026-10-27T00:00:00\",\"endTime\":\"2026-10-30T23:59:59\",\"duplicateCount\":0,\"duplicates\":[]}', 'EXPIRED', NULL, 'PROMOTION', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/148.0.0.0 Safari/537.36', '{\"proposalId\":\"pa_5e4fe3f318\",\"action\":\"PROMOTION_CREATE\",\"actionLabel\":\"新建优惠活动\",\"targetType\":\"PROMOTION\",\"targetId\":null,\"targetLabel\":\"新建优惠活动 · 满 80 立减 8 元（全场通用）\",\"params\":{\"name\":\"满 80 立减 8 元（全场通用）\",\"type\":\"FULL_REDUCTION\",\"threshold\":\"80\",\"discount\":\"8\",\"productId\":null,\"startTime\":\"2026-10-27T00:00:00\",\"endTime\":\"2026-10-30T23:59:59\",\"description\":\"全店通用满减：满 80 元减 8 元\"},\"precheck\":{\"ok\":true,\"checkedAt\":\"2026-10-27T16:47:59\",\"checks\":[{\"code\":\"PERMISSION\",\"ok\":true,\"message\":\"具备 promotion:manage\"},{\"code\":\"FIELD_VALID\",\"ok\":true,\"message\":\"字段校验通过（与创建接口同一份实现）\"},{\"code\":\"PRODUCT\",\"ok\":true,\"message\":\"全店通用\"},{\"code\":\"DUPLICATE\",\"ok\":true,\"message\":\"没有同源活动\"}]},\"risk\":{\"level\":\"LOW\",\"reasons\":[],\"requiresSecondConfirm\":false,\"secondConfirmHint\":null,\"irreversible\":false,\"note\":\"活动一旦生效会立即影响下单价格\"},\"idempotencyKey\":\"promotion:6b6ebaa89b6370304244f22b91767717f55ebf0f\",\"expiresAt\":\"2026-10-27T16:53:01.8769629+08:00\",\"snapshot\":{\"name\":\"满 80 立减 8 元（全场通用）\",\"type\":\"FULL_REDUCTION\",\"threshold\":\"80\",\"discount\":\"8\",\"productId\":null,\"startTime\":\"2026-10-27T00:00:00\",\"endTime\":\"2026-10-30T23:59:59\",\"duplicateCount\":0,\"duplicates\":[]},\"confirm\":{\"method\":\"POST\",\"path\":\"/api/admin/promotions\",\"body\":{\"name\":\"满 80 立减 8 元（全场通用）\",\"type\":\"FULL_REDUCTION\",\"threshold\":\"80\",\"discount\":\"8\",\"startTime\":\"2026-10-27T00:00:00\",\"endTime\":\"2026-10-30T23:59:59\",\"status\":1,\"description\":\"全店通用满减：满 80 元减 8 元\"},\"requiresHeader\":{\"Content-Type\":\"application/json\",\"Idempotency-Key\":\"promotion:6b6ebaa89b6370304244f22b91767717f55ebf0f\"}},\"reject\":null,\"fields\":[{\"label\":\"活动名称\",\"value\":\"满 80 立减 8 元（全场通用）\",\"tone\":\"normal\",\"mono\":false},{\"label\":\"优惠规则\",\"value\":\"满 ¥80 减 ¥8（相当于 9 折）\",\"tone\":\"normal\",\"mono\":false},{\"label\":\"适用范围\",\"value\":\"全店通用\",\"tone\":\"normal\",\"mono\":false},{\"label\":\"时间窗\",\"value\":\"2026-10-27 00:00 至 2026-10-30 23:59（3 天）\",\"tone\":\"normal\",\"mono\":false},{\"label\":\"生效影响\",\"value\":\"创建后立即影响下单实付与 GMV 口径，不可自动撤销\",\"tone\":\"normal\",\"mono\":false}]}');
INSERT INTO `admin_action_logs` VALUES (78, 'PROMOTION_CREATE', 1, NULL, NULL, '2026-10-27 16:51:07.571233', 'CHAT', NULL, NULL, NULL, '2026-10-27 16:56:07.569869', NULL, '127.0.0.1', 1, 'ADMIN', '0b12906bec4b3098c6717a21bbed9c199e2a7be5', '{\"name\":\"满 80 立减 8 元（全场通用）\",\"type\":\"FULL_REDUCTION\",\"threshold\":\"80\",\"discount\":\"8\",\"productId\":null,\"startTime\":\"2026-10-27T00:00:00\",\"endTime\":\"2026-10-30T23:59:59\",\"description\":\"全店通用满减：满 80 元减 8 元\"}', '{\"ok\":true,\"checkedAt\":\"2026-10-27T16:51:03\",\"checks\":[{\"code\":\"PERMISSION\",\"ok\":true,\"message\":\"具备 promotion:manage\"},{\"code\":\"FIELD_VALID\",\"ok\":true,\"message\":\"字段校验通过（与创建接口同一份实现）\"},{\"code\":\"PRODUCT\",\"ok\":true,\"message\":\"全店通用\"},{\"code\":\"DUPLICATE\",\"ok\":true,\"message\":\"没有同源活动\"}]}', 'pa_0f85ae8099', NULL, '{\"name\":\"满 80 立减 8 元（全场通用）\",\"type\":\"FULL_REDUCTION\",\"threshold\":\"80\",\"discount\":\"8\",\"productId\":null,\"startTime\":\"2026-10-27T00:00:00\",\"endTime\":\"2026-10-30T23:59:59\",\"duplicateCount\":0,\"duplicates\":[]}', 'EXPIRED', NULL, 'PROMOTION', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/148.0.0.0 Safari/537.36', '{\"proposalId\":\"pa_0f85ae8099\",\"action\":\"PROMOTION_CREATE\",\"actionLabel\":\"新建优惠活动\",\"targetType\":\"PROMOTION\",\"targetId\":null,\"targetLabel\":\"新建优惠活动 · 满 80 立减 8 元（全场通用）\",\"params\":{\"name\":\"满 80 立减 8 元（全场通用）\",\"type\":\"FULL_REDUCTION\",\"threshold\":\"80\",\"discount\":\"8\",\"productId\":null,\"startTime\":\"2026-10-27T00:00:00\",\"endTime\":\"2026-10-30T23:59:59\",\"description\":\"全店通用满减：满 80 元减 8 元\"},\"precheck\":{\"ok\":true,\"checkedAt\":\"2026-10-27T16:51:03\",\"checks\":[{\"code\":\"PERMISSION\",\"ok\":true,\"message\":\"具备 promotion:manage\"},{\"code\":\"FIELD_VALID\",\"ok\":true,\"message\":\"字段校验通过（与创建接口同一份实现）\"},{\"code\":\"PRODUCT\",\"ok\":true,\"message\":\"全店通用\"},{\"code\":\"DUPLICATE\",\"ok\":true,\"message\":\"没有同源活动\"}]},\"risk\":{\"level\":\"LOW\",\"reasons\":[],\"requiresSecondConfirm\":false,\"secondConfirmHint\":null,\"irreversible\":false,\"note\":\"活动一旦生效会立即影响下单价格\"},\"idempotencyKey\":\"promotion:6b6ebaa89b6370304244f22b91767717f55ebf0f\",\"expiresAt\":\"2026-10-27T16:56:07.5698686+08:00\",\"snapshot\":{\"name\":\"满 80 立减 8 元（全场通用）\",\"type\":\"FULL_REDUCTION\",\"threshold\":\"80\",\"discount\":\"8\",\"productId\":null,\"startTime\":\"2026-10-27T00:00:00\",\"endTime\":\"2026-10-30T23:59:59\",\"duplicateCount\":0,\"duplicates\":[]},\"confirm\":{\"method\":\"POST\",\"path\":\"/api/admin/promotions\",\"body\":{\"name\":\"满 80 立减 8 元（全场通用）\",\"type\":\"FULL_REDUCTION\",\"threshold\":\"80\",\"discount\":\"8\",\"startTime\":\"2026-10-27T00:00:00\",\"endTime\":\"2026-10-30T23:59:59\",\"status\":1,\"description\":\"全店通用满减：满 80 元减 8 元\"},\"requiresHeader\":{\"Content-Type\":\"application/json\",\"Idempotency-Key\":\"promotion:6b6ebaa89b6370304244f22b91767717f55ebf0f\"}},\"reject\":null,\"fields\":[{\"label\":\"活动名称\",\"value\":\"满 80 立减 8 元（全场通用）\",\"tone\":\"normal\",\"mono\":false},{\"label\":\"优惠规则\",\"value\":\"满 ¥80 减 ¥8（相当于 9 折）\",\"tone\":\"normal\",\"mono\":false},{\"label\":\"适用范围\",\"value\":\"全店通用\",\"tone\":\"normal\",\"mono\":false},{\"label\":\"时间窗\",\"value\":\"2026-10-27 00:00 至 2026-10-30 23:59（3 天）\",\"tone\":\"normal\",\"mono\":false},{\"label\":\"生效影响\",\"value\":\"创建后立即影响下单实付与 GMV 口径，不可自动撤销\",\"tone\":\"normal\",\"mono\":false}]}');
INSERT INTO `admin_action_logs` VALUES (79, 'PROMOTION_CREATE', 1, NULL, NULL, '2026-10-27 16:51:56.527869', 'CHAT', NULL, NULL, NULL, '2026-10-27 16:56:56.526845', NULL, '127.0.0.1', 1, 'ADMIN', '0b12906bec4b3098c6717a21bbed9c199e2a7be5', '{\"name\":\"满 80 立减 8 元（全场通用）\",\"type\":\"FULL_REDUCTION\",\"threshold\":\"80\",\"discount\":\"8\",\"productId\":null,\"startTime\":\"2026-10-27T00:00:00\",\"endTime\":\"2026-10-30T23:59:59\",\"description\":\"全店通用满减：满 80 元减 8 元\"}', '{\"ok\":true,\"checkedAt\":\"2026-10-27T16:51:53\",\"checks\":[{\"code\":\"PERMISSION\",\"ok\":true,\"message\":\"具备 promotion:manage\"},{\"code\":\"FIELD_VALID\",\"ok\":true,\"message\":\"字段校验通过（与创建接口同一份实现）\"},{\"code\":\"PRODUCT\",\"ok\":true,\"message\":\"全店通用\"},{\"code\":\"DUPLICATE\",\"ok\":true,\"message\":\"没有同源活动\"}]}', 'pa_6e4253f457', NULL, '{\"name\":\"满 80 立减 8 元（全场通用）\",\"type\":\"FULL_REDUCTION\",\"threshold\":\"80\",\"discount\":\"8\",\"productId\":null,\"startTime\":\"2026-10-27T00:00:00\",\"endTime\":\"2026-10-30T23:59:59\",\"duplicateCount\":0,\"duplicates\":[]}', 'EXPIRED', NULL, 'PROMOTION', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/148.0.0.0 Safari/537.36', '{\"proposalId\":\"pa_6e4253f457\",\"action\":\"PROMOTION_CREATE\",\"actionLabel\":\"新建优惠活动\",\"targetType\":\"PROMOTION\",\"targetId\":null,\"targetLabel\":\"新建优惠活动 · 满 80 立减 8 元（全场通用）\",\"params\":{\"name\":\"满 80 立减 8 元（全场通用）\",\"type\":\"FULL_REDUCTION\",\"threshold\":\"80\",\"discount\":\"8\",\"productId\":null,\"startTime\":\"2026-10-27T00:00:00\",\"endTime\":\"2026-10-30T23:59:59\",\"description\":\"全店通用满减：满 80 元减 8 元\"},\"precheck\":{\"ok\":true,\"checkedAt\":\"2026-10-27T16:51:53\",\"checks\":[{\"code\":\"PERMISSION\",\"ok\":true,\"message\":\"具备 promotion:manage\"},{\"code\":\"FIELD_VALID\",\"ok\":true,\"message\":\"字段校验通过（与创建接口同一份实现）\"},{\"code\":\"PRODUCT\",\"ok\":true,\"message\":\"全店通用\"},{\"code\":\"DUPLICATE\",\"ok\":true,\"message\":\"没有同源活动\"}]},\"risk\":{\"level\":\"LOW\",\"reasons\":[],\"requiresSecondConfirm\":false,\"secondConfirmHint\":null,\"irreversible\":false,\"note\":\"活动一旦生效会立即影响下单价格\"},\"idempotencyKey\":\"promotion:6b6ebaa89b6370304244f22b91767717f55ebf0f\",\"expiresAt\":\"2026-10-27T16:56:56.5268448+08:00\",\"snapshot\":{\"name\":\"满 80 立减 8 元（全场通用）\",\"type\":\"FULL_REDUCTION\",\"threshold\":\"80\",\"discount\":\"8\",\"productId\":null,\"startTime\":\"2026-10-27T00:00:00\",\"endTime\":\"2026-10-30T23:59:59\",\"duplicateCount\":0,\"duplicates\":[]},\"confirm\":{\"method\":\"POST\",\"path\":\"/api/admin/promotions\",\"body\":{\"name\":\"满 80 立减 8 元（全场通用）\",\"type\":\"FULL_REDUCTION\",\"threshold\":\"80\",\"discount\":\"8\",\"startTime\":\"2026-10-27T00:00:00\",\"endTime\":\"2026-10-30T23:59:59\",\"status\":1,\"description\":\"全店通用满减：满 80 元减 8 元\"},\"requiresHeader\":{\"Content-Type\":\"application/json\",\"Idempotency-Key\":\"promotion:6b6ebaa89b6370304244f22b91767717f55ebf0f\"}},\"reject\":null,\"fields\":[{\"label\":\"活动名称\",\"value\":\"满 80 立减 8 元（全场通用）\",\"tone\":\"normal\",\"mono\":false},{\"label\":\"优惠规则\",\"value\":\"满 ¥80 减 ¥8（相当于 9 折）\",\"tone\":\"normal\",\"mono\":false},{\"label\":\"适用范围\",\"value\":\"全店通用\",\"tone\":\"normal\",\"mono\":false},{\"label\":\"时间窗\",\"value\":\"2026-10-27 00:00 至 2026-10-30 23:59（3 天）\",\"tone\":\"normal\",\"mono\":false},{\"label\":\"生效影响\",\"value\":\"创建后立即影响下单实付与 GMV 口径，不可自动撤销\",\"tone\":\"normal\",\"mono\":false}]}');
INSERT INTO `admin_action_logs` VALUES (80, 'PROMOTION_CREATE', 1, NULL, NULL, '2026-10-27 16:53:37.213240', 'CHAT', NULL, NULL, NULL, '2026-10-27 16:58:37.212240', NULL, '127.0.0.1', 1, 'ADMIN', '0b12906bec4b3098c6717a21bbed9c199e2a7be5', '{\"name\":\"满 80 立减 8 元（全场通用）\",\"type\":\"FULL_REDUCTION\",\"threshold\":\"80\",\"discount\":\"8\",\"productId\":null,\"startTime\":\"2026-10-27T00:00:00\",\"endTime\":\"2026-10-30T23:59:59\",\"description\":\"全店通用满减：满 80 元减 8 元\"}', '{\"ok\":true,\"checkedAt\":\"2026-10-27T16:53:34\",\"checks\":[{\"code\":\"PERMISSION\",\"ok\":true,\"message\":\"具备 promotion:manage\"},{\"code\":\"FIELD_VALID\",\"ok\":true,\"message\":\"字段校验通过（与创建接口同一份实现）\"},{\"code\":\"PRODUCT\",\"ok\":true,\"message\":\"全店通用\"},{\"code\":\"DUPLICATE\",\"ok\":true,\"message\":\"没有同源活动\"}]}', 'pa_a1be8937e4', NULL, '{\"name\":\"满 80 立减 8 元（全场通用）\",\"type\":\"FULL_REDUCTION\",\"threshold\":\"80\",\"discount\":\"8\",\"productId\":null,\"startTime\":\"2026-10-27T00:00:00\",\"endTime\":\"2026-10-30T23:59:59\",\"duplicateCount\":0,\"duplicates\":[]}', 'EXPIRED', NULL, 'PROMOTION', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/148.0.0.0 Safari/537.36', '{\"proposalId\":\"pa_a1be8937e4\",\"action\":\"PROMOTION_CREATE\",\"actionLabel\":\"新建优惠活动\",\"targetType\":\"PROMOTION\",\"targetId\":null,\"targetLabel\":\"新建优惠活动 · 满 80 立减 8 元（全场通用）\",\"params\":{\"name\":\"满 80 立减 8 元（全场通用）\",\"type\":\"FULL_REDUCTION\",\"threshold\":\"80\",\"discount\":\"8\",\"productId\":null,\"startTime\":\"2026-10-27T00:00:00\",\"endTime\":\"2026-10-30T23:59:59\",\"description\":\"全店通用满减：满 80 元减 8 元\"},\"precheck\":{\"ok\":true,\"checkedAt\":\"2026-10-27T16:53:34\",\"checks\":[{\"code\":\"PERMISSION\",\"ok\":true,\"message\":\"具备 promotion:manage\"},{\"code\":\"FIELD_VALID\",\"ok\":true,\"message\":\"字段校验通过（与创建接口同一份实现）\"},{\"code\":\"PRODUCT\",\"ok\":true,\"message\":\"全店通用\"},{\"code\":\"DUPLICATE\",\"ok\":true,\"message\":\"没有同源活动\"}]},\"risk\":{\"level\":\"LOW\",\"reasons\":[],\"requiresSecondConfirm\":false,\"secondConfirmHint\":null,\"irreversible\":false,\"note\":\"活动一旦生效会立即影响下单价格\"},\"idempotencyKey\":\"promotion:6b6ebaa89b6370304244f22b91767717f55ebf0f\",\"expiresAt\":\"2026-10-27T16:58:37.2122395+08:00\",\"snapshot\":{\"name\":\"满 80 立减 8 元（全场通用）\",\"type\":\"FULL_REDUCTION\",\"threshold\":\"80\",\"discount\":\"8\",\"productId\":null,\"startTime\":\"2026-10-27T00:00:00\",\"endTime\":\"2026-10-30T23:59:59\",\"duplicateCount\":0,\"duplicates\":[]},\"confirm\":{\"method\":\"POST\",\"path\":\"/api/admin/promotions\",\"body\":{\"name\":\"满 80 立减 8 元（全场通用）\",\"type\":\"FULL_REDUCTION\",\"threshold\":\"80\",\"discount\":\"8\",\"startTime\":\"2026-10-27T00:00:00\",\"endTime\":\"2026-10-30T23:59:59\",\"status\":1,\"description\":\"全店通用满减：满 80 元减 8 元\"},\"requiresHeader\":{\"Content-Type\":\"application/json\",\"Idempotency-Key\":\"promotion:6b6ebaa89b6370304244f22b91767717f55ebf0f\"}},\"reject\":null,\"fields\":[{\"label\":\"活动名称\",\"value\":\"满 80 立减 8 元（全场通用）\",\"tone\":\"normal\",\"mono\":false},{\"label\":\"优惠规则\",\"value\":\"满 ¥80 减 ¥8（相当于 9 折）\",\"tone\":\"normal\",\"mono\":false},{\"label\":\"适用范围\",\"value\":\"全店通用\",\"tone\":\"normal\",\"mono\":false},{\"label\":\"时间窗\",\"value\":\"2026-10-27 00:00 至 2026-10-30 23:59（3 天）\",\"tone\":\"normal\",\"mono\":false},{\"label\":\"生效影响\",\"value\":\"创建后立即影响下单实付与 GMV 口径，不可自动撤销\",\"tone\":\"normal\",\"mono\":false}]}');
INSERT INTO `admin_action_logs` VALUES (81, 'PROMOTION_CREATE', 1, NULL, NULL, '2026-10-27 16:56:35.718675', 'CHAT', NULL, NULL, NULL, '2026-10-27 17:01:35.718675', NULL, '127.0.0.1', 1, 'ADMIN', '8d1dcc179157c4b8739244ad5a7ffa8d4136a938', '{\"name\":\"满 200 减 30 元（全场通用）\",\"type\":\"FULL_REDUCTION\",\"threshold\":\"200\",\"discount\":\"30\",\"productId\":null,\"startTime\":\"2026-11-01T00:00:00\",\"endTime\":\"2026-11-05T23:59:59\",\"description\":\"全店通用满减：满 200 元减 30 元\"}', '{\"ok\":true,\"checkedAt\":\"2026-10-27T16:56:32\",\"checks\":[{\"code\":\"PERMISSION\",\"ok\":true,\"message\":\"具备 promotion:manage\"},{\"code\":\"FIELD_VALID\",\"ok\":true,\"message\":\"字段校验通过（与创建接口同一份实现）\"},{\"code\":\"PRODUCT\",\"ok\":true,\"message\":\"全店通用\"},{\"code\":\"DUPLICATE\",\"ok\":true,\"message\":\"没有同源活动\"}]}', 'pa_e20199bf88', NULL, '{\"name\":\"满 200 减 30 元（全场通用）\",\"type\":\"FULL_REDUCTION\",\"threshold\":\"200\",\"discount\":\"30\",\"productId\":null,\"startTime\":\"2026-11-01T00:00:00\",\"endTime\":\"2026-11-05T23:59:59\",\"duplicateCount\":0,\"duplicates\":[]}', 'EXPIRED', NULL, 'PROMOTION', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/148.0.0.0 Safari/537.36', '{\"proposalId\":\"pa_e20199bf88\",\"action\":\"PROMOTION_CREATE\",\"actionLabel\":\"新建优惠活动\",\"targetType\":\"PROMOTION\",\"targetId\":null,\"targetLabel\":\"新建优惠活动 · 满 200 减 30 元（全场通用）\",\"params\":{\"name\":\"满 200 减 30 元（全场通用）\",\"type\":\"FULL_REDUCTION\",\"threshold\":\"200\",\"discount\":\"30\",\"productId\":null,\"startTime\":\"2026-11-01T00:00:00\",\"endTime\":\"2026-11-05T23:59:59\",\"description\":\"全店通用满减：满 200 元减 30 元\"},\"precheck\":{\"ok\":true,\"checkedAt\":\"2026-10-27T16:56:32\",\"checks\":[{\"code\":\"PERMISSION\",\"ok\":true,\"message\":\"具备 promotion:manage\"},{\"code\":\"FIELD_VALID\",\"ok\":true,\"message\":\"字段校验通过（与创建接口同一份实现）\"},{\"code\":\"PRODUCT\",\"ok\":true,\"message\":\"全店通用\"},{\"code\":\"DUPLICATE\",\"ok\":true,\"message\":\"没有同源活动\"}]},\"risk\":{\"level\":\"LOW\",\"reasons\":[],\"requiresSecondConfirm\":false,\"secondConfirmHint\":null,\"irreversible\":false,\"note\":\"活动一旦生效会立即影响下单价格\"},\"idempotencyKey\":\"promotion:4d55adc029d3cd509ac4764e33e0885adc119f89\",\"expiresAt\":\"2026-10-27T17:01:35.7186747+08:00\",\"snapshot\":{\"name\":\"满 200 减 30 元（全场通用）\",\"type\":\"FULL_REDUCTION\",\"threshold\":\"200\",\"discount\":\"30\",\"productId\":null,\"startTime\":\"2026-11-01T00:00:00\",\"endTime\":\"2026-11-05T23:59:59\",\"duplicateCount\":0,\"duplicates\":[]},\"confirm\":{\"method\":\"POST\",\"path\":\"/api/admin/promotions\",\"body\":{\"name\":\"满 200 减 30 元（全场通用）\",\"type\":\"FULL_REDUCTION\",\"threshold\":\"200\",\"discount\":\"30\",\"startTime\":\"2026-11-01T00:00:00\",\"endTime\":\"2026-11-05T23:59:59\",\"status\":1,\"description\":\"全店通用满减：满 200 元减 30 元\"},\"requiresHeader\":{\"Content-Type\":\"application/json\",\"Idempotency-Key\":\"promotion:4d55adc029d3cd509ac4764e33e0885adc119f89\"}},\"reject\":null,\"fields\":[{\"label\":\"活动名称\",\"value\":\"满 200 减 30 元（全场通用）\",\"tone\":\"normal\",\"mono\":false},{\"label\":\"优惠规则\",\"value\":\"满 ¥200 减 ¥30（相当于 8.5 折）\",\"tone\":\"normal\",\"mono\":false},{\"label\":\"适用范围\",\"value\":\"全店通用\",\"tone\":\"normal\",\"mono\":false},{\"label\":\"时间窗\",\"value\":\"2026-11-01 00:00 至 2026-11-05 23:59（4 天）\",\"tone\":\"normal\",\"mono\":false},{\"label\":\"生效影响\",\"value\":\"创建后立即影响下单实付与 GMV 口径，不可自动撤销\",\"tone\":\"normal\",\"mono\":false}]}');
INSERT INTO `admin_action_logs` VALUES (85, 'PROMOTION_CREATE', 1, NULL, NULL, '2026-10-27 18:22:22.578754', 'CHAT', NULL, NULL, NULL, '2026-10-27 18:23:25.000000', NULL, '0:0:0:0:0:0:0:1', 1, 'ADMIN', '4986694418b23a1ae4a0589cdcc7c532aeb2f7eb', '{\"name\":\"全场满 500 减 50（12.01-12.05）\",\"type\":\"FULL_REDUCTION\",\"threshold\":\"500\",\"discount\":\"50\",\"productId\":null,\"startTime\":\"2026-12-01T00:00:00\",\"endTime\":\"2026-12-05T23:59:59\",\"description\":\"全店通用满减活动：单笔订单满 500 元减 50 元，活动期 2026-12-01 至 2026-12-05。\"}', '{\"ok\":true,\"checkedAt\":\"2026-10-27T18:22:20\",\"checks\":[{\"code\":\"PERMISSION\",\"ok\":true,\"message\":\"具备 promotion:manage\"},{\"code\":\"FIELD_VALID\",\"ok\":true,\"message\":\"字段校验通过（与创建接口同一份实现）\"},{\"code\":\"PRODUCT\",\"ok\":true,\"message\":\"全店通用\"},{\"code\":\"DUPLICATE\",\"ok\":true,\"message\":\"没有同源活动\"}]}', 'pa_f40beeed52', NULL, '{\"name\":\"全场满 500 减 50（12.01-12.05）\",\"type\":\"FULL_REDUCTION\",\"threshold\":\"500\",\"discount\":\"50\",\"productId\":null,\"startTime\":\"2026-12-01T00:00:00\",\"endTime\":\"2026-12-05T23:59:59\",\"duplicateCount\":0,\"duplicates\":[]}', 'EXPIRED', NULL, 'PROMOTION', 'curl/8.21.0', '{\"proposalId\":\"pa_f40beeed52\",\"action\":\"PROMOTION_CREATE\",\"actionLabel\":\"新建优惠活动\",\"targetType\":\"PROMOTION\",\"targetId\":null,\"targetLabel\":\"新建优惠活动 · 全场满 500 减 50（12.01-12.05）\",\"params\":{\"name\":\"全场满 500 减 50（12.01-12.05）\",\"type\":\"FULL_REDUCTION\",\"threshold\":\"500\",\"discount\":\"50\",\"productId\":null,\"startTime\":\"2026-12-01T00:00:00\",\"endTime\":\"2026-12-05T23:59:59\",\"description\":\"全店通用满减活动：单笔订单满 500 元减 50 元，活动期 2026-12-01 至 2026-12-05。\"},\"precheck\":{\"ok\":true,\"checkedAt\":\"2026-10-27T18:22:20\",\"checks\":[{\"code\":\"PERMISSION\",\"ok\":true,\"message\":\"具备 promotion:manage\"},{\"code\":\"FIELD_VALID\",\"ok\":true,\"message\":\"字段校验通过（与创建接口同一份实现）\"},{\"code\":\"PRODUCT\",\"ok\":true,\"message\":\"全店通用\"},{\"code\":\"DUPLICATE\",\"ok\":true,\"message\":\"没有同源活动\"}]},\"risk\":{\"level\":\"LOW\",\"reasons\":[],\"requiresSecondConfirm\":false,\"secondConfirmHint\":null,\"irreversible\":false,\"note\":\"活动一旦生效会立即影响下单价格\"},\"idempotencyKey\":\"promotion:674b6f9dfa16777262bc6e41346f360ad1f4ee62\",\"expiresAt\":\"2026-10-27T18:52:22.5682789+08:00\",\"snapshot\":{\"name\":\"全场满 500 减 50（12.01-12.05）\",\"type\":\"FULL_REDUCTION\",\"threshold\":\"500\",\"discount\":\"50\",\"productId\":null,\"startTime\":\"2026-12-01T00:00:00\",\"endTime\":\"2026-12-05T23:59:59\",\"duplicateCount\":0,\"duplicates\":[]},\"confirm\":{\"method\":\"POST\",\"path\":\"/api/admin/promotions\",\"body\":{\"name\":\"全场满 500 减 50（12.01-12.05）\",\"type\":\"FULL_REDUCTION\",\"threshold\":\"500\",\"discount\":\"50\",\"startTime\":\"2026-12-01T00:00:00\",\"endTime\":\"2026-12-05T23:59:59\",\"status\":1,\"description\":\"全店通用满减活动：单笔订单满 500 元减 50 元，活动期 2026-12-01 至 2026-12-05。\"},\"requiresHeader\":{\"Idempotency-Key\":\"promotion:674b6f9dfa16777262bc6e41346f360ad1f4ee62\",\"Content-Type\":\"application/json\"}},\"reject\":null,\"fields\":[{\"label\":\"活动名称\",\"value\":\"全场满 500 减 50（12.01-12.05）\",\"tone\":\"normal\",\"mono\":false},{\"label\":\"优惠规则\",\"value\":\"满 ¥500 减 ¥50（相当于 9 折）\",\"tone\":\"normal\",\"mono\":false},{\"label\":\"适用范围\",\"value\":\"全店通用\",\"tone\":\"normal\",\"mono\":false},{\"label\":\"时间窗\",\"value\":\"2026-12-01 00:00 至 2026-12-05 23:59（4 天）\",\"tone\":\"normal\",\"mono\":false},{\"label\":\"生效影响\",\"value\":\"创建后立即影响下单实付与 GMV 口径，不可自动撤销\",\"tone\":\"normal\",\"mono\":false}]}');
INSERT INTO `admin_action_logs` VALUES (86, 'PROMOTION_CREATE', 1, NULL, NULL, '2026-10-27 18:27:45.949726', 'CHAT', NULL, NULL, NULL, '2026-10-27 18:30:31.000000', NULL, '0:0:0:0:0:0:0:1', 1, 'ADMIN', '581b056e75721337dbc3d6256e4d83a23d8e97b4', '{\"name\":\"全场满 600 减 60（12.20-12.25）\",\"type\":\"FULL_REDUCTION\",\"threshold\":\"600\",\"discount\":\"60\",\"productId\":null,\"startTime\":\"2026-12-20T00:00:00\",\"endTime\":\"2026-12-25T23:59:59\",\"description\":\"全店通用满减：单笔满 600 元减 60 元，有效期 2026-12-20 至 2026-12-25\"}', '{\"ok\":true,\"checkedAt\":\"2026-10-27T18:27:43\",\"checks\":[{\"code\":\"PERMISSION\",\"ok\":true,\"message\":\"具备 promotion:manage\"},{\"code\":\"FIELD_VALID\",\"ok\":true,\"message\":\"字段校验通过（与创建接口同一份实现）\"},{\"code\":\"PRODUCT\",\"ok\":true,\"message\":\"全店通用\"},{\"code\":\"DUPLICATE\",\"ok\":true,\"message\":\"没有同源活动\"}]}', 'pa_e55ed53e29', NULL, '{\"name\":\"全场满 600 减 60（12.20-12.25）\",\"type\":\"FULL_REDUCTION\",\"threshold\":\"600\",\"discount\":\"60\",\"productId\":null,\"startTime\":\"2026-12-20T00:00:00\",\"endTime\":\"2026-12-25T23:59:59\",\"duplicateCount\":0,\"duplicates\":[]}', 'EXPIRED', NULL, 'PROMOTION', 'curl/8.21.0', '{\"proposalId\":\"pa_e55ed53e29\",\"action\":\"PROMOTION_CREATE\",\"actionLabel\":\"新建优惠活动\",\"targetType\":\"PROMOTION\",\"targetId\":null,\"targetLabel\":\"新建优惠活动 · 全场满 600 减 60（12.20-12.25）\",\"params\":{\"name\":\"全场满 600 减 60（12.20-12.25）\",\"type\":\"FULL_REDUCTION\",\"threshold\":\"600\",\"discount\":\"60\",\"productId\":null,\"startTime\":\"2026-12-20T00:00:00\",\"endTime\":\"2026-12-25T23:59:59\",\"description\":\"全店通用满减：单笔满 600 元减 60 元，有效期 2026-12-20 至 2026-12-25\"},\"precheck\":{\"ok\":true,\"checkedAt\":\"2026-10-27T18:27:43\",\"checks\":[{\"code\":\"PERMISSION\",\"ok\":true,\"message\":\"具备 promotion:manage\"},{\"code\":\"FIELD_VALID\",\"ok\":true,\"message\":\"字段校验通过（与创建接口同一份实现）\"},{\"code\":\"PRODUCT\",\"ok\":true,\"message\":\"全店通用\"},{\"code\":\"DUPLICATE\",\"ok\":true,\"message\":\"没有同源活动\"}]},\"risk\":{\"level\":\"LOW\",\"reasons\":[],\"requiresSecondConfirm\":false,\"secondConfirmHint\":null,\"irreversible\":false,\"note\":\"活动一旦生效会立即影响下单价格\"},\"idempotencyKey\":\"promotion:12ae45516b6aa30dff051d4955bd8ce4f572accc\",\"expiresAt\":\"2026-10-27T18:57:45.934036+08:00\",\"snapshot\":{\"name\":\"全场满 600 减 60（12.20-12.25）\",\"type\":\"FULL_REDUCTION\",\"threshold\":\"600\",\"discount\":\"60\",\"productId\":null,\"startTime\":\"2026-12-20T00:00:00\",\"endTime\":\"2026-12-25T23:59:59\",\"duplicateCount\":0,\"duplicates\":[]},\"confirm\":{\"method\":\"POST\",\"path\":\"/api/admin/promotions\",\"body\":{\"name\":\"全场满 600 减 60（12.20-12.25）\",\"type\":\"FULL_REDUCTION\",\"threshold\":\"600\",\"discount\":\"60\",\"startTime\":\"2026-12-20T00:00:00\",\"endTime\":\"2026-12-25T23:59:59\",\"status\":1,\"description\":\"全店通用满减：单笔满 600 元减 60 元，有效期 2026-12-20 至 2026-12-25\"},\"requiresHeader\":{\"Idempotency-Key\":\"promotion:12ae45516b6aa30dff051d4955bd8ce4f572accc\",\"Content-Type\":\"application/json\"}},\"reject\":null,\"fields\":[{\"label\":\"活动名称\",\"value\":\"全场满 600 减 60（12.20-12.25）\",\"tone\":\"normal\",\"mono\":false},{\"label\":\"优惠规则\",\"value\":\"满 ¥600 减 ¥60（相当于 9 折）\",\"tone\":\"normal\",\"mono\":false},{\"label\":\"适用范围\",\"value\":\"全店通用\",\"tone\":\"normal\",\"mono\":false},{\"label\":\"时间窗\",\"value\":\"2026-12-20 00:00 至 2026-12-25 23:59（5 天）\",\"tone\":\"normal\",\"mono\":false},{\"label\":\"生效影响\",\"value\":\"创建后立即影响下单实付与 GMV 口径，不可自动撤销\",\"tone\":\"normal\",\"mono\":false}]}');
INSERT INTO `admin_action_logs` VALUES (87, 'PROMOTION_CREATE', 1, NULL, '2026-10-27 20:14:25.066955', '2026-10-27 20:14:03.033058', 'CHAT', NULL, NULL, '2026-10-27 20:14:25.084695', '2026-10-27 20:44:03.024563', 'promotion:fdb0fa060123e98223435076619a4aaf5ae74e0a', '0:0:0:0:0:0:0:1', 1, 'ADMIN', 'c0aaafde6b03dad4c2ad4410ab3e7bb0b3b22e22', '{\"name\":\"全场满100减60（11.01-11.11）\",\"type\":\"FULL_REDUCTION\",\"threshold\":\"100\",\"discount\":\"60\",\"productId\":null,\"startTime\":\"2026-11-01T00:00:00\",\"endTime\":\"2026-11-11T23:59:59\",\"description\":\"全场通用满减活动：单笔满 100 元减 60 元，有效期 2026-11-01 至 2026-11-11。\"}', '{\"ok\":true,\"checkedAt\":\"2026-10-27T20:13:57\",\"checks\":[{\"code\":\"PERMISSION\",\"ok\":true,\"message\":\"具备 promotion:manage\"},{\"code\":\"FIELD_VALID\",\"ok\":true,\"message\":\"字段校验通过（与创建接口同一份实现）\"},{\"code\":\"PRODUCT\",\"ok\":true,\"message\":\"全店通用\"},{\"code\":\"DUPLICATE\",\"ok\":true,\"message\":\"没有同源活动\"}]}', 'pa_eaac10b60c', '{\"createdAt\":\"2026-10-27T20:14:25.0825664\",\"description\":\"全场通用满减活动：单笔满 100 元减 60 元，有效期 2026-11-01 至 2026-11-11。\",\"discount\":60,\"endTime\":\"2026-11-11T23:59:59\",\"id\":12,\"imageUrl\":null,\"link\":null,\"name\":\"全场满100减60（11.01-11.11）\",\"productId\":null,\"startTime\":\"2026-11-01T00:00:00\",\"status\":1,\"threshold\":100,\"type\":\"FULL_REDUCTION\"}', '{\"name\":\"全场满100减60（11.01-11.11）\",\"type\":\"FULL_REDUCTION\",\"threshold\":\"100\",\"discount\":\"60\",\"productId\":null,\"startTime\":\"2026-11-01T00:00:00\",\"endTime\":\"2026-11-11T23:59:59\",\"duplicateCount\":0,\"duplicates\":[]}', 'EXECUTED', NULL, 'PROMOTION', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/156.0.0.0 Safari/537.36 Edg/156.0.0.0', '{\"proposalId\":\"pa_eaac10b60c\",\"action\":\"PROMOTION_CREATE\",\"actionLabel\":\"新建优惠活动\",\"targetType\":\"PROMOTION\",\"targetId\":null,\"targetLabel\":\"新建优惠活动 · 全场满100减60（11.01-11.11）\",\"params\":{\"name\":\"全场满100减60（11.01-11.11）\",\"type\":\"FULL_REDUCTION\",\"threshold\":\"100\",\"discount\":\"60\",\"productId\":null,\"startTime\":\"2026-11-01T00:00:00\",\"endTime\":\"2026-11-11T23:59:59\",\"description\":\"全场通用满减活动：单笔满 100 元减 60 元，有效期 2026-11-01 至 2026-11-11。\"},\"precheck\":{\"ok\":true,\"checkedAt\":\"2026-10-27T20:13:57\",\"checks\":[{\"code\":\"PERMISSION\",\"ok\":true,\"message\":\"具备 promotion:manage\"},{\"code\":\"FIELD_VALID\",\"ok\":true,\"message\":\"字段校验通过（与创建接口同一份实现）\"},{\"code\":\"PRODUCT\",\"ok\":true,\"message\":\"全店通用\"},{\"code\":\"DUPLICATE\",\"ok\":true,\"message\":\"没有同源活动\"}]},\"risk\":{\"level\":\"HIGH\",\"reasons\":[\"优惠占门槛比例过高（60%），请确认\"],\"requiresSecondConfirm\":true,\"secondConfirmHint\":\"活动参数非常规，请核对后勾选「我已核对」再确认\",\"irreversible\":false,\"note\":\"活动一旦生效会立即影响下单价格\"},\"idempotencyKey\":\"promotion:fdb0fa060123e98223435076619a4aaf5ae74e0a\",\"expiresAt\":\"2026-10-27T20:44:03.0245634+08:00\",\"snapshot\":{\"name\":\"全场满100减60（11.01-11.11）\",\"type\":\"FULL_REDUCTION\",\"threshold\":\"100\",\"discount\":\"60\",\"productId\":null,\"startTime\":\"2026-11-01T00:00:00\",\"endTime\":\"2026-11-11T23:59:59\",\"duplicateCount\":0,\"duplicates\":[]},\"confirm\":{\"method\":\"POST\",\"path\":\"/api/admin/promotions\",\"body\":{\"name\":\"全场满100减60（11.01-11.11）\",\"type\":\"FULL_REDUCTION\",\"threshold\":\"100\",\"discount\":\"60\",\"startTime\":\"2026-11-01T00:00:00\",\"endTime\":\"2026-11-11T23:59:59\",\"status\":1,\"description\":\"全场通用满减活动：单笔满 100 元减 60 元，有效期 2026-11-01 至 2026-11-11。\"},\"requiresHeader\":{\"Content-Type\":\"application/json\",\"Idempotency-Key\":\"promotion:fdb0fa060123e98223435076619a4aaf5ae74e0a\"}},\"reject\":null,\"fields\":[{\"label\":\"活动名称\",\"value\":\"全场满100减60（11.01-11.11）\",\"tone\":\"normal\",\"mono\":false},{\"label\":\"优惠规则\",\"value\":\"满 ¥100 减 ¥60（相当于 4 折）\",\"tone\":\"normal\",\"mono\":false},{\"label\":\"适用范围\",\"value\":\"全店通用\",\"tone\":\"normal\",\"mono\":false},{\"label\":\"时间窗\",\"value\":\"2026-11-01 00:00 至 2026-11-11 23:59（10 天）\",\"tone\":\"normal\",\"mono\":false},{\"label\":\"生效影响\",\"value\":\"创建后立即影响下单实付与 GMV 口径，不可自动撤销\",\"tone\":\"normal\",\"mono\":false}]}');
INSERT INTO `admin_action_logs` VALUES (88, 'PROMOTION_CREATE', 1, NULL, '2026-10-29 14:37:53.019529', '2026-10-29 14:37:48.482572', 'CHAT', NULL, NULL, '2026-10-29 14:37:53.031113', '2026-10-29 15:07:48.470972', 'promotion:87d23e60f20b1e2b76710141364421981b175c9f', '0:0:0:0:0:0:0:1', 1, 'ADMIN', '577569cc2ffe46843c6756ef8f92d57e860bae64', '{\"name\":\"全场满200减50（11.11-11.30）\",\"type\":\"FULL_REDUCTION\",\"threshold\":\"200\",\"discount\":\"50\",\"productId\":null,\"startTime\":\"2026-11-11T00:00:00\",\"endTime\":\"2026-11-30T23:59:59\",\"description\":\"全店通用满200元减50元，活动期 2026-11-11 至 2026-11-30。\"}', '{\"ok\":true,\"checkedAt\":\"2026-10-29T14:37:46\",\"checks\":[{\"code\":\"PERMISSION\",\"ok\":true,\"message\":\"具备 promotion:manage\"},{\"code\":\"FIELD_VALID\",\"ok\":true,\"message\":\"字段校验通过（与创建接口同一份实现）\"},{\"code\":\"PRODUCT\",\"ok\":true,\"message\":\"全店通用\"},{\"code\":\"DUPLICATE\",\"ok\":true,\"message\":\"没有同源活动\"}]}', 'pa_0bc3c08838', '{\"createdAt\":\"2026-10-29T14:37:53.0285316\",\"description\":\"全店通用满200元减50元，活动期 2026-11-11 至 2026-11-30。\",\"discount\":50,\"endTime\":\"2026-11-30T23:59:59\",\"id\":13,\"imageUrl\":null,\"link\":null,\"name\":\"全场满200减50（11.11-11.30）\",\"productId\":null,\"startTime\":\"2026-11-11T00:00:00\",\"status\":1,\"threshold\":200,\"type\":\"FULL_REDUCTION\"}', '{\"name\":\"全场满200减50（11.11-11.30）\",\"type\":\"FULL_REDUCTION\",\"threshold\":\"200\",\"discount\":\"50\",\"productId\":null,\"startTime\":\"2026-11-11T00:00:00\",\"endTime\":\"2026-11-30T23:59:59\",\"duplicateCount\":0,\"duplicates\":[]}', 'EXECUTED', NULL, 'PROMOTION', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/156.0.0.0 Safari/537.36 Edg/156.0.0.0', '{\"proposalId\":\"pa_0bc3c08838\",\"action\":\"PROMOTION_CREATE\",\"actionLabel\":\"新建优惠活动\",\"targetType\":\"PROMOTION\",\"targetId\":null,\"targetLabel\":\"新建优惠活动 · 全场满200减50（11.11-11.30）\",\"params\":{\"name\":\"全场满200减50（11.11-11.30）\",\"type\":\"FULL_REDUCTION\",\"threshold\":\"200\",\"discount\":\"50\",\"productId\":null,\"startTime\":\"2026-11-11T00:00:00\",\"endTime\":\"2026-11-30T23:59:59\",\"description\":\"全店通用满200元减50元，活动期 2026-11-11 至 2026-11-30。\"},\"precheck\":{\"ok\":true,\"checkedAt\":\"2026-10-29T14:37:46\",\"checks\":[{\"code\":\"PERMISSION\",\"ok\":true,\"message\":\"具备 promotion:manage\"},{\"code\":\"FIELD_VALID\",\"ok\":true,\"message\":\"字段校验通过（与创建接口同一份实现）\"},{\"code\":\"PRODUCT\",\"ok\":true,\"message\":\"全店通用\"},{\"code\":\"DUPLICATE\",\"ok\":true,\"message\":\"没有同源活动\"}]},\"risk\":{\"level\":\"LOW\",\"reasons\":[],\"requiresSecondConfirm\":false,\"secondConfirmHint\":null,\"irreversible\":false,\"note\":\"活动一旦生效会立即影响下单价格\"},\"idempotencyKey\":\"promotion:87d23e60f20b1e2b76710141364421981b175c9f\",\"expiresAt\":\"2026-10-29T15:07:48.4709723+08:00\",\"snapshot\":{\"name\":\"全场满200减50（11.11-11.30）\",\"type\":\"FULL_REDUCTION\",\"threshold\":\"200\",\"discount\":\"50\",\"productId\":null,\"startTime\":\"2026-11-11T00:00:00\",\"endTime\":\"2026-11-30T23:59:59\",\"duplicateCount\":0,\"duplicates\":[]},\"confirm\":{\"method\":\"POST\",\"path\":\"/api/admin/promotions\",\"body\":{\"name\":\"全场满200减50（11.11-11.30）\",\"type\":\"FULL_REDUCTION\",\"threshold\":\"200\",\"discount\":\"50\",\"startTime\":\"2026-11-11T00:00:00\",\"endTime\":\"2026-11-30T23:59:59\",\"status\":1,\"description\":\"全店通用满200元减50元，活动期 2026-11-11 至 2026-11-30。\"},\"requiresHeader\":{\"Idempotency-Key\":\"promotion:87d23e60f20b1e2b76710141364421981b175c9f\",\"Content-Type\":\"application/json\"}},\"reject\":null,\"fields\":[{\"label\":\"活动名称\",\"value\":\"全场满200减50（11.11-11.30）\",\"tone\":\"normal\",\"mono\":false},{\"label\":\"优惠规则\",\"value\":\"满 ¥200 减 ¥50（相当于 7.5 折）\",\"tone\":\"normal\",\"mono\":false},{\"label\":\"适用范围\",\"value\":\"全店通用\",\"tone\":\"normal\",\"mono\":false},{\"label\":\"时间窗\",\"value\":\"2026-11-11 00:00 至 2026-11-30 23:59（19 天）\",\"tone\":\"normal\",\"mono\":false},{\"label\":\"生效影响\",\"value\":\"创建后立即影响下单实付与 GMV 口径，不可自动撤销\",\"tone\":\"normal\",\"mono\":false}]}');

-- ----------------------------
-- Table structure for ai_chat_messages
-- ----------------------------
DROP TABLE IF EXISTS `ai_chat_messages`;
CREATE TABLE `ai_chat_messages`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `created_at` datetime(6) NULL DEFAULT NULL,
  `role` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_acm_user_created`(`user_id` ASC, `created_at` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 872 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of ai_chat_messages
-- ----------------------------
INSERT INTO `ai_chat_messages` VALUES (11, '你好', '2026-09-14 17:17:55.087800', 'user', 2);
INSERT INTO `ai_chat_messages` VALUES (12, '你好呀，我是大润发线上商城的小发～今天是 2026 年 9 月 14 日星期一，有什么需要我帮忙的吗？\n\n不管是找商品、比参数、查物流，还是想安排配送时间、算算保质期或看看优惠券啥时候到期，跟我说一声就行 😊', '2026-09-14 17:17:55.117550', 'assistant', 2);
INSERT INTO `ai_chat_messages` VALUES (13, '我购物车里有什么', '2026-09-14 17:18:20.169336', 'user', 2);
INSERT INTO `ai_chat_messages` VALUES (14, '你购物车里现在有 1 件商品：\n\n- **东北大米** × 1，单价 59.90 元，小计 **59.90 元**\n\n合计一共 **59.90 元**。需要我帮你结算下单吗？还是想再看看别的？😊', '2026-09-14 17:18:20.172372', 'assistant', 2);
INSERT INTO `ai_chat_messages` VALUES (15, '帮我把购物车下单', '2026-09-14 17:18:49.686836', 'user', 2);
INSERT INTO `ai_chat_messages` VALUES (16, '已经帮你准备好下单预览啦，商品清单如下：\n\n- **东北大米** × 1，单价 59.90 元\n\n**合计：59.90 元**\n\n收货信息将默认使用你最近一次的地址（收货人：12，电话：13107993087，地址：1323123）。\n\n点下方卡片里的 **『确认下单』** 按钮才会真正创建订单哦，这一步我不会替你自动扣款～ 确认没问题的话，直接点按钮就行 😊', '2026-09-14 17:18:49.694455', 'assistant', 2);
INSERT INTO `ai_chat_messages` VALUES (17, '帮我把购物车下单', '2026-09-14 18:01:05.213507', 'user', 2);
INSERT INTO `ai_chat_messages` VALUES (18, '我帮你重新读取了一下，购物车里现在有 **2 件商品**（比刚才多了一件基围虾）：\n\n- **基围虾** × 1，单价 39.90 元\n- **东北大米** × 1，单价 59.90 元\n\n**合计：99.80 元**\n\n收货信息将默认使用你最近一次的地址（收货人：12，电话：13107993087，地址：1323123）。\n\n点下方卡片里的 **『确认下单』** 按钮才会真正创建订单哦，这一步我不会替你自动扣款～ 确认清单和金额没问题的话，直接点按钮就行 😊', '2026-09-14 18:01:05.218107', 'assistant', 2);
INSERT INTO `ai_chat_messages` VALUES (19, 'place', '2026-09-14 19:44:23.133096', 'user', 2);
INSERT INTO `ai_chat_messages` VALUES (20, '已经帮你准备好下单预览啦，这次购物车里的商品是：\n\n- **曲奇饼干** × 1，单价 19.90 元\n- **赣南脐橙** × 2，单价 6.80 元，小计 13.60 元\n\n**合计：33.50 元**\n\n收货信息将默认使用你最近一次的地址（收货人：12，电话：13107993087，地址：1323123）。\n\n点下方卡片里的 **『确认下单』** 按钮才会真正创建订单哦，这一步我不会替你自动扣款～ 确认没问题的话，直接点按钮就行 😊', '2026-09-14 19:44:23.172977', 'assistant', 2);
INSERT INTO `ai_chat_messages` VALUES (21, '帮我把购物车下单', '2026-09-15 19:23:29.218458', 'user', 2);
INSERT INTO `ai_chat_messages` VALUES (22, '已经帮你准备好下单预览啦，这次购物车里的商品是：\n\n- **曲奇饼干** × 1，单价 19.90 元\n\n**合计：19.90 元**\n\n收货信息将默认使用你最近一次的地址（收货人：12，电话：13800138000，地址：北京市朝阳路 1 号）。\n\n点下方卡片里的 **『确认下单』** 按钮才会真正创建订单哦，这一步我不会替你自动扣款～ 确认没问题的话，直接点按钮就行 😊', '2026-09-15 19:23:29.221986', 'assistant', 2);
INSERT INTO `ai_chat_messages` VALUES (55, '我购物车里有什么', '2026-09-18 10:32:25.973793', 'user', 2);
INSERT INTO `ai_chat_messages` VALUES (56, '你购物车里现在有 **2 件商品**：\n\n- **蓝月亮洗衣液** × 1，单价 49.90 元\n- **东北大米** × 1，单价 59.90 元\n\n**合计：109.80 元**\n\n需要我帮你结算下单吗？还是想再看看别的？😊', '2026-09-18 10:32:25.981065', 'assistant', 2);
INSERT INTO `ai_chat_messages` VALUES (63, '我想买橘子', '2026-10-06 19:52:49.050933', 'user', 2);
INSERT INTO `ai_chat_messages` VALUES (64, '目前「橘子」这个词没搜到在售的商品，不过有一款很接近的橙子可以看看：\n\n- **赣南脐橙** —— 皮薄肉嫩、汁水丰盈，**6.80 元/斤**（原价 8.80 元），库存充足，销量不错 😋\n\n如果你就想要这种酸甜多汁的橙子，我可以帮你加进购物车；要是你坚持要橘子（比如砂糖橘、沃柑这类），也可以告诉我，我再帮你留意看看～', '2026-10-06 19:52:49.079269', 'assistant', 2);
INSERT INTO `ai_chat_messages` VALUES (149, '查一下我的订单物流', '2026-10-10 22:27:22.461692', 'user', 5);
INSERT INTO `ai_chat_messages` VALUES (150, '今天是 2026 年 10 月 10 日，星期六——记着这个日期，后面帮您安排配送时间、算保质期或者提醒优惠券截止日都会更方便。\n\n关于物流，我这边需要您提供一下订单号才能查（在 App「我的订单」里每张卡片上都能看到，格式类似 DR20260911xxxx）。您把单号发我，我立刻帮您看快递到哪了；如果手头没有单号，也可以告诉我大概的下单时间或买的什么东西，我帮您一起找找。', '2026-10-10 22:27:24.282128', 'assistant', 5);
INSERT INTO `ai_chat_messages` VALUES (151, '我的订单到哪了', '2026-10-10 22:29:07.539961', 'user', 5);
INSERT INTO `ai_chat_messages` VALUES (152, '您把订单号发我就行（在 App「我的订单」里，每笔订单卡片的标题上就有，格式类似 DR20260911xxxx），我马上帮您查快递到哪一步了。\n\n如果一时找不到单号，也可以告诉我大概的下单日期或者买了些什么，我陪您一起定位到具体那笔订单，再看物流进度。', '2026-10-10 22:29:09.248533', 'assistant', 5);
INSERT INTO `ai_chat_messages` VALUES (153, '订单 DR20260915223941978 到哪了？', '2026-10-10 22:30:40.653619', 'user', 5);
INSERT INTO `ai_chat_messages` VALUES (154, '帮您查到了，这笔订单的情况是这样：\n\n- **订单号**：DR20260915223941978\n- **下单/支付时间**：2026-09-15 22:39\n- **当前状态**：已发货，待收货\n- **承运商**：圆通速递，运单号 YT9900112233\n\n不过提醒您一句：这笔单子 9 月 15 日就发出去了，到今天已经过去二十多天，物流状态还停在「已发货」，一直没更新为已送达，这个时长不太正常。建议您：\n\n1. 先用运单号 **YT9900112233** 在圆通官网或小程序上查一下最新轨迹，看是不是卡在中转站了；\n2. 如果轨迹也停在发货那一步，可以直接在 App 订单详情里发起「物流异常」反馈或联系客服，让他们帮您催一下；\n3. 若确认包裹已丢失，属于平台/快递责任，可以申请退款或补发，这种原因产生的运费由平台承担。\n\n需要我帮您看看购物车里还有哪些东西要补一单吗？或者您有别的问题随时说。', '2026-10-10 22:30:43.515898', 'assistant', 5);
INSERT INTO `ai_chat_messages` VALUES (155, '用一句话告诉我订单 DR20260915223941978 的物流状态', '2026-10-10 22:33:18.404055', 'user', 5);
INSERT INTO `ai_chat_messages` VALUES (156, '订单 DR20260915223941978 已由圆通速递发出（运单号 YT9900112233），目前处于「已发货、待收货」状态。', '2026-10-10 22:33:19.175972', 'assistant', 5);
INSERT INTO `ai_chat_messages` VALUES (277, '把赣南脐橙补货 100 件', '2026-10-22 15:22:40.090094', 'user', 4);
INSERT INTO `ai_chat_messages` VALUES (278, '今天是 2026 年 10 月 22 日（星期四），以下为本次补货预检结果。\n\n**商品匹配（唯一命中）**\n- 赣南脐橙（商品 ID 2）｜售价 6.80 元｜当前库存 798 件｜累计销量 2\n\n**补货预检：未通过 ❌**\n- 原因：缺少「编辑商品」权限（PERM_DENIED），补货属于「编辑商品」类操作，当前账号无权执行\n- 因此未生成确认卡片，库存**未做任何变更**\n\n**下一步建议**\n1. 请联系拥有「编辑商品」权限的运营同事，由其在后台执行「赣南脐橙 +100 件」入库；\n2. 或者请管理员为你的账号开通该权限后，我再帮你重新走一次预检出卡片。\n\n另外提示：该单品现有库存 798 件，本身并不属于紧缺状态，如需评估是否真的需要补 100 件，我也可以帮你查一下库存预警清单。', '2026-10-22 15:22:44.059660', 'assistant', 4);
INSERT INTO `ai_chat_messages` VALUES (279, '把退款单 15 通过一下', '2026-10-22 15:22:49.900566', 'user', 3);
INSERT INTO `ai_chat_messages` VALUES (280, '今天是 2026年10月22日 星期四，我可以据此安排活动日程、记录售后与工单时间、追踪任务进度。下面先为您准备这笔退款审批。', '2026-10-22 15:22:51.449451', 'assistant', 3);
INSERT INTO `ai_chat_messages` VALUES (281, '已准备好「同意退款」提案，**尚未执行**，请点击卡片确认后才会真正退款。\n\n**待确认内容**\n- 退款单：#15（订单 DR20260916112719998）\n- 退款金额：¥19.90\n- 买家：测试用户\n- 退款原因：QA独立验证-退款审批链路\n- 商品件数：1 件\n\n**预检结果：全部通过**\n- 权限校验 ✓（具备售后审批权限）\n- 退款单待处理 ✓ ／ 订单处于「退款中」✓\n- 审批人非买家本人 ✓（不会触发自审拦截）\n\n**确认后会发生什么（不可撤销）**\n- 退款单：待处理 → 已同意；订单：退款中 → 已退款\n- 自动回补库存、返还该单使用的优惠券、支付流水置为 REFUNDED\n- 到账时间：微信/支付宝一般 1–3 个工作日，余额支付即时到账\n\n如信息无误，请点击卡片确认；如需修改决策（例如改为拒绝并填写理由），告诉我即可重新准备。', '2026-10-22 15:22:53.155635', 'assistant', 3);
INSERT INTO `ai_chat_messages` VALUES (282, '把退款单 16 同意退款', '2026-10-22 15:23:28.907690', 'user', 4);
INSERT INTO `ai_chat_messages` VALUES (283, '**退款单 #16 审批预检：未通过 ❌（未生成确认卡片）**\n\n| 检查项 | 结果 |\n|---|---|\n| 权限（order:view 或 cs:manage） | ✅ 具备 |\n| 参数完整性（决策=同意退款） | ✅ 通过 |\n| 退款单定位 #16 | ✅ 已定位 |\n| 退款单状态：待处理 | ✅ 通过 |\n| 订单状态：退款中 | ✅ 通过 |\n| **审批人身份校验** | ❌ **审批人就是买家本人，不能自审** |\n\n**失败原因**：退款单 #16 的买家就是你本人，按平台规则为了保证公正，系统会直接拦截“自己审批自己的退款”，所以这一单我无法替你走同意退款的流程。\n\n**下一步建议**\n1. 请另一位具备售后权限的同事（或具备 `cs:manage` 的管理员）登录后台，对该退款单点击同意退款；\n2. 这次操作**没有产生任何变更**：钱未退、库存未回补、优惠券未返还，退款单仍是待处理状态。\n\n补充一下流程，方便同事接手：同意退款后系统会自动回补商品库存、把该订单用掉的优惠券返还给买家；款项按原支付渠道退回，微信/支付宝一般 1–3 个工作日到账，余额支付即', '2026-10-22 15:23:32.311775', 'assistant', 4);
INSERT INTO `ai_chat_messages` VALUES (284, '订单 DR20260916112224719 直接退款吧，别走申请流程了', '2026-10-22 15:23:39.259301', 'user', 3);
INSERT INTO `ai_chat_messages` VALUES (285, '这笔单子当前没法直接退——**后台不能代客发起退款**，只能审批买家已提交的申请。\n\n**查询结果**\n- 订单号：DR20260916112224719\n- 预检：未通过（TARGET_NOT_FOUND）——该订单**没有提交过退款申请**\n- 权限本身是通过的，卡在\"没有可审批的申请单\"这一环\n\n**为什么绕不过去**\n退款是两步流程，缺第一步就走不了第二步：\n1. 买家在 App「我的订单」里提交退款申请 → 订单变为「退款中（REFUNDING）」，此时钱还没退\n2. 商家/售后在后台审批「同意退款」→ 才真正退钱、回补库存、返还优惠券\n\n也就是说，无论后台还是对话，都无法凭空生成一张退款申请单；必须先有买家那一步。\n\n**建议下一步**\n- 让买家在 App 内对 DR20260916112224719 提交退款申请（可在\"我的订单\"卡片里操作，选退款原因）\n- 申请一提交，订单进入「退款中」，您回来告诉我订单号，我立刻准备「同意退款」卡片，您点确认即可（同一订单多次申请时，以 id 最大的最新一次为准）\n- 如果买家不方便操作，可引导其联系在线客服走售后通道，同样需要一笔', '2026-10-22 15:23:44.928443', 'assistant', 3);
INSERT INTO `ai_chat_messages` VALUES (296, '给赣南脐橙搞个满100减10的活动', '2026-10-22 15:28:40.272051', 'user', 4);
INSERT INTO `ai_chat_messages` VALUES (297, '**活动要素先确认一下（商品和规则已明确）**\n- 活动类型：满减（FULL_REDUCTION）\n- 优惠力度：满 100 元减 10 元\n- 适用范围：指定单个商品，即赣南脐橙（商品 ID 2，售价 6.80 元）\n- 生效时间：如果你没有特别指定，就默认“立即开始、不设结束时间”；如果需要指定起止时间，请告诉我\n\n**只差活动名称，下面有两个候选，请你选择其中一个，也可以直接把你想要的名称给我：**\n1. 赣南脐橙满100减10\n2. 脐橙囤货节·满100减10\n\n收到名称后，我会生成确认卡片；你点击卡片确认后，活动才会真正创建；在那之前，它不会生效，也不会影响下单价格。\n\n（提示：本期不支持类目级活动，只能设为全店通用或指定单个商品；本次按你指定的赣南脐橙单品来设置。）', '2026-10-22 15:28:43.589900', 'assistant', 4);
INSERT INTO `ai_chat_messages` VALUES (298, '活动名叫脐橙满减，全店满100减10，立即开始，现在就建', '2026-10-22 15:28:51.278226', 'user', 4);
INSERT INTO `ai_chat_messages` VALUES (299, '**活动创建预检：未通过 ❌（未生成确认卡片）**\n\n这次按你的要求核对了一下准备信息：\n- 活动名称：脐橙满减\n- 类型：满减（满 100 元减 10 元）\n- 适用范围：全店通用（不限商品）\n- 开始时间：立即开始，结束时间不限\n\n**失败原因**：缺少「促销管理」权限（PERM_DENIED）。创建活动需要这项权限，当前账号没有。\n\n**下一步建议**\n1. 请具备「促销管理」权限的运营同事或管理员在后台创建该活动，参数可直接使用上面的内容；\n2. 或者请管理员为你的账号开通「促销管理」权限后，我再重新走一次预检并生成确认卡片。\n\n说明：本次**没有创建任何活动**，店铺的下单价格、满减规则均未发生变化。', '2026-10-22 15:28:53.948790', 'assistant', 4);
INSERT INTO `ai_chat_messages` VALUES (300, '把退款单 18 通过一下', '2026-10-22 15:38:54.494152', 'user', 3);
INSERT INTO `ai_chat_messages` VALUES (301, '我已准备好「同意退款」提案，但**尚未执行**，请您点击卡片确认后才会真正退款。\n\n**待确认内容**\n- 退款单：#18（订单 DR20260916112135320）\n- 退款金额：¥84.90\n- 买家：测试用户\n- 退款原因：QA2回归-退款同意\n- 商品件数：1 件\n\n**预检结果：全部通过**\n- 权限校验 ✓（具备 order:view 或 cs:manage）\n- 参数完整 ✓（决策=同意）\n- 退款单 #18 已定位 ✓，且状态为「待处理」✓\n- 对应订单处于「退款中」✓\n- 审批人不是买家本人 ✓（未触发自审拦截）\n\n**确认后会发生什么（不可撤销）**\n- 退款单：待处理 → 已同意；订单：退款中 → 已退款\n- 自动回补库存、返还该单使用的优惠券、支付流水置为 REFUNDED\n- 到账时间：微信、支付宝一般 1–3 个工作日，平台余额支付即时到账；超过 5 个工作日未收到可联系在线客服查退款流水号\n\n如信息无误，请点击卡片确认；若改为「拒绝退款」并需填写理由，或者发现金额不对，请告诉我，我会重新准备。', '2026-10-22 15:38:57.850951', 'assistant', 3);
INSERT INTO `ai_chat_messages` VALUES (302, '把赣南脐橙补货 100 件', '2026-10-22 15:39:16.917584', 'user', 4);
INSERT INTO `ai_chat_messages` VALUES (303, '**这次不再重复提交预检**：上一轮「赣南脐橙 +100 件」的预检已因缺少「编辑商品」权限被拦截，同一操作再跑一次仍会得到相同结果，因此我没有再生成确认卡片，库存**保持 0 变更**。\n\n**当前商品状态（刚查的实时库存）**\n- 赣南脐橙（商品 ID 2）｜售价 6.80 元｜库存 **798 件**｜累计销量 2\n\n**需要你做一步**\n1. 请管理员为你的账号开通「编辑商品」权限，开通后回我一句，我立刻重新走预检并生成确认卡片；\n2. 或直接请有该权限的同事在后台执行「赣南脐橙 增加 100 件」。\n\n**也可以告诉我**：如果你其实是想「补到 100 件」或补别的数量，我改参数就行；不过权限这道门槛还是一样，需要先解决。', '2026-10-22 15:39:22.132324', 'assistant', 4);
INSERT INTO `ai_chat_messages` VALUES (304, '给东北大米补 30 件', '2026-10-22 15:39:34.405513', 'user', 4);
INSERT INTO `ai_chat_messages` VALUES (305, '**补货预检：未通过 ❌（未生成确认卡片）**\n\n**商品定位（唯一命中）**\n- 东北大米（商品 ID 7）｜售价 59.90 元｜当前库存 **595 件**｜累计销量 5\n\n**预检结果**\n- 补货方式：在原库存基础上增加（DELTA）+30 件，即由 595 件变为 625 件\n- 检查项「权限」：❌ 缺少「编辑商品」权限（PERM_DENIED）\n- 校验时间：2026-10-22 15:39\n- 库存**未做任何变更**，仍为 595 件\n\n**下一步建议**\n1. 请管理员给你的账号开通「编辑商品」权限；开通后回我一句，我马上重新预检出卡片；\n2. 或请具备该权限的运营同事在后台执行「东北大米 增加 30 件」。\n\n顺便说一句：这个权限门槛和上次赣南脐橙是同一个（商品编辑类），后续所有补货操作都会卡在这里；建议一次把权限开通好，后面就能顺畅执行。', '2026-10-22 15:39:40.807970', 'assistant', 4);
INSERT INTO `ai_chat_messages` VALUES (867, '最近 5 单待发货有哪些？', '2026-11-01 15:44:42.701342', 'user', 1);
INSERT INTO `ai_chat_messages` VALUES (868, '今天是 2026年11月1日（星期日），这个日期可以用来安排促销日程、记录售后工单时间、追踪发货与对账节点。下面先查一下最近的待发货订单：', '2026-11-01 15:44:43.959863', 'assistant', 1);
INSERT INTO `ai_chat_messages` VALUES (869, '**待发货订单（最近 5 单，共 5 单）**\n\n| 订单号 | 用户ID | 金额 | 状态 | 下单时间 |\n|---|---|---|---|---|\n| DR20260918103315261 | 2 | ¥94.80 | 待发货 | 2026-09-18 10:33 |\n| DR20260916195351194 | 2 | ¥109.80 | 待发货 | 2026-09-16 19:53 |\n| DR20260916121347646 | 6 | ¥4.50 | 待发货 | 2026-09-16 12:13 |\n| DR20260916121335493 | 6 | ¥9.00 | 待发货 | 2026-09-16 12:13 |\n| DR20260916121314859 | 6 | ¥19.90 | 待发货 | 2026-09-16 12:13 |\n\n⚠️ 需要重点关注：\n- 这 5 单的下单时间都在 **9 月 16—18 日**，距今天（11 月 1 日）已远超 48 小时发货时效承诺，属于**发货超时**订单，建议优先处理并主动联系用户说明。\n- 用户 6 在 9', '2026-11-01 15:44:47.272373', 'assistant', 1);
INSERT INTO `ai_chat_messages` VALUES (870, '帮我新建一个满 100 减 60 的全场通用促销活动，有效期 2026-11-01到 2026-11-11', '2026-11-01 15:50:04.570521', 'user', 1);
INSERT INTO `ai_chat_messages` VALUES (871, '您的需求我记下了：**全场通用**、**满 100 减 60**、有效期 **2026-11-01 至 2026-11-11**。\n\n还差一个必填项：**活动名称**（我不能替您默认采用，需您来定）。给您两个候选：\n\n1. `双11全场满100减60`\n2. `双11狂欢节·满100减60`\n\n您选一个，或者直接给我想要的名称，我就把确认卡片准备好。\n\n另外两点请您一并确认：\n- **生效时间**：开始时间我按「2026-11-01 00:00:00」、结束时间按「2026-11-11 23:59:59」处理，需要精确到几点请告诉我。\n- **力度提醒**：满 100 减 60 相当于最高 6 折优惠，属较大力度，且这是**新建活动**、会按您填的规则在全店生效，建议核对后再点确认。\n\n（提醒：卡片生成后需您在前端亲手点「确认执行」才会真正创建，此时尚未创建任何活动。）', '2026-11-01 15:50:29.353712', 'assistant', 1);

-- ----------------------------
-- Table structure for ai_memories
-- ----------------------------
DROP TABLE IF EXISTS `ai_memories`;
CREATE TABLE `ai_memories`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `mem_key` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `mem_value` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `updated_at` datetime(6) NULL DEFAULT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `idx_am_user_key`(`user_id` ASC, `mem_key` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of ai_memories
-- ----------------------------

-- ----------------------------
-- Table structure for balance_transactions
-- ----------------------------
DROP TABLE IF EXISTS `balance_transactions`;
CREATE TABLE `balance_transactions`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `amount` decimal(12, 2) NOT NULL,
  `balance_after` decimal(12, 2) NOT NULL,
  `biz_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `created_at` datetime(6) NULL DEFAULT NULL,
  `order_id` bigint NULL DEFAULT NULL,
  `remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `type` enum('INIT','PAY','RECHARGE','REFUND') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `UK4hr04jec8k5fydgpqkpvhaffa`(`biz_no` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 40 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of balance_transactions
-- ----------------------------
INSERT INTO `balance_transactions` VALUES (1, 1000.00, 1000.00, 'INIT-5', '2026-09-16 11:03:40.553282', NULL, '新用户初始余额', 'INIT', 5);
INSERT INTO `balance_transactions` VALUES (2, 500.00, 1500.00, 'RECHARGE-selftest-1', '2026-09-16 11:05:31.493064', NULL, '账户充值（模拟）', 'RECHARGE', 5);
INSERT INTO `balance_transactions` VALUES (3, -66.00, 1434.00, 'ORDER-32-PAY', '2026-09-16 11:05:54.396059', 32, '订单 32 余额支付', 'PAY', 5);
INSERT INTO `balance_transactions` VALUES (4, -88.00, 1346.00, 'ORDER-30-PAY', '2026-09-16 11:05:54.727131', 30, '订单 30 余额支付', 'PAY', 5);
INSERT INTO `balance_transactions` VALUES (5, 66.00, 1412.00, 'ORDER-32-REFUND', '2026-09-16 11:06:12.657902', 32, '订单退款返现到余额', 'REFUND', 5);
INSERT INTO `balance_transactions` VALUES (6, 88.00, 1500.00, 'ORDER-30-REFUND', '2026-09-16 11:06:21.070903', 30, '订单退款返现到余额', 'REFUND', 5);
INSERT INTO `balance_transactions` VALUES (7, 1.00, 1501.00, 'RECHARGE-fupdtest-1', '2026-09-16 11:15:40.415378', NULL, '账户充值（模拟）', 'RECHARGE', 5);
INSERT INTO `balance_transactions` VALUES (8, -45.00, 1456.00, 'ORDER-34-PAY', '2026-09-16 11:16:29.472708', 34, '订单 34 余额支付', 'PAY', 5);
INSERT INTO `balance_transactions` VALUES (9, 45.00, 1501.00, 'ORDER-34-REFUND', '2026-09-16 11:16:30.681002', 34, '订单退款返现到余额', 'REFUND', 5);
INSERT INTO `balance_transactions` VALUES (10, 2.00, 1503.00, 'RECHARGE-fupdtest-2', '2026-09-16 11:17:17.844790', NULL, '账户充值（模拟）', 'RECHARGE', 5);
INSERT INTO `balance_transactions` VALUES (11, 100.00, 1603.00, 'RECHARGE-qa-r1-20260916', '2026-09-16 11:21:02.976900', NULL, '账户充值（模拟）', 'RECHARGE', 5);
INSERT INTO `balance_transactions` VALUES (12, -84.90, 1518.10, 'ORDER-35-PAY', '2026-09-16 11:21:21.254979', 35, '订单 35 余额支付', 'PAY', 5);
INSERT INTO `balance_transactions` VALUES (13, 84.90, 1603.00, 'ORDER-35-REFUND', '2026-09-16 11:21:53.481938', 35, '订单退款返现到余额', 'REFUND', 5);
INSERT INTO `balance_transactions` VALUES (14, -84.90, 1518.10, 'ORDER-39-PAY', '2026-09-16 11:22:00.698847', 39, '订单 39 余额支付', 'PAY', 5);
INSERT INTO `balance_transactions` VALUES (15, 84.90, 1603.00, 'ORDER-39-REFUND', '2026-09-16 11:22:01.500829', 39, '订单退款返现到余额', 'REFUND', 5);
INSERT INTO `balance_transactions` VALUES (16, -84.90, 1518.10, 'ORDER-40-PAY', '2026-09-16 11:22:09.022180', 40, '订单 40 余额支付', 'PAY', 5);
INSERT INTO `balance_transactions` VALUES (17, 84.90, 1603.00, 'ORDER-40-REFUND', '2026-09-16 11:22:10.229058', 40, '订单退款返现到余额', 'REFUND', 5);
INSERT INTO `balance_transactions` VALUES (18, -879.00, 724.00, 'ORDER-41-PAY', '2026-09-16 11:22:32.320447', 41, '订单 41 余额支付', 'PAY', 5);
INSERT INTO `balance_transactions` VALUES (19, 100.00, 824.00, 'RECHARGE-qa-conc-r1', '2026-09-16 11:22:39.902367', NULL, '账户充值（模拟）', 'RECHARGE', 5);
INSERT INTO `balance_transactions` VALUES (20, 100.00, 924.00, 'RECHARGE-54548799-340e-4b28-8134-8a9cd3435d74', '2026-09-16 11:26:03.440916', NULL, '账户充值（模拟）', 'RECHARGE', 5);
INSERT INTO `balance_transactions` VALUES (21, -19.90, 904.10, 'ORDER-43-PAY', '2026-09-16 11:27:19.966595', 43, '订单 43 余额支付', 'PAY', 5);
INSERT INTO `balance_transactions` VALUES (22, 10.00, 10.00, 'RECHARGE-c5515919-c40c-46bf-9fa7-5ce42938795a', '2026-09-16 11:30:06.978516', NULL, '账户充值（模拟）', 'RECHARGE', 6);
INSERT INTO `balance_transactions` VALUES (23, 1.00, 11.00, 'RECHARGE-186048a6-002d-428f-ada8-978e96a2d51f', '2026-09-16 11:33:54.395987', NULL, '账户充值（模拟）', 'RECHARGE', 6);
INSERT INTO `balance_transactions` VALUES (24, 879.00, 1783.10, 'ORDER-41-REFUND', '2026-09-16 11:41:37.624657', 41, '订单退款返现到余额', 'REFUND', 5);
INSERT INTO `balance_transactions` VALUES (25, -84.90, 1698.20, 'ORDER-44-PAY', '2026-09-16 11:46:50.390437', 44, '订单 44 余额支付', 'PAY', 5);
INSERT INTO `balance_transactions` VALUES (26, 84.90, 1783.10, 'ORDER-44-REFUND', '2026-09-16 11:46:51.123713', 44, '订单退款返现到余额', 'REFUND', 5);
INSERT INTO `balance_transactions` VALUES (27, -879.00, 904.10, 'ORDER-42-PAY', '2026-09-16 12:00:47.931680', 42, '订单 42 余额支付', 'PAY', 5);
INSERT INTO `balance_transactions` VALUES (28, -9.00, 895.10, 'ORDER-45-PAY', '2026-09-16 12:11:59.941026', 45, '订单 45 余额支付', 'PAY', 5);
INSERT INTO `balance_transactions` VALUES (29, 9.00, 904.10, 'ORDER-45-REFUND', '2026-09-16 12:12:14.526914', 45, '订单退款返现到余额', 'REFUND', 5);
INSERT INTO `balance_transactions` VALUES (30, 25.00, 36.00, 'RECHARGE-QAIDEM-1789531982', '2026-09-16 12:13:03.188867', NULL, '账户充值（模拟）', 'RECHARGE', 6);
INSERT INTO `balance_transactions` VALUES (31, -19.90, 16.10, 'ORDER-48-PAY', '2026-09-16 12:13:26.491779', 48, '订单 48 余额支付', 'PAY', 6);
INSERT INTO `balance_transactions` VALUES (32, -9.00, 7.10, 'ORDER-50-PAY', '2026-09-16 12:13:38.529119', 50, '订单 50 余额支付', 'PAY', 6);
INSERT INTO `balance_transactions` VALUES (33, -4.50, 2.60, 'ORDER-51-PAY', '2026-09-16 12:13:49.889684', 51, '订单 51 余额支付', 'PAY', 6);
INSERT INTO `balance_transactions` VALUES (34, -9.00, 895.10, 'ORDER-52-PAY', '2026-09-16 12:45:18.513386', 52, '订单 52 余额支付', 'PAY', 5);
INSERT INTO `balance_transactions` VALUES (35, 9.00, 904.10, 'ORDER-52-REFUND', '2026-09-16 12:45:23.823631', 52, '订单退款返现到余额', 'REFUND', 5);
INSERT INTO `balance_transactions` VALUES (36, 1000.00, 1000.00, 'RECHARGE-3c6fb7c7-6f21-44b0-af87-d1947715fbec', '2026-09-16 12:57:05.948374', NULL, '账户充值（模拟）', 'RECHARGE', 2);
INSERT INTO `balance_transactions` VALUES (37, -109.80, 890.20, 'ORDER-54-PAY', '2026-09-16 19:53:51.184268', 54, '订单 54 余额支付', 'PAY', 2);
INSERT INTO `balance_transactions` VALUES (38, -94.80, 795.40, 'ORDER-58-PAY', '2026-09-18 10:33:15.496363', 58, '订单 58 余额支付', 'PAY', 2);
INSERT INTO `balance_transactions` VALUES (39, 19.90, 924.00, 'ORDER-43-REFUND', '2026-10-22 15:23:03.174412', 43, '订单退款返现到余额', 'REFUND', 5);

-- ----------------------------
-- Table structure for cart_items
-- ----------------------------
DROP TABLE IF EXISTS `cart_items`;
CREATE TABLE `cart_items`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NULL DEFAULT NULL,
  `price` decimal(10, 2) NOT NULL,
  `product_id` bigint NOT NULL,
  `product_image` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `product_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `quantity` int NOT NULL,
  `selected` bit(1) NOT NULL,
  `updated_at` datetime(6) NULL DEFAULT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `UK1vhvont0fdtramle6nmghntj7`(`user_id` ASC, `product_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 28 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of cart_items
-- ----------------------------
INSERT INTO `cart_items` VALUES (17, '2026-09-15 20:31:00.367110', 89.90, 8, 'p030', '花生油', 5, b'1', '2026-10-10 19:48:41.224792', 5);
INSERT INTO `cart_items` VALUES (20, '2026-09-16 11:28:51.551655', 6.80, 2, 'p003', '赣南脐橙', 1, b'1', '2026-09-16 11:28:51.551655', 6);
INSERT INTO `cart_items` VALUES (25, '2026-09-18 12:38:07.303565', 49.90, 14, 'p073', '蓝月亮洗衣液', 1, b'1', '2026-09-18 12:38:07.303565', 5);
INSERT INTO `cart_items` VALUES (26, '2026-10-10 19:48:41.543030', 59.90, 7, 'p029', '东北大米', 3, b'1', '2026-10-10 19:48:41.543030', 5);
INSERT INTO `cart_items` VALUES (27, '2026-10-10 19:48:41.873329', 45.90, 9, 'p015', '纯牛奶', 2, b'1', '2026-10-10 19:48:41.873329', 5);

-- ----------------------------
-- Table structure for categories
-- ----------------------------
DROP TABLE IF EXISTS `categories`;
CREATE TABLE `categories`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NULL DEFAULT NULL,
  `icon` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `name` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `parent_id` bigint NOT NULL,
  `sort_order` int NOT NULL,
  `status` int NOT NULL,
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 27 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of categories
-- ----------------------------
INSERT INTO `categories` VALUES (1, '2026-09-07 16:42:59.602848', NULL, '新鲜果蔬', 0, 1, 1);
INSERT INTO `categories` VALUES (2, '2026-09-07 16:42:59.604885', NULL, '肉禽蛋品', 0, 2, 1);
INSERT INTO `categories` VALUES (3, '2026-09-07 16:42:59.605885', NULL, '海鲜水产', 0, 3, 1);
INSERT INTO `categories` VALUES (4, '2026-09-07 16:42:59.605885', NULL, '粮油调味', 0, 4, 1);
INSERT INTO `categories` VALUES (5, '2026-09-07 16:42:59.606886', NULL, '乳品烘焙', 0, 5, 1);
INSERT INTO `categories` VALUES (6, '2026-09-07 16:42:59.607887', NULL, '休闲零食', 0, 6, 1);
INSERT INTO `categories` VALUES (7, '2026-09-07 16:42:59.607887', NULL, '酒水饮料', 0, 7, 1);
INSERT INTO `categories` VALUES (8, '2026-09-07 16:42:59.607887', NULL, '个护清洁', 0, 8, 1);
INSERT INTO `categories` VALUES (9, '2026-09-07 16:42:59.609389', NULL, '水果类', 1, 1, 1);
INSERT INTO `categories` VALUES (10, '2026-09-07 16:42:59.610471', NULL, '叶菜类', 1, 2, 1);
INSERT INTO `categories` VALUES (11, '2026-09-07 16:42:59.610471', NULL, '猪肉', 2, 1, 1);
INSERT INTO `categories` VALUES (12, '2026-09-07 16:42:59.611489', NULL, '禽类', 2, 2, 1);
INSERT INTO `categories` VALUES (13, '2026-09-07 16:42:59.611489', NULL, '鱼类', 3, 1, 1);
INSERT INTO `categories` VALUES (14, '2026-09-07 16:42:59.612506', NULL, '虾蟹', 3, 2, 1);
INSERT INTO `categories` VALUES (15, '2026-09-07 16:42:59.613524', NULL, '米面', 4, 1, 1);
INSERT INTO `categories` VALUES (16, '2026-09-07 16:42:59.613524', NULL, '食用油', 4, 2, 1);
INSERT INTO `categories` VALUES (17, '2026-09-07 16:42:59.614555', NULL, '牛奶', 5, 1, 1);
INSERT INTO `categories` VALUES (18, '2026-09-07 16:42:59.614555', NULL, '面包糕点', 5, 2, 1);
INSERT INTO `categories` VALUES (19, '2026-09-07 16:42:59.615667', NULL, '饼干', 6, 1, 1);
INSERT INTO `categories` VALUES (20, '2026-09-07 16:42:59.616691', NULL, '坚果炒货', 6, 2, 1);
INSERT INTO `categories` VALUES (21, '2026-09-07 16:42:59.617707', NULL, '饮用水', 7, 1, 1);
INSERT INTO `categories` VALUES (22, '2026-09-07 16:42:59.617707', NULL, '碳酸饮料', 7, 2, 1);
INSERT INTO `categories` VALUES (23, '2026-09-07 16:42:59.618726', NULL, '纸品', 8, 1, 1);
INSERT INTO `categories` VALUES (24, '2026-09-07 16:42:59.618726', NULL, '洗护用品', 8, 2, 1);
INSERT INTO `categories` VALUES (26, '2026-09-16 09:46:34.994640', NULL, '贝类', 3, 3, 1);

-- ----------------------------
-- Table structure for cs_conversations
-- ----------------------------
DROP TABLE IF EXISTS `cs_conversations`;
CREATE TABLE `cs_conversations`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NULL DEFAULT NULL,
  `last_message` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `last_message_at` datetime(6) NULL DEFAULT NULL,
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `unread_for_admin` int NOT NULL,
  `updated_at` datetime(6) NULL DEFAULT NULL,
  `user_id` bigint NOT NULL,
  `user_name` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `UK61cl4pqteeg19aub5mi000xsj`(`user_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 4 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of cs_conversations
-- ----------------------------
INSERT INTO `cs_conversations` VALUES (1, '2026-09-15 21:27:20.843905', 'We are children your order， please wait', '2026-09-15 21:54:34.670357', 'AI', 0, '2026-09-15 22:07:19.823182', 5, '测试用户');
INSERT INTO `cs_conversations` VALUES (2, '2026-09-15 22:20:36.957326', '1', '2026-10-29 14:36:45.545913', 'AI', 0, '2026-11-01 15:43:11.623509', 2, '12');
INSERT INTO `cs_conversations` VALUES (3, '2026-10-08 15:21:42.080063', '我买的大米收到有点受潮了，能退吗？', '2026-10-08 15:21:42.097329', 'AI', 0, '2026-10-08 15:35:12.133705', 1, '管理员');

-- ----------------------------
-- Table structure for cs_messages
-- ----------------------------
DROP TABLE IF EXISTS `cs_messages`;
CREATE TABLE `cs_messages`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `content` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `conversation_id` bigint NOT NULL,
  `created_at` datetime(6) NULL DEFAULT NULL,
  `sender` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `sender_name` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 23 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of cs_messages
-- ----------------------------
INSERT INTO `cs_messages` VALUES (1, '我买的苹果有点坏，怎么申请退款？', 1, '2026-09-15 21:27:21.108742', 'USER', '测试用户');
INSERT INTO `cs_messages` VALUES (2, '您好，很抱歉给您带来不好的体验。您可以在订单详情页找到对应商品，点击“申请售后”并选择退款原因，提交坏果照片即可。若操作不便，也可以把订单号告诉我，我帮您登记。', 1, '2026-09-15 21:27:23.437155', 'AI', 'AI 客服');
INSERT INTO `cs_messages` VALUES (3, '转人工', 1, '2026-09-15 21:27:30.148037', 'USER', '测试用户');
INSERT INTO `cs_messages` VALUES (4, '好的，正在为您转接人工客服，客服会尽快回复您，请稍候～', 1, '2026-09-15 21:27:30.149151', 'AI', 'AI 客服');
INSERT INTO `cs_messages` VALUES (5, '您好，我是人工客服小李，已收到您的问题，正在为您核实订单，请稍等。', 1, '2026-09-15 21:27:32.093658', 'AGENT', '客服（管理员）');
INSERT INTO `cs_messages` VALUES (6, '优惠券怎么用？', 1, '2026-09-15 21:35:20.298852', 'USER', '测试用户');
INSERT INTO `cs_messages` VALUES (7, '您好，优惠券可以在结算页面使用：加入购物车后点击“去结算”，在订单确认页找到“优惠券”一栏，勾选要使用的券即可自动抵扣。请您留意券的有效期和满减门槛，部分券可能限品类或不可叠加。', 1, '2026-09-15 21:35:26.824270', 'AI', 'AI 客服');
INSERT INTO `cs_messages` VALUES (8, 'Hello my milk arrived broken', 1, '2026-09-15 21:40:23.939586', 'USER', '测试用户');
INSERT INTO `cs_messages` VALUES (9, '抱歉，智能客服暂时无法响应，已为您转接人工客服，客服会尽快回复您，请稍候～', 1, '2026-09-15 21:40:43.159619', 'AI', 'AI 客服');
INSERT INTO `cs_messages` VALUES (10, '我的苹果坏了，能赔吗', 1, '2026-09-15 21:42:51.850733', 'USER', '测试用户');
INSERT INTO `cs_messages` VALUES (11, '很抱歉给您带来不便。坏果问题可以在订单详情页申请售后，上传坏果照片并选择退款原因，审核通过后会按规则处理；具体能否赔偿或退款金额要以审核结果为准。方便的话请把订单号发我，我帮您登记。', 1, '2026-09-15 21:42:59.133902', 'AI', 'AI 客服');
INSERT INTO `cs_messages` VALUES (12, 'We are children your order， please wait', 1, '2026-09-15 21:54:34.668911', 'AGENT', '客服（管理员）');
INSERT INTO `cs_messages` VALUES (13, '我买的东北大米为什么这么久没发货', 2, '2026-09-15 22:21:38.256825', 'USER', '12');
INSERT INTO `cs_messages` VALUES (14, '很抱歉让您久等了！发货延迟可能受库存、物流或收货信息影响，麻烦您提供一下订单号，我马上帮您核实当前进度。', 2, '2026-09-15 22:21:41.322754', 'AI', 'AI 客服');
INSERT INTO `cs_messages` VALUES (15, '＃2', 2, '2026-09-15 22:21:50.015184', 'USER', '12');
INSERT INTO `cs_messages` VALUES (16, '好的，请问“#2”是您的订单号吗？为了准确帮您核实，麻烦您提供完整的订单编号（可在“我的订单”中查看），我马上为您查询发货进度。', 2, '2026-09-15 22:21:54.803361', 'AI', 'AI 客服');
INSERT INTO `cs_messages` VALUES (17, '稍等立马就给您发货', 2, '2026-09-15 23:08:21.490965', 'AGENT', '客服（管理员）');
INSERT INTO `cs_messages` VALUES (18, '我买的东北大米为什么这么久没发货', 2, '2026-09-18 10:33:55.907983', 'USER', '12');
INSERT INTO `cs_messages` VALUES (19, '非常理解您着急的心情，大米发货延迟通常和库存、物流或订单信息有关。为了帮您准确查询，麻烦您提供一下完整的订单编号，我马上为您核实；如果您不方便提供，也可以为您转接人工客服跟进。', 2, '2026-09-18 10:34:01.653505', 'AI', 'AI 客服');
INSERT INTO `cs_messages` VALUES (20, '我买的大米收到有点受潮了，能退吗？', 3, '2026-10-08 15:21:42.095316', 'USER', '管理员');
INSERT INTO `cs_messages` VALUES (21, '非常抱歉给您带来不便，大米受潮属于商品质量问题，可以申请退换货。正在为您转接人工客服，请稍候。', 3, '2026-10-08 15:21:54.096963', 'AI', 'AI 客服');
INSERT INTO `cs_messages` VALUES (22, '1', 2, '2026-10-29 14:36:45.517539', 'AGENT', '客服（管理员）');

-- ----------------------------
-- Table structure for menus
-- ----------------------------
DROP TABLE IF EXISTS `menus`;
CREATE TABLE `menus`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `component` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `created_at` datetime(6) NULL DEFAULT NULL,
  `icon` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `name` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `parent_id` bigint NOT NULL,
  `path` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `permission_id` bigint NULL DEFAULT NULL,
  `sort_order` int NOT NULL,
  `status` int NOT NULL,
  `type` enum('BUTTON','DIR','MENU') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `updated_at` datetime(6) NULL DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 10 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of menus
-- ----------------------------
INSERT INTO `menus` VALUES (1, 'products', '2026-09-08 09:04:22.084394', '🛒', '商品管理', 0, '/admin/products', 1, 1, 1, 'MENU', '2026-09-08 09:04:22.084394');
INSERT INTO `menus` VALUES (2, 'orders', '2026-09-08 09:04:22.085407', '📦', '订单管理', 0, '/admin/orders', 3, 2, 1, 'MENU', '2026-09-08 09:04:22.085407');
INSERT INTO `menus` VALUES (3, 'members', '2026-09-08 09:04:22.086418', '👥', '会员管理', 0, '/admin/members', 5, 3, 1, 'MENU', '2026-09-08 09:04:22.086418');
INSERT INTO `menus` VALUES (4, 'reviews', '2026-09-08 09:04:22.087439', '💬', '评论管理', 0, '/admin/reviews', 7, 4, 1, 'MENU', '2026-09-08 09:04:22.087439');
INSERT INTO `menus` VALUES (5, 'promotions', '2026-09-08 09:04:22.087439', '🏷️', '促销管理', 0, '/admin/promotions', 6, 5, 1, 'MENU', '2026-09-08 09:04:22.087439');
INSERT INTO `menus` VALUES (6, 'shipments', '2026-09-08 09:04:22.088457', '🚚', '物流管理', 0, '/admin/shipments', 4, 6, 1, 'MENU', '2026-09-08 09:04:22.088457');
INSERT INTO `menus` VALUES (7, 'cs', '2026-09-15 21:26:33.040368', '🎧', '客服管理', 0, '/admin/cs', 11, 8, 1, 'MENU', '2026-09-16 08:27:59.567877');
INSERT INTO `menus` VALUES (8, 'refunds', '2026-09-15 22:35:40.255726', '💰', '退款管理', 0, '/admin/refunds', 3, 7, 1, 'MENU', '2026-09-16 08:27:43.488907');
INSERT INTO `menus` VALUES (9, 'knowledge', '2026-10-08 15:30:49.152735', '📚', 'AI 知识库', 0, '/admin/knowledge', NULL, 9, 1, 'MENU', '2026-10-08 15:30:49.152735');

-- ----------------------------
-- Table structure for order_items
-- ----------------------------
DROP TABLE IF EXISTS `order_items`;
CREATE TABLE `order_items`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `order_id` bigint NOT NULL,
  `price` decimal(10, 2) NOT NULL,
  `product_id` bigint NOT NULL,
  `product_image` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `product_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `quantity` int NOT NULL,
  `subtotal` decimal(10, 2) NOT NULL,
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 56 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of order_items
-- ----------------------------
INSERT INTO `order_items` VALUES (1, 1, 2.00, 11, 'p067', '天然矿泉水', 2, 4.00);
INSERT INTO `order_items` VALUES (2, 10, 2.00, 11, 'p067', '天然矿泉水', 3, 6.00);
INSERT INTO `order_items` VALUES (3, 11, 59.90, 7, 'p029', '东北大米', 1, 59.90);
INSERT INTO `order_items` VALUES (4, 12, 39.90, 6, 'p059', '基围虾', 1, 39.90);
INSERT INTO `order_items` VALUES (5, 12, 59.90, 7, 'p029', '东北大米', 1, 59.90);
INSERT INTO `order_items` VALUES (6, 13, 19.90, 10, 'p016', '曲奇饼干', 1, 19.90);
INSERT INTO `order_items` VALUES (7, 13, 6.80, 2, 'p003', '赣南脐橙', 2, 13.60);
INSERT INTO `order_items` VALUES (8, 14, 59.90, 7, 'p029', '东北大米', 1, 59.90);
INSERT INTO `order_items` VALUES (9, 15, 19.90, 10, 'p016', '曲奇饼干', 1, 19.90);
INSERT INTO `order_items` VALUES (10, 16, 19.90, 10, 'p016', '曲奇饼干', 1, 19.90);
INSERT INTO `order_items` VALUES (11, 16, 6.80, 2, 'p003', '赣南脐橙', 2, 13.60);
INSERT INTO `order_items` VALUES (12, 17, 89.90, 8, 'p030', '花生油', 1, 89.90);
INSERT INTO `order_items` VALUES (13, 17, 39.90, 12, 'p072', '维达超韧抽纸', 1, 39.90);
INSERT INTO `order_items` VALUES (14, 18, 89.90, 8, 'p030', '花生油', 2, 179.80);
INSERT INTO `order_items` VALUES (15, 19, 45.90, 9, 'p015', '纯牛奶', 2, 91.80);
INSERT INTO `order_items` VALUES (16, 19, 89.90, 8, 'p030', '花生油', 2, 179.80);
INSERT INTO `order_items` VALUES (17, 20, 49.90, 14, 'p073', '蓝月亮洗衣液', 3, 149.70);
INSERT INTO `order_items` VALUES (18, 21, 45.90, 9, 'p015', '纯牛奶', 1, 45.90);
INSERT INTO `order_items` VALUES (19, 22, 29.90, 13, 'p033', '清风原木抽纸', 2, 59.80);
INSERT INTO `order_items` VALUES (20, 23, 49.90, 14, 'p073', '蓝月亮洗衣液', 1, 49.90);
INSERT INTO `order_items` VALUES (21, 24, 59.90, 7, 'p029', '东北大米', 2, 119.80);
INSERT INTO `order_items` VALUES (22, 25, 15.90, 5, 'p012', '农家土鸡蛋', 1, 15.90);
INSERT INTO `order_items` VALUES (23, 25, 4.50, 3, 'p007', '有机菠菜', 2, 9.00);
INSERT INTO `order_items` VALUES (24, 26, 39.90, 12, 'p072', '维达超韧抽纸', 1, 39.90);
INSERT INTO `order_items` VALUES (25, 26, 2.00, 11, 'p067', '天然矿泉水', 5, 10.00);
INSERT INTO `order_items` VALUES (26, 27, 89.90, 8, 'p030', '花生油', 1, 89.90);
INSERT INTO `order_items` VALUES (27, 28, 49.90, 14, 'p073', '蓝月亮洗衣液', 2, 99.80);
INSERT INTO `order_items` VALUES (28, 29, 29.90, 13, 'p033', '清风原木抽纸', 1, 29.90);
INSERT INTO `order_items` VALUES (29, 29, 19.90, 10, 'p016', '曲奇饼干', 1, 19.90);
INSERT INTO `order_items` VALUES (30, 35, 89.90, 8, 'p030', '花生油', 1, 89.90);
INSERT INTO `order_items` VALUES (31, 36, 89.90, 8, 'p030', '花生油', 1, 89.90);
INSERT INTO `order_items` VALUES (32, 37, 89.90, 8, 'p030', '花生油', 1, 89.90);
INSERT INTO `order_items` VALUES (33, 38, 89.90, 8, 'p030', '花生油', 1, 89.90);
INSERT INTO `order_items` VALUES (34, 39, 89.90, 8, 'p030', '花生油', 1, 89.90);
INSERT INTO `order_items` VALUES (35, 40, 89.90, 8, 'p030', '花生油', 1, 89.90);
INSERT INTO `order_items` VALUES (36, 41, 89.90, 8, 'p030', '花生油', 10, 899.00);
INSERT INTO `order_items` VALUES (37, 42, 89.90, 8, 'p030', '花生油', 10, 899.00);
INSERT INTO `order_items` VALUES (38, 43, 19.90, 10, 'p016', '曲奇饼干', 1, 19.90);
INSERT INTO `order_items` VALUES (39, 44, 89.90, 8, 'p030', '花生油', 1, 89.90);
INSERT INTO `order_items` VALUES (40, 45, 4.50, 3, 'p007', '有机菠菜', 2, 9.00);
INSERT INTO `order_items` VALUES (41, 46, 4.50, 3, 'p007', '有机菠菜', 1, 4.50);
INSERT INTO `order_items` VALUES (42, 47, 15.90, 5, 'p012', '农家土鸡蛋', 1, 15.90);
INSERT INTO `order_items` VALUES (43, 48, 19.90, 4, 'p011', '五花肉', 1, 19.90);
INSERT INTO `order_items` VALUES (44, 49, 19.90, 4, 'p011', '五花肉', 1, 19.90);
INSERT INTO `order_items` VALUES (45, 50, 4.50, 3, 'p007', '有机菠菜', 2, 9.00);
INSERT INTO `order_items` VALUES (46, 51, 4.50, 3, 'p007', '有机菠菜', 1, 4.50);
INSERT INTO `order_items` VALUES (47, 52, 4.50, 3, 'p007', '有机菠菜', 2, 9.00);
INSERT INTO `order_items` VALUES (48, 53, 4.50, 3, 'p007', '有机菠菜', 1, 4.50);
INSERT INTO `order_items` VALUES (49, 54, 39.90, 6, 'p059', '基围虾', 1, 39.90);
INSERT INTO `order_items` VALUES (50, 54, 89.90, 8, 'p030', '花生油', 1, 89.90);
INSERT INTO `order_items` VALUES (54, 58, 49.90, 14, 'p073', '蓝月亮洗衣液', 1, 49.90);
INSERT INTO `order_items` VALUES (55, 58, 59.90, 7, 'p029', '东北大米', 1, 59.90);

-- ----------------------------
-- Table structure for orders
-- ----------------------------
DROP TABLE IF EXISTS `orders`;
CREATE TABLE `orders`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `cancelled_at` datetime(6) NULL DEFAULT NULL,
  `completed_at` datetime(6) NULL DEFAULT NULL,
  `created_at` datetime(6) NULL DEFAULT NULL,
  `order_no` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `paid_at` datetime(6) NULL DEFAULT NULL,
  `pay_amount` decimal(10, 2) NOT NULL,
  `receiver_address` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `receiver_name` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `receiver_phone` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `shipped_at` datetime(6) NULL DEFAULT NULL,
  `status` enum('CANCELLED','COMPLETED','PAID','PENDING_PAYMENT','REFUNDED','REFUNDING','SHIPPED') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `total_amount` decimal(10, 2) NOT NULL,
  `user_id` bigint NOT NULL,
  `discount_amount` decimal(10, 2) NOT NULL,
  `promotion_id` bigint NULL DEFAULT NULL,
  `risk_level` enum('HIGH','LOW','MEDIUM') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `risk_scanned_at` datetime(6) NULL DEFAULT NULL,
  `risk_score` int NULL DEFAULT NULL,
  `user_coupon_id` bigint NULL DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `UKg8pohnngqi5x1nask7nff2u7w`(`order_no` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 59 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of orders
-- ----------------------------
INSERT INTO `orders` VALUES (1, '2026-09-15 22:39:16.121625', NULL, '2026-09-08 09:39:25.152628', 'O20260908001', '2026-09-15 19:45:16.418277', 49.50, '北京市朝阳区望京 SOHO T2 1801', '测试用户', '13800000003', '苹果 5 斤，请尽快发货', '2026-09-15 22:38:59.283397', 'REFUNDED', 49.50, 5, 0.00, NULL, 'LOW', '2026-09-08 10:33:16.783171', 0, NULL);
INSERT INTO `orders` VALUES (2, '2026-10-22 15:06:30.217203', NULL, '2026-09-08 09:39:25.153629', 'O20260908002', '2026-09-07 14:39:25.153629', 79.60, '上海市浦东新区世纪大道 100 号 3 楼', '张三', '13800000010', '五花肉 4 斤，分两份打包', '2026-09-16 09:00:38.788491', 'REFUNDED', 79.60, 2, 0.00, NULL, 'LOW', '2026-09-08 10:32:39.408380', 0, NULL);
INSERT INTO `orders` VALUES (3, NULL, NULL, '2026-09-08 09:39:25.166232', 'O20260908003', '2026-09-07 02:39:25.166232', 47.92, '广州市天河区珠江新城兴民路 222 号', '客服小李', '13800000002', NULL, '2026-09-07 23:39:25.166232', 'SHIPPED', 59.90, 4, 11.98, 3, 'LOW', '2026-09-08 10:33:00.021058', 5, NULL);
INSERT INTO `orders` VALUES (4, NULL, '2026-09-07 09:39:25.166737', '2026-09-08 09:39:25.166737', 'O20260908004', '2026-09-04 09:44:25.166737', 79.80, '深圳市南山区科技园南区 9 号楼', '运营小王', '13800000001', NULL, '2026-09-05 09:39:25.166737', 'COMPLETED', 79.80, 3, 0.00, NULL, 'LOW', '2026-09-08 10:33:14.714057', 0, NULL);
INSERT INTO `orders` VALUES (5, NULL, NULL, '2026-09-08 09:39:25.168240', 'O20260908005', '2026-09-06 09:49:25.168240', 89.90, '上海市浦东新区世纪大道 100 号 3 楼', '张三', '13800000010', '花生油 1 桶，请轻拿轻放', '2026-09-07 09:39:25.168240', 'SHIPPED', 89.90, 2, 0.00, NULL, 'LOW', '2026-09-08 10:33:15.668241', 0, NULL);
INSERT INTO `orders` VALUES (6, NULL, NULL, '2026-09-08 10:54:01.908543', 'O20260908083', NULL, 99.00, '北京市朝阳区望京 SOHO T3 楼下快递柜', '风险测试账户', '13900000086', '(高风险演示)', NULL, 'PENDING_PAYMENT', 99.00, 6, 0.00, NULL, 'LOW', '2026-09-09 09:24:04.687964', 15, NULL);
INSERT INTO `orders` VALUES (7, NULL, NULL, '2026-09-08 10:54:01.910560', 'O20260908084', NULL, 58.00, '北京市朝阳区望京 SOHO T3 楼下菜鸟驿站', '风险测试账户', '13900000086', '(高风险演示)', NULL, 'PENDING_PAYMENT', 58.00, 6, 0.00, NULL, 'LOW', '2026-09-16 12:09:49.122789', 15, NULL);
INSERT INTO `orders` VALUES (8, NULL, NULL, '2026-09-08 10:54:01.912094', 'O20260908085', NULL, 128.00, '北京市朝阳区望京 SOHO T3 中转站', '风险测试账户', '13900000086', '(高风险演示)', NULL, 'PENDING_PAYMENT', 128.00, 6, 0.00, NULL, 'LOW', '2026-09-16 12:09:25.965571', 15, NULL);
INSERT INTO `orders` VALUES (9, NULL, NULL, '2026-09-08 10:54:01.913098', 'O20260908086', NULL, 1888.00, '北京市朝阳区望京 SOHO T3 楼下快递柜（勿放丢失）', '风险测试账户', '13900000086', '(高风险演示：大额+凌晨+敏感地址)', NULL, 'PENDING_PAYMENT', 1888.00, 6, 0.00, NULL, 'HIGH', '2026-09-08 10:54:01.913098', 85, NULL);
INSERT INTO `orders` VALUES (10, NULL, '2026-09-11 10:14:27.147419', '2026-09-11 09:32:07.174167', 'DR20260911093207940', '2026-09-11 09:32:07.204567', 4.80, '1323123', '12', '13107993087', NULL, '2026-09-11 09:35:46.894690', 'COMPLETED', 6.00, 2, 1.20, 3, 'LOW', '2026-09-11 09:35:36.216588', 0, NULL);
INSERT INTO `orders` VALUES (11, '2026-09-15 22:23:47.902416', NULL, '2026-09-14 17:18:54.064939', 'DR20260914171854333', '2026-09-14 17:18:54.114655', 47.92, '1323123', '12', '13107993087', NULL, NULL, 'CANCELLED', 59.90, 2, 11.98, 3, 'LOW', '2026-09-16 12:13:44.893022', 0, NULL);
INSERT INTO `orders` VALUES (12, NULL, NULL, '2026-09-14 18:01:20.660350', 'DR20260914180120805', '2026-09-14 18:01:20.697429', 79.84, '1323123', '12', '13107993087', NULL, NULL, 'PAID', 99.80, 2, 19.96, 3, 'LOW', '2026-09-16 12:13:52.888821', 0, NULL);
INSERT INTO `orders` VALUES (13, NULL, NULL, '2026-09-14 19:44:54.981605', 'DR20260914194454888', '2026-09-14 19:45:27.083245', 26.80, '1323123', '12', '13107993087', NULL, NULL, 'PAID', 33.50, 2, 6.70, 3, 'LOW', '2026-09-14 19:45:05.217744', 0, NULL);
INSERT INTO `orders` VALUES (14, NULL, NULL, '2026-09-14 19:46:44.897463', 'DR20260914194644444', '2026-09-14 19:48:14.131422', 47.92, 'BeijingChaoyangRoad1', '12', '13800138000', NULL, NULL, 'PAID', 59.90, 2, 11.98, 3, 'LOW', '2026-09-16 12:14:01.648572', 0, NULL);
INSERT INTO `orders` VALUES (15, NULL, NULL, '2026-09-15 19:23:36.748451', 'DR20260915192336154', '2026-09-15 19:23:39.296613', 19.90, 'BeijingChaoyangRoad1', '12', '13800138000', NULL, NULL, 'PAID', 19.90, 2, 0.00, NULL, 'LOW', '2026-09-16 12:14:10.053839', 0, NULL);
INSERT INTO `orders` VALUES (16, '2026-09-15 22:36:06.429692', NULL, '2026-09-15 19:42:46.497826', 'DR20260915194246105', '2026-09-15 19:42:46.525679', 33.50, 'ShanghaiPudongTestRoad100', 'testuser', '13800138000', NULL, NULL, 'REFUNDED', 33.50, 5, 0.00, NULL, 'LOW', '2026-09-16 12:14:17.640761', 35, NULL);
INSERT INTO `orders` VALUES (17, NULL, NULL, '2026-09-15 19:48:41.084634', 'DR20260915194841397', '2026-09-15 19:48:41.104986', 119.80, 'adadad', '12', '13107993087', NULL, NULL, 'PAID', 129.80, 2, 10.00, 1, 'LOW', '2026-09-16 12:14:25.829796', 0, NULL);
INSERT INTO `orders` VALUES (18, '2026-09-15 20:26:07.288084', NULL, '2026-09-15 20:26:00.969840', 'DR20260915202600361', NULL, 159.80, '北京市朝阳区 建国路 88 号', '李四', '13900139000', NULL, NULL, 'CANCELLED', 179.80, 5, 20.00, 1, 'LOW', '2026-09-16 12:14:34.223293', 35, 1);
INSERT INTO `orders` VALUES (19, NULL, '2026-09-15 21:32:26.000000', '2026-09-15 20:30:28.563358', 'DR20260915203028793', '2026-09-15 20:30:28.598449', 251.60, '北京市朝阳区 建国路 88 号', '李四', '13900139000', NULL, NULL, 'COMPLETED', 271.60, 5, 20.00, 1, 'LOW', '2026-09-16 12:14:42.226419', 35, 1);
INSERT INTO `orders` VALUES (20, '2026-09-16 08:30:08.559900', NULL, '2026-09-15 20:40:22.674191', 'DR20260915204022226', '2026-09-15 20:40:22.697520', 139.70, 'adaad', '12', '13107993087', NULL, '2026-09-15 22:41:21.253778', 'REFUNDED', 149.70, 2, 10.00, 1, 'LOW', '2026-09-16 12:14:50.742944', 0, NULL);
INSERT INTO `orders` VALUES (21, '2026-09-15 22:44:36.991470', NULL, '2026-09-15 22:22:59.664848', 'DR20260915222259990', '2026-09-15 22:22:59.699820', 45.90, '福建省泉州市石狮市 宝盖镇闽南理工学院宝盖校区', '张三', '13107993087', NULL, NULL, 'REFUNDED', 45.90, 2, 0.00, NULL, 'LOW', '2026-09-16 12:14:58.271385', 0, NULL);
INSERT INTO `orders` VALUES (22, NULL, NULL, '2026-09-15 22:39:40.741708', 'DR20260915223940918', '2026-09-15 22:39:40.982175', 54.80, '上海市浦东新区大润发总部', '测试用户', '13800000003', NULL, NULL, 'PAID', 59.80, 5, 5.00, 2, 'LOW', '2026-09-16 12:15:06.298736', 35, NULL);
INSERT INTO `orders` VALUES (23, NULL, NULL, '2026-09-15 22:39:41.080796', 'DR20260915223941978', '2026-09-15 22:39:41.321758', 49.90, '上海市浦东新区大润发总部', '测试用户', '13800000003', NULL, '2026-09-15 22:39:41.382608', 'SHIPPED', 49.90, 5, 0.00, NULL, 'LOW', '2026-09-16 12:15:14.159667', 35, NULL);
INSERT INTO `orders` VALUES (24, NULL, '2026-09-13 09:12:33.000000', '2026-09-12 10:23:15.000000', 'DR20260912102315417', '2026-09-12 10:25:40.000000', 119.80, '福建省泉州市石狮市 宝盖镇闽南理工学院宝盖校区', '张三', '13107993087', NULL, '2026-09-12 16:40:12.000000', 'COMPLETED', 119.80, 2, 0.00, NULL, 'LOW', '2026-09-16 12:09:33.239871', 5, NULL);
INSERT INTO `orders` VALUES (25, NULL, '2026-09-13 14:20:05.000000', '2026-09-12 15:47:02.000000', 'DR20260912154702283', '2026-09-12 15:48:30.000000', 24.90, '北京市朝阳区 建国路 88 号', '李四', '13900139000', NULL, '2026-09-12 18:30:00.000000', 'COMPLETED', 24.90, 5, 0.00, NULL, 'LOW', '2026-09-16 12:09:41.679296', 35, NULL);
INSERT INTO `orders` VALUES (26, NULL, '2026-09-14 11:05:00.000000', '2026-09-12 19:08:41.000000', 'DR20260912190841650', '2026-09-12 19:10:02.000000', 49.90, '福建省泉州市石狮市 宝盖镇闽南理工学院宝盖校区', '张三', '13107993087', NULL, '2026-09-13 09:00:00.000000', 'COMPLETED', 49.90, 2, 0.00, NULL, 'LOW', '2026-09-16 12:09:50.193261', 0, NULL);
INSERT INTO `orders` VALUES (27, NULL, '2026-09-14 16:30:12.000000', '2026-09-13 09:31:20.000000', 'DR20260913093120544', '2026-09-13 09:32:45.000000', 89.90, '北京市朝阳区 建国路 88 号', '李四', '13900139000', NULL, '2026-09-13 12:00:00.000000', 'COMPLETED', 89.90, 5, 0.00, NULL, 'LOW', '2026-09-16 12:13:23.449081', 35, NULL);
INSERT INTO `orders` VALUES (28, NULL, '2026-09-14 19:40:00.000000', '2026-09-13 14:12:55.000000', 'DR20260913141255871', '2026-09-13 14:14:10.000000', 99.80, '福建省泉州市石狮市 宝盖镇闽南理工学院宝盖校区', '张三', '13107993087', NULL, '2026-09-13 20:00:00.000000', 'COMPLETED', 99.80, 2, 0.00, NULL, 'LOW', '2026-09-16 12:13:31.907055', 0, NULL);
INSERT INTO `orders` VALUES (29, NULL, '2026-09-15 10:15:00.000000', '2026-09-13 20:45:10.000000', 'DR20260913204510326', '2026-09-13 20:46:33.000000', 49.80, '福建省泉州市石狮市 宝盖镇闽南理工学院宝盖校区', '张三', '13107993087', NULL, '2026-09-14 08:30:00.000000', 'COMPLETED', 49.80, 2, 0.00, NULL, 'LOW', '2026-09-16 12:13:36.761673', 0, NULL);
INSERT INTO `orders` VALUES (30, '2026-09-16 11:06:21.068518', NULL, '2026-09-16 11:05:42.000000', 'SELFTEST-CANCEL', '2026-09-16 11:05:54.728435', 88.00, '北京市朝阳区望京 SOHO T2 1801', '测试用户', '13800000003', NULL, NULL, 'CANCELLED', 88.00, 5, 0.00, NULL, 'LOW', '2026-09-16 12:15:22.219475', 35, NULL);
INSERT INTO `orders` VALUES (31, NULL, NULL, '2026-09-16 11:05:42.000000', 'SELFTEST-INSUFF', '2026-09-16 11:05:55.636361', 99999.00, '北京市朝阳区望京 SOHO T2 1801', '测试用户', '13800000003', NULL, NULL, 'PAID', 99999.00, 5, 0.00, NULL, 'MEDIUM', '2026-09-16 12:15:30.244371', 65, NULL);
INSERT INTO `orders` VALUES (32, '2026-09-16 11:06:12.659958', NULL, '2026-09-16 11:05:52.000000', 'SELFTEST-REFUND', '2026-09-16 11:05:54.398254', 66.00, '北京市朝阳区望京 SOHO T2 1801', '测试用户', '13800000003', NULL, NULL, 'REFUNDED', 66.00, 5, 0.00, NULL, 'LOW', '2026-09-16 12:15:38.236558', 35, NULL);
INSERT INTO `orders` VALUES (34, NULL, NULL, '2026-09-16 11:16:26.000000', 'SELFTEST-PAYREFUND', '2026-09-16 11:16:29.494516', 45.00, 'selftest addr', 'selftest', '13800000003', NULL, NULL, 'PAID', 45.00, 5, 0.00, NULL, 'LOW', '2026-09-16 12:15:46.034856', 35, NULL);
INSERT INTO `orders` VALUES (35, '2026-09-16 11:21:53.484079', NULL, '2026-09-16 11:21:15.409132', 'DR20260916112115932', '2026-09-16 11:21:21.255481', 84.90, '北京市朝阳区测试路1号', '测试收货', '13800000003', NULL, NULL, 'REFUNDED', 89.90, 5, 5.00, 2, 'LOW', '2026-09-16 12:15:53.626826', 35, NULL);
INSERT INTO `orders` VALUES (36, NULL, NULL, '2026-09-16 11:21:27.310138', 'DR20260916112127838', NULL, 84.90, '上海市测试路2号', '风险收货', '13900000086', NULL, NULL, 'PENDING_PAYMENT', 89.90, 6, 5.00, 2, 'LOW', '2026-09-16 12:16:01.149772', 0, NULL);
INSERT INTO `orders` VALUES (37, NULL, NULL, '2026-09-16 11:21:34.516962', 'DR20260916112134691', NULL, 84.90, '北京市朝阳区测试路1号', '测试收货', '13800000003', NULL, NULL, 'PENDING_PAYMENT', 89.90, 5, 5.00, 2, 'LOW', '2026-09-16 12:16:08.897668', 35, NULL);
INSERT INTO `orders` VALUES (38, '2026-10-22 15:39:06.002645', NULL, '2026-09-16 11:21:35.017586', 'DR20260916112135320', '2026-09-16 11:21:36.166961', 84.90, '北京市朝阳区测试路1号', '测试收货', '13800000003', NULL, NULL, 'REFUNDED', 89.90, 5, 5.00, 2, 'LOW', '2026-09-16 12:16:17.599306', 35, NULL);
INSERT INTO `orders` VALUES (39, '2026-09-16 11:22:01.498608', NULL, '2026-09-16 11:21:59.833175', 'DR20260916112159794', '2026-09-16 11:22:00.699868', 84.90, '北京市朝阳区测试路1号', '测试收货', '13800000003', NULL, NULL, 'CANCELLED', 89.90, 5, 5.00, 2, 'LOW', '2026-09-16 12:16:26.347008', 35, NULL);
INSERT INTO `orders` VALUES (40, NULL, NULL, '2026-09-16 11:22:08.670549', 'DR20260916112208969', '2026-09-16 11:22:09.023187', 84.90, '北京市朝阳区测试路1号', '测试收货', '13800000003', NULL, NULL, 'PAID', 89.90, 5, 5.00, 2, 'LOW', '2026-09-16 12:16:35.548046', 35, NULL);
INSERT INTO `orders` VALUES (41, NULL, NULL, '2026-09-16 11:22:23.837719', 'DR20260916112223617', '2026-09-16 11:22:32.321448', 879.00, '北京市朝阳区测试路1号', '测试收货', '13800000003', NULL, NULL, 'PAID', 899.00, 5, 20.00, 4, 'LOW', '2026-09-16 12:16:43.931161', 35, NULL);
INSERT INTO `orders` VALUES (42, NULL, NULL, '2026-09-16 11:22:24.135922', 'DR20260916112224719', '2026-09-16 12:00:47.945141', 879.00, '北京市朝阳区测试路1号', '测试收货', '13800000003', NULL, NULL, 'PAID', 899.00, 5, 20.00, 4, 'LOW', '2026-09-16 12:16:52.404583', 35, NULL);
INSERT INTO `orders` VALUES (43, '2026-10-22 15:23:03.176919', NULL, '2026-09-16 11:27:19.928614', 'DR20260916112719998', '2026-09-16 11:27:19.968112', 19.90, '北京市朝阳区 建国路 88 号', '李四', '13900139000', NULL, NULL, 'REFUNDED', 19.90, 5, 0.00, NULL, 'LOW', '2026-09-16 12:17:00.767154', 35, NULL);
INSERT INTO `orders` VALUES (44, '2026-09-16 11:46:51.124741', NULL, '2026-09-16 11:46:50.054140', 'DR20260916114650107', '2026-09-16 11:46:50.391605', 84.90, '北京市朝阳区测试路1号', '测试收货', '13800000003', NULL, NULL, 'REFUNDED', 89.90, 5, 5.00, 2, 'LOW', '2026-09-16 12:17:09.315090', 35, NULL);
INSERT INTO `orders` VALUES (45, '2026-09-16 12:12:14.528416', NULL, '2026-09-16 12:11:53.536808', 'DR20260916121153337', '2026-09-16 12:11:59.942031', 9.00, '上海市浦东新区XX路1号', '测试用户', '13800000003', NULL, NULL, 'REFUNDED', 9.00, 5, 0.00, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `orders` VALUES (46, '2026-10-22 14:54:40.354789', NULL, '2026-09-16 12:12:26.077851', 'DR20260916121226127', '2026-09-16 12:12:31.903746', 4.50, '上海市浦东新区客服路9号', '客服自测', '13900000004', NULL, NULL, 'REFUNDED', 4.50, 4, 0.00, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `orders` VALUES (47, NULL, NULL, '2026-09-16 12:12:50.977238', 'DR20260916121250820', NULL, 15.90, '上海市静安区风控路6号', '风控用户', '13700000006', NULL, NULL, 'PENDING_PAYMENT', 15.90, 6, 0.00, NULL, 'LOW', '2026-10-06 19:56:09.456258', 0, NULL);
INSERT INTO `orders` VALUES (48, NULL, NULL, '2026-09-16 12:13:14.505895', 'DR20260916121314859', '2026-09-16 12:13:26.492779', 19.90, '上海市静安区风控路6号', '风控用户', '13700000006', NULL, NULL, 'PAID', 19.90, 6, 0.00, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `orders` VALUES (49, NULL, NULL, '2026-09-16 12:13:15.971360', 'DR20260916121315252', NULL, 19.90, '上海市静安区风控路6号', '风控用户', '13700000006', NULL, NULL, 'PENDING_PAYMENT', 19.90, 6, 0.00, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `orders` VALUES (50, NULL, NULL, '2026-09-16 12:13:35.952080', 'DR20260916121335493', '2026-09-16 12:13:38.529621', 9.00, '上海市静安区风控路6号', '风控用户', '13700000006', NULL, NULL, 'PAID', 9.00, 6, 0.00, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `orders` VALUES (51, NULL, NULL, '2026-09-16 12:13:47.218765', 'DR20260916121347646', '2026-09-16 12:13:49.890687', 4.50, '上海市静安区风控路6号', '风控用户', '13700000006', NULL, NULL, 'PAID', 4.50, 6, 0.00, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `orders` VALUES (52, '2026-09-16 12:45:23.824635', NULL, '2026-09-16 12:45:15.614510', 'DR20260916124515337', '2026-09-16 12:45:18.514392', 9.00, '上海浦东QA路1号', 'QA复验', '13800000003', NULL, NULL, 'REFUNDED', 9.00, 5, 0.00, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `orders` VALUES (53, '2026-10-22 14:55:07.682473', NULL, '2026-09-16 12:45:27.014498', 'DR20260916124527232', '2026-09-16 12:45:29.402006', 4.50, '上海浦东QA路2号', 'QA复验', '13900000004', NULL, NULL, 'REFUNDED', 4.50, 4, 0.00, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `orders` VALUES (54, NULL, NULL, '2026-09-16 19:53:51.135595', 'DR20260916195351194', '2026-09-16 19:53:51.185278', 109.80, '福建省泉州市石狮市 宝盖镇闽南理工学院宝盖校区', '张三', '13107993087', NULL, NULL, 'PAID', 129.80, 2, 20.00, 1, 'LOW', '2026-10-22 15:20:49.194577', 8, 3);
INSERT INTO `orders` VALUES (58, NULL, NULL, '2026-09-18 10:33:15.439107', 'DR20260918103315261', '2026-09-18 10:33:15.498551', 94.80, '福建省泉州市石狮市 宝盖镇闽南理工学院宝盖校区', '张三', '13107993087', NULL, NULL, 'PAID', 109.80, 2, 15.00, 1, 'LOW', '2026-10-22 16:23:50.034218', 12, 15);

-- ----------------------------
-- Table structure for payments
-- ----------------------------
DROP TABLE IF EXISTS `payments`;
CREATE TABLE `payments`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `amount` decimal(10, 2) NOT NULL,
  `created_at` datetime(6) NULL DEFAULT NULL,
  `method` enum('ALIPAY','BALANCE','BANK','CASH','WECHAT') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `order_id` bigint NOT NULL,
  `order_no` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `paid_at` datetime(6) NULL DEFAULT NULL,
  `status` enum('FAILED','REFUNDED','SUCCESS') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `trade_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 48 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of payments
-- ----------------------------
INSERT INTO `payments` VALUES (1, 79.60, '2026-09-08 09:39:25.187811', 'WECHAT', 2, 'O20260908002', '2026-09-07 14:39:25.153629', 'REFUNDED', 'WX20260908002001', 2);
INSERT INTO `payments` VALUES (2, 47.92, '2026-09-08 09:39:25.189318', 'ALIPAY', 3, 'O20260908003', '2026-09-07 02:39:25.166232', 'SUCCESS', 'ALI20260908003002', 4);
INSERT INTO `payments` VALUES (3, 79.80, '2026-09-08 09:39:25.189318', 'BANK', 4, 'O20260908004', '2026-09-04 09:44:25.166737', 'REFUNDED', 'BANK20260908004003', 3);
INSERT INTO `payments` VALUES (4, 89.90, '2026-09-08 09:39:25.190821', 'WECHAT', 5, 'O20260908005', '2026-09-06 09:49:25.168240', 'SUCCESS', 'WX20260908005004', 2);
INSERT INTO `payments` VALUES (5, 4.80, '2026-09-11 09:32:07.204567', 'WECHAT', 10, 'DR20260911093207940', '2026-09-11 09:32:07.204567', 'SUCCESS', NULL, 2);
INSERT INTO `payments` VALUES (6, 47.92, '2026-09-14 17:18:54.116159', 'WECHAT', 11, 'DR20260914171854333', '2026-09-14 17:18:54.114655', 'SUCCESS', NULL, 2);
INSERT INTO `payments` VALUES (7, 79.84, '2026-09-14 18:01:20.698430', 'WECHAT', 12, 'DR20260914180120805', '2026-09-14 18:01:20.697429', 'SUCCESS', NULL, 2);
INSERT INTO `payments` VALUES (8, 26.80, '2026-09-14 19:45:27.083245', 'WECHAT', 13, 'DR20260914194454888', '2026-09-14 19:45:27.083245', 'SUCCESS', NULL, 2);
INSERT INTO `payments` VALUES (9, 47.92, '2026-09-14 19:48:14.132423', 'WECHAT', 14, 'DR20260914194644444', '2026-09-14 19:48:14.132423', 'SUCCESS', NULL, 2);
INSERT INTO `payments` VALUES (10, 19.90, '2026-09-15 19:23:39.297720', 'WECHAT', 15, 'DR20260915192336154', '2026-09-15 19:23:39.297720', 'SUCCESS', NULL, 2);
INSERT INTO `payments` VALUES (11, 33.50, '2026-09-15 19:42:46.525679', 'WECHAT', 16, 'DR20260915194246105', '2026-09-15 19:42:46.525679', 'REFUNDED', NULL, 5);
INSERT INTO `payments` VALUES (12, 49.50, '2026-09-15 19:45:16.418277', 'WECHAT', 1, 'O20260908001', '2026-09-15 19:45:16.418277', 'REFUNDED', NULL, 5);
INSERT INTO `payments` VALUES (13, 119.80, '2026-09-15 19:48:41.104986', 'WECHAT', 17, 'DR20260915194841397', '2026-09-15 19:48:41.104986', 'SUCCESS', NULL, 2);
INSERT INTO `payments` VALUES (14, 251.60, '2026-09-15 20:30:28.598449', 'WECHAT', 19, 'DR20260915203028793', '2026-09-15 20:30:28.598449', 'SUCCESS', NULL, 5);
INSERT INTO `payments` VALUES (15, 139.70, '2026-09-15 20:40:22.697520', 'WECHAT', 20, 'DR20260915204022226', '2026-09-15 20:40:22.697520', 'REFUNDED', NULL, 2);
INSERT INTO `payments` VALUES (16, 45.90, '2026-09-15 22:22:59.700829', 'WECHAT', 21, 'DR20260915222259990', '2026-09-15 22:22:59.699820', 'REFUNDED', NULL, 2);
INSERT INTO `payments` VALUES (17, 54.80, '2026-09-15 22:39:40.982679', 'WECHAT', 22, 'DR20260915223940918', '2026-09-15 22:39:40.982679', 'SUCCESS', NULL, 5);
INSERT INTO `payments` VALUES (18, 49.90, '2026-09-15 22:39:41.323014', 'WECHAT', 23, 'DR20260915223941978', '2026-09-15 22:39:41.323014', 'SUCCESS', NULL, 5);
INSERT INTO `payments` VALUES (19, 119.80, '2026-09-12 10:25:40.000000', 'WECHAT', 24, 'DR20260912102315417', '2026-09-12 10:25:40.000000', 'SUCCESS', 'WX2026091210254034471', 2);
INSERT INTO `payments` VALUES (20, 24.90, '2026-09-12 15:48:30.000000', 'ALIPAY', 25, 'DR20260912154702283', '2026-09-12 15:48:30.000000', 'SUCCESS', 'ALI2026091215483001298', 5);
INSERT INTO `payments` VALUES (21, 49.90, '2026-09-12 19:10:02.000000', 'WECHAT', 26, 'DR20260912190841650', '2026-09-12 19:10:02.000000', 'SUCCESS', 'WX2026091219100288392', 2);
INSERT INTO `payments` VALUES (22, 89.90, '2026-09-13 09:32:45.000000', 'WECHAT', 27, 'DR20260913093120544', '2026-09-13 09:32:45.000000', 'SUCCESS', 'WX2026091309324551207', 5);
INSERT INTO `payments` VALUES (23, 99.80, '2026-09-13 14:14:10.000000', 'ALIPAY', 28, 'DR20260913141255871', '2026-09-13 14:14:10.000000', 'SUCCESS', 'ALI2026091314141007733', 2);
INSERT INTO `payments` VALUES (24, 49.80, '2026-09-13 20:46:33.000000', 'WECHAT', 29, 'DR20260913204510326', '2026-09-13 20:46:33.000000', 'SUCCESS', 'WX2026091320463304618', 2);
INSERT INTO `payments` VALUES (25, 66.00, '2026-09-16 11:05:54.399301', 'BALANCE', 32, 'SELFTEST-REFUND', '2026-09-16 11:05:54.398254', 'REFUNDED', NULL, 5);
INSERT INTO `payments` VALUES (26, 88.00, '2026-09-16 11:05:54.728435', 'BALANCE', 30, 'SELFTEST-CANCEL', '2026-09-16 11:05:54.728435', 'REFUNDED', NULL, 5);
INSERT INTO `payments` VALUES (27, 99999.00, '2026-09-16 11:05:55.636865', 'WECHAT', 31, 'SELFTEST-INSUFF', '2026-09-16 11:05:55.636361', 'SUCCESS', NULL, 5);
INSERT INTO `payments` VALUES (28, 45.00, '2026-09-16 11:16:29.495526', 'BALANCE', 34, 'SELFTEST-PAYREFUND', '2026-09-16 11:16:29.494516', 'REFUNDED', NULL, 5);
INSERT INTO `payments` VALUES (29, 84.90, '2026-09-16 11:21:21.255481', 'BALANCE', 35, 'DR20260916112115932', '2026-09-16 11:21:21.255481', 'REFUNDED', NULL, 5);
INSERT INTO `payments` VALUES (30, 84.90, '2026-09-16 11:21:36.166961', 'WECHAT', 38, 'DR20260916112135320', '2026-09-16 11:21:36.166961', 'REFUNDED', NULL, 5);
INSERT INTO `payments` VALUES (31, 84.90, '2026-09-16 11:22:00.699868', 'BALANCE', 39, 'DR20260916112159794', '2026-09-16 11:22:00.699868', 'REFUNDED', NULL, 5);
INSERT INTO `payments` VALUES (32, 84.90, '2026-09-16 11:22:09.023187', 'BALANCE', 40, 'DR20260916112208969', '2026-09-16 11:22:09.023187', 'REFUNDED', NULL, 5);
INSERT INTO `payments` VALUES (33, 879.00, '2026-09-16 11:22:32.321448', 'BALANCE', 41, 'DR20260916112223617', '2026-09-16 11:22:32.321448', 'REFUNDED', NULL, 5);
INSERT INTO `payments` VALUES (34, 19.90, '2026-09-16 11:27:19.968112', 'BALANCE', 43, 'DR20260916112719998', '2026-09-16 11:27:19.968112', 'REFUNDED', NULL, 5);
INSERT INTO `payments` VALUES (35, 84.90, '2026-09-16 11:46:50.391605', 'BALANCE', 44, 'DR20260916114650107', '2026-09-16 11:46:50.391605', 'REFUNDED', NULL, 5);
INSERT INTO `payments` VALUES (36, 879.00, '2026-09-16 12:00:47.946644', 'BALANCE', 42, 'DR20260916112224719', '2026-09-16 12:00:47.946644', 'SUCCESS', NULL, 5);
INSERT INTO `payments` VALUES (39, 9.00, '2026-09-16 12:11:59.942031', 'BALANCE', 45, 'DR20260916121153337', '2026-09-16 12:11:59.942031', 'REFUNDED', NULL, 5);
INSERT INTO `payments` VALUES (40, 4.50, '2026-09-16 12:12:31.903746', 'WECHAT', 46, 'DR20260916121226127', '2026-09-16 12:12:31.903746', 'REFUNDED', NULL, 4);
INSERT INTO `payments` VALUES (41, 19.90, '2026-09-16 12:13:26.492779', 'BALANCE', 48, 'DR20260916121314859', '2026-09-16 12:13:26.492779', 'SUCCESS', NULL, 6);
INSERT INTO `payments` VALUES (42, 9.00, '2026-09-16 12:13:38.529621', 'BALANCE', 50, 'DR20260916121335493', '2026-09-16 12:13:38.529621', 'SUCCESS', NULL, 6);
INSERT INTO `payments` VALUES (43, 4.50, '2026-09-16 12:13:49.891688', 'BALANCE', 51, 'DR20260916121347646', '2026-09-16 12:13:49.891688', 'SUCCESS', NULL, 6);
INSERT INTO `payments` VALUES (44, 9.00, '2026-09-16 12:45:18.515402', 'BALANCE', 52, 'DR20260916124515337', '2026-09-16 12:45:18.514392', 'REFUNDED', NULL, 5);
INSERT INTO `payments` VALUES (45, 4.50, '2026-09-16 12:45:29.402006', 'WECHAT', 53, 'DR20260916124527232', '2026-09-16 12:45:29.402006', 'REFUNDED', NULL, 4);
INSERT INTO `payments` VALUES (46, 109.80, '2026-09-16 19:53:51.185278', 'BALANCE', 54, 'DR20260916195351194', '2026-09-16 19:53:51.185278', 'SUCCESS', NULL, 2);
INSERT INTO `payments` VALUES (47, 94.80, '2026-09-18 10:33:15.498551', 'BALANCE', 58, 'DR20260918103315261', '2026-09-18 10:33:15.498551', 'SUCCESS', NULL, 2);

-- ----------------------------
-- Table structure for permissions
-- ----------------------------
DROP TABLE IF EXISTS `permissions`;
CREATE TABLE `permissions`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `created_at` datetime(6) NULL DEFAULT NULL,
  `module` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `parent_id` bigint NOT NULL,
  `sort_order` int NOT NULL,
  `status` int NOT NULL,
  `type` enum('API','BUTTON','MENU') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `updated_at` datetime(6) NULL DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `UK7lcb6glmvwlro3p2w2cewxtvd`(`code` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 12 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of permissions
-- ----------------------------
INSERT INTO `permissions` VALUES (1, 'product:view', '2026-09-08 09:04:22.028656', '商品', '查看商品', 0, 1, 1, 'MENU', '2026-09-08 09:04:22.028656');
INSERT INTO `permissions` VALUES (2, 'product:edit', '2026-09-08 09:04:22.029664', '商品', '编辑商品', 0, 2, 1, 'MENU', '2026-09-08 09:04:22.029664');
INSERT INTO `permissions` VALUES (3, 'order:view', '2026-09-08 09:04:22.030719', '订单', '查看订单', 0, 3, 1, 'MENU', '2026-09-08 09:04:22.030719');
INSERT INTO `permissions` VALUES (4, 'order:ship', '2026-09-08 09:04:22.031727', '订单', '订单发货', 0, 4, 1, 'MENU', '2026-09-08 09:04:22.031727');
INSERT INTO `permissions` VALUES (5, 'member:view', '2026-09-08 09:04:22.032747', '会员', '查看会员', 0, 5, 1, 'MENU', '2026-09-08 09:04:22.032747');
INSERT INTO `permissions` VALUES (6, 'promotion:manage', '2026-09-08 09:04:22.032747', '促销', '促销管理', 0, 6, 1, 'MENU', '2026-09-08 09:04:22.032747');
INSERT INTO `permissions` VALUES (7, 'review:reply', '2026-09-08 09:04:22.033762', '评论', '评论回复', 0, 7, 1, 'MENU', '2026-09-08 09:04:22.033762');
INSERT INTO `permissions` VALUES (8, 'review:analyze', '2026-09-08 09:04:22.034782', '评论', 'AI 情感分析', 0, 8, 1, 'MENU', '2026-09-08 09:04:22.034782');
INSERT INTO `permissions` VALUES (9, 'risk:view', NULL, '风控', '风险监控', 0, 9, 1, 'MENU', NULL);
INSERT INTO `permissions` VALUES (10, 'risk:scan', NULL, '风控', '风险扫描', 0, 10, 1, 'MENU', NULL);
INSERT INTO `permissions` VALUES (11, 'cs:manage', '2026-09-15 21:26:32.949562', '客服', '客服管理', 0, 11, 1, 'MENU', '2026-09-15 21:26:32.949562');

-- ----------------------------
-- Table structure for products
-- ----------------------------
DROP TABLE IF EXISTS `products`;
CREATE TABLE `products`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `category_id` bigint NOT NULL,
  `created_at` datetime(6) NULL DEFAULT NULL,
  `description` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL,
  `image_url` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `images` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL,
  `name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `original_price` decimal(10, 2) NULL DEFAULT NULL,
  `price` decimal(10, 2) NOT NULL,
  `sales` int NOT NULL,
  `status` int NOT NULL,
  `stock` int NOT NULL,
  `subtitle` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `unit` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `updated_at` datetime(6) NULL DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 15 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of products
-- ----------------------------
INSERT INTO `products` VALUES (1, 9, '2026-09-07 16:42:59.619729', '精选山东烟台红富士苹果，个大皮薄，果肉脆甜。', 'p002', NULL, '红富士苹果', 12.90, 9.90, 0, 0, 60, '脆甜多汁 产地直供', '斤', '2026-10-23 12:55:54.653673');
INSERT INTO `products` VALUES (2, 9, '2026-09-07 16:42:59.623131', '江西赣南脐橙，酸甜可口，富含维生素C。', 'p003', NULL, '赣南脐橙', 8.80, 6.80, 2, 1, 828, '皮薄肉嫩 汁水丰盈', '斤', '2026-10-22 16:24:05.912222');
INSERT INTO `products` VALUES (3, 10, '2026-09-07 16:42:59.623131', '有机种植菠菜，鲜嫩无农药残留。', 'p007', NULL, '有机菠菜', 5.50, 4.50, 5, 1, 295, '有机认证 现摘现发', '把', '2026-10-22 14:55:07.683539');
INSERT INTO `products` VALUES (4, 11, '2026-09-07 16:42:59.624633', '当日现宰五花肉，肥瘦均匀。', 'p011', NULL, '五花肉', 23.90, 19.90, 2, 1, 198, '肥瘦相间 红烧首选', '斤', '2026-10-22 15:33:11.614580');
INSERT INTO `products` VALUES (5, 12, '2026-09-07 16:42:59.625651', '农家散养土鸡蛋，营养丰富。', 'p012', NULL, '农家土鸡蛋', 19.90, 15.90, 2, 1, 398, '散养土鸡 蛋黄饱满', '盒', '2026-09-16 12:12:50.978742');
INSERT INTO `products` VALUES (6, 14, '2026-09-07 16:42:59.626703', '鲜活基围虾，当天捕捞当天送。', 'p059', NULL, '基围虾', 49.90, 39.90, 2, 1, 148, '鲜活直送 肉质Q弹', '斤', '2026-09-16 19:53:51.125060');
INSERT INTO `products` VALUES (7, 15, '2026-09-07 16:42:59.627728', '五常稻花香大米，香糯可口。', 'p029', NULL, '东北大米', 69.90, 59.90, 5, 1, 595, '五常稻花香 颗粒饱满', '袋', '2026-10-22 15:40:28.295136');
INSERT INTO `products` VALUES (8, 16, '2026-09-07 16:42:59.628768', '一级压榨花生油，非转基因。', 'p030', NULL, '花生油', 99.90, 89.90, 28, 1, 272, '物理压榨 香浓醇厚', '桶', '2026-10-22 15:39:06.002645');
INSERT INTO `products` VALUES (9, 17, '2026-09-07 16:42:59.630271', '高品质纯牛奶，每100ml含3.6g优质蛋白。', 'p015', NULL, '纯牛奶', 55.90, 45.90, 2, 1, 498, '牧场直供 3.6g蛋白', '箱', '2026-10-22 15:40:27.664688');
INSERT INTO `products` VALUES (10, 19, '2026-09-07 16:42:59.631749', '丹麦风味黄油曲奇饼干。', 'p016', NULL, '曲奇饼干', 25.90, 19.90, 3, 1, 697, '黄油曲奇 酥脆香甜', '盒', '2026-10-22 15:23:03.178178');
INSERT INTO `products` VALUES (11, 21, '2026-09-07 16:42:59.632824', '深层岩层天然矿泉水，弱碱性。', 'p067', NULL, '天然矿泉水', 2.50, 2.00, 8, 1, 1992, '弱碱性 深层岩层水', '瓶', '2026-09-15 22:39:16.122735');
INSERT INTO `products` VALUES (12, 23, '2026-09-14 18:06:40.892913', '维达超韧系列抽纸，3层加厚不易破，整箱装。', 'p072', NULL, '维达超韧抽纸', 49.90, 39.90, 2, 1, 398, '3层加厚 整箱装', '箱', '2026-09-15 19:48:41.089086');
INSERT INTO `products` VALUES (13, 23, '2026-09-14 18:06:40.895293', '清风原木纯品抽纸，100%原生木浆，亲肤柔韧。', 'p033', NULL, '清风原木抽纸', 36.90, 29.90, 3, 1, 497, '原生木浆 整箱装', '箱', '2026-09-15 22:39:40.747853');
INSERT INTO `products` VALUES (14, 24, '2026-09-14 18:06:40.896235', '蓝月亮深层洁净洗衣液，低泡易漂，护色护衣。', 'p073', NULL, '蓝月亮洗衣液', 59.90, 49.90, 4, 1, 296, '深层洁净 3kg', '瓶', '2026-09-18 10:33:15.425648');

-- ----------------------------
-- Table structure for promotions
-- ----------------------------
DROP TABLE IF EXISTS `promotions`;
CREATE TABLE `promotions`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NULL DEFAULT NULL,
  `discount` decimal(10, 4) NOT NULL,
  `end_time` datetime(6) NULL DEFAULT NULL,
  `name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `product_id` bigint NULL DEFAULT NULL,
  `start_time` datetime(6) NULL DEFAULT NULL,
  `status` int NOT NULL,
  `threshold` decimal(10, 2) NOT NULL,
  `type` enum('DISCOUNT','FULL_REDUCTION') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `description` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `image_url` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `link` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 14 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of promotions
-- ----------------------------
INSERT INTO `promotions` VALUES (1, '2026-09-08 09:39:25.125503', 10.0000, '2026-12-31 23:59:00.000000', '满 100 减 10（全场通用）', NULL, '2026-10-01 00:00:00.000000', 1, 100.00, 'FULL_REDUCTION', NULL, NULL, NULL);
INSERT INTO `promotions` VALUES (2, '2026-09-08 09:39:25.142319', 5.0000, '2026-12-31 23:59:00.000000', '新人立减 5 元（满 50 可用）', NULL, '2026-10-01 00:00:00.000000', 1, 50.00, 'FULL_REDUCTION', NULL, NULL, NULL);
INSERT INTO `promotions` VALUES (3, '2026-09-08 09:39:25.147481', 0.8000, '2026-09-15 09:39:25.147481', '东北大米限时 8 折', 7, '2026-09-06 09:39:25.147481', 1, 0.00, 'DISCOUNT', NULL, NULL, NULL);
INSERT INTO `promotions` VALUES (4, '2026-09-16 09:47:26.265962', 20.0000, '2026-12-31 23:59:00.000000', '满500减20', NULL, '2026-10-01 00:00:00.000000', 1, 500.00, 'FULL_REDUCTION', NULL, NULL, NULL);
INSERT INTO `promotions` VALUES (5, '2026-10-10 19:51:17.092883', 30.0000, '2026-12-31 23:59:00.000000', '满 200 减 30（全场通用）', NULL, '2026-10-01 00:00:00.000000', 0, 200.00, 'FULL_REDUCTION', NULL, NULL, NULL);
INSERT INTO `promotions` VALUES (12, '2026-10-27 20:14:25.082566', 60.0000, '2026-11-11 23:59:59.000000', '全场满100减60（11.01-11.11）', NULL, '2026-11-01 00:00:00.000000', 1, 100.00, 'FULL_REDUCTION', '全场通用满减活动：单笔满 100 元减 60 元，有效期 2026-11-01 至 2026-11-11。', NULL, NULL);
INSERT INTO `promotions` VALUES (13, '2026-10-29 14:37:53.028532', 50.0000, '2026-11-30 23:59:59.000000', '全场满200减50（11.11-11.30）', NULL, '2026-11-11 00:00:00.000000', 1, 200.00, 'FULL_REDUCTION', '全店通用满200元减50元，活动期 2026-11-11 至 2026-11-30。', NULL, NULL);

-- ----------------------------
-- Table structure for refund_requests
-- ----------------------------
DROP TABLE IF EXISTS `refund_requests`;
CREATE TABLE `refund_requests`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `amount` decimal(10, 2) NOT NULL,
  `created_at` datetime(6) NULL DEFAULT NULL,
  `handled_at` datetime(6) NULL DEFAULT NULL,
  `handled_by` bigint NULL DEFAULT NULL,
  `order_id` bigint NOT NULL,
  `order_no` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `previous_status` enum('CANCELLED','COMPLETED','PAID','PENDING_PAYMENT','REFUNDED','REFUNDING','SHIPPED') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `reason` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `reject_reason` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `status` enum('APPROVED','PENDING','REJECTED') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 19 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of refund_requests
-- ----------------------------
INSERT INTO `refund_requests` VALUES (1, 33.50, '2026-09-15 22:36:05.799300', '2026-09-15 22:36:06.429692', 1, 16, 'DR20260915194246105', 'PAID', '商品不想要了', NULL, 'APPROVED', 5);
INSERT INTO `refund_requests` VALUES (2, 49.50, '2026-09-15 22:39:07.584764', '2026-09-15 22:39:08.067320', 1, 1, 'O20260908001', 'SHIPPED', '买错了', '商品已发出，请签收后申请退货', 'REJECTED', 5);
INSERT INTO `refund_requests` VALUES (3, 49.50, '2026-09-15 22:39:15.803628', '2026-09-15 22:39:16.121625', 1, 1, 'O20260908001', 'SHIPPED', '还是不想要了', NULL, 'APPROVED', 5);
INSERT INTO `refund_requests` VALUES (4, 45.90, '2026-09-15 22:42:36.873356', '2026-09-15 22:44:36.991470', 1, 21, 'DR20260915222259990', 'PAID', 'bought the wrong item', NULL, 'APPROVED', 2);
INSERT INTO `refund_requests` VALUES (5, 139.70, '2026-09-15 23:06:59.011957', '2026-09-16 08:30:08.559900', 1, 20, 'DR20260915204022226', 'SHIPPED', '不想要了', NULL, 'APPROVED', 2);
INSERT INTO `refund_requests` VALUES (6, 79.60, '2026-09-16 09:01:18.229097', '2026-10-22 15:06:30.218456', 1, 2, 'O20260908002', 'SHIPPED', '发货太晚了', NULL, 'APPROVED', 2);
INSERT INTO `refund_requests` VALUES (8, 66.00, '2026-09-16 11:06:10.654539', '2026-09-16 11:06:12.659958', 1, 32, 'SELFTEST-REFUND', 'PAID', '自测：余额支付退款返现', NULL, 'APPROVED', 5);
INSERT INTO `refund_requests` VALUES (9, 84.90, '2026-09-16 11:21:47.763885', '2026-09-16 11:21:53.484079', 1, 35, 'DR20260916112115932', 'PAID', '质量问题申请退款', NULL, 'APPROVED', 5);
INSERT INTO `refund_requests` VALUES (10, 84.90, '2026-09-16 11:46:50.778788', '2026-09-16 11:46:51.124741', 6, 44, 'DR20260916114650107', 'PAID', '越权验证', NULL, 'APPROVED', 5);
INSERT INTO `refund_requests` VALUES (11, 9.00, '2026-09-16 12:12:07.431548', '2026-09-16 12:12:14.528416', 1, 45, 'DR20260916121153337', 'PAID', 'QA验证-正常审批', NULL, 'APPROVED', 5);
INSERT INTO `refund_requests` VALUES (12, 4.50, '2026-09-16 12:12:33.348434', '2026-10-22 14:54:40.354789', 1, 46, 'DR20260916121226127', 'PAID', 'QA自审用例', NULL, 'APPROVED', 4);
INSERT INTO `refund_requests` VALUES (13, 9.00, '2026-09-16 12:45:21.329378', '2026-09-16 12:45:23.824635', 1, 52, 'DR20260916124515337', 'PAID', 'QA复验', NULL, 'APPROVED', 5);
INSERT INTO `refund_requests` VALUES (14, 4.50, '2026-09-16 12:45:31.242814', '2026-10-22 14:55:07.682473', 1, 53, 'DR20260916124527232', 'PAID', 'QA复验自审', NULL, 'APPROVED', 4);
INSERT INTO `refund_requests` VALUES (15, 19.90, '2026-10-22 15:22:24.604157', '2026-10-22 15:23:03.176919', 3, 43, 'DR20260916112719998', 'PAID', 'QA独立验证-退款审批链路', NULL, 'APPROVED', 5);
INSERT INTO `refund_requests` VALUES (16, 47.92, '2026-10-22 15:22:32.193368', '2026-10-22 15:25:58.816874', 1, 3, 'O20260908003', 'SHIPPED', 'QA独立验证-禁止自审', 'QA验证后回滚-禁止自审用例', 'REJECTED', 4);
INSERT INTO `refund_requests` VALUES (17, 879.00, '2026-10-22 15:38:35.470927', '2026-10-22 15:38:36.130465', 1, 42, 'DR20260916112224719', 'PAID', 'QA2回归-退款拒绝', 'QA2回归-拒绝并回滚', 'REJECTED', 5);
INSERT INTO `refund_requests` VALUES (18, 84.90, '2026-10-22 15:38:47.363544', '2026-10-22 15:39:06.002645', 3, 38, 'DR20260916112135320', 'PAID', 'QA2回归-退款同意', NULL, 'APPROVED', 5);

-- ----------------------------
-- Table structure for review_replies
-- ----------------------------
DROP TABLE IF EXISTS `review_replies`;
CREATE TABLE `review_replies`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `content` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `created_at` datetime(6) NULL DEFAULT NULL,
  `review_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `user_name` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `reply_type` enum('MERCHANT','USER') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 3 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of review_replies
-- ----------------------------
INSERT INTO `review_replies` VALUES (1, '感谢您的反馈，非常抱歉给您带来不便，我们已安排专人为您处理，可联系客服领取补偿券。', '2026-09-15 21:27:40.712841', 1, 1, '官方客服（管理员）', 'MERCHANT');
INSERT INTO `review_replies` VALUES (2, 'Sorry for the delay， we will improve logistics。', '2026-09-15 22:02:02.803846', 2, 1, '官方客服（管理员）', 'MERCHANT');

-- ----------------------------
-- Table structure for reviews
-- ----------------------------
DROP TABLE IF EXISTS `reviews`;
CREATE TABLE `reviews`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `content` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `created_at` datetime(6) NULL DEFAULT NULL,
  `product_id` bigint NOT NULL,
  `rating` decimal(2, 1) NOT NULL,
  `user_id` bigint NOT NULL,
  `user_name` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `ai_analyzed_at` datetime(6) NULL DEFAULT NULL,
  `ai_reason` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `ai_sentiment` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `ai_urgency` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 14 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of reviews
-- ----------------------------
INSERT INTO `reviews` VALUES (1, '脆甜好吃，果香浓郁，包装也很严实。已回购多次，强烈推荐！', '2026-09-08 09:39:25.174563', 1, 5.0, 5, 'testuser', '2026-10-08 15:42:39.113257', '五星好评，称脆甜好吃、包装严实并多次回购推荐', '正面', '普通');
INSERT INTO `reviews` VALUES (2, '东北大米确实香，煮饭颗粒分明。物流稍微慢了一天。', '2026-09-08 09:39:25.176577', 7, 4.0, 4, 'csuser', '2026-09-06 09:39:25.175571', '对商品品质满意但对物流时效存在轻微不满，整体中性。', '中性', '普通');
INSERT INTO `reviews` VALUES (3, '五花肉肥瘦不太均匀，有一块肥肉偏多。', '2026-09-08 09:39:25.178585', 4, 3.0, 2, '12', '2026-10-29 14:36:04.310786', '3分评价指出五花肉肥瘦不均、有块肥肉偏多，属一般品质抱怨，无退换或安全诉求', '负面', '普通');
INSERT INTO `reviews` VALUES (4, '纯牛奶味道不错，小孩很爱喝，日期新鲜。', '2026-09-08 09:39:25.179672', 9, 5.0, 3, 'opsuser', '2026-10-29 15:20:28.287341', '五星好评，认可味道和日期新鲜，无负面问题', '正面', '普通');
INSERT INTO `reviews` VALUES (5, '喝起来很清爽', '2026-09-11 10:27:48.001433', 11, 5.0, 2, '12', '2026-09-18 10:42:12.838301', '用户给出五星好评，评价口感清爽', '正面', '普通');
INSERT INTO `reviews` VALUES (8, 'Fresh tast，e my kid loves 。it', '2026-09-15 21:45:56.193697', 9, 5.0, 5, '测试用户', '2026-10-10 19:04:38.079793', '五星好评，称味道新鲜且孩子喜欢', '正面', '普通');
INSERT INTO `reviews` VALUES (9, 'Good oil， rich flavor', '2026-09-15 21:47:01.192100', 8, 5.0, 5, '测试用户', '2026-10-10 21:28:43.503356', '五星好评，称油品质好、风味浓郁，无负面信息', '正面', '普通');
INSERT INTO `reviews` VALUES (10, '香味很清新不冲鼻，洗完的衣服软软的，贴身衣物也敢给宝宝穿。用量也省，半瓶盖就够洗一桶，会回购。', '2026-09-16 09:58:23.402054', 14, 5.0, 5, '测试用户', '2026-10-08 15:21:21.348417', '香味温和、洗后柔软、用量省且愿意回购', '正面', '普通');
INSERT INTO `reviews` VALUES (11, '去污力还可以，孩子校服袖口的汗渍泡十分钟基本能洗掉。就是瓶口没有导流槽，倒的时候容易多倒一点。', '2026-09-16 09:58:23.483866', 14, 4.0, 4, '客服小李', '2026-10-08 15:21:19.010717', '去污效果获认可，仅瓶口设计有小不便', '正面', '普通');
INSERT INTO `reviews` VALUES (12, '中规中矩，日常汗味能洗掉，但领口的陈年油渍还是要先搓一遍。泡沫偏多，漂洗比平时多过一遍水，性价比一般。', '2026-09-16 09:58:23.561789', 14, 3.0, 3, '运营小王', '2026-10-08 15:21:02.886773', '中规中矩，有清洁和漂洗小缺点但无强烈负面或维权', '中性', '普通');
INSERT INTO `reviews` VALUES (13, '收到的这瓶瓶身有渗漏，外箱湿了一角，瓶盖也没拧紧。香味本身还行，但漏液的问题得给个说法。', '2026-09-16 09:58:23.635572', 14, 2.0, 6, '风险测试账户', '2026-10-10 21:28:42.618072', '收到货瓶身渗漏、外箱湿，用户要求给说法，有维权意愿', '投诉', '紧急');

-- ----------------------------
-- Table structure for risk_scores
-- ----------------------------
DROP TABLE IF EXISTS `risk_scores`;
CREATE TABLE `risk_scores`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NULL DEFAULT NULL,
  `is_read` bit(1) NOT NULL,
  `level` enum('HIGH','LOW','MEDIUM') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `order_id` bigint NOT NULL,
  `reasons` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `score` int NOT NULL,
  `source` enum('HYBRID','LLM','RULE') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `suggested_action` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_rs_order`(`order_id` ASC) USING BTREE,
  INDEX `idx_rs_level`(`level` ASC) USING BTREE,
  INDEX `idx_rs_score`(`score` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 76 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of risk_scores
-- ----------------------------
INSERT INTO `risk_scores` VALUES (1, '2026-09-08 10:32:29.293775', b'1', 'LOW', 2, '{\"ruleScore\":0,\"signals\":[],\"llmSummary\":\"规则引擎未命中任何风险信号，订单金额较小，收货地址完整且为正常商务地址，未见明显异常。\",\"llmAction\":\"放行\"}', 5, 'HYBRID', '放行');
INSERT INTO `risk_scores` VALUES (2, '2026-09-08 10:32:39.408380', b'1', 'LOW', 2, '{\"ruleScore\":0,\"signals\":[]}', 0, 'RULE', '放行');
INSERT INTO `risk_scores` VALUES (3, '2026-09-08 10:32:46.721547', b'1', 'LOW', 1, '{\"ruleScore\":0,\"signals\":[]}', 0, 'RULE', '放行');
INSERT INTO `risk_scores` VALUES (4, '2026-09-08 10:33:00.021058', b'1', 'LOW', 3, '{\"ruleScore\":0,\"signals\":[],\"llmSummary\":\"规则引擎未命中风险信号，订单金额较小，收货地址完整且收件人信息与用户一致，暂无明显欺诈特征。\",\"llmAction\":\"放行\"}', 5, 'HYBRID', '放行');
INSERT INTO `risk_scores` VALUES (5, '2026-09-08 10:33:14.714057', b'1', 'LOW', 4, '{\"ruleScore\":0,\"signals\":[]}', 0, 'RULE', '放行');
INSERT INTO `risk_scores` VALUES (6, '2026-09-08 10:33:15.668241', b'1', 'LOW', 5, '{\"ruleScore\":0,\"signals\":[]}', 0, 'RULE', '放行');
INSERT INTO `risk_scores` VALUES (7, '2026-09-08 10:33:16.783171', b'1', 'LOW', 1, '{\"ruleScore\":0,\"signals\":[]}', 0, 'RULE', '放行');
INSERT INTO `risk_scores` VALUES (8, '2026-09-08 10:54:01.943636', b'0', 'HIGH', 9, '{\"ruleScore\":85,\"signals\":[{\"code\":\"FIRST_BIG\",\"weight\":25,\"message\":\"新账户首单金额 1888.00 > 500（首单大额）\"},{\"code\":\"LATE_NIGHT\",\"weight\":10,\"message\":\"下单时间 02:45 属于凌晨时段（0-5 点）\"},{\"code\":\"ADDRESS_SUSPICIOUS\",\"weight\":15,\"message\":\"收货地址含敏感词「快递柜」（疑似代收/快递柜）\"},{\"code\":\"BURST_ORDERS\",\"weight\":35,\"message\":\"最近 1 小时内该用户已下 4 单\"}]}', 85, 'RULE', '暂停发货');
INSERT INTO `risk_scores` VALUES (9, '2026-09-09 09:24:04.687964', b'1', 'LOW', 6, '{\"ruleScore\":15,\"signals\":[{\"code\":\"ADDRESS_SUSPICIOUS\",\"message\":\"收货地址含敏感词「快递柜」（疑似代收/快递柜）\",\"weight\":15}]}', 15, 'RULE', '放行');
INSERT INTO `risk_scores` VALUES (10, '2026-09-11 09:35:36.216588', b'1', 'LOW', 10, '{\"ruleScore\":0,\"signals\":[]}', 0, 'RULE', '放行');
INSERT INTO `risk_scores` VALUES (11, '2026-09-14 19:45:05.217744', b'0', 'LOW', 13, '{\"ruleScore\":0,\"signals\":[]}', 0, 'RULE', '放行');
INSERT INTO `risk_scores` VALUES (12, '2026-09-16 12:09:17.248350', b'0', 'LOW', 7, '{\"ruleScore\":15,\"signals\":[{\"code\":\"ADDRESS_SUSPICIOUS\",\"message\":\"收货地址含敏感词「菜鸟驿站」（疑似代收/快递柜）\",\"weight\":15}]}', 15, 'RULE', '放行');
INSERT INTO `risk_scores` VALUES (13, '2026-09-16 12:09:25.965571', b'0', 'LOW', 8, '{\"ruleScore\":15,\"signals\":[{\"code\":\"ADDRESS_SUSPICIOUS\",\"message\":\"收货地址含敏感词「中转站」（疑似代收/快递柜）\",\"weight\":15}]}', 15, 'RULE', '放行');
INSERT INTO `risk_scores` VALUES (14, '2026-09-16 12:09:33.239871', b'0', 'LOW', 24, '{\"ruleScore\":0,\"signals\":[],\"llmSummary\":\"规则引擎未命中任何风险信号，订单金额较低且为正常商品数量，收货地址为高校地址，整体行为符合日常消费特征。\",\"llmAction\":\"放行\"}', 5, 'HYBRID', '放行');
INSERT INTO `risk_scores` VALUES (15, '2026-09-16 12:09:41.679296', b'0', 'LOW', 25, '{\"ruleScore\":35,\"signals\":[{\"code\":\"BURST_ORDERS\",\"message\":\"最近 1 小时内该用户已下 10 单\",\"weight\":35}]}', 35, 'RULE', '放行');
INSERT INTO `risk_scores` VALUES (16, '2026-09-16 12:09:49.122789', b'0', 'LOW', 7, '{\"ruleScore\":15,\"signals\":[{\"code\":\"ADDRESS_SUSPICIOUS\",\"message\":\"收货地址含敏感词「菜鸟驿站」（疑似代收/快递柜）\",\"weight\":15}]}', 15, 'RULE', '放行');
INSERT INTO `risk_scores` VALUES (17, '2026-09-16 12:09:50.193261', b'0', 'LOW', 26, '{\"ruleScore\":0,\"signals\":[]}', 0, 'RULE', '放行');
INSERT INTO `risk_scores` VALUES (18, '2026-09-16 12:09:58.929899', b'0', 'LOW', 27, '{\"ruleScore\":35,\"signals\":[{\"code\":\"BURST_ORDERS\",\"message\":\"最近 1 小时内该用户已下 10 单\",\"weight\":35}]}', 35, 'RULE', '放行');
INSERT INTO `risk_scores` VALUES (19, '2026-09-16 12:10:06.212318', b'0', 'LOW', 28, '{\"ruleScore\":0,\"signals\":[],\"llmSummary\":\"规则引擎未命中风险信号，订单金额较低且信息完整，收件地址为高校常见收货场景，整体无异常。\",\"llmAction\":\"放行\"}', 5, 'HYBRID', '放行');
INSERT INTO `risk_scores` VALUES (20, '2026-09-16 12:10:11.654215', b'0', 'LOW', 29, '{\"ruleScore\":0,\"signals\":[],\"llmSummary\":\"订单金额较低，规则引擎未命中任何异常信号，收货地址为高校正常收件场景，整体风险低。\",\"llmAction\":\"放行\"}', 5, 'HYBRID', '放行');
INSERT INTO `risk_scores` VALUES (21, '2026-09-16 12:10:19.694565', b'0', 'LOW', 11, '{\"ruleScore\":0,\"signals\":[]}', 0, 'RULE', '放行');
INSERT INTO `risk_scores` VALUES (22, '2026-09-16 12:10:28.132728', b'0', 'LOW', 12, '{\"ruleScore\":0,\"signals\":[]}', 0, 'RULE', '放行');
INSERT INTO `risk_scores` VALUES (23, '2026-09-16 12:10:36.082931', b'0', 'LOW', 14, '{\"ruleScore\":0,\"signals\":[]}', 0, 'RULE', '放行');
INSERT INTO `risk_scores` VALUES (24, '2026-09-16 12:10:44.724503', b'0', 'LOW', 15, '{\"ruleScore\":0,\"signals\":[]}', 0, 'RULE', '放行');
INSERT INTO `risk_scores` VALUES (25, '2026-09-16 12:10:53.160679', b'0', 'LOW', 16, '{\"ruleScore\":35,\"signals\":[{\"code\":\"BURST_ORDERS\",\"message\":\"最近 1 小时内该用户已下 10 单\",\"weight\":35}]}', 35, 'RULE', '放行');
INSERT INTO `risk_scores` VALUES (26, '2026-09-16 12:11:02.262525', b'0', 'LOW', 17, '{\"ruleScore\":0,\"signals\":[]}', 0, 'RULE', '放行');
INSERT INTO `risk_scores` VALUES (27, '2026-09-16 12:11:10.358918', b'0', 'LOW', 18, '{\"ruleScore\":35,\"signals\":[{\"code\":\"BURST_ORDERS\",\"message\":\"最近 1 小时内该用户已下 10 单\",\"weight\":35}]}', 35, 'RULE', '放行');
INSERT INTO `risk_scores` VALUES (28, '2026-09-16 12:11:19.347700', b'0', 'LOW', 19, '{\"ruleScore\":35,\"signals\":[{\"code\":\"BURST_ORDERS\",\"message\":\"最近 1 小时内该用户已下 10 单\",\"weight\":35}]}', 35, 'RULE', '放行');
INSERT INTO `risk_scores` VALUES (29, '2026-09-16 12:11:27.351965', b'0', 'LOW', 20, '{\"ruleScore\":0,\"signals\":[]}', 0, 'RULE', '放行');
INSERT INTO `risk_scores` VALUES (30, '2026-09-16 12:11:32.014720', b'0', 'LOW', 21, '{\"ruleScore\":0,\"signals\":[],\"llmSummary\":\"规则引擎未识别异常信号，订单金额较低，收货地址为学校地址，整体交易特征正常。\",\"llmAction\":\"放行\"}', 5, 'HYBRID', '放行');
INSERT INTO `risk_scores` VALUES (31, '2026-09-16 12:11:40.664065', b'0', 'LOW', 22, '{\"ruleScore\":35,\"signals\":[{\"code\":\"BURST_ORDERS\",\"message\":\"最近 1 小时内该用户已下 10 单\",\"weight\":35}]}', 35, 'RULE', '放行');
INSERT INTO `risk_scores` VALUES (32, '2026-09-16 12:11:50.558504', b'0', 'LOW', 23, '{\"ruleScore\":35,\"signals\":[{\"code\":\"BURST_ORDERS\",\"message\":\"最近 1 小时内该用户已下 10 单\",\"weight\":35}]}', 35, 'RULE', '放行');
INSERT INTO `risk_scores` VALUES (33, '2026-09-16 12:11:58.357715', b'0', 'LOW', 30, '{\"ruleScore\":35,\"signals\":[{\"code\":\"BURST_ORDERS\",\"message\":\"最近 1 小时内该用户已下 10 单\",\"weight\":35}]}', 35, 'RULE', '放行');
INSERT INTO `risk_scores` VALUES (34, '2026-09-16 12:12:06.030575', b'0', 'MEDIUM', 31, '{\"ruleScore\":65,\"signals\":[{\"code\":\"AMOUNT_HIGH\",\"message\":\"支付金额 99999.00 超过该用户历史订单 P95 (879.00) 的 2 倍\",\"weight\":30},{\"code\":\"BURST_ORDERS\",\"message\":\"最近 1 小时内该用户已下 10 单\",\"weight\":35}]}', 65, 'RULE', '人工审核');
INSERT INTO `risk_scores` VALUES (35, '2026-09-16 12:12:14.432779', b'0', 'LOW', 32, '{\"ruleScore\":35,\"signals\":[{\"code\":\"BURST_ORDERS\",\"message\":\"最近 1 小时内该用户已下 10 单\",\"weight\":35}]}', 35, 'RULE', '放行');
INSERT INTO `risk_scores` VALUES (36, '2026-09-16 12:12:23.248998', b'0', 'LOW', 34, '{\"ruleScore\":35,\"signals\":[{\"code\":\"BURST_ORDERS\",\"message\":\"最近 1 小时内该用户已下 10 单\",\"weight\":35}]}', 35, 'RULE', '放行');
INSERT INTO `risk_scores` VALUES (37, '2026-09-16 12:12:32.908320', b'0', 'LOW', 35, '{\"ruleScore\":35,\"signals\":[{\"code\":\"BURST_ORDERS\",\"message\":\"最近 1 小时内该用户已下 10 单\",\"weight\":35}]}', 35, 'RULE', '放行');
INSERT INTO `risk_scores` VALUES (38, '2026-09-16 12:12:41.220842', b'0', 'LOW', 36, '{\"ruleScore\":0,\"signals\":[]}', 0, 'RULE', '放行');
INSERT INTO `risk_scores` VALUES (39, '2026-09-16 12:12:50.315783', b'0', 'LOW', 37, '{\"ruleScore\":35,\"signals\":[{\"code\":\"BURST_ORDERS\",\"message\":\"最近 1 小时内该用户已下 10 单\",\"weight\":35}]}', 35, 'RULE', '放行');
INSERT INTO `risk_scores` VALUES (40, '2026-09-16 12:12:59.300909', b'0', 'LOW', 38, '{\"ruleScore\":35,\"signals\":[{\"code\":\"BURST_ORDERS\",\"message\":\"最近 1 小时内该用户已下 10 单\",\"weight\":35}]}', 35, 'RULE', '放行');
INSERT INTO `risk_scores` VALUES (41, '2026-09-16 12:13:07.684977', b'0', 'LOW', 39, '{\"ruleScore\":35,\"signals\":[{\"code\":\"BURST_ORDERS\",\"message\":\"最近 1 小时内该用户已下 10 单\",\"weight\":35}]}', 35, 'RULE', '放行');
INSERT INTO `risk_scores` VALUES (42, '2026-09-16 12:13:15.620472', b'0', 'LOW', 40, '{\"ruleScore\":35,\"signals\":[{\"code\":\"BURST_ORDERS\",\"message\":\"最近 1 小时内该用户已下 10 单\",\"weight\":35}]}', 35, 'RULE', '放行');
INSERT INTO `risk_scores` VALUES (43, '2026-09-16 12:13:23.449081', b'0', 'LOW', 27, '{\"ruleScore\":35,\"signals\":[{\"code\":\"BURST_ORDERS\",\"message\":\"最近 1 小时内该用户已下 10 单\",\"weight\":35}]}', 35, 'RULE', '放行');
INSERT INTO `risk_scores` VALUES (44, '2026-09-16 12:13:31.907055', b'0', 'LOW', 28, '{\"ruleScore\":0,\"signals\":[]}', 0, 'RULE', '放行');
INSERT INTO `risk_scores` VALUES (45, '2026-09-16 12:13:36.761673', b'0', 'LOW', 29, '{\"ruleScore\":0,\"signals\":[],\"llmSummary\":\"订单金额较小，规则引擎未触发任何风险信号，收货地址为高校地址，信息完整且无明显异常。\",\"llmAction\":\"放行\"}', 0, 'HYBRID', '放行');
INSERT INTO `risk_scores` VALUES (46, '2026-09-16 12:13:44.893022', b'0', 'LOW', 11, '{\"ruleScore\":0,\"signals\":[]}', 0, 'RULE', '放行');
INSERT INTO `risk_scores` VALUES (47, '2026-09-16 12:13:52.888821', b'0', 'LOW', 12, '{\"ruleScore\":0,\"signals\":[]}', 0, 'RULE', '放行');
INSERT INTO `risk_scores` VALUES (48, '2026-09-16 12:14:01.648572', b'0', 'LOW', 14, '{\"ruleScore\":0,\"signals\":[]}', 0, 'RULE', '放行');
INSERT INTO `risk_scores` VALUES (49, '2026-09-16 12:14:10.053839', b'0', 'LOW', 15, '{\"ruleScore\":0,\"signals\":[]}', 0, 'RULE', '放行');
INSERT INTO `risk_scores` VALUES (50, '2026-09-16 12:14:17.640761', b'0', 'LOW', 16, '{\"ruleScore\":35,\"signals\":[{\"code\":\"BURST_ORDERS\",\"message\":\"最近 1 小时内该用户已下 10 单\",\"weight\":35}]}', 35, 'RULE', '放行');
INSERT INTO `risk_scores` VALUES (51, '2026-09-16 12:14:25.829796', b'0', 'LOW', 17, '{\"ruleScore\":0,\"signals\":[]}', 0, 'RULE', '放行');
INSERT INTO `risk_scores` VALUES (52, '2026-09-16 12:14:34.223293', b'0', 'LOW', 18, '{\"ruleScore\":35,\"signals\":[{\"code\":\"BURST_ORDERS\",\"message\":\"最近 1 小时内该用户已下 10 单\",\"weight\":35}]}', 35, 'RULE', '放行');
INSERT INTO `risk_scores` VALUES (53, '2026-09-16 12:14:42.226419', b'0', 'LOW', 19, '{\"ruleScore\":35,\"signals\":[{\"code\":\"BURST_ORDERS\",\"message\":\"最近 1 小时内该用户已下 10 单\",\"weight\":35}]}', 35, 'RULE', '放行');
INSERT INTO `risk_scores` VALUES (54, '2026-09-16 12:14:50.742944', b'0', 'LOW', 20, '{\"ruleScore\":0,\"signals\":[]}', 0, 'RULE', '放行');
INSERT INTO `risk_scores` VALUES (55, '2026-09-16 12:14:58.271385', b'0', 'LOW', 21, '{\"ruleScore\":0,\"signals\":[]}', 0, 'RULE', '放行');
INSERT INTO `risk_scores` VALUES (56, '2026-09-16 12:15:06.298736', b'0', 'LOW', 22, '{\"ruleScore\":35,\"signals\":[{\"code\":\"BURST_ORDERS\",\"message\":\"最近 1 小时内该用户已下 10 单\",\"weight\":35}]}', 35, 'RULE', '放行');
INSERT INTO `risk_scores` VALUES (57, '2026-09-16 12:15:14.159667', b'0', 'LOW', 23, '{\"ruleScore\":35,\"signals\":[{\"code\":\"BURST_ORDERS\",\"message\":\"最近 1 小时内该用户已下 10 单\",\"weight\":35}]}', 35, 'RULE', '放行');
INSERT INTO `risk_scores` VALUES (58, '2026-09-16 12:15:22.219475', b'0', 'LOW', 30, '{\"ruleScore\":35,\"signals\":[{\"code\":\"BURST_ORDERS\",\"message\":\"最近 1 小时内该用户已下 10 单\",\"weight\":35}]}', 35, 'RULE', '放行');
INSERT INTO `risk_scores` VALUES (59, '2026-09-16 12:15:30.244371', b'0', 'MEDIUM', 31, '{\"ruleScore\":65,\"signals\":[{\"code\":\"AMOUNT_HIGH\",\"message\":\"支付金额 99999.00 超过该用户历史订单 P95 (879.00) 的 2 倍\",\"weight\":30},{\"code\":\"BURST_ORDERS\",\"message\":\"最近 1 小时内该用户已下 10 单\",\"weight\":35}]}', 65, 'RULE', '人工审核');
INSERT INTO `risk_scores` VALUES (60, '2026-09-16 12:15:38.236558', b'0', 'LOW', 32, '{\"ruleScore\":35,\"signals\":[{\"code\":\"BURST_ORDERS\",\"message\":\"最近 1 小时内该用户已下 10 单\",\"weight\":35}]}', 35, 'RULE', '放行');
INSERT INTO `risk_scores` VALUES (61, '2026-09-16 12:15:46.034856', b'0', 'LOW', 34, '{\"ruleScore\":35,\"signals\":[{\"code\":\"BURST_ORDERS\",\"message\":\"最近 1 小时内该用户已下 10 单\",\"weight\":35}]}', 35, 'RULE', '放行');
INSERT INTO `risk_scores` VALUES (62, '2026-09-16 12:15:53.626826', b'0', 'LOW', 35, '{\"ruleScore\":35,\"signals\":[{\"code\":\"BURST_ORDERS\",\"message\":\"最近 1 小时内该用户已下 10 单\",\"weight\":35}]}', 35, 'RULE', '放行');
INSERT INTO `risk_scores` VALUES (63, '2026-09-16 12:16:01.149772', b'0', 'LOW', 36, '{\"ruleScore\":0,\"signals\":[]}', 0, 'RULE', '放行');
INSERT INTO `risk_scores` VALUES (64, '2026-09-16 12:16:08.897668', b'0', 'LOW', 37, '{\"ruleScore\":35,\"signals\":[{\"code\":\"BURST_ORDERS\",\"message\":\"最近 1 小时内该用户已下 10 单\",\"weight\":35}]}', 35, 'RULE', '放行');
INSERT INTO `risk_scores` VALUES (65, '2026-09-16 12:16:17.599306', b'0', 'LOW', 38, '{\"ruleScore\":35,\"signals\":[{\"code\":\"BURST_ORDERS\",\"message\":\"最近 1 小时内该用户已下 10 单\",\"weight\":35}]}', 35, 'RULE', '放行');
INSERT INTO `risk_scores` VALUES (66, '2026-09-16 12:16:26.347008', b'0', 'LOW', 39, '{\"ruleScore\":35,\"signals\":[{\"code\":\"BURST_ORDERS\",\"message\":\"最近 1 小时内该用户已下 10 单\",\"weight\":35}]}', 35, 'RULE', '放行');
INSERT INTO `risk_scores` VALUES (67, '2026-09-16 12:16:35.548046', b'0', 'LOW', 40, '{\"ruleScore\":35,\"signals\":[{\"code\":\"BURST_ORDERS\",\"message\":\"最近 1 小时内该用户已下 9 单\",\"weight\":35}]}', 35, 'RULE', '放行');
INSERT INTO `risk_scores` VALUES (68, '2026-09-16 12:16:43.931161', b'0', 'LOW', 41, '{\"ruleScore\":35,\"signals\":[{\"code\":\"BURST_ORDERS\",\"message\":\"最近 1 小时内该用户已下 9 单\",\"weight\":35}]}', 35, 'RULE', '放行');
INSERT INTO `risk_scores` VALUES (69, '2026-09-16 12:16:52.404583', b'0', 'LOW', 42, '{\"ruleScore\":35,\"signals\":[{\"code\":\"BURST_ORDERS\",\"message\":\"最近 1 小时内该用户已下 9 单\",\"weight\":35}]}', 35, 'RULE', '放行');
INSERT INTO `risk_scores` VALUES (70, '2026-09-16 12:17:00.767154', b'0', 'LOW', 43, '{\"ruleScore\":35,\"signals\":[{\"code\":\"BURST_ORDERS\",\"message\":\"最近 1 小时内该用户已下 9 单\",\"weight\":35}]}', 35, 'RULE', '放行');
INSERT INTO `risk_scores` VALUES (71, '2026-09-16 12:17:09.315090', b'1', 'LOW', 44, '{\"ruleScore\":35,\"signals\":[{\"code\":\"BURST_ORDERS\",\"message\":\"最近 1 小时内该用户已下 9 单\",\"weight\":35}]}', 35, 'RULE', '放行');
INSERT INTO `risk_scores` VALUES (72, '2026-10-06 19:56:09.456258', b'0', 'LOW', 47, '{\"ruleScore\":0,\"signals\":[]}', 0, 'RULE', '放行');
INSERT INTO `risk_scores` VALUES (73, '2026-10-22 15:15:40.255299', b'0', 'LOW', 58, '{\"ruleScore\":0,\"signals\":[],\"llmSummary\":\"规则引擎没有命中任何风险信号；订单金额较低（实付94.80元），商品为常规日用品，数量正常；收货地址是高校校区，且下单时间在工作日白天，符合学生消费特征，没有明显异常。\",\"llmAction\":\"放行\"}', 12, 'HYBRID', '放行');
INSERT INTO `risk_scores` VALUES (74, '2026-10-22 15:20:49.194577', b'0', 'LOW', 54, '{\"ruleScore\":0,\"signals\":[],\"llmSummary\":\"规则引擎没有发现任何风险信号，订单金额较小且实付与应付之间的差额是由正常的优惠导致的；收货地址为高校校区，符合学生日常消费特征，没有发现异常迹象。\",\"llmAction\":\"放行\"}', 8, 'HYBRID', '放行');
INSERT INTO `risk_scores` VALUES (75, '2026-10-22 16:23:50.034218', b'0', 'LOW', 58, '{\"ruleScore\":0,\"signals\":[],\"llmSummary\":\"规则引擎未识别任何风险信号，订单金额较小（实付94.80元），商品为常规低价商品；收货地址为高校校区，属于学生网购常见场景，收货人姓名较通用，但无其他异常佐证。\",\"llmAction\":\"放行\"}', 12, 'HYBRID', '放行');

-- ----------------------------
-- Table structure for role_permissions
-- ----------------------------
DROP TABLE IF EXISTS `role_permissions`;
CREATE TABLE `role_permissions`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NULL DEFAULT NULL,
  `permission_id` bigint NOT NULL,
  `role_id` bigint NOT NULL,
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 36 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of role_permissions
-- ----------------------------
INSERT INTO `role_permissions` VALUES (9, '2026-09-08 09:04:22.063916', 1, 2);
INSERT INTO `role_permissions` VALUES (10, '2026-09-08 09:04:22.064935', 2, 2);
INSERT INTO `role_permissions` VALUES (11, '2026-09-08 09:04:22.064935', 3, 2);
INSERT INTO `role_permissions` VALUES (12, '2026-09-08 09:04:22.065943', 4, 2);
INSERT INTO `role_permissions` VALUES (13, '2026-09-08 09:04:22.067212', 6, 2);
INSERT INTO `role_permissions` VALUES (14, '2026-09-08 09:04:22.067212', 7, 3);
INSERT INTO `role_permissions` VALUES (15, '2026-09-08 09:04:22.067212', 8, 3);
INSERT INTO `role_permissions` VALUES (16, '2026-09-08 09:04:22.068716', 5, 3);
INSERT INTO `role_permissions` VALUES (17, '2026-09-08 09:05:06.334027', 1, 1);
INSERT INTO `role_permissions` VALUES (18, '2026-09-08 09:05:06.335031', 2, 1);
INSERT INTO `role_permissions` VALUES (19, '2026-09-08 09:05:06.335534', 3, 1);
INSERT INTO `role_permissions` VALUES (20, '2026-09-08 09:05:06.335534', 4, 1);
INSERT INTO `role_permissions` VALUES (21, '2026-09-08 09:05:06.336551', 5, 1);
INSERT INTO `role_permissions` VALUES (22, '2026-09-08 09:05:06.336551', 6, 1);
INSERT INTO `role_permissions` VALUES (23, '2026-09-08 09:05:06.337558', 7, 1);
INSERT INTO `role_permissions` VALUES (24, '2026-09-08 09:05:06.338620', 8, 1);
INSERT INTO `role_permissions` VALUES (29, NULL, 9, 1);
INSERT INTO `role_permissions` VALUES (30, NULL, 10, 1);
INSERT INTO `role_permissions` VALUES (31, NULL, 9, 2);
INSERT INTO `role_permissions` VALUES (32, NULL, 10, 2);
INSERT INTO `role_permissions` VALUES (33, '2026-09-15 21:26:33.004528', 11, 1);
INSERT INTO `role_permissions` VALUES (34, '2026-09-15 21:26:33.011040', 11, 2);
INSERT INTO `role_permissions` VALUES (35, '2026-09-15 21:26:33.015460', 11, 3);

-- ----------------------------
-- Table structure for roles
-- ----------------------------
DROP TABLE IF EXISTS `roles`;
CREATE TABLE `roles`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `code` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `created_at` datetime(6) NULL DEFAULT NULL,
  `data_scope` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `description` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `name` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `sort_order` int NOT NULL,
  `status` int NOT NULL,
  `updated_at` datetime(6) NULL DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `UKch1113horj4qr56f91omojv8`(`code` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 5 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of roles
-- ----------------------------
INSERT INTO `roles` VALUES (1, 'ADMIN', '2026-09-08 09:04:22.007972', 'ALL', '拥有系统全部权限，可管理角色/权限/菜单', '超级管理员', 1, 1, '2026-09-08 09:04:22.007972');
INSERT INTO `roles` VALUES (2, 'OPS', '2026-09-08 09:04:22.023493', 'ALL', '负责商品上下架、订单发货、促销配置等日常运营', '运营专员', 2, 1, '2026-09-08 09:04:22.023493');
INSERT INTO `roles` VALUES (3, 'CS', '2026-09-08 09:04:22.024504', 'ALL', '处理用户评论、回复工单、退款审核', '客服', 3, 1, '2026-09-08 09:04:22.024504');
INSERT INTO `roles` VALUES (4, 'USER', '2026-09-08 09:04:22.025513', 'SELF', '前台消费者账号（与用户枚举 USER 对应）', '普通用户', 4, 1, '2026-09-08 09:04:22.025513');

-- ----------------------------
-- Table structure for shipments
-- ----------------------------
DROP TABLE IF EXISTS `shipments`;
CREATE TABLE `shipments`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `company` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `created_at` datetime(6) NULL DEFAULT NULL,
  `delivered_at` datetime(6) NULL DEFAULT NULL,
  `order_id` bigint NOT NULL,
  `order_no` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `shipped_at` datetime(6) NULL DEFAULT NULL,
  `status` enum('DELIVERED','IN_TRANSIT','SHIPPED') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `tracking_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 11 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of shipments
-- ----------------------------
INSERT INTO `shipments` VALUES (1, '顺丰速运', '2026-09-08 09:39:25.197069', NULL, 3, 'O20260908003', '北京市朝阳分拣中心→上海中转中心', '2026-09-07 23:39:25.166232', 'IN_TRANSIT', 'SF17888315651950001');
INSERT INTO `shipments` VALUES (2, '中通快递', '2026-09-08 09:39:25.198076', '2026-09-07 09:39:25.166737', 4, 'O20260908004', '已由本人签收', '2026-09-05 09:39:25.166737', 'DELIVERED', 'ZT17888315651980002');
INSERT INTO `shipments` VALUES (3, '京东物流', '2026-09-08 09:39:25.199076', NULL, 5, 'O20260908005', '广州花都分拣中心发出', '2026-09-07 09:39:25.168240', 'IN_TRANSIT', 'JD17888315651990003');
INSERT INTO `shipments` VALUES (4, '顺丰速运', '2026-09-11 09:35:46.896700', '2026-09-11 10:15:08.875144', 10, 'DR20260911093207940', '', '2026-09-11 09:35:46.896700', 'DELIVERED', 'SF438196453011');
INSERT INTO `shipments` VALUES (5, '顺丰速运', '2026-09-15 22:38:59.295015', NULL, 1, 'O20260908001', '测试发货', '2026-09-15 22:38:59.293010', 'SHIPPED', 'SF7788990011');
INSERT INTO `shipments` VALUES (6, '圆通速递', '2026-09-15 22:39:41.384620', NULL, 23, 'DR20260915223941978', '演示发货', '2026-09-15 22:39:41.384620', 'SHIPPED', 'YT9900112233');
INSERT INTO `shipments` VALUES (7, '中通快递', '2026-09-15 22:41:21.254779', NULL, 20, 'DR20260915204022226', 'UI 验证', '2026-09-15 22:41:21.254779', 'SHIPPED', 'ZT6677889900');
INSERT INTO `shipments` VALUES (8, '顺丰速运', '2026-09-16 09:00:38.800165', NULL, 2, 'O20260908002', '', '2026-09-16 09:00:38.798120', 'SHIPPED', 'SF333193460193');

-- ----------------------------
-- Table structure for user_coupons
-- ----------------------------
DROP TABLE IF EXISTS `user_coupons`;
CREATE TABLE `user_coupons`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `claimed_at` datetime(6) NULL DEFAULT NULL,
  `discount` decimal(10, 4) NOT NULL,
  `expire_time` datetime(6) NULL DEFAULT NULL,
  `name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `order_id` bigint NULL DEFAULT NULL,
  `promotion_id` bigint NOT NULL,
  `status` enum('UNUSED','USED') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `threshold` decimal(10, 2) NOT NULL,
  `type` enum('DISCOUNT','FULL_REDUCTION') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `used_at` datetime(6) NULL DEFAULT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_user_coupon_user_promo`(`user_id` ASC, `promotion_id` ASC) USING BTREE,
  INDEX `idx_user_coupon_user`(`user_id` ASC) USING BTREE,
  INDEX `idx_user_coupon_user_promo`(`user_id` ASC, `promotion_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 19 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of user_coupons
-- ----------------------------
INSERT INTO `user_coupons` VALUES (1, '2026-09-15 20:25:29.127109', 10.0000, '2026-09-28 09:39:25.123002', '满 100 减 10（全场通用）', 19, 1, 'USED', 100.00, 'FULL_REDUCTION', '2026-09-15 20:30:28.566383', 5);
INSERT INTO `user_coupons` VALUES (2, '2026-09-15 20:32:39.972786', 5.0000, '2026-10-08 09:39:25.142319', '新人立减 5 元（满 50 可用）', NULL, 2, 'UNUSED', 50.00, 'FULL_REDUCTION', NULL, 5);
INSERT INTO `user_coupons` VALUES (3, '2026-09-15 20:59:37.970471', 10.0000, '2026-09-28 09:39:25.123002', '满 100 减 10（全场通用）', 54, 1, 'USED', 100.00, 'FULL_REDUCTION', '2026-09-16 19:53:51.138611', 2);
INSERT INTO `user_coupons` VALUES (4, '2026-09-16 20:04:00.174049', 20.0000, '2026-09-26 09:47:00.000000', '满500减20', NULL, 4, 'UNUSED', 500.00, 'FULL_REDUCTION', NULL, 9);
INSERT INTO `user_coupons` VALUES (15, '2026-09-16 20:20:21.736794', 5.0000, '2026-10-08 09:39:25.142319', '新人立减 5 元（满 50 可用）', 58, 2, 'USED', 50.00, 'FULL_REDUCTION', '2026-09-18 10:33:15.447313', 2);
INSERT INTO `user_coupons` VALUES (16, '2026-09-18 10:31:28.255339', 20.0000, '2026-09-26 09:47:00.000000', '满500减20', NULL, 4, 'UNUSED', 500.00, 'FULL_REDUCTION', NULL, 2);
INSERT INTO `user_coupons` VALUES (17, '2026-09-18 12:37:31.642778', 20.0000, '2026-09-26 09:47:00.000000', '满500减20', NULL, 4, 'UNUSED', 500.00, 'FULL_REDUCTION', NULL, 5);
INSERT INTO `user_coupons` VALUES (18, '2026-10-10 19:51:17.397120', 30.0000, '2026-12-31 23:59:00.000000', '满 200 减 30（全场通用）', NULL, 5, 'UNUSED', 200.00, 'FULL_REDUCTION', NULL, 5);

-- ----------------------------
-- Table structure for users
-- ----------------------------
DROP TABLE IF EXISTS `users`;
CREATE TABLE `users`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `avatar` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `created_at` datetime(6) NULL DEFAULT NULL,
  `email` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `nickname` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `password` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `phone` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `role` enum('ADMIN','CS','OPS','USER') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `status` int NOT NULL,
  `updated_at` datetime(6) NULL DEFAULT NULL,
  `username` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `balance` decimal(12, 2) NOT NULL DEFAULT 0.00,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `UKr43af9ap4edm43mmtq01oddj6`(`username` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 12 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of users
-- ----------------------------
INSERT INTO `users` VALUES (1, NULL, '2026-09-07 16:42:59.561088', NULL, '管理员', '$2a$10$oOJZQFdfp6KpyqYiN7uBY.3JzJLKUCVbw4nybntRjXArm7oDRpMOa', NULL, 'ADMIN', 1, '2026-09-07 16:42:59.561088', 'admin', 0.00);
INSERT INTO `users` VALUES (2, NULL, '2026-09-07 16:44:43.597180', NULL, '12', '$2a$10$.sMNuVQPTCB5RvPZmiUlHuEPRdhUglK7.ae7CNkxF4NYeRZ1EQiEW', NULL, 'OPS', 1, '2026-09-18 10:33:15.504217', '12', 795.40);
INSERT INTO `users` VALUES (3, NULL, '2026-09-08 09:04:22.158535', 'ops@darunfa.com', '运营小王', '$2a$10$QyuCh3slCJOdvaTE97iZTO93wbmLuBV.SqRuTqPY6Tptrbbt9zpO2', '13800000001', 'OPS', 1, '2026-09-08 09:04:22.158535', 'opsuser', 0.00);
INSERT INTO `users` VALUES (4, NULL, '2026-09-08 09:04:22.222325', 'cs@darunfa.com', '客服小李', '$2a$10$B8ZwQgZCNdkcbXY85vX8ResCEqXg3SMCgsxGcegbX5IXSOmEu02Be', '13800000002', 'CS', 1, '2026-09-08 09:04:22.222325', 'csuser', 0.00);
INSERT INTO `users` VALUES (5, NULL, '2026-09-08 09:04:22.286295', 'test@darunfa.com', '测试用户', '$2a$10$QV.9ns/vtjlEhSTBatG62eAgvVdQ1PiAgpIJ851TfFPwFA2hgBu4y', '13800000003', 'USER', 1, '2026-10-22 15:23:03.178178', 'testuser', 924.00);
INSERT INTO `users` VALUES (6, NULL, '2026-09-08 10:54:01.890285', 'risk@darunfa.com', '风险测试账户', '$2a$10$aUqEXx.AMWA.V9j4u1qlJeYZUvn0IT1xsZ.sZbqBdo0n9wOdyrdzq', '13900000086', 'USER', 1, '2026-09-16 12:13:49.893711', 'riskdemo', 2.60);
INSERT INTO `users` VALUES (9, NULL, '2026-09-16 20:03:59.784433', NULL, '券测试', '$2a$10$CpY7wfpdFCNVjHZctLjhY.VYQCZ0uUyr.JRwTMxbCtRim72WiKaV2', NULL, 'USER', 1, '2026-09-16 20:03:59.784433', 'couponqa', 0.00);

SET FOREIGN_KEY_CHECKS = 1;
