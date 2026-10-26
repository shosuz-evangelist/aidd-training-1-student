# Research & Design Decisions

## Summary
- **Feature**: `purchase-approval`
- **Discovery Scope**: Extension（既存の Spring Boot / React / PostgreSQL のプロジェクトに、業務のパッケージを 1 つ足す。新しいライブラリは足さない）。light discovery を実施した
- **Key Findings**:
  - 社員の部署と役割を持つテーブルが既存のコードに無い。customer と 1 対 1 の参照用テーブル `employee` を足す。登録と管理の API は作らない（requirements の Out of scope）
  - 同じ申請への同時操作は、「期待する状態と承認待ちの役割」を WHERE に持つ条件付き UPDATE で 1 回分に絞れる（steering の testing.md が認める方式）。負けた側は更新行数 0 で、A03 にする
  - requirements.md に未決定の事項が 7 つある。設計は、どれも決めずに「未決定の接点」として 1 か所に閉じ込める（File Structure Plan と Open Questions に書く）

## Research Log

### 既存コードの確認
- **Context**: 認証、現在日時、エラーの形、マイグレーションの番号、テストの土台を確かめた
- **Sources Consulted**: `AuthController`・`AuthService`、`ClockConfig`、`ApiExceptionHandler`、`ReservationError`・`ReservationException`、`db/migration/`、`TestDataHelper`・`MutableClock`、`reservation` の design.md
- **Findings**:
  - ログインは customer の ID を返すだけで、セッションも権限も持たない。reservation の API は本文の `customerId` で利用者を渡している。同じ形で `actorId` を本文で渡す
  - 現在日時は `Clock`（Asia/Tokyo）の Bean。テストは `MutableClock` で差し替えられる
  - エラーは「業務ごとの例外 + 理由コードの enum」を `ApiExceptionHandler` が `{ code, message }` に変える
  - マイグレーションは V1・V2・V4 がある。V3 は空いているが、V4 を適用済みの開発用データベースでは、V3 を足すと Flyway が順序違反で止まる（outOfOrder は設定していない）。V5 を使う
  - `TestDataHelper.reset()` は TRUNCATE の対象を列挙している。新しいテーブルを足したら、ここにも足す
- **Implications**: 認証と日時は既存をそのまま使う。新しいテーブルは V5 に置く。`TestDataHelper` の変更が必要

### 同時更新の方式
- **Context**: Requirement 10 は、承認・差し戻し・発注・出し直しが同じ申請に同時に行われても、状態と履歴を 1 回分にすることを求める
- **Sources Consulted**: steering の `testing.md`（楽観ロックか条件付き UPDATE）、reservation の design.md（条件付き UPDATE の前例）
- **Findings**:
  - 承認は段階が進むと承認待ちの役割が変わる。`@Version` だけでも防げるが、失敗が例外（`OptimisticLockException`）になり、A03 への変換を別に書く必要がある
  - 条件付き UPDATE `... WHERE id = ? AND status = ? AND pending_role IS NOT DISTINCT FROM ?` なら、更新行数 0 をそのまま A03 にできる。履歴の INSERT は同じトランザクションで、UPDATE が 1 行のときだけ行う
- **Implications**: 条件付き UPDATE を採る。2 人が同時に承認した場合、負けた側は A03 になる（requirements の 10.1 は結果の件数だけを定め、負けた側のエラーは 10.2 の「後の操作を A03 で拒否する」に揃える）

### 日時の判定
- **Context**: 代理期間の「終了日の 23:59 を含む」「開始日の 0:00 を含む」(7.3、7.4)
- **Findings**: 日付の比較に落とせる。`LocalDate.now(clock)` が `start <= today <= end` のとき期間中とする。時刻の比較は要らない。履歴の日時は `OffsetDateTime.now(clock)`
- **Implications**: delegation は `date` 型の 2 列で持つ

### 理由の空白の判定
- **Context**: 半角と全角の空白だけの理由を未入力として扱う(4.3)
- **Findings**: `String.strip()` は `Character.isWhitespace` で判定し、全角の空白（U+3000）も取り除く。`trim()` は U+0020 以下だけで、全角の空白は残る
- **Implications**: 理由の判定は `strip().isEmpty()` を使う（`trim()` は使わない）。履歴には入力された理由をそのまま保存する

