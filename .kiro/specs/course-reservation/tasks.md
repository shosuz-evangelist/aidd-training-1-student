# Implementation Plan

- [ ] 1. Foundation: テーブル・エラー・永続化層・テスト基盤
- [x] 1.1 講座・回・予約のテーブルとサンプルデータを作る
  - 3 テーブルを Flyway の V2 で作る。courses は kind と capacity と reserved_count の CHECK 制約、course_sessions は (course_id, session_no) の一意制約と starts_at < ends_at の CHECK 制約と教室・講師の索引、reservations は ACTIVE の予約だけを対象にした (customer_id, course_id) の部分一意索引を持つ。price に範囲の制約は付けない
  - 動作確認用の講座（単発・連続・満席に近いもの）を db/sample の R__ のファイルに置く
  - Observable: 開発用とテスト用の両方のデータベースにマイグレーションが適用され、ddl-auto=validate でアプリケーションが起動する
  - _Requirements: 1.1, 1.2, 3.9, 4.3, 4.5_

- [x] 1.2 エラーコード R01 から R09 とその HTTP 応答を用意する
  - 9 つのコード・メッセージ・HTTP ステータスを持つ ReservationError と ReservationException を作る
  - 既存の例外の受け口に ReservationException の処理を足し、code と message を JSON で返す
  - Observable: ReservationException を投げるテストで、9 つすべてについて仕様どおりのコード・メッセージ・ステータスが返る
  - _Requirements: 9.1_

- [x] 1.3 講座・回・予約のエンティティを作る
  - Course（回を持つ）、CourseSession、Reservation と、種類と状態の列挙を作る。Course に、残席が 1 以上かつ第 1 回の開始の 1 時間前以前かを返す受付可否の判定を置く
  - テストのデータ初期化の TRUNCATE に reservations, course_sessions, courses を足す
  - テストを先に書く（Course の単体テスト）: 受付可否の判定が、1 時間前ちょうどを可、1 秒後を不可、残席 0 を不可とする
  - Observable: ddl-auto=validate でエンティティとテーブルが一致して起動し、受付可否の単体テストが通る。既存のテストの初期化が新しい 3 テーブルも空にする
  - _Depends: 1.1_
  - _Requirements: 1.1, 1.2, 2.4, 2.5_

- [x] 1.4 講座・回・予約のリポジトリを作る
  - 講座の取得と reserved_count の条件付き増減、教室・講師が重なる回の検索、お客様の有効な予約の取得、ACTIVE のときだけ行うキャンセルの条件付き更新を用意する
  - Observable: アプリケーションが起動し、すべてのリポジトリの問い合わせが起動時の検証を通る
  - _Depends: 1.3_
  - _Requirements: 3.9, 4.3_

- [x] 1.5 予約のテストの基盤を用意し、永続化層を確かめる
  - 時刻を固定した Clock、講座・回・予約・お客様を作る補助を持つ ReservationTestBase を作る
  - テストを先に書く（永続化層のテスト）: 条件付きの席の確保が定員に達すると 0 行を返す、教室・講師の検索が重なる回だけを返す、ACTIVE でない予約への条件付きのキャンセル更新が 0 行を返す
  - Observable: 基盤を継承した永続化層のテストが、固定した現在日時のもとで講座を作り、テストごとにデータが空に戻ることを確かめて通る
  - _Depends: 1.4_
  - _Requirements: 4.3, 9.2_

- [ ] 2. Core: 純粋な計算
- [x] 2.1 (P) 時間帯の重なりの判定を作る
  - 開始と終了の組と、「一方の開始が他方の終了より前、かつ他方の開始が一方の終了より前」の判定を作る。終了と開始が同じなら重ならない
  - テストを先に書く（TimeRange の単体テスト）: 一部が重なる、完全に含む、終了と開始が同じ（重ならない）、離れている
  - Observable: 単体テストが 4 つの場合すべてで期待どおりの結果を返して通る
  - _Boundary: TimeRange_
  - _Requirements: 3.7, 3.8, 8.6_

- [x] 2.2 (P) キャンセルの可否とキャンセル料の算出を作る
  - 現在日時・第 1 回の開始日時・受講料から、キャンセルできるかと料金を求める。開始時刻以降（ちょうどを含む）は不可、日本時間の日付が開始日より前なら 0 円、同じ日なら受講料 × 50 % の 1 円未満切り捨て
  - 連続講座でも受講料に回数を掛けない。材料費は含めない
  - テストを先に書く（CancellationPolicyTest）: 前日 0 円、当日の開始前 50 %、受講料 1,001 円で 500 円、開始時刻ちょうどが不可、日本時間の 0 時の前後
  - Observable: 単体テストが上記の境界すべてで期待どおりの結果を返して通る
  - _Boundary: CancellationPolicy_
  - _Requirements: 5.2, 5.3, 5.4, 5.5, 6.2, 6.3, 6.4, 6.5, 6.7, 6.8, 9.2_

