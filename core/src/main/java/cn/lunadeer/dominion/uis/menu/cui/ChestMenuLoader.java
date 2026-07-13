package cn.lunadeer.dominion.uis.menu.cui;

import cn.lunadeer.dominion.uis.menu.action.ActionParser;
import cn.lunadeer.dominion.uis.menu.action.ActionRegistry;
import cn.lunadeer.dominion.uis.menu.action.ActionSpec;
import cn.lunadeer.dominion.uis.menu.config.SharedMenuDefinition;
import cn.lunadeer.dominion.uis.menu.data.MenuDataRegistry;
import cn.lunadeer.dominion.uis.menu.route.MenuRoute;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Parses and validates localized chest menu YAML without opening Bukkit inventories.
 */
public final class ChestMenuLoader {

    private static final int SCHEMA_VERSION = 1;
    private static final int MAX_TITLE_LENGTH = 128;
    private static final int MAX_TEXT_LENGTH = 4096;
    private static final int MAX_LORE_LINES = 64;
    private static final int MAX_VARIABLES = 256;
    private static final Pattern ITEM_MODEL = Pattern.compile(
            "[a-z0-9_.-]+:[a-z0-9/._-]+");
    private static final Pattern PERMISSION = Pattern.compile("[a-zA-Z0-9_.-]{1,128}");

    private final ActionParser actionParser;
    private final ActionRegistry actionRegistry;
    private final MenuDataRegistry dataRegistry;

    /**
     * Creates a strict loader backed by the shared action and provider whitelists.
     */
    public ChestMenuLoader(ActionParser actionParser,
                           ActionRegistry actionRegistry,
                           MenuDataRegistry dataRegistry) {
        this.actionParser = actionParser;
        this.actionRegistry = actionRegistry;
        this.dataRegistry = dataRegistry;
    }

    /**
     * Loads one immutable chest definition from localized YAML and a shared menu contract.
     */
    public ChestMenuDefinition load(String menuId,
                                    String locale,
                                    YamlConfiguration yaml,
                                    SharedMenuDefinition shared) {
        String path = "uis/" + locale + "/chest_menu/" + menuId + ".yml";
        if (!shared.menuId().equals(menuId)) {
            throw new IllegalArgumentException("Shared menu id does not match chest menu at " + path);
        }
        validateSchema(yaml, path);
        rejectUnknownKeys(yaml, path, Set.of(
                "schema-version", "Title", "title", "Layout", "layout",
                "Buttons", "buttons", "Variables", "variables"));
        rejectAliasPair(yaml, path, "Title", "title");
        rejectAliasPair(yaml, path, "Layout", "layout");
        rejectAliasPair(yaml, path, "Buttons", "buttons");
        rejectAliasPair(yaml, path, "Variables", "variables");
        String title = readRequiredText(yaml, "Title", "title", path);
        if (title.length() > MAX_TITLE_LENGTH) {
            throw new IllegalArgumentException("Chest title exceeds " + MAX_TITLE_LENGTH + " characters at " + path);
        }
        List<String> layout = readStringList(yaml, "Layout", "layout");
        validateLayoutShape(layout, path);
        ConfigurationSection buttonSection = section(yaml, "Buttons", "buttons");
        if (buttonSection == null) {
            throw new IllegalArgumentException("Missing chest Buttons at " + path);
        }
        Map<Character, ChestButtonDefinition> buttons = loadButtons(buttonSection, shared, path);
        validateLayoutSymbols(layout, buttons, path);
        long dynamicButtons = buttons.values().stream().filter(ChestButtonDefinition::dynamic).count();
        if (dynamicButtons > 1) {
            throw new IllegalArgumentException("Only one dynamic source symbol is allowed at " + path);
        }
        return new ChestMenuDefinition(menuId, locale, title, layout, buttons,
                readVariables(yaml, path), shared);
    }

