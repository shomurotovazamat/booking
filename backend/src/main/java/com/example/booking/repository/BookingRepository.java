package com.example.booking.repository;

import com.example.booking.model.Booking;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    /** Подтверждённые бронирования ресурса, пересекающиеся с интервалом. */
    @Query("""
            select b from Booking b
            where b.resource.id = :resourceId
              and b.status = 'CONFIRMED'
              and b.startTime < :intervalEnd
              and b.endTime > :intervalStart
            """)
    List<Booking> findOverlapping(@Param("resourceId") Long resourceId,
                                  @Param("intervalStart") LocalDateTime intervalStart,
                                  @Param("intervalEnd") LocalDateTime intervalEnd);

    @Query("select b from Booking b join fetch b.resource "
            + "where b.customerEmail = :email order by b.startTime desc")
    List<Booking> findByEmail(@Param("email") String email);
}
