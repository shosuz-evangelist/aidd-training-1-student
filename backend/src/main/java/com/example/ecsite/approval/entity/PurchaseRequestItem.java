package com.example.ecsite.approval.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "purchase_request_item")
public class PurchaseRequestItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "request_id", nullable = false)
    private PurchaseRequest request;

    @Column(name = "unit_price", nullable = false)
    private long unitPrice;

    @Column(nullable = false)
    private int quantity;

    protected PurchaseRequestItem() {
    }

    public PurchaseRequestItem(long unitPrice, int quantity) {
        this.unitPrice = unitPrice;
        this.quantity = quantity;
    }

    void setRequest(PurchaseRequest request) { this.request = request; }

    public Long getId() { return id; }
    public PurchaseRequest getRequest() { return request; }
    public long getUnitPrice() { return unitPrice; }
    public int getQuantity() { return quantity; }
}
