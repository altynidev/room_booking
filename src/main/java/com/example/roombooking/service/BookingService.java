package com.example.roombooking.service;

import com.example.roombooking.dto.booking.BookingRequest;
import com.example.roombooking.dto.booking.BookingResponse;
import com.example.roombooking.entity.Booking;
import com.example.roombooking.entity.BookingStatus;
import com.example.roombooking.entity.Room;
import com.example.roombooking.entity.User;
import com.example.roombooking.exception.BadRequestException;
import com.example.roombooking.exception.ConflictException;
import com.example.roombooking.exception.NotFoundException;
import com.example.roombooking.repository.BookingRepository;
import com.example.roombooking.repository.UserRepository;
import com.example.roombooking.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BookingService {

    private static final String OVERLAP_MESSAGE = "Room is already booked for the requested time";

    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;
    private final RoomService roomService;

    @Transactional
    public BookingResponse create(BookingRequest request, UserPrincipal principal) {
        OffsetDateTime start = request.startTime();
        OffsetDateTime end = request.endTime();
        if (!start.isBefore(end)) {
            throw new BadRequestException("startTime must be before endTime");
        }
        if (!start.isAfter(OffsetDateTime.now())) {
            throw new BadRequestException("startTime must be in the future");
        }

        Room room = roomService.getRoom(request.roomId());
        if (bookingRepository.existsActiveOverlap(room.getId(), start, end)) {
            throw new ConflictException(OVERLAP_MESSAGE);
        }

        User user = userRepository.getReferenceById(principal.getId());
        Booking booking = Booking.builder()
                .room(room)
                .user(user)
                .startTime(start)
                .endTime(end)
                .status(BookingStatus.ACTIVE)
                .build();
        try {
            booking = bookingRepository.saveAndFlush(booking);
        } catch (DataIntegrityViolationException e) {
            // A concurrent request won the race; the DB exclusion constraint rejected this one.
            throw new ConflictException(OVERLAP_MESSAGE);
        }
        return BookingResponse.from(booking);
    }

    @Transactional(readOnly = true)
    public List<BookingResponse> findMine(UserPrincipal principal) {
        return bookingRepository.findAllByUserId(principal.getId()).stream()
                .map(BookingResponse::from)
                .toList();
    }

    @Transactional
    public BookingResponse cancel(Long bookingId, UserPrincipal principal) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new NotFoundException("Booking %d not found".formatted(bookingId)));

        boolean isOwner = booking.getUser().getId().equals(principal.getId());
        if (!isOwner && !principal.isAdmin()) {
            throw new AccessDeniedException("You can only cancel your own bookings");
        }
        if (booking.getStatus() == BookingStatus.CANCELLED) {
            throw new ConflictException("Booking %d is already cancelled".formatted(bookingId));
        }
        booking.setStatus(BookingStatus.CANCELLED);
        return BookingResponse.from(booking);
    }
}
