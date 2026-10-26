# Implementation Plan

- [ ] 1. Foundation: テーブル・型・エラー応答・テスト基盤
- [x] 1.1 購買承認の 5 つのテーブルを作るマイグレーションを足す
  - 着手前に `db/migration/` の最大の番号を確かめ、V5 が空いていることを確認する
  - employee(役員だけ部署が NULL、課長・部長は部署ごとに 1 名の部分一意インデックス)、purchase_request(`status` と `pending_role` の対応を CHECK で持つ)、purchase_request_item(数量 1 以上・単価 0 以上の CHECK)、approval_history、delegation を作る
  - 外部キー・CHECK・索引は design.md の物理モデルのとおりにする。代理の開始日と終了日の前後関係の CHECK は付けない(未決定のため)
  - アプリを起動すると Flyway が V5 を適用し、Hibernate の validate が通る。同じ部署に課長を 2 名入れる INSERT と、明細の数量 0 の INSERT がデータベースに拒否される
  - _Requirements: 1.4, 1.5, 1.6, 7.1, 9.1_

- [x] 1.2 承認のエンティティ・列挙型・リポジトリを作る
  - 申請・明細・履歴・代理・社員のエンティティと、状態・役割・操作の列挙型を作る
  - 申請と明細は 1 つの集約として読み書きできるようにする。履歴は追記だけで、更新も削除もしない
  - 5 つのリポジトリ(基本の保存と検索だけ)を作る。申請のリポジトリへの条件付き更新の操作は 2.5 が足す
  - 統合テストで 5 つのエンティティを保存して読み戻せ、`./mvnw test` の起動でスキーマの validate が通る
  - _Requirements: 1.1, 7.1, 9.1_

- [x] 1.3 業務の拒否(A01・A02・A03)と、コードなしの 400 を既存と同じ形で返す
  - A01(403・操作する権限がありません)、A02(400・差し戻しの理由を入力してください)、A03(409・今の状態ではこの操作はできません)を持つ業務の例外とエラーの列挙型を作る
  - 申請の入力が不正なとき(明細の不備、申請者の役割が対象外)に使う、業務のコードを持たない HTTP 400 の例外も足す(エラーコードは未決定のため付けない)
  - 既存の例外応答の処理に、この例外を `{ code, message }` に変える応答と、コードなしの 400 の応答を足す。既存の例外の応答は変えない
  - 例外を投げるだけのテスト用の経路で、3 つのコードそれぞれが定義した HTTP の状態とメッセージを返し、コードなしの例外は 400 を返す
  - _Requirements: 1.4, 1.5, 1.6, 3.4, 3.6, 4.2_

- [x] 1.4 サンプルの社員とテスト補助を用意する
  - 担当者・課長・部長を 2 つの部署に、役員 1 名、購買部 1 名のサンプルの社員を、マイグレーションとは別のサンプルデータとして足す。参照する既存のサンプルの顧客を、着手時に確かめて決める
  - サンプルデータは開発用の設定だけで読み込まれ、テスト用の設定(`db/migration` だけ)では読み込まれないことを確かめる
  - テスト補助の `reset()` が 5 つの新しいテーブルを空にし、社員と申請を作る補助を足す
  - テストの前処理が `reset()` の後に社員を作って申請を保存でき、既存のテストが変わらず通る。開発用の起動でサンプルの社員が employee に入る
  - _Requirements: 2.1, 3.4, 6.1_

- [ ] 2. Core: 判定・代理・社員・条件付き更新
- [x] 2.1 (P) 決裁権限表の承認者の並びと申請金額の合計を求める
  - 申請者の役割(担当者・課長・部長)と申請金額から、承認者の並びと、ある承認者の次の承認者を返す
  - 帯の境界は 1 万円未満 / 10 万円以下 / 100 万円以下の 3 つの比較だけで決める(10,000 円ちょうどは「1 万円以上 10 万円以下」、100,000 円と 1,000,000 円は下の帯)
  - 明細の単価 × 数量の合計を求める。桁あふれは検出して例外にする
  - 申請者が担当者・課長・部長以外のときは例外を投げる
  - DB を使わない単体テストで、担当者・課長・部長の各行について 9,999 / 10,000 / 100,000 / 100,001 / 1,000,000 / 1,000,001 円の並びが決裁権限表のとおりになり、合計の 3 例(1,000 × 3、500 × 2 + 2,000 × 1、0 × 1)も合う
  - _Boundary: ApprovalPolicy_
  - _Depends: 1.2_
  - _Requirements: 1.1, 1.2, 1.3, 2.1, 2.2, 2.3, 2.4, 2.5, 2.6, 2.7, 2.8, 2.9, 2.10, 2.11, 2.12, 2.13_

