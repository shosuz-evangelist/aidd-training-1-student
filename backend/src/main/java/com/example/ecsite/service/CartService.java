package com.example.ecsite.service;

import com.example.ecsite.dto.CartItemView;
import com.example.ecsite.dto.SaleView;
import com.example.ecsite.entity.Cart;
import com.example.ecsite.entity.CartItem;
import com.example.ecsite.entity.Customer;
import com.example.ecsite.entity.Product;
import com.example.ecsite.entity.Sale;
import com.example.ecsite.exception.CartException;
import com.example.ecsite.exception.ErrorCode;
import com.example.ecsite.repository.CartItemRepository;
import com.example.ecsite.repository.CartRepository;
import com.example.ecsite.repository.ProductRepository;
import com.example.ecsite.repository.SaleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** カートへの出し入れと購入の確定。 */
@Service
public class CartService {
    private static final ZoneId TOKYO = ZoneId.of("Asia/Tokyo");

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final SaleRepository saleRepository;
    private final InventoryService inventoryService;

    public CartService(CartRepository cartRepository, CartItemRepository cartItemRepository,
                       ProductRepository productRepository, SaleRepository saleRepository,
                       InventoryService inventoryService) {
        this.inventoryService = inventoryService;
        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
        this.productRepository = productRepository;
        this.saleRepository = saleRepository;
    }

    @Transactional(readOnly = true)
    public List<CartItemView> getCartItems(Long customerId) {
        return cartRepository.findByCustomerIdWithItems(customerId)
                .map(this::toViews)
                .orElse(Collections.emptyList());
    }

    /** カートの数量を増やす。カートにない商品なら、カートに足す。 */
    @Transactional
    public List<CartItemView> addToCart(Long customerId, Long productId, int quantity) {
        if (quantity <= 0) {
            throw new CartException(ErrorCode.INVALID_QUANTITY);
        }
        if (!productRepository.existsById(productId)) {
            throw new CartException(ErrorCode.PRODUCT_NOT_FOUND);
        }

        Optional<Cart> found = cartRepository.findByCustomerIdWithItems(customerId);
        long inCart = found.flatMap(c -> findItem(c, productId))
                .map(CartItem::getQuantity)
                .orElse(0);
        if (inCart + (long) quantity > inventoryService.availableQuantity(productId)) {
            throw new CartException(ErrorCode.INSUFFICIENT_STOCK);
        }

        Cart cart = found.orElseGet(() -> createCart(customerId));
        Optional<CartItem> existing = findItem(cart, productId);
        if (existing.isPresent()) {
            existing.get().setQuantity(existing.get().getQuantity() + quantity);
        } else {
            CartItem item = new CartItem();
            item.setCart(cart);
            item.setProduct(productRepository.getReferenceById(productId));
            item.setQuantity(quantity);
            cart.getCartItems().add(item);
        }
        cartRepository.saveAndFlush(cart);
        return toViews(cart);
    }

    /** カートから外す。 */
    @Transactional
    public void removeFromCart(Long customerId, Long productId) {
        Cart cart = cartRepository.findByCustomerIdWithItems(customerId)
                .orElseThrow(() -> new CartException(ErrorCode.NOT_IN_CART));
        CartItem item = findItem(cart, productId)
                .orElseThrow(() -> new CartException(ErrorCode.NOT_IN_CART));
        cart.getCartItems().remove(item);
        cartRepository.save(cart);
    }

    /** 画面が見ていた商品ごとの購入可能数。 */
    public record AvailabilityInput(Long productId, int availableQuantity) {}

    /**
     * 送られた購入可能数で検査し、商品 ID の昇順で引き当て、カートの全商品の売上を記録し、カートを空にする。
     * 引当は加算式の更新で行い、同時購入でも引当済数の加算を失わない。
     * 失敗(CartException)したときは、全ての変更を戻す。
     */
    @Transactional
    public List<SaleView> checkout(Long customerId, List<AvailabilityInput> availabilities) {
        Cart cart = cartRepository.findByCustomerIdWithItems(customerId)
                .filter(c -> !c.getCartItems().isEmpty())
                .orElseThrow(() -> new CartException(ErrorCode.CART_EMPTY));

        record Line(Long productId, long unitPrice, int quantity) {}
        List<Line> lines = cart.getCartItems().stream()
                .map(i -> new Line(i.getProduct().getId(), i.getProduct().getPrice(), i.getQuantity()))
                .sorted(Comparator.comparing(Line::productId))
                .toList();
        Long cartId = cart.getCartId();
        OffsetDateTime now = OffsetDateTime.now(TOKYO);

        // 同じ商品 ID が重複して送られたときは、小さい方を採る(厳しい側)。送られていない商品は 0 とみなす。
        Map<Long, Integer> sent = new HashMap<>();
        if (availabilities != null) {
            for (AvailabilityInput a : availabilities) {
                sent.merge(a.productId(), a.availableQuantity(), Math::min);
            }
        }
        for (Line line : lines) {
            if (sent.getOrDefault(line.productId(), 0) < line.quantity()) {
                throw new CartException(ErrorCode.INSUFFICIENT_STOCK);
            }
        }
        for (Line line : lines) {
            if (!inventoryService.reserve(line.productId(), line.quantity(),
                    sent.getOrDefault(line.productId(), 0))) {
                throw new CartException(ErrorCode.INSUFFICIENT_STOCK);
            }
        }

        List<SaleView> sales = new ArrayList<>();
        for (Line line : lines) {
            Sale sale = new Sale();
            sale.setCustomerId(customerId);
            sale.setProductId(line.productId());
            sale.setUnitPrice(line.unitPrice());
            sale.setQuantity(line.quantity());
            sale.setTotalAmount(line.unitPrice() * line.quantity());
            sale.setSaleDate(now);
            saleRepository.save(sale);
            sales.add(new SaleView(line.productId(), line.unitPrice(), line.quantity(), sale.getTotalAmount(), now));
        }
        cartItemRepository.deleteByCartId(cartId);
        return sales;
    }

    private Cart createCart(Long customerId) {
        Cart cart = new Cart();
        Customer customer = new Customer();
        customer.setId(customerId);
        cart.setCustomer(customer);
        cart.setCartItems(new ArrayList<>());
        return cartRepository.save(cart);
    }

    private Optional<CartItem> findItem(Cart cart, Long productId) {
        return cart.getCartItems().stream()
                .filter(i -> i.getProduct().getId().equals(productId))
                .findFirst();
    }

    private List<CartItemView> toViews(Cart cart) {
        List<Long> productIds = cart.getCartItems().stream()
                .map(i -> i.getProduct().getId())
                .toList();
        Map<Long, Integer> available = inventoryService.availableQuantities(productIds);
        return cart.getCartItems().stream()
                .map(i -> {
                    Product p = i.getProduct();
                    return new CartItemView(p.getId(), p.getName(), p.getImageUrl(), p.getPrice(), i.getQuantity(),
                            available.getOrDefault(p.getId(), 0));
                })
                .toList();
    }
}
