# Requirements Document

## Project Description (Input)
講座のキャンセル待ちを足す。

- 課題を持つ人: 満席の人気講座に参加したいお客様と、お問い合わせに電話で応じている店の担当者
- 現状: 満席の講座は予約できず、「空いたら教えてほしい」というお問い合わせを店で受ける。担当者が名前を控えておき、キャンセルが出たら電話をかけている
- 変えたいこと: お客様が満席の講座のキャンセル待ちに登録でき、席が空いたら登録順にメールで案内される。案内に返事がなければ次の人に案内する。キャンセル待ちの人数には上限を設ける。連続講座も単発講座と同じに扱う。締め切りの直前に出たキャンセルもなるべく案内する

元の要求の補足: 「キャンセル待ちのテーブルを作って登録順に並べる」は実現手段の指定なので design で扱い、本書には「登録順に案内する」という振る舞いだけを書く。

## Introduction
満席の講座に対して、お客様がキャンセル待ちに登録できるようにする。予約がキャンセルされて席が空いたら、キャンセル待ちの先頭のお客様にメールで案内し、一定の時間その席を取り置く。案内された人が期限までに予約しなければ、席を次の人に案内する。これにより、担当者が名前を控えて電話をかける作業をなくす。

## Boundary Context
- **In scope**: キャンセル待ちの登録と取り消し、キャンセル待ちの人数の上限、席が空いたときの登録順の案内（メール）、案内中の席の取り置き、案内への返事（予約の確定と辞退）、返事がないときの次の人への案内、締め切り前後の扱い、連続講座の扱い、キャンセル待ちの状況の表示、エラーコード W01 から W05
- **Out of scope**: 担当者がキャンセル待ちを管理する画面、メールの文面の装飾と再送、メールの送信失敗時の再試行、キャンセル待ちの有料化、複数の席をまとめて待つ登録、順番の入れ替え、電話による案内
- **Adjacent expectations**: 講座の予約とキャンセル、残席の数え方、締め切り（第 1 回の開始の 1 時間前）、キャンセル料、エラー R01 から R09 は `course-reservation` の決めに従い、本機能はそれを変えない。ログイン中のお客様の識別と、お客様のメールアドレスの保持は既存の EC サイトに依存する。ログインと顧客の管理は本機能の対象外とする

## 用語
- **キャンセル待ち**: 満席の講座に、席が空いたら案内してほしい旨を登録すること。登録した 1 件を「キャンセル待ちの登録」と呼ぶ
- **待ち中**: 登録後、まだ案内されていないキャンセル待ちの登録の状態
- **案内中**: 席が空いて案内メールを送り、返事の期限が過ぎていない状態。案内中の席は、その人のために取り置く
- **返事の期限**: 案内した時刻から 24 時間後。ただし、予約の締め切り（第 1 回の開始の 1 時間前）がそれより早い場合は、締め切りの時刻とする
- **順番**: 登録した日時の早い順。登録日時が同じ場合は、先に処理を受け付けたほうを先とする
- **残席**: `course-reservation` の定義（定員から有効な予約の件数を引いた数）から、案内中の取り置きの数をさらに引いた数。本 spec では、取り置きを含めた残席の表示を扱う
- **日時**: すべて日本時間（Asia/Tokyo）で扱う

## 決定事項
元の要求に具体的な値や規則がなく、お問い合わせ先の担当者にも確認できなかったため、本 spec で次のとおり決めた。変えたい場合は、requirements.md を直してから design に進む。

- キャンセル待ちの上限は、1 講座あたり 10 人とする（「ほどほど」の具体化）
- 返事の期限は、案内から 24 時間（「しばらく」の具体化）。締め切りが先に来る場合は締め切りまで
- 案内は席の取り置きを伴う。案内中の席は、ほかのお客様が予約できない
- 案内に応じる操作は、案内メールのリンクから開く画面での予約の確定とする
- 返事の期限までに予約しなかった人は、キャンセル待ちから外す。再び待つ場合は、新しく登録し直して末尾に並ぶ
- 締め切りの後に出たキャンセルは案内しない。締め切りの前に出たキャンセルは、締め切りまでの時間が短くても案内する
- キャンセル待ちの登録は、受付を締め切る前の満席の講座だけにできる

## Requirements

### Requirement 1: キャンセル待ちの登録
**Objective:** As a お客様, I want 満席の講座のキャンセル待ちに登録したい, so that 席が空いたときに知らせてもらえる

