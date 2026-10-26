# Research & Design Decisions

## Summary
- **Feature**: `course-reservation`
- **Discovery Scope**: Extension（既存の EC サイトに、billing・approval と同じ形で業務を 1 つ足す。外部サービスと新しいライブラリはない）
- **Key Findings**:
  - 業務ごとのパッケージ（controller・service・repository・entity・dto・exception）と、業務ごとの例外と理由コードの enum が既にあり、`reservation` パッケージも同じ形にできる
  - `ApiExceptionHandler` は、例外ごとに `code`（enum の name）と `message` を返す。エラーコード R01 から R09 を返すには、enum の定数名を `R01` から `R09` にする
  - 同時更新の取りこぼしを防ぐ道具は、steering の testing.md が定める条件付き UPDATE（`InventoryRepository` が前例）。席の確保も同じ方法にする
  - 現在日時は `Clock` の Bean（Asia/Tokyo）から取る。テストでは `MutableClock` に差し替えられる
  - マイグレーションの V2 が空いている。V1（EC）・V3（approval）・V4・V5（billing）が使用済み

## Research Log

### 既存コードの拡張点
- **Context**: 新しい業務を足すとき、どこまで既存の形に合わせるかを決めるため
- **Sources Consulted**: `billing/` 一式、`approval/exception/`、`exception/ApiExceptionHandler.java`、`config/ClockConfig.java`、`db/migration/V1・V3・V4`、`frontend/src/App.tsx`・`Header.tsx`・`SubscriptionPage.tsx`、`backend/src/test/.../TestDataHelper.java`
- **Findings**:
  - お客様は ID だけで持つ（`customer_id` に外部キー制約）。API は `customerId` をリクエストで受ける（billing と同じ。サーバー側のセッション検査はない）
  - 例外は `XxxException(XxxError)`。`XxxError` が `HttpStatus` とメッセージを持つ。`ApiExceptionHandler` に `@ExceptionHandler` を 1 つ足す必要がある
  - 画面は `pages/` の 1 ファイル 1 画面で、axios で API を呼び、`localStorage` の `token` からお客様 ID を取る。業務ルールは持たない
  - `TestDataHelper.reset()` が TRUNCATE する表の一覧を持つ。新しい表を足したら一覧に足す必要がある
- **Implications**: 構成は billing に倣う。共通部分で変更するのは `ApiExceptionHandler`・`TestDataHelper`・`App.tsx`・`Header.tsx` の 4 つだけにする

### 席の確保と同時更新
- **Context**: 要件 4.3 が、同時に予約しても有効な予約の件数が定員を超えないことを求める
- **Sources Consulted**: steering の testing.md、`InventoryRepository` の条件付き UPDATE
- **Findings**:
  - steering は、読み取りと更新を 1 つのトランザクションに入れ、楽観ロックか条件付き UPDATE を使うよう定める
  - 要件 3.1 は検査の順序（R01 → R02 → R03 → R04）を定める。行ロックで先に講座を押さえる方法は steering の 2 択にないため採らない
- **Implications**: 検査を番号の順に読み取りで行ったあと、最後に `UPDATE courses SET reserved_count = reserved_count + 1 WHERE id = ? AND reserved_count < capacity` を実行する。更新が 0 行なら R01 を返す。同じお客様の同じ講座の二重予約は、部分一意索引（有効な予約だけ）が最後の防壁になる

### 時刻と日付の扱い
- **Context**: 要件 3.1 の 2、6.2 から 6.5 が、1 時間前の境界と日付の区切りを定める
- **Findings**:
  - 受付の締め切りは `now > firstStart - 1 時間` で R02。ちょうど同じ時刻は受け付ける（3.4）
  - キャンセルは `now >= firstStart` で R05（開始時刻ちょうどを含む）。それ以外は、日本時間の日付が開始日より前なら 0 円、同じ日なら受講料の 50 %（切り捨て）
  - 日付は `firstStart` を Asia/Tokyo の `LocalDate` に直して比べる。`Clock` が Asia/Tokyo なので、現在の日付も同じ区切りになる
- **Implications**: 判定は `CancellationPolicy` と予約の受付判定の 2 か所に集め、`Instant` の比較と `LocalDate` の比較だけで書く

## Architecture Pattern Evaluation

