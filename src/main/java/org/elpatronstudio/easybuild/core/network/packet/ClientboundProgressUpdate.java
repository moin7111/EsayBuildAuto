package org.elpatronstudio.easybuild.core.network.packet;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.elpatronstudio.easybuild.core.model.JobPhase;
import org.elpatronstudio.easybuild.core.model.SchematicRef;
import org.elpatronstudio.easybuild.core.network.EasyBuildNetwork;
import org.elpatronstudio.easybuild.client.state.EasyBuildClientState;
import org.slf4j.Logger;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server → Client message describing the state of an outstanding build job.
 */
public record ClientboundProgressUpdate(
        String jobId,
        SchematicRef schematic,
        int placed,
        int total,
        JobPhase phase,
        String message,
        long nonce,
        long serverTime
) implements CustomPacketPayload {

    public static final ResourceLocation ID = EasyBuildNetwork.payloadId("progress_update");
    public static final Type<ClientboundProgressUpdate> TYPE = new Type<>(ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundProgressUpdate> STREAM_CODEC =
        StreamCodec.of(ClientboundProgressUpdate::write, ClientboundProgressUpdate::read);

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<String, JobPhase> LAST_LOG_PHASE = new ConcurrentHashMap<>();
    private static final Map<String, Integer> LAST_LOG_BUCKET = new ConcurrentHashMap<>();

    public ClientboundProgressUpdate {
        Objects.requireNonNull(jobId, "jobId");
        Objects.requireNonNull(schematic, "schematic");
        Objects.requireNonNull(phase, "phase");
        if (message == null) {
            message = "";
        }
    }

    private static void write(RegistryFriendlyByteBuf buf, ClientboundProgressUpdate value) {
        buf.writeUtf(value.jobId);
        FriendlyByteBufUtil.writeSchematicRef(buf, value.schematic);
        buf.writeVarInt(value.placed);
        buf.writeVarInt(value.total);
        buf.writeEnum(value.phase);
        buf.writeUtf(value.message);
        buf.writeLong(value.nonce);
        buf.writeLong(value.serverTime);
    }

    private static ClientboundProgressUpdate read(RegistryFriendlyByteBuf buf) {
        String jobId = buf.readUtf();
        SchematicRef schematic = FriendlyByteBufUtil.readSchematicRef(buf);
        int placed = buf.readVarInt();
        int total = buf.readVarInt();
        JobPhase phase = buf.readEnum(JobPhase.class);
        String message = buf.readUtf();
        long nonce = buf.readLong();
        long serverTime = buf.readLong();
        return new ClientboundProgressUpdate(jobId, schematic, placed, total, phase, message, nonce, serverTime);
    }

    @Override
    public Type<ClientboundProgressUpdate> type() {
        return TYPE;
    }

    public void handleClient() {
        Minecraft minecraft = Minecraft.getInstance();
        EasyBuildClientState.get().recordProgressUpdate(this);
        logProgress();
        if (minecraft.player != null && !message.isBlank()) {
            minecraft.player.displayClientMessage(Component.literal("[EasyBuild] " + message), true);
        }
    }

    private void logProgress() {
        int bucket = computeProgressBucket();
        JobPhase previousPhase = LAST_LOG_PHASE.get(jobId);
        Integer previousBucket = LAST_LOG_BUCKET.get(jobId);

        boolean shouldLog = previousPhase == null || previousPhase != phase;
        if (!shouldLog && bucket >= 0 && phase == JobPhase.PLACING) {
            shouldLog = previousBucket == null || previousBucket != bucket;
        }
        if (phase == JobPhase.COMPLETED || phase == JobPhase.CANCELLED) {
            shouldLog = true;
        }

        if (!shouldLog) {
            return;
        }

        LAST_LOG_PHASE.put(jobId, phase);
        if (bucket >= 0) {
            LAST_LOG_BUCKET.put(jobId, bucket);
        } else if (phase == JobPhase.COMPLETED || phase == JobPhase.CANCELLED) {
            LAST_LOG_BUCKET.remove(jobId);
        }

        LOGGER.info("[EasyBuild] Job {} [{}] {}", jobId, phase, buildLogSummary());
    }

    private int computeProgressBucket() {
        if (total <= 0) {
            return -1;
        }
        double ratio = (double) placed / Math.max(1, total);
        return (int) Math.floor(ratio * 10.0);
    }

    private String buildLogSummary() {
        String summary = message.isBlank() ? placed + " / " + total : message;
        if (phase == JobPhase.PLACING && total > 0) {
            double percent = (double) placed / Math.max(1, total) * 100.0;
            summary = summary + String.format(Locale.ROOT, " (%.0f%%)", percent);
        }
        return summary;
    }
}
