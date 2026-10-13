package com.prisma.nominations.api.web;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Limite de tasa por cliente sobre el endpoint de escritura (POST /v1/nominations).
 * Ventana fija de un minuto en memoria (suficiente para un nodo; en cluster se reemplaza por un
 * backend distribuido como Redis/Bucket4j sin cambiar la frontera: 429 + Retry-After).
 *
 * <p>El sujeto del limite es {@code X-Client-Id} si viene, o la IP remota como aproximacion.</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RateLimitFilter extends OncePerRequestFilter {

    private static final long WINDOW_MILLIS = 60_000L;
    private static final String WRITE_PATH = "/v1/nominations";

    private final boolean enabled;
    private final int requestsPerMinute;
    private final MeterRegistry meterRegistry;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public RateLimitFilter(@Value("${app.rate-limit.enabled:true}") boolean enabled,
                           @Value("${app.rate-limit.requests-per-minute:120}") int requestsPerMinute,
                           MeterRegistry meterRegistry) {
        this.enabled = enabled;
        this.requestsPerMinute = requestsPerMinute;
        this.meterRegistry = meterRegistry;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!enabled || !isRateLimited(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        String key = key(request);
        long now = System.currentTimeMillis();
        Window window = windows.compute(key, (k, current) ->
                (current == null || now - current.start >= WINDOW_MILLIS) ? new Window(now) : current);

        if (window.count.incrementAndGet() > requestsPerMinute) {
            meterRegistry.counter("api.ratelimited").increment();
            long retryAfterSeconds = Math.max(1L, (WINDOW_MILLIS - (now - window.start) + 999L) / 1000L);
            response.setStatus(429);
            response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
            response.setContentType("application/json");
            response.getWriter().write(
                    "{\"code\":\"RATE_LIMITED\",\"message\":\"Se supero el limite de solicitudes; reintente mas tarde\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean isRateLimited(HttpServletRequest request) {
        return "POST".equalsIgnoreCase(request.getMethod())
                && WRITE_PATH.equals(request.getRequestURI());
    }

    private String key(HttpServletRequest request) {
        String clientId = request.getHeader("X-Client-Id");
        return (clientId == null || clientId.isBlank()) ? request.getRemoteAddr() : clientId.trim();
    }

    /** Ventana fija: instante de inicio y contador de solicitudes. */
    private static final class Window {
        private final long start;
        private final AtomicInteger count = new AtomicInteger();

        private Window(long start) {
            this.start = start;
        }
    }
}
