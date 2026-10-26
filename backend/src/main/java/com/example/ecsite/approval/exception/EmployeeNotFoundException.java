package com.example.ecsite.approval.exception;

/** 指定された社員がいないことを表す。エラーコードは決めていないため、業務のコードを持たない HTTP 404 で返す。 */
public class EmployeeNotFoundException extends RuntimeException {
    public EmployeeNotFoundException(String message) {
        super(message);
    }
}
