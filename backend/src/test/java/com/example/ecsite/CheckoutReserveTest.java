package com.example.ecsite;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 要件 6.1, 6.2, 6.3, 6.4, 6.5, 7.1, 7.2, 7.3, 7.4, 7.5, 7.6, 8.1, 8.2 - 購入の確定が購入可能数を検査して引き当てる */
class CheckoutReserveTest extends EcTestBase {
    @Autowired private JdbcTemplate jdbc;

    /** 画面から送る購入可能数。 */
    private record Avail(long productId, int availableQuantity) {}

    /** availabilities を null にすると、その項目自体を送らない。 */
    private String checkoutJson(long customerId, List<Avail> availabilities) {
        if (availabilities == null) {
            return "{\"customerId\":%d}".formatted(customerId);
        }
        String items = availabilities.stream()
                .map(a -> "{\"productId\":%d,\"availableQuantity\":%d}".formatted(a.productId(), a.availableQuantity()))
                .collect(Collectors.joining(","));
        return "{\"customerId\":%d,\"availabilities\":[%s]}".formatted(customerId, items);
    }

    private ResultActions checkout(long customerId, List<Avail> availabilities) throws Exception {
        return mvc.perform(post("/api/cart/checkout")
                .contentType(MediaType.APPLICATION_JSON)
                .content(checkoutJson(customerId, availabilities)));
    }

    private int saleCount() {
        return jdbc.queryForObject("SELECT count(*) FROM sale", Integer.class);
    }

    private int saleCount(long productId) {
        return jdbc.queryForObject("SELECT count(*) FROM sale WHERE product_id = ?", Integer.class, productId);
    }

    private int cartItemCount(long customerId) {
        return cartService.getCartItems(customerId).size();
    }