| Option | Description | Strengths | Risks / Limitations | Notes |
|--------|-------------|-----------|---------------------|-------|
| billing と同じ層構成 | controller・service・repository・entity を業務パッケージに置く | steering と一致。受講者が読み慣れている | なし | 採用 |
| ドメインモデルに時間帯の値オブジェクトを置く | `TimeRange` を値オブジェクトにして重なりを持たせる | 重なりの境界を 1 か所にできる | クラスが増える | 重なりの判定は予約と講座の登録の 2 か所で使うため、小さな record を 1 つだけ採用 |
| 行ロック（SELECT FOR UPDATE） | 講座の行を先にロックして検査を順に行う | 検査の順序を保てる | steering の 2 択にない | 不採用 |

## Design Decisions

### Decision: 残席は講座の `reserved_count` で持ち、条件付き UPDATE で確保する
- **Context**: 要件 2・4・6 が残席を使い、4.3 が同時予約で定員を超えないことを求める
- **Alternatives Considered**:
  1. 有効な予約の件数をその都度数える。数えてから挿入するまでに他の予約が入ると定員を超える
  2. 講座に `@Version` を付ける。競合した予約は再試行が要り、要件に再試行の定めがない
  3. 講座に `reserved_count` を持ち、条件付き UPDATE で増減する
- **Selected Approach**: 3。予約の登録と `reserved_count` の増加、キャンセルと減少は同じトランザクションに入れる。列に `CHECK (reserved_count BETWEEN 0 AND capacity)` を付ける
- **Rationale**: steering の方法に合い、要件 4.3 を単純に満たす。残席 = 定員 - `reserved_count` は、要件の「定員から、キャンセルしていない予約の件数を引いた数」と常に一致する
- **Trade-offs**: 件数が二重に保存される。一致はトランザクションと CHECK 制約で守る
- **Follow-up**: 同時予約のテストで、予約の件数と `reserved_count` が一致することを確かめる

### Decision: エラーの enum の定数名を要件のコードにする
- **Context**: 要件 9.1 が R01 から R09 のコードとメッセージを返すことを求める
- **Selected Approach**: `ReservationError` の定数名を `R01` から `R09` にする。`ApiExceptionHandler` が `code = name()` で返すため、既存の仕組みのまま R01 から R09 が返る
- **Rationale**: 変換の表を足さずに済む
- **Trade-offs**: 定数名から意味が読めないため、各定数にメッセージとコメントを付ける

### Decision: 未決定の事項は設計で決めない
- **Context**: requirements.md の「未決定の事項」に、設計が決めてはならない項目がある
- **Selected Approach**: 材料費・キャンセル済みの予約の再キャンセル・担当者の権限・受講料の範囲・種類と回の数が合わない入力の扱いは、design.md の「未決定の事項と設計の扱い」に表で記録し、実装とテストに加えない
- **Rationale**: steering の tech.md が、決まっていないことを実装で決めないと定める
- **Follow-up**: 決まったら requirements.md に追記し、design.md を再生成する

### Decision: 締め切りとキャンセル可否の判定は service が行い、画面に判定を持たせない
- **Context**: 要件 2.4・2.5・5.5 が、画面のボタンの状態とキャンセル不可の表示を定める
- **Selected Approach**: `CourseView.reservable`、`ReservationView.cancellable` と `cancelFee` を API が返す。「残り n 席」「満席」の文言も `seatLabel` として API が返す
- **Rationale**: steering は画面のテストを作らず、業務ルールを service と API のテストで確かめると定める。文言と状態を API が返せば、受入基準のすべてを API のテストで確かめられる

## Risks & Mitigations
- 件数の二重保存がずれる → 同じトランザクションで更新し、DB の CHECK 制約と同時予約のテストで守る
- 同じお客様が別々の講座を同時に予約して、時間帯が重なる予約が 2 件できる → 要件に同時実行の定めがなく、設計の対象外とする（Non-Goals に記録）
- 連続講座の重なりは全回で判定する（3.7）。第 2 回以降が重なる予約は R04 にする。画面とエラーの文言は変えない
- 教室と講師の重なりの検査が、同時の講座登録で競合する → 要件に同時実行の定めがなく、対象外とする

## References
- steering `.kiro/steering/structure.md`・`tech.md`・`testing.md`・`database.md` - 層構成、金額と日時、同時更新、マイグレーション
- 変更仕様書「講座予約機能追加」（版 1.0）- requirements.md の入力資料
