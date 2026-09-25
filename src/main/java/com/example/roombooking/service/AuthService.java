package com.example.roombooking.service;

import com.example.roombooking.dto.auth.AuthResponse;
import com.example.roombooking.dto.auth.LoginRequest;
import com.example.roombooking.dto.auth.RegisterRequest;
import com.example.roombooking.dto.auth.UserResponse;
import com.example.roombooking.entity.Role;
import com.example.roombooking.entity.User;
import com.example.roombooking.exception.ConflictException;
import com.example.roombooking.repository.UserRepository;
import com.example.roombooking.security.JwtService;
import com.example.roombooking.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    @Transactional
    public UserResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new ConflictException("Username '%s' is already taken".formatted(request.username()));
        }
        User user = User.builder()
                .username(request.username())
                .password(passwordEncoder.encode(request.password()))
                .role(Role.USER)
                .build();
        return UserResponse.from(userRepository.save(user));
    }

    public AuthResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password()));
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        String token = jwtService.generateToken(principal);
        return AuthResponse.bearer(token, jwtService.getExpirationMs() / 1000);
    }
}
