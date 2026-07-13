package cn.lunadeer.dominion.uis.menu.tui;

import cn.lunadeer.dominion.api.dtos.PlayerDTO;
import cn.lunadeer.dominion.cache.CacheManager;
import cn.lunadeer.dominion.configuration.Configuration;
import cn.lunadeer.dominion.uis.menu.action.ActionParser;
import cn.lunadeer.dominion.uis.menu.action.ActionRegistry;
import cn.lunadeer.dominion.uis.menu.action.ActionSpec;
import cn.lunadeer.dominion.uis.menu.config.SharedMenuDefinition;
import cn.lunadeer.dominion.uis.menu.cui.ChestMenuDefinition;
import cn.lunadeer.dominion.uis.menu.cui.ChestMenuLoader;
import cn.lunadeer.dominion.uis.menu.data.MenuDataRegistry;
import cn.lunadeer.dominion.uis.menu.dialog.DialogMenuDefinition;
import cn.lunadeer.dominion.uis.menu.dialog.DialogMenuLoader;
import cn.lunadeer.dominion.uis.menu.route.MenuRoute;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Installs, validates, and caches localized TUI menu YAML files.
 */
public final class TuiMenuRepository {

    private static final int SCHEMA_VERSION = 1;
    private static final String FALLBACK_LOCALE = "en_US";
    private static final Pattern BUTTON_ID = Pattern.compile("[a-z0-9][a-z0-9_-]{0,63}");
    private static final Pattern LOCALE_ID = Pattern.compile("[A-Za-z]{2,8}[-_][A-Za-z0-9]{2,8}");
    private static final int MAX_LAYOUT_LINES = 64;
    private static final int MAX_LINE_LENGTH = 4096;
    private static final int MAX_BUTTONS = 128;
    private static final int MAX_BUTTONS_PER_LINE = 32;
    private static final int MAX_TEXT_LENGTH = 4096;
    private static final int MAX_VARIABLES = 256;

    private final JavaPlugin plugin;
    private final ActionParser actionParser;
    private final ActionRegistry actionRegistry;
    private final MenuDataRegistry dataRegistry;
    private volatile MenuSnapshot snapshot = new MenuSnapshot(
            Map.of(), Map.of(), Map.of(), Set.of(), Set.of(), Set.of(), Set.of(), 0);

    /**
     * Creates a repository using the shared action whitelist for load-time validation.
     */
    public TuiMenuRepository(JavaPlugin plugin,
                             ActionParser actionParser,
                             ActionRegistry actionRegistry,
                             MenuDataRegistry dataRegistry) {
        this.plugin = plugin;
        this.actionParser = actionParser;
        this.actionRegistry = actionRegistry;
        this.dataRegistry = dataRegistry;
    }

