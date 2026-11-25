package com.example.booking.dto;

import com.example.booking.model.Resource;

public record ResourceDto(Long id, String name, String description, int capacity, int pricePerHour) {

    public static ResourceDto from(Resource r) {
        return new ResourceDto(r.getId(), r.getName(), r.getDescription(),
                r.getCapacity(), r.getPricePerHour());
    }
}
