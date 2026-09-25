package com.example.roombooking.dto.booking;

import com.example.roombooking.entity.Booking;
import com.example.roombooking.entity.BookingStatus;

import java.time.OffsetDateTime;

public record BookingResponse(
        Long id,
        Long roomId,
        String roomName,
        Long userId,
        String username,
        OffsetDateTime startTime,
        OffsetDateTime endTime,
        BookingStatus status
) {

    public static BookingResponse from(Booking booking) {
        return new BookingResponse(
                booking.getId(),
                booking.getRoom().getId(),
                booking.getRoom().getName(),
                booking.getUser().getId(),
                booking.getUser().getUsername(),
                booking.getStartTime(),
                booking.getEndTime(),
                booking.getStatus());
    }
}
