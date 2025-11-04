package org.elpatronstudio.easybuild.server.job;

import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.elpatronstudio.easybuild.core.model.JobPhase;
import org.elpatronstudio.easybuild.core.model.PasteMode;
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

        ServerLevel targetLevel = resolveTargetLevel(player, job);
        if (targetLevel == null) {
            EasyBuildPacketSender.sendTo(player, new ClientboundBuildFailed(
                    job.jobId(),
                    "DIMENSION_UNAVAILABLE",
                    "Ziel-Dimension nicht geladen.",
                    false,
                    ThreadLocalRandom.current().nextLong(),
                    System.currentTimeMillis()
            ));
            sendChat(player, Component.literal("[EasyBuild] Dimension " + job.anchor().dimension() + " ist nicht verfügbar."));
            return;
        }

        BlockPlacementPlan plan;
        try {
            plan = BlockPlacementPlanner.plan(targetLevel, job, job.options());
        } catch (BlockPlacementException ex) {
            EasyBuildPacketSender.sendTo(player, new ClientboundBuildFailed(
                    job.jobId(),
                    ex.reasonCode(),
                    ex.getMessage(),
                    false,
                    ThreadLocalRandom.current().nextLong(),
                    System.currentTimeMillis()
            ));
            sendChat(player, Component.literal("[EasyBuild] Job abgelehnt: " + ex.getMessage()));
            LOGGER.debug("Rejected job {} due to planning error: {}", job.jobId(), ex.getMessage());
            return;
        } catch (Exception ex) {
            EasyBuildPacketSender.sendTo(player, new ClientboundBuildFailed(
                    job.jobId(),
                    "PLAN_FAILURE",
                    ex.getMessage(),
                    false,
                    ThreadLocalRandom.current().nextLong(),
                    System.currentTimeMillis()
            ));
            LOGGER.warn("Failed to prepare job {}", job.jobId(), ex);
            return;
        }

        BuildJobState state = new BuildJobState(job, UUID.randomUUID());
        state.attachPlan(plan);
        state.updateProgress(0, plan.totalBlocks(), JobPhase.QUEUED);

        jobs.put(jobId, state);
        playerJobs.computeIfAbsent(player.getUUID(), uuid -> ConcurrentHashMap.newKeySet()).add(jobId);
        jobQueue.add(state);

        long estimatedTicks = estimateDurationTicks(job, plan);
        long now = System.currentTimeMillis();
        EasyBuildPacketSender.sendTo(player, new ClientboundBuildAccepted(
                job.jobId(),
                job.mode(),
                estimatedTicks,
                state.reservationToken(),
                ThreadLocalRandom.current().nextLong(),
                now
        ));

        sendChat(player, Component.literal("[EasyBuild] Build-Job " + job.jobId() + " aufgenommen – " + plan.totalBlocks() + " Blöcke."));

        LOGGER.debug("Queued EasyBuild job {} for player {} ({} blocks)", job.jobId(), player.getGameProfile().name(), plan.totalBlocks());
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

    private long estimateDurationTicks(BuildJob job, BlockPlacementPlan plan) {
        int total = plan != null ? plan.totalBlocks() : 0;
        int perTick = resolveBlocksPerTick(job);
        if (perTick == Integer.MAX_VALUE || total == 0) {
            return 1L;
        }
        return Math.max(1L, (long) Math.ceil((double) total / perTick));
    }

    private void sendChat(ServerPlayer player, Component component) {
        player.sendSystemMessage(component);
    }

    public void publishProgress(BuildJobState state, int placed, int total, JobPhase phase) {
        state.updateProgress(placed, total, phase);
    }

    private int resolveBlocksPerTick(BuildJob job) {
        if (job.mode() == PasteMode.ATOMIC) {
            return Integer.MAX_VALUE;
        }

        int base = switch (job.mode()) {
            case STEP -> 64;
            case SIMULATED -> 24;
            default -> 32;
        };

        JsonObject options = job.options();
        if (options != null && options.has("blocksPerTick")) {
            try {
                int configured = options.get("blocksPerTick").getAsInt();
                if (configured > 0) {
                    base = configured;
                }
            } catch (Exception ignored) {
                // Ignore malformed configuration; fall back to default.
            }
        }

        return Math.max(1, Math.min(2048, base));
    }

    private ServerLevel resolveTargetLevel(ServerPlayer player, BuildJob job) {
        if (player == null) {
            return null;
        }
        ResourceKey<Level> dimensionKey = BlockPlacementPlanner.resolveDimensionKey(job.anchor());
        return ((ServerLevel) player.level()).getServer().getLevel(dimensionKey);
    }

    private ServerLevel resolveTargetLevel(ServerLevel reference, BuildJob job) {
        if (reference == null || reference.getServer() == null) {
            return null;
        }
        ResourceKey<Level> dimensionKey = BlockPlacementPlanner.resolveDimensionKey(job.anchor());
        return reference.getServer().getLevel(dimensionKey);
    }

    public void tickServer(ServerLevel level) {
        if (!level.dimension().equals(Level.OVERWORLD)) {
            return;
        }

        if (currentJob == null) {
            BuildJobState nextState;
            while ((nextState = jobQueue.poll()) != null) {
                ServerLevel targetLevel = resolveTargetLevel(level, nextState.job());
                if (targetLevel == null) {
                    failJob(level, nextState, "DIMENSION_UNAVAILABLE", "Ziel-Dimension nicht geladen.", false);
                    continue;
                }

                BlockPlacementPlan plan = nextState.plan();
                if (plan == null) {
                    try {
                        plan = BlockPlacementPlanner.plan(targetLevel, nextState.job(), nextState.job().options());
                        nextState.attachPlan(plan);
                        nextState.updateProgress(nextState.placed(), plan.totalBlocks(), JobPhase.QUEUED);
                    } catch (BlockPlacementException ex) {
                        failJob(targetLevel, nextState, ex.reasonCode(), ex.getMessage(), false);
                        continue;
                    } catch (Exception ex) {
                        failJob(targetLevel, nextState, "PLAN_FAILURE", ex.getMessage(), false);
                        continue;
                    }
                }

                BlockPlacementExecutor executor = new BlockPlacementExecutor(
                        targetLevel,
                        plan,
                        nextState.job().mode(),
                        resolveBlocksPerTick(nextState.job())
                );
                nextState.setPhase(JobPhase.PLACING);
                publishProgress(nextState, executor.placedBlocks(), executor.totalBlocks(), JobPhase.PLACING);
                currentJob = new ActiveJob(nextState, targetLevel, executor);
                sendChatToOwner(targetLevel, nextState, Component.literal("[EasyBuild] Starte Platzierung (" + nextState.job().mode() + ")"));
                break;
            }
        }

        if (currentJob != null) {
            ActiveJob active = currentJob;
            try {
                boolean finished = active.executor.tick();
                int placed = active.executor.placedBlocks();
                int total = active.executor.totalBlocks();
                JobPhase phase = finished ? JobPhase.COMPLETED : JobPhase.PLACING;
                publishProgress(active.state, placed, total, phase);
                EasyBuildPacketSender.sendTo(active.level, active.state.job().ownerUuid(), new ClientboundProgressUpdate(
                        active.state.job().jobId(),
                        placed,
                        total,
                        phase,
                        progressMessage(placed, total, finished),
                        ThreadLocalRandom.current().nextLong(),
                        System.currentTimeMillis()
                ));
                if (finished) {
                    completeJob(active.level, active.state, active.executor);
                    currentJob = null;
                }
            } catch (BlockPlacementException ex) {
                LOGGER.warn("Job {} failed during placement: {}", active.state.job().jobId(), ex.getMessage());
                failJob(active.level, active.state, ex.reasonCode(), ex.getMessage(), false);
                currentJob = null;
            } catch (Exception ex) {
                LOGGER.error("Unexpected error while running job {}", active.state.job().jobId(), ex);
                failJob(active.level, active.state, "UNEXPECTED_ERROR", ex.getMessage(), false);
                currentJob = null;
            }
        }
    }

    private void completeJob(ServerLevel level, BuildJobState state, BlockPlacementExecutor executor) {
        removeJob(state, JobPhase.COMPLETED);
        publishProgress(state, executor.totalBlocks(), executor.totalBlocks(), JobPhase.COMPLETED);
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

    private void failJob(ServerLevel level, BuildJobState state, String reasonCode, String details, boolean rolledBack) {
        removeJob(state, rolledBack ? JobPhase.ROLLING_BACK : JobPhase.CANCELLED);
        String safeDetails = (details == null || details.isBlank()) ? "Unbekannter Fehler" : details;
        EasyBuildPacketSender.sendTo(level, state.job().ownerUuid(), new ClientboundBuildFailed(
                state.job().jobId(),
                reasonCode,
                safeDetails,
                rolledBack,
                ThreadLocalRandom.current().nextLong(),
                System.currentTimeMillis()
        ));
        sendChatToOwner(level, state, Component.literal("[EasyBuild] Job " + state.job().jobId() + " fehlgeschlagen: " + safeDetails));
    }

    private void sendChatToOwner(ServerLevel level, BuildJobState state, Component message) {
        Optional.ofNullable(level.getServer().getPlayerList().getPlayer(state.job().ownerUuid()))
                .ifPresent(player -> sendChat(player, message));
    }

    private String progressMessage(int placed, int total, boolean finished) {
        if (finished) {
            return "Abgeschlossen";
        }
        if (total <= 0) {
            return "";
        }
        return placed + " / " + total;
    }

    private static final class ActiveJob {
        private final BuildJobState state;
        private final ServerLevel level;
        private final BlockPlacementExecutor executor;

        private ActiveJob(BuildJobState state, ServerLevel level, BlockPlacementExecutor executor) {
            this.state = state;
            this.level = level;
            this.executor = executor;
        }
    }
}
