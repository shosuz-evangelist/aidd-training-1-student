-- 開発用の講座と予約のサンプル。予約が customer を参照するため、R__sample_data.sql の後に適用される名前にしている。再実行しても重複しないよう、id を指定し、すでにある行は入れない
-- 日時は実行日からの相対で置く(単発: 10 日後、連続: 14 日後から毎週 3 回、満席に近い: 7 日後)
INSERT INTO courses (id, name, category, kind, price, capacity, reserved_count) VALUES
(1, 'はじめてのパン作り', '料理', 'SINGLE', 4000, 10, 0),
(2, 'ヨガ入門 3 回コース', 'スポーツ', 'SERIES', 9000, 12, 0),
(3, '水彩画の基礎', '美術', 'SINGLE', 3500, 5, 4)
ON CONFLICT (id) DO NOTHING;

INSERT INTO course_sessions (id, course_id, session_no, starts_at, ends_at, room, instructor) VALUES
(1, 1, 1, date_trunc('day', now()) + interval '10 days 10 hours', date_trunc('day', now()) + interval '10 days 12 hours', '調理室', '山田 太郎'),
(2, 2, 1, date_trunc('day', now()) + interval '14 days 18 hours', date_trunc('day', now()) + interval '14 days 19 hours 30 minutes', 'スタジオ A', '中村 恵'),
(3, 2, 2, date_trunc('day', now()) + interval '21 days 18 hours', date_trunc('day', now()) + interval '21 days 19 hours 30 minutes', 'スタジオ A', '中村 恵'),
(4, 2, 3, date_trunc('day', now()) + interval '28 days 18 hours', date_trunc('day', now()) + interval '28 days 19 hours 30 minutes', 'スタジオ A', '中村 恵'),
(5, 3, 1, date_trunc('day', now()) + interval '7 days 13 hours', date_trunc('day', now()) + interval '7 days 15 hours', 'アトリエ', '小林 優')
ON CONFLICT (id) DO NOTHING;

-- 水彩画の基礎は定員 5 のうち 4 席が予約済み(reserved_count と ACTIVE の予約の件数を合わせる)
INSERT INTO reservations (id, customer_id, course_id, status, reserved_at) VALUES
(1, 1, 3, 'ACTIVE', now()),
(2, 2, 3, 'ACTIVE', now()),
(3, 3, 3, 'ACTIVE', now()),
(4, 4, 3, 'ACTIVE', now())
ON CONFLICT (id) DO NOTHING;

SELECT setval('courses_id_seq', (SELECT max(id) FROM courses));
SELECT setval('course_sessions_id_seq', (SELECT max(id) FROM course_sessions));
SELECT setval('reservations_id_seq', (SELECT max(id) FROM reservations));
