# Research & Design Decisions

## Summary
- **Feature**: `premium-plan`
- **Discovery Scope**: Extension（既存の `billing` パッケージへの追加。軽い調査のみ。外部依存の追加なし）
- **Key Findings**:
  - プランの違いは、月額・年契約の割引率・消費税の端数処理の 3 つだけである。月額は `billing_plans` にあるが、割引率（10%）と税の端数（切り捨て）は `BillingCalculator` に定数と整数の割り算として埋まっている
  - プランの選択肢は `GET /api/billing-plans` が `billing_plans` の全行を返し、画面はそれを描くだけである。プレミアムの行を足せば、一覧と申し込みの選択肢は変更なしで出る。画面で直す必要があるのは、プラン変更時の `window.prompt` の文言だけである
  - 請求書は金額を保存した行で、再計算しない。マスタの値を足しても、作成済みの請求書は変わらない（Requirement 6.5）

## Research Log

### 既存の課金コードの構造
- **Context**: プランごとの計算の違いを、どこに置くかを決める
- **Sources Consulted**: `billing/service/BillingCalculator.java`、`SubscriptionService.java`、`SubscriptionController.java`、`entity/BillingPlan.java`、`db/migration/V4__create_billing_tables.sql`、`frontend/src/pages/SubscriptionPage.tsx`、`billing/BillingTestBase.java`
- **Findings**:
  - `BillingCalculator.calculate` は DB に触らない静的メソッドで、日割り → 年契約の割引 → クーポン → 税の順に long で求める
  - 日割りは `PlanPeriod` ごとに `monthlyFee * days / daysInMonth`（切り捨て）。明細 `InvoiceView.Line` は `planCode` を持つ
  - `PlanPeriod.getPlan()` から `BillingPlan` に届くため、計算にプランの属性を渡すために引数を足す必要はない
  - エラーは `BillingError` の enum で、プラン変更の同一プラン拒否（`SAME_PLAN`）、二重発行（`ALREADY_ISSUED`）、未契約月（`NOT_SUBSCRIBED_IN_MONTH`）は実装済み。プレミアムでも `planCode` の比較と月の判定で動く
- **Implications**: プランの属性を `billing_plans` に列として持たせ、計算は選んだプランの属性を読む形にする。分岐（`if code == PREMIUM`）をコードに持たない

### プレミアムを含む月の方針の決め方
- **Context**: 未決定の事項 2（プレミアムと別のプランが同じ月にある請求書）は、requirements に定まった決めがない。tech.md は「書かれていない端数と順序は実装で決めず、未決定の事項として残す」と定める
- **Findings**: 月に出てくるプランの方針（割引率と税の端数）がすべて同じなら、1 つの方針で一意に計算できる。ライト + スタンダードの混在は今の動作のままになる。プレミアムと別の方針のプランが混ざる月だけが、決められない場合になる
- **Implications**: 「月の中のプランの方針が 1 つに定まるか」を判定する 1 か所を作り、定まらない場合は計算せず止める。止め方は暫定で、未決定の事項が解消したらこの 1 か所だけを直す

## Architecture Pattern Evaluation

| Option | Description | Strengths | Risks / Limitations | Notes |
|--------|-------------|-----------|---------------------|-------|
| A. プランの属性を列にする（採用） | `billing_plans` に `annual_discount_percent` と `tax_rounding` を足し、計算が読む | 分岐がない。プラン追加はマスタの行の追加で済む。既存のプランは同じ値（10 と FLOOR）を入れて変えない | マイグレーションが 1 本増える | steering の「マスタはマイグレーションで入れる」に沿う |
| B. コードにプランごとの分岐を書く | `BillingCalculator` に `if (PREMIUM)` を足す | 変更が小さく見える | プランが増えるたびに計算が分岐し、仕様との対応が読みにくい | 不採用 |
| C. プランごとの計算クラス（Strategy） | `PlanPricing` インタフェースを作る | 拡張しやすい | 違いは 2 つの数値だけで、過剰な抽象になる | 不採用 |

## Design Decisions

### Decision: プランの属性を `billing_plans` の列にする
- **Context**: プレミアムだけ割引率が 15%、税の端数が四捨五入である
- **Alternatives Considered**: 表の A / B / C
- **Selected Approach**: A。`annual_discount_percent`（整数）と `tax_rounding`（`FLOOR` / `HALF_UP`）を足す
- **Rationale**: 差が数値 2 つだけで、データで表せる。仕様の「プラン」の表と列が 1 対 1 に対応する
- **Trade-offs**: 列の追加で、既存の 2 行にも値を入れる必要がある（DEFAULT で 10 と FLOOR を入れ、既存の動作を保つ）
- **Follow-up**: 既存の請求書テストが変更なしで通ることを確かめる（Requirement 6）

### Decision: 四捨五入は整数演算で行う
- **Context**: tech.md は金額に double と float を使わないと定める
- **Selected Approach**: `HALF_UP` の税は `(afterDiscount * 10 + 50) / 100`（`afterDiscount` は 0 以上の long）。438.7 円は `(4387 * 10 + 50) / 100 = 439`
- **Rationale**: BigDecimal も不要で、既存の long の割り算と同じ形を保てる

### Decision: 年契約の割引の 1 円未満は既存の切り捨ての経路を使い、差し替え点を 1 つにする
- **Context**: 未決定の事項 1。受入基準のどれも、割り切れない小計を使っていない
- **Selected Approach**: 割引の端数処理は `BillingCalculator` の 1 つのメソッド（`annualDiscountOf`）に閉じ込める。今は既存と同じ切り捨てを通すだけで、プレミアムの仕様としては未確定と明記する。この端数を確かめるテストは作らない
- **Follow-up**: 決まったら requirements に受入基準を足し、このメソッドだけを直す

### Decision: 方針が 1 つに定まらない月は計算しない
- **Context**: 未決定の事項 2
- **Selected Approach**: 月の明細のプランの方針（割引率と税の端数の組）が 2 種類以上なら、`IllegalStateException` で止める。画面向けの `BillingError` は足さない（仕様にない理由コードを作らないため）
- **Rationale**: 決まっていない金額を黙って出さない。ただし、これは仕様ではなく暫定の保護である
- **Follow-up**: 未決定の事項 2 が決まったら、この判定と受入基準を差し替える。tasks にはこの判定の実装だけを含め、混在月の金額のテストは作らない

## Risks & Mitigations
- 未決定の事項 3（全プランを四捨五入にする意図でないか）の確認が取れていない - ライト・スタンダードは切り捨てのままとする読みで進め、列の値だけを直せば全プランに広げられる形にしてある
- 既存プランの値を列の DEFAULT で入れ損ねると、既存の請求額が変わる - Requirement 6 の既存テストと、新たな受入基準 6.1 から 6.4 のテストで確かめる
- `window.prompt` の文言が古いままだと、担当者がプレミアムを入力できると気づけない - 画面の文言を直す（コードの検証はサーバーが行う）

## References
- `.kiro/specs/premium-plan/requirements.md` - 業務の決まり、受入基準、未決定の事項
- `.kiro/steering/tech.md` - 金額は long、書かれていない端数は未決定として残す
- `.kiro/steering/database.md` - マスタとテーブルの変更は Flyway で行う
