package com.example.roombooking.security;

import com.example.roombooking.entity.Role;
import com.example.roombooking.entity.User;
import com.example.roombooking.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Replaces the publicly known seed password ("admin123") when ADMIN_PASSWORD is set.
 * Runs on every start, so rotating the password is just changing the variable and restarting.
 * Without the variable (local development, tests) the seed account is left untouched.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminAccountInitializer implements ApplicationRunner {

    static final int MIN_LENGTH = 12;
    private static final String SEED_PASSWORD = "admin123";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.username:admin}")
    private String username;

    @Value("${app.admin.password:}")
    private String password;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (password.isBlank()) {
            log.warn("ADMIN_PASSWORD is not set: account '{}' keeps the default seed password. "
                    + "Never expose this instance publicly.", username);
            return;
        }
        if (password.length() < MIN_LENGTH || password.equals(SEED_PASSWORD)) {
            throw new IllegalStateException(
                    "ADMIN_PASSWORD must be at least %d characters and not the seed password".formatted(MIN_LENGTH));
        }

        User admin = userRepository.findByUsername(username).orElse(null);
        if (admin == null) {
            userRepository.save(User.builder()
                    .username(username)
                    .password(passwordEncoder.encode(password))
                    .role(Role.ADMIN)
                    .build());
            log.info("Created administrator account '{}'", username);
        } else if (admin.getRole() != Role.ADMIN || !passwordEncoder.matches(password, admin.getPassword())) {
            admin.setPassword(passwordEncoder.encode(password));
            admin.setRole(Role.ADMIN);
            log.info("Updated password of administrator account '{}' from ADMIN_PASSWORD", username);
        }
    }
}
