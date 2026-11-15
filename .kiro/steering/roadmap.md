# Roadmap

## Overview
文房具を売る EC サイトに、4 つの業務を足す。4 つの業務は同じ土台（スタック・画面の枠・ログインと顧客）を使い、業務ごとに 1 つの spec に分ける。

## Approach Decision
- **Chosen**: 業務ごとに spec を分け、それぞれ requirements・design・tasks の順に作る
- **Why**: 業務ごとに決まりと画面が独立していて、ほかの業務の spec を変えずに作れるため
- **Rejected alternatives**: 4 つの業務を 1 つの spec にまとめる案は、1 つの spec が大きくなりすぎるため採らない

## Scope
- **In**: EC の在庫と購入確定、講座の予約とキャンセル、購買申請と承認、定期便の月額課金
- **Out**: 決済・支払い・配送、ログインと顧客の管理（既存の仕組みを使う）

## Constraints
- Java 25・Spring Boot 4（API）、React（画面）、PostgreSQL。GitHub Codespaces で開く
- 日付と日時は日本時間で扱う

## Boundary Strategy
- **Why this split**: 業務ごとにテーブルと画面が分かれていて、spec ごとに独立して検証できるため
- **Shared seams to watch**: ログインと顧客の識別、画面の枠（4 つの業務で共通）

## Specs (dependency order)
- [x] ec-inventory-checkout -- EC に在庫の管理と、購入確定時の在庫の引当を足す。Dependencies: none
- [x] course-reservation -- 講座（単発・連続）の予約とキャンセルを足す。Dependencies: none
- [x] purchase-approval -- 法人の社員の購買申請と承認を足す。Dependencies: none
- [x] subscription-billing -- 法人向けの文房具の定期便と月ごとの請求書を足す。Dependencies: none
