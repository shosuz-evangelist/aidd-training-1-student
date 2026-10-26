# Requirements Document

## Project Description (Input)
EC サイトに在庫の管理と、購入確定時の在庫の引当を足す。
入力は変更仕様書「EC 在庫引当対応」v1.3（`inputs/EC_在庫引当_変更仕様書_v1.3.md`）。

## Introduction

文房具サイトの EC（商品一覧・商品詳細・カート・購入確定）に、商品ごとの在庫の管理を足す。お客様には、商品ごとの購入可能数を一覧と詳細に見せ、購入可能数を超える数量はカートに入れられないようにする。在庫の引当は、カートに入れたときではなく、購入確定のときに行う。
購入確定では、カート画面を表示した時点の購入可能数で在庫を確かめ、足りれば明細ごとに引当済数を増やして売上を登録し、カートを空にする。1 件でも足りなければ、全明細を確定前の状態に戻す。

## Boundary Context

- **In scope**: 商品ごとの在庫数と引当済数の保持、購入可能数の算出、商品一覧と商品詳細での購入可能数の表示、数量入力の上限、カート追加時の検査、カートからの削除、購入確定時の在庫の検査と引当、売上の登録、エラーの表示
- **Out of scope**: 決済、出荷（出荷時の在庫数と引当済数の減算）、入荷画面、税の計算（価格は税込み）
- **Adjacent expectations**: 商品の名前と価格は既存の商品マスタを使い、この機能では変更しない。売上の登録は既存の購入確定の流れを引き継ぎ、この機能は購入確定に在庫の検査と引当を足す。エラーは既存のエラー表示（画面下部に赤字）と同じ見せ方にする

## 業務の決まり（全要件に共通）

### 在庫と購入可能数

- 在庫は商品ごとに、在庫数と引当済数を持つ。在庫は商品の情報とは別に管理する
- 購入可能数は、在庫数から引当済数を引いた数とする
- 在庫が登録されていない商品、または在庫数から引当済数を引いた数が負になる商品の購入可能数は 0 とする
- 棚卸しの補正のため、在庫数が引当済数より小さい状態を許容する

### エラーコード

| コード | メッセージ |
|---|---|
| E01 | 数量が正しくありません |
| E02 | 在庫が足りません |
| E03 | 商品がありません |
| E04 | カートに入っていません |
| E05 | カートが空です |

### 確認用データ

| 商品 | 価格 | 在庫数 | 引当済数 | 購入可能数 |
|---|---|---|---|---|
| A | 120 | 10 | 0 | 10 |
| B | 300 | 5 | 0 | 5 |
| C | 200 | 10 | 8 | 2 |
| D | 150 | 3 | 3 | 0 |

## Requirements

### Requirement 1: 在庫と購入可能数の保持
**Objective:** As a 店の担当者, I want 商品ごとの在庫数と引当済数を持ち、購入可能数を一貫して求めたい, so that 画面と購入確定が同じ基準で在庫を扱える

#### Acceptance Criteria
1. The EC System shall 商品ごとに在庫数と引当済数を、商品の名前や価格とは別に保持する
2. The EC System shall 購入可能数を、在庫数から引当済数を引いた数として求める
3. If 商品の在庫が登録されていない, then the EC System shall その商品の購入可能数を 0 とする
4. If 在庫数から引当済数を引いた数が負になる, then the EC System shall その商品の購入可能数を 0 とする
5. The EC System shall 在庫数が引当済数より小さい在庫を、エラーにせず保持する

### Requirement 2: 商品一覧での購入可能数の表示
**Objective:** As a お客様, I want 商品一覧で商品ごとの購入可能数を見たい, so that 買える商品かどうかを一覧のまま判断できる

#### Acceptance Criteria
1. The EC System shall 商品一覧に、商品名と価格を既存のとおり表示する
2. While 商品の購入可能数が 0 より大きい, the EC System shall 商品一覧のその商品に「購入できる数: n」（n は購入可能数）を表示する
3. While 商品の購入可能数が 0 である, the EC System shall 商品一覧のその商品に「在庫切れ」を赤字で表示する

### Requirement 3: 商品詳細での購入可能数と数量入力
**Objective:** As a お客様, I want 商品詳細で購入可能数を見て、買える数だけを入力したい, so that 買えない数量を選ばずに済む