    /**
     * Installs missing defaults and atomically replaces all loaded definitions.
     */
    public synchronized void load() {
        YamlConfiguration manifest = loadEmbeddedYaml("uis/manifest.yml");
        validateSchema(manifest, "uis/manifest.yml");
        List<String> locales = manifest.getStringList("locales");
        List<String> menuIds = manifest.getStringList("text-menus");
        List<String> chestMenuIds = manifest.getStringList("chest-menus");
        List<String> dialogMenuIds = manifest.getStringList("dialog-menus");
        installDefaults(locales, menuIds, chestMenuIds, dialogMenuIds);

        Set<String> loadedLocales = discoverLocales(locales);
        Map<String, TuiMenuDefinition> loaded = new LinkedHashMap<>();
        Map<String, SharedMenuDefinition> sharedDefinitions = new LinkedHashMap<>();
        for (String menuId : menuIds) {
            validateMenuId(menuId);
            SharedMenuDefinition shared = loadShared(menuId);
            sharedDefinitions.put(menuId, shared);
            for (String locale : loadedLocales) {
                LocalizedYaml localized = loadLocalized(menuId, locale, "text_menu");
                loaded.put(localizedKey(locale, menuId),
                        loadTui(menuId, localized.locale(), localized.yaml(), shared));
            }
        }
        ChestMenuLoader chestLoader = new ChestMenuLoader(actionParser, actionRegistry, dataRegistry);
        Map<String, ChestMenuDefinition> loadedChestMenus = new LinkedHashMap<>();
        for (String menuId : chestMenuIds) {
            validateMenuId(menuId);
            SharedMenuDefinition shared = sharedDefinitions.computeIfAbsent(menuId, this::loadShared);
            for (String locale : loadedLocales) {
                LocalizedYaml localized = loadLocalized(menuId, locale, "chest_menu");
                loadedChestMenus.put(localizedKey(locale, menuId),
                        chestLoader.load(menuId, localized.locale(), localized.yaml(), shared));
            }
        }
        DialogMenuLoader dialogLoader = new DialogMenuLoader(actionParser, actionRegistry);
        Map<String, DialogMenuDefinition> loadedDialogMenus = new LinkedHashMap<>();
        for (String menuId : dialogMenuIds) {
            validateMenuId(menuId);
            SharedMenuDefinition shared = sharedDefinitions.computeIfAbsent(menuId, this::loadShared);
            for (String locale : loadedLocales) {
                LocalizedYaml localized = loadLocalized(menuId, locale, "dialog_menu");
                loadedDialogMenus.put(localizedKey(locale, menuId),
                        dialogLoader.load(menuId, localized.locale(), localized.yaml(), shared));
            }
        }
        snapshot = new MenuSnapshot(loaded, loadedChestMenus, loadedDialogMenus, loadedLocales,
                Set.copyOf(menuIds), Set.copyOf(chestMenuIds), Set.copyOf(dialogMenuIds), snapshot.revision() + 1);
    }

    /**
     * Returns a loaded menu or null when the id is not part of the current registry.
     */
    public TuiMenuDefinition find(String menuId) {
        return find(menuId, Configuration.language);
    }

    /**
     * Returns one text menu in the requested locale with configured and English fallback.
     */
    public TuiMenuDefinition find(String menuId, String locale) {
        return localized(snapshot.textMenus(), menuId, locale);
    }

    /**
     * Returns text menu ids available to commands and tab completion.
     */
    public Set<String> menuIds() {
        return snapshot.textMenuIds();
    }

    /**
     * Returns a loaded chest menu or null when no configured CUI exists for the route.
     */
    public ChestMenuDefinition findChest(String menuId) {
        return findChest(menuId, Configuration.language);
    }

    /**
     * Returns one chest menu in the requested locale with configured and English fallback.
     */
    public ChestMenuDefinition findChest(String menuId, String locale) {
        return localized(snapshot.chestMenus(), menuId, locale);
    }

    /**
     * Returns chest menu ids available in the current atomic registry snapshot.
     */
    public Set<String> chestMenuIds() {
        return snapshot.chestMenuIds();
    }

    /**
     * Returns a loaded native Dialog menu or null when no definition exists.
     */
    public DialogMenuDefinition findDialog(String menuId) {
        return findDialog(menuId, Configuration.language);
    }

    /**
     * Returns one Dialog menu in the requested locale with configured and English fallback.
     */
    public DialogMenuDefinition findDialog(String menuId, String locale) {
        return localized(snapshot.dialogMenus(), menuId, locale);
    }

    /**
     * Returns Dialog menu ids available to commands and platform adapters.
     */
    public Set<String> dialogMenuIds() {
        return snapshot.dialogMenuIds();
    }

    /**
     * Resolves the player's persisted menu locale against the currently loaded manifest.
     */
    public String localeFor(Player player) {
        PlayerDTO playerData = CacheManager.instance.getPlayer(player.getUniqueId());
        String requested = playerData == null ? "NONE" : playerData.getLanguage();
        if (!"NONE".equalsIgnoreCase(requested)) {
            String normalized = normalizeLocale(requested);
            if (snapshot.locales().contains(normalized)) {
                return normalized;
            }
        }
        String configured = normalizeLocale(Configuration.language);
        return snapshot.locales().contains(configured) ? configured : FALLBACK_LOCALE;
    }

