package com.example.ecsite.approval.entity;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/** 購買申請。明細を持つ集約の根で、明細は申請と一緒に保存する。 */
@Entity
@Table(name = "purchase_request")
public class PurchaseRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "applicant_id", nullable = false)
    private Long applicantId;

    @Column(name = "total_amount", nullable = false)
    private long totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RequestStatus status;

    /** status が PENDING のときだけ値を持つ。 */
    @Enumerated(EnumType.STRING)
    @Column(name = "pending_role")
    private EmployeeRole pendingRole;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @OneToMany(mappedBy = "request", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<PurchaseRequestItem> items = new ArrayList<>();

    protected PurchaseRequest() {
    }

    public PurchaseRequest(Long applicantId, long totalAmount, RequestStatus status, EmployeeRole pendingRole,
                           OffsetDateTime createdAt) {
        this.applicantId = applicantId;
        this.totalAmount = totalAmount;
        this.status = status;
        this.pendingRole = pendingRole;
        this.createdAt = createdAt;
    }

    public void addItem(PurchaseRequestItem item) {
        item.setRequest(this);
        items.add(item);
    }

    public Long getId() { return id; }
    public Long getApplicantId() { return applicantId; }
    public long getTotalAmount() { return totalAmount; }
    public RequestStatus getStatus() { return status; }
    public EmployeeRole getPendingRole() { return pendingRole; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public List<PurchaseRequestItem> getItems() { return items; }
}
