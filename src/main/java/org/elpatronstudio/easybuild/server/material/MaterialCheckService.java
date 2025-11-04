package org.elpatronstudio.easybuild.server.material;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.elpatronstudio.easybuild.core.model.MaterialStack;
import org.elpatronstudio.easybuild.core.network.EasyBuildPacketSender;
import org.elpatronstudio.easybuild.core.network.packet.ClientboundMaterialCheckResponse;
import org.elpatronstudio.easybuild.core.network.packet.ClientboundMissingMaterials;
import org.elpatronstudio.easybuild.core.network.packet.ServerboundMaterialCheckRequest;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Performs (placeholder) material availability checks.
 */
public final class MaterialCheckService {

    private static final MaterialCheckService INSTANCE = new MaterialCheckService();

    private MaterialCheckService() {
    }

    public static MaterialCheckService get() {
        return INSTANCE;
    }

    public void handle(ServerPlayer player, ServerboundMaterialCheckRequest request) {
        if (player == null) {
            return;
        }
        ServerLevel level = (ServerLevel) player.level();

        MaterialCheckResult result = evaluate(level, player, request);
        long nonce = ThreadLocalRandom.current().nextLong();
        long serverTime = System.currentTimeMillis();

        if (result.ok()) {
            long expiresAt = serverTime + 30_000L;
            EasyBuildPacketSender.sendTo(player, new ClientboundMaterialCheckResponse(
                    request.schematic(),
                    true,
                    Collections.emptyList(),
                    true,
                    expiresAt,
                    nonce,
                    serverTime
            ));
        } else {
            EasyBuildPacketSender.sendTo(player, new ClientboundMissingMaterials(
                    request.schematic(),
                    result.missing(),
                    request.chests(),
                    nonce,
                    serverTime
            ));
        }
    }

    private MaterialCheckResult evaluate(ServerLevel level, ServerPlayer player, ServerboundMaterialCheckRequest request) {
        // TODO: Implement real chest/inventory scanning. For now assume success.
        return new MaterialCheckResult(true, List.of());
    }

    public record MaterialCheckResult(boolean ok, List<MaterialStack> missing) {
    }
}
