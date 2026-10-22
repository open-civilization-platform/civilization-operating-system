package io.github.opencivilizationplatform.modules.social.domain;

public record HumanPlayer(
        String playerId,
        String username,
        String assignedCivilizationId,
        int influenceTokens
) {
}
