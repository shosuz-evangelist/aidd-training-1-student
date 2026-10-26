# Implementation Plan

- [x] 1. 土台を用意する
- [x] 1.1 V4 でテーブルとプラン 2 件を作り、サンプルの契約を入れる
  - V4__create_billing_tables.sql に design.md のテーブルと、ライト（3,000 円）・スタンダード（5,000 円）を作る
  - R__sample_subscriptions.sql に契約のサンプルを入れる
- [x] 1.2 (P) エンティティとリポジトリを作る
  - billing/entity に BillingPlan・Subscription・PlanPeriod・Invoice・InvoiceLine を作る
  - billing/repository を作る
- [x] 1.3 (P) エラーの応答とテストの土台を作る
  - billing/exception に BillingException・BillingError を作り、ApiExceptionHandler で応答に変える
  - テストに、契約とプランの期間を作る処理を足す

- [x] 2. 1 か月分と日割りの請求書を作る
- [x] 2.1 要件 1・2 の受入基準のテストを書き、失敗を確かめる
  - InvoiceProrationTest に 1.1・2.1〜2.3 のテストを書く
  - _Requirements: 1.1, 2.1, 2.2, 2.3_
- [x] 2.2 BillingCalculator の日割りと税、SubscriptionService.issueInvoice を作る
  - InvoiceProrationTest がすべて成功する
  - _Requirements: 1.1, 2.1, 2.2, 2.3_

- [x] 3. プランの変更を作る
- [x] 3.1 要件 3 の受入基準のテストを書き、失敗を確かめる
  - PlanChangeTest に 3.1〜3.3 のテストを書く
  - _Requirements: 3.1, 3.2, 3.3_
- [x] 3.2 SubscriptionService.changePlan でプランの期間を区切る
  - PlanChangeTest がすべて成功する
  - _Requirements: 3.1, 3.2, 3.3_

- [x] 4. 割引を決まった順に引く
- [x] 4.1 要件 4 の受入基準のテストを書き、失敗を確かめる
  - DiscountTest に 4.1〜4.4 のテストを書く
  - _Requirements: 4.1, 4.2, 4.3, 4.4_
- [x] 4.2 BillingCalculator に年契約の割引 → クーポン → 0 円の下限を足す
  - DiscountTest がすべて成功する
  - _Requirements: 4.1, 4.2, 4.3, 4.4_

- [x] 5. 解約を作る
- [x] 5.1 要件 5 の受入基準のテストを書き、失敗を確かめる
  - CancellationTest に 5.1〜5.2 のテストを書く
  - _Requirements: 5.1, 5.2_
- [x] 5.2 SubscriptionService.cancel と、契約していない月の拒否を作る
  - CancellationTest がすべて成功する
  - _Requirements: 5.1, 5.2_

- [x] 6. 同じ月の請求書を 2 回作らないようにする
- [x] 6.1 要件 6 の受入基準のテストを書き、失敗を確かめる
  - DuplicateInvoiceTest に 6.1〜6.2 のテストを書く（6.2 は 2 スレッド）
  - _Requirements: 6.1, 6.2_
- [x] 6.2 一意制約の違反を ALREADY_ISSUED に変える
  - DuplicateInvoiceTest がすべて成功する
  - _Requirements: 6.1, 6.2_

- [x] 7. 画面と API を作る
- [x] 7.1 SubscriptionController を design.md の API Contract の形で作る
  - _Requirements: 1.1, 3.1, 5.1, 6.1_
- [x] 7.2 (P) frontend に SubscriptionPage を作り、ヘッダーに「定期便」を足す
  - quickstart.md の手順で画面を確かめる
  - _Requirements: 2.1, 3.1, 6.1_

- [x] 8. `./mvnw test` がすべて成功することを確かめる
