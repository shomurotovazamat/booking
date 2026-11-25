package com.example.booking.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateResourceRequest(
        @NotBlank @Size(max = 120) String name,
        @Size(max = 500) String description,
        @Min(1) int capacity,
        @Min(0) int pricePerHour) {
}
