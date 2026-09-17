CREATE TABLE supplier_earnings (
    id BIGINT NOT NULL AUTO_INCREMENT,
    business_profile_id BIGINT NOT NULL,
    order_id BIGINT NOT NULL,
    payment_id BIGINT NOT NULL,
    gross_amount DECIMAL(19, 2) NOT NULL,
    fee_rate DECIMAL(10, 6) NOT NULL,
    platform_fee DECIMAL(19, 2) NOT NULL,
    net_amount DECIMAL(19, 2) NOT NULL,
    earned_at DATETIME(6) NOT NULL,
    reversed_at DATETIME(6) NULL,
    reversal_reason VARCHAR(1000) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_supplier_earnings_order UNIQUE (order_id),
    CONSTRAINT uk_supplier_earnings_payment UNIQUE (payment_id),
    CONSTRAINT fk_supplier_earnings_profile FOREIGN KEY (business_profile_id) REFERENCES business_profiles (id),
    CONSTRAINT fk_supplier_earnings_order FOREIGN KEY (order_id) REFERENCES orders (id),
    CONSTRAINT fk_supplier_earnings_payment FOREIGN KEY (payment_id) REFERENCES payments (id),
    CONSTRAINT chk_supplier_earnings_amounts CHECK (
        gross_amount >= 0 AND fee_rate >= 0 AND fee_rate <= 1
        AND platform_fee >= 0 AND net_amount >= 0
        AND net_amount = gross_amount - platform_fee
    ),
    INDEX idx_supplier_earnings_profile_active (business_profile_id, reversed_at)
);

INSERT INTO supplier_earnings (
    business_profile_id, order_id, payment_id, gross_amount, fee_rate,
    platform_fee, net_amount, earned_at, created_at, updated_at
)
SELECT
    o.business_profile_id,
    o.id,
    p.id,
    p.amount,
    COALESCE(CAST(sc.config_value AS DECIMAL(10, 6)), 0.050000),
    ROUND(p.amount * COALESCE(CAST(sc.config_value AS DECIMAL(10, 6)), 0.050000), 2),
    p.amount - ROUND(p.amount * COALESCE(CAST(sc.config_value AS DECIMAL(10, 6)), 0.050000), 2),
    COALESCE(o.completed_at, p.paid_at, CURRENT_TIMESTAMP(6)),
    CURRENT_TIMESTAMP(6),
    CURRENT_TIMESTAMP(6)
FROM orders o
JOIN payments p ON p.order_id = o.id
LEFT JOIN system_configs sc ON sc.config_key = 'PLATFORM_FEE_PERCENTAGE'
WHERE o.order_status = 'COMPLETED'
  AND p.payment_status = 'SUCCESS'
  AND p.method = 'EWALLET'
  AND p.id = (
      SELECT MIN(p2.id)
      FROM payments p2
      WHERE p2.order_id = o.id
        AND p2.payment_status = 'SUCCESS'
        AND p2.method = 'EWALLET'
  );

ALTER TABLE payouts ADD COLUMN business_profile_id BIGINT NULL;
ALTER TABLE payouts ADD COLUMN requested_amount DECIMAL(19, 2) NULL;
ALTER TABLE payouts ADD COLUMN bank_code VARCHAR(30) NULL;
ALTER TABLE payouts ADD COLUMN bank_name VARCHAR(150) NULL;
ALTER TABLE payouts ADD COLUMN account_number VARCHAR(50) NULL;
ALTER TABLE payouts ADD COLUMN account_holder_name VARCHAR(150) NULL;
ALTER TABLE payouts ADD COLUMN reviewed_by BIGINT NULL;
ALTER TABLE payouts ADD COLUMN reviewed_at DATETIME(6) NULL;
ALTER TABLE payouts ADD COLUMN rejection_reason VARCHAR(1000) NULL;

UPDATE payouts
SET business_profile_id = (
        SELECT o.business_profile_id FROM orders o WHERE o.id = payouts.order_id
    ),
    requested_amount = net_amount,
    bank_code = (
        SELECT pa.bank_code FROM payout_accounts pa WHERE pa.id = payouts.payout_account_id
    ),
    bank_name = (
        SELECT pa.bank_name FROM payout_accounts pa WHERE pa.id = payouts.payout_account_id
    ),
    account_number = (
        SELECT pa.account_number FROM payout_accounts pa WHERE pa.id = payouts.payout_account_id
    ),
    account_holder_name = (
        SELECT pa.account_holder_name FROM payout_accounts pa WHERE pa.id = payouts.payout_account_id
    );

UPDATE payouts SET payout_status = 'PENDING' WHERE payout_status = 'PROCESSING';
UPDATE payouts SET payout_status = 'FAILED' WHERE payout_status IN ('EXPIRED', 'REFUNDED');

ALTER TABLE payouts MODIFY COLUMN business_profile_id BIGINT NOT NULL;
ALTER TABLE payouts MODIFY COLUMN requested_amount DECIMAL(19, 2) NOT NULL;
ALTER TABLE payouts MODIFY COLUMN bank_code VARCHAR(30) NOT NULL;
ALTER TABLE payouts MODIFY COLUMN bank_name VARCHAR(150) NOT NULL;
ALTER TABLE payouts MODIFY COLUMN account_number VARCHAR(50) NOT NULL;
ALTER TABLE payouts MODIFY COLUMN account_holder_name VARCHAR(150) NOT NULL;
CREATE INDEX idx_payouts_order ON payouts (order_id);
ALTER TABLE payouts DROP INDEX uk_payouts_order;
ALTER TABLE payouts MODIFY COLUMN order_id BIGINT NULL;

ALTER TABLE payouts ADD CONSTRAINT fk_payouts_business_profile
    FOREIGN KEY (business_profile_id) REFERENCES business_profiles (id);
ALTER TABLE payouts ADD CONSTRAINT fk_payouts_reviewed_by
    FOREIGN KEY (reviewed_by) REFERENCES users (id);
ALTER TABLE payouts ADD CONSTRAINT chk_payouts_requested_amount CHECK (requested_amount > 0);
CREATE INDEX idx_payouts_profile_status_created
    ON payouts (business_profile_id, payout_status, created_at);
