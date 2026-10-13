package com.prisma.nominations.worker.abm;

import com.prisma.nominations.common.correlation.Correlation;
import com.prisma.nominations.common.correlation.CorrelationContext;
import com.prisma.nominations.persistence.domain.Nomination;
import com.prisma.nominations.worker.config.AbmProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Map;

/**
 * Cliente HTTP real hacia ABM. Solo se activa fuera del perfil {@code mock-abm}.
 * Distingue errores recuperables (timeout/5xx/429) de rechazos funcionales (4xx definitivo).
 */
@Component
@Profile("!mock-abm")
@RequiredArgsConstructor
@Slf4j
public class HttpAbmClient implements AbmClient {

    private final AbmProperties properties;
    private volatile RestClient restClient;

    @Override
    public AbmDispatchResult dispatch(Nomination nomination) {
        Map<String, Object> body = Map.of(
                "nomination_id", nomination.getId().toString(),
                "customer_id", nomination.getCustomerId(),
                "account_ref", nomination.getAccountRef(),
                "card_token", nomination.getCardToken(),
                "alias", nomination.getAlias(),
                "correlation_id", CorrelationContext.getOrCreateCorrelationId()
        );

        try {
            AbmDispatchResponse response = client().post()
                    .uri("/v1/abm/nominations")
                    .header(Correlation.CORRELATION_ID_HEADER, CorrelationContext.getOrCreateCorrelationId())
                    .body(body)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                        throw new AbmFunctionalException("UPSTREAM_ERROR",
                                "ABM rechazo el envio con estado " + res.getStatusCode());
                    })
                    .body(AbmDispatchResponse.class);

            String externalRef = response == null ? null : response.externalRef();
            log.debug("ABM acepto nominacion id={} externalRef={}", nomination.getId(), externalRef);
            return AbmDispatchResult.accepted(externalRef);
        } catch (ResourceAccessException e) {
            throw new AbmTechnicalException("Timeout o error de red al invocar ABM", e);
        } catch (AbmFunctionalException e) {
            throw e;
        } catch (Exception e) {
            throw new AbmTechnicalException("Error tecnico invocando ABM: " + e.getMessage(), e);
        }
    }

    private RestClient client() {
        RestClient current = restClient;
        if (current == null) {
            synchronized (this) {
                if (restClient == null) {
                    restClient = buildClient();
                }
                current = restClient;
            }
        }
        return current;
    }

    private RestClient buildClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        Duration timeout = properties.getTimeout();
        factory.setConnectTimeout((int) timeout.toMillis());
        factory.setReadTimeout((int) timeout.toMillis());
        return RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .requestFactory(factory)
                .build();
    }

    /** Respuesta de aceptacion de ABM. */
    public record AbmDispatchResponse(String externalRef) {
    }
}
