package cn.lunadeer.dominion.uis.menu.config;

import cn.lunadeer.dominion.uis.menu.action.ActionSpec;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Stores language-independent action groups for one menu id.
 */
public record SharedMenuDefinition(
        String menuId,
        Set<String> requiredRouteArguments,
        Set<String> optionalRouteArguments,
        Map<String, String> dataSources,
        Map<String, List<ActionSpec>> actionGroups
) {

    /**
     * Freezes nested action group lists before the definition enters the registry.
     */
    public SharedMenuDefinition {
        requiredRouteArguments = Set.copyOf(requiredRouteArguments);
        optionalRouteArguments = Set.copyOf(optionalRouteArguments);
        dataSources = Map.copyOf(dataSources);
        actionGroups = actionGroups.entrySet().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
                Map.Entry::getKey,
                entry -> List.copyOf(entry.getValue())
        ));
    }
}
