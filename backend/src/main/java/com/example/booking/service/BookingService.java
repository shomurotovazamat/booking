package com.example.booking.service;

import com.example.booking.dto.AvailabilityDto;
import com.example.booking.dto.BookingDto;
import com.example.booking.dto.CreateBookingRequest;
import com.example.booking.model.Booking;
import com.example.booking.model.Resource;
import com.example.booking.repository.BookingRepository;
import com.example.booking.repository.ResourceRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class BookingService {

    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    private final ResourceRepository resources;
    private final BookingRepository bookings;
    private final ZoneId zone;
    private final int openHour;
    private final int closeHour;

    // Бизнес-метрики, которые забирает Prometheus (/actuator/prometheus)
    private final Counter createdCounter;
    private final Counter cancelledCounter;
    private final Counter conflictCounter;

    public BookingService(ResourceRepository resources,
                          BookingRepository bookings,
                          MeterRegistry registry,
                          @Value("${app.timezone:Europe/Moscow}") String timezone,
                          @Value("${app.open-hour:9}") int openHour,
                          @Value("${app.close-hour:21}") int closeHour) {
        this.resources = resources;
        this.bookings = bookings;
        this.zone = ZoneId.of(timezone);
        this.openHour = openHour;
        this.closeHour = closeHour;
        this.createdCounter = Counter.builder("bookings.confirmed")
                .description("Количество созданных бронирований").register(registry);
        this.cancelledCounter = Counter.builder("bookings.cancelled")
                .description("Количество отменённых бронирований").register(registry);
        this.conflictCounter = Counter.builder("bookings.conflicts")
                .description("Отказы из-за занятого времени").register(registry);
    }

    @Transactional
    public BookingDto create(CreateBookingRequest request) {
        validateInterval(request.startTime(), request.endTime());

        Resource resource = resources.findByIdForUpdate(request.resourceId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ресурс не найден"));

        boolean busy = !bookings.findOverlapping(
                resource.getId(), request.startTime(), request.endTime()).isEmpty();
        if (busy) {
            conflictCounter.increment();
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Это время уже занято");
        }

        Booking booking = new Booking();
        booking.setResource(resource);
        booking.setCustomerName(request.customerName().trim());
        booking.setCustomerEmail(request.customerEmail().trim().toLowerCase());
        booking.setStartTime(request.startTime());
        booking.setEndTime(request.endTime());
        bookings.save(booking);

        createdCounter.increment();
        return BookingDto.from(booking);
    }

    @Transactional(readOnly = true)
    public AvailabilityDto availability(Long resourceId, LocalDate date) {
        if (!resources.existsById(resourceId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Ресурс не найден");
        }
        LocalDateTime dayStart = date.atTime(openHour, 0);
        LocalDateTime dayEnd = date.atTime(closeHour, 0);
        List<Booking> busy = bookings.findOverlapping(resourceId, dayStart, dayEnd);
        LocalDateTime now = LocalDateTime.now(zone);

        List<AvailabilityDto.Slot> slots = new ArrayList<>();
        for (LocalDateTime cursor = dayStart; cursor.isBefore(dayEnd); cursor = cursor.plusHours(1)) {
            LocalDateTime slotStart = cursor;
            LocalDateTime slotEnd = cursor.plusHours(1);
            boolean taken = busy.stream().anyMatch(b ->
                    b.getStartTime().isBefore(slotEnd) && b.getEndTime().isAfter(slotStart));
            boolean free = !taken && slotStart.isAfter(now);
            slots.add(new AvailabilityDto.Slot(slotStart.format(HH_MM), slotEnd.format(HH_MM), free));
        }
        return new AvailabilityDto(resourceId, date, slots);
    }

    @Transactional(readOnly = true)
    public List<BookingDto> findByEmail(String email) {
        return bookings.findByEmail(email.trim().toLowerCase()).stream()
                .map(BookingDto::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public BookingDto get(Long id) {
        return BookingDto.from(find(id));
    }

    @Transactional
    public BookingDto cancel(Long id, String email) {
        Booking booking = find(id);
        if (!booking.getCustomerEmail().equalsIgnoreCase(email.trim())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Отменить бронирование может только его владелец");
        }
        if (!Booking.CANCELLED.equals(booking.getStatus())) {
            booking.setStatus(Booking.CANCELLED);
            cancelledCounter.increment();
        }
        return BookingDto.from(booking);
    }

    private Booking find(Long id) {
        return bookings.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Бронирование не найдено"));
    }

    private void validateInterval(LocalDateTime start, LocalDateTime end) {
        if (!end.isAfter(start)) {
            throw badRequest("Время окончания должно быть позже времени начала");
        }
        if (!start.toLocalDate().equals(end.toLocalDate())) {
            throw badRequest("Бронирование должно укладываться в один день");
        }
        if (!isWholeHour(start) || !isWholeHour(end)) {
            throw badRequest("Бронирование возможно только целыми часами");
        }
        if (start.getHour() < openHour || end.getHour() > closeHour) {
            throw badRequest("Рабочие часы: " + openHour + ":00–" + closeHour + ":00");
        }
        if (!start.isAfter(LocalDateTime.now(zone))) {
            throw badRequest("Нельзя бронировать время в прошлом");
        }
    }

    private static boolean isWholeHour(LocalDateTime t) {
        return t.getMinute() == 0 && t.getSecond() == 0 && t.getNano() == 0;
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
