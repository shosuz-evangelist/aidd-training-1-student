package com.example.ecsite;

import com.example.ecsite.exception.CartException;
import com.example.ecsite.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 要件 5.1, 5.2, 5.3 - カートからの削除は在庫に触れない */
class CartRemoveStockTest extends EcTestBase {
    @Autowired private JdbcTemplate jdbc;

    private int stockQuantity(long productId) {
        return jdbc.queryForObject("SELECT stock_quantity FROM inventories WHERE product_id = ?", Integer.class, productId);
    }

    @Test
    void 削除するとカートから消える() {
        long customer = data.createCustomer("a@example.com");
        long product = data.createProduct("商品", 100);
        data.createInventory(product, 10, 0);
        cartService.addToCart(customer, product, 2);

        cartService.removeFromCart(customer, product);

        assertThat(cartService.getCartItems(customer)).isEmpty();
    }

    @Test
    void カートに無い商品を削除するとE04で拒否する() {
        long customer = data.createCustomer("a@example.com");
        long inCart = data.createProduct("商品1", 100);
        long other = data.createProduct("商品2", 100);
        data.createInventory(inCart, 10, 0);
        data.createInventory(other, 10, 0);
        cartService.addToCart(customer, inCart, 1);

        assertThatThrownBy(() -> cartService.removeFromCart(customer, other))
                .isInstanceOf(CartException.class)
                .extracting(e -> ((CartException) e).getCode())
                .isEqualTo(ErrorCode.NOT_IN_CART);
    }

    @Test
    void 削除の前後で在庫数と引当済数は変わらない() {
        long customer = data.createCustomer("a@example.com");
        long product = data.createProduct("商品", 100);
        data.createInventory(product, 10, 4);
        cartService.addToCart(customer, product, 3);

        cartService.removeFromCart(customer, product);

        assertThat(stockQuantity(product)).isEqualTo(10);
        assertThat(data.getReservedQuantity(product)).isEqualTo(4);
    }
}
