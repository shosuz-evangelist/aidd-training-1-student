# Requirements Document

## Project Description (Input)
満席の講座にキャンセル待ちを足す。

- 課題を持つ人: 満席の人気講座に参加したいお客様と、問い合わせに対応する店の担当者
- 現状: 人気の講座はすぐ満席になり、「空いたら教えてほしい」という問い合わせが店に多い。店の担当者が名前を控えておき、キャンセルが出たら電話をかけている
- 変えたいこと: お客様が満席の講座のキャンセル待ちに登録でき、席が空いたら順番に案内され、期限までに予約できるようにする。店の担当者の控えと電話を不要にする

決まっていること（変えるときは店の担当者の合意が要る）
1. 登録できる条件: 残席が 0 の講座だけ、キャンセル待ちに登録できる。受付の締め切り（第 1 回の開始の 1 時間前）を過ぎた講座には登録できない。その講座の有効な予約を持っているお客様と、同じ講座のキャンセル待ちにすでに登録しているお客様は、登録できない
2. 上限: 1 つの講座のキャンセル待ちは 10 人まで。11 人目は登録できない
3. 順番: キャンセル待ちに登録した日時の早い順
4. 席が空いたとき: 予約がキャンセルされて席が 1 つ空いたら、順番が最初の 1 人に案内する。その席は案内した人のために確保し、確保している間は、ほかのお客様は予約できない（講座一覧では満席と表示する）
5. 確保の期限: 案内から 24 時間後と、受付の締め切りの早い方。案内された人は、期限までに予約すれば、通常の予約と同じ検査（時間帯の重なりを含む）をして予約できる。期限までに予約しなかった場合と、案内を断った場合は、次の人に案内する
6. 締め切りとの関係: 受付の締め切りを過ぎたら、新しく案内しない。確保していた席は、締め切りの時点で確保を解く（締め切りの後は誰も予約できない）
7. 取り消し: お客様は、案内される前ならいつでもキャンセル待ちを取り消せる。料金はかからない
8. 連続講座: 1 件のキャンセル待ちで全回の席を待つ（予約と同じく、途中の回だけは扱わない）
9. 表示: お客様は予約一覧の画面で、キャンセル待ちの順番と、案内されたときの確保の期限を見られる

実装の都合で決めてよいこと
- 案内の届け方。今回は、予約一覧の画面に表示するだけでよい（メールは送らない）
- データの持ち方（テーブルの構成など）

まだ決まっていないこと（今回は作らない）
- 店の担当者が定員を増やしたときに、キャンセル待ちの人をどう扱うか
- 締め切りの何時間前までなら案内するか。締め切りの直前に案内しても、お客様が気づかないまま期限が来るおそれがある

## Introduction
お客様が満席の講座のキャンセル待ちに登録し、予約がキャンセルされて席が空いたときに、登録した順に案内を受けて予約できるようにする。案内された人には席を一定の期限まで確保し、期限までに予約しなかった場合と案内を断った場合は、次の人に案内する。案内は予約一覧の画面に表示するだけで、メールなどでは届けない。

## Boundary Context
- **In scope**: キャンセル待ちの登録とその受付条件、登録人数の上限、順番、席が空いたときの案内、案内した人のための席の確保と期限、確保中の講座一覧の表示、案内された人の予約と案内の辞退、期限切れと締め切りでの確保の解除、キャンセル待ちの取り消し、連続講座の扱い、予約一覧でのキャンセル待ちの表示、エラーコード W01 から W08 の表示
- **Out of scope**: メールなど予約一覧の画面以外での案内、担当者が定員を増やしたときのキャンセル待ちの扱い、締め切りの何時間前まで案内するかの決め、キャンセル待ちの人数や状況を担当者が見る画面、キャンセル待ちの順番の入れ替え
- **Adjacent expectations**: 本機能は course-reservation の講座・残席・有効な予約・受付の締め切り・予約の受付条件（R01 から R04）・予約のキャンセルに依存する。予約の受付条件とキャンセルの規定は course-reservation のとおりとし、本機能では変えない。ログインと顧客の識別は既存の EC サイトのものを使い、キャンセル待ちはログイン中のお客様本人の操作として扱う

