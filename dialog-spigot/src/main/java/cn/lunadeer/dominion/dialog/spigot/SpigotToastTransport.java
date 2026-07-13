package cn.lunadeer.dominion.dialog.spigot;

import cn.lunadeer.dominion.uis.menu.action.ToastTransport;
import cn.lunadeer.dominion.utils.scheduler.Scheduler;
import com.google.gson.JsonParser;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Sends Spigot 26.2 toast packets without mutating or reloading the server advancement registry.
 */
public final class SpigotToastTransport implements ToastTransport {

    private final Constructor<?> holderConstructor;
    private final Constructor<?> progressConstructor;
    private final Constructor<?> packetConstructor;
    private final Method identifierFactory;
    private final Object advancementCodec;
    private final Object jsonOps;
    private final Method codecParse;
    private final Method dataResultGetOrThrow;
    private final Method advancementRequirements;
    private final Method progressUpdate;
    private final Method progressGrant;
    private final Method craftPlayerGetHandle;
    private final Field playerConnection;
    private final Method connectionSend;

    /**
     * Resolves the Mojang-mapped Spigot 26.2 packet contract once at runtime.
     */
    public SpigotToastTransport(JavaPlugin plugin) throws ReflectiveOperationException {
        ClassLoader classLoader = plugin.getClass().getClassLoader();
        Class<?> advancement = load(classLoader, "net.minecraft.advancements.Advancement");
        Class<?> advancementHolder = load(classLoader, "net.minecraft.advancements.AdvancementHolder");
        Class<?> advancementProgress = load(classLoader, "net.minecraft.advancements.AdvancementProgress");
        Class<?> advancementRequirementsType = load(classLoader,
                "net.minecraft.advancements.AdvancementRequirements");
        Class<?> identifier = load(classLoader, "net.minecraft.resources.Identifier");
        Class<?> packet = load(classLoader,
                "net.minecraft.network.protocol.game.ClientboundUpdateAdvancementsPacket");
        Class<?> packetInterface = load(classLoader, "net.minecraft.network.protocol.Packet");
        Class<?> craftPlayer = load(classLoader, "org.bukkit.craftbukkit.entity.CraftPlayer");
        Class<?> serverPlayer = load(classLoader, "net.minecraft.server.level.ServerPlayer");
        Class<?> connection = load(classLoader,
                "net.minecraft.server.network.ServerGamePacketListenerImpl");
        Class<?> dynamicOps = load(classLoader, "com.mojang.serialization.DynamicOps");
        Class<?> decoder = load(classLoader, "com.mojang.serialization.Decoder");
        Class<?> dataResult = load(classLoader, "com.mojang.serialization.DataResult");
        Class<?> jsonOpsType = load(classLoader, "com.mojang.serialization.JsonOps");

        holderConstructor = advancementHolder.getConstructor(identifier, advancement);
        progressConstructor = advancementProgress.getConstructor();
        packetConstructor = packet.getConstructor(boolean.class, Collection.class, Set.class, Map.class,
                boolean.class);
        identifierFactory = identifier.getMethod("fromNamespaceAndPath", String.class, String.class);
        advancementCodec = advancement.getField("CODEC").get(null);
        jsonOps = jsonOpsType.getField("INSTANCE").get(null);
        codecParse = decoder.getMethod("parse", dynamicOps, Object.class);
        dataResultGetOrThrow = dataResult.getMethod("getOrThrow");
        advancementRequirements = advancement.getMethod("requirements");
        progressUpdate = advancementProgress.getMethod("update", advancementRequirementsType);
        progressGrant = advancementProgress.getMethod("grantProgress", String.class);
        craftPlayerGetHandle = craftPlayer.getMethod("getHandle");
        playerConnection = serverPlayer.getField("connection");
        connectionSend = connection.getMethod("send", packetInterface);
    }

    /**
     * Sends one completed temporary advancement followed by a client-only removal packet.
     */
    @Override
    public void send(Player player, String itemId, String titleJson, String frame) {
        try {
            Object identifier = identifierFactory.invoke(null, "dominion",
                    "ui_toast_" + UUID.randomUUID().toString().replace("-", ""));
            Object advancement = parseAdvancement(advancementJson(itemId, titleJson, frame));
            Object holder = holderConstructor.newInstance(identifier, advancement);
            Object progress = progressConstructor.newInstance();
            progressUpdate.invoke(progress, advancementRequirements.invoke(advancement));
            progressGrant.invoke(progress, "trigger");
            Object connection = playerConnection.get(craftPlayerGetHandle.invoke(player));
            Object addPacket = packetConstructor.newInstance(false, List.of(holder), Set.of(),
                    Map.of(identifier, progress), false);
            connectionSend.invoke(connection, addPacket);
            Scheduler.runTaskLater(() -> {
                if (player.isOnline()) {
                    remove(connection, identifier);
                }
            }, 10L);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to send Spigot toast packet", exception);
        }
    }

    /**
     * Decodes one advancement with the same codec path used by Spigot's UnsafeValues implementation.
     */
    private Object parseAdvancement(String json) throws ReflectiveOperationException {
        Object result = codecParse.invoke(advancementCodec, jsonOps, JsonParser.parseString(json));
        return dataResultGetOrThrow.invoke(result);
    }

    /**
     * Removes the temporary advancement from the target client's local advancement state.
     */
    private void remove(Object connection, Object identifier) {
        try {
            Object removePacket = packetConstructor.newInstance(false, List.of(), Set.of(identifier), Map.of(), false);
            connectionSend.invoke(connection, removePacket);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to remove Spigot toast advancement", exception);
        }
    }

    /**
     * Loads one runtime-only server type without linking it into the module bytecode signatures.
     */
    private static Class<?> load(ClassLoader classLoader, String name) throws ClassNotFoundException {
        return Class.forName(name, false, classLoader);
    }

    /**
     * Builds the minimal impossible advancement payload required for a client toast.
     */
    private static String advancementJson(String itemId, String titleJson, String frame) {
        return "{" +
                "\"criteria\":{\"trigger\":{\"trigger\":\"minecraft:impossible\"}}," +
                "\"display\":{" +
                "\"icon\":{\"id\":\"" + itemId + "\"}," +
                "\"title\":" + titleJson + "," +
                "\"description\":{\"text\":\"\"}," +
                "\"frame\":\"" + frame + "\"," +
                "\"show_toast\":true," +
                "\"announce_to_chat\":false," +
                "\"hidden\":true}}";
    }
}
