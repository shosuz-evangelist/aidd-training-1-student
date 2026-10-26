# Project Structure

## Organization Philosophy

業務（ドメイン）ごとにパッケージを分け、その中を controller・service・repository・entity の 4 つの層に分ける。
業務ルールの場所を 1 か所に決めておくと、spec と実装を突き合わせやすい。

## Directory Patterns

### 業務ごとのパッケージ
**Location**: `backend/src/main/java/com/example/ecsite/<業務>/`
**Purpose**: 1 つの業務の controller・service・repository・entity・dto・exception を置く
**Example**: `reservation/`（講座の予約）、`approval/`（購買申請と承認）、`billing/`（定期便の課金）。EC はパッケージの直下に置く

### マイグレーション
**Location**: `backend/src/main/resources/db/migration/`
**Purpose**: テーブルの定義（Flyway の V 番号つきの SQL）。サンプルデータは `db/sample/` の R__ のファイルに分ける

### 画面
**Location**: `frontend/src/pages/`
**Purpose**: 業務ごとの画面。API を呼んで表示する。業務ルールは持たない

## Layer Rules

- controller は HTTP の入力と出力だけを扱う。controller から repository を直接呼ばない
- 業務ルール（在庫の引き当て、金額の計算、状態の遷移、権限の判定など）は service に書く
- ある業務のクラスから、ほかの業務のクラスを直接参照しない

## Naming Conventions

- **クラス**: 役割を名前の後ろに付ける（`ReservationService`、`ReservationController`、`ReservationRepository`）
- **エラー**: 業務ごとに例外と理由コードの enum を持つ（`ReservationException`、`ReservationError`）
- **API**: `/api/<業務>/...` の形にする

---
_updated_at: 2026-10-23_
