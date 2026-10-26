
package com.example.ecsite.controller;


import com.example.ecsite.entity.Customer;
import com.example.ecsite.entity.Product;
import com.example.ecsite.repository.CustomerRepository;
import com.example.ecsite.repository.ProductRepository;
import java.util.stream.Collectors;
import com.example.ecsite.entity.Sale;
import com.example.ecsite.service.SaleService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.List;

import com.example.ecsite.entity.Sale;
import com.example.ecsite.service.SaleService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/sales")
public class SaleController {
    private final SaleService saleService;
    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;

    @Autowired
    public SaleController(SaleService saleService, CustomerRepository customerRepository, ProductRepository productRepository) {
        this.saleService = saleService;
        this.customerRepository = customerRepository;
        this.productRepository = productRepository;
    }

    @GetMapping("")
    public List<SaleDto> getAll() {
        return saleService.findAll().stream().map(sale -> {
            Customer customer = customerRepository.findById(sale.getCustomerId()).orElse(null);
            Product product = productRepository.findById(sale.getProductId()).orElse(null);
            return new SaleDto(
                sale.getId(),
                customer != null ? customer.getName() : "",
                product != null ? product.getName() : "",
                sale.getUnitPrice(),
                sale.getQuantity(),
                sale.getTotalAmount(),
                sale.getSaleDate() != null ? sale.getSaleDate().toString() : ""
            );
        }).collect(Collectors.toList());
    }

    public static class SaleDto {
        public Long id;
        public String customerName;
        public String productName;
        public long unitPrice;
        public int quantity;
        public long total;
        public String date;

        public SaleDto(Long id, String customerName, String productName, long unitPrice, int quantity, long total, String date) {
            this.unitPrice = unitPrice;
            this.id = id;
            this.customerName = customerName;
            this.productName = productName;
            this.quantity = quantity;
            this.total = total;
            this.date = date;
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<Sale> getById(@PathVariable Long id) {
        return saleService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("")
    public ResponseEntity<Sale> create(@RequestBody Sale sale) {
        Sale created = saleService.save(sale);
        return ResponseEntity.status(201).body(created);
    }
}
