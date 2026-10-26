package com.example.ecsite.dto;

public record CartItemView(Long productId, String productName, String imageUrl, long unitPrice, int quantity,
                           int availableQuantity) {
}
