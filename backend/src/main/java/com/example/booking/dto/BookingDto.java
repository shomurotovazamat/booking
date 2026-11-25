package com.example.booking.dto;

import com.example.booking.model.Booking;
import java.time.LocalDateTime;

public record BookingDto(Long id, Long resourceId, String resourceName, String customerName,
                         String customerEmail, LocalDateTime startTime, LocalDateTime endTime,
                         String status) {

    /** Вызывать внутри транзакции: читает лениво загружаемый ресурс. */
    public static BookingDto from(Booking b) {
        return new BookingDto(b.getId(), b.getResource().getId(), b.getResource().getName(),
                b.getCustomerName(), b.getCustomerEmail(), b.getStartTime(), b.getEndTime(),
                b.getStatus());
    }
}