    /**
     * Validates and normalizes a locale selected by a player.
     */
    public String requireLocale(String locale) {
        if (locale == null || locale.isBlank() || locale.equalsIgnoreCase("NONE")) {
            throw new IllegalArgumentException("A concrete menu language is required");
        }
        String normalized = normalizeLocale(locale);
        if (!snapshot.locales().contains(normalized)) {
            throw new IllegalArgumentException("Unsupported menu language: " + locale);
        }
        return normalized;
    }

    /**
     * Returns all locale ids loaded from the UI manifest.
     */
    public Set<String> localeIds() {
        return snapshot.locales();
    }

    /**
     * Returns the revision assigned after the most recent successful atomic load.
     */
    public long revision() {
        return snapshot.revision();
    }

    private SharedMenuDefinition loadShared(String menuId) {
        String relativePath = "uis/_shared/" + menuId + ".yml";
        YamlConfiguration yaml = loadDataYaml(relativePath);
        validateSchema(yaml, relativePath);
        String configuredId = yaml.getString("menu-id", "");
        if (!configuredId.equals(menuId)) {
            throw new IllegalArgumentException("menu-id must match file name at " + relativePath);
        }

        Set<String> requiredRouteArguments = loadRouteArguments(yaml, relativePath, "required");
        Set<String> optionalRouteArguments = loadRouteArguments(yaml, relativePath, "optional");
        Set<String> duplicateRouteArguments = new java.util.HashSet<>(requiredRouteArguments);
        duplicateRouteArguments.retainAll(optionalRouteArguments);
        if (!duplicateRouteArguments.isEmpty()) {
            throw new IllegalArgumentException("Route arguments cannot be both required and optional at "
                    + relativePath + ": " + duplicateRouteArguments);
        }
        Map<String, String> dataSources = loadDataSources(yaml, relativePath);
        ConfigurationSection actionGroupsSection = yaml.getConfigurationSection("action-groups");
        Map<String, List<ActionSpec>> actionGroups = new LinkedHashMap<>();
        if (actionGroupsSection != null) {
            for (String actionGroupId : actionGroupsSection.getKeys(false)) {
                validateButtonId(actionGroupId, relativePath + ".action-groups");
                List<String> rawActions = yaml.getStringList("action-groups." + actionGroupId + ".actions");
                if (rawActions.isEmpty()) {
                    throw new IllegalArgumentException("Action group has no actions: "
                            + relativePath + ".action-groups." + actionGroupId);
                }
                actionGroups.put(actionGroupId, parseActions(rawActions,
                        relativePath + ".action-groups." + actionGroupId + ".actions"));
            }
        }
        return new SharedMenuDefinition(menuId, requiredRouteArguments, optionalRouteArguments, dataSources,
                actionGroups);
    }

    TuiMenuDefinition loadTui(String menuId,
                              String locale,
                              YamlConfiguration yaml,
                              SharedMenuDefinition shared) {
        String relativePath = "uis/" + locale + "/text_menu/" + menuId + ".yml";
        validateSchema(yaml, relativePath);
        List<String> layout = readStringList(yaml, "Layout", "layout");
        if (layout.isEmpty()) {
            throw new IllegalArgumentException("TUI Layout cannot be empty at " + relativePath);
        }

        ConfigurationSection buttonsSection = section(yaml, "Buttons", "buttons");
        if (buttonsSection == null) {
            throw new IllegalArgumentException("Missing TUI Buttons at " + relativePath);
        }
        Map<String, TuiButtonDefinition> buttons = loadButtons(buttonsSection, shared, relativePath, true);
        long listCount = buttons.values().stream().filter(button -> button.type() == TuiButtonType.LIST).count();
        if (listCount > 1) {
            throw new IllegalArgumentException("Only one LIST is allowed at " + relativePath + ".Buttons");
        }
        buttons.values().stream()
                .filter(button -> button.type() == TuiButtonType.LIST)
                .forEach(button -> validateListReference(layout, button.id(), relativePath));
        validateLayout(layout, buttons, relativePath);
        return new TuiMenuDefinition(menuId, locale, layout, buttons, readVariables(yaml, relativePath), shared);
    }

