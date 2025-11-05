package org.elpatronstudio.easybuild.server.job;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks mutually exclusive region locks for running EasyBuild jobs.
 */
public final class RegionLockManager {

    private static final RegionLockManager INSTANCE = new RegionLockManager();

    private final Map<ResourceKey<Level>, List<RegionLock>> locks = new ConcurrentHashMap<>();

    private RegionLockManager() {
    }

    public static RegionLockManager get() {
        return INSTANCE;
    }

    public LockResult tryAcquire(ResourceKey<Level> dimension, BlockRegion region, UUID ownerUuid, String ownerName, String jobId, long estimatedTicks) {
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(region, "region");
        Objects.requireNonNull(ownerUuid, "ownerUuid");
        Objects.requireNonNull(jobId, "jobId");
        String safeOwnerName = ownerName == null ? "" : ownerName;

        synchronized (locks) {
            List<RegionLock> entries = locks.computeIfAbsent(dimension, key -> new ArrayList<>());
            for (RegionLock existing : entries) {
                if (existing.region().intersects(region)) {
                    return LockResult.conflict(existing);
                }
            }

            RegionLock created = new RegionLock(dimension, region, ownerUuid, safeOwnerName, jobId, estimatedTicks, System.currentTimeMillis());
            entries.add(created);
            return LockResult.acquired(created);
        }
    }

    public void release(RegionLock lock) {
        if (lock == null) {
            return;
        }
        synchronized (locks) {
            List<RegionLock> entries = locks.get(lock.dimension());
            if (entries == null) {
                return;
            }
            entries.removeIf(existing -> existing.jobId().equals(lock.jobId()));
            if (entries.isEmpty()) {
                locks.remove(lock.dimension());
            }
        }
    }

    public Optional<RegionLock> findByJobId(String jobId) {
        if (jobId == null || jobId.isBlank()) {
            return Optional.empty();
        }
        synchronized (locks) {
            for (List<RegionLock> entries : locks.values()) {
                for (RegionLock lock : entries) {
                    if (lock.jobId().equals(jobId)) {
                        return Optional.of(lock);
                    }
                }
            }
        }
        return Optional.empty();
    }

    public List<RegionLock> getLocks(ResourceKey<Level> dimension) {
        synchronized (locks) {
            return new ArrayList<>(locks.getOrDefault(dimension, Collections.emptyList()));
        }
    }

    public record RegionLock(
            ResourceKey<Level> dimension,
            BlockRegion region,
            UUID ownerUuid,
            String ownerName,
            String jobId,
            long estimatedTicks,
            long lockedAt
    ) {
        public RegionLock {
            Objects.requireNonNull(dimension, "dimension");
            Objects.requireNonNull(region, "region");
            Objects.requireNonNull(ownerUuid, "ownerUuid");
            Objects.requireNonNull(jobId, "jobId");
            ownerName = ownerName == null ? "" : ownerName;
        }
    }

    public record LockResult(RegionLock acquired, RegionLock conflict) {

        public static LockResult acquired(RegionLock lock) {
            return new LockResult(Objects.requireNonNull(lock, "lock"), null);
        }

        public static LockResult conflict(RegionLock collision) {
            return new LockResult(null, Objects.requireNonNull(collision, "collision"));
        }

        public boolean success() {
            return acquired != null;
        }
    }
}
