package cn.lunadeer.dominion.uis.menu.dialog;

import cn.lunadeer.dominion.uis.menu.action.ActionParser;
import cn.lunadeer.dominion.uis.menu.action.ActionRegistry;
import cn.lunadeer.dominion.uis.menu.action.ActionSpec;
import cn.lunadeer.dominion.uis.menu.config.SharedMenuDefinition;
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
 * Loads the bounded KaMenu-compatible syntax into platform-neutral Dialog definitions.
 */
public final class DialogMenuLoader {

    private static final Pattern ID = Pattern.compile("[a-z0-9][a-z0-9_.-]{0,63}");
    private static final Pattern INPUT_NAME = Pattern.compile("[a-z0-9][a-z0-9_]{0,63}");
    private static final Pattern PERMISSION = Pattern.compile("[a-zA-Z0-9_.-]{1,128}");
    private static final int MAX_TEXT = 4096;

    private final ActionParser actionParser;
    private final ActionRegistry actionRegistry;

    /**
     * Creates a loader backed by the same action whitelist as TUI and CUI.
     */
    public DialogMenuLoader(ActionParser actionParser, ActionRegistry actionRegistry) {
        this.actionParser = actionParser;
        this.actionRegistry = actionRegistry;
    }

    /**
     * Parses and validates one localized Dialog menu.
     */
    public DialogMenuDefinition load(String menuId,
                                     String locale,
                                     YamlConfiguration yaml,
                                     SharedMenuDefinition shared) {
        String path = "uis/" + locale + "/dialog_menu/" + menuId + ".yml";
        if (yaml.getInt("schema-version", -1) != 1) {
            throw new IllegalArgumentException("Unsupported Dialog schema-version at " + path);
        }
        String title = requiredText(yaml.getString("Title", ""), path + ".Title");
        ConfigurationSection settings = yaml.getConfigurationSection("Settings");
        boolean canEscape = settings == null || settings.getBoolean("can_escape", true);
        boolean pause = settings != null && settings.getBoolean("pause", false);
        DialogAfterAction afterAction = parseAfterAction(
                settings == null ? "NONE" : settings.getString("after_action", "NONE"), path);
        if (afterAction == DialogAfterAction.WAIT_FOR_RESPONSE) {
            throw new IllegalArgumentException("Dialog after_action WAIT_FOR_RESPONSE is not supported at "
                    + path + ".Settings");
        }
        List<DialogBodyDefinition> body = loadBody(yaml.getConfigurationSection("Body"), path);
        List<DialogInputDefinition> inputs = loadInputs(yaml.getConfigurationSection("Inputs"), path);
        DialogBottomDefinition bottom = loadBottom(yaml.getConfigurationSection("Bottom"), shared, path);
        DialogDynamicDefinition dynamic = loadDynamic(yaml.getConfigurationSection("Dynamic"), shared, path);
        if (dynamic != null && bottom.type() != DialogBottomDefinition.Type.MULTI) {
            throw new IllegalArgumentException("Dynamic Dialog requires a multi Bottom at " + path);
        }
        return new DialogMenuDefinition(menuId, locale, title, canEscape, pause, afterAction,
                body, inputs, bottom, dynamic, readVariables(yaml.getConfigurationSection("Variables"), path),
                shared, null);
    }

    /**
     * Reads one provider-backed repeated button group shared by both platform adapters.
     */
    private DialogDynamicDefinition loadDynamic(ConfigurationSection section,
                                                SharedMenuDefinition shared,
                                                String path) {
        if (section == null) {
            return null;
        }
        String source = section.getString("source", "").trim();
        if (!shared.dataSources().containsKey(source)) {
            throw new IllegalArgumentException("Unknown Dialog dynamic source '" + source + "' at " + path);
        }
        int pageSize = bounded(section.getInt("page-size", 8), 1, 32, path + ".Dynamic.page-size");
        String layout = requiredText(section.getString("layout", ""), path + ".Dynamic.layout");
        int width = bounded(section.getInt("width", 400), 1, 1024, path + ".Dynamic.width");
        ConfigurationSection buttonSection = requireSection(section, "buttons", path + ".Dynamic.buttons");
        if (buttonSection.getKeys(false).isEmpty() || buttonSection.getKeys(false).size() > 4) {
            throw new IllegalArgumentException("Dialog Dynamic requires 1-4 button templates at " + path);
        }
        List<DialogButtonDefinition> buttons = new ArrayList<>();
        for (String id : buttonSection.getKeys(false)) {
            validateId(id, path + ".Dynamic.buttons");
            buttons.add(loadButton(id, requireSection(buttonSection, id,
                    path + ".Dynamic.buttons." + id), shared, path + ".Dynamic.buttons." + id));
        }
        return new DialogDynamicDefinition(
                source, pageSize, section.getString("empty", ""), layout, width, buttons);
    }

