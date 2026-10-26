package com.example.ecsite;

import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 要件 2.1, 2.2, 2.3, 3.1 - 商品の一覧と詳細の API が名前、価格、購入可能数を返す */
class ProductStockApiTest extends EcTestBase {
    private long a, b, c, d;

    /** 確認用データ A から D。購入可能数はそれぞれ 10、5、2、0。 */
    private void createCheckData() {
        a = data.createProduct("商品A", 120);
        b = data.createProduct("商品B", 300);
        c = data.createProduct("商品C", 200);
        d = data.createProduct("商品D", 150);
        data.createInventory(a, 10, 0);
        data.createInventory(b, 5, 0);
        data.createInventory(c, 10, 8);
        data.createInventory(d, 3, 3);
    }

    @Test
    void 一覧は全商品の名前と価格を返す() throws Exception {
        createCheckData();

        mvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[?(@.id == %d)].name", a).value("商品A"))
                .andExpect(jsonPath("$[?(@.id == %d)].price", a).value(120))
                .andExpect(jsonPath("$[?(@.id == %d)].name", b).value("商品B"))
                .andExpect(jsonPath("$[?(@.id == %d)].price", b).value(300))
                .andExpect(jsonPath("$[?(@.id == %d)].name", c).value("商品C"))
                .andExpect(jsonPath("$[?(@.id == %d)].price", c).value(200))
                .andExpect(jsonPath("$[?(@.id == %d)].name", d).value("商品D"))
                .andExpect(jsonPath("$[?(@.id == %d)].price", d).value(150));
    }

    @Test
    void 一覧は商品ごとの購入可能数を10と5と2と0で返す() throws Exception {
        createCheckData();

        mvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == %d)].availableQuantity", a).value(10))
                .andExpect(jsonPath("$[?(@.id == %d)].availableQuantity", b).value(5))
                .andExpect(jsonPath("$[?(@.id == %d)].availableQuantity", c).value(2))
                .andExpect(jsonPath("$[?(@.id == %d)].availableQuantity", d).value(0));
    }

    @Test
    void 詳細は商品Aの名前と価格と購入可能数10を返す() throws Exception {
        createCheckData();

        mvc.perform(get("/api/products/{id}", a))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("商品A"))
                .andExpect(jsonPath("$.price").value(120))
                .andExpect(jsonPath("$.availableQuantity").value(10));
    }

    @Test
    void 詳細は商品Bの名前と価格と購入可能数5を返す() throws Exception {
        createCheckData();

        mvc.perform(get("/api/products/{id}", b))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("商品B"))
                .andExpect(jsonPath("$.price").value(300))
                .andExpect(jsonPath("$.availableQuantity").value(5));
    }

    @Test
    void 詳細は商品Cの名前と価格と購入可能数2を返す() throws Exception {
        createCheckData();

        mvc.perform(get("/api/products/{id}", c))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("商品C"))
                .andExpect(jsonPath("$.price").value(200))
                .andExpect(jsonPath("$.availableQuantity").value(2));
    }

    @Test
    void 詳細は商品Dの名前と価格と購入可能数0を返す() throws Exception {
        createCheckData();

        mvc.perform(get("/api/products/{id}", d))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("商品D"))
                .andExpect(jsonPath("$.price").value(150))
                .andExpect(jsonPath("$.availableQuantity").value(0));
    }

    @Test
    void カートの明細は商品ごとの実際の購入可能数を返す() throws Exception {
        createCheckData();
        long customer = data.createCustomer("a@example.com");
        cartService.addToCart(customer, a, 1);
        cartService.addToCart(customer, c, 1);

        mvc.perform(get("/api/cart/items").param("customerId", String.valueOf(customer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.productId == %d)].availableQuantity", a).value(10))
                .andExpect(jsonPath("$[?(@.productId == %d)].availableQuantity", c).value(2));
    }

    @Test
    void 在庫の行が無い商品は一覧でも詳細でも購入可能数0() throws Exception {
        long noRow = data.createProduct("在庫なし", 100);

        mvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == %d)].availableQuantity", noRow).value(0));
        mvc.perform(get("/api/products/{id}", noRow))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableQuantity").value(0));
    }
}