## 用語
- **キャンセル待ち**: お客様が、満席の講座に空きが出たときの案内を待つために行う登録。1 件で講座の全回を対象にする
- **待機中**: 登録後、まだ案内されておらず、取り消してもいない状態
- **案内済み**: 席が空いて案内を受けた状態。期限までは、その人のために席が確保されている
- **確保**: 空いた席を、案内した人以外の予約に使わせないこと
- **確保の期限**: 案内の日時から 24 時間後と、受付の締め切りの、早い方の時刻
- **受付の締め切り**: 第 1 回の開始日時の 1 時間前の時刻（course-reservation の定義と同じ）
- **残席**: course-reservation の定義のとおり（定員から、キャンセルしていない予約の件数を引いた数）
- **予約できる席**: 残席から、確保している席の数を引いた数。講座一覧の「満席」の表示と、予約・キャンセル待ちの受付条件は、この数で判定する
- **日時**: すべて日本時間（Asia/Tokyo）で扱う

## Requirements

### Requirement 1: キャンセル待ちの登録の受付条件
**Objective:** As a お客様, I want 満席の講座のキャンセル待ちに登録したい, so that 席が空いたときに案内を受けられる

#### Acceptance Criteria
1. When お客様が満席の講座でキャンセル待ちの登録ボタンを押下する, the Course Waitlist Service shall 次の検査を番号の順に行い、最初に満たさなかった検査のエラーを返して登録しない
   1. 予約できる席が 0 である
   2. 現在日時が、受付の締め切り以前である
   3. お客様が同じ講座の有効な予約を持っていない
   4. お客様が同じ講座の待機中または案内済みのキャンセル待ちを持っていない
   5. 同じ講座の待機中のキャンセル待ちが 10 人未満である
2. If 予約できる席が 1 以上である, then the Course Waitlist Service shall エラー W01「空席があるため、予約してください」を返す
3. If 現在日時が受付の締め切りより後である, then the Course Waitlist Service shall エラー W02「受付を締め切りました」を返す
4. When 現在日時が受付の締め切りとちょうど同じである, the Course Waitlist Service shall キャンセル待ちの登録を受け付ける
5. If お客様が同じ講座の有効な予約をすでに持っている, then the Course Waitlist Service shall エラー W03「すでに予約しています」を返す
6. If お客様が同じ講座の待機中または案内済みのキャンセル待ちをすでに持っている, then the Course Waitlist Service shall エラー W04「すでにキャンセル待ちに登録しています」を返す
7. If 同じ講座の待機中のキャンセル待ちがすでに 10 人である, then the Course Waitlist Service shall エラー W05「キャンセル待ちが上限に達しています」を返す
8. When お客様が以前にキャンセルした講座のキャンセル待ちに登録する, the Course Waitlist Service shall キャンセル済みの予約を W03 の判定に含めない
9. When お客様が以前に取り消した、または辞退した、または期限が切れたキャンセル待ちと同じ講座に、あらためて登録する, the Course Waitlist Service shall 他の登録と同じ受付条件で受け付ける
10. The Course Waitlist Service shall キャンセル待ちの登録で、予約の時間帯の重なり（R04）を検査しない

### Requirement 2: キャンセル待ちの登録と順番
**Objective:** As a お客様, I want 登録した順に案内されたい, so that 先に待った人が先に席を得られる

