package cn.lunadeer.dominion.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.ChatColor;

import java.util.Map;

public class LegacyToMiniMessage {

    private static MiniMessage miniMessage = null;

    private static final Map<String, String> legacyToMiniMessageMap = Map.ofEntries(
            // Standard color codes
            Map.entry("§0", "<black>"),
            Map.entry("§1", "<dark_blue>"),
            Map.entry("§2", "<dark_green>"),
            Map.entry("§3", "<dark_aqua>"),
            Map.entry("§4", "<dark_red>"),
            Map.entry("§5", "<dark_purple>"),
            Map.entry("§6", "<gold>"),
            Map.entry("§7", "<gray>"),
            Map.entry("§8", "<dark_gray>"),
            Map.entry("§9", "<blue>"),
            Map.entry("§a", "<green>"),
            Map.entry("§b", "<aqua>"),
            Map.entry("§c", "<red>"),
            Map.entry("§d", "<light_purple>"),
            Map.entry("§e", "<yellow>"),
            Map.entry("§f", "<white>"),
            // Legacy formatting codes
            Map.entry("§l", "<bold>"),
            Map.entry("§m", "<strikethrough>"),
            Map.entry("§n", "<underlined>"),
            Map.entry("§o", "<italic>"),
            Map.entry("§r", "<reset>"),
            Map.entry("§k", "<obfuscated>")
    );

    public static Component parse(String legacyText) {
        initialize();
        return miniMessage.deserialize(convertLegacyCodes(legacyText));
    }

    /**
     * Parses configured formatting while inserting variables as literal unparsed text.
     */
    public static Component parseTemplate(String legacyText, Map<String, String> variables) {
        initialize();
        String template = legacyText;
        TagResolver.Builder resolvers = TagResolver.builder();
        int index = 0;
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            String tag = "dominion_var_" + index++;
            template = template.replace("{" + entry.getKey() + "}", "<" + tag + ">");
            resolvers.resolver(Placeholder.unparsed(tag, entry.getValue()));
        }
        return miniMessage.deserialize(convertLegacyCodes(template), resolvers.build());
    }

    private static void initialize() {
        if (miniMessage == null) {
            miniMessage = MiniMessage.miniMessage();
        }
    }

    private static String convertLegacyCodes(String legacyText) {
        String miniMessageText = ChatColor.translateAlternateColorCodes('&', legacyText);
        for (Map.Entry<String, String> entry : legacyToMiniMessageMap.entrySet()) {
            miniMessageText = miniMessageText.replace(entry.getKey(), entry.getValue());
        }
        return miniMessageText;
    }

}
