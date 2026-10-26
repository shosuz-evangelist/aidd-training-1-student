package com.example.ecsite.approval.exception;

/** 指定された購買申請がないことを表す。業務のコードは持たず、HTTP 404 で返す。 */
public class RequestNotFoundException extends RuntimeException {
    public RequestNotFoundException(String message) {
        super(message);
    }
}