    /**
     * Resolves one localized button into a renderer-neutral validated definition.
     */
    TuiButtonDefinition loadButton(String id,
                                   ConfigurationSection section,
                                   SharedMenuDefinition shared,
                                   String relativePath) {
        String typeName = section.getString("type", "BUTTON").toUpperCase(Locale.ROOT);
        TuiButtonType type;
        try {
            type = TuiButtonType.valueOf(typeName);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unknown TUI button type '" + typeName + "' at " + relativePath + ".Buttons." + id);
        }
        if (type == TuiButtonType.LIST) {
            return loadList(id, section, shared, relativePath);
        }

        String text = section.getString("text", "");
        if (text.isEmpty()) {
            throw new IllegalArgumentException("Button text cannot be empty at " + relativePath + ".Buttons." + id);
        }
        validateTextLength(text, relativePath + ".Buttons." + id + ".text");
        validateTextLength(section.getString("hover", ""), relativePath + ".Buttons." + id + ".hover");
        String actionGroup = section.getString("action-group", "").trim();
        String url = section.getString("url", "");
        String copy = section.getString("copy", "");
        boolean hasActions = section.contains("actions");
        String actionId = "";
        List<ActionSpec> actions = List.of();
        switch (type) {
            case BUTTON -> {
                if (hasActions == !actionGroup.isEmpty()) {
                    throw new IllegalArgumentException("BUTTON requires exactly one of actions or action-group at "
                            + relativePath + ".Buttons." + id);
                }
                rejectFields(section, relativePath, id, "url", "copy");
                if (hasActions) {
                    List<String> rawActions = section.getStringList("actions");
                    if (rawActions.isEmpty()) {
                        throw new IllegalArgumentException("BUTTON actions cannot be empty at "
                                + relativePath + ".Buttons." + id + ".actions");
                    }
                    actionId = id;
                    actions = parseActions(rawActions, relativePath + ".Buttons." + id + ".actions");
                } else {
                    actions = shared.actionGroups().get(actionGroup);
                    if (actions == null) {
                        throw new IllegalArgumentException("Unknown action group '" + actionGroup + "' at "
                                + relativePath + ".Buttons." + id);
                    }
                    actionId = actionGroup;
                }
            }
            case URL -> {
                rejectFields(section, relativePath, id, "actions", "action-group", "copy");
                if (url.isBlank()) {
                    throw new IllegalArgumentException("URL button requires url at " + relativePath + ".Buttons." + id);
                }
                validateUrl(url, relativePath + ".Buttons." + id + ".url");
            }
            case COPY -> {
                rejectFields(section, relativePath, id, "actions", "action-group", "url");
                if (copy.isEmpty()) {
                    throw new IllegalArgumentException("COPY button requires copy at " + relativePath + ".Buttons." + id);
                }
            }
            case LIST -> throw new IllegalStateException("LIST validation should have stopped earlier");
        }

        return new TuiButtonDefinition(
                id,
                type,
                text,
                section.getString("hover", ""),
                actionId,
                actions,
                url,
                copy,
                section.getString("permission", ""),
                section.getString("disabled-text", "&8[Unavailable]"),
                section.getBoolean("hidden-when-disabled", false),
                null
        );
    }

    private TuiButtonDefinition loadList(String id,
                                         ConfigurationSection section,
                                         SharedMenuDefinition shared,
                                         String relativePath) {
        String path = relativePath + ".Buttons." + id;
        rejectFields(section, relativePath, id, "text", "hover", "actions", "action-group", "url", "copy");
        String source = section.getString("source", "").trim();
        String providerId = shared.dataSources().get(source);
        if (providerId == null) {
            throw new IllegalArgumentException("Unknown shared data source '" + source + "' at " + path);
        }
        if (dataRegistry.resolve(providerId) == null) {
            throw new IllegalArgumentException("Unknown menu data provider '" + providerId + "' at " + path);
        }
        int rows = section.getInt("rows", 0);
        if (rows < 1 || rows > 20) {
            throw new IllegalArgumentException("LIST rows must be between 1 and 20 at " + path);
        }
        String emptyText = section.getString("empty", "");
        if (emptyText.isEmpty()) {
            throw new IllegalArgumentException("LIST empty text cannot be empty at " + path);
        }
        validateTextLength(emptyText, path + ".empty");
        List<String> layout = section.getStringList("Layout");
        if (layout.isEmpty()) {
            layout = section.getStringList("layout");
        }
        if (layout.size() != 1) {
            throw new IllegalArgumentException("LIST Layout must contain exactly one line at " + path);
        }
        ConfigurationSection nestedSection = section.getConfigurationSection("Buttons");
        if (nestedSection == null) {
            nestedSection = section.getConfigurationSection("buttons");
        }
        if (nestedSection == null) {
            throw new IllegalArgumentException("Missing LIST Buttons at " + path);
        }
        Map<String, TuiButtonDefinition> buttons = loadButtons(nestedSection, shared, path, false);
        validateLayout(layout, buttons, path);
        TuiListDefinition list = new TuiListDefinition(source, providerId, rows, emptyText, layout, buttons);
        return new TuiButtonDefinition(id, TuiButtonType.LIST, "", "", "", List.of(), "", "", "", "",
                false, list);
    }

