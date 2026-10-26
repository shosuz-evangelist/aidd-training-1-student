package com.example.ecsite.reservation.exception;

public class ReservationException extends RuntimeException {
    private final ReservationError error;

    public ReservationException(ReservationError error) {
        super(error.getMessage());
        this.error = error;
    }

    public ReservationError getError() { return error; }
}
