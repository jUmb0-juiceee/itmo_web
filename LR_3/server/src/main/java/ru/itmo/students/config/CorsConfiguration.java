package ru.itmo.students.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.*;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.cors.DefaultCorsProcessor;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import ru.itmo.students.dto.ApiError;
import java.io.IOException;
import java.util.List;
import java.util.Map;

@Configuration
@EnableConfigurationProperties(CorsProperties.class)
public class CorsConfiguration {
    @Bean
    public UrlBasedCorsConfigurationSource corsConfigurationSource(CorsProperties properties) {
        var cors = new org.springframework.web.cors.CorsConfiguration();
        cors.setAllowedOrigins(properties.allowedOrigins());
        cors.setAllowedMethods(List.of("GET", "HEAD", "POST", "PUT", "PATCH", "DELETE", "OPTIONS", "QUERY"));
        cors.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept"));
        cors.setExposedHeaders(List.of("Retry-After", "Location"));
        cors.setAllowCredentials(false); // The static client uses explicit bearer tokens, not cookies.
        cors.setMaxAge(3600L);
        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", cors);
        return source;
    }

    @Bean
    public CorsFilter corsFilter(UrlBasedCorsConfigurationSource source, ObjectMapper json) {
        var filter = new CorsFilter(source);
        filter.setCorsProcessor(new DefaultCorsProcessor() {
            @Override
            protected void rejectRequest(ServerHttpResponse response) throws IOException {
                response.setStatusCode(HttpStatus.FORBIDDEN);
                response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
                json.writeValue(response.getBody(), new ApiError("Источник запроса не разрешён", Map.of()));
                response.flush();
            }
        });
        return filter;
    }

    @Bean
    public FilterRegistrationBean<CorsFilter> corsFilterRegistration(CorsFilter filter) {
        var registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false); // Spring Security installs this filter in its own chain.
        return registration;
    }
}
