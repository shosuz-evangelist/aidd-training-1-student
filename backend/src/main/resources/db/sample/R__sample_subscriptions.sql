-- 開発用の定期便の契約のサンプル。再実行しても重複しないよう、id を指定し、すでにある行は入れない
-- 佐藤花子（customer 2）が、先月の 1 日からスタンダードを年契約している
INSERT INTO subscriptions (id, customer_id, started_on, annual, coupon) VALUES
(1, 2, (date_trunc('month', now() AT TIME ZONE 'Asia/Tokyo') - interval '1 month')::date, true, false)
ON CONFLICT (id) DO NOTHING;

INSERT INTO plan_periods (id, subscription_id, plan_code, starts_on) VALUES
(1, 1, 'STANDARD', (date_trunc('month', now() AT TIME ZONE 'Asia/Tokyo') - interval '1 month')::date)
ON CONFLICT (id) DO NOTHING;

SELECT setval('subscriptions_id_seq', (SELECT max(id) FROM subscriptions));
SELECT setval('plan_periods_id_seq', (SELECT max(id) FROM plan_periods));
