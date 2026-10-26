package com.example.ecsite.reservation.dto;

import java.time.OffsetDateTime;

public record SessionView(OffsetDateTime startsAt, OffsetDateTime endsAt, String room, String instructor) {}
