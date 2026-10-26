package com.example.ecsite.exception;

import com.example.ecsite.approval.exception.ApprovalException;
import com.example.ecsite.approval.exception.EmployeeNotFoundException;
import com.example.ecsite.approval.exception.RequestNotFoundException;
import com.example.ecsite.approval.exception.InvalidApprovalRequestException;
import com.example.ecsite.billing.exception.BillingException;
import com.example.ecsite.reservation.exception.ReservationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(CartException.class)
    public ResponseEntity<Map<String, String>> handle(CartException e) {
        return ResponseEntity.status(e.getCode().getStatus())
                .body(Map.of("code", e.getCode().name(), "message", e.getCode().getMessage()));
    }

    @ExceptionHandler(BillingException.class)
    public ResponseEntity<Map<String, String>> handle(BillingException e) {
        return ResponseEntity.status(e.getError().getStatus())
                .body(Map.of("code", e.getError().name(), "message", e.getError().getMessage()));
    }

    @ExceptionHandler(ReservationException.class)
    public ResponseEntity<Map<String, String>> handle(ReservationException e) {
        return ResponseEntity.status(e.getError().getStatus())
                .body(Map.of("code", e.getError().name(), "message", e.getError().getMessage()));
    }

    @ExceptionHandler(ApprovalException.class)
    public ResponseEntity<Map<String, String>> handle(ApprovalException e) {
        return ResponseEntity.status(e.getError().getStatus())
                .body(Map.of("code", e.getError().name(), "message", e.getError().getMessage()));
    }

    @ExceptionHandler(InvalidApprovalRequestException.class)
    public ResponseEntity<Map<String, String>> handle(InvalidApprovalRequestException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("message", e.getMessage()));
    }

    @ExceptionHandler(EmployeeNotFoundException.class)
    public ResponseEntity<Map<String, String>> handle(EmployeeNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("message", e.getMessage()));
    }

    @ExceptionHandler(RequestNotFoundException.class)
    public ResponseEntity<Map<String, String>> handle(RequestNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("message", e.getMessage()));
    }
}
