package com.example.ecsite.approval.dto;

import com.example.ecsite.approval.exception.InvalidApprovalRequestException;

import java.util.List;

/** 申請の入力。明細の単価と数量が抜けた入力を 0 として受け付けないよう、受け取りはラッパー型で行う。 */
public record SubmitRequest(Long applicantId, List<ItemRequest> items) {

    /** 明細 1 行の入力。 */
    public record ItemRequest(Long unitPrice, Integer quantity) {
    }

    /** 明細を業務側の入力へ変換する。欠けた項目は 400(コードなし)にする。件数や値の範囲の確認はサービスに任せる。 */
    public List<ItemInput> toItems() {
        if (items == null) {
            throw new InvalidApprovalRequestException("明細が 1 件もありません");
        }
        return items.stream().map(i -> {
            if (i == null || i.unitPrice() == null || i.quantity() == null) {
                throw new InvalidApprovalRequestException("明細の単価と数量を指定してください");
            }
            return new ItemInput(i.unitPrice(), i.quantity());
        }).toList();
    }
}
