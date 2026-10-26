package com.example.ecsite;

import com.example.ecsite.entity.Inventory;
import com.example.ecsite.repository.InventoryRepository;
import com.example.ecsite.service.InventoryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/** 要件 6.5 - 引当済数は加算式 UPDATE だけで変える */
class InventoryReserveTest extends EcTestBase {
    @Autowired InventoryService inventoryService;
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate tx;
    @Autowired InventoryRepository inventoryRepository;

    @Test
    void 引当すると引当済数に加算され在庫数は変わらない() {
        long product = data.createProduct("商品A", 1000);
        data.createInventory(product, 10, 2);

        boolean reserved = tx.execute(status -> inventoryService.reserve(product, 3));

        assertThat(reserved).isTrue();
        assertThat(data.getReservedQuantity(product)).isEqualTo(5);
        assertThat(jdbc.queryForObject("SELECT stock_quantity FROM inventories WHERE product_id = ?",
                Integer.class, product)).isEqualTo(10);
    }

    @Test
    void 在庫の行が無い商品の引当はfalseを返す() {
        long product = data.createProduct("商品A", 1000);

        boolean reserved = tx.execute(status -> inventoryService.reserve(product, 1));

        assertThat(reserved).isFalse();
    }

    @Test
    void エンティティを保存しても引当済数は更新されない() {
        long product = data.createProduct("商品A", 1000);
        data.createInventory(product, 10, 2);

        tx.executeWithoutResult(status -> {
            Inventory inventory = inventoryRepository.findById(product).orElseThrow();
            inventory.setReservedQuantity(99);
            inventory.setStockQuantity(7);
            inventoryRepository.saveAndFlush(inventory);
        });

        assertThat(data.getReservedQuantity(product)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT stock_quantity FROM inventories WHERE product_id = ?",
                Integer.class, product)).isEqualTo(7);
    }
}
