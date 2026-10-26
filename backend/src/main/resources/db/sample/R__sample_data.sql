-- 開発用のサンプルデータ。テストのデータベースには読み込まない
-- 再実行しても重複しないよう、id を指定し、すでにある行は入れない（ON CONFLICT DO NOTHING）

INSERT INTO customer (id, name, email, password, phone) VALUES
(1, '田中太郎', 'tanaka@example.com', '123', '090-1234-5678'),
(2, '佐藤花子', 'sato@example.com', '123', '080-2345-6789'),
(3, '鈴木一郎', 'suzuki@example.com', '123', '070-3456-7890'),
(4, '高橋美咲', 'takahashi@example.com', '123', '090-4567-8901'),
(5, '山田次郎', 'yamada@example.com', '123', '080-5678-9012')
ON CONFLICT (id) DO NOTHING;

INSERT INTO products (id, name, price, image_url) VALUES
(1, 'ボールペン', 120, '/images/pen.jpg'),
(2, 'ノート', 200, '/images/notebook.jpg'),
(3, '消しゴム', 100, '/images/eraser.jpg'),
(4, '定規', 180, '/images/ruler.jpg'),
(5, 'ホッチキス', 500, '/images/stapler.jpg'),
(6, 'クリップ', 80, '/images/clip.jpg'),
(7, '付箋', 150, '/images/sticky_notes.jpg'),
(8, 'セロハンテープ', 180, '/images/tape.jpg'),
(9, 'ハサミ', 350, '/images/scissors.jpg'),
(10, 'シルバーペン', 220, '/images/ballpoint_pen_silver.jpg'),
(11, 'テープディスペンサー', 400, '/images/tape_dispenser_2.jpg'),
(12, 'クリアファイル', 120, '/images/file.jpg'),
(13, 'バインダークリップ', 90, '/images/binder.jpg'),
(14, '蛍光ペンセット', 300, '/images/highlighter_set.jpg'),
(15, 'テープディスペンサー（小）', 350, '/images/tape_dispenser_3.jpg'),
(16, '手帳', 600, '/images/planner_with_glasses.jpg'),
(17, '色鉛筆セット', 450, '/images/colored_pencil_set.jpg'),
(18, 'マーカー', 150, '/images/marker.jpg'),
(19, '付箋セット', 250, '/images/sticky_notes_with_notebook.jpg'),
(20, 'スパイラルノート', 220, '/images/spiral_notebook_2.jpg')
ON CONFLICT (id) DO NOTHING;


SELECT setval('customer_id_seq', (SELECT max(id) FROM customer));
SELECT setval('products_id_seq', (SELECT max(id) FROM products));