- [x] 2.2 承認待ちの承認者としての資格を判定する
  - 操作者の役割が承認待ちの役割と同じで、課長・部長なら申請者と同じ部署であること(役員は部署を問わない)を判定する
  - 操作者が申請者本人のときは、申請金額が 1 万円未満のときだけ資格を認める
  - 単体テストで、部署違いの課長・部長は資格なし、役員は部署が違っても資格あり、承認待ちの役割と違う役割は資格なし、課長・部長が自分の 9,999 円は資格あり・10,000 円以上は資格なしになる
  - _Boundary: ApprovalPolicy_
  - _Depends: 2.1_
  - _Requirements: 2.14, 3.3, 3.4, 3.5, 4.4_

- [x] 2.3 (P) 代理を登録して、ある日に有効な代理を検索する
  - 代理元・代理人・開始日・終了日を保存する登録の処理と、その API を作る。社員がいなければ 404 にする
  - 代理人の ID と日付から、開始日と終了日を含む期間で有効な代理を返す
  - 誰を代理人にできるか、終了日が開始日より前、期間の重なりの検証は足さない(未決定。保存と検索だけ)
  - テストで、登録した代理が保存されて API が 201 を返し、開始日の前日は無効・開始日の 0:00 は有効・終了日の 23:59 は有効・終了日の翌日は無効になる(固定した Clock を使う)
  - _Boundary: DelegationService, DelegationController_
  - _Depends: 1.2, 1.3_
  - _Requirements: 7.1, 7.3, 7.4, 7.5_

- [x] 2.4 (P) 社員の部署と役割を返す読み取りの API を作る
  - 顧客の ID から社員の ID・名前・部署・役割を返す。書き込みの API は作らない
  - API テストで、サンプルの課長を指定すると部署と役割が返り、社員でない顧客は 404 になる
  - _Boundary: EmployeeController_
  - _Depends: 1.2, 1.3_
  - _Requirements: 8.1, 8.2, 8.3_

- [x] 2.5 (P) 条件付き UPDATE で申請の状態を 1 回だけ進めるリポジトリの操作を作る
  - 1.2 で作った申請のリポジトリに、この操作だけを足す。申請の ID・読んだ時点の状態・読んだ時点の承認待ちの役割(NULL を含む)が一致するときだけ、次の状態と承認待ちの役割に更新し、更新した行数を返す
  - 複数のスレッドから同じ条件で同時に呼ぶテストで、更新行数 1 を得るのが 1 つだけで、残りは 0 になる。承認待ちの役割が NULL の申請でも一致の判定が働く
  - _Boundary: PurchaseRequestRepository_
  - _Depends: 1.2_
  - _Requirements: 10.1, 10.2, 10.3, 10.4_

- [ ] 3. Core: 購買申請の業務ルール
- [x] 3.1 購買を申請する
  - 明細を検証し(1 件以上、数量 1 以上、単価 0 以上、桁あふれなし)、検証に通らないときは申請を作らず、1.3 のコードなしの 400 の例外で拒否する
  - 申請者が担当者・課長・部長以外のときも申請を作らず、同じ例外で拒否する(応答のコードは未決定)
  - 合計を申請金額にして、決裁権限表の最初の承認者の承認待ちの申請を、明細と一緒に保存する。申請そのものは履歴に残さない
  - 統合テストで、担当者の 1,000 × 3 が 3,000 円・課長の承認待ちで保存され、0 円の申請も受け付けられ、明細なし・数量 0・単価マイナスは申請の件数が増えない。課長の 9,999 円は課長本人、部長の 9,999 円は部長本人の承認待ちになる
  - _Depends: 1.2, 1.3, 1.4, 2.1_
  - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 1.6, 2.1, 2.7, 2.12_

