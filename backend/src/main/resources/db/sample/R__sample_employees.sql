-- 開発用の購買承認の社員のサンプル。テストのデータベースには読み込まない
-- 既存の顧客 1〜5（R__sample_data.sql）を社員として参照し、足りない 3 名の顧客をここで足す
-- 再実行しても重複しないよう、id を指定し、すでにある行は入れない（ON CONFLICT DO NOTHING）

INSERT INTO customer (id, name, email, password, phone) VALUES
(6, '伊藤健一', 'ito@example.com', '123', '090-6789-0123'),
(7, '渡辺理沙', 'watanabe@example.com', '123', '080-7890-1234'),
(8, '中島大輔', 'nakajima@example.com', '123', '070-8901-2345')
ON CONFLICT (id) DO NOTHING;

SELECT setval('customer_id_seq', (SELECT max(id) FROM customer));

-- 営業部: 担当者(1)・課長(2)・部長(3)、総務部: 担当者(4)・課長(5)・部長(6)、役員(7)、購買部(8)
INSERT INTO employee (customer_id, department, role) VALUES
(1, '営業部', 'STAFF'),
(2, '営業部', 'SECTION_MANAGER'),
(3, '営業部', 'DEPARTMENT_MANAGER'),
(4, '総務部', 'STAFF'),
(5, '総務部', 'SECTION_MANAGER'),
(6, '総務部', 'DEPARTMENT_MANAGER'),
(7, NULL, 'EXECUTIVE'),
(8, '購買部', 'PURCHASING')
ON CONFLICT (customer_id) DO NOTHING;
