package io.github.opencivilizationplatform.modules.participation.application;

import io.github.opencivilizationplatform.modules.participation.domain.Interaction;
import io.github.opencivilizationplatform.modules.participation.infrastructure.InteractionRepository;
import org.springframework.stereotype.Service;

@Service
public class InteractionService {
    private final InteractionRepository interactionRepository;

    public InteractionService(InteractionRepository interactionRepository) {
        this.interactionRepository = interactionRepository;
    }

    public java.util.List<Interaction> getAllInteractions() {
        return interactionRepository.findAll();
    }

    public Interaction saveInteraction(Interaction interaction) {
        return interactionRepository.save(interaction);
    }
}
