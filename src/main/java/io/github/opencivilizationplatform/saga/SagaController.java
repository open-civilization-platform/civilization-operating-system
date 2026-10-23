package io.github.opencivilizationplatform.saga;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/sagas")
@Tag(name = "Sagas", description = "Distributed transaction sagas for multi-step operations")
public class SagaController {

    private final FoundCivilizationSagaService service;

    public SagaController(FoundCivilizationSagaService service) {
        this.service = service;
    }

    @PostMapping("/found-civilization")
    @Operation(summary = "Submit an asynchronous FoundCivilization saga",
        description = "Returns 202 with durable status (PENDING, RUNNING, COMPENSATING, SUCCEEDED, FAILED, "
            + "COMPENSATED or COMPENSATION_FAILED). Retains civilizationId and name when available.")
    public ResponseEntity<SagaStatusResponse> foundCivilization(
            @RequestBody FoundCivilizationRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            Authentication authentication) {
        if (request.name() == null || request.name().isBlank() || request.scale() == null
                || request.regionId() == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("Missing saga submission field");
        }
        return ResponseEntity.accepted().body(service.submit(request, authentication.getName(), idempotencyKey));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get the authenticated owner's durable saga status")
    public SagaStatusResponse getStatus(@PathVariable UUID id, Authentication authentication) {
        return service.getStatus(id, authentication.getName());
    }
}
