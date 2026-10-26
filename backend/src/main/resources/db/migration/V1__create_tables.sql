CREATE TABLE products (
    id        BIGSERIAL PRIMARY KEY,
    name      VARCHAR(255) NOT NULL,
    price     INTEGER      NOT NULL CHECK (price >= 0),
    image_url VARCHAR(500) NOT NULL
);

CREATE TABLE customer (
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(100) NOT NULL,
    email      VARCHAR(255) NOT NULL UNIQUE,
    password   VARCHAR(100) NOT NULL,
    phone      VARCHAR(20),
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE carts (
    cart_id     BIGSERIAL PRIMARY KEY,
    customer_id BIGINT      NOT NULL UNIQUE REFERENCES customer (id),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE cart_items (
    cart_item_id BIGSERIAL PRIMARY KEY,
    cart_id      BIGINT      NOT NULL REFERENCES carts (cart_id) ON DELETE CASCADE,
    product_id   BIGINT      NOT NULL REFERENCES products (id),
    quantity     INTEGER     NOT NULL CHECK (quantity > 0),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_cart_product UNIQUE (cart_id, product_id)
);

CREATE TABLE sale (
    id           BIGSERIAL PRIMARY KEY,
    customer_id  BIGINT      NOT NULL REFERENCES customer (id),
    product_id   BIGINT      NOT NULL REFERENCES products (id),
    unit_price   BIGINT      NOT NULL,
    quantity     INTEGER     NOT NULL CHECK (quantity > 0),
    total_amount BIGINT      NOT NULL,
    sale_date    TIMESTAMPTZ NOT NULL
);
