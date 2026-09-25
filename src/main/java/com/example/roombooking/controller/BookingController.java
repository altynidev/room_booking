package com.example.roombooking.controller;

import com.example.roombooking.dto.booking.BookingRequest;
import com.example.roombooking.dto.booking.BookingResponse;
import com.example.roombooking.security.UserPrincipal;
import com.example.roombooking.service.BookingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BookingResponse create(@Valid @RequestBody BookingRequest request,
                                  @AuthenticationPrincipal UserPrincipal principal) {
        return bookingService.create(request, principal);
    }

    @GetMapping("/my")
    public List<BookingResponse> findMine(@AuthenticationPrincipal UserPrincipal principal) {
        return bookingService.findMine(principal);
    }

    @PatchMapping("/{id}/cancel")
    public BookingResponse cancel(@PathVariable Long id,
                                  @AuthenticationPrincipal UserPrincipal principal) {
        return bookingService.cancel(id, principal);
    }
}
