package com.example.roombooking.dto.auth;

import com.example.roombooking.entity.Role;
import com.example.roombooking.entity.User;

public record UserResponse(Long id, String username, Role role) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getUsername(), user.getRole());
    }
}