    @Test
    void 明細が0件ならE05で拒否し引当済数も売上も変わらない() throws Exception {
        long customer = data.createCustomer("a@example.com");
        long product = data.createProduct("商品", 100);
        data.createInventory(product, 10, 4);

        checkout(customer, List.of(new Avail(product, 6)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CART_EMPTY"))
                .andExpect(jsonPath("$.message").value("カートが空です"));

        assertThat(data.getReservedQuantity(product)).isEqualTo(4);
        assertThat(saleCount()).isZero();
    }

    @Test
    void 全明細の購入可能数が足りると引当済数が数量だけ増え売上が明細ごとに1件でカートが空になる() throws Exception {
        long customer = data.createCustomer("a@example.com");
        long p1 = data.createProduct("商品1", 100);
        long p2 = data.createProduct("商品2", 250);
        data.createInventory(p1, 10, 2);
        data.createInventory(p2, 5, 0);
        cartService.addToCart(customer, p1, 3);
        cartService.addToCart(customer, p2, 4);

        checkout(customer, List.of(new Avail(p1, 8), new Avail(p2, 5)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.length()").value(2));

        assertThat(data.getReservedQuantity(p1)).isEqualTo(5);
        assertThat(data.getReservedQuantity(p2)).isEqualTo(4);
        assertThat(saleCount()).isEqualTo(2);
        assertThat(saleCount(p1)).isEqualTo(1);
        assertThat(saleCount(p2)).isEqualTo(1);
        assertThat(cartItemCount(customer)).isZero();
    }

    @Test
    void 送った購入可能数が数量ちょうどでも検査が通る() throws Exception {
        long customer = data.createCustomer("a@example.com");
        long product = data.createProduct("商品", 100);
        data.createInventory(product, 10, 0);
        cartService.addToCart(customer, product, 3);

        checkout(customer, List.of(new Avail(product, 3))).andExpect(status().isCreated());

        assertThat(data.getReservedQuantity(product)).isEqualTo(3);
        assertThat(saleCount()).isEqualTo(1);
    }

    @Test
    void 画面から送られた購入可能数が数量以上なら実際の購入可能数が0でも検査が通り引当済数が加算される() throws Exception {
        long customer = data.createCustomer("a@example.com");
        long product = data.createProduct("商品", 100);
        data.createInventory(product, 10, 0);
        cartService.addToCart(customer, product, 3);
        // カートに入れた後で、実際の購入可能数が 0 になる
        jdbc.update("UPDATE inventories SET stock_quantity = 3, reserved_quantity = 3 WHERE product_id = ?", product);

        checkout(customer, List.of(new Avail(product, 3))).andExpect(status().isCreated());

        assertThat(data.getReservedQuantity(product)).isEqualTo(6);
        assertThat(saleCount()).isEqualTo(1);
        assertThat(cartItemCount(customer)).isZero();
    }

    @Test
    void 購入可能数が数量未満の明細が1件でもあればE02で拒否し全商品の引当済数と売上とカートが変わらない() throws Exception {
        long customer = data.createCustomer("a@example.com");
        long p1 = data.createProduct("商品1", 100);
        long p2 = data.createProduct("商品2", 200);
        data.createInventory(p1, 10, 1);
        data.createInventory(p2, 10, 2);
        cartService.addToCart(customer, p1, 3);
        cartService.addToCart(customer, p2, 4);

        checkout(customer, List.of(new Avail(p1, 9), new Avail(p2, 3)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"))
                .andExpect(jsonPath("$.message").value("在庫が足りません"));

        assertThat(data.getReservedQuantity(p1)).isEqualTo(1);
        assertThat(data.getReservedQuantity(p2)).isEqualTo(2);
        assertThat(saleCount()).isZero();
        assertThat(cartItemCount(customer)).isEqualTo(2);
    }

    @Test
    void 先の商品が足りず後の商品が足りるときもE02で何も変わらない() throws Exception {
        long customer = data.createCustomer("a@example.com");
        long p1 = data.createProduct("商品1", 100);
        long p2 = data.createProduct("商品2", 200);
        data.createInventory(p1, 10, 0);
        data.createInventory(p2, 10, 0);
        cartService.addToCart(customer, p1, 3);
        cartService.addToCart(customer, p2, 4);

        checkout(customer, List.of(new Avail(p1, 2), new Avail(p2, 10)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"));

        assertThat(data.getReservedQuantity(p1)).isZero();
        assertThat(data.getReservedQuantity(p2)).isZero();
        assertThat(saleCount()).isZero();
        assertThat(cartItemCount(customer)).isEqualTo(2);
    }

    @Test
    void 購入可能数の項目を省略するとE02で拒否する() throws Exception {
        long customer = data.createCustomer("a@example.com");
        long product = data.createProduct("商品", 100);
        data.createInventory(product, 10, 0);
        cartService.addToCart(customer, product, 3);

        checkout(customer, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"));

        assertThat(data.getReservedQuantity(product)).isZero();
        assertThat(saleCount()).isZero();
        assertThat(cartItemCount(customer)).isEqualTo(1);
    }

    @Test
    void 購入可能数の一覧が空ならE02で拒否する() throws Exception {
        long customer = data.createCustomer("a@example.com");
        long product = data.createProduct("商品", 100);
        data.createInventory(product, 10, 0);
        cartService.addToCart(customer, product, 3);

        checkout(customer, List.of())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"));

        assertThat(data.getReservedQuantity(product)).isZero();
        assertThat(saleCount()).isZero();
        assertThat(cartItemCount(customer)).isEqualTo(1);
    }

    @Test
    void 一部の商品の購入可能数しか送られていなければE02で拒否し何も変わらない() throws Exception {
        long customer = data.createCustomer("a@example.com");
        long p1 = data.createProduct("商品1", 100);
        long p2 = data.createProduct("商品2", 200);
        data.createInventory(p1, 10, 0);
        data.createInventory(p2, 10, 0);
        cartService.addToCart(customer, p1, 3);
        cartService.addToCart(customer, p2, 4);

        checkout(customer, List.of(new Avail(p1, 10)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"));

        assertThat(data.getReservedQuantity(p1)).isZero();
        assertThat(data.getReservedQuantity(p2)).isZero();
        assertThat(saleCount()).isZero();
        assertThat(cartItemCount(customer)).isEqualTo(2);
    }

    @Test
    void 引当の途中で在庫の行が無い商品に当たるとE02で先に更新した引当済数も戻る() throws Exception {
        long customer = data.createCustomer("a@example.com");
        long p1 = data.createProduct("商品1", 100);
        long p2 = data.createProduct("商品2", 200); // id が大きいので p1 の後に処理される
        data.createInventory(p1, 10, 1);
        data.createInventory(p2, 10, 0);
        cartService.addToCart(customer, p1, 3);
        cartService.addToCart(customer, p2, 4);
        jdbc.update("DELETE FROM inventories WHERE product_id = ?", p2);

        checkout(customer, List.of(new Avail(p1, 9), new Avail(p2, 10)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"))
                .andExpect(jsonPath("$.message").value("在庫が足りません"));

        assertThat(data.getReservedQuantity(p1)).isEqualTo(1);
        assertThat(saleCount()).isZero();
        assertThat(cartItemCount(customer)).isEqualTo(2);
    }

    @Test
    void 売上の単価は購入確定時の価格で金額は単価掛ける数量で日時は購入確定の日時() throws Exception {
        long customer = data.createCustomer("a@example.com");
        long product = data.createProduct("商品", 100);
        data.createInventory(product, 10, 0);
        cartService.addToCart(customer, product, 3);
        // カートに入れた後で価格が変わる。購入確定時の価格が単価になる
        jdbc.update("UPDATE products SET price = 150 WHERE id = ?", product);

        OffsetDateTime before = OffsetDateTime.now();
        checkout(customer, List.of(new Avail(product, 10))).andExpect(status().isCreated());
        OffsetDateTime after = OffsetDateTime.now();

        assertThat(jdbc.queryForObject("SELECT unit_price FROM sale WHERE product_id = ?", Long.class, product)).isEqualTo(150L);
        assertThat(jdbc.queryForObject("SELECT quantity FROM sale WHERE product_id = ?", Integer.class, product)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT total_amount FROM sale WHERE product_id = ?", Long.class, product)).isEqualTo(450L);
        OffsetDateTime saleDate = jdbc.queryForObject("SELECT sale_date FROM sale WHERE product_id = ?", OffsetDateTime.class, product);
        assertThat(saleDate).isBetween(before.minusSeconds(1), after.plusSeconds(1));
    }

    @Test
    void 登録後に商品の価格を変えても登録済みの売上は変わらない() throws Exception {
        long customer = data.createCustomer("a@example.com");
        long product = data.createProduct("商品", 100);
        data.createInventory(product, 10, 0);
        cartService.addToCart(customer, product, 2);
        checkout(customer, List.of(new Avail(product, 10))).andExpect(status().isCreated());

        jdbc.update("UPDATE products SET price = 999 WHERE id = ?", product);

        assertThat(jdbc.queryForObject("SELECT unit_price FROM sale WHERE product_id = ?", Long.class, product)).isEqualTo(100L);
        assertThat(jdbc.queryForObject("SELECT total_amount FROM sale WHERE product_id = ?", Long.class, product)).isEqualTo(200L);
    }

    @Test
    void 引当と売上の登録が成功したときの応答は明細ごとの売上を返す() throws Exception {
        long customer = data.createCustomer("a@example.com");
        long product = data.createProduct("商品", 120);
        data.createInventory(product, 10, 0);
        cartService.addToCart(customer, product, 2);

        checkout(customer, List.of(new Avail(product, 10)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$[0].productId").value(product))
                .andExpect(jsonPath("$[0].unitPrice").value(120))
                .andExpect(jsonPath("$[0].quantity").value(2))
                .andExpect(jsonPath("$[0].totalAmount").value(240));
    }
}
