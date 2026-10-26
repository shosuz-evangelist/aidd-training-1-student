package com.example.ecsite.approval.dto;

import com.example.ecsite.approval.entity.ApprovalHistory;
import com.example.ecsite.approval.entity.HistoryAction;

import java.time.OffsetDateTime;
import java.time.ZoneId;

/** 履歴の 1 件。日時は日本時間(+09:00)で表す。本人の操作では onBehalfOfId は null。 */
public record HistoryView(Long id, Long actorId, Long onBehalfOfId, HistoryAction action, OffsetDateTime actedAt,
                          String reason) {
    private static final ZoneId JST = ZoneId.of("Asia/Tokyo");

    public static HistoryView of(ApprovalHistory h) {
        return new HistoryView(h.getId(), h.getActorId(), h.getOnBehalfOfId(), h.getAction(),
                h.getActedAt().atZoneSameInstant(JST).toOffsetDateTime(), h.getReason());
    }
}
