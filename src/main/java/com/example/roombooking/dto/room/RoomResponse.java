package com.example.roombooking.dto.room;

import com.example.roombooking.entity.Room;

public record RoomResponse(Long id, String name, Integer capacity, Integer floor) {

    public static RoomResponse from(Room room) {
        return new RoomResponse(room.getId(), room.getName(), room.getCapacity(), room.getFloor());
    }
}
