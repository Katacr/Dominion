package cn.lunadeer.dominion.uis.menu.session;

import cn.lunadeer.dominion.uis.menu.action.ActionSpec;
import cn.lunadeer.dominion.uis.menu.input.InputSchema;
import cn.lunadeer.dominion.uis.menu.route.MenuRoute;
import cn.lunadeer.dominion.uis.menu.route.UiSurface;
import cn.lunadeer.dominion.utils.scheduler.Scheduler;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stores short-lived, player-bound callbacks for configured UI components.
 */
public final class UiCallbackSessionManager implements Listener {

    private static final Duration CALLBACK_LIFETIME = Duration.ofMinutes(5);
    private static final String CALLBACK_COMMAND = "/dominion ui_callback ";

    private final Map<String, RegisteredCallback> callbacks = new ConcurrentHashMap<>();
    private final Map<UUID, Set<String>> playerTokens = new ConcurrentHashMap<>();
    private final Duration callbackLifetime;
    private final Clock clock;
    private volatile long activeRevision;
    private boolean started;

    /**
     * Registers player lifecycle cleanup and periodic expiration cleanup.
     */
    public UiCallbackSessionManager(JavaPlugin plugin) {
        this();
        start(plugin);
    }

    /**
     * Creates an unstarted session store so menu validation can finish before Bukkit hooks are installed.
     */
    public UiCallbackSessionManager() {
        this(CALLBACK_LIFETIME, Clock.systemUTC());
    }

    /**
     * Creates a scheduler-free session store with an explicit clock and callback lifetime.
     */
    UiCallbackSessionManager(Duration callbackLifetime, Clock clock) {
        this.callbackLifetime = callbackLifetime;
        this.clock = clock;
    }

    /**
     * Installs player lifecycle hooks after configured menu initialization succeeds.
     */
    public synchronized void start(JavaPlugin plugin) {
        if (started) {
            throw new IllegalStateException("UI callback sessions are already started");
        }
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        Scheduler.runTaskRepeat(this::removeExpired, 20L * 60L, 20L * 60L);
        started = true;
    }

    /**
     * Activates a successfully loaded menu revision and invalidates all older callbacks.
     */
    public synchronized void activateRevision(long revision) {
        activeRevision = revision;
        clear();
    }

    /**
     * Invalidates the player's previous configured menu before rendering a new one.
     */
    public void beginSession(Player player) {
        invalidate(player.getUniqueId());
    }

    /**
     * Invalidates configured callbacks before a transitional action opens a legacy UI.
     */
    public void invalidateForTransition(Player player) {
        invalidate(player.getUniqueId());
    }

    /**
     * Registers one action group callback and returns its fixed command transport.
     */
    public String register(Player player,
                           MenuRoute route,
                           long menuRevision,
                           String actionGroupId,
                           Map<String, List<ActionSpec>> actionGroups,
                           Map<String, String> variables,
                           Map<String, String> trustedArguments) {
        return register(player, route, menuRevision, actionGroupId, actionGroups, variables, trustedArguments,
                UiSurface.TUI);
    }

    /**
     * Registers one action group callback with an explicit result surface and returns its TUI command transport.
     */
    public String register(Player player,
                           MenuRoute route,
                           long menuRevision,
                           String actionGroupId,
                           Map<String, List<ActionSpec>> actionGroups,
                           Map<String, String> variables,
                           Map<String, String> trustedArguments,
                           UiSurface surface) {
        if (!actionGroups.containsKey(actionGroupId)) {
            throw new IllegalArgumentException("Unknown action group '" + actionGroupId + "' in menu " + route.menuId());
        }
        String token = registerActionsToken(player, route, menuRevision, actionGroupId, actionGroups.get(actionGroupId),
                actionGroups, variables, trustedArguments, surface);
        return commandFor(token);
    }

    /**
     * Registers a resolved inline or shared action sequence using one callback transport.
     */
    public synchronized String registerActions(Player player,
                                  MenuRoute route,
                                  long menuRevision,
                                  String actionId,
                                  List<ActionSpec> actions,
                                  Map<String, List<ActionSpec>> actionGroups,
                                  Map<String, String> variables,
                                  Map<String, String> trustedArguments) {
        return commandFor(registerActionsToken(player, route, menuRevision, actionId, actions, actionGroups,
                variables, trustedArguments, UiSurface.TUI));
    }

    /**
     * Registers a callback and returns only its opaque transport-neutral token.
     */
    public synchronized String registerActionsToken(Player player,
                                                     MenuRoute route,
                                                     long menuRevision,
                                                     String actionId,
                                                     List<ActionSpec> actions,
                                                     Map<String, List<ActionSpec>> actionGroups,
                                                     Map<String, String> variables,
                                                     Map<String, String> trustedArguments,
                                                     UiSurface surface) {
        return registerActionsToken(player, route, menuRevision, actionId, actions, actionGroups, variables,
                trustedArguments, surface, null);
    }