- [ ] 3. Core: 講座（一覧と登録）
- [x] 3.1 講座一覧の API を作る
  - CourseService に一覧の組み立てを置く。講座名・カテゴリ・種類・全回の日程・受講料・残席を返し、残席は「残り n 席」または「満席」、予約ボタンの可否は Course の受付可否の判定から求める。開始日時の順に並べる
  - 一覧の行（CourseView）と回の表示（SessionView）の dto をここで作る
  - 一覧を返す GET を CourseController に置く
  - テストを先に書く（CourseApiTest）: 表示項目、残席 1 以上と 0 の文言、満席と締め切り後の可否
  - Observable: GET /api/reservation/courses が仕様どおりの項目を返し、CourseApiTest が通る
  - _Depends: 1.5_
  - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5_

- [x] 3.2 講座の登録の API を作る（基本の検査と保存）
  - 8.1 の順のうち、開始が終了より前（R07）と定員が 1 以上（R07）を検査し、最初の失敗を返す。検査を通った講座と回を 1 つのトランザクションで保存し、回の番号は入力の順に 1 から振る
  - 種類と回の数が合わない入力（単発で 2 回以上、回が 0 件）は未決定の事項のため、検査も変換もしない
  - 登録の入力（CourseRequest）の dto をここで作る
  - POST を CourseController に置く
  - テストを先に書く（CourseRegistrationTest の前半）: R07 の 2 通り、検査の順序、正しい入力で講座と回が保存される。1.3 は有効な単発の入力（回が 1 つ）だけで確かめる
  - Observable: POST /api/reservation/courses が 201 で講座を返し、R07 で 400 を返して何も保存せず、該当のテストが通る
  - _Depends: 1.2, 3.1_
  - _Requirements: 1.1, 1.2, 1.3, 8.1, 8.2, 8.3, 8.7_

- [x] 3.3 講座の登録に教室と講師の重なりの検査を足す
  - 3 番目と 4 番目の検査として、教室の重なりを R08、講師の重なりを R09 で返す。登録済みの回（CourseSessionRepository）と同じ登録内の他の回の両方と、TimeRange で比べる。終了と開始が同じなら重ならない
  - エラーのとき講座も回も保存しない
  - テストを先に書く（CourseRegistrationTest の後半）: R08、R09、検査の順序（R07 が先）、同じ登録内の重なり、接するだけは登録できる、エラー時に講座も回も残らない
  - Observable: 教室または講師が重なる登録が 409 になって何も残らず、CourseRegistrationTest が通る
  - _Depends: 2.1, 3.2_
  - _Requirements: 8.1, 8.4, 8.5, 8.6, 8.7_

- [ ] 4. Core: 予約（受付・一覧・キャンセル）
- [x] 4.1 予約の受付（満席・締め切り・二重予約）と席の確保を作る
  - ReservationService.reserve に、残席 → 締め切り → 二重予約の順の検査を置く。席の確保は reserved_count の条件付き UPDATE で行い、0 行なら R01、部分一意索引の違反は R03 に変換する。予約を ACTIVE で 1 件登録し、連続講座も 1 件で全回の席を確保する
  - 締め切りの検査は、1.3 で作った Course の受付可否の判定を使い、講座一覧の予約ボタンの条件と食い違わないようにする
  - 応答の ReservationView（講座名・日程・受講料・キャンセル料・キャンセルの可否）と、その部品の dto をここで作る。キャンセル料と可否は 2.2 の算出から求める
  - POST を ReservationController に置く。エラー時は予約の件数と reserved_count を変えない
  - テストを先に書く（ReservationReserveTest）: R01、1 時間前ちょうどは受付で 1 秒後は R02、R03、検査の順序（R01 と R02 が同時なら R01）、エラー時に変わらない
  - Observable: POST /api/reservation/reservations が 201 を返して残席が 1 減り、各エラーで 409 を返し、ReservationReserveTest が通る
  - _Depends: 1.2, 1.5, 2.2_
  - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 4.1, 4.2, 4.4_

