-- 社員。customer の ID を主キーとして持つ参照専用の記録。役員だけ部署が NULL
CREATE TABLE employee (
    customer_id BIGINT       PRIMARY KEY REFERENCES customer (id),
    department  VARCHAR(100),
    role        VARCHAR(20)  NOT NULL
        CHECK (role IN ('STAFF', 'SECTION_MANAGER', 'DEPARTMENT_MANAGER', 'EXECUTIVE', 'PURCHASING')),
    CHECK ((role = 'EXECUTIVE') = (department IS NULL))
);

-- 課長と部長は部署ごとに 1 名
CREATE UNIQUE INDEX uq_employee_manager_per_department
    ON employee (department, role)
    WHERE role IN ('SECTION_MANAGER', 'DEPARTMENT_MANAGER');

CREATE TABLE purchase_request (
    id           BIGSERIAL    PRIMARY KEY,
    applicant_id BIGINT       NOT NULL REFERENCES employee (customer_id),
    total_amount BIGINT       NOT NULL CHECK (total_amount >= 0),
    status       VARCHAR(10)  NOT NULL CHECK (status IN ('PENDING', 'RETURNED', 'APPROVED', 'ORDERED')),
    pending_role VARCHAR(20),
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CHECK ((status = 'PENDING') = (pending_role IS NOT NULL))
);

CREATE INDEX idx_purchase_request_status_pending_role ON purchase_request (status, pending_role);

CREATE TABLE purchase_request_item (
    id         BIGSERIAL PRIMARY KEY,
    request_id BIGINT    NOT NULL REFERENCES purchase_request (id) ON DELETE CASCADE,
    unit_price BIGINT    NOT NULL CHECK (unit_price >= 0),
    quantity   INTEGER   NOT NULL CHECK (quantity >= 1)
);

-- 履歴は追記だけ。申請そのものは履歴に残さない
CREATE TABLE approval_history (
    id              BIGSERIAL     PRIMARY KEY,
    request_id      BIGINT        NOT NULL REFERENCES purchase_request (id),
    actor_id        BIGINT        NOT NULL REFERENCES employee (customer_id),
    on_behalf_of_id BIGINT        REFERENCES employee (customer_id),
    action          VARCHAR(10)   NOT NULL CHECK (action IN ('APPROVE', 'RETURN', 'RESUBMIT', 'ORDER')),
    reason          VARCHAR(1000),
    acted_at        TIMESTAMPTZ   NOT NULL
);

CREATE INDEX idx_approval_history_request_id ON approval_history (request_id);

-- 開始日と終了日の前後関係の CHECK は付けない(未決定)
CREATE TABLE delegation (
    id           BIGSERIAL PRIMARY KEY,
    delegator_id BIGINT    NOT NULL REFERENCES employee (customer_id),
    delegate_id  BIGINT    NOT NULL REFERENCES employee (customer_id),
    start_date   DATE      NOT NULL,
    end_date     DATE      NOT NULL
);

CREATE INDEX idx_delegation_delegate_id ON delegation (delegate_id);