#### Acceptance Criteria
1. While 講座の残席が 0 で、かつ受付の締め切り前である, the Course Waitlist Service shall その講座の予約ボタンに代えて、キャンセル待ちの登録ボタンを押下できる状態で表示する
2. When お客様がキャンセル待ちの登録ボタンを押下する, the Course Waitlist Service shall 次の検査を番号の順に行い、最初に満たさなかった検査のエラーを返して登録しない
   1. 残席が 0 である
   2. 現在日時が、第 1 回の開始日時の 1 時間前の時刻以前である
   3. お客様が同じ講座の有効な予約を持っていない
   4. お客様が同じ講座の待ち中または案内中のキャンセル待ちの登録を持っていない
   5. お客様の有効な予約の講座のいずれとも、時間帯が重ならない
   6. その講座の待ち中と案内中のキャンセル待ちの登録の合計が 10 件未満である
3. If 残席が 1 以上である, then the Course Waitlist Service shall エラー W01「空席があるため、予約してください」を返す
4. If 現在日時が第 1 回の開始日時の 1 時間前の時刻より後である, then the Course Waitlist Service shall エラー R02「受付を締め切りました」を返す
5. If お客様が同じ講座の有効な予約をすでに持っている, then the Course Waitlist Service shall エラー R03「すでに予約しています」を返す
6. If お客様が同じ講座のキャンセル待ちにすでに登録している, then the Course Waitlist Service shall エラー W02「すでにキャンセル待ちに登録しています」を返す
7. If キャンセル待ちに登録しようとする講座の時間帯が、お客様の有効な予約の講座の時間帯と重なる, then the Course Waitlist Service shall エラー R04「時間帯が重なります」を返す
8. If その講座の待ち中と案内中のキャンセル待ちの登録の合計がすでに 10 件である, then the Course Waitlist Service shall エラー W03「キャンセル待ちが上限に達しています」を返す
9. When 現在日時が第 1 回の開始日時の 1 時間前の時刻とちょうど同じである, the Course Waitlist Service shall キャンセル待ちの登録を受け付ける
10. When キャンセル待ちの登録を受け付ける, the Course Waitlist Service shall 登録日時を記録し、順番をその登録日時の早い順とする
11. If キャンセル待ちの登録がエラーで終わった, then the Course Waitlist Service shall キャンセル待ちの登録の件数を変えない
12. When 複数のお客様が同時に同じ講座のキャンセル待ちに登録する, the Course Waitlist Service shall 待ち中と案内中の登録の合計が 10 件を超えないようにし、超える分にはエラー W03 を返す
13. The Course Waitlist Service shall 時間帯の重なりの判定を、`course-reservation` の予約と同じ規則（連続講座は第 1 回の時間帯だけ、終了時刻と開始時刻が同じ場合は重ならない）で行う

### Requirement 2: 席が空いたときの案内
**Objective:** As a キャンセル待ちのお客様, I want 席が空いたら登録順にメールで案内されたい, so that 担当者の電話を待たずに予約できる

#### Acceptance Criteria
1. When 講座の有効な予約がキャンセルされて席が 1 つ空き、かつ現在日時が受付の締め切り前である, the Course Waitlist Service shall その講座の待ち中のキャンセル待ちの登録のうち順番が最も早いものを案内中にし、その席を取り置く
2. When キャンセル待ちの登録を案内中にする, the Course Waitlist Service shall 登録したお客様のメールアドレスに、講座名、日程、返事の期限、予約するための画面へのリンクを含む案内メールを送る
3. When 案内中にする, the Course Waitlist Service shall 案内した日時と返事の期限を記録する
4. If 席が空いたときに待ち中のキャンセル待ちの登録がない, then the Course Waitlist Service shall 通常の残席として扱い、案内を送らない
5. When 同じ講座で複数の席が空く, the Course Waitlist Service shall 空いた席の数だけ、順番の早い待ち中の登録から 1 人に 1 席ずつ案内する
6. If 席が空いたとき、順番が最も早い待ち中の登録のお客様が、その時点で同じ講座の有効な予約を持っている, または時間帯が重なる有効な予約を持っている, then the Course Waitlist Service shall そのキャンセル待ちの登録を外し、順番が次の待ち中の登録を案内する
7. The Course Waitlist Service shall 1 つの空いた席を、同時に 2 人以上に案内しない
8. The Course Waitlist Service shall 案内中の人数と有効な予約の件数の合計が、定員を超えないようにする
9. While 講座に案内中の取り置きがある, the Course Waitlist Service shall その数を残席から引いて表示し、案内を受けていないお客様がその席を予約できないようにする

