package com.example.ecsite;

import com.example.ecsite.service.InventoryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** 要件 1.1 から 1.5 - 在庫は商品と別の表で持ち、購入可能数は在庫数から引当済数を引いた値で、負にならない */
class InventoryAvailabilityTest extends EcTestBase {
    @Autowired InventoryService inventoryService;
    @Autowired JdbcTemplate jdbc;

    @Test
    void 在庫10で引当0のとき購入可能数は10() {
        long product = data.createProduct("商品A", 1000);
        data.createInventory(product, 10, 0);

        assertThat(inventoryService.availableQuantity(product)).isEqualTo(10);
    }

    @Test
    void 在庫10で引当8のとき購入可能数は2() {
        long product = data.createProduct("商品A", 1000);
        data.createInventory(product, 10, 8);

        assertThat(inventoryService.availableQuantity(product)).isEqualTo(2);
    }

    @Test
    void 在庫3で引当3のとき購入可能数は0() {
        long product = data.createProduct("商品A", 1000);
        data.createInventory(product, 3, 3);

        assertThat(inventoryService.availableQuantity(product)).isZero();
    }

    @Test
    void 在庫の行が無い商品の購入可能数は0() {
        long product = data.createProduct("商品A", 1000);

        assertThat(inventoryService.availableQuantity(product)).isZero();
    }

    @Test
    void 引当が在庫を超えていても購入可能数は負にならず0() {
        long product = data.createProduct("商品A", 1000);
        data.createInventory(product, 3, 5);

        assertThat(inventoryService.availableQuantity(product)).isZero();
    }

    @Test
    void 引当が在庫を超える行も保存でき読み戻せる() {
        long product = data.createProduct("商品A", 1000);
        data.createInventory(product, 3, 5);

        Map<String, Object> row = jdbc.queryForMap(
                "SELECT stock_quantity, reserved_quantity FROM inventories WHERE product_id = ?", product);
        assertThat(row.get("stock_quantity")).isEqualTo(3);
        assertThat(row.get("reserved_quantity")).isEqualTo(5);
        assertThat(data.getReservedQuantity(product)).isEqualTo(5);
    }

    @Test
    void 在庫は商品と別の表にあり名前と価格は商品の表から読める() {
        assertThat(columns("inventories")).contains("product_id", "stock_quantity", "reserved_quantity")
                .doesNotContain("name", "price");
        assertThat(columns("products")).contains("name", "price")
                .doesNotContain("stock_quantity", "reserved_quantity");

        long product = data.createProduct("商品A", 1234);
        data.createInventory(product, 10, 0);

        Map<String, Object> row = jdbc.queryForMap("SELECT name, price FROM products WHERE id = ?", product);
        assertThat(row.get("name")).isEqualTo("商品A");
        assertThat(row.get("price")).isEqualTo(1234);
    }

    @Test
    void まとめて引くと商品ごとの購入可能数が返り行が無い商品も0で含まれる() {
        long full = data.createProduct("商品A", 1000);
        long partial = data.createProduct("商品B", 1000);
        long over = data.createProduct("商品C", 1000);
        long noRow = data.createProduct("商品D", 1000);
        data.createInventory(full, 10, 0);
        data.createInventory(partial, 10, 8);
        data.createInventory(over, 3, 5);

        Map<Long, Integer> result = inventoryService.availableQuantities(List.of(full, partial, over, noRow));

        assertThat(result).containsOnlyKeys(full, partial, over, noRow);
        assertThat(result).containsEntry(full, 10).containsEntry(partial, 2)
                .containsEntry(over, 0).containsEntry(noRow, 0);
    }

    private List<String> columns(String table) {
        return jdbc.queryForList(
                "SELECT column_name FROM information_schema.columns WHERE table_schema = current_schema() AND table_name = ?",
                String.class, table);
    }
}
