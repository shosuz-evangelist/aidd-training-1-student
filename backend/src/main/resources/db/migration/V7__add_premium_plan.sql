ALTER TABLE billing_plans
    ADD COLUMN annual_discount_percent BIGINT      NOT NULL DEFAULT 10 CHECK (annual_discount_percent BETWEEN 0 AND 100),
    ADD COLUMN tax_rounding            VARCHAR(10) NOT NULL DEFAULT 'FLOOR' CHECK (tax_rounding IN ('FLOOR', 'HALF_UP'));

INSERT INTO billing_plans (code, name, monthly_fee, annual_discount_percent, tax_rounding)
VALUES ('PREMIUM', 'プレミアム', 8000, 15, 'HALF_UP');
