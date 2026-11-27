ALTER TABLE product ADD COLUMN purchase_supplier_id BIGINT DEFAULT NULL;
ALTER TABLE purchase_order ADD COLUMN batch_id BIGINT DEFAULT NULL;
ALTER TABLE purchase_order ADD COLUMN client_token VARCHAR(64) DEFAULT NULL;
ALTER TABLE purchase_order ADD COLUMN revision INT NOT NULL DEFAULT 0;
CREATE INDEX idx_purchase_batch ON purchase_order (batch_id);
CREATE UNIQUE INDEX uk_purchase_client_token ON purchase_order (client_token);

CREATE TABLE purchase_correction (
    id BIGINT NOT NULL PRIMARY KEY,
    purchase_id BIGINT NOT NULL,
    reason VARCHAR(255) NOT NULL,
    operator_id BIGINT NOT NULL,
    operator_name VARCHAR(50) NOT NULL,
    before_json TEXT NOT NULL,
    after_json TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_purchase_correction ON purchase_correction (purchase_id, id);
