package com.example.ecsite.reservation.controller;

import com.example.ecsite.reservation.dto.CancelRequest;
import com.example.ecsite.reservation.dto.CancelResult;
import com.example.ecsite.reservation.dto.ReservationRequest;
import com.example.ecsite.reservation.dto.ReservationView;
import com.example.ecsite.reservation.service.ReservationService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import java.util.List;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reservation")
public class ReservationController {
    private final ReservationService service;

    public ReservationController(ReservationService service) {
        this.service = service;
    }

    @PostMapping("/reservations")
    @ResponseStatus(HttpStatus.CREATED)
    public ReservationView reserve(@RequestBody ReservationRequest request) {
        return service.reserve(request.customerId(), request.courseId());
    }

    @PostMapping("/reservations/{id}/cancel")
    public CancelResult cancel(@PathVariable long id, @RequestBody CancelRequest request) {
        return service.cancel(request.customerId(), id);
    }

    @GetMapping("/reservations")
    public List<ReservationView> list(@RequestParam long customerId) {
        return service.list(customerId);
    }
}
