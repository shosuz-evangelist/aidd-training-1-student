package com.example.ecsite.reservation.dto;

import java.util.List;

public record ReservationView(long id, long courseId, String courseName, List<SessionView> sessions,
                              long price, long cancelFee, boolean cancellable) {}
