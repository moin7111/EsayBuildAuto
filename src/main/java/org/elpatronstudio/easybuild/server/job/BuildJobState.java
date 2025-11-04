package org.elpatronstudio.easybuild.server.job;

import org.elpatronstudio.easybuild.core.model.JobPhase;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Mutable state holder for an active build job.
 */
public final class BuildJobState {

    private final BuildJob job;
    private final UUID reservationToken;
    private final AtomicInteger placed = new AtomicInteger();
    private final AtomicInteger total = new AtomicInteger();
    private volatile JobPhase phase = JobPhase.QUEUED;
    private volatile BlockPlacementPlan plan;

    public BuildJobState(BuildJob job, UUID reservationToken) {
        this.job = Objects.requireNonNull(job, "job");
        this.reservationToken = Objects.requireNonNull(reservationToken, "reservationToken");
    }

    public BuildJob job() {
        return job;
    }

    public UUID reservationToken() {
        return reservationToken;
    }

    public JobPhase phase() {
        return phase;
    }

    public int placed() {
        return placed.get();
    }

    public int total() {
        return total.get();
    }

    public BlockPlacementPlan plan() {
        return plan;
    }

    public void updateProgress(int placed, int total, JobPhase phase) {
        this.placed.set(Math.max(0, placed));
        this.total.set(Math.max(0, total));
        this.phase = Objects.requireNonNull(phase, "phase");
    }

    public void setPhase(JobPhase phase) {
        this.phase = Objects.requireNonNull(phase, "phase");
    }

    public void attachPlan(BlockPlacementPlan plan) {
        this.plan = plan;
    }
}
