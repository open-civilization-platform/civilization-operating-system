package io.github.opencivilizationplatform.modules.social;

import io.github.opencivilizationplatform.modules.social.application.HumanPlayerService;
import io.github.opencivilizationplatform.modules.social.domain.HumanPlayer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class HumanPlayerServiceTest {

    private HumanPlayerService service;

    @BeforeEach
    void setUp() {
        service = new HumanPlayerService();
    }

    @Test
    void shouldRegisterAndRetrievePlayer() {
        HumanPlayer player = new HumanPlayer("player-1", "Alice", "civ-alpha", 100);
        service.registerPlayer(player);

        Optional<HumanPlayer> retrieved = service.getPlayer("player-1");
        assertTrue(retrieved.isPresent());
        assertEquals("Alice", retrieved.get().username());
        assertEquals(100, retrieved.get().influenceTokens());
    }

    @Test
    void shouldSpendInfluenceTokensIfSufficient() {
        HumanPlayer player = new HumanPlayer("player-1", "Alice", "civ-alpha", 50);
        service.registerPlayer(player);

        boolean success = service.spendInfluenceTokens("player-1", 20);
        assertTrue(success);
        assertEquals(30, service.getPlayer("player-1").get().influenceTokens());
    }

    @Test
    void shouldFailSpendingInfluenceTokensIfInsufficient() {
        HumanPlayer player = new HumanPlayer("player-1", "Alice", "civ-alpha", 10);
        service.registerPlayer(player);

        boolean success = service.spendInfluenceTokens("player-1", 20);
        assertFalse(success);
        assertEquals(10, service.getPlayer("player-1").get().influenceTokens());
    }

    @Test
    void shouldProcessHumanPlayerTickRefill() {
        HumanPlayer player = new HumanPlayer("player-1", "Alice", "civ-alpha", 5);
        service.registerPlayer(player);

        service.processHumanPlayerTick();
        assertEquals(6, service.getPlayer("player-1").get().influenceTokens());
    }
}
