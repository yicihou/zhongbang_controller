-- 第二版：商品图片与上下架字段、订单行、发货、改价记录、线下收款、师傅档期、管理端通知。
-- 不修改 V1__init.sql。金额仍然是分。H2（MySQL 模式）和 MySQL 8 都能执行。
-- 已有的 orders、payment 保留旧列，本文件只追加管理端现在用到的列，并新建行项目等表。

-- 商品图片地址、型号、副标题、分类编号、标签。status 仍用 V1 的 ON / OFF。
ALTER TABLE product ADD COLUMN cover_image VARCHAR(512) DEFAULT NULL COMMENT '图片地址，存 URL，不存文件本身';
ALTER TABLE product ADD COLUMN model VARCHAR(64) DEFAULT NULL COMMENT '型号，例如 A5-5000';
ALTER TABLE product ADD COLUMN subtitle VARCHAR(255) DEFAULT NULL COMMENT '副标题';
ALTER TABLE product ADD COLUMN category_id INT DEFAULT NULL COMMENT '分类编号，小程序按这个筛选';
ALTER TABLE product ADD COLUMN tags VARCHAR(255) DEFAULT NULL COMMENT '标签，逗号分隔，只用于展示';

-- 订单头补上商品合计、安装费、运费、改价、客户快照。旧的 total_amount 等列保留，写入时同步成应付。
ALTER TABLE orders ADD COLUMN goods_amount BIGINT NOT NULL DEFAULT 0 COMMENT '商品标价合计，单位分';
ALTER TABLE orders ADD COLUMN install_amount BIGINT NOT NULL DEFAULT 0 COMMENT '安装费合计，未定价的行按 0';
ALTER TABLE orders ADD COLUMN freight_amount BIGINT NOT NULL DEFAULT 0 COMMENT '运费，默认 0，管理员手加';
ALTER TABLE orders ADD COLUMN adjust_amount BIGINT NOT NULL DEFAULT 0 COMMENT '整单调整，可正可负，单位分';
ALTER TABLE orders ADD COLUMN payable_amount BIGINT NOT NULL DEFAULT 0 COMMENT '应付 = 商品 + 安装费 + 运费 + 调整';
ALTER TABLE orders ADD COLUMN contact_remark VARCHAR(500) DEFAULT NULL COMMENT '客户备注';
ALTER TABLE orders ADD COLUMN admin_remark VARCHAR(500) DEFAULT NULL COMMENT '管理员备注';
ALTER TABLE orders ADD COLUMN price_adjusted INT NOT NULL DEFAULT 0 COMMENT '是否改过价：0 否，1 是';
ALTER TABLE orders ADD COLUMN address_text VARCHAR(500) DEFAULT NULL COMMENT '收货地址快照，纯文本';
ALTER TABLE orders ADD COLUMN customer_name VARCHAR(50) DEFAULT NULL COMMENT '下单时的客户姓名';
ALTER TABLE orders ADD COLUMN customer_mobile VARCHAR(20) DEFAULT NULL COMMENT '下单时的客户手机';
ALTER TABLE orders ADD COLUMN request_id VARCHAR(64) DEFAULT NULL COMMENT '客户端幂等号，同一客户重复提交返回原单';
CREATE INDEX idx_orders_request ON orders (customer_id, request_id);

-- 订单行。install_fee 为空表示上门安装费还没定价（页面上显示待定），0 表示明确免费。
CREATE TABLE order_item (
    id                     BIGINT        NOT NULL COMMENT '主键，应用生成',
    order_id               BIGINT        NOT NULL COMMENT '所属订单',
    sku_id                 BIGINT        NOT NULL COMMENT '下单时的规格',
    product_name           VARCHAR(128)  NOT NULL COMMENT '商品名快照',
    sku_name               VARCHAR(128)  NOT NULL COMMENT '规格名快照',
    cover_image            VARCHAR(512)  DEFAULT NULL COMMENT '图片地址快照',
    quantity               INT           NOT NULL COMMENT '数量',
    unit_price             BIGINT        NOT NULL COMMENT '单价，单位分',
    line_amount            BIGINT        NOT NULL COMMENT '行金额 = 单价乘数量',
    delivery_method        VARCHAR(24)   NOT NULL COMMENT '发货方式：快递、物流、上门送货、上门安装、到店自提',
    install_fee            BIGINT        DEFAULT NULL COMMENT '安装费，仅上门安装行有意义；空=待定',
    install_date           VARCHAR(16)   DEFAULT NULL COMMENT '期望安装日期 yyyy-MM-dd',
    install_slot           VARCHAR(16)   DEFAULT NULL COMMENT '安装时段，例如上午',
    technician_id          BIGINT        DEFAULT NULL COMMENT '安装师傅',
    technician_name        VARCHAR(50)   DEFAULT NULL COMMENT '师傅姓名快照',
    address_snapshot_json  VARCHAR(1000) DEFAULT NULL COMMENT '行级地址快照，可空',
    shipment_id            BIGINT        DEFAULT NULL COMMENT '发货记录',
    work_order_no          VARCHAR(32)   DEFAULT NULL COMMENT '安装工单号',
    PRIMARY KEY (id)
);
CREATE INDEX idx_order_item_order ON order_item (order_id);