    private Map<String, TuiButtonDefinition> loadButtons(ConfigurationSection section,
                                                          SharedMenuDefinition shared,
                                                          String relativePath,
                                                          boolean allowList) {
        Map<String, TuiButtonDefinition> buttons = new LinkedHashMap<>();
        if (section.getKeys(false).size() > MAX_BUTTONS) {
            throw new IllegalArgumentException("TUI Buttons exceeds " + MAX_BUTTONS + " entries at "
                    + relativePath + ".Buttons");
        }
        for (String buttonId : section.getKeys(false)) {
            validateButtonId(buttonId, relativePath + ".Buttons");
            ConfigurationSection button = section.getConfigurationSection(buttonId);
            if (button == null) {
                throw new IllegalArgumentException("Button must be a section at " + relativePath + ".Buttons." + buttonId);
            }
            TuiButtonDefinition definition = loadButton(buttonId, button, shared, relativePath);
            if (!allowList && definition.type() == TuiButtonType.LIST) {
                throw new IllegalArgumentException("Nested LIST is not allowed at " + relativePath + ".Buttons." + buttonId);
            }
            buttons.put(buttonId, definition);
        }
        return Map.copyOf(buttons);
    }

    private Map<String, String> loadDataSources(YamlConfiguration yaml, String relativePath) {
        ConfigurationSection dataSection = yaml.getConfigurationSection("data");
        if (dataSection == null) {
            return Map.of();
        }
        Map<String, String> dataSources = new LinkedHashMap<>();
        for (String source : dataSection.getKeys(false)) {
            validateButtonId(source, relativePath + ".data");
            String providerId = dataSection.getString(source + ".provider", "").trim();
            if (providerId.isEmpty()) {
                throw new IllegalArgumentException("Data source requires provider at " + relativePath + ".data." + source);
            }
            dataSources.put(source, providerId);
        }
        return Map.copyOf(dataSources);
    }

    private Set<String> loadRouteArguments(YamlConfiguration yaml, String relativePath, String type) {
        Set<String> arguments = new java.util.LinkedHashSet<>(yaml.getStringList("route." + type));
        for (String argument : arguments) {
            if (!MenuRoute.isValidArgumentKey(argument)) {
                throw new IllegalArgumentException("Invalid " + type + " route argument '" + argument + "' at "
                        + relativePath);
            }
        }
        return Set.copyOf(arguments);
    }

    // Both inline actions and shared action groups use the same whitelist and parser.
    private List<ActionSpec> parseActions(List<String> rawActions, String path) {
        List<ActionSpec> actions = actionParser.parseAll(rawActions, path);
        for (ActionSpec action : actions) {
            actionRegistry.validate(action);
        }
        return actions;
    }

    // Rejecting unused fields prevents ambiguous configuration from being silently ignored.
    private void rejectFields(ConfigurationSection section, String relativePath, String id, String... fields) {
        for (String field : fields) {
            if (section.contains(field)) {
                throw new IllegalArgumentException("Field '" + field + "' is not allowed for "
                        + section.getString("type", "BUTTON").toUpperCase(Locale.ROOT) + " at "
                        + relativePath + ".Buttons." + id);
            }
        }
    }

