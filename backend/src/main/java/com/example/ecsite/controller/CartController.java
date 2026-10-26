package com.example.ecsite.controller;

import com.example.ecsite.dto.CartItemView;
import com.example.ecsite.dto.SaleView;
import com.example.ecsite.service.CartService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/cart")
public class CartController {
    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    public record AddRequest(Long customerId, Long productId, int quantity) {}

    public record CheckoutRequest(Long customerId, List<CartService.AvailabilityInput> availabilities) {}

    @PostMapping("")
    public ResponseEntity<List<CartItemView>> addToCart(@RequestBody AddRequest request) {
        List<CartItemView> items = cartService.addToCart(request.customerId(), request.productId(), request.quantity());
        return ResponseEntity.status(HttpStatus.CREATED).body(items);
    }

    @GetMapping("/items")
    public List<CartItemView> getCartItems(@RequestParam Long customerId) {
        return cartService.getCartItems(customerId);
    }

    @DeleteMapping("/items/{productId}")
    public ResponseEntity<Void> removeFromCart(@PathVariable Long productId, @RequestParam Long customerId) {
        cartService.removeFromCart(customerId, productId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/checkout")
    public ResponseEntity<List<SaleView>> checkout(@RequestBody CheckoutRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(cartService.checkout(request.customerId(), request.availabilities()));
    }
}
