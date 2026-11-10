package ru.itmo.students;

import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import ru.itmo.students.web.*;
import java.time.Duration;
import java.util.ArrayList;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitTest {
    @Test
    void slidingWindowExpiresRequestsAndKeepsIpsIndependent() {
        var time = new AtomicLong();
        var limiter = new IpRateLimiter(2, Duration.ofSeconds(60), time::get);
        assertThat(limiter.check("one")).isZero();
        time.set(Duration.ofSeconds(10).toNanos());
        assertThat(limiter.check("one")).isZero();
        assertThat(limiter.check("one")).isEqualTo(50);
        assertThat(limiter.check("two")).isZero();
        time.set(Duration.ofSeconds(59).toNanos());
        assertThat(limiter.check("one")).isEqualTo(1);
        time.set(Duration.ofSeconds(60).toNanos());
        assertThat(limiter.check("one")).isZero();
        assertThat(limiter.check("one")).isEqualTo(10);
    }

    @Test
    void concurrentRequestsCannotOverrunTheLimit() throws Exception {
        var limiter = new IpRateLimiter(100, Duration.ofSeconds(60), () -> 0L);
        var pool = Executors.newFixedThreadPool(8);
        try {
            var calls = new ArrayList<Callable<Long>>();
            for (int i = 0; i < 150; i++) calls.add(() -> limiter.check("same-ip"));
            long allowed = 0;
            for (Future<Long> result : pool.invokeAll(calls)) if (result.get() == 0) allowed++;
            assertThat(allowed).isEqualTo(100);
        } finally { pool.shutdownNow(); }
    }

    @Test
    void filterReturnsJson429AndDoesNotTrustForwardedIp() throws Exception {
        var mapper = JsonMapper.builder().build();
        var filter = new RateLimitFilter(new IpRateLimiter(1, Duration.ofSeconds(60), () -> 0L), new ApiErrorWriter(mapper));
        var first = new MockHttpServletRequest("GET", "/api/requests");
        first.setRemoteAddr("127.0.0.1");
        filter.doFilter(first, new MockHttpServletResponse(), new MockFilterChain());
        var second = new MockHttpServletRequest("QUERY", "/api/requests");
        second.setRemoteAddr("127.0.0.1");
        second.addHeader("X-Forwarded-For", "another-address");
        var response = new MockHttpServletResponse();
        filter.doFilter(second, response, new MockFilterChain());
        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getHeader("Retry-After")).isEqualTo("60");
        assertThat(mapper.readTree(response.getContentAsString()).get("details").get("retry_after").asInt()).isEqualTo(60);
        var options = new MockHttpServletRequest("OPTIONS", "/api/requests");
        options.setRemoteAddr("127.0.0.1");
        var preflight = new MockHttpServletResponse();
        filter.doFilter(options, preflight, new MockFilterChain());
        assertThat(preflight.getStatus()).isEqualTo(200);
    }
}
