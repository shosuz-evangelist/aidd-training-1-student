package com.example.ecsite.service;

import com.example.ecsite.entity.Customer;
import com.example.ecsite.repository.CustomerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class CustomerService {
    private final CustomerRepository customerRepository;

    public CustomerService(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    public List<Customer> findAll() {
        return customerRepository.findAll();
    }

    public Optional<Customer> findById(Long id) {
        return customerRepository.findById(id);
    }

    @Transactional
    public Customer save(Customer customer) {
        return customerRepository.save(customer);
    }

    @Transactional
    public Optional<Customer> update(Long id, Customer updated) {
        return customerRepository.findById(id).map(customer -> {
            customer.setName(updated.getName());
            customer.setEmail(updated.getEmail());
            customer.setPhone(updated.getPhone());
            return customerRepository.save(customer);
        });
    }
}
