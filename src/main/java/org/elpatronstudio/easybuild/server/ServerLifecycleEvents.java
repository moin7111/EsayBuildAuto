package org.elpatronstudio.easybuild.server;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import org.elpatronstudio.easybuild.server.job.BuildJobManager;

/**
 * Listens to server lifecycle events relevant to EasyBuild features.
 */
public final class ServerLifecycleEvents {

    private ServerLifecycleEvents() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(ServerLifecycleEvents::onPlayerLogout);
        NeoForge.EVENT_BUS.addListener(ServerLifecycleEvents::onLevelTick);
    }

    private static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            BuildJobManager.get().handlePlayerLogout(serverPlayer);
        }
    }

    private static void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel serverLevel && serverLevel.dimension().equals(Level.OVERWORLD)) {
            BuildJobManager.get().tickServer(serverLevel);
        }
    }
}
