-- 第五版：客户档案扩展、地址簿、跟进、等级规则、协议还款、分析结果，以及老板账号。
-- 不修改 V1 到 V4。金额仍然是分。H2（MySQL 模式）和 MySQL 8 都能执行。
-- 客户模块的接口只允许角色 BOSS。这里只建表和初始数据，权限在 Java 里拦截。

-- 客户档案补上老板要看的资料。原来的 name、mobile、level、tags、total_paid、source 保留。
-- level 仍是数字：1 普通、2 银卡、3 金卡、4 VIP，方便小程序登录文案继续用这一列。
-- level_code 对应 customer_level_rule.code。level_manual=1 时以老板指定为准，不自动升级。
-- customer_type：PERSONAL 个人、FACTORY 服装厂、WORKSHOP 加工作坊、SCHOOL 学校/培训班、DEALER 同行经销商。
ALTER TABLE customer ADD COLUMN alt_mobile VARCHAR(20) DEFAULT NULL COMMENT '备用电话';
ALTER TABLE customer ADD COLUMN wechat VARCHAR(64) DEFAULT NULL COMMENT '微信号';
ALTER TABLE customer ADD COLUMN customer_type VARCHAR(20) NOT NULL DEFAULT 'PERSONAL' COMMENT '客户类型';
ALTER TABLE customer ADD COLUMN shop_name VARCHAR(128) DEFAULT NULL COMMENT '厂名或店名';
ALTER TABLE customer ADD COLUMN machine_count INT DEFAULT NULL COMMENT '厂里机器数量，不清楚就空着';
ALTER TABLE customer ADD COLUMN main_products VARCHAR(255) DEFAULT NULL COMMENT '主营产品';
ALTER TABLE customer ADD COLUMN referrer VARCHAR(50) DEFAULT NULL COMMENT '介绍人';
ALTER TABLE customer ADD COLUMN birthday DATE DEFAULT NULL COMMENT '生日';
ALTER TABLE customer ADD COLUMN remark VARCHAR(500) DEFAULT NULL COMMENT '备注';
ALTER TABLE customer ADD COLUMN region VARCHAR(64) DEFAULT NULL COMMENT '地区，例如杭州市西湖区，列表按它搜索';
ALTER TABLE customer ADD COLUMN level_code VARCHAR(32) NOT NULL DEFAULT 'NORMAL' COMMENT '等级编码，对应规则表';
ALTER TABLE customer ADD COLUMN level_manual INT NOT NULL DEFAULT 0 COMMENT '1 表示老板手动指定等级，自动升级会跳过';

-- 一个客户可以有多个地址。default_flag=1 是下单时优先用的那条。
-- source：MANUAL 老板手填，ORDER 从历史订单的 address_text 导入。
CREATE TABLE customer_address (
    id              BIGINT       NOT NULL COMMENT '主键，应用生成',
    customer_id     BIGINT       NOT NULL COMMENT '客户',
    region          VARCHAR(64)  DEFAULT NULL COMMENT '地区',
    detail          VARCHAR(500) NOT NULL COMMENT '详细地址',
    contact_name    VARCHAR(50)  DEFAULT NULL COMMENT '联系人',
    contact_mobile  VARCHAR(20)  DEFAULT NULL COMMENT '联系电话',
    default_flag    INT          NOT NULL DEFAULT 0 COMMENT '1 默认地址',
    source          VARCHAR(16)  NOT NULL DEFAULT 'MANUAL' COMMENT 'MANUAL 手填，ORDER 从订单导入',
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id)
);
CREATE INDEX idx_customer_address_customer ON customer_address (customer_id);

-- 跟进记录。下次跟进日期可空，到了由老板自己看，不另做推送。
CREATE TABLE customer_follow (
    id              BIGINT       NOT NULL COMMENT '主键',
    customer_id     BIGINT       NOT NULL COMMENT '客户',
    follow_date     DATE         NOT NULL COMMENT '跟进日期',
    content         VARCHAR(1000) NOT NULL COMMENT '跟进内容',
    next_date       DATE         DEFAULT NULL COMMENT '下次跟进日期',
    operator_name   VARCHAR(50)  DEFAULT NULL COMMENT '记录人',
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '写入时间',
    PRIMARY KEY (id)
);
CREATE INDEX idx_customer_follow_customer ON customer_follow (customer_id, follow_date);

-- 购买偏好。自动算出来的品牌、品类不放这里，每次打开详情现算。
-- 这里只存老板手动补充的内容。某一列为空表示没改过，页面上仍显示自动结果。
CREATE TABLE customer_preference (
    customer_id          BIGINT       NOT NULL COMMENT '客户，一客户一行',
    manual_note          VARCHAR(1000) DEFAULT NULL COMMENT '老板补充说明',
    manual_brand         VARCHAR(64)  DEFAULT NULL COMMENT '老板指定的常买品牌',
    manual_category      VARCHAR(64)  DEFAULT NULL COMMENT '老板指定的常买品类',
    manual_price_band    VARCHAR(32)  DEFAULT NULL COMMENT '老板指定的价格区间',
    manual_pay_method    VARCHAR(32)  DEFAULT NULL COMMENT '老板指定的常用付款方式',
    manual_need_install  INT          DEFAULT NULL COMMENT '1 常需上门安装，0 否，空表示没指定',
    updated_at           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (customer_id)
);

