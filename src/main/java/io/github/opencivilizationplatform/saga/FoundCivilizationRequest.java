package io.github.opencivilizationplatform.saga;

import io.github.opencivilizationplatform.config.seed.CivilizationScale;

public record FoundCivilizationRequest(String name, CivilizationScale scale, Long regionId) {}