- [x] 4.2 予約の受付に時間帯の重なりの検査を足す
  - 4 番目の検査として、お客様の有効な予約の講座の全回と、予約しようとする講座の全回を TimeRange で比べ、重なれば R04 を返す。接するだけなら予約できる
  - キャンセル済みの予約は R03 と R04 の判定に含めない。キャンセル後の再予約は新しい予約として受け付ける
  - テストを先に書く（ReservationOverlapTest）: 連続講座は全回で判定、接するだけは予約できる、キャンセル済みは含めない、キャンセル後の再予約
  - Observable: 重なる講座の予約が R04（409）になり、ReservationOverlapTest が通る
  - _Depends: 2.1, 4.1_
  - _Requirements: 3.1, 3.6, 3.7, 3.8, 3.9, 4.5_

- [x] 4.3 同時予約で定員を超えないことを確かめる
  - 残席 1 の講座を複数のスレッドから同時に予約するテストを作る（ReservationConcurrencyTest）
  - 成功が 1 件、ほかは R01、ACTIVE な予約の件数が定員を超えず reserved_count と一致することを確かめる
  - Observable: ReservationConcurrencyTest を 10 回続けて実行し、すべて通る
  - _Depends: 4.1_
  - _Requirements: 4.3_

- [x] 4.4 予約一覧の API を作る
  - ReservationService.list が、本人の ACTIVE な予約だけを、講座名・日程・受講料・現在のキャンセル料・キャンセルの可否とともに返す。キャンセル料と可否は CancellationPolicy から求め、キャンセルできないときの料金は 0 とする。ReservationView への変換は 4.1 で作ったものを再利用する
  - GET を ReservationController に置く
  - テストを先に書く（ReservationCancelTest の一覧の部分）: 本人の有効な予約だけが出る、前日 0 円、当日 50 %、開始後は cancellable が false
  - Observable: GET /api/reservation/reservations?customerId= が仕様どおりの金額と可否を返し、該当のテストが通る
  - _Depends: 2.2, 4.2_
  - _Requirements: 5.1, 5.2, 5.3, 5.4, 5.5_

- [x] 4.5 予約のキャンセルの API を作る
  - ReservationService.cancel が、本人の確認（R06）→ 開始時刻の確認（R05）→ 料金の算出の順に行う。ACTIVE のときだけ条件付きで CANCELLED にしてキャンセル料を保存し、reserved_count を 1 減らす
  - キャンセルの結果（CancelResult）の dto をここで作る
  - POST を ReservationController に置く。予約の状態を変える操作はキャンセルだけとし、途中の回の欠席や返金の処理は作らない
  - テストを先に書く（ReservationCancelTest のキャンセルの部分）: 本人以外は R06 で変わらない、開始後は R05、他人の開始後の予約は R06、残席が 1 戻る、連続講座の料金が受講料基準
  - 再キャンセルの扱いは未決定のため、実装とテストで決めない
  - Observable: POST /api/reservation/reservations/{id}/cancel が CancelResult を返して残席が 1 戻り、R05 は 409、R06 は 404 を返し、ReservationCancelTest が通る。予約の状態を変えるエンドポイントがキャンセルだけである
  - _Depends: 1.2, 4.4_
  - _Requirements: 6.1, 6.2, 6.3, 6.4, 6.5, 6.6, 6.7, 6.8, 7.1, 7.2, 7.3_

- [ ] 5. Integration: 画面
- [x] 5.1 講座一覧の画面を作る
  - GET の結果を表示し、reservable が false のとき予約ボタンを無効にする。予約のエラーは message を表示する。既存のログインがお客様の ID を保持している場所を先に確かめ、その ID を予約の要求に付ける
  - /courses のルートと、ヘッダーの「講座」のリンクを足す
  - Observable: 画面の起動後、/courses に講座が表示され、予約が成功すると残席の表示が減り、満席の講座の予約ボタンが押せない
  - _Depends: 3.1, 4.1_
  - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5, 3.1, 9.1_

- [x] 5.2 予約一覧の画面を作る
  - 本人の有効な予約と、講座名・日程・キャンセル料を表示する。cancellable が false のときはキャンセルボタンの代わりに「開始後はキャンセルできません」を表示する。キャンセルの前に cancelFee を入れた確認の文言を出す。エラーは message を表示する
  - /reservations のルートと、ヘッダーの「予約一覧」のリンクを足す
  - Observable: 画面の起動後、/reservations でキャンセル料の確認のあとにキャンセルでき、一覧から予約が消える
  - _Depends: 4.5, 5.1_
  - _Requirements: 5.1, 5.2, 5.5, 6.1, 6.5, 6.6, 9.1_

