package com.prisma.nominations.api.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Seguridad del borde: stateless, sin CSRF (API sin cookies), autenticacion por token.
 * Aplica minimo privilegio: solo {@code ROLE_NOMINATION_WRITER} puede escribir y consultar.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           @Value("${app.security.enabled:true}") boolean securityEnabled,
                                           @Value("${app.security.token:dev-token}") String token,
                                           @Value("${app.security.authority:ROLE_NOMINATION_WRITER}") String authority)
            throws Exception {

        http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers(
                            "/actuator/health/**",
                            "/actuator/info",
                            "/actuator/prometheus",
                            "/v3/api-docs/**",
                            "/swagger-ui/**",
                            "/swagger-ui.html"
                    ).permitAll();
                    if (securityEnabled) {
                        auth.requestMatchers("/v1/nominations/**").hasAuthority(authority);
                        auth.anyRequest().authenticated();
                    } else {
                        auth.anyRequest().permitAll();
                    }
                })
                .exceptionHandling(ex -> ex.authenticationEntryPoint(
                        new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)));

        if (securityEnabled) {
            http.addFilterBefore(new ApiTokenAuthFilter(token, authority),
                    UsernamePasswordAuthenticationFilter.class);
        }
        return http.build();
    }
}
