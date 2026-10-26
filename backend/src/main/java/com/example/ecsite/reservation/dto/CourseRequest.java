package com.example.ecsite.reservation.dto;

import com.example.ecsite.reservation.entity.CourseKind;

import java.util.List;

/** 講座登録の入力。回は入力の順に並べる。 */
public record CourseRequest(String name, String category, CourseKind kind, long price, int capacity,
                            List<SessionView> sessions) {}
