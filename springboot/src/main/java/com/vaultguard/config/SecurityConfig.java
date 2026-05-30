package com.vaultguard.config;

import com.vaultguard.auth.JwtAuthenticationFilter;
import com.vaultguard.auth.JwtService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtService jwtService;
    private final RateLimitFilter rateLimitFilter;
    private final AdminAuthFilter adminAuthFilter;
    private final VaultGuardProperties props;

    public SecurityConfig(JwtService jwtService,
                          RateLimitFilter rateLimitFilter,
                          AdminAuthFilter adminAuthFilter,
                          VaultGuardProperties props) {
        this.jwtService = jwtService;
        this.rateLimitFilter = rateLimitFilter;
        this.adminAuthFilter = adminAuthFilter;
        this.props = props;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .cors(c -> c.configurationSource(adminCorsConfigurationSource()))
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/identity/connect/token",
                    "/api/accounts/register",
                    "/api/accounts/prelogin",
                    "/icons/**",
                    "/admin/**",          // admin static pages (no user auth)
                    "/api/admin/**",       // protected by AdminAuthFilter, not Spring Security user auth
                    "/",                   // web vault root
                    "/app/**",             // web vault SPA routes
                    "/assets/**",          // web vault assets
                    "/vw_static/**"        // static assets
                ).permitAll()
                .anyRequest().authenticated()
            )
            // Rate limiting must run before JWT auth to block brute-force before token validation
            .addFilterBefore(adminAuthFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(new JwtAuthenticationFilter(jwtService),
                UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public FilterRegistrationBean<AdminAuthFilter> adminFilterRegistration(AdminAuthFilter f) {
        FilterRegistrationBean<AdminAuthFilter> reg = new FilterRegistrationBean<>(f);
        reg.setEnabled(false);
        return reg;
    }

    @Bean
    public FilterRegistrationBean<RateLimitFilter> rateLimitFilterRegistration(RateLimitFilter f) {
        FilterRegistrationBean<RateLimitFilter> reg = new FilterRegistrationBean<>(f);
        reg.setEnabled(false);
        return reg;
    }

    @Bean
    public CorsConfigurationSource adminCorsConfigurationSource() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        List<String> origins = props.getAdminCorsOrigins();
        if (origins != null && !origins.isEmpty()) {
            CorsConfiguration cors = new CorsConfiguration();
            cors.setAllowedOrigins(origins);
            cors.setAllowedMethods(List.of("GET", "POST", "DELETE", "OPTIONS"));
            cors.setAllowedHeaders(List.of("X-Admin-Token", "Content-Type"));
            cors.setExposedHeaders(List.of("X-Total-Count"));
            cors.setAllowCredentials(false);
            cors.setMaxAge(3600L);
            source.registerCorsConfiguration("/api/admin/**", cors);
        }
        return source;
    }
}
