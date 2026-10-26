package com.example.ecsite;

import com.example.ecsite.service.CartService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** 要件 6.6 - 同時に購入が確定されても引当済数の加算が失われず、逆順の明細でも止まらない */
class CheckoutConcurrencyTest extends EcTestBase {
    private static final int TIMEOUT_SECONDS = 20;

    @Autowired private JdbcTemplate jdbc;

    private int saleCount() {
        return jdbc.queryForObject("SELECT count(*) FROM sale", Integer.class);
    }

    /** 全員が開始の合図を待ってから一斉に購入を確定する。待ち時間に上限を付け、止まったらテストを失敗させる。 */
    private List<String> checkoutSimultaneously(List<Long> customers, List<CartService.AvailabilityInput> availabilities)
            throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(customers.size());
        try {
            List<Future<String>> results = new ArrayList<>();
            for (long c : customers) {
                results.add(pool.submit(() -> {
                    start.await();
                    try {
                        cartService.checkout(c, availabilities);
                        return "OK";
                    } catch (RuntimeException e) {
                        return e.getClass().getSimpleName() + ": " + e.getMessage();
                    }
                }));
            }
            start.countDown();

            List<String> outcomes = new ArrayList<>();
            for (Future<String> r : results) {
                outcomes.add(r.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
            }
            return outcomes;
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void 要件6_6_10人が同じ商品を同時に購入確定しても引当済数の加算が失われない() throws Exception {
        long product = data.createProduct("商品", 100);
        data.createInventory(product, 100, 5);
        List<Long> customers = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            long c = data.createCustomer("c" + i + "@example.com");
            cartService.addToCart(c, product, 1);
            customers.add(c);
        }

        List<String> outcomes = checkoutSimultaneously(customers, List.of(new CartService.AvailabilityInput(product, 100)));

        assertThat(outcomes).containsOnly("OK").hasSize(10);
        assertThat(data.getReservedQuantity(product)).isEqualTo(15);
        assertThat(saleCount()).isEqualTo(10);
    }

    @Test
    void 要件6_6_逆順の2商品を2人が同時に購入確定しても止まらず両方の加算が反映される() throws Exception {
        long p1 = data.createProduct("商品1", 100);
        long p2 = data.createProduct("商品2", 200);
        assertThat(p1).isLessThan(p2);
        data.createInventory(p1, 1000, 3);
        data.createInventory(p2, 1000, 7);
        long x = data.createCustomer("x@example.com");
        long y = data.createCustomer("y@example.com");
        List<CartService.AvailabilityInput> availabilities = List.of(
                new CartService.AvailabilityInput(p1, 1000), new CartService.AvailabilityInput(p2, 1000));

        int rounds = 20;
        for (int round = 1; round <= rounds; round++) {
            // X は商品1 -> 商品2、Y は商品2 -> 商品1 の順にカートへ入れる
            cartService.addToCart(x, p1, 1);
            cartService.addToCart(x, p2, 1);
            cartService.addToCart(y, p2, 1);
            cartService.addToCart(y, p1, 1);

            List<String> outcomes = checkoutSimultaneously(List.of(x, y), availabilities);

            assertThat(outcomes).as("round %d", round).containsOnly("OK").hasSize(2);
            assertThat(data.getReservedQuantity(p1)).as("round %d", round).isEqualTo(3 + 2 * round);
            assertThat(data.getReservedQuantity(p2)).as("round %d", round).isEqualTo(7 + 2 * round);
        }
        assertThat(saleCount()).isEqualTo(4 * rounds);
    }
}