### Requirement 3: 案内を受けた人の返事
**Objective:** As a 案内を受けたお客様, I want 期限までに予約するか辞退するかを選びたい, so that 席を確保できる、または席を譲れる

#### Acceptance Criteria
1. When 案内中のお客様が、返事の期限までに案内のリンクから予約を確定する, the Course Waitlist Service shall 取り置いた席でお客様の予約を 1 件登録し、そのキャンセル待ちの登録を完了にする
2. When 案内中のお客様の予約を登録する, the Course Waitlist Service shall 満席の検査（R01）と受付の締め切りの検査（R02）を、取り置いた席に限って行わない
3. When 案内中のお客様が予約を確定する, the Course Waitlist Service shall 取り置きを解消して、有効な予約の件数に数え直す。残席の合計は変えない
4. When 案内中のお客様が案内のリンクから辞退する, the Course Waitlist Service shall そのキャンセル待ちの登録を外し、取り置いた席を、Requirement 4 に従って次の人に案内する
5. If 案内中のお客様が予約を確定するときに、同じ講座の有効な予約を持っている, または時間帯が重なる有効な予約を持っている, then the Course Waitlist Service shall エラー R03 または R04 を返し、そのキャンセル待ちの登録を外して取り置きを次の人に案内する
6. If 案内の返事の期限が過ぎた後に、お客様が案内のリンクから予約を確定しようとする, then the Course Waitlist Service shall エラー W04「案内の期限が過ぎました」を返し、予約を登録しない
7. If 案内を受けていないお客様、または他のお客様宛ての案内のリンクで予約を確定しようとする, then the Course Waitlist Service shall エラー W05「案内が見つかりません」を返し、予約を変えない
8. The Course Waitlist Service shall 案内からできた予約を、キャンセル料を含め、通常の予約と同じ規則で扱う
9. If 予約の確定がエラーで終わった, then the Course Waitlist Service shall 予約の件数と残席を変えない

### Requirement 4: 返事がないときの次の人への案内
**Objective:** As a キャンセル待ちのお客様, I want 先に案内された人が返事をしなかったとき次の人に案内してほしい, so that 空いた席が使われないまま残らない

#### Acceptance Criteria
1. When 案内中のキャンセル待ちの登録の返事の期限が過ぎ、お客様が予約も辞退もしていない, the Course Waitlist Service shall そのキャンセル待ちの登録を外し、取り置きを解消する
2. When 取り置きを解消して席が空く, the Course Waitlist Service shall 現在日時が受付の締め切り前であれば、Requirement 2 に従って、待ち中の登録のうち順番が最も早いものを案内する
3. While 返事の期限が締め切りの時刻である, the Course Waitlist Service shall 返事の期限の時刻ちょうどまで、予約の確定を受け付ける
4. If 現在日時が受付の締め切りより後で、待ち中の登録が残っている, then the Course Waitlist Service shall 新しい案内を送らず、残りの待ち中の登録を外す
5. When 返事の期限が過ぎて外されたお客様が、同じ講座のキャンセル待ちに再び登録する, the Course Waitlist Service shall 他のお客様と同じ登録の検査を行い、登録できた場合は末尾の順番とする
6. When 返事の期限が過ぎて外されたキャンセル待ちの登録が複数ある, the Course Waitlist Service shall それぞれの取り置きを別々に解消し、重複して案内しない
7. The Course Waitlist Service shall 返事の期限が過ぎてから 5 分以内に、次の人への案内または待ち中の登録の整理を行う

### Requirement 5: 締め切りの直前に出たキャンセル
**Objective:** As a キャンセル待ちのお客様, I want 締め切りの直前に席が空いても案内されたい, so that 直前でも席を取れる機会がある

