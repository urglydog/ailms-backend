package com.lms.common.config;

import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import com.lms.auth.security.JwtAuthenticationFilter;
import com.lms.auth.security.CustomUserDetailsService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import lombok.RequiredArgsConstructor;
/**
 * Cấu hình bảo mật — <b>khung sườn của Giai đoạn 0</b>.
 *
 * <p>Ở giai đoạn này chưa có JWT filter, chưa có UserDetailsService: những phần đó
 * thuộc Giai đoạn 1 (UC01–UC08). Hiện tại chỉ thiết lập những thứ không phụ thuộc
 * nghiệp vụ: stateless session, CORS cho frontend, {@link PasswordEncoder}, và mở
 * các endpoint public đã biết.
 *
 * <p><b>Giai đoạn 1 sẽ thêm:</b> {@code JwtAuthenticationFilter} trước
 * {@code UsernamePasswordAuthenticationFilter}, {@code OAuth2} Google (UC02),
 * và cơ chế thu hồi refresh token (BR-AUTH-04).
 */
@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final CustomUserDetailsService customUserDetailsService;

    /**
     * Danh sách endpoint public — khớp mục "Nhóm endpoint public" trong
     * {@code ai-agent/context/api.md}. Mọi UC khác đều {@code <<include>>} UC02
     * nên bắt buộc có JWT.
     */
    private static final String[] PUBLIC_ENDPOINTS = {
            "/actuator/health",
            "/actuator/health/**",
            "/actuator/info",
            "/api/v1/auth/**",          // UC01 đăng ký, UC02 đăng nhập, UC04 quên mật khẩu
            "/api/v1/oauth2/**",        // UC02 Google OAuth2
            "/api/v1/payments/callback/**", // UC14 IPN từ cổng thanh toán (xác thực bằng HMAC, không phải JWT)
            "/ws/**"                    // handshake WebSocket, token kiểm ở tầng STOMP
    };

    /** Endpoint public chỉ cho phép đọc: UC09 tìm kiếm, UC10 chi tiết, UC11 preview, UC49 chat. */
    private static final String[] PUBLIC_GET_ENDPOINTS = {
            "/api/v1/courses/**",
            "/api/v1/categories/**",
            "/api/v1/discovery/**"
    };

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // API stateless dùng JWT nên không cần CSRF token
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                        .requestMatchers(HttpMethod.GET, PUBLIC_GET_ENDPOINTS).permitAll()
                        .anyRequest().authenticated()
                )
                .authenticationProvider(authenticationProvider())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(customUserDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    /**
     * Bcrypt với cost 12 (mặc định của Spring là 10).
     * BR-AUTH-01 yêu cầu {@code cost >= 10}; chọn 12 để có biên an toàn.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    /**
     * CORS cho frontend dev. Giai đoạn 10 phải thay {@code localhost} bằng domain thật.
     * {@code allowCredentials(true)} cần thiết để browser gửi cookie refresh token.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of("http://localhost:3000"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