#### Acceptance Criteria
1. When キャンセル待ちの受付条件をすべて満たした, the Course Waitlist Service shall お客様のキャンセル待ちを待機中として 1 件登録し、登録した日時を保持する
2. The Course Waitlist Service shall 同じ講座の待機中のキャンセル待ちの順番を、登録した日時の早い順とする
3. When 連続講座のキャンセル待ちを登録する, the Course Waitlist Service shall 1 件のキャンセル待ちで全回の席を待つものとして扱う
4. The Course Waitlist Service shall 連続講座の途中の回だけを対象にしたキャンセル待ちを受け付けない
5. When 複数のお客様が同時に同じ講座のキャンセル待ちに登録する, the Course Waitlist Service shall 待機中の人数が 10 人を超えないようにし、超える分の登録にはエラー W05 を返す
6. If キャンセル待ちの登録がエラーで終わった, then the Course Waitlist Service shall キャンセル待ちの件数と順番を変えない
7. The Course Waitlist Service shall キャンセル待ちの登録に料金を求めない

### Requirement 3: 席が空いたときの案内と席の確保
**Objective:** As a お客様, I want 席が空いたら順番どおりに案内され、その席が自分のために取っておかれたい, so that 他のお客様に先に取られずに予約できる

#### Acceptance Criteria
1. When 予約がキャンセルされて席が 1 つ空き、同じ講座に待機中のキャンセル待ちがあり、かつ現在日時が受付の締め切り以前である, the Course Waitlist Service shall 順番が最初の 1 人を案内済みにし、案内の日時と確保の期限を保持して、その席を確保する
2. When 席が 1 つ空くごとに, the Course Waitlist Service shall 案内する人を 1 人にし、空いた席の数より多い人を案内しない
3. While 席を確保している, the Course Waitlist Service shall 講座一覧の残席の表示を、確保した席の数を除いた予約できる席で決め、予約できる席が 0 の間は「満席」とする
4. While 席を確保している, the Course Waitlist Service shall 案内済みの本人以外のお客様の予約を、確保している席の分は受け付けない
5. If 予約できる席が 0 の間に、案内済みの本人以外のお客様が予約しようとする, then the Course Waitlist Service shall エラー R01「満席です」を返し、予約を登録しない
6. When 予約がキャンセルされて席が 1 つ空いたが、同じ講座に待機中のキャンセル待ちがない, the Course Waitlist Service shall その席を通常の残席として扱う
7. The Course Waitlist Service shall 確保の期限を、案内の日時から 24 時間後と、受付の締め切りの、早い方の時刻とする
8. The Course Waitlist Service shall 案内を、予約一覧の画面に表示することで届ける
9. The Course Waitlist Service shall 案内を、メールなど予約一覧の画面以外で届けない
10. When 連続講座の席を確保する, the Course Waitlist Service shall 全回の席を 1 件の確保として扱う
11. When 複数の予約が同時にキャンセルされる, the Course Waitlist Service shall 同じ人を重ねて案内せず、1 つの席を 2 人に確保しない

### Requirement 4: 案内された人の予約
**Objective:** As a 案内を受けたお客様, I want 期限までに予約したい, so that 空いた席で講座に参加できる

#### Acceptance Criteria
1. When 案内済みのお客様が、確保の期限までに同じ講座を予約する, the Course Waitlist Service shall 確保した席を使い、通常の予約と同じ検査（course-reservation の R02 から R04。時間帯の重なりを含む）を行って予約を受け付ける
2. When 案内済みのお客様が予約に成功した, the Course Waitlist Service shall 予約を 1 件登録し、そのキャンセル待ちを予約済みとして終わらせ、確保を解く
3. When 案内済みのお客様の予約に成功した, the Course Waitlist Service shall 残席を 1 減らす。確保を解く分と合わせて、予約できる席は変わらない
4. If 案内済みのお客様の予約が、通常の予約と同じ検査で拒否された, then the Course Waitlist Service shall 該当するエラー（R02 から R04）を返し、予約を登録せず、確保の期限が切れるまで確保を保つ
5. When 確保の期限とちょうど同じ日時に、案内済みのお客様が予約する, the Course Waitlist Service shall 予約を受け付ける
6. When 案内済みのお客様が連続講座を予約する, the Course Waitlist Service shall 1 件の予約で全回の席を確保する
7. If 案内済みのお客様の予約がエラーで終わった, then the Course Waitlist Service shall 予約の件数、残席、確保を変えない