    /**
     * Parses every symbol definition and rejects duplicate character ownership.
     */
    private Map<Character, ChestButtonDefinition> loadButtons(ConfigurationSection section,
                                                               SharedMenuDefinition shared,
                                                               String path) {
        Map<Character, ChestButtonDefinition> buttons = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            char symbol = parseSymbol(key, path + ".Buttons");
            ConfigurationSection button = section.getConfigurationSection(key);
            if (button == null) {
                throw new IllegalArgumentException("Chest button must be a section at " + path + ".Buttons." + key);
            }
            ChestButtonDefinition definition = loadButton(symbol, button, shared,
                    path + ".Buttons." + key);
            if (buttons.putIfAbsent(symbol, definition) != null) {
                throw new IllegalArgumentException("Duplicate chest symbol '" + symbol + "' at " + path);
            }
        }
        return Map.copyOf(buttons);
    }

    /**
     * Resolves one static or dynamic symbol using only presentation fields and shared actions.
     */
    private ChestButtonDefinition loadButton(char symbol,
                                             ConfigurationSection section,
                                             SharedMenuDefinition shared,
                                             String path) {
        rejectUnknownKeys(section, path, Set.of(
                "source", "Display", "display", "Empty-Display", "empty-display", "Clicks", "clicks",
                "permission", "capability", "hidden-when-disabled"));
        rejectAliasPair(section, path, "Display", "display");
        rejectAliasPair(section, path, "Empty-Display", "empty-display");
        rejectAliasPair(section, path, "Clicks", "clicks");
        String source = section.getString("source", "").trim();
        if (!source.isEmpty()) {
            String provider = shared.dataSources().get(source);
            if (provider == null) {
                throw new IllegalArgumentException("Unknown shared data source '" + source + "' at " + path);
            }
            if (dataRegistry.resolve(provider) == null) {
                throw new IllegalArgumentException("Unknown menu data provider '" + provider + "' at " + path);
            }
        }
        ConfigurationSection displaySection = section(section, "Display", "display");
        if (displaySection == null) {
            throw new IllegalArgumentException("Missing chest Display at " + path);
        }
        ConfigurationSection emptyDisplaySection = section(section, "Empty-Display", "empty-display");
        if (emptyDisplaySection != null && source.isEmpty()) {
            throw new IllegalArgumentException("Empty-Display requires a dynamic source at " + path);
        }
        ConfigurationSection clicksSection = section(section, "Clicks", "clicks");
        Map<ChestClickType, ChestClickDefinition> clicks = clicksSection == null
                ? Map.of()
                : loadClicks(symbol, clicksSection, shared, path);
        String permission = section.getString("permission", "").trim();
        if (!permission.isEmpty() && !PERMISSION.matcher(permission).matches()) {
            throw new IllegalArgumentException("Invalid chest permission at " + path + ".permission");
        }
        String capability = section.getString("capability", "").trim();
        if (!capability.isEmpty()) {
            CuiCapability.fromConfigKey(capability);
        }
        ChestDisplayDefinition emptyDisplay = emptyDisplaySection == null
                ? null
                : loadDisplay(emptyDisplaySection, path + ".Empty-Display", false);
        return new ChestButtonDefinition(symbol, source, loadDisplay(displaySection, path, !source.isEmpty()),
                emptyDisplay, clicks, permission, capability, section.getBoolean("hidden-when-disabled", false));
    }

    /**
     * Validates ItemStack fields independently from the future renderer implementation.
     */
    private ChestDisplayDefinition loadDisplay(ConfigurationSection section,
                                               String path,
                                               boolean dynamic) {
        rejectUnknownKeys(section, path + ".Display", Set.of(
                "material", "name", "lore", "amount", "custom-data", "item-model", "glow"));
        String configuredMaterial = section.getString("material", "").trim();
        boolean entryMaterial = configuredMaterial.equals("{entry.material}");
        String materialName = entryMaterial ? configuredMaterial : configuredMaterial.toUpperCase(Locale.ROOT);
        if (materialName.isEmpty()
                || (entryMaterial && !dynamic)
                || (!entryMaterial && Material.matchMaterial(materialName) == null)) {
            throw new IllegalArgumentException("Unknown chest material '" + materialName + "' at " + path + ".Display");
        }
        if (!section.contains("name")) {
            throw new IllegalArgumentException("Chest Display requires name at " + path);
        }
        String name = section.getString("name", "");
        validateText(name, path + ".Display.name");
        List<String> lore = new ArrayList<>(section.getStringList("lore"));
        if (lore.size() > MAX_LORE_LINES) {
            throw new IllegalArgumentException("Chest lore exceeds " + MAX_LORE_LINES + " lines at " + path);
        }
        for (int index = 0; index < lore.size(); index++) {
            validateText(lore.get(index), path + ".Display.lore[" + index + "]");
        }
        int amount = section.getInt("amount", 1);
        if (amount < 1 || amount > 64) {
            throw new IllegalArgumentException("Chest item amount must be between 1 and 64 at " + path);
        }
        Integer customData = section.contains("custom-data") ? section.getInt("custom-data") : null;
        if (customData != null && customData < 0) {
            throw new IllegalArgumentException("Chest custom-data cannot be negative at " + path);
        }
        String itemModel = section.getString("item-model", "").trim();
        if (!itemModel.isEmpty() && !ITEM_MODEL.matcher(itemModel).matches()) {
            throw new IllegalArgumentException("Invalid chest item-model at " + path + ".Display.item-model");
        }
        return new ChestDisplayDefinition(materialName, name, lore, amount, customData, itemModel,
                section.getBoolean("glow", false));
    }

    /**
     * Resolves each configured click type into a validated immutable action sequence.
     */
    private Map<ChestClickType, ChestClickDefinition> loadClicks(char symbol,
                                                                 ConfigurationSection section,
                                                                 SharedMenuDefinition shared,
                                                                 String path) {
        Map<ChestClickType, ChestClickDefinition> clicks = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            ChestClickType type;
            try {
                type = ChestClickType.fromConfigKey(key);
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException(exception.getMessage() + " at " + path + ".Clicks", exception);
            }
            ConfigurationSection click = section.getConfigurationSection(key);
            if (click == null) {
                throw new IllegalArgumentException("Chest click must be a section at " + path + ".Clicks." + key);
            }
            rejectUnknownKeys(click, path + ".Clicks." + key, Set.of("action-group", "actions"));
            String actionGroup = click.getString("action-group", "").trim();
            boolean hasActions = click.contains("actions");
            if (hasActions == !actionGroup.isEmpty()) {
                throw new IllegalArgumentException("Chest click requires exactly one of action-group or actions at "
                        + path + ".Clicks." + key);
            }
            String actionId;
            List<ActionSpec> actions;
            if (!actionGroup.isEmpty()) {
                actions = shared.actionGroups().get(actionGroup);
                if (actions == null) {
                    throw new IllegalArgumentException("Unknown shared action group '" + actionGroup + "' at "
                            + path + ".Clicks." + key);
                }
                actionId = actionGroup;
            } else {
                List<String> rawActions = click.getStringList("actions");
                if (rawActions.isEmpty()) {
                    throw new IllegalArgumentException("Chest inline actions cannot be empty at "
                            + path + ".Clicks." + key);
                }
                actionId = "slot-" + symbol + "-" + type.configKey();
                actions = parseActions(rawActions, path + ".Clicks." + key + ".actions");
            }
            if (clicks.putIfAbsent(type, new ChestClickDefinition(actionId, actions)) != null) {
                throw new IllegalArgumentException("Duplicate chest click type at " + path + ".Clicks." + key);
            }
        }
        return Map.copyOf(clicks);
    }

    /**
     * Ensures every row maps exactly to a legal Bukkit chest width and height.
     */
    private void validateLayoutShape(List<String> layout, String path) {
        if (layout.isEmpty() || layout.size() > 6) {
            throw new IllegalArgumentException("Chest Layout must contain between 1 and 6 rows at " + path);
        }
        for (int row = 0; row < layout.size(); row++) {
            String value = layout.get(row);
            if (value.length() != 9) {
                throw new IllegalArgumentException("Chest Layout row must contain exactly 9 symbols at "
                        + path + ".Layout[" + row + "]");
            }
            for (int column = 0; column < value.length(); column++) {
                char symbol = value.charAt(column);
                if (symbol < 32 || symbol > 126) {
                    throw new IllegalArgumentException("Chest Layout only accepts ASCII symbols at "
                            + path + ".Layout[" + row + "]");
                }
            }
        }
    }

    /**
     * Rejects missing symbol definitions and unused button configuration.
     */
    private void validateLayoutSymbols(List<String> layout,
                                       Map<Character, ChestButtonDefinition> buttons,
                                       String path) {
        Set<Character> used = new java.util.LinkedHashSet<>();
        for (String row : layout) {
            for (char symbol : row.toCharArray()) {
                if (symbol != ' ') {
                    used.add(symbol);
                }
            }
        }
        for (char symbol : used) {
            if (!buttons.containsKey(symbol)) {
                throw new IllegalArgumentException("Missing chest button for symbol '" + symbol + "' at " + path);
            }
        }
        for (char symbol : buttons.keySet()) {
            if (!used.contains(symbol)) {
                throw new IllegalArgumentException("Unused chest button symbol '" + symbol + "' at " + path);
            }
        }
    }

    /**
     * Flattens optional localized variables using the same namespaced key convention as TUI.
     */
    private Map<String, String> readVariables(YamlConfiguration yaml, String path) {
        ConfigurationSection variablesSection = section(yaml, "Variables", "variables");
        if (variablesSection == null) {
            return Map.of();
        }
        Map<String, String> variables = new LinkedHashMap<>();
        collectVariables(variablesSection, "", variables, path);
        return Map.copyOf(variables);
    }

    /**
     * Recursively collects scalar variable values and rejects unbounded or invalid keys.
     */
    private void collectVariables(ConfigurationSection section,
                                  String prefix,
                                  Map<String, String> variables,
                                  String path) {
        for (String key : section.getKeys(false)) {
            String variableKey = prefix.isEmpty() ? key : prefix + "." + key;
            Object value = section.get(key);
            if (value instanceof ConfigurationSection child) {
                collectVariables(child, variableKey, variables, path);
                continue;
            }
            if (!(value instanceof String text) || !MenuRoute.isValidArgumentKey(variableKey)) {
                throw new IllegalArgumentException("Invalid chest variable at " + path + ".Variables." + variableKey);
            }
            if (variables.size() >= MAX_VARIABLES) {
                throw new IllegalArgumentException("Chest Variables exceeds " + MAX_VARIABLES + " entries at " + path);
            }
            validateText(text, path + ".Variables." + variableKey);
            variables.put(variableKey, text);
        }
    }

    /**
     * Parses inline actions through the existing global whitelist.
     */
    private List<ActionSpec> parseActions(List<String> rawActions, String path) {
        List<ActionSpec> actions = actionParser.parseAll(rawActions, path);
        actions.forEach(actionRegistry::validate);
        return actions;
    }

    /**
     * Reads a strict one-character ASCII symbol from a Buttons key.
     */
    private char parseSymbol(String key, String path) {
        if (key.length() != 1 || key.charAt(0) <= 32 || key.charAt(0) > 126) {
            throw new IllegalArgumentException("Chest button keys must be one non-space ASCII symbol at " + path);
        }
        return key.charAt(0);
    }

    /**
     * Rejects misspelled or unsupported fields instead of silently ignoring them.
     */
    private void rejectUnknownKeys(ConfigurationSection section, String path, Set<String> allowed) {
        for (String key : section.getKeys(false)) {
            if (!allowed.contains(key)) {
                throw new IllegalArgumentException("Unknown field '" + key + "' at " + path);
            }
        }
    }

    /**
     * Rejects simultaneous canonical and compatibility keys instead of choosing one silently.
     */
    private void rejectAliasPair(ConfigurationSection section,
                                 String path,
                                 String preferred,
                                 String fallback) {
        if (section.contains(preferred) && section.contains(fallback)) {
            throw new IllegalArgumentException("Duplicate fields '" + preferred + "' and '" + fallback
                    + "' at " + path);
        }
    }

    /**
     * Validates the version marker shared by all configured UI resources.
     */
    private void validateSchema(YamlConfiguration yaml, String path) {
        int version = yaml.getInt("schema-version", -1);
        if (version != SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported schema-version " + version + " at " + path);
        }
    }

    /**
     * Reads a required text value from the preferred or compatibility key.
     */
    private String readRequiredText(YamlConfiguration yaml, String preferred, String fallback, String path) {
        String value = yaml.contains(preferred) ? yaml.getString(preferred, "") : yaml.getString(fallback, "");
        if (value.isBlank()) {
            throw new IllegalArgumentException("Missing chest " + preferred + " at " + path);
        }
        return value;
    }

    /**
     * Reads a localized list from the canonical or compatibility key.
     */
    private List<String> readStringList(YamlConfiguration yaml, String preferred, String fallback) {
        List<String> values = yaml.getStringList(preferred);
        return values.isEmpty() ? new ArrayList<>(yaml.getStringList(fallback)) : new ArrayList<>(values);
    }

    /**
     * Resolves a nested section from canonical or compatibility casing.
     */
    private ConfigurationSection section(ConfigurationSection parent, String preferred, String fallback) {
        ConfigurationSection child = parent.getConfigurationSection(preferred);
        return child == null ? parent.getConfigurationSection(fallback) : child;
    }

    /**
     * Enforces the shared bounded text size used by UI configuration.
     */
    private void validateText(String value, String path) {
        if (value.length() > MAX_TEXT_LENGTH) {
            throw new IllegalArgumentException("Chest text exceeds " + MAX_TEXT_LENGTH + " characters at " + path);
        }
    }
}