-- 等级规则。累计实收（订单和报修已付合计，单位分）达到 min_paid 就升到这一档。
-- 多条都达到时取得分最高的一档（rank_no 大）。老板手动指定时不看这张表。
-- 等级只是标识，不参与价格。
CREATE TABLE customer_level_rule (
    id          BIGINT       NOT NULL COMMENT '主键',
    code        VARCHAR(32)  NOT NULL COMMENT '编码，程序用，不要改',
    name        VARCHAR(32)  NOT NULL COMMENT '页面上的名称，可以改',
    min_paid    BIGINT       NOT NULL DEFAULT 0 COMMENT '累计实收门槛，单位分',
    rank_no     INT          NOT NULL COMMENT '高低顺序，数字大的等级更高',
    enabled     INT          NOT NULL DEFAULT 1 COMMENT '1 启用',
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_customer_level_code ON customer_level_rule (code);

INSERT INTO customer_level_rule (id, code, name, min_paid, rank_no, enabled) VALUES
(1, 'NORMAL', '普通', 0, 1, 1),
(2, 'SILVER', '银卡', 500000, 2, 1),
(3, 'GOLD', '金卡', 2000000, 3, 1),
(4, 'VIP', 'VIP', 5000000, 4, 1);

-- 协议还款。scope：CUSTOMER 整个客户的待付款，ORDER 某一张订单，PAYMENT 某一笔付款单（含余款）。
-- status：ACTIVE 生效，FULFILLED 已履约，VOID 作废。作废和修改都不物理删除，历史在日志表。
CREATE TABLE repay_agreement (
    id              BIGINT        NOT NULL COMMENT '主键',
    customer_id     BIGINT        NOT NULL COMMENT '客户',
    scope           VARCHAR(16)   NOT NULL COMMENT 'CUSTOMER / ORDER / PAYMENT',
    order_id        BIGINT        DEFAULT NULL COMMENT '关联订单，scope=ORDER 或从付款单反查',
    payment_id      BIGINT        DEFAULT NULL COMMENT '关联付款单，scope=PAYMENT',
    due_date        DATE          NOT NULL COMMENT '约定还款日',
    agreed_amount   BIGINT        NOT NULL COMMENT '约定金额，单位分',
    status          VARCHAR(16)   NOT NULL COMMENT 'ACTIVE / FULFILLED / VOID',
    note            VARCHAR(1000) DEFAULT NULL COMMENT '文本说明，可以不填',
    materials_json  VARCHAR(4000) DEFAULT NULL COMMENT '附件 JSON：图片、录音、文档或一段文字',
    operator_name   VARCHAR(50)   DEFAULT NULL COMMENT '登记人',
    created_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id)
);
CREATE INDEX idx_repay_agreement_customer ON repay_agreement (customer_id, status);
CREATE INDEX idx_repay_agreement_due ON repay_agreement (status, due_date);

-- 协议每次新建、修改、履约、作废都留一行，页面上能看历史。
CREATE TABLE repay_agreement_log (
    id              BIGINT        NOT NULL COMMENT '主键',
    agreement_id    BIGINT        NOT NULL COMMENT '协议',
    action          VARCHAR(16)   NOT NULL COMMENT 'CREATE / UPDATE / FULFILL / VOID',
    snapshot_json   VARCHAR(4000) NOT NULL COMMENT '当时的协议内容',
    operator_name   VARCHAR(50)   DEFAULT NULL COMMENT '操作人',
    created_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    PRIMARY KEY (id)
);
CREATE INDEX idx_repay_agreement_log ON repay_agreement_log (agreement_id, created_at);

-- 提醒发过没有。同一协议、同一天、同一种提醒只写一次，避免早上 9 点的任务重复刷屏。
-- kind：SOON 到期前几天，TODAY 当天，OVERDUE 已逾期。
CREATE TABLE repay_remind_log (
    id              BIGINT      NOT NULL COMMENT '主键',
    agreement_id    BIGINT      NOT NULL COMMENT '协议',
    remind_on       VARCHAR(16) NOT NULL COMMENT '检查日期 yyyy-MM-dd',
    kind            VARCHAR(16) NOT NULL COMMENT 'SOON / TODAY / OVERDUE',
    created_at      TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '写入时间',
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_repay_remind ON repay_remind_log (agreement_id, remind_on, kind);

-- 发给 DeepSeek 的摘要（已脱敏）和模型返回的中文分析。customer_id 为空表示全店分析。
CREATE TABLE customer_analysis (
    id              BIGINT        NOT NULL COMMENT '主键',
    customer_id     BIGINT        DEFAULT NULL COMMENT '客户，全店分析为空',
    kind            VARCHAR(16)   NOT NULL COMMENT 'CUSTOMER 单个客户，OVERALL 全店',
    prompt_text     VARCHAR(8000) NOT NULL COMMENT '发给模型的摘要，不含完整手机号和门牌',
    result_text     VARCHAR(8000) NOT NULL COMMENT '模型返回的中文',
    operator_name   VARCHAR(50)   DEFAULT NULL COMMENT '谁点的分析',
    created_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '分析时间',
    PRIMARY KEY (id)
);
CREATE INDEX idx_customer_analysis ON customer_analysis (kind, customer_id, created_at);

-- 通知增加受众。ALL 所有员工都能看到（新订单、新报修）。BOSS 只推给老板（协议还款）。
ALTER TABLE admin_notice ADD COLUMN audience VARCHAR(16) NOT NULL DEFAULT 'ALL' COMMENT 'ALL 或 BOSS';

-- 初始老板。密码是 boss123 的 BCrypt，不是明文。角色 BOSS。
-- 登录后 JWT 类型仍是 STAFF，另外带 ROLE_BOSS，所以进得了后台，但客户接口还要再查这个角色。
INSERT INTO sys_user (id, tenant_id, username, password, role, name, mobile, status, remark, deleted, created_at, updated_at)
VALUES (2, 1, 'boss', '$2a$10$PRjITwOixUaUbUuX8fRvt.xH1PxlMGfpJQes44dSuEzY2.UauF.dW', 'BOSS', '老板', '13900000000', 'ENABLED', '客户模块', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
