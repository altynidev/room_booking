package com.example.roombooking.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI metadata. Swagger UI is served at /swagger-ui.html; use "Authorize" with a token
 * from POST /api/auth/login. Register and login ignore the token.
 */
@Configuration
@OpenAPIDefinition(
        info = @Info(title = "Meeting Room Booking API", version = "v1",
                description = "Book meeting rooms; overlapping ACTIVE bookings are rejected with 409."),
        security = @SecurityRequirement(name = "bearerAuth"))
@SecurityScheme(name = "bearerAuth", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
public class OpenApiConfig {
}
