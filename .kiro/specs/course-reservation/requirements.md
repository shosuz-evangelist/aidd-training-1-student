# Requirements Document

## Project Description (Input)
店舗の講座（単発・連続）の予約とキャンセルを足す。

- 課題を持つ人: 店で開く講座に参加したいお客様と、講座を運営する店の担当者
- 現状: EC サイトには商品の購入しかなく、講座の席を予約する手段がない
- 変えたいこと: お客様が単発・連続の講座の席を予約でき、必要なときにキャンセルできるようにする（キャンセル料などの詳細は requirements の段階で決める）

入力資料: 変更仕様書「講座予約機能追加」（版 1.0）。対象は 講座一覧 / 講座予約 / 予約一覧 / 講座登録（担当者）の 4 つ。

## Introduction
お客様が店で開く講座の席を予約し、キャンセル規定に従ってキャンセルできるようにする。店の担当者は、講座とその各回（開始日時・終了日時・教室・講師）を登録する。講座には、1 回で終わる単発と、複数回で構成する連続がある。連続講座は 1 件の予約で全回の席を確保する。

## Boundary Context
- **In scope**: 講座一覧の表示、講座の予約、予約一覧の表示、予約のキャンセルとキャンセル料の算出、担当者による講座の登録とその入力検査、エラーコード R01 から R09 の表示
- **Out of scope**: 受講料やキャンセル料の決済と返金の処理、材料費の扱い（別途検討）、登録済みの講座の変更と削除、連続講座の途中の回だけを対象にした予約とキャンセル、講座の検索と絞り込み
- **Adjacent expectations**: 本機能は、既存の EC サイトのログインと顧客の識別に依存し、予約とキャンセルはログイン中のお客様本人の操作として扱う。ログインと顧客の管理は本機能の対象外とする

## 用語
- **回**: 講座を構成する 1 回分の開催。開始日時・終了日時・教室・講師を持つ。単発講座は 1 回、連続講座は 2 回以上を持つ
- **残席**: 定員から、キャンセルしていない予約の件数を引いた数
- **有効な予約**: キャンセルしていない予約
- **重なる**: 2 つの時間帯が一部でも重なること。一方の終了時刻と他方の開始時刻が同じ場合は重ならない
- **日時**: すべて日本時間（Asia/Tokyo）で扱う。日付の区切りも日本時間とする

## Requirements

### Requirement 1: 講座の情報
**Objective:** As a 店の担当者, I want 講座を種類・カテゴリ・受講料・定員と各回の情報で管理したい, so that お客様に開催内容を正しく示せる

#### Acceptance Criteria
1. The Course Reservation Service shall 講座ごとに、種類（単発 / 連続）、カテゴリ、受講料（円の整数）、定員を保持する
2. The Course Reservation Service shall 講座ごとに 1 回以上の回を保持し、回ごとに開始日時、終了日時、教室、講師を保持する
3. Where 講座の種類が単発である, the Course Reservation Service shall その講座の回を 1 回とする

### Requirement 2: 講座一覧の表示
**Objective:** As a お客様, I want 講座の内容と残席を一覧で見たい, so that 予約する講座を選べる

#### Acceptance Criteria
1. When お客様が講座一覧を開く, the Course Reservation Service shall 講座ごとに、講座名、カテゴリ、種類、日程（全回の開始日時と終了日時）、受講料（円）、残席を表示する
2. While 講座の残席が 1 以上である, the Course Reservation Service shall 残席を「残り n 席」（n は残席の数）の形で表示する
3. While 講座の残席が 0 である, the Course Reservation Service shall 残席の表示を「満席」とする
4. While 講座の残席が 0 である, the Course Reservation Service shall その講座の予約ボタンを押下できない状態にする
5. While 現在日時が第 1 回の開始日時の 1 時間前を過ぎている, the Course Reservation Service shall その講座の予約ボタンを押下できない状態にする

### Requirement 3: 予約の受付条件
**Objective:** As a お客様, I want 予約できない理由が分かるようにしたい, so that 別の講座や別の時間を選び直せる

#### Acceptance Criteria
1. When お客様が講座の予約ボタンを押下する, the Course Reservation Service shall 次の検査を番号の順に行い、最初に満たさなかった検査のエラーを返して予約を登録しない
   1. 残席が 1 以上である
   2. 現在日時が、第 1 回の開始日時の 1 時間前の時刻以前である
   3. お客様が同じ講座の有効な予約を持っていない
   4. お客様の有効な予約の講座のいずれとも、時間帯が重ならない
