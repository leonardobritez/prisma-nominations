package com.prisma.nominations.api.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Autenticacion por bearer token de demostracion. En produccion se reemplaza por OAuth2/JWT
 * (resource server) con validacion de firma, expiracion, audiencia y scopes; la frontera de
 * autenticacion es identica: cada solicitud debe portar credencial valida y un sujeto identificable.
 */
public class ApiTokenAuthFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final String expectedToken;
    private final String authority;

    public ApiTokenAuthFilter(String expectedToken, String authority) {
        this.expectedToken = expectedToken;
        this.authority = authority;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            String token = header.substring(BEARER_PREFIX.length()).trim();
            if (expectedToken.equals(token)) {
                var authentication = new UsernamePasswordAuthenticationToken(
                        "api-client", null, List.of(new SimpleGrantedAuthority(authority)));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }
        filterChain.doFilter(request, response);
    }
}