-- 一行一条发货记录。未付款也可以先写入。status：PENDING / DISPATCHED / DONE / CANCELLED。
CREATE TABLE shipment (
    id                 BIGINT       NOT NULL COMMENT '主键',
    order_id           BIGINT       NOT NULL COMMENT '订单',
    order_item_id      BIGINT       NOT NULL COMMENT '订单行',
    method             VARCHAR(24)  NOT NULL COMMENT '发货方式，与订单行一致',
    status             VARCHAR(16)  NOT NULL COMMENT 'PENDING 待发出，DISPATCHED 已发出，DONE 已完成，CANCELLED 已取消',
    carrier            VARCHAR(64)  DEFAULT NULL COMMENT '快递公司',
    tracking_no        VARCHAR(64)  DEFAULT NULL COMMENT '快递单号',
    logistics_company  VARCHAR(64)  DEFAULT NULL COMMENT '物流公司',
    freight_no         VARCHAR(64)  DEFAULT NULL COMMENT '物流单号',
    vehicle_no         VARCHAR(32)  DEFAULT NULL COMMENT '车牌',
    courier_name       VARCHAR(50)  DEFAULT NULL COMMENT '送货人或师傅姓名',
    courier_mobile     VARCHAR(20)  DEFAULT NULL COMMENT '联系电话',
    appoint_at         VARCHAR(32)  DEFAULT NULL COMMENT '预约时间文本',
    pickup_code        VARCHAR(32)  DEFAULT NULL COMMENT '自提码',
    pickup_store_id    BIGINT       DEFAULT NULL COMMENT '自提门店',
    picked_at          VARCHAR(32)  DEFAULT NULL COMMENT '提货时间',
    remark             VARCHAR(500) DEFAULT NULL COMMENT '备注',
    created_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id)
);
CREATE INDEX idx_shipment_order ON shipment (order_id);
CREATE INDEX idx_shipment_item ON shipment (order_item_id);

-- 改价记录。before_amount 为空表示改之前这项还没定价，例如第一次填写安装费。
CREATE TABLE price_log (
    id              BIGINT       NOT NULL COMMENT '主键',
    order_id        BIGINT       NOT NULL COMMENT '订单',
    scope           VARCHAR(16)  NOT NULL COMMENT 'ORDER 整单或 LINE 某一行',
    order_item_id   BIGINT       DEFAULT NULL COMMENT '行级改价时的订单行',
    field_name      VARCHAR(32)  NOT NULL COMMENT 'unitPrice、installFee、adjustAmount、freightAmount',
    before_amount   BIGINT       DEFAULT NULL COMMENT '改前金额，单位分，可空',
    after_amount    BIGINT       NOT NULL COMMENT '改后金额，单位分',
    reason          VARCHAR(255) NOT NULL COMMENT '原因',
    operator_name   VARCHAR(50)  NOT NULL COMMENT '操作人',
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id)
);
CREATE INDEX idx_price_log_order ON price_log (order_id);

-- 收款单补上管理端字段。旧的 order_id、out_trade_no 仍然必填，线下单把 out_trade_no 写成和 payment_no 一样。
ALTER TABLE payment ADD COLUMN biz_type VARCHAR(16) NOT NULL DEFAULT 'ORDER' COMMENT 'ORDER 订单或 REPAIR 报修';
ALTER TABLE payment ADD COLUMN biz_id BIGINT DEFAULT NULL COMMENT '订单或报修单 id';
ALTER TABLE payment ADD COLUMN payment_no VARCHAR(40) DEFAULT NULL COMMENT '内部单号';
ALTER TABLE payment ADD COLUMN title VARCHAR(64) DEFAULT NULL COMMENT '展示名，例如全款、（余款）';
ALTER TABLE payment ADD COLUMN sort_no INT NOT NULL DEFAULT 1 COMMENT '同一订单里的顺序';
ALTER TABLE payment ADD COLUMN voucher_urls VARCHAR(1000) DEFAULT NULL COMMENT '凭证地址 JSON 数组，可空';
ALTER TABLE payment ADD COLUMN offline_method VARCHAR(32) DEFAULT NULL COMMENT '线下方式：BANK_TRANSFER / WECHAT_TRANSFER / ALIPAY / CASH / OTHER';
ALTER TABLE payment ADD COLUMN operator_id BIGINT DEFAULT NULL COMMENT '登记人 id';
ALTER TABLE payment ADD COLUMN operator_name VARCHAR(50) DEFAULT NULL COMMENT '登记人姓名';
ALTER TABLE payment ADD COLUMN remark VARCHAR(500) DEFAULT NULL COMMENT '备注，可空';
ALTER TABLE payment ADD COLUMN split_from_id BIGINT DEFAULT NULL COMMENT '部分收款时，这笔余款是从哪一笔拆出来的';
CREATE INDEX idx_payment_biz ON payment (biz_type, biz_id);

