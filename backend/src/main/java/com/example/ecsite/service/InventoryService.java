package com.example.ecsite.service;

import com.example.ecsite.entity.Inventory;
import com.example.ecsite.repository.InventoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/** 在庫の購入可能数の計算と引当。呼び出し元のトランザクションに参加する。 */
@Service
public class InventoryService {
    private final InventoryRepository inventoryRepository;

    public InventoryService(InventoryRepository inventoryRepository) {
        this.inventoryRepository = inventoryRepository;
    }

    /** 購入可能数。在庫の行が無ければ 0。 */
    @Transactional(readOnly = true)
    public int availableQuantity(long productId) {
        return inventoryRepository.findById(productId).map(Inventory::availableQuantity).orElse(0);
    }

    /** 商品 ID ごとの購入可能数を 1 回の問い合わせで返す。在庫の行が無い商品も 0 で含める。 */
    @Transactional(readOnly = true)
    public Map<Long, Integer> availableQuantities(Collection<Long> productIds) {
        Map<Long, Integer> result = new HashMap<>();
        for (Long productId : productIds) {
            result.put(productId, 0);
        }
        for (Inventory inventory : inventoryRepository.findAllById(productIds)) {
            result.put(inventory.getProductId(), inventory.availableQuantity());
        }
        return result;
    }

    /** 引当済数を加算する。在庫の行が無いか購入可能数が足りなければ false。呼び出し元のトランザクションに参加する。 */
    @Transactional
    public boolean reserve(long productId, int quantity) {
        return inventoryRepository.addReserved(productId, quantity) > 0;
    }

    /** 画面で確認した購入可能数を使って引き当てる。 */
    @Transactional
    public boolean reserve(long productId, int quantity, int expectedAvailable) {
        return inventoryRepository.addReservedUsingSnapshot(productId, quantity, expectedAvailable) > 0;
    }
}
