package com.example.ecsite;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/** 要件 1.1 - 在庫の表を使うテストの土台で、商品と在庫を作って引当済数を読み戻せ、初期化で在庫が空になる */
class EcTestBaseTest extends EcTestBase {
    @Autowired JdbcTemplate jdbc;

    @Test
    void 商品と在庫を作って引当済数を読み戻せる() {
        long product = data.createProduct("テスト商品", 1200);
        data.createInventory(product, 3, 5);

        assertThat(data.getReservedQuantity(product)).isEqualTo(5);
    }

    @Test
    void 初期化のあとは在庫の表が空になる() {
        long product = data.createProduct("テスト商品", 1200);
        data.createInventory(product, 3, 5);

        data.reset();

        assertThat(jdbc.queryForObject("SELECT count(*) FROM inventories", Integer.class)).isZero();
    }
}