-- 师傅档期。没有这一格时派工会新建，容量默认 2。
CREATE TABLE technician_schedule (
    id             BIGINT      NOT NULL COMMENT '主键',
    technician_id  BIGINT      NOT NULL COMMENT '师傅',
    service_date   VARCHAR(16) NOT NULL COMMENT '日期 yyyy-MM-dd',
    time_slot      VARCHAR(16) NOT NULL COMMENT '时段，例如上午',
    capacity       INT         NOT NULL DEFAULT 2 COMMENT '容量',
    used_count     INT         NOT NULL DEFAULT 0 COMMENT '已占用',
    order_no       VARCHAR(32) DEFAULT NULL COMMENT '最近一单的订单号，便于排期页展示',
    customer_name  VARCHAR(50) DEFAULT NULL COMMENT '最近一单的客户名',
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_technician_schedule ON technician_schedule (technician_id, service_date, time_slot);

-- 派工时关联到订单行。V1 的 work_order 没有这一列。
ALTER TABLE work_order ADD COLUMN order_item_id BIGINT DEFAULT NULL COMMENT '上门安装对应的订单行';

-- 新订单通知。管理端 SSE 推一条，同时落在这里，断线后可以轮询未读。
CREATE TABLE admin_notice (
    id          BIGINT       NOT NULL COMMENT '主键',
    tenant_id   BIGINT       NOT NULL DEFAULT 1 COMMENT '租户',
    title       VARCHAR(128) NOT NULL COMMENT '标题',
    body        VARCHAR(500) NOT NULL COMMENT '正文',
    biz_type    VARCHAR(16)  NOT NULL COMMENT 'ORDER / REPAIR / PAYMENT',
    biz_id      BIGINT       NOT NULL COMMENT '业务 id',
    event_type  VARCHAR(40)  NOT NULL COMMENT 'ORDER_CREATED 等',
    read_flag   INT          NOT NULL DEFAULT 0 COMMENT '0 未读，1 已读',
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id)
);
CREATE INDEX idx_admin_notice_read ON admin_notice (read_flag, created_at);

-- 员工显示名。登录 JWT 的 name 用这一列，没有则用 username。
ALTER TABLE sys_user ADD COLUMN name VARCHAR(50) DEFAULT NULL COMMENT '显示名';

-- 初始管理员。密码是 admin123 的 BCrypt，不是明文。角色 ADMIN，登录后 JWT 类型仍是 STAFF，因此能进 /api/admin。
INSERT INTO sys_user (id, tenant_id, username, password, role, name, deleted, created_at, updated_at)
VALUES (1, 1, 'admin', '$2b$10$n/JQfqPLE/3h.1Y9IwpG0.6lnRNi1FZycUtgmkmgefLHk89SyJ3A.', 'ADMIN', '管理员', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- 三位安装师傅，供派工和排期页使用。
INSERT INTO technician (id, tenant_id, name, mobile, skills, region, rating, deleted, created_at, updated_at) VALUES
(88, 1, '王建国', '13800001001', '平缝机,包缝机', '城东', 4.90, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(89, 1, '刘洋', '13800001002', '厚料机,花样机', '城西', 4.70, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(90, 1, '陈静', '13800001003', '电控维修', '全市', 4.80, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- 和原先小程序 Mock 相同的四台机器，方便两端打开就能看到商品。价格单位分。
INSERT INTO product (id, tenant_id, name, brand, category, category_id, model, subtitle, cover_image, tags, status, deleted, created_at, updated_at) VALUES
(101, 1, '电脑平缝机 A5', '标准牌', '工业平缝', 2, 'A5-5000', '直驱 · 自动剪线 · 5000 针/分', '', '含上门安装,现货', 'ON', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(102, 1, '家用多功能机 M3', '标准牌', '家用多功能', 1, 'M3', '锁边 · 绣花 · 家用', '', '含上门教学', 'ON', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(103, 1, '四线包缝机 E4', '标准牌', '包缝绷缝', 3, 'E4-7000', '超高速 · 低噪', '', '现货', 'ON', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(104, 1, '电脑绣花机 12 头', '标准牌', '特种机', 4, 'EM-12', '适用帽子 / 成衣', '', '可分期', 'ON', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO product_sku (id, product_id, title, price, stock, locked, deleted, created_at, updated_at) VALUES
(1011, 101, '标准台板 + 伺服电机', 328000, 8, 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(1012, 101, '加厚台板 + 伺服电机', 336000, 4, 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(1021, 102, '标准套装', 158000, 20, 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(1031, 103, '标准配置', 690000, 3, 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(1041, 104, '12 头标准型', 6800000, 2, 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
