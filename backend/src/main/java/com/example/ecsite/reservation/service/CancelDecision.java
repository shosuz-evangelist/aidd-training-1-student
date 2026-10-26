package com.example.ecsite.reservation.service;

/** キャンセルの可否と、キャンセルできるときの料金（円）。cancellable が false のとき fee は 0。 */
public record CancelDecision(boolean cancellable, long fee) {}
