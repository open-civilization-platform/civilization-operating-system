package io.github.opencivilizationplatform.saga;

import io.github.opencivilizationplatform.config.GlobalExceptionHandler;
import io.github.opencivilizationplatform.config.JwtAuthFilter;
import io.github.opencivilizationplatform.config.JwtService;
import io.github.opencivilizationplatform.config.SecurityConfig;
import io.github.opencivilizationplatform.config.seed.CivilizationScale;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.http.MediaType;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

@SpringJUnitWebConfig({SagaController.class, SecurityConfig.class, JwtAuthFilter.class,
    GlobalExceptionHandler.class, SagaControllerTest.MvcConfiguration.class})
class SagaControllerTest {
    private static final UUID ID = UUID.fromString("2ab0beac-910e-4fa1-a772-d751d4561a74");
    private static final String BODY = """
        {"name":"Arcadia","scale":"LOCAL","regionId":42}
        """;

    private MockMvc mvc;
    @Autowired WebApplicationContext context;
    @Autowired FilterChainProxy securityFilterChain;
    @MockitoBean FoundCivilizationSagaService service;
    @MockitoBean JwtService jwtService;

    @BeforeEach
    void validToken() {
        mvc = webAppContextSetup(context).addFilters(securityFilterChain).build();
        when(jwtService.isTokenValid("alice-token")).thenReturn(true);
        when(jwtService.extractClientId("alice-token")).thenReturn("alice");
        when(jwtService.isTokenValid("bob-token")).thenReturn(true);
        when(jwtService.extractClientId("bob-token")).thenReturn("bob");
    }

    @Test
    void postReturnsAcceptedWithSagaIdAndLegacyFields() throws Exception {
        when(service.submit(eq(new FoundCivilizationRequest("Arcadia", CivilizationScale.LOCAL, 42L)),
                eq("alice"), eq("key-1")))
            .thenReturn(response());

        mvc.perform(post("/api/v1/sagas/found-civilization")
                .header("Authorization", "Bearer alice-token")
                .header("X-Client-Id", "bob")
                .header("Idempotency-Key", "key-1")
                .contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.sagaId").value(ID.toString()))
            .andExpect(jsonPath("$.status").value("SUCCEEDED"))
            .andExpect(jsonPath("$.civilizationId").value(7))
            .andExpect(jsonPath("$.name").value("Arcadia"))
            .andExpect(jsonPath("$.steps[0].key").value("CREATE_CIVILIZATION"))
            .andExpect(jsonPath("$.steps[0].label").value("Civilization created"))
            .andExpect(jsonPath("$.steps[1].key").value("CLAIM_REGION"))
            .andExpect(jsonPath("$.steps[1].label").value("Region claimed"))
            .andExpect(jsonPath("$.steps[2].key").value("DEPLOY_NEXUS_NODE"))
            .andExpect(jsonPath("$.steps[2].label").value("Nexus node deployed"))
            .andExpect(jsonPath("$.sequence").value(3))
            .andExpect(jsonPath("$.createdAt").value("2026-10-23T12:00:00Z"))
            .andExpect(jsonPath("$.updatedAt").value("2026-10-23T12:01:00Z"));
    }

    @Test
    void getReturnsOwnedSaga() throws Exception {
        when(service.getStatus(ID, "alice")).thenReturn(response());
        mvc.perform(get("/api/v1/sagas/{id}", ID)
                .header("Authorization", "Bearer alice-token"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.sagaId").value(ID.toString()))
            .andExpect(jsonPath("$.name").value("Arcadia"));
    }

    @Test
    void getReturnsNotFoundForForeignSaga() throws Exception {
        when(service.getStatus(ID, "bob")).thenThrow(new SagaException("Saga not found"));
        mvc.perform(get("/api/v1/sagas/{id}", ID)
                .header("Authorization", "Bearer bob-token"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("Saga not found"));
    }

    @Test
    void getReturnsNotFoundForUnknownSaga() throws Exception {
        when(service.getStatus(ID, "alice")).thenThrow(new SagaException("Saga not found"));
        mvc.perform(get("/api/v1/sagas/{id}", ID)
                .header("Authorization", "Bearer alice-token"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("Saga not found"));
    }

    @Test
    void postRequiresAuthentication() throws Exception {
        mvc.perform(post("/api/v1/sagas/found-civilization")
                .header("Idempotency-Key", "key-1")
                .contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    void getRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/v1/sagas/{id}", ID))
            .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    void sameKeyDifferentPayloadReturnsConflict() throws Exception {
        when(service.submit(eq(new FoundCivilizationRequest("Arcadia", CivilizationScale.LOCAL, 42L)),
                eq("alice"), eq("key-1")))
            .thenThrow(new SagaIdempotencyConflictException());
        mvc.perform(post("/api/v1/sagas/found-civilization")
                .header("Authorization", "Bearer alice-token")
                .header("Idempotency-Key", "key-1")
                .contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isConflict());
    }

    @Test
    void postRejectsMissingIdempotencyKey() throws Exception {
        mvc.perform(post("/api/v1/sagas/found-civilization")
                .header("Authorization", "Bearer alice-token")
                .contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "{\"scale\":\"LOCAL\",\"regionId\":42}",
        "{\"name\":\" \",\"scale\":\"LOCAL\",\"regionId\":42}",
        "{\"name\":\"Arcadia\",\"regionId\":42}",
        "{\"name\":\"Arcadia\",\"scale\":\"LOCAL\"}",
        "{\"name\":\"Arcadia\",\"scale\":\"UNKNOWN\",\"regionId\":42}",
        "{\"name\":\"Arcadia\",\"scale\":\"LOCAL\",\"regionId\":\"forest\"}",
        "{", "null"
    })
    void postRejectsInvalidRequest(String body) throws Exception {
        mvc.perform(post("/api/v1/sagas/found-civilization")
                .header("Authorization", "Bearer alice-token")
                .header("Idempotency-Key", "key-2")
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void postRejectsBlankIdempotencyKey() throws Exception {
        mvc.perform(post("/api/v1/sagas/found-civilization")
                .header("Authorization", "Bearer alice-token")
                .header("Idempotency-Key", " ")
                .contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void getRejectsMalformedSagaId() throws Exception {
        mvc.perform(get("/api/v1/sagas/not-a-uuid")
                .header("Authorization", "Bearer alice-token"))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    private static SagaStatusResponse response() {
        return new SagaStatusResponse(ID, SagaStatus.SUCCEEDED, null, null, 7L, "Arcadia", null,
            List.of(new SagaStepView("CREATE_CIVILIZATION", SagaStepStatus.SUCCEEDED, 1,
                null, null, null),
                new SagaStepView("CLAIM_REGION", SagaStepStatus.SUCCEEDED, 1,
                    null, null, null),
                new SagaStepView("DEPLOY_NEXUS_NODE", SagaStepStatus.SUCCEEDED, 1,
                    null, null, null)), 3L,
            Instant.parse("2026-10-23T12:00:00Z"), Instant.parse("2026-10-23T12:01:00Z"));
    }

    @TestConfiguration
    @EnableWebMvc
    static class MvcConfiguration {}
}
