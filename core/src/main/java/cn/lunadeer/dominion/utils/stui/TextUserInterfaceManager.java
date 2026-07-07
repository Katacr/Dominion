package cn.lunadeer.dominion.utils.stui;

import cn.lunadeer.dominion.utils.Misc;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.bungeecord.BungeeComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class TextUserInterfaceManager {

    private BukkitAudiences adventure = null;
    private final JavaPlugin plugin;
    private static TextUserInterfaceManager instance;

    public static TextUserInterfaceManager getInstance() {
        return instance;
    }

    public TextUserInterfaceManager(JavaPlugin plugin) {
        instance = this;
        if (!Misc.isPaper()) {
            this.adventure = BukkitAudiences.create(plugin);
        }
        this.plugin = plugin;
    }

    public BukkitAudiences getAdventure() {
        return adventure;
    }

    public void sendMessage(Player player, Component msg) {
        if (adventure != null) {
            // Spigot does not expose Paper's native Adventure sender. Sending via
            // Bungee components keeps click events working on legacy Bukkit forks.
            player.spigot().sendMessage(BungeeComponentSerializer.get().serialize(msg));
        } else {
            player.sendMessage(msg);
        }
    }
}
