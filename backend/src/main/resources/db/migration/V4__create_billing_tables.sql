CREATE TABLE billing_plans (
    code        VARCHAR(20) PRIMARY KEY,
    name        VARCHAR(50) NOT NULL,
    monthly_fee BIGINT      NOT NULL CHECK (monthly_fee >= 0)
);

-- プランはマスタなので、マイグレーションで入れる（税抜きの月額）
INSERT INTO billing_plans (code, name, monthly_fee) VALUES
('LIGHT', 'ライト', 3000),
('STANDARD', 'スタンダード', 5000);

CREATE TABLE subscriptions (
    id           BIGSERIAL PRIMARY KEY,
    customer_id  BIGINT  NOT NULL REFERENCES customer (id),
    started_on   DATE    NOT NULL,
    annual       BOOLEAN NOT NULL,
    coupon       BOOLEAN NOT NULL,
    cancelled_on DATE,
    CHECK (cancelled_on IS NULL OR started_on <= cancelled_on)
);

CREATE TABLE plan_periods (
    id              BIGSERIAL PRIMARY KEY,
    subscription_id BIGINT      NOT NULL REFERENCES subscriptions (id) ON DELETE CASCADE,
    plan_code       VARCHAR(20) NOT NULL REFERENCES billing_plans (code),
    starts_on       DATE        NOT NULL,
    ends_on         DATE,
    CHECK (ends_on IS NULL OR starts_on <= ends_on)
);

CREATE TABLE invoices (
    id              BIGSERIAL PRIMARY KEY,
    subscription_id BIGINT      NOT NULL REFERENCES subscriptions (id),
    billing_month   VARCHAR(7)  NOT NULL,
    subtotal        BIGINT      NOT NULL,
    annual_discount BIGINT      NOT NULL,
    coupon_discount BIGINT      NOT NULL,
    after_discount  BIGINT      NOT NULL CHECK (after_discount >= 0),
    tax             BIGINT      NOT NULL,
    total           BIGINT      NOT NULL,
    issued_at       TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_invoice_month UNIQUE (subscription_id, billing_month)
);

CREATE TABLE invoice_lines (
    id         BIGSERIAL PRIMARY KEY,
    invoice_id BIGINT      NOT NULL REFERENCES invoices (id) ON DELETE CASCADE,
    plan_code  VARCHAR(20) NOT NULL,
    from_date  DATE        NOT NULL,
    to_date    DATE        NOT NULL,
    days       INTEGER     NOT NULL,
    amount     BIGINT      NOT NULL
);
