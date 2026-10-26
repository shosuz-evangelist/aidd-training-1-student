package com.example.ecsite.reservation.exception;

import org.springframework.http.HttpStatus;

public enum ReservationError {
    R01(HttpStatus.CONFLICT, "満席です"),
    R02(HttpStatus.CONFLICT, "受付を締め切りました"),
    R03(HttpStatus.CONFLICT, "すでに予約しています"),
    R04(HttpStatus.CONFLICT, "時間帯が重なります"),
    R05(HttpStatus.CONFLICT, "開始後はキャンセルできません"),
    R06(HttpStatus.NOT_FOUND, "予約が見つかりません"),
    R07(HttpStatus.BAD_REQUEST, "入力が正しくありません"),
    R08(HttpStatus.CONFLICT, "教室が重なります"),
    R09(HttpStatus.CONFLICT, "講師が重なります");

    private final HttpStatus status;
    private final String message;

    ReservationError(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public HttpStatus getStatus() { return status; }
    public String getMessage() { return message; }
}
