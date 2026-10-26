package com.example.ecsite;

import com.example.ecsite.dto.CartItemView;
import com.example.ecsite.exception.CartException;
import com.example.ecsite.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 要件 4.1, 4.2, 4.3, 4.4, 4.5, 4.7 - カートへの追加が購入可能数を超えると拒否する */
class CartAddStockTest extends EcTestBase {
    @Autowired private JdbcTemplate jdbc;

    private int stockQuantity(long productId) {
        return jdbc.queryForObject("SELECT stock_quantity FROM inventories WHERE product_id = ?", Integer.class, productId);
    }

    private int cartQuantity(long customerId, long productId) {
        List<CartItemView> items = cartService.getCartItems(customerId);
        return items.stream().filter(i -> i.productId().equals(productId))
                .mapToInt(CartItemView::quantity).sum();
    }

    private void assertCode(Runnable action, ErrorCode expected) {
        assertThatThrownBy(action::run)
                .isInstanceOf(CartException.class)
                .extracting(e -> ((CartException) e).getCode())
                .isEqualTo(expected);
    }

    @Test
    void 数量が0ならE01で拒否する() {
        long customer = data.createCustomer("a@example.com");
        long product = data.createProduct("商品", 100);
        data.createInventory(product, 10, 0);

        assertCode(() -> cartService.addToCart(customer, product, 0), ErrorCode.INVALID_QUANTITY);
    }

    @Test
    void 存在しない商品ならE03で拒否する() {
        long customer = data.createCustomer("a@example.com");

        assertCode(() -> cartService.addToCart(customer, 9999L, 1), ErrorCode.PRODUCT_NOT_FOUND);
    }

    @Test
    void 数量が不正かつ商品も存在しないときはE01を先に返す() {
        long customer = data.createCustomer("a@example.com");

        assertCode(() -> cartService.addToCart(customer, 9999L, 0), ErrorCode.INVALID_QUANTITY);
    }

    @Test
    void 商品が存在せず数量が大きいときはE02より先にE03を返す() {
        long customer = data.createCustomer("a@example.com");

        assertCode(() -> cartService.addToCart(customer, 9999L, 1000), ErrorCode.PRODUCT_NOT_FOUND);
    }

    @Test
    void 購入可能数を超える数量で初めて追加するとE02で拒否しカートに入らない() {
        long customer = data.createCustomer("a@example.com");
        long product = data.createProduct("商品", 100);
        data.createInventory(product, 10, 8);

        assertCode(() -> cartService.addToCart(customer, product, 3), ErrorCode.INSUFFICIENT_STOCK);
        assertThat(cartService.getCartItems(customer)).isEmpty();
    }

    @Test
    void カート内の数量と追加分の合計が購入可能数ちょうどなら追加できる() {
        long customer = data.createCustomer("a@example.com");
        long product = data.createProduct("商品", 100);
        data.createInventory(product, 10, 8);
        cartService.addToCart(customer, product, 1);

        cartService.addToCart(customer, product, 1);

        assertThat(cartQuantity(customer, product)).isEqualTo(2);
    }

    @Test
    void カート内の数量と追加分の合計が購入可能数を1超えるとE02で拒否しカートは変わらない() {
        long customer = data.createCustomer("a@example.com");
        long product = data.createProduct("商品", 100);
        data.createInventory(product, 10, 8);
        cartService.addToCart(customer, product, 2);

        assertCode(() -> cartService.addToCart(customer, product, 1), ErrorCode.INSUFFICIENT_STOCK);
        assertThat(cartQuantity(customer, product)).isEqualTo(2);
    }

    @Test
    void 購入可能数0の商品を追加するとE02で拒否する() {
        long customer = data.createCustomer("a@example.com");
        long product = data.createProduct("商品", 100);
        data.createInventory(product, 3, 3);

        assertCode(() -> cartService.addToCart(customer, product, 1), ErrorCode.INSUFFICIENT_STOCK);
        assertThat(cartService.getCartItems(customer)).isEmpty();
    }

    @Test
    void 同じ商品を再び追加すると数量が加算される() {
        long customer = data.createCustomer("a@example.com");
        long product = data.createProduct("商品", 100);
        data.createInventory(product, 10, 0);
        cartService.addToCart(customer, product, 2);

        cartService.addToCart(customer, product, 3);

        assertThat(cartService.getCartItems(customer)).hasSize(1);
        assertThat(cartQuantity(customer, product)).isEqualTo(5);
    }

    @Test
    void 追加の前後で在庫数と引当済数は変わらない() {
        long customer = data.createCustomer("a@example.com");
        long product = data.createProduct("商品", 100);
        data.createInventory(product, 10, 4);

        cartService.addToCart(customer, product, 3);

        assertThat(stockQuantity(product)).isEqualTo(10);
        assertThat(data.getReservedQuantity(product)).isEqualTo(4);
    }

    @Test
    void 追加をE02で拒否した前後でも在庫数と引当済数は変わらない() {
        long customer = data.createCustomer("a@example.com");
        long product = data.createProduct("商品", 100);
        data.createInventory(product, 10, 8);

        assertCode(() -> cartService.addToCart(customer, product, 3), ErrorCode.INSUFFICIENT_STOCK);

        assertThat(stockQuantity(product)).isEqualTo(10);
        assertThat(data.getReservedQuantity(product)).isEqualTo(8);
    }

    @Test
    void APIは購入可能数を超える追加に409とINSUFFICIENT_STOCKを返す() throws Exception {
        long customer = data.createCustomer("a@example.com");
        long product = data.createProduct("商品", 100);
        data.createInventory(product, 10, 8);

        mvc.perform(post("/api/cart")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerId\":%d,\"productId\":%d,\"quantity\":3}".formatted(customer, product)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"))
                .andExpect(jsonPath("$.message").value("在庫が足りません"));
    }
}
