package ru.itmo.students.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;
import ru.itmo.students.dto.ApiError;
import java.io.IOException;
import java.util.Map;

public class RateLimitFilter extends OncePerRequestFilter {
    private final IpRateLimiter limiter;
    private final ApiErrorWriter errors;

    public RateLimitFilter(IpRateLimiter limiter, ApiErrorWriter errors) {
        this.limiter = limiter;
        this.errors = errors;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) { return request.getMethod().equals("OPTIONS"); }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        // X-Forwarded-For is deliberately not trusted.
        long retryAfter = limiter.check(request.getRemoteAddr());
        if (retryAfter > 0) {
            response.setHeader("Retry-After", Long.toString(retryAfter));
            errors.write(response, 429, new ApiError("Превышен лимит запросов", Map.of("retry_after", retryAfter)));
            return;
        }
        chain.doFilter(request, response);
    }
}
