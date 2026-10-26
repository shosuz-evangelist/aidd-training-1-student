package com.example.ecsite.billing.exception;

import org.springframework.http.HttpStatus;

public enum BillingError {
    SUBSCRIPTION_NOT_FOUND(HttpStatus.NOT_FOUND, "契約がありません"),
    PLAN_NOT_FOUND(HttpStatus.NOT_FOUND, "プランがありません"),
    INVALID_DATE(HttpStatus.BAD_REQUEST, "日付が正しくありません"),
    SAME_PLAN(HttpStatus.CONFLICT, "同じプランには変えられません"),
    NOT_SUBSCRIBED_IN_MONTH(HttpStatus.CONFLICT, "その月は契約していません"),
    ALREADY_ISSUED(HttpStatus.CONFLICT, "その月の請求書はすでに作りました");

    private final HttpStatus status;
    private final String message;

    BillingError(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public HttpStatus getStatus() { return status; }
    public String getMessage() { return message; }
}