## Architecture Pattern Evaluation

| Option | Description | Strengths | Risks / Limitations | Notes |
|--------|-------------|-----------|---------------------|-------|
| 4 層 + 純粋な方針クラス | controller / service / repository / entity に、決裁権限表と権限判定を持つ `ApprovalPolicy`（リポジトリを持たない）を足す | steering の構造に従う。業務ルールの場所が 1 か所で、spec と突き合わせやすい。単体テストが速い | クラスが 1 つ増える | 採用 |
| 状態ごとのクラス（State パターン） | 状態の遷移を状態クラスに分ける | 遷移が型で表れる | 状態が 4 つ、操作が 4 つで、クラスの数が見合わない | 不採用 |

## Design Decisions

### Decision: 社員は customer への参照用テーブルで持つ
- **Context**: 社員の部署と役割が既存のコードに無い。一方で requirements は「既存の顧客・社員の情報を使う」「登録と管理は Out of scope」としている
- **Alternatives Considered**:
  1. customer に department と role の列を足す
  2. customer と 1 対 1 の `employee` テーブルを新設する
- **Selected Approach**: 2。`employee(customer_id PK/FK, department, role)`。サンプルデータは `R__sample_employees.sql` に置く。書き込みの API は作らない
- **Rationale**: customer は EC の個人客も使う。EC の entity と V1 を変えずに済み、Allowed Dependencies を「customer の ID だけ」に保てる
- **Trade-offs**: 社員の登録の画面が無いため、社員はサンプルデータとテストのデータで用意する
- **Follow-up**: 社員の管理を別の spec が持つときは、`employee` の所有をそちらへ移す（Revalidation Triggers）

### Decision: 承認の段階は「承認待ちの役割」だけで持つ
- **Context**: 決裁権限表は、申請者の役割と金額から承認者の並びを決める
- **Selected Approach**: 申請に `pending_role` を持ち、承認の後の次の承認者は `ApprovalPolicy.route(申請者の役割, 申請金額)` の並びで `pending_role` の次を求める。並びを申請に保存しない
- **Rationale**: 金額と申請者の役割は申請の後に変わらない（金額と明細は出し直しでも変えない 5.4）。並びはいつでも同じ結果で再計算できる
- **Trade-offs**: 申請者の役割が申請の後に変わると並びが変わる。社員の管理は Out of scope のため、今は考えない

### Decision: 未決定の事項は決めずに接点として残す
- **Context**: requirements.md の「未決定」7 件。steering の `tech.md` は、書かれていないことを実装で決めないことを求める
- **Selected Approach**: 未決定の事項ごとに、コードの置き場所を 1 つに限り、design.md の Open Questions に列挙する。決まるまで実装しない・テストで固定しない
- **Rationale**: 設計が推測で決めると、あとで requirements が決まったときに、どこを直すか分からなくなる
- **Follow-up**: requirements.md の未決定が決まったら、Open Questions の該当の行を消し、`/kiro-spec-design` を再実行する

### Synthesis
- **Generalization**: 承認・差し戻し・出し直し・発注は、どれも「現在の状態と操作者を確かめて、状態を 1 回だけ進め、履歴を 1 件足す」操作である。`PurchaseRequestService` の内部に共通の遷移メソッドを 1 つ持つ
- **Build vs Adopt**: 状態機械のライブラリ（Spring State Machine など）は、状態 4 つ・操作 4 つに対して過大で、steering のスタックに無い。足さない
- **Simplification**: 申請の明細は別のテーブルを持つが、集約は申請 1 つ（`PurchaseRequest` が items を持つ）。履歴は追記だけで、更新と削除をしない

## Risks & Mitigations
- 未決定の事項が実装に紛れ込む - Open Questions に置き場所を 1 つ決め、tasks.md でその箇所の実装を除外する
- 同時更新の負けた側のエラーが requirements に無い - 10.2 に揃えて A03 とし、テストでは「1 回分だけ反映」を確かめる
- V5 の番号が他の業務のマイグレーションとぶつかる - 追加の前に `db/migration/` の最大の番号を確かめる

## References
- steering の `tech.md`・`structure.md`・`testing.md`・`database.md`
- `.kiro/specs/course-reservation/design.md` - 条件付き UPDATE・エラー応答の前例
