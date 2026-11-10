package ru.itmo.students.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import ru.itmo.students.web.*;

@Configuration
@EnableConfigurationProperties(RateLimitProperties.class)
public class RateLimitConfiguration {
    @Bean
    public IpRateLimiter ipRateLimiter(RateLimitProperties properties) {
        return new IpRateLimiter(properties.maxRequests(), properties.window(), System::nanoTime);
    }

    @Bean
    public FilterRegistrationBean<RateLimitFilter> rateLimitFilter(IpRateLimiter limiter, ApiErrorWriter errors) {
        var registration = new FilterRegistrationBean<>(new RateLimitFilter(limiter, errors));
        registration.addUrlPatterns("/api/*");
        registration.setOrder(-90); // After the Spring Security filter (-100).
        return registration;
    }
}