    /**
     * Reads ordered plain-message and item components supported by both platform adapters.
     */
    private List<DialogBodyDefinition> loadBody(ConfigurationSection section, String path) {
        if (section == null) {
            return List.of();
        }
        List<DialogBodyDefinition> body = new ArrayList<>();
        for (String id : section.getKeys(false)) {
            validateId(id, path + ".Body");
            ConfigurationSection component = requireSection(section, id, path + ".Body." + id);
            String type = component.getString("type", "message").toLowerCase(Locale.ROOT);
            if (type.equals("message")) {
                String text = readText(component, "text", path + ".Body." + id + ".text");
                int width = bounded(component.getInt("width", 200), 1, 1024, path + ".Body." + id + ".width");
                body.add(new DialogBodyDefinition(id, text, width));
                continue;
            }
            if (!type.equals("item")) {
                throw new IllegalArgumentException("Unknown Dialog Body type '" + type + "' at "
                        + path + ".Body." + id);
            }
            String materialName = component.getString("material", "").trim();
            Material material = Material.matchMaterial(materialName);
            if (material == null || !material.isItem()) {
                throw new IllegalArgumentException("Invalid Dialog item material '" + materialName + "' at "
                        + path + ".Body." + id);
            }
            int amount = bounded(component.getInt("amount", 1), 1, 99, path + ".Body." + id + ".amount");
            int width = bounded(component.getInt("width", 16), 1, 256, path + ".Body." + id + ".width");
            int height = bounded(component.getInt("height", 16), 1, 256, path + ".Body." + id + ".height");
            body.add(DialogBodyDefinition.item(id, material.getKey().toString(), amount,
                    readOptionalText(component, "description", path + ".Body." + id + ".description"),
                    width, height, component.getBoolean("show_overlays", true),
                    component.getBoolean("show_tooltip", true)));
        }
        return List.copyOf(body);
    }

    /**
     * Reads KaMenu input, slider, dropdown, and checkbox controls.
     */
    private List<DialogInputDefinition> loadInputs(ConfigurationSection section, String path) {
        if (section == null) {
            return List.of();
        }
        if (section.getKeys(false).size() > 16) {
            throw new IllegalArgumentException("Dialog Inputs exceeds 16 fields at " + path);
        }
        List<DialogInputDefinition> inputs = new ArrayList<>();
        for (String key : section.getKeys(false)) {
            validateInputName(key, path + ".Inputs");
            ConfigurationSection input = requireSection(section, key, path + ".Inputs." + key);
            inputs.add(loadInput(key, input, path + ".Inputs." + key));
        }
        return List.copyOf(inputs);
    }

