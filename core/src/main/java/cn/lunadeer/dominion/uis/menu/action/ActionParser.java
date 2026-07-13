package cn.lunadeer.dominion.uis.menu.action;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses the intentionally small string action syntax used by UI YAML files.
 */
public final class ActionParser {

    private static final int MAX_ACTIONS = 32;
    private static final int MAX_ACTION_LENGTH = 4096;

    /**
     * Parses one action using the first colon as the type separator.
     */
    public ActionSpec parse(String rawAction, String configPath) {
        if (rawAction == null || rawAction.isBlank()) {
            throw new IllegalArgumentException("Action cannot be empty at " + configPath);
        }
        if (rawAction.length() > MAX_ACTION_LENGTH) {
            throw new IllegalArgumentException("Action exceeds " + MAX_ACTION_LENGTH + " characters at " + configPath);
        }
        String value = rawAction.trim();
        int separator = value.indexOf(':');
        if (separator < 0) {
            return new ActionSpec(value, "", configPath);
        }
        return new ActionSpec(value.substring(0, separator), value.substring(separator + 1), configPath);
    }

    /**
     * Parses an ordered action list and retains each YAML list index in errors.
     */
    public List<ActionSpec> parseAll(List<String> rawActions, String configPath) {
        if (rawActions.size() > MAX_ACTIONS) {
            throw new IllegalArgumentException("Action list exceeds " + MAX_ACTIONS + " entries at " + configPath);
        }
        List<ActionSpec> actions = new ArrayList<>();
        for (int index = 0; index < rawActions.size(); index++) {
            actions.add(parse(rawActions.get(index), configPath + "[" + index + "]"));
        }
        return List.copyOf(actions);
    }
}