### Requirement 5: 案内の辞退と期限切れ
**Objective:** As a お客様, I want 参加できないときに案内を断りたい, so that 次の人が早く席を得られる

#### Acceptance Criteria
1. When 案内済みのお客様が案内を断る, the Course Waitlist Service shall そのキャンセル待ちを辞退済みとして終わらせ、確保を解く
2. When 確保の期限を過ぎても案内済みのお客様が予約していない, the Course Waitlist Service shall そのキャンセル待ちを期限切れとして終わらせ、確保を解く
3. When 確保を解いた, 現在日時が受付の締め切り以前である, かつ同じ講座に待機中のキャンセル待ちがある, the Course Waitlist Service shall 順番が最初の 1 人を案内済みにし、その席を確保する
4. When 確保を解いた, かつ同じ講座に待機中のキャンセル待ちがない, the Course Waitlist Service shall その席を通常の残席として扱う
5. If 案内済みでないキャンセル待ちについて、お客様が案内を断ろうとする, then the Course Waitlist Service shall エラー W07「案内されていません」を返し、キャンセル待ちを変えない
6. If 確保の期限を過ぎたキャンセル待ちについて、お客様が予約しようとする, then the Course Waitlist Service shall 通常の予約と同じ受付条件で判定する
7. The Course Waitlist Service shall 辞退した人と期限切れの人に、料金を求めない

### Requirement 6: 受付の締め切りとの関係
**Objective:** As a 店の担当者, I want 締め切りの後は席の扱いを通常の予約と同じに戻したい, so that 締め切り後に誰も予約できない状態を保てる

#### Acceptance Criteria
1. If 現在日時が受付の締め切りより後である, then the Course Waitlist Service shall 新しい案内をしない
2. When 受付の締め切りの時刻を過ぎた, the Course Waitlist Service shall 確保している席の確保を解く
3. When 受付の締め切りの時刻を過ぎて確保を解いた, the Course Waitlist Service shall 案内済みだったキャンセル待ちを期限切れとして終わらせる
4. When 受付の締め切りの時刻を過ぎて確保を解いた, the Course Waitlist Service shall 解いた席を次の人に案内しない
5. While 現在日時が受付の締め切りより後である, the Course Waitlist Service shall 講座の予約を course-reservation の R02 のとおり受け付けない（確保を解いた席も予約できない）
6. When 受付の締め切りの時刻を過ぎた, the Course Waitlist Service shall 待機中だったキャンセル待ちに新しい案内をしない
7. The Course Waitlist Service shall 受付の締め切りの判定を、course-reservation と同じ時刻の基準で行う（締め切りとちょうど同じ日時は締め切り前として扱う）

### Requirement 7: キャンセル待ちの取り消し
**Objective:** As a お客様, I want 不要になったキャンセル待ちをいつでも取り消したい, so that 他の人の順番を早められる

#### Acceptance Criteria
1. When お客様が待機中のキャンセル待ちを取り消す, the Course Waitlist Service shall そのキャンセル待ちを取り消し済みとして終わらせる
2. The Course Waitlist Service shall キャンセル待ちの取り消しに料金を求めない
3. When 待機中のキャンセル待ちが取り消された, the Course Waitlist Service shall 後ろの人の順番を 1 つずつ繰り上げて表示する
4. If お客様が案内済みのキャンセル待ちを取り消そうとする, then the Course Waitlist Service shall エラー W06「案内後は取り消せません。案内を断ってください」を返し、キャンセル待ちを変えない
5. If 取り消そうとするキャンセル待ちがお客様本人のものではない, または存在しない, then the Course Waitlist Service shall エラー W08「キャンセル待ちが見つかりません」を返し、キャンセル待ちを変えない
6. When お客様が取り消したキャンセル待ちと同じ講座に、あらためて登録する, the Course Waitlist Service shall 新しい登録として扱い、登録した日時は新しい日時とする

