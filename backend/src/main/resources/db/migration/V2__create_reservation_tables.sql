CREATE TABLE courses (
    id             BIGSERIAL PRIMARY KEY,
    name           VARCHAR(100) NOT NULL,
    category       VARCHAR(50)  NOT NULL,
    kind           VARCHAR(10)  NOT NULL CHECK (kind IN ('SINGLE', 'SERIES')),
    price          BIGINT       NOT NULL,
    capacity       INT          NOT NULL CHECK (capacity >= 1),
    reserved_count INT          NOT NULL DEFAULT 0,
    CHECK (reserved_count BETWEEN 0 AND capacity)
);

CREATE TABLE course_sessions (
    id         BIGSERIAL PRIMARY KEY,
    course_id  BIGINT       NOT NULL REFERENCES courses (id) ON DELETE CASCADE,
    session_no INT          NOT NULL,
    starts_at  TIMESTAMPTZ  NOT NULL,
    ends_at    TIMESTAMPTZ  NOT NULL,
    room       VARCHAR(50)  NOT NULL,
    instructor VARCHAR(50)  NOT NULL,
    UNIQUE (course_id, session_no),
    CHECK (starts_at < ends_at)
);

CREATE INDEX idx_course_sessions_room ON course_sessions (room, starts_at);
CREATE INDEX idx_course_sessions_instructor ON course_sessions (instructor, starts_at);

CREATE TABLE reservations (
    id           BIGSERIAL PRIMARY KEY,
    customer_id  BIGINT      NOT NULL REFERENCES customer (id),
    course_id    BIGINT      NOT NULL REFERENCES courses (id),
    status       VARCHAR(10) NOT NULL CHECK (status IN ('ACTIVE', 'CANCELLED')),
    reserved_at  TIMESTAMPTZ NOT NULL,
    cancelled_at TIMESTAMPTZ,
    cancel_fee   BIGINT
);

-- 同じお客様の同じ講座の有効な予約は 1 件まで（キャンセル済みは数えない）
CREATE UNIQUE INDEX uq_reservations_active ON reservations (customer_id, course_id) WHERE status = 'ACTIVE';
