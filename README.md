# AI 駆動開発研修 演習用リポジトリ

文房具の EC サイトに、講座の予約・購買申請の承認・定期便の課金を足したサンプルアプリです。バックエンドは Java（Spring Boot）、画面は React、データベースは PostgreSQL です。仕様は cc-sdd の形で `.kiro/` に置いています。

## 開き方

1. このリポジトリの **Code** ボタンから **Codespaces** を選び、**Create codespace on main** を押します
2. 開いたら、左の GitHub Copilot のチャットを開きます

演習で受講者がコマンドを打つことはありません。アプリの起動、テストの実行、差分の表示は、Copilot のチャットに普通の文で頼みます。

## ログイン

パスワードはどのアカウントも `123` です。

| 番号 | アカウント | 使う画面 |
|---|---|---|
| 1 | tanaka@example.com | EC・講座の予約・定期便、購買申請の営業部の担当者 |
| 2 | sato@example.com | 購買申請の営業部の課長 |
| 3 | suzuki@example.com | 購買申請の営業部の部長 |
| 4 | takahashi@example.com | 購買申請の総務部の担当者 |
| 5 | yamada@example.com | 購買申請の総務部の課長 |
| 6 | ito@example.com | 購買申請の総務部の部長 |
| 7 | watanabe@example.com | 購買申請の役員 |
| 8 | nakajima@example.com | 購買申請の購買部 |

番号は社員の番号で、購買申請の承認の画面で代理人を設定するときに使います。

## フォルダ

- `.kiro/steering/` プロジェクト全体の決まり
- `.kiro/specs/` 業務ごとの spec（requirements.md・design.md・tasks.md など）
- `backend/` バックエンドとテスト
- `frontend/` 画面
- `演習/` 演習で使う資料
