-- Core schema skeleton (H2 + MySQL compatible). Extend in later Flyway versions.
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
