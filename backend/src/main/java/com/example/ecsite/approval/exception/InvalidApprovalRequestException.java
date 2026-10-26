package com.example.ecsite.approval.exception;

/** 申請の入力が正しくないことを表す。エラーコードは未決定のため、業務のコードを持たない HTTP 400 で返す。 */
public class InvalidApprovalRequestException extends RuntimeException {
    public InvalidApprovalRequestException(String message) {
        super(message);
    }
}
