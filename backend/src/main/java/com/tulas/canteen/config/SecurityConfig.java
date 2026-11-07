package com.tulas.canteen.config;

import com.tulas.canteen.security.JwtAuthFilter;
import jakarta.servlet.DispatcherType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtAuthFilter jwtFilter) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(c -> {})
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a
                        // Live-update streams finish on an async dispatch, which carries no token.
                        .dispatcherTypeMatchers(DispatcherType.ASYNC, DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/api/auth/register", "/api/auth/login", "/api/health").permitAll()
                        .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/kitchen/**").hasAnyRole("STAFF", "ADMIN")
                        .requestMatchers("/api/orders/**", "/api/wallet/**", "/api/favourites/**").hasRole("STUDENT")
                        .requestMatchers("/api/**").authenticated()
                        // Anything else is the built React app (when served from the same jar).
                        .anyRequest().permitAll())
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) -> writeError(res, 401, "Please log in again."))
                        .accessDeniedHandler((req, res, ex) -> writeError(res, 403, "You don't have access to this.")))
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    private static void writeError(jakarta.servlet.http.HttpServletResponse res, int status, String message)
            throws java.io.IOException {
        res.setStatus(status);
        res.setContentType("application/json");
        res.setCharacterEncoding("UTF-8");
        res.getWriter().write("{\"status\":" + status + ",\"message\":\"" + message.replace("\"", "'") + "\"}");
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(@Value("${app.cors-origins}") String origins) {
        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOrigins(Arrays.stream(origins.split(",")).map(String::trim).toList());
        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cfg.setAllowedHeaders(List.of("Authorization", "Content-Type", "Idempotency-Key"));
        cfg.setExposedHeaders(List.of("Content-Disposition"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cfg);
        return source;
    }
}
