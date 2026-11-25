package com.example.booking.web;

import com.example.booking.dto.AvailabilityDto;
import com.example.booking.dto.CreateResourceRequest;
import com.example.booking.dto.ResourceDto;
import com.example.booking.model.Resource;
import com.example.booking.repository.ResourceRepository;
import com.example.booking.service.BookingService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/resources")
public class ResourceController {

    private final ResourceRepository resources;
    private final BookingService bookingService;
    private final String adminToken;

    public ResourceController(ResourceRepository resources,
                              BookingService bookingService,
                              @Value("${app.admin-token:change-me}") String adminToken) {
        this.resources = resources;
        this.bookingService = bookingService;
        this.adminToken = adminToken;
    }

    @GetMapping
    public List<ResourceDto> list() {
        return resources.findAll(Sort.by("id")).stream().map(ResourceDto::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ResourceDto create(@RequestHeader(name = "X-Admin-Token", defaultValue = "") String token,
                              @Valid @RequestBody CreateResourceRequest request) {
        if (!adminToken.equals(token)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Неверный токен администратора");
        }
        if (resources.existsByName(request.name())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ресурс с таким названием уже существует");
        }
        String description = request.description() == null ? "" : request.description();
        Resource saved = resources.save(
                new Resource(request.name(), description, request.capacity(), request.pricePerHour()));
        return ResourceDto.from(saved);
    }

    @GetMapping("/{id}/availability")
    public AvailabilityDto availability(
            @PathVariable("id") Long id,
            @RequestParam("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return bookingService.availability(id, date);
    }
}
