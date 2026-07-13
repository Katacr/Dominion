package cn.lunadeer.dominion.uis.menu.session;

import cn.lunadeer.dominion.uis.menu.action.ActionSpec;
import cn.lunadeer.dominion.uis.menu.route.MenuRoute;
import cn.lunadeer.dominion.uis.menu.route.UiSurface;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

class UiCallbackSessionManagerTest {

    private MutableClock clock;
    private UiCallbackSessionManager sessions;
    private Player owner;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        sessions = new UiCallbackSessionManager(Duration.ofMinutes(5), clock);
        sessions.activateRevision(1);
        owner = player(UUID.fromString("00000000-0000-0000-0000-000000000001"));
    }

    @Test
    void consumesOwnedCallbackOnlyOnce() {
        String token = register(owner, 1);

        assertTrue(sessions.consume(owner, token).isPresent());
        assertTrue(sessions.consume(owner, token).isEmpty());
    }

    @Test
    void separatesOpaqueTokenFromCommandTransport() {
        String token = register(owner, 1, UiSurface.CUI);

        assertEquals(32, token.length());
        assertFalse(token.contains(" "));
        assertEquals("/dominion ui_callback " + token, sessions.commandFor(token));
        assertEquals(UiSurface.CUI, sessions.consume(owner, token).orElseThrow().surface());
    }

    @Test
    void crossPlayerAttemptDoesNotConsumeOwnerCallback() {
        String token = register(owner, 1);
        Player other = player(UUID.fromString("00000000-0000-0000-0000-000000000002"));

        assertTrue(sessions.consume(other, token).isEmpty());
        assertTrue(sessions.consume(owner, token).isPresent());
    }

    @Test
    void rejectsExpiredCallback() {
        String token = register(owner, 1);
        clock.advance(Duration.ofMinutes(5));

        assertTrue(sessions.consume(owner, token).isEmpty());
    }

    @Test
    void revisionChangeRejectsOldCallbacksAndRegistrations() {
        String token = register(owner, 1);
        sessions.activateRevision(2);

        assertTrue(sessions.consume(owner, token).isEmpty());
        assertThrows(StaleMenuRevisionException.class, () -> register(owner, 1));
        String currentToken = register(owner, 2);
        assertEquals(2, sessions.consume(owner, currentToken).orElseThrow().menuRevision());
    }

    @Test
    void invalidatesOnlySpecifiedHolderTokens() {
        String first = register(owner, 1);
        String second = register(owner, 1);

        sessions.invalidateTokens(List.of(first));

        assertTrue(sessions.consume(owner, first).isEmpty());
        assertTrue(sessions.consume(owner, second).isPresent());
    }

    private String register(Player player, long revision) {
        return register(player, revision, UiSurface.TUI);
    }

    private String register(Player player, long revision, UiSurface surface) {
        return sessions.registerActionsToken(
                player,
                MenuRoute.of("test"),
                revision,
                "test-action",
                List.of(new ActionSpec("tell", "test", "test.actions[0]")),
                Map.of(),
                Map.of(),
                Map.of("resource.id", "trusted"),
                surface
        );
    }

    private Player player(UUID playerId) {
        return (Player) Proxy.newProxyInstance(
                Player.class.getClassLoader(),
                new Class<?>[]{Player.class},
                (proxy, method, arguments) -> method.getName().equals("getUniqueId") ? playerId : null
        );
    }

    private static final class MutableClock extends Clock {

        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
