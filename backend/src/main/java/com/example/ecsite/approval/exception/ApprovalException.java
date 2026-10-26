package com.example.ecsite.approval.exception;

public class ApprovalException extends RuntimeException {
    private final ApprovalError error;

    public ApprovalException(ApprovalError error) {
        super(error.getMessage());
        this.error = error;
    }

    public ApprovalError getError() { return error; }
}
