package io.github.opencivilizationplatform.saga;

public record SagaProgressEvent(String type, SagaProgressPayload payload) {
    public SagaProgressEvent(SagaProgressPayload payload) {
        this("SAGA_PROGRESS", payload);
    }

    // Redis needs routing metadata; only event is forwarded to the browser.
    public record Routed(String ownerId, SagaProgressEvent event) {}
}
