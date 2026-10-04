package com.example.seatreservation.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String SECURITY_SCHEME_NAME = "X-User-Id";

    @Bean
    public OpenAPI seatReservationOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Seat Reservation API")
                        .description("High-throughput, concurrent seat reservation system with idempotency and advisory locks.")
                        .version("1.0.0")
                        .contact(new Contact().name("Backend Engineering Team")))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME))
                .components(new Components()
                        .addSecuritySchemes(SECURITY_SCHEME_NAME,
                                new SecurityScheme()
                                        .name("X-User-Id")
                                        .type(SecurityScheme.Type.APIKEY)
                                        .in(SecurityScheme.In.HEADER)
                                        .description("User ID header for authenticating user requests (e.g. user-123)")));
    }
}
