# Brief: subscription-billing

## Problem
法人のお客様向けに、文房具を毎月届ける定期便と、その請求の仕組みがない。

## Current State
文房具サイトには、月額のプランと請求書の機能がない。

## Desired Outcome
お客様が月額のプラン（ライト・スタンダード）を申し込み、毎月の請求書を受け取れる。

## Approach
プランと契約を管理し、決まった順序（日割り・割引・消費税）で月ごとの請求書を作る。

## Scope
- **In**: プランと契約の管理、プランの変更、解約、月ごとの請求書の作成（日割り・割引・消費税）、同じ月の請求書を 1 通に限ること
- **Out**: 支払い（口座振替など）、届ける文房具の中身と配送、請求書を作る担当者の権限

## Boundary Candidates
- 契約とプランの変更
- 請求書の計算と作成

## Out of Boundary
- お客様の管理（EC の顧客をそのまま使う）

## Upstream / Downstream
- **Upstream**: EC の顧客
- **Downstream**: なし
