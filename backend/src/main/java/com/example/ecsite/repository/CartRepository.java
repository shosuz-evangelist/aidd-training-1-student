package com.example.ecsite.repository;

import com.example.ecsite.entity.Cart;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface CartRepository extends JpaRepository<Cart, Long> {
    @Query("SELECT c FROM Cart c LEFT JOIN FETCH c.cartItems WHERE c.customer.id = :customerId")
    Optional<Cart> findByCustomerIdWithItems(Long customerId);
}
