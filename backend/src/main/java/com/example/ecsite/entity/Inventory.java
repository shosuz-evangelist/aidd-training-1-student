package com.example.ecsite.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "inventories")
public class Inventory {
    @Id
    @Column(name = "product_id")
    private Long productId;

    @Column(name = "stock_quantity", nullable = false)
    private int stockQuantity;

    // 引当済数は加算式 UPDATE (InventoryRepository.addReserved) だけで変える。エンティティの保存では更新しない
    @Column(name = "reserved_quantity", nullable = false, updatable = false)
    private int reservedQuantity;

    /** 購入可能数。在庫数から引当済数を引いた値で、負にならない。 */
    public int availableQuantity() {
        long available = (long) stockQuantity - reservedQuantity;
        return (int) Math.max(0L, available);
    }

    // getter/setter
    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }

    public int getStockQuantity() { return stockQuantity; }
    public void setStockQuantity(int stockQuantity) { this.stockQuantity = stockQuantity; }

    public int getReservedQuantity() { return reservedQuantity; }
    public void setReservedQuantity(int reservedQuantity) { this.reservedQuantity = reservedQuantity; }
}
