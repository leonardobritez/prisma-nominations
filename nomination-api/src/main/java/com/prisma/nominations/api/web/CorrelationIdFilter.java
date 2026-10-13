package com.prisma.nominations.api.web;

import com.prisma.nominations.common.correlation.Correlation;
import com.prisma.nominations.common.correlation.CorrelationContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Toma (o genera) el correlationId y el requestId, los publica en el contexto y en el MDC
 * (para que aparezcan en todos los logs) y los devuelve en las cabeceras de respuesta.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String correlationId = headerOrRandom(request, Correlation.CORRELATION_ID_HEADER);
        String requestId = headerOrRandom(request, Correlation.REQUEST_ID_HEADER);

        CorrelationContext.setCorrelationId(correlationId);
        CorrelationContext.setRequestId(requestId);
        MDC.put(Correlation.MDC_CORRELATION_ID, correlationId);
        MDC.put(Correlation.MDC_REQUEST_ID, requestId);

        response.setHeader(Correlation.CORRELATION_ID_HEADER, correlationId);
        response.setHeader(Correlation.REQUEST_ID_HEADER, requestId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            CorrelationContext.clear();
            MDC.remove(Correlation.MDC_CORRELATION_ID);
            MDC.remove(Correlation.MDC_REQUEST_ID);
        }
    }

    private String headerOrRandom(HttpServletRequest request, String header) {
        String value = request.getHeader(header);
        return (value == null || value.isBlank()) ? UUID.randomUUID().toString() : value.trim();
    }
}
