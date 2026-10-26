package com.example.ecsite;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/** テストで現在の日時を動かすための Clock。 */
public class MutableClock extends Clock {
    private static final ZoneId TOKYO = ZoneId.of("Asia/Tokyo");
    private volatile Instant now = Instant.now();

    public void set(OffsetDateTime time) {
        this.now = time.toInstant();
    }

    @Override public ZoneId getZone() { return TOKYO; }
    @Override public Clock withZone(ZoneId zone) { return this; }
    @Override public Instant instant() { return now; }
}
