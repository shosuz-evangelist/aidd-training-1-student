package com.example.ecsite.approval.exception;

import org.springframework.http.HttpStatus;

public enum ApprovalError {
    A01(HttpStatus.FORBIDDEN, "操作する権限がありません"),
    A02(HttpStatus.BAD_REQUEST, "差し戻しの理由を入力してください"),
    A03(HttpStatus.CONFLICT, "今の状態ではこの操作はできません");

    private final HttpStatus status;
    private final String message;

    ApprovalError(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public HttpStatus getStatus() { return status; }
    public String getMessage() { return message; }
}
