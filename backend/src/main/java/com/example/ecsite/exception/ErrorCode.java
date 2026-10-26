package com.example.ecsite.exception;

import org.springframework.http.HttpStatus;

/** 拒否した理由。画面は code を見て表示を変える。 */
public enum ErrorCode {
    INVALID_QUANTITY(HttpStatus.BAD_REQUEST, "数量が正しくありません"),
    INSUFFICIENT_STOCK(HttpStatus.CONFLICT, "在庫が足りません"),
    PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, "商品がありません"),
    NOT_IN_CART(HttpStatus.NOT_FOUND, "カートに入っていません"),
    CART_EMPTY(HttpStatus.CONFLICT, "カートが空です");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public HttpStatus getStatus() { return status; }
    public String getMessage() { return message; }
}
