package org.elpatronstudio.easybuild.server.job;

import com.mojang.logging.LogUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.elpatronstudio.easybuild.core.model.JobPhase;
import org.elpatronstudio.easybuild.core.network.EasyBuildPacketSender;
import org.elpatronstudio.easybuild.core.network.packet.ClientboundBuildAccepted;
import org.elpatronstudio.easybuild.core.network.packet.ClientboundBuildCompleted;
import org.elpatronstudio.easybuild.core.network.packet.ClientboundBuildFailed;
import org.elpatronstudio.easybuild.core.network.packet.ClientboundProgressUpdate;
import org.elpatronstudio.easybuild.core.network.packet.ServerboundAcknowledgeStatus;
import org.elpatronstudio.easybuild.core.network.packet.ServerboundRequestBuild;
import org.elpatronstudio.easybuild.server.ServerHandshakeService;
import org.slf4j.Logger;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ThreadLocalRandom;

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
            EasyBuildPacketSender.sendTo(player, new ClientboundBuildFailed(
                    message.requestId(),
                    "HANDSHAKE_REQUIRED",
                    "Handshake required before requesting builds.",
                    false,
                    ThreadLocalRandom.current().nextLong(),
                    System.currentTimeMillis()
            ));
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

        long now = System.currentTimeMillis();
        EasyBuildPacketSender.sendTo(player, new ClientboundBuildAccepted(
                job.jobId(),
                job.mode(),
                estimateDurationTicks(job),
                state.reservationToken(),
                ThreadLocalRandom.current().nextLong(),
                now
        ));

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
            EasyBuildPacketSender.sendTo(player, new ClientboundBuildFailed(
                    jobId,
                    "JOB_NOT_FOUND",
                    "Kein aktiver Job mit dieser ID.",
                    false,
                    ThreadLocalRandom.current().nextLong(),
                    System.currentTimeMillis()
            ));
            return;
        }

        if (!state.job().ownerUuid().equals(player.getUUID())) {
            sendChat(player, Component.literal("[EasyBuild] Dieser Job gehört einem anderen Spieler."));
            return;
        }

        EasyBuildPacketSender.sendTo(player, new ClientboundBuildFailed(
                jobId,
                "CANCELLED",
                "Job wurde abgebrochen.",
                false,
                ThreadLocalRandom.current().nextLong(),
                System.currentTimeMillis()
        ));

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
        jobQueue.remove(state);
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
        return TICKS_PER_JOB;
    }

    private void sendChat(ServerPlayer player, Component component) {
        player.sendSystemMessage(component);
    }

    public void publishProgress(BuildJobState state, int placed, int total, JobPhase phase) {
        state.updateProgress(placed, total, phase);
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

            int blocksPerTick = Math.max(1, SIMULATED_TOTAL_BLOCKS / TICKS_PER_JOB);
            int placed = Math.min(SIMULATED_TOTAL_BLOCKS, currentJob.ticksElapsed * blocksPerTick);
            publishProgress(state, placed, SIMULATED_TOTAL_BLOCKS, state.phase());
            EasyBuildPacketSender.sendTo(level, state.job().ownerUuid(), new ClientboundProgressUpdate(
                    state.job().jobId(),
                    placed,
                    SIMULATED_TOTAL_BLOCKS,
                    state.phase(),
                    "",
                    ThreadLocalRandom.current().nextLong(),
                    System.currentTimeMillis()
            ));

            if (currentJob.ticksElapsed >= TICKS_PER_JOB) {
                completeJob(level, state);
                currentJob = null;
            }
        }
    }

    private void completeJob(ServerLevel level, BuildJobState state) {
        removeJob(state, JobPhase.COMPLETED);
        publishProgress(state, SIMULATED_TOTAL_BLOCKS, SIMULATED_TOTAL_BLOCKS, JobPhase.COMPLETED);
        EasyBuildPacketSender.sendTo(level, state.job().ownerUuid(), new ClientboundBuildCompleted(
                state.job().jobId(),
                true,
                List.of(),
                "",
                ThreadLocalRandom.current().nextLong(),
                System.currentTimeMillis()
        ));
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
