package com.example.booking.web;

import com.example.booking.dto.BookingDto;
import com.example.booking.dto.CreateBookingRequest;
import com.example.booking.service.BookingService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    private final BookingService service;

    public BookingController(BookingService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BookingDto create(@Valid @RequestBody CreateBookingRequest request) {
        return service.create(request);
    }

    @GetMapping
    public List<BookingDto> byEmail(@RequestParam("email") String email) {
        return service.findByEmail(email);
    }

    @GetMapping("/{id}")
    public BookingDto get(@PathVariable("id") Long id) {
        return service.get(id);
    }

    @DeleteMapping("/{id}")
    public BookingDto cancel(@PathVariable("id") Long id, @RequestParam("email") String email) {
        return service.cancel(id, email);
    }
}
