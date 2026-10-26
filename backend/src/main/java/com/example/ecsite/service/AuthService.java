package com.example.ecsite.service;

import com.example.ecsite.entity.Customer;
import com.example.ecsite.repository.CustomerRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class AuthService {
    private final CustomerRepository customerRepository;

    public AuthService(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    public Customer authenticate(String email, String password) {
        Optional<Customer> customerOpt = customerRepository.findAll().stream()
                .filter(c -> c.getEmail().equals(email) && c.getPassword().equals(password))
                .findFirst();
        return customerOpt.orElse(null);
    }
}