#### Acceptance Criteria
1. The EC System shall 商品詳細に、商品一覧と同じ表示で購入可能数を表示する
2. The EC System shall 商品詳細の数量入力を、1 から購入可能数までとする
3. While 商品の購入可能数が 0 である, the EC System shall 商品詳細の「カートに追加」を押せない状態（グレー表示）にする

### Requirement 4: カートへの追加
**Objective:** As a お客様, I want 購入可能数の範囲でカートに商品を追加したい, so that 買える数だけをカートに入れられる

#### Acceptance Criteria
1. When お客様が商品詳細で「カートに追加」を押した, the EC System shall 次の 1 から 4 を順に行う。1: 数量が 1 以上であることを検査する。2: 商品が存在することを検査する。3: カート内の同じ商品の数量に追加する数量を足した数が購入可能数以下であることを検査する。4: カートに追加する
2. If 追加する数量が 1 未満である, then the EC System shall E01 を表示し、カートを変更しない
3. If 追加する商品が存在しない, then the EC System shall E03 を表示し、カートを変更しない
4. If カート内の同じ商品の数量に追加する数量を足した数が購入可能数を超える, then the EC System shall E02 を表示し、カートを変更しない
5. When カート内に同じ商品が既にある状態で追加が検査を通った, the EC System shall その商品の数量に追加する数量を加算する
6. When カートへの追加が正常に終わった, the EC System shall カート画面へ遷移する
7. The EC System shall カートへの追加では、在庫数も引当済数も変更しない

### Requirement 5: カートからの削除
**Objective:** As a お客様, I want カートから商品を削除したい, so that 買わない商品を外せる

#### Acceptance Criteria
1. When お客様がカート画面で商品の「削除」を押した, the EC System shall カートからその商品を削除する
2. If 削除する商品がカートに入っていない, then the EC System shall E04 を表示する
3. The EC System shall カートからの削除では、在庫数も引当済数も変更しない

### Requirement 6: 購入確定時の在庫の検査と引当
**Objective:** As a お客様, I want 購入を確定したときに在庫が引当てられてほしい, so that 買った分が在庫に反映される

#### Acceptance Criteria
1. When お客様がカート画面で「購入確定」を押した, the EC System shall 次の 1 から 4 を順に行う。1: カートの明細を取得する。2: 在庫を検査し、明細ごとに引当済数を増やす。3: 明細ごとに売上を登録する。4: カートを空にする
2. If カートの明細が 0 件である, then the EC System shall E05 を表示し、在庫も売上も変更しない
3. The EC System shall 購入確定の在庫の検査を、カート画面を表示したときに取得した明細ごとの購入可能数（カート画面から送られる値）で行い、購入確定の時点の在庫を読み直して検査することはしない
4. If カート画面を表示したときの購入可能数が数量より小さい明細が 1 件でもある, then the EC System shall E02 を表示し、全明細について在庫・売上・カートを購入確定の前の状態に戻す
5. When 在庫の検査が通った, the EC System shall 明細ごとに、その時点の引当済数に数量を加算した値を引当済数として保存する
6. When 複数のお客様が同じ商品を同時に購入確定した, the EC System shall それぞれの数量の加算を、引当済数に失わずに反映する
7. While 購入確定を処理している, the EC System shall 「購入確定」を押せない状態にする
8. When 購入確定が正常に終わった, the EC System shall 売上画面へ遷移する

### Requirement 7: 購入確定時の売上の登録
**Objective:** As a 店の担当者, I want 購入確定のときの価格で売上を記録したい, so that 売上が確定した時点の金額で残る

#### Acceptance Criteria
1. When 購入確定の在庫の検査が通った, the EC System shall 明細ごとに売上を 1 件登録する
2. The EC System shall 売上の単価を、購入確定の時点の商品マスタの価格とする
3. The EC System shall 売上の金額を、単価に数量を掛けた額とする
4. The EC System shall 売上の日時を、購入確定の日時とする
5. If 売上の登録のあとで商品の価格が変わった, then the EC System shall 登録済みの売上を変更しない
6. If 購入確定の途中で失敗した, then the EC System shall 売上を 1 件も残さない

### Requirement 8: エラーの表示
**Objective:** As a お客様, I want 失敗した理由を既存と同じ見せ方で知りたい, so that 何を直せばよいか分かる

#### Acceptance Criteria
1. When 在庫に関する処理でエラーが起きた, the EC System shall エラーコード一覧のメッセージを、既存のエラー表示と同じく画面の下部に赤字で表示する
2. The EC System shall エラーコード E01 から E05 に、エラーコード一覧のメッセージを対応させる
