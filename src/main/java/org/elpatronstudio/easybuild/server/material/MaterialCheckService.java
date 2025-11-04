package org.elpatronstudio.easybuild.server.material;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.elpatronstudio.easybuild.core.model.MaterialStack;
import org.elpatronstudio.easybuild.core.network.EasyBuildPacketSender;
import org.elpatronstudio.easybuild.core.network.packet.ClientboundMaterialCheckResponse;
import org.elpatronstudio.easybuild.core.network.packet.ClientboundMissingMaterials;
import org.elpatronstudio.easybuild.core.network.packet.ServerboundMaterialCheckRequest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Performs material availability checks by scanning linked containers and the player's inventory.
 */
public final class MaterialCheckService {

    private static final MaterialCheckService INSTANCE = new MaterialCheckService();
    private static final long RESERVATION_DURATION_MS = 30_000L;

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
            long expiresAt = serverTime + RESERVATION_DURATION_MS;
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
        if (request.clientEstimate().isEmpty()) {
            return new MaterialCheckResult(true, List.of());
        }

        Map<ResourceLocation, Integer> required = new HashMap<>();
        request.clientEstimate().forEach(stack -> required.merge(stack.itemId(), stack.count(), Integer::sum));

        Map<ResourceLocation, Integer> available = new HashMap<>();
        collectFromInventory(player.getInventory(), available);

        request.chests().forEach(chest -> {
            if (!level.dimension().location().equals(chest.dimension())) {
                return;
            }
            BlockPos pos = chest.blockPos();
            if (!level.hasChunkAt(pos)) {
                return;
            }
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof Container container) {
                collectFromContainer(container, available);
            }
        });

        List<MaterialStack> missing = new ArrayList<>();
        required.forEach((itemId, requiredAmount) -> {
            int have = available.getOrDefault(itemId, 0);
            if (have < requiredAmount) {
                missing.add(new MaterialStack(itemId, requiredAmount - have));
            }
        });

        return missing.isEmpty()
                ? new MaterialCheckResult(true, List.of())
                : new MaterialCheckResult(false, missing);
    }

    private void collectFromInventory(Inventory inventory, Map<ResourceLocation, Integer> target) {
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            addStack(inventory.getItem(i), target);
        }
    }

    private void collectFromContainer(Container container, Map<ResourceLocation, Integer> target) {
        for (int i = 0; i < container.getContainerSize(); i++) {
            addStack(container.getItem(i), target);
        }
    }

    private void addStack(ItemStack stack, Map<ResourceLocation, Integer> target) {
        if (stack.isEmpty()) {
            return;
        }
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        target.merge(itemId, stack.getCount(), Integer::sum);
    }

    public record MaterialCheckResult(boolean ok, List<MaterialStack> missing) {
    }
}