#### Acceptance Criteria
1. When 現在日時が第 1 回の開始日時の 1 時間前の時刻以前で、予約のキャンセルによって席が空く, the Course Waitlist Service shall 締め切りまでの時間の長さにかかわらず、待ち中の登録を案内する
2. When 案内の時刻から締め切りまでが 24 時間に満たない, the Course Waitlist Service shall 返事の期限を締め切りの時刻とする
3. When 返事の期限が締め切りの時刻の案内を送る, the Course Waitlist Service shall 案内メールに、返事の期限が締め切りの時刻であることを示す
4. When 現在日時が第 1 回の開始日時の 1 時間前の時刻より後で、予約のキャンセルによって席が空く, the Course Waitlist Service shall 案内を送らず、待ち中の登録を外す
5. When 受付の締め切りの時刻が過ぎる, the Course Waitlist Service shall その講座の待ち中のキャンセル待ちの登録を外し、案内中の登録はその返事の期限（締め切りの時刻）まで有効とする
6. The Course Waitlist Service shall 受付の締め切りの時刻を、`course-reservation` と同じく第 1 回の開始日時の 1 時間前とする

### Requirement 6: キャンセル待ちの取り消しと状況の表示
**Objective:** As a キャンセル待ちのお客様, I want 自分の状況を見て、必要なら取り消したい, so that 参加できなくなったときに順番を譲れる

#### Acceptance Criteria
1. When お客様がキャンセル待ちの一覧を開く, the Course Waitlist Service shall お客様本人の待ち中と案内中のキャンセル待ちの登録だけを、講座名、日程、状態（待ち中 / 案内中）、待ち中の場合は前に並んでいる人数、案内中の場合は返事の期限とともに表示する
2. When お客様が待ち中のキャンセル待ちの登録を取り消す, the Course Waitlist Service shall その登録を外し、後ろの登録の順番を詰める
3. When お客様が案内中のキャンセル待ちの登録を取り消す, the Course Waitlist Service shall 辞退として扱い、Requirement 3 の 4 に従う
4. If 取り消そうとする登録がお客様本人のものではない, または存在しない, then the Course Waitlist Service shall エラー W06「キャンセル待ちが見つかりません」を返し、登録を変えない
5. The Course Waitlist Service shall 取り消しに料金を求めない
6. While 講座が満席で受付の締め切り前であり、お客様がその講座のキャンセル待ちに登録している, the Course Waitlist Service shall 講座一覧に、その講座にキャンセル待ちに登録していることを示す

### Requirement 7: 連続講座の扱い
**Objective:** As a お客様, I want 連続講座も単発講座と同じ方法でキャンセル待ちしたい, so that 講座の種類を意識せずに使える

#### Acceptance Criteria
1. The Course Waitlist Service shall 連続講座のキャンセル待ちを、単発講座と同じ規則で扱う
2. The Course Waitlist Service shall 連続講座のキャンセル待ちの登録と案内を、1 件で全回の席を対象とするものとして扱う
3. The Course Waitlist Service shall 連続講座の受付の締め切りと返事の期限の判定を、第 1 回の開始日時を基準に行う
4. When 連続講座の案内中のお客様が予約を確定する, the Course Waitlist Service shall 全回の席を 1 件の予約で確保する
5. The Course Waitlist Service shall 連続講座の途中の回だけを対象にしたキャンセル待ちを受け付けない

### Requirement 8: エラーの表示
**Objective:** As a お客様, I want エラーの理由を分かる文言で知りたい, so that 操作を直せる

#### Acceptance Criteria
1. When キャンセル待ちの登録、取り消し、案内への返事がエラーになる, the Course Waitlist Service shall 次のエラーコードとメッセージの組を返す
   - W01 空席があるため、予約してください
   - W02 すでにキャンセル待ちに登録しています
   - W03 キャンセル待ちが上限に達しています
   - W04 案内の期限が過ぎました
   - W05 案内が見つかりません
   - W06 キャンセル待ちが見つかりません
2. The Course Waitlist Service shall 既存の予約に関するエラー R01 から R09 を、`course-reservation` の文言のまま返す
3. The Course Waitlist Service shall 日時の判定をすべて日本時間で行う

## 未決定の事項
本 spec でも決めず、決まってから requirements.md に追記するもの。

- **メールの送信失敗**: 案内メールが届かなかった場合の再送と、次の人への繰り上げの扱いは決めていない。本 spec では、送信を試みた時点で案内中とし、返事の期限で外す
- **お客様のメールアドレス**: 既存のお客様情報に、メールアドレスを必ず持たせているかは、この資料からは分からない。持たないお客様の登録の扱いは決めていない
- **担当者の画面**: 担当者がキャンセル待ちの人数と案内の状況を見る画面は、本 spec の対象外とする。必要かどうかは別途決める
