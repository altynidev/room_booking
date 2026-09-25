package com.example.roombooking.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param secret       Base64-encoded HMAC key, at least 256 bits
 * @param expirationMs token lifetime in milliseconds
 */
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(String secret, long expirationMs) {
}
