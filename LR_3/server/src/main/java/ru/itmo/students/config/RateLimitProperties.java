package ru.itmo.students.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import java.time.Duration;

@Validated
@ConfigurationProperties("app.rate-limit")
public record RateLimitProperties(@Min(1) int maxRequests, @NotNull Duration window) {
    public RateLimitProperties {
        if (window != null && (window.isNegative() || window.isZero() || window.compareTo(Duration.ofDays(1)) > 0)) {
            throw new IllegalArgumentException("Rate limit window must be positive and at most one day");
        }
    }
}