- [x] 3.2 承認して次の段階または承認済みに進める
  - 状態・権限の確認を `checkOperation` の 1 か所にまとめる(A01・A02・A03 が重なるときの順序は未決定のため、受入基準にある単一の原因のケースだけを確かめる)。以降の差し戻し・代理・発注は、この確認を使うか広げる
  - 承認待ちで、承認待ちの承認者の資格がある操作者の承認だけを受け付け、次の承認者があればその承認待ちに、なければ承認済みにする。状態の更新(2.5)と履歴の追加を同じトランザクションに入れ、履歴は更新が 1 行のときだけ追加する。履歴は操作者・代理元(本人の操作では空)・操作・日時(日本時間)を 1 件記録する
  - 承認待ちでない申請の承認は A03、資格がなければ A01 で拒否し、状態と履歴を変えない。課長・部長が自分の 10,000 円以上の申請を承認しようとすると A01 にする
  - 統合テストで、担当者の 1,000,001 円を課長 → 部長 → 役員の順に承認して承認済みになり履歴が 3 件、10,000 円・100,000 円・1,000,000 円の各帯の最後の承認で承認済み、別の部署の課長・部長と役割違いは A01、差し戻し・承認済み・発注済みの承認は A03、拒否のときは履歴が増えない
  - _Depends: 1.3, 2.2, 2.5, 3.1_
  - _Requirements: 2.2, 2.3, 2.4, 2.5, 2.6, 2.8, 2.9, 2.10, 2.11, 2.13, 2.14, 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 3.7, 9.1, 9.3, 9.5_

- [x] 3.3 履歴の一覧を返す
  - 申請の ID から履歴を古い順に返す処理を足す。1 件ごとに操作者・代理元・操作・日時(日本時間)・理由を持つ
  - 統合テストで、承認を 2 回行った申請の履歴が古い順に 2 件返り、日時が日本時間で、本人の操作の代理元が空になる
  - _Depends: 3.2_
  - _Requirements: 9.1, 9.3_

- [x] 3.4 理由をつけて差し戻す
  - 3.2 の `checkOperation` による承認と同じ権限・状態の確認の後で、理由の前後の空白(半角と全角)を除いて空なら A02 で拒否する
  - 差し戻しにして、理由を履歴に残す。更新と履歴の追加は承認と同じ方式で行う
  - 承認者でないとき(別の部署の課長・部長を含む)は A01、承認待ちでないときは A03 で拒否し、状態と履歴を変えない
  - 統合テストで、理由ありは差し戻しになり理由が履歴に残り、理由なし・半角の空白だけ・全角の空白だけは A02、2 段階目の承認者の差し戻しでは先の承認の履歴が残る
  - _Depends: 3.2_
  - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5, 4.6, 9.1, 9.4, 9.5_

- [x] 3.5 代理人が承認と差し戻しを行えるようにする
  - 承認と差し戻しで、操作者に本人の資格がないとき、今日有効な代理のうち代理元が資格を満たすものを探す。見つかれば本人が行ったときと同じ遷移にし、履歴の代理元に承認者を記録する。見つからなければ A01 にする
  - 代理元が承認できない申請(別の部署の申請を含む)は、代理期間中でも A01 にする
  - 代理設定の細部(誰が代理人になれるか、期間中の本人の承認、代理人が自分の申請を承認など)は未決定のため、制限を足さない
  - 統合テストで、期間の開始日 0:00 と終了日 23:59 は代理人の承認が通り、開始日の前日と終了日の翌日は A01、別の部署の申請は A01、代理人の操作では履歴の操作者が代理人・代理元が承認者、本人の操作では代理元が空になる
  - _Boundary: PurchaseRequestService, DelegationService(統合)_
  - _Depends: 2.3, 3.4_
  - _Requirements: 7.2, 7.3, 7.4, 7.5, 7.6, 9.2, 9.3_

- [x] 3.6 出し直しの権限と状態の確認を作る
  - 出し直しは申請者本人だけ、差し戻しの状態だけ受け付ける。申請者本人でなければ A01、差し戻しでなければ A03 で拒否し、状態と履歴を変えない。代理人は出し直しできない
  - 出し直した後の承認待ちの承認者は未決定のため、承認待ちにする遷移の書き込みは含めない(この task の範囲の外。「保留する要件」を参照)
  - 統合テストで、申請者以外の出し直しは A01、承認待ち・承認済み・発注済みの出し直しは A03、承認者の代理人による出し直しは A01 になり、いずれも状態と履歴が変わらない
  - _Depends: 3.5_
  - _Requirements: 5.2, 5.3, 7.7_

