-- 第六版：进价、采购入库、开票口径、库存流水和预警、毛利解读。
-- 不修改 V1 到 V5。金额仍然是分。H2（MySQL 模式）和 MySQL 8 都能执行。
-- 进价和毛利只在老板的接口里返回。没填过进价的订单行成本是空，不能当成 0。

-- 规格上的当前进价和最低库存。cost_price 为空表示还没设过进价。
-- min_stock 为空时，预警用 finance_setting.default_min_stock。
ALTER TABLE product_sku ADD COLUMN cost_price BIGINT DEFAULT NULL COMMENT '当前进价，单位分。空表示还没设';
ALTER TABLE product_sku ADD COLUMN min_stock INT DEFAULT NULL COMMENT '最低库存。空则用全局默认';

-- 谁在什么时候把进价从多少改成多少。改前为空表示这是第一次填写。
CREATE TABLE cost_price_log (
    id              BIGINT       NOT NULL COMMENT '主键，应用生成',
    sku_id          BIGINT       NOT NULL COMMENT '规格',
    before_price    BIGINT       DEFAULT NULL COMMENT '改前进价，单位分，第一次填写为空',
    after_price     BIGINT       DEFAULT NULL COMMENT '改后进价，单位分。清空进价时为空',
    operator_name   VARCHAR(50)  NOT NULL COMMENT '操作人',
    reason          VARCHAR(255) DEFAULT NULL COMMENT '原因。演示数据会写「演示数据」，方便以后清掉',
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '修改时间',
    PRIMARY KEY (id)
);
CREATE INDEX idx_cost_price_log_sku ON cost_price_log (sku_id, created_at);

-- 订单行记下当时的成本。cost_known=0 是未知成本，报表不能把这一行的成本当成 0。
ALTER TABLE order_item ADD COLUMN cost_price BIGINT DEFAULT NULL COMMENT '成本单价快照，单位分。未知时为空';
ALTER TABLE order_item ADD COLUMN cost_known INT NOT NULL DEFAULT 0 COMMENT '1 已记下成本，0 未知';
ALTER TABLE order_item ADD COLUMN cost_amount BIGINT DEFAULT NULL COMMENT '行成本 = 单价乘数量。未知时为空';

-- 订单是否开票。税率用「百分数乘 100」保存，1300 表示 13.00%。不开票时税率和含税标记都空着。
ALTER TABLE orders ADD COLUMN invoiced INT NOT NULL DEFAULT 0 COMMENT '0 不开票，1 开票';
ALTER TABLE orders ADD COLUMN invoice_type VARCHAR(32) DEFAULT NULL COMMENT '发票类型，例如增值税专用发票';
ALTER TABLE orders ADD COLUMN tax_rate INT DEFAULT NULL COMMENT '税率，百分数乘 100，1300 表示 13.00%';
ALTER TABLE orders ADD COLUMN tax_included INT DEFAULT NULL COMMENT '1 金额含税，0 不含税。不开票时为空';

-- 报修单同样可以标记开票，维修收入才能按开票口径筛选。
ALTER TABLE repair_ticket ADD COLUMN invoiced INT NOT NULL DEFAULT 0 COMMENT '0 不开票，1 开票';
ALTER TABLE repair_ticket ADD COLUMN invoice_type VARCHAR(32) DEFAULT NULL COMMENT '发票类型';
ALTER TABLE repair_ticket ADD COLUMN tax_rate INT DEFAULT NULL COMMENT '税率，百分数乘 100';
ALTER TABLE repair_ticket ADD COLUMN tax_included INT DEFAULT NULL COMMENT '1 金额含税，0 不含税';

-- 供应商。账期是天数，例如 30 表示月结 30 天。主营品牌是一段文字，不另建品牌表。
CREATE TABLE supplier (
    id            BIGINT       NOT NULL COMMENT '主键',
    name          VARCHAR(128) NOT NULL COMMENT '供应商名称',
    contact       VARCHAR(50)  DEFAULT NULL COMMENT '联系人',
    mobile        VARCHAR(20)  DEFAULT NULL COMMENT '电话',
    brands        VARCHAR(255) DEFAULT NULL COMMENT '主营品牌',
    account_days  INT          DEFAULT NULL COMMENT '账期天数',
    remark        VARCHAR(500) DEFAULT NULL COMMENT '备注',
    source        VARCHAR(16)  NOT NULL DEFAULT 'MANUAL' COMMENT 'MANUAL 手填，DEMO 演示，IMPORT 导入',
    deleted       INT          NOT NULL DEFAULT 0 COMMENT '0 未删，1 已删',
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id)
);