    /**
     * Registers a callback carrying an optional renderer-neutral input schema.
     */
    public synchronized String registerActionsToken(Player player,
                                                     MenuRoute route,
                                                     long menuRevision,
                                                     String actionId,
                                                     List<ActionSpec> actions,
                                                     Map<String, List<ActionSpec>> actionGroups,
                                                     Map<String, String> variables,
                                                     Map<String, String> trustedArguments,
                                                     UiSurface surface,
                                                     InputSchema inputSchema) {
        if (menuRevision != activeRevision) {
            throw new StaleMenuRevisionException();
        }
        if (actions.isEmpty()) {
            throw new IllegalArgumentException("Callback actions cannot be empty for '" + actionId + "' in menu "
                    + route.menuId());
        }
        String token = UUID.randomUUID().toString().replace("-", "");
        RegisteredCallback callback = new RegisteredCallback(
                player.getUniqueId(),
                surface,
                route,
                menuRevision,
                actionId,
                actions,
                actionGroups,
                variables,
                trustedArguments,
                inputSchema,
                clock.instant().plus(callbackLifetime)
        );
        callbacks.put(token, callback);
        playerTokens.computeIfAbsent(player.getUniqueId(), ignored -> ConcurrentHashMap.newKeySet()).add(token);
        return token;
    }

    /**
     * Wraps an opaque callback token in the fixed command used by clickable text transports.
     */
    public String commandFor(String token) {
        return CALLBACK_COMMAND + token;
    }

    /**
     * Consumes a token once after validating player ownership and expiration.
     */
    public Optional<RegisteredCallback> consume(Player player, String token) {
        RegisteredCallback callback = callbacks.get(token);
        if (callback == null) {
            return Optional.empty();
        }
        if (!callback.playerId().equals(player.getUniqueId())) {
            return Optional.empty();
        }
        if (!callbacks.remove(token, callback)) {
            return Optional.empty();
        }
        removePlayerToken(callback.playerId(), token);
        if (callback.menuRevision() != activeRevision || !callback.expiresAt().isAfter(clock.instant())) {
            return Optional.empty();
        }
        return Optional.of(callback);
    }

    /**
     * Invalidates only callbacks owned by a closing holder without affecting a newer player session.
     */
    public void invalidateTokens(Collection<String> tokens) {
        for (String token : tokens) {
            RegisteredCallback callback = callbacks.remove(token);
            if (callback != null) {
                removePlayerToken(callback.playerId(), token);
            }
        }
    }

    /**
     * Clears every configured UI callback, normally during plugin disable.
     */
    public void clear() {
        callbacks.clear();
        playerTokens.clear();
    }

    /**
     * Removes callbacks immediately when their owning player disconnects.
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        invalidate(event.getPlayer().getUniqueId());
    }

    private void invalidate(UUID playerId) {
        Set<String> tokens = playerTokens.remove(playerId);
        if (tokens != null) {
            tokens.forEach(callbacks::remove);
        }
    }

    private void removeExpired() {
        Instant now = clock.instant();
        callbacks.forEach((token, callback) -> {
            if (!callback.expiresAt().isAfter(now) && callbacks.remove(token, callback)) {
                removePlayerToken(callback.playerId(), token);
            }
        });
    }

    private void removePlayerToken(UUID playerId, String token) {
        Set<String> tokens = playerTokens.get(playerId);
        if (tokens == null) {
            return;
        }
        tokens.remove(token);
        if (tokens.isEmpty()) {
            playerTokens.remove(playerId, tokens);
        }
    }

    /**
     * Stores the trusted context restored after a client callback command.
     */
    public record RegisteredCallback(
            UUID playerId,
            UiSurface surface,
            MenuRoute route,
            long menuRevision,
            String actionId,
            List<ActionSpec> actions,
            Map<String, List<ActionSpec>> actionGroups,
            Map<String, String> variables,
            Map<String, String> trustedArguments,
            InputSchema inputSchema,
            Instant expiresAt
    ) {

        /**
         * Freezes nested callback state before exposing it to asynchronous actions.
         */
        public RegisteredCallback {
            surface = java.util.Objects.requireNonNull(surface, "surface");
            actions = List.copyOf(actions);
            actionGroups = actionGroups.entrySet().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
                    Map.Entry::getKey,
                    entry -> List.copyOf(entry.getValue())
            ));
            variables = Map.copyOf(variables);
            trustedArguments = Map.copyOf(trustedArguments);
        }

        /**
         * Returns the menu id retained for concise callback diagnostics.
         */
        public String menuId() {
            return route.menuId();
        }
    }
}
