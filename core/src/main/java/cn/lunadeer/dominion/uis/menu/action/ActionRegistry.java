package cn.lunadeer.dominion.uis.menu.action;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Stores the explicit whitelist of action types available to menu files.
 */
public final class ActionRegistry {

    private final Map<String, RegisteredAction> actions = new HashMap<>();

    /**
     * Registers one action type and rejects accidental duplicate ownership.
     */
    public void register(String type, ActionHandler handler) {
        register(type, handler, action -> {
        });
    }

    /**
     * Registers one action type with its load-time argument validator.
     */
    public void register(String type, ActionHandler handler, ActionValidator validator) {
        String normalizedType = normalize(type);
        RegisteredAction registered = new RegisteredAction(
                Objects.requireNonNull(handler, "handler"),
                Objects.requireNonNull(validator, "validator")
        );
        if (actions.putIfAbsent(normalizedType, registered) != null) {
            throw new IllegalStateException("Action type already registered: " + normalizedType);
        }
    }

    /**
     * Resolves a previously registered action handler.
     */
    public ActionHandler resolve(String type) {
        RegisteredAction action = actions.get(normalize(type));
        return action == null ? null : action.handler();
    }

    /**
     * Checks whether a menu action type belongs to the whitelist.
     */
    public boolean contains(String type) {
        return actions.containsKey(normalize(type));
    }

    /**
     * Applies the registered load-time validator to one parsed action.
     */
    public void validate(ActionSpec action) {
        RegisteredAction registered = actions.get(normalize(action.type()));
        if (registered == null) {
            throw new IllegalArgumentException("Unknown action type '" + action.type() + "' at "
                    + action.configPath());
        }
        registered.validator().validate(action);
    }

    private String normalize(String type) {
        return type.trim().toLowerCase(Locale.ROOT);
    }

    private record RegisteredAction(ActionHandler handler, ActionValidator validator) {
    }
}
