# Research & Design Decisions

## Summary
- **Feature**: `ec-inventory-checkout`
- **Discovery Scope**: Extension（既存の EC に在庫を足す。軽量な調査）
- **Key Findings**:
  - EC のコードは `com.example.ecsite` 直下の controller・service・repository・entity・dto・exception に置かれ、`CartService` が追加・削除・購入確定を持つ。購入確定は売上の登録とカートの削除までを 1 つのトランザクションで行っており、在庫の検査と引当だけが無い
  - 同時更新の取りこぼしを防ぐ型は、予約（`CourseRepository.takeSeat`）と承認（`PurchaseRequestRepository`）に「条件付き・加算式のネイティブ UPDATE」がすでにある。引当済数の加算も同じ型にできる
  - 要件 6.3 は、購入確定の在庫の検査を「カート画面から送られる購入可能数」で行い、購入確定の時点の在庫を読み直さないと定めている。これを守ると、画面を開いている間に他のお客様が引当てた分は検査に反映されない（リスクとして記録する）

## Research Log

### 既存の EC の構造
- **Context**: 在庫を足す場所と、守るべき既存の型を決める
- **Sources Consulted**: `CartService`、`CartController`、`ProductService`、`ProductController`、`ErrorCode`、`CartException`、`ApiExceptionHandler`、`V1__create_tables.sql`、`R__sample_data.sql`、`CartPage.tsx`、`ProductListPage.tsx`、`ProductDetailPage.tsx`、`App.tsx`、`TestDataHelper`
- **Findings**:
  - `addToCart` は数量 1 未満（INVALID_QUANTITY）→ 商品なし（PRODUCT_NOT_FOUND）の順で検査し、在庫の検査は無い
  - `removeFromCart` は NOT_IN_CART を返す。在庫には触れない
  - `checkout` は CART_EMPTY → 売上の登録（単価は `Product.getPrice()` の現在値、金額は単価 × 数量、日時は `OffsetDateTime.now(TOKYO)`）→ `deleteByCartId` の順。`@Transactional` で囲まれ、`CartException`（RuntimeException）で全体が戻る
  - エラーは `{ code: <enum 名>, message }` で返り、画面は `message` を表示する
  - 業務のクラスは、EC はパッケージ直下、他の業務はサブパッケージに置く（steering structure.md）
  - 売上の画面は `/sales`、カートは `/cart`、商品詳細は `/products/:id`
- **Implications**: 在庫は EC の直下に `Inventory`・`InventoryRepository`・`InventoryService` として足す。売上の登録は既存の処理を引き継ぎ、そこへ在庫の検査と引当を差し込む。時刻は steering tech.md の Clock の Bean を使う規約があるが、既存の `checkout` は `OffsetDateTime.now(TOKYO)` を使っている。この機能は売上の日時の求め方を変えない（変えると既存の動作が変わるため、`Clock` の導入は対象外とする）

### 同時更新の型
- **Context**: 要件 6.6（同時の購入確定で引当済数を失わない）と steering testing.md（在庫数は 1 つのトランザクション内で更新し、楽観ロックか条件付き UPDATE を使う）
- **Sources Consulted**: `CourseRepository.takeSeat` / `releaseSeat`、`PurchaseRequestRepository` の条件付き UPDATE、`ReservationConcurrencyTest`
- **Findings**: 既存は `@Modifying(flushAutomatically = true, clearAutomatically = true)` の native UPDATE で、行ロックによって同じ行の更新を直列にする。テストは `CountDownLatch` で複数スレッドを同時に開始する
- **Implications**: 引当済数は `UPDATE inventories SET reserved_quantity = reserved_quantity + :q WHERE product_id = :id` で加算する。読み取った値に足して保存する方式は使わない。同時テストは `ReservationConcurrencyTest` の型に合わせる

### スタック
- **Findings**: 新しいライブラリは要らない。Spring Data JPA・Flyway・JUnit 5・MockMvc で足りる
- **Implications**: steering tech.md の「一覧にないライブラリを足すときは理由を書く」に該当しない

## Architecture Pattern Evaluation

| Option | Description | Strengths | Risks / Limitations | Notes |
|--------|-------------|-----------|---------------------|-------|
| A. `products` に列を足す | 在庫数と引当済数を `products` に持つ | 表が増えない | 要件 1.1（商品の情報とは別に保持）に反する。商品マスタの更新と在庫の更新が同じ行を取り合う | 不採用 |
| B. `inventories` を別の表にする | 商品 ID を主キーにした 1 対 1 の表 | 要件 1.1 と 1.3（行が無い = 0）に合う。商品マスタに触れない | 一覧で結合または一括取得が要る | 採用 |
| C. 別パッケージ `inventory/` に切る | 在庫を 1 つの業務として切り出す | 将来の入荷・出荷に備えやすい | 現在の要件に無い。EC は直下に置く既存の型と合わない | 不採用 |

## Design Decisions

### Decision: 在庫を別の表 `inventories` に持つ
- **Context**: 要件 1.1、1.3、1.5
- **Alternatives Considered**:
  1. `products` に列を足す
  2. 別の表 `inventories`（`product_id` が主キーかつ外部キー）