-- 采购单。status：DRAFT 草稿，RECEIVED 已入库，VOID 作废。
-- 运费记在单头，计入对供应商的应付，不摊进每一台的进价。
-- cost_method 在确认入库时记下当时的计价方式，作废时按这个方式回滚进价。
CREATE TABLE purchase_order (
    id               BIGINT       NOT NULL COMMENT '主键',
    purchase_no      VARCHAR(32)  NOT NULL COMMENT '采购单号',
    supplier_id      BIGINT       NOT NULL COMMENT '供应商',
    order_date       VARCHAR(16)  NOT NULL COMMENT '单据日期 yyyy-MM-dd',
    status           VARCHAR(16)  NOT NULL COMMENT 'DRAFT 草稿，RECEIVED 已入库，VOID 作废',
    freight_amount   BIGINT       NOT NULL DEFAULT 0 COMMENT '运费，单位分',
    goods_amount     BIGINT       NOT NULL DEFAULT 0 COMMENT '明细进价合计，单位分。有未填进价的行时只加已填的',
    total_amount     BIGINT       NOT NULL DEFAULT 0 COMMENT '应付 = 明细 + 运费',
    invoiced         INT          NOT NULL DEFAULT 0 COMMENT '0 不开票，1 开票',
    invoice_type     VARCHAR(32)  DEFAULT NULL COMMENT '发票类型',
    tax_rate         INT          DEFAULT NULL COMMENT '税率，百分数乘 100',
    tax_included     INT          DEFAULT NULL COMMENT '1 含税，0 不含税',
    attachment_urls  VARCHAR(2000) DEFAULT NULL COMMENT '进货单照片地址 JSON 数组，走文件上传接口',
    remark           VARCHAR(500) DEFAULT NULL COMMENT '备注',
    source           VARCHAR(16)  NOT NULL DEFAULT 'MANUAL' COMMENT 'MANUAL、DEMO、IMPORT',
    cost_method      VARCHAR(24)  DEFAULT NULL COMMENT '入库时的计价方式：MOVING_AVERAGE 或 LATEST',
    operator_name    VARCHAR(50)  DEFAULT NULL COMMENT '最近操作人',
    created_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_purchase_no ON purchase_order (purchase_no);
CREATE INDEX idx_purchase_supplier ON purchase_order (supplier_id, status);

-- 采购明细。unit_cost 为空表示这行还没填进价（业务员可以先记数量）。
-- before_cost / before_stock 是入库前的进价和库存，作废时用来回滚。
CREATE TABLE purchase_item (
    id            BIGINT       NOT NULL COMMENT '主键',
    purchase_id   BIGINT       NOT NULL COMMENT '采购单',
    sku_id        BIGINT       NOT NULL COMMENT '规格',
    product_name  VARCHAR(128) NOT NULL COMMENT '商品名快照',
    sku_name      VARCHAR(128) NOT NULL COMMENT '规格名快照',
    quantity      INT          NOT NULL COMMENT '数量',
    unit_cost     BIGINT       DEFAULT NULL COMMENT '进价，单位分。空表示未填',
    line_amount   BIGINT       DEFAULT NULL COMMENT '行金额。进价为空时为空',
    before_cost   BIGINT       DEFAULT NULL COMMENT '入库前的进价',
    before_stock  INT          DEFAULT NULL COMMENT '入库前的库存',
    PRIMARY KEY (id)
);
CREATE INDEX idx_purchase_item_purchase ON purchase_item (purchase_id);
CREATE INDEX idx_purchase_item_sku ON purchase_item (sku_id);

-- 供应商往来。PAYABLE 应付，PAID 已付，VOID 作废冲销。
-- 余额 = 应付合计 - 已付合计 - 冲销合计。只有老板能看这些接口。
CREATE TABLE supplier_ledger (
    id             BIGINT       NOT NULL COMMENT '主键',
    supplier_id    BIGINT       NOT NULL COMMENT '供应商',
    purchase_id    BIGINT       DEFAULT NULL COMMENT '关联采购单，手工付款可空',
    entry_type     VARCHAR(16)  NOT NULL COMMENT 'PAYABLE 应付，PAID 已付，VOID 冲销',
    amount         BIGINT       NOT NULL COMMENT '金额，单位分，都记正数',
    method         VARCHAR(32)  DEFAULT NULL COMMENT '付款方式，已付时填写',
    remark         VARCHAR(500) DEFAULT NULL COMMENT '备注',
    operator_name  VARCHAR(50)  DEFAULT NULL COMMENT '操作人',
    created_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '时间',
    PRIMARY KEY (id)
);
CREATE INDEX idx_supplier_ledger_supplier ON supplier_ledger (supplier_id, created_at);

-- 库存流水。采购入库、作废回滚会写。数量正数是增加，负数是减少。
CREATE TABLE stock_log (
    id             BIGINT       NOT NULL COMMENT '主键',
    sku_id         BIGINT       NOT NULL COMMENT '规格',
    biz_type       VARCHAR(24)  NOT NULL COMMENT 'PURCHASE_IN、PURCHASE_VOID、ADJUST',
    biz_id         BIGINT       DEFAULT NULL COMMENT '采购单或其他业务 id',
    quantity       INT          NOT NULL COMMENT '变动数量，入库为正，作废为负',
    stock_after    INT          NOT NULL COMMENT '变动后的库存',
    unit_cost      BIGINT       DEFAULT NULL COMMENT '这一笔的进价，可空',
    operator_name  VARCHAR(50)  DEFAULT NULL COMMENT '操作人',
    created_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '时间',
    PRIMARY KEY (id)
);
CREATE INDEX idx_stock_log_sku ON stock_log (sku_id, created_at);

-- 预警状态。alerting=1 表示已经提醒过，库存回到最低线以上才清掉，避免同一规格反复刷屏。
CREATE TABLE stock_alert_state (
    sku_id      BIGINT    NOT NULL COMMENT '规格',
    alerting    INT       NOT NULL DEFAULT 0 COMMENT '1 正在预警，0 已恢复',
    updated_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最近判断时间',
    PRIMARY KEY (sku_id)
);

-- 全店一份的计价和预警参数。id 固定是 1。
-- cost_method：MOVING_AVERAGE 移动加权平均（默认），LATEST 最近进价。
-- suggest_multiplier：建议采购量 = 最低库存 × 这个系数 - 现有库存 - 在途数量。默认 2。
CREATE TABLE finance_setting (
    id                   BIGINT      NOT NULL COMMENT '固定 1',
    cost_method          VARCHAR(24) NOT NULL DEFAULT 'MOVING_AVERAGE' COMMENT 'MOVING_AVERAGE 或 LATEST',
    default_min_stock    INT         NOT NULL DEFAULT 2 COMMENT '规格没单独设最低库存时用这个数',
    suggest_multiplier   INT         NOT NULL DEFAULT 2 COMMENT '建议采购量系数',
    hot_turnover_days    INT         NOT NULL DEFAULT 30 COMMENT '周转天数不超过这个数，标成畅销',
    slow_turnover_days   INT         NOT NULL DEFAULT 90 COMMENT '周转天数超过这个数，或有库存但没销量，标成滞销',
    PRIMARY KEY (id)
);
INSERT INTO finance_setting (id, cost_method, default_min_stock, suggest_multiplier, hot_turnover_days, slow_turnover_days)
VALUES (1, 'MOVING_AVERAGE', 2, 2, 30, 90);

-- 老板点「让 DeepSeek 解读」之后的结果。数字是系统算好再交给模型的，模型只写中文说明。
CREATE TABLE margin_insight (
    id             BIGINT       NOT NULL COMMENT '主键',
    period_type    VARCHAR(8)   NOT NULL COMMENT 'MONTH 或 YEAR',
    period_key     VARCHAR(16)  NOT NULL COMMENT '2026-11 或 2026',
    invoice_scope  VARCHAR(16)  NOT NULL COMMENT 'ALL、INVOICED、UNINVOICED',
    numbers_json   VARCHAR(8000) NOT NULL COMMENT '交给模型的数字，系统先算好',
    result_text    VARCHAR(8000) NOT NULL COMMENT '模型写的中文解读',
    operator_name  VARCHAR(50)  NOT NULL COMMENT '操作人',
    created_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '生成时间',
    PRIMARY KEY (id)
);
CREATE INDEX idx_margin_insight_period ON margin_insight (period_type, period_key, created_at);
