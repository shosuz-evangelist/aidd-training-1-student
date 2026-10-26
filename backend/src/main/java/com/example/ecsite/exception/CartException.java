package com.example.ecsite.exception;

/** カートと購入の確定で、操作を受け付けなかったことを表す。 */
public class CartException extends RuntimeException {
    private final ErrorCode code;

    public CartException(ErrorCode code) {
        super(code.getMessage());
        this.code = code;
    }

    public ErrorCode getCode() { return code; }
}