- [x] 3.7 購買部が発注する
  - 購買部の社員が承認済みの申請を発注済みにし、操作者・操作・日時を履歴に記録する。代理人は発注できない
  - 購買部以外は A01、承認済みでない申請(承認待ち・差し戻し)は A03、発注済みの再発注は A03 で拒否し、履歴を増やさない
  - 発注済みの申請への承認・差し戻し・出し直し・発注はすべて A03 で拒否し、状態と履歴を変えない
  - 統合テストで、購買部の発注で状態が発注済みになり履歴が 1 件増え、上の拒否のケースで状態も履歴も変わらない
  - _Depends: 3.2, 3.6_
  - _Requirements: 6.1, 6.2, 6.3, 6.4, 6.5, 7.7, 9.1, 9.5_

- [x] 3.8 承認一覧に操作の可否を付けて返す
  - 全ての申請を、閲覧者ごとの承認・差し戻し・出し直し・発注の 4 つの可否つきで返す。可否は各操作と同じ判定(代理期間中の代理人を含む)を呼んで求める
  - 表示項目は暫定で ID・申請者・申請金額・状態・承認待ちの役割だけにする(未決定のため増やさない)
  - 統合テストで、承認待ちの承認者本人と代理期間中の代理人だけが承認と差し戻しが true、差し戻しの申請の申請者だけが出し直しが true、承認済みの申請を見る購買部だけが発注が true になる
  - _Depends: 3.5, 3.6, 3.7_
  - _Requirements: 8.1, 8.2, 8.3, 8.4_

- [ ] 4. Integration: API の組み立て
- [x] 4.1 購買申請の API を組み立てて、画面を使わない操作でも権限と状態が確かめられるようにする
  - 申請・一覧・承認・差し戻し・出し直し・発注・履歴の 7 つのエンドポイントを、業務のサービスにつなぐ(controller から repository は呼ばない)。出し直しは、権限と状態の確認(A01・A03)までを返し、成功の応答は「保留する要件」が決まってから足す
  - 存在しない申請は 404、入力の不正は 400(コードなし)、A01・A02・A03 は定義した HTTP の状態で返す
  - MockMvc のテストで、各エンドポイントの正常な応答(出し直しを除く)と上のエラー応答を確かめ、一覧で操作できないとされた操作を API で直接呼ぶと A01 か A03 で拒否され、状態も履歴も変わらない
  - _Boundary: PurchaseRequestController(統合)_
  - _Depends: 1.3, 3.3, 3.8_
  - _Requirements: 1.4, 1.5, 1.6, 4.2, 5.2, 6.2, 8.4, 9.5_

- [ ] 5. Frontend: 承認の画面
- [x] 5.1 承認一覧の画面と経路・メニューを足す
  - `/approvals` の経路と、ヘッダーのメニューを足す
  - 操作者の ID は、既存の画面と同じくログイン情報(localStorage の `token`)の顧客 ID から取り、役割を社員の API で取る。承認一覧を表示し、承認・差し戻し・出し直し・発注のボタンは、API が返した可否のとおりに有効または無効にする。差し戻しは理由の入力を求める。業務ルールは画面に持たない
  - 履歴を申請ごとに確かめられる表示を足す
  - フロントエンドのビルド(型の検査を含む)が通る。サンプルの課長でログインして `/approvals` を開くと、承認待ちの申請の承認と差し戻しのボタンが有効になる(画面のテストは作らず、開発サーバーで目視する)
  - _Boundary: ApprovalPage, App routing, Header_
  - _Depends: 2.4, 4.1_
  - _Requirements: 8.1, 8.2, 8.3_

- [x] 5.2 申請の入力と代理設定の画面を足す
  - 明細(単価・数量)を複数行入力して申請するフォームと、代理人・開始日・終了日を入力して代理を設定するフォームを、承認の画面に足す
  - API の拒否(A01・A02・A03・400)のメッセージを画面に表示する
  - フロントエンドのビルドが通り、画面から申請すると一覧に承認待ちの申請が増え、代理を設定すると 201 の応答を得る(開発サーバーで目視する)
  - _Boundary: ApprovalPage_
  - _Depends: 2.3, 5.1_
  - _Requirements: 1.1, 7.1_

- [ ] 6. Validation: 同時実行と全体の流れ
- [x] 6.1 同じ申請への同時の操作が 1 回分だけ反映されることを確かめる
  - 複数のスレッドから同時に呼ぶテストを作る
  - 承認者本人と代理人が同じ承認待ちの申請を同時に承認 → 状態が 1 回分だけ進み、履歴が 1 件
  - 承認と差し戻しが同時 → 先の 1 回分だけ反映され、後の操作は A03 で履歴が増えない
  - 購買部の 2 人が同じ承認済みの申請を同時に発注 → 発注済みで履歴が 1 件
  - 出し直しの同時 2 回のテストは、出し直しの遷移が決まってから足す(「保留する要件」を参照)
  - 上の 3 つのテストが繰り返して実行しても通る
  - _Depends: 3.4, 3.5, 3.7_
  - _Requirements: 10.1, 10.2, 10.3, 10.5_