### Requirement 8: 予約一覧と講座一覧の表示
**Objective:** As a お客様, I want 自分のキャンセル待ちの順番と案内の期限を見たい, so that 待つか、予約するか、断るかを判断できる

#### Acceptance Criteria
1. When お客様が予約一覧を開く, the Course Waitlist Service shall お客様本人の待機中と案内済みのキャンセル待ちだけを、講座名、日程とともに、予約とは区別して表示する
2. While キャンセル待ちが待機中である, the Course Waitlist Service shall 予約一覧に、同じ講座の待機中の中での順番（先頭を 1 番とする）を表示する
3. While キャンセル待ちが案内済みである, the Course Waitlist Service shall 予約一覧に、案内されたことと確保の期限を表示する
4. While キャンセル待ちが案内済みである, the Course Waitlist Service shall 予約一覧から、予約すること、案内を断ることができるようにする
5. While キャンセル待ちが待機中である, the Course Waitlist Service shall 予約一覧から、取り消すことができるようにする
6. When 席が空いて案内されたか、確保の期限が切れたか、締め切りで確保を解いた, the Course Waitlist Service shall 次に予約一覧を開いたときの表示に、その状態を反映する
7. While 講座が満席（予約できる席が 0）で、現在日時が受付の締め切り以前である, the Course Waitlist Service shall 講座一覧に、その講座のキャンセル待ちの登録ボタンを押下できる状態で表示する
8. While 講座の予約できる席が 1 以上である, または現在日時が受付の締め切りより後である, the Course Waitlist Service shall 講座一覧のキャンセル待ちの登録ボタンを押下できない状態にする
9. The Course Waitlist Service shall 予約一覧に、他のお客様のキャンセル待ちの情報を表示しない

### Requirement 9: エラーの表示
**Objective:** As a お客様, I want エラーの理由を分かる文言で知りたい, so that 操作を直せる

#### Acceptance Criteria
1. When キャンセル待ちの登録、取り消し、案内の辞退がエラーになる, the Course Waitlist Service shall 次のエラーコードとメッセージの組を返す
   - W01 空席があるため、予約してください
   - W02 受付を締め切りました
   - W03 すでに予約しています
   - W04 すでにキャンセル待ちに登録しています
   - W05 キャンセル待ちが上限に達しています
   - W06 案内後は取り消せません。案内を断ってください
   - W07 案内されていません
   - W08 キャンセル待ちが見つかりません
2. The Course Waitlist Service shall キャンセル待ちの日時の判定をすべて日本時間で行う

## 未決定の事項
決まっていないため、本 spec では作らず、実装で決めない。決まってから requirements.md に追記する。

- **担当者が定員を増やしたとき**: キャンセル待ちの人を案内するか、どの順で案内するかが決まっていない
- **締め切りの何時間前まで案内するか**: 締め切りの直前に案内すると、お客様が気づかないまま期限が来るおそれがある。決まるまでは、締め切り以前であれば案内する（Requirement 3 の 1）
- **案内済みの人の数え方**: キャンセル待ちの上限 10 人に、案内済みの人を含めるかが決まっていない。本 spec は待機中の人だけを数える（Requirement 1 の 1 の 5）
- **案内する時点での検査**: 順番が最初の人が、すでに時間帯の重なる予約を持っている場合に、飛ばして次の人に案内するかが決まっていない。本 spec は検査せず、順番どおりに案内する（予約の時点で R04 になる）
- **登録の日時が同じ場合の順番**: 同時刻に登録された人の順番が決まっていない
- **締め切りを過ぎた後の待機中の表示**: 案内されないまま締め切りを過ぎたキャンセル待ちを、予約一覧にいつまで表示するかが決まっていない
