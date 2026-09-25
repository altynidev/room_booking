package com.example.roombooking.repository;

import com.example.roombooking.entity.Booking;
import com.example.roombooking.entity.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    /**
     * True if the room has an ACTIVE booking overlapping [start, end).
     * Touching intervals (existing.end == start) do not count as overlap.
     */
    @Query("""
            select count(b) > 0 from Booking b
            where b.room.id = :roomId
              and b.status = com.example.roombooking.entity.BookingStatus.ACTIVE
              and b.startTime < :end
              and b.endTime > :start
            """)
    boolean existsActiveOverlap(@Param("roomId") Long roomId,
                                @Param("start") OffsetDateTime start,
                                @Param("end") OffsetDateTime end);

    @Query("""
            select b from Booking b
            where b.room.id = :roomId
              and b.status = :status
              and b.startTime < :to
              and b.endTime > :from
            order by b.startTime
            """)
    List<Booking> findByRoomInRange(@Param("roomId") Long roomId,
                                    @Param("status") BookingStatus status,
                                    @Param("from") OffsetDateTime from,
                                    @Param("to") OffsetDateTime to);

    @Query("""
            select b from Booking b
            join fetch b.room
            join fetch b.user
            where b.user.id = :userId
            order by b.startTime
            """)
    List<Booking> findAllByUserId(@Param("userId") Long userId);

    boolean existsByRoomId(Long roomId);
}
