package com.example.ecsite.dto;

import java.time.OffsetDateTime;

public record SaleView(Long productId, long unitPrice, int quantity, long totalAmount, OffsetDateTime saleDate) {
}
