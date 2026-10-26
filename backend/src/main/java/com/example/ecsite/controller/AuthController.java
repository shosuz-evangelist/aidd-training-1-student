package com.example.ecsite.controller;

import com.example.ecsite.entity.Customer;
import com.example.ecsite.service.AuthService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:5173"})
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> request) {
        String email = request.get("email");
        String password = request.get("password");
        Customer customer = authService.authenticate(email, password);
        if (customer != null) {
            Map<String, Object> result = new HashMap<>();
            result.put("id", customer.getId());
            result.put("name", customer.getName());
            result.put("email", customer.getEmail());
            result.put("phone", customer.getPhone());
            return ResponseEntity.ok(result);
        } else {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Invalid email or password");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
        }
    }
}
