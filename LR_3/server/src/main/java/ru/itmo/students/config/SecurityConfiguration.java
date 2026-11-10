package ru.itmo.students.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.web.firewall.StrictHttpFirewall;
import ru.itmo.students.dto.ApiError;
import ru.itmo.students.web.ApiErrorWriter;
import ru.itmo.students.security.DatabaseJwtAuthenticationConverter;

import java.util.List;
import java.util.Map;

/** Stateless access JWT authentication plus role checks on routes and service methods. */
@Configuration
@EnableMethodSecurity
public class SecurityConfiguration {
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, ApiErrorWriter errors,
            @Qualifier("accessJwtDecoder") JwtDecoder decoder, DatabaseJwtAuthenticationConverter converter) throws Exception {
        String[] students = {"/api/requests", "/api/requests/*"};
        return http
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers(HttpMethod.GET, "/actuator/health/readiness").permitAll()
                        .requestMatchers(HttpMethod.GET, "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs", "/v3/api-docs.yaml", "/v3/api-docs/**").permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/api/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login", "/api/auth/refresh", "/api/auth/logout").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/auth/me").authenticated()
                        .requestMatchers(HttpMethod.GET, students).hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.HEAD, students).hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.valueOf("QUERY"), "/api/requests").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(students).hasRole("ADMIN")
                        .anyRequest().denyAll())
                .oauth2ResourceServer(resource -> resource
                        .jwt(jwt -> jwt.decoder(decoder).jwtAuthenticationConverter(converter))
                        .authenticationEntryPoint((request, response, exception) -> {
                            response.setHeader("WWW-Authenticate", "Bearer");
                            errors.write(response, 401, new ApiError("Access-токен недействителен или истёк", Map.of()));
                        })
                        .accessDeniedHandler((request, response, exception) ->
                                errors.write(response, 403, new ApiError("Недостаточно прав", Map.of()))))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) ->
                                errors.write(response, 401, new ApiError("Требуется авторизация", Map.of())))
                        .accessDeniedHandler((request, response, exception) ->
                                errors.write(response, 403, new ApiError("Недостаточно прав", Map.of()))))
                .build();
    }

    @Bean
    public WebSecurityCustomizer firewall(ApiErrorWriter errors) {
        var firewall = new StrictHttpFirewall();
        firewall.setAllowedHttpMethods(List.of("GET", "HEAD", "POST", "PUT", "PATCH", "DELETE", "OPTIONS", "QUERY"));
        return web -> web.httpFirewall(firewall).requestRejectedHandler((request, response, exception) ->
                errors.write(response, 400, new ApiError("Некорректный HTTP-запрос", Map.of())));
    }
}