- [x] 6.2 申請から発注までの全体の流れを API で通して確かめる
  - 担当者が 1,000,001 円を申請し、課長・部長(部長は代理人が承認)・役員の順に承認し、購買部が発注するまでを API だけで実行し、各段階の状態を確かめる
  - 別の流れで、2 段階目の承認者が理由つきで差し戻し、先の承認の履歴が残ることを確かめる
  - 全体のテスト(`./mvnw test`)が通り、履歴の件数・操作者・代理元が期待どおりになる
  - _Depends: 4.1, 6.1_
  - _Requirements: 2.6, 3.1, 3.2, 3.3, 4.6, 6.1, 7.2, 9.1, 9.2, 9.3_

## 保留する要件(requirements.md の未決定による)

- 5.1, 5.4: 出し直しの後の承認待ちの承認者が未決定のため、差し戻しから承認待ちへの遷移を書き込む task を置かない。`pending_role` は PENDING のとき NULL にできないため、値が決まるまで実装できない。決まったら requirements.md を更新し、出し直しの遷移と、5.1・5.4 のテストを追加する task を足す
- 10.4: 出し直しの同時 2 回は、上の遷移が決まってから 6.1 に足す
- 5.1・5.4・10.4 が決まって task が足されるまで、この spec は完了としない(`/kiro-validate-impl` で未達として扱う)

## Implementation Notes

