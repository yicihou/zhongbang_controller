ALTER TABLE payment ADD COLUMN registered_at DATETIME DEFAULT NULL;
UPDATE payment
SET registered_at = updated_at
WHERE status = 'SUCCESS' AND operator_id IS NOT NULL;

CREATE TABLE margin_daily_snapshot (
    report_date DATE NOT NULL,
    period_type VARCHAR(8) NOT NULL,
    invoice_scope VARCHAR(16) NOT NULL,
    recorded_at DATETIME NOT NULL,
    totals_json TEXT NOT NULL,
    PRIMARY KEY (report_date, period_type, invoice_scope)
);
