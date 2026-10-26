package com.example.ecsite.approval.entity;

import jakarta.persistence.*;

import java.time.OffsetDateTime;

/** 承認の履歴。追記だけで、保存した後は更新も削除もしない(setter を持たず、列も更新不可)。 */
@Entity
@Table(name = "approval_history")
public class ApprovalHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_id", nullable = false, updatable = false)
    private Long requestId;

    @Column(name = "actor_id", nullable = false, updatable = false)
    private Long actorId;

    @Column(name = "on_behalf_of_id", updatable = false)
    private Long onBehalfOfId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private HistoryAction action;

    @Column(updatable = false)
    private String reason;

    @Column(name = "acted_at", nullable = false, updatable = false)
    private OffsetDateTime actedAt;

    protected ApprovalHistory() {
    }

    public ApprovalHistory(Long requestId, Long actorId, Long onBehalfOfId, HistoryAction action, String reason,
                           OffsetDateTime actedAt) {
        this.requestId = requestId;
        this.actorId = actorId;
        this.onBehalfOfId = onBehalfOfId;
        this.action = action;
        this.reason = reason;
        this.actedAt = actedAt;
    }

    public Long getId() { return id; }
    public Long getRequestId() { return requestId; }
    public Long getActorId() { return actorId; }
    public Long getOnBehalfOfId() { return onBehalfOfId; }
    public HistoryAction getAction() { return action; }
    public String getReason() { return reason; }
    public OffsetDateTime getActedAt() { return actedAt; }
}
