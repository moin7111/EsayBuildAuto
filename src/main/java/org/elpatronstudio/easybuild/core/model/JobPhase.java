package org.elpatronstudio.easybuild.core.model;

/**
 * Represents the current stage of a server-side build job.
 */
public enum JobPhase {
    QUEUED,
    RESERVING,
    PLACING,
    PAUSED,
    ROLLING_BACK,
    COMPLETED,
    CANCELLED;
}
