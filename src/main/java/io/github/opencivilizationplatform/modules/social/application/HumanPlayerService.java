package io.github.opencivilizationplatform.modules.social.application;

import io.github.opencivilizationplatform.modules.social.domain.HumanPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class HumanPlayerService {

    private static final Logger log = LoggerFactory.getLogger(HumanPlayerService.class);

    private final Map<String, HumanPlayer> playerRegistry = new ConcurrentHashMap<>();

    public void registerPlayer(HumanPlayer player) {
        playerRegistry.put(player.playerId(), player);
        log.info("[HUMAN PLAYER SERVICE] Registered player: {} for civilization: {}",
                player.username(), player.assignedCivilizationId());
    }

    public Optional<HumanPlayer> getPlayer(String playerId) {
        return Optional.ofNullable(playerRegistry.get(playerId));
    }

    public Collection<HumanPlayer> getAllPlayers() {
        return playerRegistry.values();
    }

    public boolean spendInfluenceTokens(String playerId, int amount) {
        HumanPlayer player = playerRegistry.get(playerId);
        if (player != null && player.influenceTokens() >= amount) {
            HumanPlayer updated = new HumanPlayer(
                    player.playerId(),
                    player.username(),
                    player.assignedCivilizationId(),
                    player.influenceTokens() - amount
            );
            playerRegistry.put(playerId, updated);
            log.info("[HUMAN PLAYER SERVICE] Player {} spent {} tokens. Remaining: {}",
                    player.username(), amount, updated.influenceTokens());
            return true;
        }
        return false;
    }

    public void processHumanPlayerTick() {
        for (Map.Entry<String, HumanPlayer> entry : playerRegistry.entrySet()) {
            HumanPlayer p = entry.getValue();
            HumanPlayer refilled = new HumanPlayer(p.playerId(), p.username(), p.assignedCivilizationId(), p.influenceTokens() + 1);
            playerRegistry.put(entry.getKey(), refilled);
        }
        log.info("[HUMAN PLAYER TICK] Refilled influence tokens for {} active human players", playerRegistry.size());
    }
}