2. If 残席が 0 である, then the Course Reservation Service shall エラー R01「満席です」を返す
3. If 現在日時が第 1 回の開始日時の 1 時間前の時刻より後である, then the Course Reservation Service shall エラー R02「受付を締め切りました」を返す
4. When 現在日時が第 1 回の開始日時の 1 時間前の時刻とちょうど同じである, the Course Reservation Service shall 予約を受け付ける
5. If お客様が同じ講座の有効な予約をすでに持っている, then the Course Reservation Service shall エラー R03「すでに予約しています」を返す
6. If 予約しようとする講座の時間帯が、お客様の有効な予約の講座の時間帯と重なる, then the Course Reservation Service shall エラー R04「時間帯が重なります」を返す
7. The Course Reservation Service shall 連続講座の時間帯の重なりを、全回の時間帯で判定する（予約しようとする講座と予約済みの講座のいずれの回が重なっても R04 とする）
8. When 予約しようとする講座の第 1 回の終了時刻が、予約済みの講座の第 1 回の開始時刻と同じである, または第 1 回の開始時刻が予約済みの講座の第 1 回の終了時刻と同じである, the Course Reservation Service shall その 2 つの講座を重ならないものとして扱う
9. When お客様が以前にキャンセルした講座を予約する, the Course Reservation Service shall キャンセル済みの予約を R03 と R04 の判定に含めない

### Requirement 4: 予約の登録と席の確保
**Objective:** As a お客様, I want 予約すると席が確保されるようにしたい, so that 当日に参加できる

#### Acceptance Criteria
1. When 予約の受付条件をすべて満たした, the Course Reservation Service shall お客様の予約を 1 件登録し、残席を 1 減らす
2. When 連続講座の予約を登録する, the Course Reservation Service shall 1 件の予約で全回の席を確保する
3. When 複数のお客様が同時に同じ講座を予約する, the Course Reservation Service shall 有効な予約の件数が定員を超えないようにし、定員を超える分の予約にはエラー R01 を返す
4. If 予約がエラーで終わった, then the Course Reservation Service shall 予約の件数と残席を変えない
5. When お客様が同じ講座の予約をキャンセルした後に、再び予約する, the Course Reservation Service shall 他の予約と同じ受付条件で予約を受け付ける

### Requirement 5: 予約一覧の表示
**Objective:** As a お客様, I want 自分の予約とキャンセルした場合の金額を見たい, so that キャンセルするか判断できる

#### Acceptance Criteria
1. When お客様が予約一覧を開く, the Course Reservation Service shall お客様本人の有効な予約だけを、講座名、日程、キャンセル料とともに表示する
2. The Course Reservation Service shall 予約一覧のキャンセル料に、現在キャンセルした場合の金額（円）を、キャンセル規定（Requirement 6）に従って表示する
3. While 第 1 回の開始日の前日までである, the Course Reservation Service shall 予約一覧のキャンセル料を 0 円とする
4. While 第 1 回の開始日の当日で、かつ開始時刻より前である, the Course Reservation Service shall 予約一覧のキャンセル料を受講料の 50 %（1 円未満は切り捨て）とする
5. While 第 1 回の開始時刻を過ぎている（開始時刻ちょうどを含む）, the Course Reservation Service shall キャンセルできないことが分かる表示にする

### Requirement 6: キャンセルとキャンセル料
**Objective:** As a お客様, I want 規定に従って予約をキャンセルしたい, so that 参加できなくなったときに席を手放せる

