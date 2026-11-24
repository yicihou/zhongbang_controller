-- 第三版：报修单一单多行（含外购设备建档）。
-- 不修改 V1、V2。金额仍然是分。H2（MySQL 模式）和 MySQL 8 都能执行。
-- 维修费不新建收款表，继续写 V1 的 payment，并用 V2 补上的 biz_type=REPAIR、biz_id=报修单 id。
-- payment.order_id 在 V1 里不能为空，报修收款把这一列写成报修单 id，真正区分业务看 biz_type。

-- 报修单头。一张单对应一次客户提交，可以包含多台设备。
-- status：SUBMITTED 已提交，QUOTED 已报价，PENDING_PAYMENT 待付维修费，
-- PAID 已付清，CONFIRMED 保内或 0 元可派工，IN_SERVICE 已派工，DONE 已完成，CANCELLED 已取消。
-- quote_amount：管理员报价合计。还没报价时为空，页面显示「—」。
-- in_warranty：管理员报价时的保内标记。1 保内，0 保外。还没报价时为空。
CREATE TABLE repair_ticket (
    id              BIGINT       NOT NULL COMMENT '主键，应用生成',
    tenant_id       BIGINT       NOT NULL DEFAULT 1 COMMENT '租户',
    ticket_no       VARCHAR(32)  NOT NULL COMMENT '报修单号，例如 RT202611240001',
    customer_id     BIGINT       NOT NULL COMMENT '提交的客户',
    customer_name   VARCHAR(50)  DEFAULT NULL COMMENT '提交时的客户姓名',
    status          VARCHAR(24)  NOT NULL COMMENT '报修状态，见文件头说明',
    expect_date     VARCHAR(16)  DEFAULT NULL COMMENT '客户期望上门日期 yyyy-MM-dd',
    expect_slot     VARCHAR(16)  DEFAULT NULL COMMENT '期望时段，例如 AM、PM',
    contact_mobile  VARCHAR(20)  DEFAULT NULL COMMENT '联系电话',
    remark          VARCHAR(500) DEFAULT NULL COMMENT '客户备注或管理员报价说明',
    quote_amount    BIGINT       DEFAULT NULL COMMENT '报价合计，单位分；未报价为空',
    payable_amount  BIGINT       NOT NULL DEFAULT 0 COMMENT '应付维修费，单位分',
    paid_amount     BIGINT       NOT NULL DEFAULT 0 COMMENT '已收维修费，单位分',
    in_warranty     INT          DEFAULT NULL COMMENT '报价时是否保内：1 是，0 否，未报价为空',
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '提交时间',
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最近一次变更',
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_repair_ticket_no ON repair_ticket (ticket_no);
CREATE INDEX idx_repair_ticket_customer ON repair_ticket (customer_id);
CREATE INDEX idx_repair_ticket_status ON repair_ticket (status, created_at);

-- 报修单里的一台设备。本店设备填 asset_id；外购设备提交时先写入 customer_asset，再把新档案 id 填到这里。
-- images_json：现场照片的地址列表，JSON 数组，至少一张。不在数据库里存图片文件。
-- source：INTERNAL 本店，EXTERNAL 外购。
CREATE TABLE repair_line (
    id                  BIGINT        NOT NULL COMMENT '主键，应用生成',
    ticket_id           BIGINT        NOT NULL COMMENT '所属报修单',
    line_no             INT           NOT NULL COMMENT '行号，从 1 开始',
    asset_id            BIGINT        DEFAULT NULL COMMENT '设备档案 id',
    brand               VARCHAR(64)   DEFAULT NULL COMMENT '品牌快照',
    model               VARCHAR(64)   NOT NULL COMMENT '型号快照',
    sn                  VARCHAR(64)   DEFAULT NULL COMMENT '机身码快照',
    source              VARCHAR(16)   NOT NULL COMMENT 'INTERNAL 本店或 EXTERNAL 外购',
    fault_type          VARCHAR(32)   NOT NULL COMMENT '故障类型编码，例如 THREAD_BREAK',
    fault_desc          VARCHAR(500)  NOT NULL COMMENT '故障描述',
    images_json         VARCHAR(4000) NOT NULL COMMENT '现场照片地址 JSON 数组',
    purchase_proof_url  VARCHAR(500)  DEFAULT NULL COMMENT '购买凭证地址，外购选填',
    purchase_channel    VARCHAR(128)  DEFAULT NULL COMMENT '购买渠道，外购选填',
    line_quote_amount   BIGINT        DEFAULT NULL COMMENT '这一行的报价，单位分；未报价为空',
    in_warranty         INT           DEFAULT NULL COMMENT '提交时按保修截止日推断：1 保内，0 否',
    work_order_id       BIGINT        DEFAULT NULL COMMENT '派工后的维修工单 id',
    technician_id       BIGINT        DEFAULT NULL COMMENT '派给哪位师傅',
    technician_name     VARCHAR(50)   DEFAULT NULL COMMENT '师傅姓名快照',
    appoint_at          VARCHAR(32)   DEFAULT NULL COMMENT '上门时间 yyyy-MM-dd HH:mm:ss',
    PRIMARY KEY (id)
);
CREATE INDEX idx_repair_line_ticket ON repair_line (ticket_id);

-- 设备档案补上来源和品牌，外购报修才能建档，下次客户还能再选到这台机器。
-- 已经存在的行用默认值 INTERNAL，表示本店或以前绑定的设备。
ALTER TABLE customer_asset ADD COLUMN source VARCHAR(16) NOT NULL DEFAULT 'INTERNAL' COMMENT 'INTERNAL 本店或绑定，EXTERNAL 报修时登记的外购';
ALTER TABLE customer_asset ADD COLUMN brand VARCHAR(64) DEFAULT NULL COMMENT '品牌';
ALTER TABLE customer_asset ADD COLUMN purchase_channel VARCHAR(128) DEFAULT NULL COMMENT '购买渠道，外购选填';
ALTER TABLE customer_asset ADD COLUMN purchase_proof_url VARCHAR(500) DEFAULT NULL COMMENT '购买凭证地址，外购选填';