    private void validateLayout(List<String> layout,
                                Map<String, TuiButtonDefinition> buttons,
                                String relativePath) {
        if (layout.size() > MAX_LAYOUT_LINES) {
            throw new IllegalArgumentException("TUI Layout exceeds " + MAX_LAYOUT_LINES + " lines at "
                    + relativePath);
        }
        for (int lineIndex = 0; lineIndex < layout.size(); lineIndex++) {
            String line = layout.get(lineIndex);
            if (line.length() > MAX_LINE_LENGTH) {
                throw new IllegalArgumentException("TUI Layout line exceeds " + MAX_LINE_LENGTH + " characters at "
                        + relativePath + ".Layout[" + lineIndex + "]");
            }
            int buttonReferences = 0;
            int cursor = 0;
            while (cursor < line.length()) {
                if (line.startsWith("{{", cursor) || line.startsWith("}}", cursor)) {
                    cursor += 2;
                    continue;
                }
                if (line.charAt(cursor) != '{') {
                    cursor++;
                    continue;
                }
                int opening = cursor;
                int closing = line.indexOf('}', cursor + 1);
                if (closing < 0) {
                    throw new IllegalArgumentException("Unclosed placeholder at " + relativePath + ".Layout[" + lineIndex + "]");
                }
                String placeholder = line.substring(opening + 1, closing);
                if (!placeholder.contains(".") && !buttons.containsKey(placeholder)) {
                    throw new IllegalArgumentException("Unknown button placeholder '" + placeholder + "' at "
                            + relativePath + ".Layout[" + lineIndex + "]");
                }
                TuiButtonDefinition definition = buttons.get(placeholder);
                if (definition != null && ++buttonReferences > MAX_BUTTONS_PER_LINE) {
                    throw new IllegalArgumentException("TUI Layout line exceeds " + MAX_BUTTONS_PER_LINE
                            + " button components at " + relativePath + ".Layout[" + lineIndex + "]");
                }
                if (definition != null && definition.type() == TuiButtonType.LIST
                        && !line.trim().equals("{" + placeholder + "}")) {
                    throw new IllegalArgumentException("LIST placeholder must occupy its own line at "
                            + relativePath + ".Layout[" + lineIndex + "]");
                }
                cursor = closing + 1;
            }
            if (line.indexOf('｛') >= 0 || line.indexOf('｝') >= 0) {
                throw new IllegalArgumentException("Use ASCII braces for placeholders at " + relativePath + ".Layout[" + lineIndex + "]");
            }
        }
    }

    private void validateListReference(List<String> layout, String listId, String relativePath) {
        long references = layout.stream().filter(line -> line.trim().equals("{" + listId + "}")).count();
        if (references != 1) {
            throw new IllegalArgumentException("LIST placeholder '" + listId + "' must appear exactly once at "
                    + relativePath + ".Layout");
        }
    }

    private void installDefaults(List<String> locales,
                                 List<String> menuIds,
                                 List<String> chestMenuIds,
                                 List<String> dialogMenuIds) {
        saveIfMissing("uis/manifest.yml");
        for (String menuId : menuIds) {
            saveIfMissing("uis/_shared/" + menuId + ".yml");
            for (String locale : locales) {
                saveIfMissing("uis/" + locale + "/text_menu/" + menuId + ".yml");
            }
        }
        for (String menuId : chestMenuIds) {
            validateMenuId(menuId);
            if (!menuIds.contains(menuId)) {
                saveIfMissing("uis/_shared/" + menuId + ".yml");
            }
            for (String locale : locales) {
                saveIfMissing("uis/" + locale + "/chest_menu/" + menuId + ".yml");
            }
        }
        for (String menuId : dialogMenuIds) {
            validateMenuId(menuId);
            if (!menuIds.contains(menuId)) {
                saveIfMissing("uis/_shared/" + menuId + ".yml");
            }
            for (String locale : locales) {
                saveIfMissing("uis/" + locale + "/dialog_menu/" + menuId + ".yml");
            }
        }
    }

