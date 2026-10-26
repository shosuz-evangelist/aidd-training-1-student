package com.example.ecsite.reservation;

import com.example.ecsite.reservation.service.TimeRange;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class TimeRangeTest {

    private static OffsetDateTime at(int hour) {
        return OffsetDateTime.parse("2026-11-01T00:00:00+09:00").plusHours(hour);
    }

    private static TimeRange range(int startHour, int endHour) {
        return new TimeRange(at(startHour), at(endHour));
    }

    @Test
    void 一部が重なるときは重なる() {
        assertThat(range(10, 12).overlaps(range(11, 13))).isTrue();
        assertThat(range(11, 13).overlaps(range(10, 12))).isTrue();
    }

    @Test
    void 完全に含むときは重なる() {
        assertThat(range(10, 14).overlaps(range(11, 12))).isTrue();
        assertThat(range(11, 12).overlaps(range(10, 14))).isTrue();
    }

    @Test
    void 終了と開始が同じなら重ならない() {
        assertThat(range(10, 12).overlaps(range(12, 14))).isFalse();
        assertThat(range(12, 14).overlaps(range(10, 12))).isFalse();
    }

    @Test
    void 離れているときは重ならない() {
        assertThat(range(10, 11).overlaps(range(13, 14))).isFalse();
        assertThat(range(13, 14).overlaps(range(10, 11))).isFalse();
    }
}