    /**
     * Parses one reusable native input definition from a menu or secondary prompt.
     */
    private DialogInputDefinition loadInput(String key, ConfigurationSection input, String path) {
        DialogInputType type;
            try {
                type = DialogInputType.valueOf(input.getString("type", "input").toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unknown Dialog input type at " + path, exception);
            }
        String label = requiredText(input.getString("text", ""), path + ".text");
            int width = bounded(input.getInt("width", type == DialogInputType.INPUT ? 250 : 200),
                1, 1024, path + ".width");
            int minLength = type == DialogInputType.INPUT ? input.getInt("min_length", 0) : 1;
            int maxLength = type == DialogInputType.INPUT ? input.getInt("max_length", 256) : 1024;
            if (minLength < 0 || maxLength < Math.max(1, minLength) || maxLength > 1024) {
            throw new IllegalArgumentException("Invalid Dialog input length at " + path);
            }
            float minimum = (float) input.getDouble("min", 0.0);
            float maximum = (float) input.getDouble("max", 10.0);
            Float step = input.contains("step") ? (float) input.getDouble("step") : null;
            if (type == DialogInputType.SLIDER && (minimum >= maximum || step != null && step <= 0)) {
            throw new IllegalArgumentException("Invalid Dialog slider range at " + path);
            }
            String initial = input.getString("default", "");
            if (type == DialogInputType.SLIDER && !initial.isBlank()) {
                try {
                    float initialValue = Float.parseFloat(initial);
                    if (initialValue < minimum || initialValue > maximum) {
                    throw new IllegalArgumentException("Dialog slider default is outside its range at " + path);
                    }
                } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("Dialog slider default must be numeric at " + path, exception);
                }
            }
            List<DialogOptionDefinition> options = type == DialogInputType.DROPDOWN
                    ? loadOptions(input.getStringList("options"), input.getString("default_id", ""),
                path) : List.of();
        return new DialogInputDefinition(
                key, type, label, width, !input.getBoolean("hide_text", false),
                initial, minLength, maxLength, input.getString("pattern", ""),
                minimum, maximum, step, input.getString("format", "options.generic_value"),
                input.getString("on_true", "true"), input.getString("on_false", "false"), options);
    }

    /**
     * Parses stable dropdown IDs using KaMenu's `id => display` syntax.
     */
    private List<DialogOptionDefinition> loadOptions(List<String> raw, String defaultId, String path) {
        if (raw.isEmpty()) {
            throw new IllegalArgumentException("Dialog dropdown requires options at " + path);
        }
        List<DialogOptionDefinition> options = new ArrayList<>();
        Set<String> ids = new java.util.HashSet<>();
        for (int index = 0; index < raw.size(); index++) {
            String[] parts = raw.get(index).split("\\s*=>\\s*", 2);
            String id = parts[0].trim();
            validateId(id, path + ".options");
            if (!ids.add(id)) {
                throw new IllegalArgumentException("Duplicate Dialog option '" + id + "' at " + path);
            }
            String display = requiredText(parts.length == 2 ? parts[1] : id, path + ".options");
            options.add(new DialogOptionDefinition(id, display,
                    defaultId.isBlank() ? index == 0 : id.equals(defaultId)));
        }
        if (!defaultId.isBlank() && options.stream().noneMatch(option -> option.id().equals(defaultId))) {
            throw new IllegalArgumentException("Unknown Dialog default option '" + defaultId + "' at " + path);
        }
        return List.copyOf(options);
    }