- [x] 5.3 講座登録の画面を作る
  - 講座の基本項目と、回の行を足せる入力フォームを作る。入力の検査は画面でせず、エラーの code と message を表示する
  - /courses/new のルートを足し、講座一覧の画面に担当者向けの登録画面へのリンクを置く
  - Observable: 画面の起動後、講座一覧のリンクから /courses/new を開いて講座を登録でき、教室が重なる入力では R08 のコードとメッセージが表示される
  - _Depends: 3.3, 5.2_
  - _Requirements: 8.1, 8.2, 8.3, 8.4, 8.5, 9.1_

- [ ] 6. Validation: 全体の確認
- [x] 6.1 全テストを実行し、受入基準とテストの対応を確かめる
  - バックエンドの全テスト（既存の EC・承認・定期便を含む）を実行する
  - 要件 1 から 9 の各受入基準に対応するテストがあること（要件 7 を除く）を、テストの一覧と突き合わせる
  - Observable: ./mvnw test が全件通り、画面の npm run build が成功する
  - _Depends: 5.3_
  - _Requirements: 1.1, 1.2, 1.3, 2.1, 2.2, 2.3, 2.4, 2.5, 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 3.7, 3.8, 3.9, 4.1, 4.2, 4.3, 4.4, 4.5, 5.1, 5.2, 5.3, 5.4, 5.5, 6.1, 6.2, 6.3, 6.4, 6.5, 6.6, 6.7, 6.8, 8.1, 8.2, 8.3, 8.4, 8.5, 8.6, 8.7, 9.1, 9.2_

## Implementation Notes

- Course.reservedCount は updatable=false。値を変えるのは CourseRepository の tryAcquireSeat / releaseSeat だけで、テストデータで 0 以外の値が要るときは insert 時に設定するか JDBC を使う（1.3）
- CourseRepository / ReservationRepository の @Modifying は clearAutomatically=true。呼んだ後に永続化コンテキストが空になるため、4.1 と 4.5 は更新後にエンティティを読み直す（1.4）
- findAllWithSessions に ORDER BY はない。講座一覧の開始日時順は CourseService で並べる（1.4）
- 再キャンセルの 0 行の扱いは cancelIfActive の呼び出し側（4.5）に残してあり、未決定の事項のまま（1.4）
- テスト用データベース ecsite_test_res_cs の Flyway 履歴が古いと起動が失敗する。作り直して解消する（1.3）
- ReservationTestBase: @Modifying のリポジトリ呼び出しはトランザクションの中が必要で、補助の tx() で包む。createReservation は reservedCount を変えないため、createCourse に整合した reservedCount を渡す。クラス全体に @Transactional は付けない（4.3 の同時実行で各スレッドから見えるようにするため）（1.5）
- seatLabel は「残り 」+ n + 「 席」（n の前後に半角スペース）。要件の表記どおりで、画面でもそのまま表示する（3.1）
- Jackson の日時の出力は spring.jackson.time-zone=Asia/Tokyo で +09:00 になる。dto の日時は OffsetDateTime のまま返す（3.1）
- 講座登録の入力で、回の startsAt / endsAt や name / kind が null のときの扱いは spec に決まりがない（現状は 500）。実装では決めない（3.2）
- 3.2 の時点で R07 の 2 つの検査は同じコードを返すため、検査の順序は外から区別できない。順序は R08・R09 を足す 3.3 で確かめる（3.2）
- 講座登録の重なりの検査は、教室（R08）をすべての回について調べてから講師（R09）を調べる。回ごとに教室→講師の順にはしない。テストで固定してある（3.3）
- ReservationService.reserve は 残席→締め切り→二重予約の検査のあとで tryAcquireSeat を呼び、呼んだあとに講座を読み直す。例外はトランザクション全体を戻すため reserved_count も戻る。4.2 の R04 は二重予約の検査のあと、席の確保の前に足す（4.1）
- 存在しない courseId・customerId の予約は spec に決まりがなく、現状は 500。実装では決めない（4.1）
- ReservationReserveTest は reservations の外部キーのため、テストごとにお客様を data.createCustomer で作る（4.1）
- ReservationService の firstSessionRange は Course.firstSessionStart() と第 1 回の取り出しが重複している。3 か所目で使うときは Course に firstSessionRange() を置いて共通にする（4.2）
- 予約の一覧（4.4）とキャンセル（4.5）では firstSessionRange の 3 か所目は増えなかったため、共通化していない
- ReservationService.cancel は cancelIfActive が 0 行のとき、R コードに割り当てない IllegalStateException を投げる（応答は未決定のまま。テストなし）。再キャンセルの応答は requirements.md に追記してから決める（4.5）
