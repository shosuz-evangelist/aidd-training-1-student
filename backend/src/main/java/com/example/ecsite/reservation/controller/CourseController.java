package com.example.ecsite.reservation.controller;

import com.example.ecsite.reservation.dto.CourseRequest;
import com.example.ecsite.reservation.dto.CourseView;
import com.example.ecsite.reservation.service.CourseService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/reservation")
public class CourseController {
    private final CourseService service;

    public CourseController(CourseService service) {
        this.service = service;
    }

    @GetMapping("/courses")
    public List<CourseView> list() {
        return service.list();
    }

    @PostMapping("/courses")
    @ResponseStatus(HttpStatus.CREATED)
    public CourseView register(@RequestBody CourseRequest request) {
        return service.register(request);
    }
}
