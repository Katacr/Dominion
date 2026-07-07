package cn.lunadeer.dominion.v26_2.events.environment.SulfurCubeExplode;

import cn.lunadeer.dominion.api.dtos.flag.Flags;
import cn.lunadeer.dominion.events.LowestVersion;
import cn.lunadeer.dominion.utils.XVersionManager;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityExplodeEvent;

import static cn.lunadeer.dominion.misc.Others.checkEnvironmentFlag;

/**
 * Protects dominion blocks from Sulfur Cube explosions in Minecraft 26.2+.
 * <p>
 * Sulfur Cubes can carry explosive content and eventually trigger the generic
 * {@link EntityExplodeEvent}. Since the resulting block damage is TNT-like, it
 * is governed by the existing {@link Flags#TNT_EXPLODE} environment flag.
 */
@LowestVersion(XVersionManager.ImplementationVersion.v26_2)
public class BlockExploded implements Listener {
    @EventHandler(priority = EventPriority.LOWEST)
    public void handler(EntityExplodeEvent event) {
        if (event.isCancelled()) return;
        Entity entity = event.getEntity();
        if (entity.getType() != EntityType.SULFUR_CUBE) {
            return;
        }
        event.blockList().removeIf(block -> !checkEnvironmentFlag(block.getLocation(), Flags.TNT_EXPLODE, null));
    }
}
