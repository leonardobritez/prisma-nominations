package com.prisma.nominations.worker;

import com.prisma.nominations.common.domain.NominationResult;
import com.prisma.nominations.common.domain.NominationStatus;
import com.prisma.nominations.persistence.domain.Nomination;
import com.prisma.nominations.persistence.repository.NominationRepository;
import com.prisma.nominations.persistence.repository.NominationStatusHistoryRepository;
import com.prisma.nominations.persistence.repository.OutboxEventRepository;
import com.prisma.nominations.worker.abm.AbmClient;
import com.prisma.nominations.worker.abm.AbmDispatchResult;
import com.prisma.nominations.worker.abm.AbmGateway;
import com.prisma.nominations.worker.abm.AbmResultCallback;
import com.prisma.nominations.worker.abm.AbmTechnicalException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import com.prisma.nominations.worker.service.AbmResultCallbackService;
import com.prisma.nominations.worker.service.NominationDispatchService;
import com.prisma.nominations.worker.service.NominationOutcomeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
class WorkerProcessingIntegrationTest {

    @Autowired
    private NominationRepository nominationRepository;
    @Autowired
    private NominationStatusHistoryRepository historyRepository;
    @Autowired
    private OutboxEventRepository outboxEventRepository;
    @Autowired
    private NominationDispatchService dispatchService;
    @Autowired
    private NominationOutcomeService outcomeService;
    @Autowired
    private AbmResultCallbackService callbackService;
    @Autowired
    private AbmGateway abmGateway;
    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @MockBean
    private AbmClient abmClient;

    @BeforeEach
    void cleanDatabase() {
        outboxEventRepository.deleteAll();
        historyRepository.deleteAll();
        nominationRepository.deleteAll();
        circuitBreakerRegistry.circuitBreaker("abm").reset();
    }

