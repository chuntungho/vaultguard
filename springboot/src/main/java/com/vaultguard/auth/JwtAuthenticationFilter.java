package com.vaultguard.auth;

import com.vaultguard.db.entity.User;
import com.vaultguard.db.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepository;

    public JwtAuthenticationFilter(JwtService jwtService, UserRepository userRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            try {
                JwtService.ParsedToken parsed = jwtService.validateAccessToken(token);
                // Security-stamp check: rotating users.security_stamp invalidates
                // every previously issued token (deauthorize sessions), same as Rust.
                User user = userRepository.findById(parsed.userUuid()).orElse(null);
                boolean valid = user != null && user.isEnabled()
                    && (parsed.securityStamp() == null
                        || parsed.securityStamp().equals(user.getSecurityStamp()));
                if (valid) {
                    VaultGuardUserDetails userDetails =
                        new VaultGuardUserDetails(parsed.userUuid(), parsed.deviceUuid());
                    UsernamePasswordAuthenticationToken auth =
                        new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            } catch (Exception ignored) {
                // Invalid token — leave SecurityContext unauthenticated
            }
        }
        chain.doFilter(request, response);
    }
}
