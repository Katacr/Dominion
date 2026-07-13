package cn.lunadeer.dominion.utils.stui.inputter;

import cn.lunadeer.dominion.utils.scheduler.Scheduler;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class Inputter implements Listener {

    private static Inputter instance;

    public static Inputter getInstance() {
        if (instance == null) {
            throw new IllegalStateException("Inputter has not been initialized. Please call Inputter.init(plugin) first.");
        }
        return instance;
    }

    public Inputter(JavaPlugin plugin) {
        instance = this;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        cachedInputters = new ConcurrentHashMap<>();
    }

    private final Map<UUID, InputterRunner> cachedInputters;

    public void register(InputterRunner inputterRunner) {
        cachedInputters.put(inputterRunner.getSender().getUniqueId(), inputterRunner);
    }

    public boolean unregister(InputterRunner inputterRunner) {
        return cachedInputters.remove(inputterRunner.getSender().getUniqueId(), inputterRunner);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerInput(AsyncPlayerChatEvent event) {
        Player sender = event.getPlayer();
        InputterRunner runner = cachedInputters.remove(sender.getUniqueId());
        if (runner == null) return;
        event.setCancelled(true);
        String messageClone = event.getMessage();
        Scheduler.runEntityTask(() -> runner.runner(messageClone), sender);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerLogout(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        cachedInputters.remove(player.getUniqueId());
    }

}