- 履歴の操作の列挙型は design.md に合わせて `HistoryAction`(APPROVE, RETURN, RESUBMIT, ORDER)。状態は `RequestStatus`(PENDING, RETURNED, APPROVED, ORDERED)、役割は `EmployeeRole`(STAFF, SECTION_MANAGER, DEPARTMENT_MANAGER, EXECUTIVE, PURCHASING)
- コードなしの 400 は `InvalidApprovalRequestException`(本文は `{message}` だけ)。業務の拒否は `ApprovalException(ApprovalError)`
- サンプルの顧客 1-3 が営業部の担当者・課長・部長、4-6 が総務部の担当者・課長・部長、7 が役員、8 が購買部(`R__sample_employees.sql`)。テストは `db/migration` だけで、サンプルは入らない
- テスト補助: `TestDataHelper.createEmployee(customerId, department, role)`、`createRequest(applicantId, status, pendingRole, Item...)`。`reset()` は 5 つの新しいテーブルを明示的に空にする
- `ApprovalHistoryRepository` は追記専用で `Repository` を継承(`save`・`findById`・`findByRequestIdOrderByIdAsc` だけ)
- `PurchaseRequest` は更新用のセッターを持たない。状態の更新は 2.5 の条件付き UPDATE で行う
- `ApprovalPolicy.route` は対象外の申請者の役割で `IllegalArgumentException` を投げる(design.md のとおり)。3.1 は `route` を呼ぶ前に役割を検査するか、この例外を `InvalidApprovalRequestException` に変えて 500 にしない。`totalAmount` の桁あふれは `InvalidApprovalRequestException`。`ItemInput(long unitPrice, int quantity)` は `approval/dto`
- 資格の判定は `ApprovalPolicy.isEligibleApprover(PurchaseRequest, Employee applicant, Employee approver)`(DB を使わない)。本人かどうかは `Employee.getCustomerId()` の比較
- `DelegationService.activeFor(long delegateId, LocalDate today)` は日付を引数に取る(呼び出し側が `LocalDate.now(clock)` で渡す)。社員がいないときは `EmployeeNotFoundException`(404、本文は `{message}`)。代理の登録は POST `/api/approval/delegations`
- approval のクラスは EC のクラス(`CustomerRepository` など)を参照しない(design.md の Allowed Dependencies)。customer テーブルの値(名前)が要るときは approval 側のリポジトリのネイティブクエリで ID だけを使って取る(`EmployeeRepository.findWithName`)
- 状態の更新は `PurchaseRequestRepository.advance(id, fromStatus, fromRole, toStatus, toRole)`(更新行数を返す)。`@Modifying(flushAutomatically, clearAutomatically)` のため、呼び出しは `@Transactional` の中で行う(外では `TransactionRequiredException`)
- `PurchaseRequestService.submit(applicantId, List<ItemInput>)` は `PurchaseRequestView(id, applicantId, totalAmount, status, pendingRole)` を返す。申請者が存在しないときは `EmployeeNotFoundException`(404、design.md に定めがないための暫定)。`PurchaseRequestView` の可否フラグは 3.8 で足す
- `PurchaseRequestService.approve(requestId, actorId)`。状態と権限の確認は private の `checkOperation(request, applicant, actor)`(PENDING でなければ A03、資格がなければ A01)。UPDATE が 0 行なら A03 で履歴を足さず戻す。存在しない申請は `RequestNotFoundException`(404、`{message}`)。履歴の日時は `OffsetDateTime.now(clock)`。更新後の戻り値は `findById` で読み直す(`advance` が永続性コンテキストを空にするため)
- `PurchaseRequestService.history(requestId)` は `List<HistoryView>`(id, actorId, onBehalfOfId, action, actedAt(+09:00), reason)を古い順に返す。存在しない申請は `RequestNotFoundException`
- 差し戻しは `PurchaseRequestService.returnRequest(requestId, actorId, reason)`(design.md の名前)。承認と差し戻しは private の `advanceWithHistory`(条件付き UPDATE → 0 行なら A03 → 履歴 → 読み直し)を共有する。履歴に残す理由は入力そのまま(空の判定だけ `strip()`)
- 代理は `checkOperation` が実効の代理元(本人の資格があれば null)を返す形で統合済み。`LocalDate.now(clock)` の有効な代理を id 昇順に見て、代理元が `isEligibleApprover` を満たす最初のものを使う。履歴の on_behalf_of に代理元を記録。3.6・3.7・3.8 は代理人を認めない操作(出し直し・発注)と認める操作(承認・差し戻し)を区別すること
- 出し直しは check-only の `PurchaseRequestService.checkResubmit(requestId, actorId)`(void。A03 → A01 の順に検査し、書き込みと代理の検索はしない)。design.md の `resubmit`(承認待ちへの遷移を含む)は「保留する要件」の 5.1・5.4 が決まってから足す。4.1 の出し直しのエンドポイントはこの確認までを返す
- 発注は `PurchaseRequestService.order(requestId, actorId)`(購買部だけ・代理なし・APPROVED だけ)。`advanceWithHistory` は元の状態と元の承認待ちの役割を引数に取る
- 一覧は `PurchaseRequestService.list(viewerId)` が `List<PurchaseRequestListView>`(id, applicantId, totalAmount, status, pendingRole, canApprove, canReturn, canResubmit, canOrder)を id 昇順で返す。design.md の `List<PurchaseRequestView>` とは型名が違う(意図した差。`PurchaseRequestView` は可否を持たない)。4.1 はこの型を使う。可否は各操作と同じ private の確認(`checkOperation`・`checkResubmit`・`checkOrder`)から求める
- API は `/api/approval/requests`(POST 申請 201、GET 一覧 `?viewerId=`、POST `{id}/approve`・`{id}/return`(本文 `{actorId, reason}`)・`{id}/resubmit`・`{id}/order`(本文 `{actorId}`)、GET `{id}/history`)。出し直しは A03/A01 の確認の後に 501 `{message}`(成功の応答は「保留する要件」が決まってから)。申請の本文は `{applicantId, items:[{unitPrice, quantity}]}` で、単価・数量の欠落は 400(コードなし)
- 5.1 のブラウザでの目視(サンプルの課長 `sato@example.com` / 123 でログインして `/approvals` を開き、承認待ちの申請で承認と差し戻しのボタンが有効になること)は未実施。Playwright の許可が下りなかったため。ビルドと API の契約の突き合わせだけ済んでいる。`ApprovalPage` は `window.prompt` で差し戻しの理由を聞き、API の `message` をそのまま表示する
- 5.2 も画面からの目視(申請すると一覧に承認待ちの申請が増える・代理を設定すると 201)は未実施。API の契約は curl で、画面の型はビルドで確かめた。代理の API は、開始日が終了日より後でも 201 を返す(未決定のため。仕様どおり)
- 同時の承認と差し戻しで、先の操作が承認だった場合、後の操作は状態を読み直すと承認待ちの役割が進んでいるため A01 になることがある(UPDATE が 0 行になる読み取り直後の競合では A03)。requirements 10.2 の「A03」の文言と実装は厳密には一致しない。6.1 のテストは A01 または A03 を許している。文言を揃えるかどうかは requirements.md の判断
