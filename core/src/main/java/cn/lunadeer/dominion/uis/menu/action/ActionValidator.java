package cn.lunadeer.dominion.uis.menu.action;

/**
 * Validates one parsed action during menu loading before callbacks can reference it.
 */
@FunctionalInterface
public interface ActionValidator {

    /**
     * Rejects malformed action arguments with a configuration-path-aware exception.
     */
    void validate(ActionSpec action);
}
