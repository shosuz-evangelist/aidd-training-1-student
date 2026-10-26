# Technology Stack

## Architecture

バックエンドは Spring Boot の Web API、画面は React の SPA。データベースは PostgreSQL。
GitHub Codespaces で開くと、devcontainer がアプリとデータベース（開発用 ecsite・テスト用 ecsite_test）を用意する。

## Core Technologies

- **Language**: Java 25（バックエンド）、TypeScript 5.9（画面）
- **Framework**: Spring Boot 4.1（Web MVC・Data JPA・Validation）、React 19・React Router 7
- **Build**: Maven（mvnw）、Vite 8
- **Database**: PostgreSQL 18、Flyway

## Key Libraries

- Spring Data JPA（エンティティとリポジトリ）
- Flyway と flyway-database-postgresql（テーブルの定義）
- JUnit 5・Spring Boot Test・MockMvc（テスト）

この一覧にないライブラリを足すときは、design.md に理由を書く。
受講者が環境の違いでつまずかないように、スタックは固定する。

## Development Standards

### 金額

- 金額は円の整数（long）で持つ。double と float は使わない
- 端数の処理（切り捨て・切り上げ・四捨五入）と、割引と税の適用順序は、requirements.md に書かれたとおりにする
- requirements.md に書かれていない場合は、実装で決めずに、requirements.md の未決定の事項として残す

金額の扱いが実装ごとに変わると、同じ spec から違う結果が出るため。

### 日時

- 日時は日本時間（Asia/Tokyo）で扱い、データベースには timestamp with time zone で保存する
- 現在の時刻は Clock の Bean から取る。テストでは時刻を固定した Clock に差し替える

### テスト

`testing.md` を見る。

## Development Environment

### Required Tools

- GitHub Codespaces（ブラウザーで開く）。追加の設定なしでアプリとテストが動く状態を保つ

### Common Commands

```bash
# テスト: cd backend && ./mvnw test
# バックエンドの起動: cd backend && ./mvnw spring-boot:run
# 画面の起動: cd frontend && npm run dev
```

---
_updated_at: 2026-10-23_
