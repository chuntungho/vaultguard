package com.vaultguard.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component
public class AdminAuthFilter extends OncePerRequestFilter {

    private final VaultGuardProperties props;

    public AdminAuthFilter(VaultGuardProperties props) {
        this.props = props;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/admin/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String token = request.getHeader("X-Admin-Token");
        if (!verifyAdminToken(token)) {
            response.setStatus(401);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"unauthorized\"}");
            return;
        }
        chain.doFilter(request, response);
    }

    private boolean verifyAdminToken(String token) {
        String stored = props.getAdminToken();
        if (stored == null || stored.isBlank() || token == null || token.isBlank()) return false;
        // Constant-time comparison to prevent timing attacks
        return MessageDigest.isEqual(
            token.getBytes(StandardCharsets.UTF_8),
            stored.getBytes(StandardCharsets.UTF_8));
    }
}
