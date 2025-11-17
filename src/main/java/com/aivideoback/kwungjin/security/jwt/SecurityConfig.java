// src/main/java/com/aivideoback/kwungjin/security/jwt/SecurityConfig.java
package com.aivideoback.kwungjin.security.jwt;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // ✅ JWT 사용이라 CSRF 비활성화
                .csrf(csrf -> csrf.disable())
                // ✅ CORS 설정
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                // ✅ 세션 사용하지 않음 (STATELESS)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                .authorizeHttpRequests(auth -> auth
                        // ✅ preflight
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        // ✅ 관리자 로그인은 모두 허용
                        .requestMatchers("/api/admin/login").permitAll()

                        // ✅ 관리자 API는 ADMIN 권한 필요
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")

                        // ✅ 🔥 비로그인도 볼 수 있는 "영상 조회" 관련 GET 전부 허용
                        .requestMatchers(HttpMethod.GET, "/api/videos/**").permitAll()
                        // (아래 세 줄은 위 한 줄에 포함되지만, 있어도 상관 없음)
                        // .requestMatchers(HttpMethod.GET, "/api/videos/*/stream").permitAll()
                        // .requestMatchers(HttpMethod.GET, "/api/videos/public").permitAll()
                        // .requestMatchers(HttpMethod.GET, "/api/videos/home-summary").permitAll()

                        // ✅ 회원가입/로그인 관련 공개 API
                        .requestMatchers(
                                "/api/auth/register",
                                "/api/auth/login",
                                "/api/auth/check-userid",
                                "/api/auth/check-nickname",
                                "/api/auth/check-email",
                                "/api/auth/email/send-code",
                                "/api/auth/email/verify-code"
                        ).permitAll()

                        // ✅ 그 외 나머지는 인증 필요
                        .anyRequest().authenticated()
                )

                // ✅ UsernamePasswordAuthenticationFilter 전에 JWT 필터 동작
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration configuration
    ) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        // 🔹 프론트 도메인 추가 (개발 + 운영)
        config.setAllowedOrigins(List.of(
                "http://localhost:5173",
                "http://localhost:3000",
                "https://aicollector.co.kr",
                "https://www.aicollector.co.kr"
        ));
        config.setAllowedMethods(List.of(
                "GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"
        ));
        config.setAllowedHeaders(List.of(
                "Authorization",
                "Content-Type",
                "X-Requested-With",
                "Accept",
                "Origin"
        ));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
