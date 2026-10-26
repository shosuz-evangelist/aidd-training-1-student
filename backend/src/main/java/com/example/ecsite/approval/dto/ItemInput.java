package com.example.ecsite.approval.dto;

/** 申請の明細 1 件の入力。単価は円、数量は個数。 */
public record ItemInput(long unitPrice, int quantity) {
}
