package com.example.roombooking.service;

import com.example.roombooking.dto.room.AvailabilityResponse;
import com.example.roombooking.dto.room.RoomRequest;
import com.example.roombooking.dto.room.RoomResponse;
import com.example.roombooking.dto.room.TimeSlot;
import com.example.roombooking.entity.Booking;
import com.example.roombooking.entity.BookingStatus;
import com.example.roombooking.entity.Room;
import com.example.roombooking.exception.ConflictException;
import com.example.roombooking.exception.NotFoundException;
import com.example.roombooking.repository.BookingRepository;
import com.example.roombooking.repository.RoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RoomService {

    private final RoomRepository roomRepository;
    private final BookingRepository bookingRepository;

    @Transactional(readOnly = true)
    public List<RoomResponse> findAll() {
        return roomRepository.findAll(Sort.by("name")).stream()
                .map(RoomResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public RoomResponse findById(Long id) {
        return RoomResponse.from(getRoom(id));
    }

    @Transactional
    public RoomResponse create(RoomRequest request) {
        if (roomRepository.existsByName(request.name())) {
            throw new ConflictException("Room with name '%s' already exists".formatted(request.name()));
        }
        Room room = Room.builder()
                .name(request.name())
                .capacity(request.capacity())
                .floor(request.floor())
                .build();
        return RoomResponse.from(roomRepository.save(room));
    }

    @Transactional
    public RoomResponse update(Long id, RoomRequest request) {
        Room room = getRoom(id);
        if (roomRepository.existsByNameAndIdNot(request.name(), id)) {
            throw new ConflictException("Room with name '%s' already exists".formatted(request.name()));
        }
        room.setName(request.name());
        room.setCapacity(request.capacity());
        room.setFloor(request.floor());
        return RoomResponse.from(room);
    }

    @Transactional
    public void delete(Long id) {
        Room room = getRoom(id);
        if (bookingRepository.existsByRoomId(id)) {
            throw new ConflictException("Room %d has bookings and cannot be deleted".formatted(id));
        }
        roomRepository.delete(room);
    }

    @Transactional(readOnly = true)
    public AvailabilityResponse availability(Long id, LocalDate date) {
        Room room = getRoom(id);
        OffsetDateTime dayStart = date.atStartOfDay().atOffset(ZoneOffset.UTC);
        OffsetDateTime dayEnd = dayStart.plusDays(1);

        List<Booking> bookings = bookingRepository.findByRoomInRange(
                id, BookingStatus.ACTIVE, dayStart, dayEnd);

        List<TimeSlot> booked = new ArrayList<>();
        List<TimeSlot> free = new ArrayList<>();
        OffsetDateTime cursor = dayStart;
        for (Booking b : bookings) {
            OffsetDateTime start = max(b.getStartTime(), dayStart);
            OffsetDateTime end = min(b.getEndTime(), dayEnd);
            if (start.isAfter(cursor)) {
                free.add(new TimeSlot(cursor, start));
            }
            booked.add(new TimeSlot(start, end));
            cursor = max(cursor, end);
        }
        if (cursor.isBefore(dayEnd)) {
            free.add(new TimeSlot(cursor, dayEnd));
        }
        return new AvailabilityResponse(room.getId(), room.getName(), date, booked, free);
    }

    Room getRoom(Long id) {
        return roomRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Room %d not found".formatted(id)));
    }

    private static OffsetDateTime max(OffsetDateTime a, OffsetDateTime b) {
        return a.isAfter(b) ? a : b;
    }

    private static OffsetDateTime min(OffsetDateTime a, OffsetDateTime b) {
        return a.isBefore(b) ? a : b;
    }
}
