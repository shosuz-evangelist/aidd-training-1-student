package com.example.ecsite.billing.exception;

public class BillingException extends RuntimeException {
    private final BillingError error;

    public BillingException(BillingError error) {
        super(error.getMessage());
        this.error = error;
    }

    public BillingError getError() { return error; }
}
