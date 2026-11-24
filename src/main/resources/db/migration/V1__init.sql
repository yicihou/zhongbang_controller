-- 第一版表结构，H2（MySQL 模式）和 MySQL 8 都能执行。
-- 这是骨架：Java 里真正读写的只有 customer、customer_asset。
-- 其余表建出来给后续模块用，目前没有 Mapper。
-- 金额字段单位是分（BIGINT），不是元。主键由应用生成雪花 id，不是自增。
-- 只加了注释，建表语句本身与原先一致。已经在 MySQL 上执行过本文件的环境，
-- Flyway 会因校验和变化拒绝启动，需要单独处理历史库；dev 的 H2 内存库每次重启都会重跑。

-- 客户档案。小程序登录按 openid 查找，没有就插入一行。
-- openid：微信用户标识；本地登录是 dev_ 加上请求里的 code。有唯一索引。
-- unionid：同一开放平台下的统一 id，当前登录不写。
-- mobile：手机号。绑定接口尚未实现，新客户为空。有普通索引，不是唯一索引。
-- level：1 普通、2 银卡、3 金卡。登录文案按这个数字翻译，没有自动升级。
-- total_paid：累计已付，单位分。登录时新客户写成 0，没有累加代码。
-- source：客户来源。登录插入时写成 MINIAPP。
-- deleted：0 未删，1 逻辑删除。MyBatis-Plus 全局配置认这个列名。
CREATE TABLE customer (
    id            BIGINT       NOT NULL,
    tenant_id     BIGINT       NOT NULL DEFAULT 1,
    openid        VARCHAR(64)  DEFAULT NULL,
    unionid       VARCHAR(64)  DEFAULT NULL,
    mobile        VARCHAR(20)  DEFAULT NULL,
    name          VARCHAR(50)  DEFAULT NULL,
    level         INT          NOT NULL DEFAULT 1,
    tags          VARCHAR(255) DEFAULT NULL,
    total_paid    BIGINT       NOT NULL DEFAULT 0,
    source        VARCHAR(20)  DEFAULT NULL,
    deleted       INT          NOT NULL DEFAULT 0,
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_customer_openid ON customer (openid);
CREATE INDEX idx_customer_mobile ON customer (mobile);

-- 客户名下的一台机器。绑定接口会插入占位行，不查出厂数据。
-- customer_id：属于哪个客户。查询「我的设备」用它过滤。
-- sn：序列号，全表唯一。已属于别的客户时绑定会失败。
-- model：机型。占位绑定写成「待完善机型」。
-- warranty_end：保修截止日。占位绑定写成绑定当天加两年。没有这一列的 deleted，不能逻辑删除。
-- install_order：关联的安装订单 id，占位绑定不填。
CREATE TABLE customer_asset (
    id              BIGINT       NOT NULL,
    tenant_id       BIGINT       NOT NULL DEFAULT 1,
    customer_id     BIGINT       NOT NULL,
    sku_id          BIGINT       DEFAULT NULL,
    model           VARCHAR(64)  NOT NULL,
    sn              VARCHAR(64)  DEFAULT NULL,
    buy_date        DATE         NOT NULL,
    warranty_end    DATE         DEFAULT NULL,
    install_order   BIGINT       DEFAULT NULL,
    last_service_at TIMESTAMP    DEFAULT NULL,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_customer_asset_sn ON customer_asset (sn);
CREATE INDEX idx_customer_asset_customer ON customer_asset (customer_id);

-- 商品。没有 Java 实体。公开的 /api/products 不读这张表，固定返回空列表。
-- status：默认 ON。没有任何上下架代码。
CREATE TABLE product (
    id          BIGINT       NOT NULL,
    tenant_id   BIGINT       NOT NULL DEFAULT 1,
    name        VARCHAR(128) NOT NULL,
    brand       VARCHAR(64)  DEFAULT NULL,
    category    VARCHAR(64)  DEFAULT NULL,
    status      VARCHAR(16)  NOT NULL DEFAULT 'ON',
    deleted     INT          NOT NULL DEFAULT 0,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);

-- 商品规格。price 单位分，stock 可售数量，locked 预占数量。都没有对应的扣减代码。
CREATE TABLE product_sku (
    id          BIGINT       NOT NULL,
    product_id  BIGINT       NOT NULL,
    title       VARCHAR(128) NOT NULL,
    price       BIGINT       NOT NULL DEFAULT 0,
    stock       INT          NOT NULL DEFAULT 0,
    locked      INT          NOT NULL DEFAULT 0,
    deleted     INT          NOT NULL DEFAULT 0,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);
CREATE INDEX idx_product_sku_product ON product_sku (product_id);

-- 订单头，旧模型：一笔订单上的总额、定金、尾款和整单安装信息。
-- 管理端现在用的行项目、运费、改价不在这张表里，那些页面走前端 Mock，不读这里。
-- order_no：业务单号，唯一。还没有生成 SO+日期+序号 的代码。
-- total_amount / deposit_amount / paid_amount / balance_amount：单位分。
-- status：字符串，没有枚举约束，也没有状态机。
-- address_json：地址快照，应用层还没写。
CREATE TABLE orders (
    id              BIGINT       NOT NULL,
    tenant_id       BIGINT       NOT NULL DEFAULT 1,
    order_no        VARCHAR(32)  NOT NULL,
    customer_id     BIGINT       NOT NULL,
    store_id        BIGINT       DEFAULT NULL,
    total_amount    BIGINT       NOT NULL,
    deposit_amount  BIGINT       NOT NULL DEFAULT 0,
    paid_amount     BIGINT       NOT NULL DEFAULT 0,
    balance_amount  BIGINT       NOT NULL DEFAULT 0,
    status          VARCHAR(24)  NOT NULL,
    source          VARCHAR(16)  NOT NULL DEFAULT 'MINIAPP',
    install_date    DATE         DEFAULT NULL,
    install_slot    VARCHAR(16)  DEFAULT NULL,
    technician_id   BIGINT       DEFAULT NULL,
    address_json    VARCHAR(1000) DEFAULT NULL,
    remark          VARCHAR(500) DEFAULT NULL,
    pay_time        TIMESTAMP    DEFAULT NULL,
    finish_time     TIMESTAMP    DEFAULT NULL,
    deleted         INT          NOT NULL DEFAULT 0,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_orders_order_no ON orders (order_no);
CREATE INDEX idx_orders_customer ON orders (customer_id);

-- 支付单。没有下单、回调或验签代码。/api/pay/notify 只是在安全配置里放行了。
-- out_trade_no：交给微信支付的商户单号，唯一。
-- type：定金、尾款等，表上没有枚举约束。
-- amount：单位分。
-- channel：默认 WXPAY，但没有真正的微信下单。
-- transaction_id / prepay_id：预留给微信返回的单号。
CREATE TABLE payment (
    id              BIGINT       NOT NULL,
    tenant_id       BIGINT       NOT NULL DEFAULT 1,
    order_id        BIGINT       NOT NULL,
    out_trade_no    VARCHAR(40)  NOT NULL,
    type            VARCHAR(12)  NOT NULL,
    amount          BIGINT       NOT NULL,
    channel         VARCHAR(12)  NOT NULL DEFAULT 'WXPAY',
    status          VARCHAR(12)  NOT NULL,
    transaction_id  VARCHAR(64)  DEFAULT NULL,
    prepay_id       VARCHAR(64)  DEFAULT NULL,
    paid_at         TIMESTAMP    DEFAULT NULL,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_payment_out_trade_no ON payment (out_trade_no);
CREATE INDEX idx_payment_order ON payment (order_id);

-- 安装或维修工单。没有派单代码。管理端派工改的是前端 Mock，不会写这张表。
-- work_no：工单号，唯一。
-- type：安装还是维修，没有枚举约束。
-- in_warranty：是否保内，可空。
-- quote_amount / actual_amount：报价和实收，单位分。
CREATE TABLE work_order (
    id              BIGINT       NOT NULL,
    tenant_id       BIGINT       NOT NULL DEFAULT 1,
    work_no         VARCHAR(32)  NOT NULL,
    type            VARCHAR(12)  NOT NULL,
    order_id        BIGINT       DEFAULT NULL,
    asset_id        BIGINT       DEFAULT NULL,
    customer_id     BIGINT       NOT NULL,
    technician_id   BIGINT       DEFAULT NULL,
    status          VARCHAR(20)  NOT NULL,
    fault_type      VARCHAR(32)  DEFAULT NULL,
    fault_desc      VARCHAR(500) DEFAULT NULL,
    in_warranty     INT          DEFAULT NULL,
    quote_amount    BIGINT       DEFAULT 0,
    actual_amount   BIGINT       DEFAULT 0,
    appointment_at  TIMESTAMP    DEFAULT NULL,
    checkin_at      TIMESTAMP    DEFAULT NULL,
    finish_at       TIMESTAMP    DEFAULT NULL,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_work_order_work_no ON work_order (work_no);
CREATE INDEX idx_work_order_customer ON work_order (customer_id);

-- 师傅档案。没有查询接口。/api/tech/** 在安全配置里要求 TECHNICIAN 角色，还没有签发这种令牌。
-- skills：技能，一个字符串，不是单独的技能表。
-- rating：评分，默认 5.00。没有评价写入代码。
CREATE TABLE technician (
    id          BIGINT       NOT NULL,
    tenant_id   BIGINT       NOT NULL DEFAULT 1,
    name        VARCHAR(50)  NOT NULL,
    mobile      VARCHAR(20)  NOT NULL,
    skills      VARCHAR(255) DEFAULT NULL,
    region      VARCHAR(128) DEFAULT NULL,
    rating      DECIMAL(3,2) DEFAULT 5.00,
    deleted     INT          NOT NULL DEFAULT 0,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);

-- 后台员工。管理端登录页期望的账号表，但后端没有 /api/auth/staff/login。
-- username 唯一。password 没有加密或校验代码。
-- role 默认 STAFF，还没有人把它写进 JWT。
CREATE TABLE sys_user (
    id          BIGINT       NOT NULL,
    tenant_id   BIGINT       NOT NULL DEFAULT 1,
    username    VARCHAR(64)  NOT NULL,
    password    VARCHAR(128) DEFAULT NULL,
    role        VARCHAR(32)  NOT NULL DEFAULT 'STAFF',
    deleted     INT          NOT NULL DEFAULT 0,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_sys_user_username ON sys_user (username);
