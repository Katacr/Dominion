package cn.lunadeer.dominion.uis.menu.action;

import cn.lunadeer.dominion.utils.Misc;
import cn.lunadeer.dominion.utils.XLogger;
import cn.lunadeer.dominion.utils.scheduler.Scheduler;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;
import java.util.UUID;

/**
 * Sends a toast through a temporary impossible advancement on Bukkit platforms.
 */
public final class ToastSender {

    private final JavaPlugin plugin;
    private final ToastTransport transport;
    private final boolean packetTransportRequired;

    /**
     * Creates a toast sender owned by the Dominion plugin namespace.
     */
    public ToastSender(JavaPlugin plugin) {
        this.plugin = plugin;
        this.packetTransportRequired = isSpigot26_2();
        this.transport = loadTransport(plugin);
    }

    /**
     * Grants and removes one temporary advancement to display a client toast.
     */
    public void send(Player player, String iconName, Component title, String frameName) {
        Material material = Material.matchMaterial(iconName);
        if (material == null || !material.isItem()) {
            throw new IllegalArgumentException("Invalid toast icon: " + iconName);
        }
        String frame = frameName.toLowerCase(Locale.ROOT);
        if (!frame.equals("task") && !frame.equals("goal") && !frame.equals("challenge")) {
            throw new IllegalArgumentException("Invalid toast frame: " + frameName);
        }

        String titleJson = GsonComponentSerializer.gson().serialize(title);
        if (transport != null) {
            transport.send(player, material.getKey().toString(), titleJson, frame);
            return;
        }
        if (packetTransportRequired) {
            throw new IllegalStateException("Spigot 26.2 packet toast transport is unavailable");
        }
        NamespacedKey key = new NamespacedKey(plugin, "ui_toast_" + UUID.randomUUID().toString().replace("-", ""));
        Advancement advancement = loadAdvancement(key, material, titleJson, frame);
        if (advancement == null) {
            throw new IllegalStateException("Bukkit rejected the temporary toast advancement");
        }

        AdvancementProgress progress = player.getAdvancementProgress(advancement);
        java.util.List<String> awardedCriteria = new java.util.ArrayList<>(progress.getRemainingCriteria());
        for (String criteria : awardedCriteria) {
            progress.awardCriteria(criteria);
        }
        Scheduler.runTaskLater(() -> {
            if (player.isOnline()) {
                awardedCriteria.forEach(progress::revokeCriteria);
            }
            Bukkit.getUnsafe().removeAdvancement(key);
        }, 10L);
    }

    // Modern servers use `id`, while legacy 1.20.1 advancement JSON uses `item`.
    private Advancement loadAdvancement(NamespacedKey key, Material material, String titleJson, String frame) {
        String itemId = material.getKey().toString();
        boolean legacyIcon = Bukkit.getBukkitVersion().startsWith("1.20.1")
                || Bukkit.getBukkitVersion().startsWith("1.20.2")
                || Bukkit.getBukkitVersion().startsWith("1.20.3")
                || Bukkit.getBukkitVersion().startsWith("1.20.4");
        String primaryField = legacyIcon ? "item" : "id";
        String fallbackField = legacyIcon ? "id" : "item";
        try {
            Advancement advancement = Bukkit.getUnsafe().loadAdvancement(
                    key, advancementJson(primaryField, itemId, titleJson, frame));
            if (advancement != null) {
                return advancement;
            }
        } catch (RuntimeException ignored) {
            Bukkit.getUnsafe().removeAdvancement(key);
        }
        return Bukkit.getUnsafe().loadAdvancement(key, advancementJson(fallbackField, itemId, titleJson, frame));
    }

    private String advancementJson(String iconField, String itemId, String titleJson, String frame) {
        return "{" +
                "\"criteria\":{\"trigger\":{\"trigger\":\"minecraft:impossible\"}}," +
                "\"display\":{" +
                "\"icon\":{\"" + iconField + "\":\"" + itemId + "\"}," +
                "\"title\":" + titleJson + "," +
                "\"description\":{\"text\":\"\"}," +
                "\"frame\":\"" + frame + "\"," +
                "\"show_toast\":true," +
                "\"announce_to_chat\":false," +
                "\"hidden\":true}}";
    }

    /**
     * Loads the isolated Spigot 26.2 packet transport without linking core to CraftBukkit or NMS.
     */
    private ToastTransport loadTransport(JavaPlugin plugin) {
        if (!packetTransportRequired) {
            return null;
        }
        try {
            Class<?> implementation = Class.forName(
                    "cn.lunadeer.dominion.dialog.spigot.SpigotToastTransport",
                    true, plugin.getClass().getClassLoader());
            return (ToastTransport) implementation.getDeclaredConstructor(JavaPlugin.class).newInstance(plugin);
        } catch (ReflectiveOperationException | LinkageError exception) {
            XLogger.warn("Spigot packet toast transport is unavailable: {0}", exception.getMessage());
            return null;
        }
    }

    /**
     * Detects the server line whose UnsafeValues toast fallback reloads advancement resources.
     */
    private boolean isSpigot26_2() {
        return !Misc.isPaper() && Bukkit.getBukkitVersion().startsWith("26.2");
    }

}
