package com.example.ecsite.service;

import com.example.ecsite.dto.ProductView;
import com.example.ecsite.entity.Product;
import com.example.ecsite.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class ProductService {
    private final ProductRepository productRepository;

    private final InventoryService inventoryService;

    public ProductService(ProductRepository productRepository, InventoryService inventoryService) {
        this.productRepository = productRepository;
        this.inventoryService = inventoryService;
    }

    @Transactional(readOnly = true)
    public List<ProductView> getAllProducts() {
        List<Product> products = productRepository.findAll();
        List<Long> ids = products.stream().map(Product::getId).toList();
        Map<Long, Integer> quantities = inventoryService.availableQuantities(ids);
        return products.stream()
                .map(p -> toView(p, quantities.getOrDefault(p.getId(), 0)))
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<ProductView> getProductById(Long id) {
        return productRepository.findById(id)
                .map(p -> toView(p, inventoryService.availableQuantity(p.getId())));
    }

    @Transactional
    public Product createProduct(Product product) {
        return productRepository.save(product);
    }

    private ProductView toView(Product p, int availableQuantity) {
        return new ProductView(p.getId(), p.getName(), p.getPrice(), p.getImageUrl(), availableQuantity);
    }
}
