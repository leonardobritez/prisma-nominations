package com.prisma.nominations.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prisma.nominations.common.correlation.Correlation;
import com.prisma.nominations.common.domain.NominationStatus;
import com.prisma.nominations.persistence.repository.IdempotencyRepository;
import com.prisma.nominations.persistence.repository.NominationRepository;
import com.prisma.nominations.persistence.repository.NominationStatusHistoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NominationApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private NominationRepository nominationRepository;
    @Autowired
    private NominationStatusHistoryRepository historyRepository;
    @Autowired
    private IdempotencyRepository idempotencyRepository;

    @BeforeEach
    void cleanDatabase() {
        idempotencyRepository.deleteAll();
        historyRepository.deleteAll();
        nominationRepository.deleteAll();
    }

    @Test
    @DisplayName("E1 - Solicitud valida: aceptacion asincronica 202 y trazabilidad")
    void e1_validRequestIsAcceptedAndTraced() throws Exception {
        String body = validBody();

        mockMvc.perform(post("/v1/nominations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(Correlation.IDEMPOTENCY_KEY_HEADER, UUID.randomUUID().toString())
                        .content(body))
                .andExpect(status().isAccepted())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.nomination_id").isNotEmpty())
                .andExpect(jsonPath("$.status").value("RECEIVED"));

        assertThat(nominationRepository.count()).isEqualTo(1);
        assertThat(historyRepository.count()).isEqualTo(1);
        assertThat(nominationRepository.findAll().get(0).getStatus()).isEqualTo(NominationStatus.RECEIVED);
    }

    @Test
    @DisplayName("E2 - Datos obligatorios ausentes: 400 con codigo de error claro")
    void e2_missingFieldsReturnsBadRequest() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "account_id", "987654",
                "alias", "CUENTA_PRINCIPAL",
                "request_id", UUID.randomUUID().toString()
        ));

        mockMvc.perform(post("/v1/nominations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_FIELD"))
                .andExpect(jsonPath("$.errors[0].field").isNotEmpty())
                .andExpect(jsonPath("$.correlationId").isNotEmpty());

        assertThat(nominationRepository.count()).isZero();
    }

    @Test
    @DisplayName("E2b - Formato invalido (alias): 400")
    void e2b_invalidFormatReturnsBadRequest() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "customer_id", "123456",
                "account_id", "987654",
                "card_id", "TOKEN",
                "alias", "alias con espacios",
                "request_id", UUID.randomUUID().toString()
        ));

        mockMvc.perform(post("/v1/nominations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_FORMAT"));
    }

    @Test
    @DisplayName("E3 - Solicitud repetida: respuesta idempotente, sin doble registro")
    void e3_duplicateRequestIsIdempotent() throws Exception {
        String idempotencyKey = "idem-" + UUID.randomUUID();
        String body = validBody();

        String first = mockMvc.perform(post("/v1/nominations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(Correlation.IDEMPOTENCY_KEY_HEADER, idempotencyKey)
                        .content(body))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();

        String second = mockMvc.perform(post("/v1/nominations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(Correlation.IDEMPOTENCY_KEY_HEADER, idempotencyKey)
                        .content(body))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();

        String firstId = objectMapper.readTree(first).get("nomination_id").asText();
        String secondId = objectMapper.readTree(second).get("nomination_id").asText();
        assertThat(secondId).isEqualTo(firstId);
        assertThat(nominationRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("E3b - Misma clave con cuerpo distinto: 409 IDEMPOTENCY_CONFLICT")
    void e3b_sameKeyDifferentBodyConflicts() throws Exception {
        String idempotencyKey = "idem-" + UUID.randomUUID();

        mockMvc.perform(post("/v1/nominations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(Correlation.IDEMPOTENCY_KEY_HEADER, idempotencyKey)
                        .content(validBody()))
                .andExpect(status().isAccepted());

        String differentBody = objectMapper.writeValueAsString(Map.of(
                "customer_id", "999999",
                "account_id", "111222",
                "card_id", "TOKEN_2",
                "alias", "OTRA_CUENTA",
                "request_id", UUID.randomUUID().toString()
        ));

        mockMvc.perform(post("/v1/nominations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(Correlation.IDEMPOTENCY_KEY_HEADER, idempotencyKey)
                        .content(differentBody))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));
    }

    @Test
    @DisplayName("Consulta de estado e historial, y 404 si no existe")
    void queryStatusAndHistory() throws Exception {
        String body = validBody();
        String response = mockMvc.perform(post("/v1/nominations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(Correlation.IDEMPOTENCY_KEY_HEADER, UUID.randomUUID().toString())
                        .content(body))
                .andReturn().getResponse().getContentAsString();
        String nominationId = objectMapper.readTree(response).get("nomination_id").asText();

        mockMvc.perform(get("/v1/nominations/{id}", nominationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RECEIVED"))
                .andExpect(jsonPath("$.card_mask").value("****ENCE"))
                .andExpect(jsonPath("$.account_mask").value("****7654"));

        mockMvc.perform(get("/v1/nominations/{id}/history", nominationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries[0].to_status").value("RECEIVED"));

        mockMvc.perform(get("/v1/nominations/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    private String validBody() throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "customer_id", "123456",
                "account_id", "987654",
                "card_id", "TOKEN_OR_MASKED_REFERENCE",
                "alias", "CUENTA_PRINCIPAL",
                "request_id", UUID.randomUUID().toString()
        ));
    }
}
