package com.prisma.nominations.worker.abm;

import com.prisma.nominations.persistence.domain.Nomination;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Puerta de entrada unica hacia ABM, protegida por un Circuit Breaker (Resilience4j).
 *
 * <p>Proposito: cuando ABM se degrada de forma sostenida, el circuito se abre y las llamadas
 * fallan rapido ({@link CallNotPermittedException}) en lugar de consumir hilos/tiempo de espera.
 * Las fallas se convierten en {@link AbmTechnicalException} para que la maquina de estados del
 * worker programe el reintento con backoff; asi el circuit breaker y la politica de reintentos
 * son complementarios y no se duplican.</p>
 *
 * <p>Los rechazos funcionales ({@link AbmFunctionalException}) estan en {@code ignoreExceptions}:
 * no cuentan como falla del proveedor y se propagan sin abrir el circuito.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AbmGateway {

    private final AbmClient delegate;

    @CircuitBreaker(name = "abm", fallbackMethod = "onFailure")
    public AbmDispatchResult send(Nomination nomination) {
        return delegate.dispatch(nomination);
    }

    private AbmDispatchResult onFailure(Nomination nomination, Throwable error) {
        if (error instanceof AbmFunctionalException functional) {
            throw functional;
        }
        if (error instanceof AbmTechnicalException technical) {
            throw technical;
        }
        log.warn("Circuito ABM no permite la llamada (estado abierto). nominationId={} causa={}",
                nomination.getId(), error.getClass().getSimpleName());
        throw new AbmTechnicalException("Circuito ABM abierto o falla de invocacion: " + error.getMessage(), error);
    }
}
