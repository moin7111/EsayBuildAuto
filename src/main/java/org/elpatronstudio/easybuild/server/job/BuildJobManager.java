package org.elpatronstudio.easybuild.server.job;

import com.mojang.logging.LogUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.elpatronstudio.easybuild.core.model.JobPhase;
import org.elpatronstudio.easybuild.core.network.packet.ServerboundAcknowledgeStatus;
import org.elpatronstudio.easybuild.core.network.packet.ServerboundRequestBuild;
import org.elpatronstudio.easybuild.server.ServerHandshakeService;
import org.slf4j.Logger;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Coordinates server-side build job lifecycle.
 */
public final class BuildJobManager {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final BuildJobManager INSTANCE = new BuildJobManager();

    private final Map<String, BuildJobState> jobs = new ConcurrentHashMap<>();
    private final Map<UUID, Set<String>> playerJobs = new ConcurrentHashMap<>();

    private BuildJobManager() {
    }

    public static BuildJobManager get() {
        return INSTANCE;
    }

    public void submitBuild(ServerPlayer player, ServerboundRequestBuild message) {
        if (player == null) {
            return;
        }

        if (ServerHandshakeService.getSession(player.getUUID()) == null) {
            sendChat(player, Component.literal("[EasyBuild] Bitte zuerst einen gültigen Handshake abschließen."));
            return;
        }

        String jobId = JobIdGenerator.nextId();
        BuildJob job = new BuildJob(
                jobId,
                player.getUUID(),
                message.schematic(),
                message.anchor(),
                message.mode(),
                message.options(),
                System.currentTimeMillis(),
                message.requestId()
        );
        BuildJobState state = new BuildJobState(job, UUID.randomUUID());

        jobs.put(jobId, state);
        playerJobs.computeIfAbsent(player.getUUID(), uuid -> ConcurrentHashMap.newKeySet()).add(jobId);

        sendChat(player, Component.literal("[EasyBuild] Build-Job " + job.jobId() + " aufgenommen (Modus: " + job.mode() + ")"));

        LOGGER.debug("Queued EasyBuild job {} for player {}", job.jobId(), player.getGameProfile().name());
    }

    public void cancelJob(ServerPlayer player, String jobId) {
        if (player == null) {
            return;
        }

        BuildJobState state = jobs.get(jobId);
        if (state == null) {
            sendChat(player, Component.literal("[EasyBuild] Kein aktiver Job mit ID " + jobId + "."));
            return;
        }

        if (!state.job().ownerUuid().equals(player.getUUID())) {
            sendChat(player, Component.literal("[EasyBuild] Dieser Job gehört einem anderen Spieler."));
            return;
        }

        removeJob(state);
        sendChat(player, Component.literal("[EasyBuild] Job " + jobId + " wurde abgebrochen."));
    }

    public void acknowledgeStatus(ServerPlayer player, ServerboundAcknowledgeStatus message) {
        if (player == null) {
            return;
        }

        BuildJobState state = jobs.get(message.jobId());
        if (state == null) {
            LOGGER.debug("Ignoring acknowledgement for unknown job {}", message.jobId());
            return;
        }

        LOGGER.debug("Player {} acknowledged status {} for job {}", player.getGameProfile().name(), message.statusCode(), message.jobId());
    }

    public void handlePlayerLogout(ServerPlayer player) {
        if (player == null) {
            return;
        }

        Set<String> jobIds = playerJobs.remove(player.getUUID());
        if (jobIds != null) {
            jobIds.forEach(jobs::remove);
            LOGGER.debug("Cleared {} jobs for disconnecting player {}", jobIds.size(), player.getGameProfile().name());
        }
        ServerHandshakeService.removeSession(player.getUUID());
    }

    public BuildJobState getJob(String jobId) {
        return jobs.get(jobId);
    }

    private void removeJob(BuildJobState state) {
        jobs.remove(state.job().jobId());
        Set<String> owned = playerJobs.get(state.job().ownerUuid());
        if (owned != null) {
            owned.remove(state.job().jobId());
            if (owned.isEmpty()) {
                playerJobs.remove(state.job().ownerUuid());
            }
        }
        state.setPhase(JobPhase.COMPLETED);
    }

    private long estimateDurationTicks(BuildJob job) {
        // TODO: compute estimate based on schematic size and server config.
        return 0L;
    }

    private void sendChat(ServerPlayer player, Component component) {
        player.sendSystemMessage(component);
    }

    public void publishProgress(BuildJobState state, int placed, int total, JobPhase phase) {
        state.updateProgress(placed, total, phase);
        // Placeholder: broadcast to owner only for now.
        // This assumes the owner is online and present in playerJobs map.
        // Actual implementation will push via scheduler context.
    }
}
