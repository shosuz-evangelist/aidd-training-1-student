# Research & Design Decisions

## Summary
- **Feature**: `subscription-billing`
- **Discovery Scope**: Extension（EC と同じプロジェクトに定期便の課金を足す）
- **Key Findings**:
  - 請求額の計算をデータベースに触らない BillingCalculator に集めると、計算の順序と端数の処理を 1 か所で読める
  - プランの変更は、プランの期間を始まりの日と終わりの日で持てば、請求する月との重なりを数えるだけで日割りの明細が作れる
  - 同じ月の請求書は、先に探すだけでは同時に作られたときに 2 通になる。一意制約で防ぐ

## Design Decisions

### Decision: 請求額の計算を 1 か所に集める
- **Context**: 要件 1〜5（日割り・割引・消費税・端数）
- **Selected Approach**: BillingCalculator に、契約のプランの期間と請求する月を渡すと、明細と各金額を返す関数を作る。データベースには触らない。計算の順序は requirements.md の「請求額の求め方」の 1〜4 のとおりにする
  1. プランの期間ごとに、請求する月と重なる日数を数え、`月額 × 日数 / 月の日数`（long の割り算で切り捨て）を求める
  2. 小計 = 明細の合計。年契約なら `小計 × 10 / 100`（切り捨て）を引く。クーポンがあり、申し込んだ日がその月なら 500 円を引く。0 円より小さければ 0 円にする
  3. 消費税 = `割引の後 × 10 / 100`（切り捨て）
  4. 請求額 = 割引の後 + 消費税
- **Rationale**: 計算の順序と端数の処理を 1 か所で読めるようにする。テストも、この順序のとおりに金額を確かめる

### Decision: プランの変更の持ち方
- **Context**: 要件 3（月の途中でプランを変えた月は分けて日割りにする）と要件 5（解約）
- **Selected Approach**: プランの期間（plan_periods）を、始まりの日と終わりの日で持つ。プランを変えると、今の期間の終わりの日を変更日の前日にし、変更日から始まる新しい期間を足す。解約すると、今の期間の終わりの日を解約日にする
- **Rationale**: 請求する月と各期間の重なりを数えるだけで、日割りの明細が作れる

### Decision: 同じ月の請求書を 1 通にする方法
- **Context**: 要件 6（同じ月の請求書を 2 回作らない）
- **Alternatives Considered**:
  1. 作る前に同じ月の請求書を探すだけにする: 同時に作られたときに 2 通になる
- **Selected Approach**: invoices に一意制約 `(subscription_id, billing_month)` を付ける。作る前に同じ月の請求書を探し、あれば ALREADY_ISSUED にする。同時に作られて一意制約に違反したときも ALREADY_ISSUED にする
- **Rationale**: データベースの制約で 1 通に限るので、同時に作られても 2 通にならない

### Decision: エラーの返し方
- **Selected Approach**: BillingException に理由コードを持たせ、ApiExceptionHandler で HTTP の応答に変える。理由コードは design.md の Error Handling の表のとおり
