package com.vaultguard.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Set<String> RATE_LIMITED_PATHS = Set.of(
        "/identity/connect/token",
        "/api/accounts/register"
    );

    private final VaultGuardProperties props;
    private final Map<String, AtomicInteger> counts = new ConcurrentHashMap<>();
    private volatile long windowStart = System.currentTimeMillis();

    public RateLimitFilter(VaultGuardProperties props) {
        this.props = props;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String path = request.getRequestURI();
        if (!RATE_LIMITED_PATHS.contains(path)) {
            chain.doFilter(request, response);
            return;
        }
        long now = System.currentTimeMillis();
        long windowMs = props.getRateLimit().getWindowSeconds() * 1000L;
        if (now - windowStart > windowMs) {
            synchronized (this) {
                if (now - windowStart > windowMs) {
                    counts.clear();
                    windowStart = now;
                }
            }
        }
        String ip = request.getRemoteAddr();
        AtomicInteger counter = counts.computeIfAbsent(ip, k -> new AtomicInteger(0));
        if (counter.incrementAndGet() >= props.getRateLimit().getMaxRequests()) {
            response.setStatus(429);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"too_many_requests\"}");
            return;
        }
        chain.doFilter(request, response);
    }
}
