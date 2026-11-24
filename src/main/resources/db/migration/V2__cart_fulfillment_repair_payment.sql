-- P1 schema: cart, order lines, fulfillment, repair, payment stages, notices
-- H2 (MODE=MySQL) + MySQL 8 compatible

-- ---------- product seed for local/dev demos ----------
INSERT INTO product (id, tenant_id, name, brand, category, status, deleted, created_at, updated_at)
VALUES (1001, 1, '电脑平缝机 A5', 'Zhongbang', '工业平缝', 'ON', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO product (id, tenant_id, name, brand, category, status, deleted, created_at, updated_at)
VALUES (1002, 1, '平缝机针板套件', 'Zhongbang', '零件', 'ON', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO product_sku (id, product_id, title, price, stock, locked, deleted, created_at, updated_at)
VALUES (1011, 1001, '标准台板 + 伺服电机', 328000, 20, 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO product_sku (id, product_id, title, price, stock, locked, deleted, created_at, updated_at)
VALUES (2011, 1002, '针板套件标准装', 8800, 100, 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- ---------- customer address ----------
CREATE TABLE customer_address (
    id              BIGINT       NOT NULL,
    tenant_id       BIGINT       NOT NULL DEFAULT 1,
    customer_id     BIGINT       NOT NULL,
    contact_name    VARCHAR(50)  NOT NULL,
    mobile          VARCHAR(20)  NOT NULL,
    province        VARCHAR(32)  DEFAULT NULL,
    city            VARCHAR(32)  DEFAULT NULL,
    district        VARCHAR(32)  DEFAULT NULL,
    detail          VARCHAR(255) NOT NULL,
    is_default      INT          NOT NULL DEFAULT 0,
    deleted         INT          NOT NULL DEFAULT 0,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);
CREATE INDEX idx_customer_address_customer ON customer_address (customer_id);

-- ---------- cart ----------
CREATE TABLE cart_item (
    id               BIGINT       NOT NULL,
    tenant_id        BIGINT       NOT NULL DEFAULT 1,
    customer_id      BIGINT       NOT NULL,
    sku_id           BIGINT       NOT NULL,
    quantity         INT          NOT NULL DEFAULT 1,
    delivery_method  VARCHAR(32)  NOT NULL DEFAULT 'EXPRESS',
    need_install     INT          NOT NULL DEFAULT 0,
    created_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_cart_customer_sku_delivery ON cart_item (customer_id, sku_id, delivery_method);

-- ---------- extend orders ----------
ALTER TABLE orders ADD COLUMN goods_amount BIGINT NOT NULL DEFAULT 0;
ALTER TABLE orders ADD COLUMN install_amount BIGINT NOT NULL DEFAULT 0;
ALTER TABLE orders ADD COLUMN freight_amount BIGINT NOT NULL DEFAULT 0;
ALTER TABLE orders ADD COLUMN adjust_amount BIGINT NOT NULL DEFAULT 0;
ALTER TABLE orders ADD COLUMN payable_amount BIGINT NOT NULL DEFAULT 0;
ALTER TABLE orders ADD COLUMN contact_remark VARCHAR(500) DEFAULT NULL;
ALTER TABLE orders ADD COLUMN admin_remark VARCHAR(500) DEFAULT NULL;
ALTER TABLE orders ADD COLUMN price_adjusted INT NOT NULL DEFAULT 0;
ALTER TABLE orders ADD COLUMN address_id BIGINT DEFAULT NULL;

CREATE TABLE order_item (
    id                   BIGINT       NOT NULL,
    order_id             BIGINT       NOT NULL,
    sku_id               BIGINT       NOT NULL,
    product_name         VARCHAR(128) NOT NULL,
    sku_name             VARCHAR(128) NOT NULL,
    cover_image          VARCHAR(255) DEFAULT NULL,
    quantity             INT          NOT NULL,
    unit_price           BIGINT       NOT NULL,
    line_amount          BIGINT       NOT NULL,
    delivery_method      VARCHAR(32)  NOT NULL,
    install_fee          BIGINT       NOT NULL DEFAULT 0,
    install_date         DATE         DEFAULT NULL,
    install_slot         VARCHAR(16)  DEFAULT NULL,
    technician_id        BIGINT       DEFAULT NULL,
    address_snapshot_json VARCHAR(1000) DEFAULT NULL,
    shipment_id          BIGINT       DEFAULT NULL,
    created_at           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);
CREATE INDEX idx_order_item_order ON order_item (order_id);

-- ---------- shipment ----------
CREATE TABLE shipment (
    id                 BIGINT       NOT NULL,
    order_id           BIGINT       NOT NULL,
    order_item_id      BIGINT       NOT NULL,
    method             VARCHAR(32)  NOT NULL,
    status             VARCHAR(24)  NOT NULL DEFAULT 'PENDING',
    carrier            VARCHAR(64)  DEFAULT NULL,
    tracking_no        VARCHAR(64)  DEFAULT NULL,
    logistics_company  VARCHAR(64)  DEFAULT NULL,
    freight_no         VARCHAR(64)  DEFAULT NULL,
    vehicle_no         VARCHAR(32)  DEFAULT NULL,
    courier_name       VARCHAR(50)  DEFAULT NULL,
    courier_mobile     VARCHAR(20)  DEFAULT NULL,
    appoint_at         TIMESTAMP    DEFAULT NULL,
    pickup_code        VARCHAR(32)  DEFAULT NULL,
    pickup_store_id    BIGINT       DEFAULT NULL,
    picked_at          TIMESTAMP    DEFAULT NULL,
    remark             VARCHAR(500) DEFAULT NULL,
    created_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);
CREATE INDEX idx_shipment_order ON shipment (order_id);

-- ---------- extend payment for stages / channels ----------
ALTER TABLE payment ADD COLUMN biz_type VARCHAR(16) NOT NULL DEFAULT 'ORDER';
ALTER TABLE payment ADD COLUMN biz_id BIGINT DEFAULT NULL;
ALTER TABLE payment ADD COLUMN payment_no VARCHAR(40) DEFAULT NULL;
ALTER TABLE payment ADD COLUMN title VARCHAR(64) DEFAULT NULL;
ALTER TABLE payment ADD COLUMN sort_no INT NOT NULL DEFAULT 0;
ALTER TABLE payment ADD COLUMN voucher_urls_json VARCHAR(2000) DEFAULT NULL;
ALTER TABLE payment ADD COLUMN operator_id BIGINT DEFAULT NULL;
-- widen type/channel for FULL/DEPOSIT/OFFLINE
-- (VARCHAR sizes already enough)

CREATE INDEX idx_payment_biz ON payment (biz_type, biz_id);

-- ---------- customer_asset extensions ----------
ALTER TABLE customer_asset ADD COLUMN source VARCHAR(16) NOT NULL DEFAULT 'INTERNAL';
ALTER TABLE customer_asset ADD COLUMN brand VARCHAR(64) DEFAULT NULL;

-- ---------- repair ----------
CREATE TABLE repair_ticket (
    id              BIGINT       NOT NULL,
    tenant_id       BIGINT       NOT NULL DEFAULT 1,
    ticket_no       VARCHAR(32)  NOT NULL,
    customer_id     BIGINT       NOT NULL,
    status          VARCHAR(24)  NOT NULL,
    expect_date     DATE         DEFAULT NULL,
    expect_slot     VARCHAR(16)  DEFAULT NULL,
    contact_mobile  VARCHAR(20)  DEFAULT NULL,
    remark          VARCHAR(500) DEFAULT NULL,
    quote_amount    BIGINT       NOT NULL DEFAULT 0,
    payable_amount  BIGINT       NOT NULL DEFAULT 0,
    paid_amount     BIGINT       NOT NULL DEFAULT 0,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_repair_ticket_no ON repair_ticket (ticket_no);
CREATE INDEX idx_repair_ticket_customer ON repair_ticket (customer_id);

CREATE TABLE repair_line (
    id                  BIGINT       NOT NULL,
    ticket_id           BIGINT       NOT NULL,
    line_no             INT          NOT NULL,
    asset_id            BIGINT       DEFAULT NULL,
    brand               VARCHAR(64)  DEFAULT NULL,
    model               VARCHAR(64)  DEFAULT NULL,
    sn                  VARCHAR(64)  DEFAULT NULL,
    source              VARCHAR(16)  NOT NULL DEFAULT 'INTERNAL',
    fault_type          VARCHAR(32)  NOT NULL,
    fault_desc          VARCHAR(500) NOT NULL,
    images_json         VARCHAR(2000) NOT NULL,
    purchase_proof_url  VARCHAR(255) DEFAULT NULL,
    purchase_channel    VARCHAR(64)  DEFAULT NULL,
    line_quote_amount   BIGINT       NOT NULL DEFAULT 0,
    work_order_id       BIGINT       DEFAULT NULL,
    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);
CREATE INDEX idx_repair_line_ticket ON repair_line (ticket_id);

-- ---------- technician schedule ----------
CREATE TABLE technician_schedule (
    id              BIGINT       NOT NULL,
    tenant_id       BIGINT       NOT NULL DEFAULT 1,
    technician_id   BIGINT       NOT NULL,
    service_date    DATE         NOT NULL,
    time_slot       VARCHAR(16)  NOT NULL,
    capacity        INT          NOT NULL DEFAULT 3,
    used            INT          NOT NULL DEFAULT 0,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_tech_schedule ON technician_schedule (technician_id, service_date, time_slot);

-- ---------- admin notice ----------
CREATE TABLE admin_notice (
    id          BIGINT       NOT NULL,
    tenant_id   BIGINT       NOT NULL DEFAULT 1,
    user_id     BIGINT       DEFAULT NULL,
    title       VARCHAR(128) NOT NULL,
    body        VARCHAR(500) DEFAULT NULL,
    biz_type    VARCHAR(32)  NOT NULL,
    biz_id      BIGINT       NOT NULL,
    read_flag   INT          NOT NULL DEFAULT 0,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);
CREATE INDEX idx_admin_notice_unread ON admin_notice (read_flag, created_at);

-- demo staff for admin APIs (password unused; JWT issued via staff-login stub)
INSERT INTO sys_user (id, tenant_id, username, password, role, deleted, created_at, updated_at)
VALUES (9001, 1, 'admin', '', 'STAFF', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