    private LocalizedYaml loadLocalized(String menuId, String requestedLocale, String menuType) {
        String requestedDirectory = localeDirectory(requestedLocale);
        String requestedPath = "uis/" + requestedDirectory + "/" + menuType + "/" + menuId + ".yml";
        File requestedFile = dataFile(requestedPath);
        if (requestedFile.exists()) {
            return new LocalizedYaml(normalizeLocale(requestedLocale),
                    YamlConfiguration.loadConfiguration(requestedFile));
        }
        String fallbackPath = "uis/" + FALLBACK_LOCALE + "/" + menuType + "/" + menuId + ".yml";
        File fallbackFile = dataFile(fallbackPath);
        if (!fallbackFile.exists()) {
            throw new IllegalArgumentException("Missing localized UI menu: " + requestedPath + " and " + fallbackPath);
        }
        return new LocalizedYaml(FALLBACK_LOCALE, YamlConfiguration.loadConfiguration(fallbackFile));
    }

    private String localeDirectory(String locale) {
        String normalized = normalizeLocale(locale);
        File[] directories = dataFile("uis").listFiles(File::isDirectory);
        if (directories != null) {
            for (File directory : directories) {
                if (LOCALE_ID.matcher(directory.getName()).matches()
                        && normalizeLocale(directory.getName()).equals(normalized)) {
                    return directory.getName();
                }
            }
        }
        return normalized;
    }

    /**
     * Discovers administrator-provided locale directories in addition to bundled manifest defaults.
     */
    private Set<String> discoverLocales(List<String> bundledLocales) {
        Set<String> locales = new java.util.LinkedHashSet<>();
        bundledLocales.stream().map(this::normalizeLocale).forEach(locales::add);
        File root = dataFile("uis");
        File[] directories = root.listFiles(File::isDirectory);
        if (directories != null) {
            for (File directory : directories) {
                if (directory.getName().equals("_shared") || !LOCALE_ID.matcher(directory.getName()).matches()) {
                    continue;
                }
                if (new File(directory, "text_menu").isDirectory()
                        || new File(directory, "chest_menu").isDirectory()
                        || new File(directory, "dialog_menu").isDirectory()) {
                    locales.add(normalizeLocale(directory.getName()));
                }
            }
        }
        locales.add(FALLBACK_LOCALE);
        return Set.copyOf(locales);
    }

    private YamlConfiguration loadDataYaml(String relativePath) {
        File file = dataFile(relativePath);
        if (!file.exists()) {
            throw new IllegalArgumentException("Missing UI configuration: " + file.getAbsolutePath());
        }
        return YamlConfiguration.loadConfiguration(file);
    }

    private YamlConfiguration loadEmbeddedYaml(String relativePath) {
        try (InputStream input = plugin.getResource(relativePath)) {
            if (input == null) {
                throw new IllegalArgumentException("Missing embedded UI resource: " + relativePath);
            }
            return YamlConfiguration.loadConfiguration(new InputStreamReader(input, StandardCharsets.UTF_8));
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("Failed to read embedded UI resource: " + relativePath, exception);
        }
    }

    private void saveIfMissing(String relativePath) {
        if (!dataFile(relativePath).exists()) {
            plugin.saveResource(relativePath, false);
        }
    }

    private File dataFile(String relativePath) {
        return new File(plugin.getDataFolder(), relativePath);
    }

    private void validateSchema(YamlConfiguration yaml, String path) {
        int version = yaml.getInt("schema-version", -1);
        if (version != SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported schema-version " + version + " at " + path);
        }
    }

    private void validateMenuId(String menuId) {
        validateButtonId(menuId, "uis/manifest.yml.text-menus");
    }

    private void validateButtonId(String id, String path) {
        if (!BUTTON_ID.matcher(id).matches()) {
            throw new IllegalArgumentException("Invalid id '" + id + "' at " + path);
        }
    }

    private Map<String, String> readVariables(YamlConfiguration yaml, String relativePath) {
        ConfigurationSection variablesSection = section(yaml, "Variables", "variables");
        if (variablesSection == null) {
            return Map.of();
        }
        Map<String, String> variables = new LinkedHashMap<>();
        collectVariables(variablesSection, "", variables, relativePath);
        return Map.copyOf(variables);
    }

