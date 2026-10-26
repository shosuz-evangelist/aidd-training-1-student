package com.example.ecsite.reservation.dto;

import com.example.ecsite.reservation.entity.CourseKind;

import java.util.List;

/** 講座一覧の 1 行。 */
public record CourseView(long id, String name, String category, CourseKind kind, List<SessionView> sessions,
                         long price, int remaining, String seatLabel, boolean reservable) {}
