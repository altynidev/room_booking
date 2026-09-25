package com.example.roombooking.dto.room;

import java.time.OffsetDateTime;

public record TimeSlot(OffsetDateTime start, OffsetDateTime end) {
}
