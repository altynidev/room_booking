package com.example.roombooking.dto.room;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RoomRequest(
        @NotBlank @Size(max = 100) String name,
        @NotNull @Min(1) @Max(10000) Integer capacity,
        @NotNull @Min(-10) @Max(300) Integer floor
) {
}
