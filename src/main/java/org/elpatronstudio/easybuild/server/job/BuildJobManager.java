package org.elpatronstudio.easybuild.server.job;

import com.mojang.logging.LogUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.elpatronstudio.easybuild.core.model.JobPhase;
import org.elpatronstudio.easybuild.core.network.packet.ServerboundAcknowledgeStatus;
import org.elpatronstudio.easybuild.core.network.packet.ServerboundRequestBuild;
import org.elpatronstudio.easybuild.server.ServerHandshakeService;
import org.slf4j.Logger;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Coordinates server-side build job lifecycle.
 */
public final class BuildJobManager {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final BuildJobManager INSTANCE = new BuildJobManager();

    private static final int SIMULATED_TOTAL_BLOCKS = 1200;
    private static final int TICKS_PER_JOB = 40;

    private final Map<String, BuildJobState> jobs = new ConcurrentHashMap<>();
    private final Map<UUID, Set<String>> playerJobs = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<BuildJobState> jobQueue = new ConcurrentLinkedQueue<>();
    private ActiveJob currentJob;

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
        jobQueue.add(state);

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

        removeJob(state, JobPhase.CANCELLED);
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

        UUID uuid = player.getUUID();
        Set<String> jobIds = playerJobs.remove(uuid);
        if (jobIds != null) {
            jobIds.stream()
                    .map(jobs::remove)
                    .filter(state -> state != null)
                    .forEach(state -> removeJob(state, JobPhase.CANCELLED));
            LOGGER.debug("Cleared {} jobs for disconnecting player {}", jobIds.size(), player.getGameProfile().name());
        }
        ServerHandshakeService.removeSession(uuid);
    }

    public BuildJobState getJob(String jobId) {
        return jobs.get(jobId);
    }

    private void removeJob(BuildJobState state, JobPhase finalPhase) {
        jobs.remove(state.job().jobId());
        jobQueue.removeIf(candidate -> candidate == state);
        if (currentJob != null && currentJob.state == state) {
            currentJob = null;
        }
        Set<String> owned = playerJobs.get(state.job().ownerUuid());
        if (owned != null) {
            owned.remove(state.job().jobId());
            if (owned.isEmpty()) {
                playerJobs.remove(state.job().ownerUuid());
            }
        }
        state.setPhase(finalPhase);
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

    public void tickServer(ServerLevel level) {
        if (!level.dimension().equals(Level.OVERWORLD)) {
            return;
        }

        if (currentJob == null) {
            BuildJobState next = jobQueue.poll();
            if (next != null) {
                currentJob = new ActiveJob(next);
                next.setPhase(JobPhase.RESERVING);
                sendChatToOwner(level, next, Component.literal("[EasyBuild] Reservierung gestartet (Stub)."));
            }
        }

        if (currentJob != null) {
            currentJob.ticksElapsed++;
            BuildJobState state = currentJob.state;
            if (state.phase() == JobPhase.RESERVING && currentJob.ticksElapsed >= 5) {
                state.setPhase(JobPhase.PLACING);
                sendChatToOwner(level, state, Component.literal("[EasyBuild] Baue jetzt (Stub)."));
            }

            int placed = Math.min(SIMULATED_TOTAL_BLOCKS, currentJob.ticksElapsed * (SIMULATED_TOTAL_BLOCKS / Math.max(1, TICKS_PER_JOB)));
            publishProgress(state, placed, SIMULATED_TOTAL_BLOCKS, state.phase());

            if (currentJob.ticksElapsed >= TICKS_PER_JOB) {
                completeJob(level, state);
                currentJob = null;
            }
        }
    }

    private void completeJob(ServerLevel level, BuildJobState state) {
        removeJob(state, JobPhase.COMPLETED);
        publishProgress(state, SIMULATED_TOTAL_BLOCKS, SIMULATED_TOTAL_BLOCKS, JobPhase.COMPLETED);
        sendChatToOwner(level, state, Component.literal("[EasyBuild] Job " + state.job().jobId() + " abgeschlossen."));
    }

    private void sendChatToOwner(ServerLevel level, BuildJobState state, Component message) {
        Optional.ofNullable(level.getServer().getPlayerList().getPlayer(state.job().ownerUuid()))
                .ifPresent(player -> sendChat(player, message));
    }

    private static final class ActiveJob {
        private final BuildJobState state;
        private int ticksElapsed;

        private ActiveJob(BuildJobState state) {
            this.state = state;
            this.ticksElapsed = 0;
        }
    }
}
