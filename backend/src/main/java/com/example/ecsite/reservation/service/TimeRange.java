package com.example.ecsite.reservation.service;

import java.time.OffsetDateTime;

/** 開始・終了の組。終了と開始が同じ（接するだけ）なら重ならない。 */
public record TimeRange(OffsetDateTime start, OffsetDateTime end) {

    /** this.start < other.end && other.start < this.end */
    public boolean overlaps(TimeRange other) {
        return start.isBefore(other.end) && other.start.isBefore(end);
    }
}
