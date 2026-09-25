package com.example.roombooking.dto.room;

import java.time.LocalDate;
import java.util.List;

/**
 * Availability of a room for one calendar day (UTC).
 *
 * @param bookedSlots ACTIVE bookings intersecting the day, clipped to the day's bounds
 * @param freeSlots   gaps between booked slots within the day
 */
public record AvailabilityResponse(
        Long roomId,
        String roomName,
        LocalDate date,
        List<TimeSlot> bookedSlots,
        List<TimeSlot> freeSlots
) {
}
