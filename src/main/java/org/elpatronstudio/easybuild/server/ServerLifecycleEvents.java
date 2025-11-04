package org.elpatronstudio.easybuild.server;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.elpatronstudio.easybuild.server.job.BuildJobManager;

/**
 * Listens to server lifecycle events relevant to EasyBuild features.
 */
public final class ServerLifecycleEvents {

    private ServerLifecycleEvents() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(ServerLifecycleEvents::onPlayerLogout);
    }

    private static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            BuildJobManager.get().handlePlayerLogout(serverPlayer);
        }
    }
}
