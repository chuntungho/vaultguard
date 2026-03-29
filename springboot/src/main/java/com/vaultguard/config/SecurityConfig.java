package com.vaultguard.config;

import com.vaultguard.auth.JwtAuthenticationFilter;
import com.vaultguard.auth.JwtService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtService jwtService;
    private final RateLimitFilter rateLimitFilter;

    public SecurityConfig(JwtService jwtService, RateLimitFilter rateLimitFilter) {
        this.jwtService = jwtService;
        this.rateLimitFilter = rateLimitFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/identity/connect/token",
                    "/api/accounts/register",
                    "/api/accounts/prelogin",
                    "/icons/**",
                    "/admin/**",          // admin static pages (no user auth)
                    "/",                   // web vault root
                    "/app/**",             // web vault SPA routes
                    "/assets/**",          // web vault assets
                    "/vw_static/**"        // static assets
                ).permitAll()
                .anyRequest().authenticated()
            )
            // Rate limiting must run before JWT auth to block brute-force before token validation
            .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(new JwtAuthenticationFilter(jwtService),
                UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
