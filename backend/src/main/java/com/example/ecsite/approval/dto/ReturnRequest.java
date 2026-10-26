package com.example.ecsite.approval.dto;

/** 差し戻しの入力。理由の検証(A02)は業務のサービスが行う。 */
public record ReturnRequest(Long actorId, String reason) {
}