    /**
     * Reads the native footer layout while preserving configured button order.
     */
    private DialogBottomDefinition loadBottom(ConfigurationSection section,
                                              SharedMenuDefinition shared,
                                              String path) {
        if (section == null) {
            throw new IllegalArgumentException("Dialog Bottom is required at " + path);
        }
        DialogBottomDefinition.Type type;
        try {
            type = DialogBottomDefinition.Type.valueOf(section.getString("type", "notice").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unknown Dialog Bottom type at " + path, exception);
        }
        List<DialogButtonDefinition> buttons = new ArrayList<>();
        DialogButtonDefinition exit = null;
        if (type == DialogBottomDefinition.Type.NOTICE) {
            buttons.add(loadButton("confirm", requireSection(section, "confirm", path + ".Bottom.confirm"),
                    shared, path + ".Bottom.confirm"));
        } else if (type == DialogBottomDefinition.Type.CONFIRMATION) {
            buttons.add(loadButton("confirm", requireSection(section, "confirm", path + ".Bottom.confirm"),
                    shared, path + ".Bottom.confirm"));
            buttons.add(loadButton("deny", requireSection(section, "deny", path + ".Bottom.deny"),
                    shared, path + ".Bottom.deny"));
        } else {
            ConfigurationSection buttonSection = requireSection(section, "buttons", path + ".Bottom.buttons");
            if (buttonSection.getKeys(false).isEmpty() || buttonSection.getKeys(false).size() > 64) {
                throw new IllegalArgumentException("Dialog multi Bottom requires 1-64 buttons at " + path);
            }
            for (String id : buttonSection.getKeys(false)) {
                validateId(id, path + ".Bottom.buttons");
                buttons.add(loadButton(id, requireSection(buttonSection, id,
                        path + ".Bottom.buttons." + id), shared, path + ".Bottom.buttons." + id));
            }
            if (section.isConfigurationSection("exit")) {
                exit = loadButton("exit", section.getConfigurationSection("exit"), shared, path + ".Bottom.exit");
            }
        }
        int columns = bounded(section.getInt("columns", 1), 1, 16, path + ".Bottom.columns");
        return new DialogBottomDefinition(type, buttons, exit, columns);
    }

    /**
     * Resolves either one shared action group or one local inline action sequence.
     */
    private DialogButtonDefinition loadButton(String id,
                                              ConfigurationSection section,
                                              SharedMenuDefinition shared,
                                              String path) {
        String text = requiredText(section.getString("text", ""), path + ".text");
        String actionGroup = section.getString("action-group", "").trim();
        boolean inline = section.contains("actions");
        if (inline == !actionGroup.isEmpty()) {
            throw new IllegalArgumentException("Dialog button requires exactly one of action-group or actions at " + path);
        }
        List<ActionSpec> actions;
        String actionId;
        if (inline) {
            actions = actionParser.parseAll(section.getStringList("actions"), path + ".actions");
            if (actions.isEmpty()) {
                throw new IllegalArgumentException("Dialog inline actions cannot be empty at " + path);
            }
            actions.forEach(actionRegistry::validate);
            actionId = id;
        } else {
            actions = shared.actionGroups().get(actionGroup);
            if (actions == null) {
                throw new IllegalArgumentException("Unknown Dialog action group '" + actionGroup + "' at " + path);
            }
            actionId = actionGroup;
        }
        String permission = section.getString("permission", "").trim();
        if (!permission.isEmpty() && !PERMISSION.matcher(permission).matches()) {
            throw new IllegalArgumentException("Invalid Dialog permission at " + path);
        }
        DialogPromptDefinition prompt = loadPrompt(section, path);
        return new DialogButtonDefinition(id, text, readOptionalText(section, "tooltip", path + ".tooltip"),
                bounded(section.getInt("width", 150), 1, 1024, path + ".width"),
                permission, actionId, actions, Map.of(), Map.of(), prompt);
    }

    /**
     * Loads an optional secondary confirmation or input-capture Dialog for one button.
     */
    private DialogPromptDefinition loadPrompt(ConfigurationSection button, String path) {
        boolean confirmation = button.isConfigurationSection("confirmation");
        boolean inputCapture = button.isConfigurationSection("input-capture");
        if (confirmation && inputCapture) {
            throw new IllegalArgumentException("Dialog button cannot define both confirmation and input-capture at "
                    + path);
        }
        if (!confirmation && !inputCapture) {
            return null;
        }
        String key = confirmation ? "confirmation" : "input-capture";
        ConfigurationSection section = requireSection(button, key, path + "." + key);
        String promptPath = path + "." + key;
        String title = requiredText(section.getString("title", ""), promptPath + ".title");
        String body = readOptionalText(section, "body", promptPath + ".body");
        DialogInputDefinition input = null;
        if (inputCapture) {
            ConfigurationSection inputSection = requireSection(section, "input", promptPath + ".input");
            String inputKey = inputSection.getString("key", "value").trim();
            validateInputName(inputKey, promptPath + ".input.key");
            input = loadInput(inputKey, inputSection, promptPath + ".input");
        }
        DialogPromptButtonDefinition confirm = loadPromptButton(
                requireSection(section, "confirm", promptPath + ".confirm"), promptPath + ".confirm");
        DialogPromptButtonDefinition deny = loadPromptButton(
                requireSection(section, "deny", promptPath + ".deny"), promptPath + ".deny");
        return new DialogPromptDefinition(
                confirmation ? DialogPromptDefinition.Type.CONFIRMATION : DialogPromptDefinition.Type.INPUT_CAPTURE,
                title, body, input, confirm, deny);
    }

    /**
     * Loads one secondary prompt button with KaMenu-compatible tooltip text.
     */
    private DialogPromptButtonDefinition loadPromptButton(ConfigurationSection section, String path) {
        return new DialogPromptButtonDefinition(
                requiredText(section.getString("text", ""), path + ".text"),
                readOptionalText(section, "tooltip", path + ".tooltip"),
                bounded(section.getInt("width", 150), 1, 1024, path + ".width"));
    }

    /**
     * Reads localized scalar variables using the shared namespaced key convention.
     */
    private Map<String, String> readVariables(ConfigurationSection section, String path) {
        if (section == null) {
            return Map.of();
        }
        Map<String, String> variables = new LinkedHashMap<>();
        collectVariables(section, "", variables, path);
        return Map.copyOf(variables);
    }

    /**
     * Flattens nested localized variables into namespaced keys.
     */
    private void collectVariables(ConfigurationSection section, String prefix,
                                  Map<String, String> output, String path) {
        for (String key : section.getKeys(false)) {
            String fullKey = prefix.isEmpty() ? key : prefix + "." + key;
            Object value = section.get(key);
            if (value instanceof ConfigurationSection child) {
                collectVariables(child, fullKey, output, path);
            } else if (value instanceof String text) {
                if (output.size() >= 256) {
                    throw new IllegalArgumentException("Dialog Variables exceeds 256 entries at " + path);
                }
                output.put(fullKey, requiredText(text, path + ".Variables." + fullKey));
            } else {
                throw new IllegalArgumentException("Dialog variable must be text at " + path + ".Variables." + fullKey);
            }
        }
    }

    /**
     * Joins KaMenu string-list text into one native multiline body value.
     */
    private String readText(ConfigurationSection section, String key, String path) {
        if (section.isList(key)) {
            return requiredText(String.join("\n", section.getStringList(key)), path);
        }
        return requiredText(section.getString(key, ""), path);
    }

    /**
     * Reads an optional scalar or string-list text field such as a button tooltip.
     */
    private String readOptionalText(ConfigurationSection section, String key, String path) {
        if (!section.contains(key)) {
            return "";
        }
        return readText(section, key, path);
    }

    /**
     * Applies the common non-empty bounded text contract.
     */
    private String requiredText(String value, String path) {
        if (value == null || value.isEmpty() || value.length() > MAX_TEXT) {
            throw new IllegalArgumentException("Dialog text must contain 1-" + MAX_TEXT + " characters at " + path);
        }
        return value;
    }

    /**
     * Resolves the configured client lifecycle enum without silent fallback.
     */
    private DialogAfterAction parseAfterAction(String value, String path) {
        try {
            return DialogAfterAction.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unknown Dialog after_action at " + path + ".Settings", exception);
        }
    }

    /**
     * Returns one required nested section with a config-path error.
     */
    private ConfigurationSection requireSection(ConfigurationSection parent, String key, String path) {
        ConfigurationSection section = parent.getConfigurationSection(key);
        if (section == null) {
            throw new IllegalArgumentException("Missing Dialog section at " + path);
        }
        return section;
    }

    /**
     * Validates bounded stable identifiers used by inputs, buttons, and components.
     */
    private void validateId(String id, String path) {
        if (!ID.matcher(id).matches()) {
            throw new IllegalArgumentException("Invalid Dialog id '" + id + "' at " + path);
        }
    }

    /**
     * Enforces the native Dialog input-name grammar before a platform adapter builds the form.
     */
    private void validateInputName(String name, String path) {
        if (!INPUT_NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("Invalid Dialog input name '" + name + "' at " + path);
        }
    }

    /**
     * Validates integer dimensions before they reach platform constructors.
     */
    private int bounded(int value, int minimum, int maximum, String path) {
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException("Dialog value must be between " + minimum + " and " + maximum
                    + " at " + path);
        }
        return value;
    }
}