#### Acceptance Criteria
1. When お客様が予約一覧でキャンセルを押下し、キャンセル規定上キャンセルできる, the Course Reservation Service shall その予約をキャンセル済みにし、残席を 1 増やす
2. The Course Reservation Service shall キャンセル料の算出に使う日付の区切りを日本時間とし、連続講座も第 1 回の開始日を基準とする
3. When 現在日時が第 1 回の開始日の前日以前の日付である, the Course Reservation Service shall キャンセル料を 0 円としてキャンセルを受け付ける
4. When 現在日時が第 1 回の開始日と同じ日付で、かつ第 1 回の開始時刻より前である, the Course Reservation Service shall 受講料の 50 %（1 円未満は切り捨て）をキャンセル料としてキャンセルを受け付ける
5. If 現在日時が第 1 回の開始時刻以降である（開始時刻ちょうどを含む）, then the Course Reservation Service shall エラー R05「開始後はキャンセルできません」を返し、予約を変えない
6. If キャンセルしようとする予約がお客様本人のものではない, then the Course Reservation Service shall エラー R06「予約が見つかりません」を返し、予約を変えない
7. The Course Reservation Service shall 連続講座のキャンセル料を、全回分の受講料を基準に算出する
8. While 材料費が未定である, the Course Reservation Service shall キャンセル料を受講料だけで計算する

### Requirement 7: 連続講座の途中の回の欠席
**Objective:** As a お客様, I want 連続講座の途中の回だけを休めるようにしたい, so that 都合の悪い回があっても講座を続けられる

#### Acceptance Criteria
1. When お客様が連続講座の予約を持ったまま途中の回に出席しない, the Course Reservation Service shall その予約を有効なまま保つ
2. The Course Reservation Service shall 休んだ回の受講料を返金しない
3. The Course Reservation Service shall 休んだ回を、予約のキャンセルとして扱わない（残席とキャンセル料は変わらない）

### Requirement 8: 講座の登録（担当者）
**Objective:** As a 店の担当者, I want 講座とその各回を、誤りなく登録したい, so that 教室や講師の二重の割り当てを防げる

#### Acceptance Criteria
1. When 担当者が講座を登録する, the Course Reservation Service shall 次の検査を番号の順に行い、最初に満たさなかった検査のエラーを返して講座を登録しない
   1. すべての回で、開始日時が終了日時より前である
   2. 定員が 1 以上である
   3. 同じ教室で時間帯が重なる回がない
   4. 同じ講師で時間帯が重なる回がない
2. If いずれかの回の開始日時が終了日時以降である, then the Course Reservation Service shall エラー R07「入力が正しくありません」を返す
3. If 定員が 1 未満である, then the Course Reservation Service shall エラー R07「入力が正しくありません」を返す
4. If 登録する回の時間帯が、同じ教室の他の回（登録済みの講座の回と、同じ登録内の他の回）と重なる, then the Course Reservation Service shall エラー R08「教室が重なります」を返す
5. If 登録する回の時間帯が、同じ講師の他の回（登録済みの講座の回と、同じ登録内の他の回）と重なる, then the Course Reservation Service shall エラー R09「講師が重なります」を返す
6. When 一方の回の終了時刻が他方の回の開始時刻と同じである, the Course Reservation Service shall その 2 つの回を、教室と講師の検査で重ならないものとして扱う
7. If 講座の登録がエラーで終わった, then the Course Reservation Service shall 講座と回を 1 つも登録しない

### Requirement 9: エラーの表示
**Objective:** As a お客様または担当者, I want エラーの理由を分かる文言で知りたい, so that 入力や操作を直せる

#### Acceptance Criteria
1. When 予約、キャンセル、講座の登録がエラーになる, the Course Reservation Service shall 次のエラーコードとメッセージの組を返す
   - R01 満席です
   - R02 受付を締め切りました
   - R03 すでに予約しています
   - R04 時間帯が重なります
   - R05 開始後はキャンセルできません
   - R06 予約が見つかりません
   - R07 入力が正しくありません
   - R08 教室が重なります
   - R09 講師が重なります
2. The Course Reservation Service shall 日時の判定をすべて日本時間で行う

## 未決定の事項
変更仕様書に決めがなく、本 spec でも決めないもの。実装で決めずに、決まってから requirements.md に追記する。

- **材料費**: 変更仕様書 C-6 のとおり未定。決まるまでキャンセル料は受講料だけで計算する（Requirement 6 の 8）
- **キャンセル済みの予約を再度キャンセルした場合**: エラーの種類が決まっていない
- **担当者の権限**: 講座登録を行える担当者の判定の仕方が決まっていない
- **受講料の範囲**: 0 円や負の値を受け付けるかが決まっていない
- **予約処理 No.4 の「境界を含む」**: 変更仕様書 C-4 は、終了時刻と開始時刻が同じ場合を重ならないと定めている。本 spec は C-4 に従い、時間帯が接するだけの講座は重ならないものとして扱う（Requirement 3 の 8）
