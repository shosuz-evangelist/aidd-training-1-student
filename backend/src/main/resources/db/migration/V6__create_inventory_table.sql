-- 商品ごとの在庫数と引当済数。商品マスタ（products）とは別の表にする
-- 在庫数 >= 引当済数 の CHECK は付けない（在庫数が引当済数より小さい行も保存できる）
CREATE TABLE inventories (
    product_id        BIGINT  PRIMARY KEY REFERENCES products (id),
    stock_quantity    INTEGER NOT NULL CHECK (stock_quantity >= 0),
    reserved_quantity INTEGER NOT NULL CHECK (reserved_quantity >= 0)
);
