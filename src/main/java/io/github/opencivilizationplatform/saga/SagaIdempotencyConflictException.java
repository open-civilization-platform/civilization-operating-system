package io.github.opencivilizationplatform.saga;

public class SagaIdempotencyConflictException extends SagaException {
    public SagaIdempotencyConflictException() { super("Idempotency key already belongs to a different request"); }
}
