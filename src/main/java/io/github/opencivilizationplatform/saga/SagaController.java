package io.github.opencivilizationplatform.saga;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.github.opencivilizationplatform.dto.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/sagas")
@Tag(name = "Sagas", description = "Distributed transaction sagas for multi-step operations")
public class SagaController {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(SagaController.class);
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

    // Local handlers retain the REST status contract ahead of the Reaktor starter's catch-all advice.
    @ExceptionHandler({SagaException.class, IllegalArgumentException.class,
        HttpMessageNotReadableException.class, ServletRequestBindingException.class,
        MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ErrorResponse> handleSubmissionError(Exception failure) {
        HttpStatus status = failure instanceof SagaIdempotencyConflictException ? HttpStatus.CONFLICT
            : failure instanceof SagaException ? HttpStatus.NOT_FOUND : HttpStatus.BAD_REQUEST;
        String message = status == HttpStatus.CONFLICT ? "Idempotency key already belongs to a different request"
            : status == HttpStatus.NOT_FOUND ? "Saga not found" : "Invalid saga submission";
        return error(status, message);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpectedFailure(Exception failure) {
        log.error("Saga request failed", failure);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
    }

    private ResponseEntity<ErrorResponse> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(new ErrorResponse(status.getReasonPhrase(), message));
    }
}