- **Selected Approach**: 2。行が無い商品の購入可能数は 0。`stock_quantity >= 0`、`reserved_quantity >= 0` の CHECK だけを付け、`stock_quantity >= reserved_quantity` の CHECK は付けない（要件 1.5）
- **Rationale**: 要件が「商品の情報とは別」と定めている
- **Trade-offs**: 一覧と詳細で在庫を引く処理が増える。商品の ID の一括取得で 1 回にまとめる
- **Follow-up**: 一覧の件数が増えたときの取得方法の見直しは、現在の要件の外

### Decision: 購入可能数の算出を 1 か所に集める
- **Context**: 要件 1.2、1.3、1.4 を、一覧・詳細・カート追加・カート画面が同じ基準で使う
- **Selected Approach**: `Inventory.availableQuantity()` が `max(0, stockQuantity - reservedQuantity)` を返し、`InventoryService` が行の無い商品に 0 を返す。ほかのクラスは引き算を自分で書かない
- **Rationale**: 画面ごとに引き算を書くと、負の値や行なしの扱いが食い違う

### Decision: 引当済数は加算式の UPDATE で更新し、在庫数を条件に入れない
- **Context**: 要件 6.3（在庫を読み直して検査しない）、6.5、6.6
- **Alternatives Considered**:
  1. 行を読んで `reserved + q` を保存する（@Version の楽観ロックを付ける）
  2. `reserved_quantity = reserved_quantity + :q` の UPDATE（条件は商品の行が存在すること）
  3. `... AND stock_quantity - reserved_quantity >= :q` を条件に足す
- **Selected Approach**: 2。更新件数が 0（在庫の行が無い）なら E02 として全体を戻す
- **Rationale**: 2 は加算が失われない（6.6）。3 は購入確定の時点の在庫で検査することになり、要件 6.3 に反する
- **Trade-offs**: 画面を開いている間に他のお客様が引当てた分は検査に反映されず、`reserved_quantity` が `stock_quantity` を超えることがある。この状態は要件 1.4・1.5 が許容している
- **Follow-up**: 要件 6.3 の見直しは requirements の変更になるため、design では扱わない

### Decision: 購入確定の在庫の検査に使う値は、リクエストの明細ごとの購入可能数
- **Context**: 要件 6.3 が「カート画面から送られる値」と指定している
- **Selected Approach**: `GET /api/cart/items` が各明細に `availableQuantity` を返し、`POST /api/cart/checkout` が `{ customerId, availabilities: [{ productId, availableQuantity }] }` を受ける。サーバーはカートの明細ごとに productId で送られた値を引き、数量より小さければ E02。カートの明細に対応する値が送られていない場合は、購入可能数 0 として E02 にする
- **Rationale**: 対応する値が無いときの扱いは要件に書かれていない。数量を引当てられると確かめられないものを通さないよう、E02 側に倒す
- **Follow-up**: この扱いは requirements.md の 6.3・6.4 の補足として、レビューで確認する

### Decision: 引当は商品 ID の昇順に行う
- **Context**: 複数明細の購入確定が互いに逆順の商品を引当てると、行ロックの待ち合いで止まる
- **Selected Approach**: 明細を商品 ID の昇順に並べてから UPDATE する
- **Rationale**: ロックを取る順序をそろえれば、待ち合いは起きない

### Decision: 共通の表示部品 `StockLabel`
- **Context**: 要件 3.1（詳細は一覧と同じ表示）
- **Selected Approach**: `frontend/src/components/StockLabel.tsx` が購入可能数から「購入できる数: n」または赤字の「在庫切れ」を返す。一覧と詳細が共に使う
- **Rationale**: 2 か所の表示の食い違いを防ぐ

## Synthesis
- **Generalization**: 「購入可能数を求める」が 5 つの要件（2・3・4・6 と 1）で使われる。`InventoryService.availableQuantity` / `availableQuantities` を唯一の入口にする
- **Build vs. Adopt**: 同時更新は既存の native UPDATE の型を使う。新しいライブラリは足さない
- **Simplification**: 別パッケージ、在庫の履歴の表、引当の取り消し API は作らない。要件に無い

## Risks & Mitigations
- 要件 6.3 により、購入確定の時点の在庫は読み直されない。画面の値が古い、または改ざんされていると、`reserved_quantity` が `stock_quantity` を超える — 要件が許容する状態（1.4、1.5）であり、design では変えない。要件の見直しを提案できるよう、ここに記録する
- 在庫の行が無い商品を購入確定に送られた値が 0 超で通そうとした場合 — UPDATE の更新件数が 0 となり、E02 で全体を戻す
- 複数明細のロックの待ち合い — 商品 ID の昇順で更新する
- `checkout` の売上の日時は `OffsetDateTime.now(TOKYO)` のまま（steering の Clock 規約との差） — この機能では変えず、必要なら別の変更で扱う

## References
- `.kiro/steering/testing.md` — 同時更新の規約とテストの方針
- `.kiro/steering/structure.md` — 層の規則と命名
- `backend/src/main/java/com/example/ecsite/reservation/repository/CourseRepository.java` — 加算式の UPDATE の既存の型
