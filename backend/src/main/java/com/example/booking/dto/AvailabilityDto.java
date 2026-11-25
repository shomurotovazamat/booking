package com.example.booking.dto;

import java.time.LocalDate;
import java.util.List;

public record AvailabilityDto(Long resourceId, LocalDate date, List<Slot> slots) {

    /** Часовой слот: время начала/окончания (ЧЧ:ММ) и признак «свободен». */
    public record Slot(String start, String end, boolean free) {
    }
}