    @Test
    @DisplayName("E4 - ABM aprueba: estado final APPROVED y un unico evento")
    void e4_abmApproves() {
        Nomination nomination = createNomination("ACC_APPROVE");
        when(abmClient.dispatch(any())).thenReturn(AbmDispatchResult.accepted("ABM-1"));

        assertThat(dispatchService.dispatchOne(nomination.getId())).isTrue();
        Nomination claimed = reload(nomination);
        assertThat(claimed.getStatus()).isEqualTo(NominationStatus.SENT_TO_ABM);
        assertThat(claimed.getExternalRef()).isEqualTo("ABM-1");

        AbmResultCallbackService.Outcome outcome = callbackService.process(
                new AbmResultCallback(nomination.getId(), "ABM-1", NominationResult.APPROVED, null, "test"));

        assertThat(outcome).isEqualTo(AbmResultCallbackService.Outcome.APPLIED);
        Nomination done = reload(nomination);
        assertThat(done.getStatus()).isEqualTo(NominationStatus.APPROVED);
        assertThat(outboxEventRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("E5 - ABM rechaza por regla funcional: REJECTED con motivo normalizado")
    void e5_abmRejectsWithNormalizedReason() {
        Nomination nomination = createNomination("ACC_REJECT");
        when(abmClient.dispatch(any())).thenReturn(AbmDispatchResult.accepted("ABM-2"));
        dispatchService.dispatchOne(nomination.getId());

        callbackService.process(new AbmResultCallback(
                nomination.getId(), "ABM-2", NominationResult.REJECTED, "customer-blocked", "test"));

        Nomination done = reload(nomination);
        assertThat(done.getStatus()).isEqualTo(NominationStatus.REJECTED);
        assertThat(done.getReasonCode()).isEqualTo("CUSTOMER_BLOCKED");
        assertThat(outboxEventRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("E6a - ABM no responde y se agotan intentos: TIMEOUT terminal")
    void e6a_timeoutAfterExhaustedRetries() {
        Nomination nomination = createNomination("ACC_TIMEOUT");
        nomination.setStatus(NominationStatus.SENT_TO_ABM);
        nomination.setAttempts(4);
        nomination.setSentToAbmAt(Instant.now().minusSeconds(60));
        nominationRepository.save(nomination);

        outcomeService.applyTimeout(nomination.getId());

        Nomination done = reload(nomination);
        assertThat(done.getStatus()).isEqualTo(NominationStatus.TIMEOUT);
        assertThat(outboxEventRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("E6b - ABM no responde con intentos disponibles: se reprograma reintento")
    void e6b_timeoutSchedulesRetry() {
        Nomination nomination = createNomination("ACC_TIMEOUT2");
        nomination.setStatus(NominationStatus.SENT_TO_ABM);
        nomination.setAttempts(1);
        nomination.setSentToAbmAt(Instant.now().minusSeconds(60));
        nominationRepository.save(nomination);

        outcomeService.applyTimeout(nomination.getId());

        Nomination retried = reload(nomination);
        assertThat(retried.getStatus()).isEqualTo(NominationStatus.RECEIVED);
        assertThat(retried.getNextAttemptAt()).isNotNull();
        assertThat(outboxEventRepository.count()).isZero();
    }

    @Test
    @DisplayName("E7 - Respuesta duplicada de ABM: se ignora, un unico evento")
    void e7_duplicateCallbackIgnored() {
        Nomination nomination = createNomination("ACC_DUP");
        nomination.setStatus(NominationStatus.SENT_TO_ABM);
        nominationRepository.save(nomination);

        AbmResultCallback callback = new AbmResultCallback(
                nomination.getId(), "ABM-3", NominationResult.APPROVED, null, "test");

        AbmResultCallbackService.Outcome first = callbackService.process(callback);
        AbmResultCallbackService.Outcome second = callbackService.process(callback);

        assertThat(first).isEqualTo(AbmResultCallbackService.Outcome.APPLIED);
        assertThat(second).isEqualTo(AbmResultCallbackService.Outcome.DUPLICATE_IGNORED);
        assertThat(reload(nomination).getStatus()).isEqualTo(NominationStatus.APPROVED);
        assertThat(outboxEventRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("RNF - Circuit breaker ABM: se abre ante fallas tecnicas sostenidas y falla rapido")
    void circuitBreakerOpensOnSustainedFailures() {
        Nomination nomination = createNomination("ACC_FLAKY");
        when(abmClient.dispatch(any())).thenThrow(new AbmTechnicalException("ABM caido (simulado)"));

        for (int i = 0; i < 4; i++) {
            assertThatThrownBy(() -> abmGateway.send(nomination))
                    .isInstanceOf(AbmTechnicalException.class);
        }
        int callsWithCircuitClosed = mockingDetails(abmClient).getInvocations().size();

        assertThat(circuitBreakerRegistry.circuitBreaker("abm").getState())
                .isEqualTo(CircuitBreaker.State.OPEN);

        assertThatThrownBy(() -> abmGateway.send(nomination))
                .isInstanceOf(AbmTechnicalException.class);
        int callsWithCircuitOpen = mockingDetails(abmClient).getInvocations().size();

        assertThat(callsWithCircuitOpen).isEqualTo(callsWithCircuitClosed);
    }

    private Nomination createNomination(String accountRef) {
        Instant now = Instant.now();
        Nomination nomination = new Nomination();
        nomination.setId(UUID.randomUUID());
        nomination.setCustomerId("123456");
        nomination.setAccountRef(accountRef);
        nomination.setCardToken("TOKEN_REFERENCE");
        nomination.setCardMask("****ENCE");
        nomination.setAlias("CUENTA_PRINCIPAL");
        nomination.setRequestId(UUID.randomUUID());
        nomination.setStatus(NominationStatus.RECEIVED);
        nomination.setAttempts(0);
        nomination.setNextAttemptAt(now);
        nomination.setCorrelationId(UUID.randomUUID().toString());
        nomination.setCreatedAt(now);
        nomination.setUpdatedAt(now);
        return nominationRepository.save(nomination);
    }

    private Nomination reload(Nomination nomination) {
        return nominationRepository.findById(nomination.getId()).orElseThrow();
    }
}
