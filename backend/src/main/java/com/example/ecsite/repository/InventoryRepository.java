package com.example.ecsite.repository;

import com.example.ecsite.entity.Inventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InventoryRepository extends JpaRepository<Inventory, Long> {
    /** 引当済数を加算式で増やす。行が無ければ 0 を返す。 */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "UPDATE inventories SET reserved_quantity = reserved_quantity + :quantity WHERE product_id = :productId",
            nativeQuery = true)
    int addReserved(@Param("productId") Long productId, @Param("quantity") int quantity);
}
