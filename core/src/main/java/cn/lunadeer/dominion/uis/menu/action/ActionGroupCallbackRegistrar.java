package cn.lunadeer.dominion.uis.menu.action;

/**
 * Creates a player-bound callback command for a named menu action group.
 */
@FunctionalInterface
public interface ActionGroupCallbackRegistrar {

    /**
     * Registers the action group and returns the command sent to the client.
     */
    String register(String actionGroupId);
}