    private void collectVariables(ConfigurationSection section,
                                  String prefix,
                                  Map<String, String> variables,
                                  String relativePath) {
        for (String key : section.getKeys(false)) {
            String variableKey = prefix.isEmpty() ? key : prefix + "." + key;
            Object value = section.get(key);
            if (value instanceof ConfigurationSection child) {
                collectVariables(child, variableKey, variables, relativePath);
                continue;
            }
            if (!(value instanceof String stringValue)) {
                throw new IllegalArgumentException("TUI variable must be text at " + relativePath + ".Variables." + variableKey);
            }
            if (variables.size() >= MAX_VARIABLES) {
                throw new IllegalArgumentException("TUI Variables exceeds " + MAX_VARIABLES + " entries at "
                        + relativePath);
            }
            validateTextLength(stringValue, relativePath + ".Variables." + variableKey);
            variables.put(variableKey, stringValue);
        }
    }

    private void validateUrl(String value, String path) {
        validateTextLength(value, path);
        try {
            URI uri = new URI(value);
            if ((!"https".equalsIgnoreCase(uri.getScheme()) && !"http".equalsIgnoreCase(uri.getScheme()))
                    || !uri.isAbsolute() || uri.getHost() == null || uri.getHost().isBlank()) {
                throw new IllegalArgumentException("Only HTTP(S) URLs are allowed at " + path);
            }
        } catch (URISyntaxException exception) {
            throw new IllegalArgumentException("Invalid URL at " + path, exception);
        }
    }

    private void validateTextLength(String value, String path) {
        if (value.length() > MAX_TEXT_LENGTH) {
            throw new IllegalArgumentException("Text exceeds " + MAX_TEXT_LENGTH + " characters at " + path);
        }
    }

    private String normalizeLocale(String locale) {
        if (locale == null || locale.isBlank() || locale.equalsIgnoreCase("NONE")) {
            return FALLBACK_LOCALE;
        }
        String normalized = locale.replace('-', '_');
        String[] parts = normalized.split("_", 2);
        if (parts.length != 2) {
            return FALLBACK_LOCALE;
        }
        return parts[0].toLowerCase(Locale.ROOT) + "_" + parts[1].toUpperCase(Locale.ROOT);
    }

    private String localizedKey(String locale, String menuId) {
        return normalizeLocale(locale) + "\u0000" + menuId;
    }

    private <T> T localized(Map<String, T> definitions, String menuId, String locale) {
        T requested = definitions.get(localizedKey(locale, menuId));
        if (requested != null) {
            return requested;
        }
        T configured = definitions.get(localizedKey(Configuration.language, menuId));
        return configured != null ? configured : definitions.get(localizedKey(FALLBACK_LOCALE, menuId));
    }

    private List<String> readStringList(YamlConfiguration yaml, String preferred, String fallback) {
        List<String> values = yaml.getStringList(preferred);
        return values.isEmpty() ? yaml.getStringList(fallback) : new ArrayList<>(values);
    }

    private ConfigurationSection section(YamlConfiguration yaml, String preferred, String fallback) {
        ConfigurationSection section = yaml.getConfigurationSection(preferred);
        return section == null ? yaml.getConfigurationSection(fallback) : section;
    }

    private record LocalizedYaml(String locale, YamlConfiguration yaml) {
    }

    /**
     * Publishes both renderer registries and their shared revision through one volatile assignment.
     */
    private record MenuSnapshot(
            Map<String, TuiMenuDefinition> textMenus,
            Map<String, ChestMenuDefinition> chestMenus,
            Map<String, DialogMenuDefinition> dialogMenus,
            Set<String> locales,
            Set<String> textMenuIds,
            Set<String> chestMenuIds,
            Set<String> dialogMenuIds,
            long revision
    ) {

        private MenuSnapshot {
            textMenus = Map.copyOf(textMenus);
            chestMenus = Map.copyOf(chestMenus);
            dialogMenus = Map.copyOf(dialogMenus);
            locales = Set.copyOf(locales);
            textMenuIds = Set.copyOf(textMenuIds);
            chestMenuIds = Set.copyOf(chestMenuIds);
            dialogMenuIds = Set.copyOf(dialogMenuIds);
        }
    }
}
