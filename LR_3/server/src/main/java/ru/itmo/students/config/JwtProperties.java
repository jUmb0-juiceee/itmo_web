package ru.itmo.students.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import java.time.Duration;

@Validated
@ConfigurationProperties("app.jwt")
public record JwtProperties(@NotBlank String secret, @NotBlank String issuer,
                            @NotBlank String audience, Duration accessTtl, Duration refreshTtl) {
    public JwtProperties {
        if (accessTtl == null || refreshTtl == null || accessTtl.isNegative() || accessTtl.isZero()
                || accessTtl.getNano() != 0 || refreshTtl.getNano() != 0 || refreshTtl.compareTo(accessTtl) <= 0) {
            throw new IllegalArgumentException("JWT lifetimes must be whole seconds, positive, with refresh longer than access");
        }
    }
    public String refreshAudience() { return audience + "-refresh"; }
    @Override public String toString() { return "JwtProperties[secret=<redacted>]"; }
}
