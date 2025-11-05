package org.elpatronstudio.easybuild.client.state;

import net.minecraft.util.Mth;
import org.elpatronstudio.easybuild.client.model.SchematicFileEntry;
import org.elpatronstudio.easybuild.core.model.BuildMode;
import org.elpatronstudio.easybuild.core.model.ChestRef;
import org.elpatronstudio.easybuild.core.model.JobPhase;
import org.elpatronstudio.easybuild.core.model.MaterialStack;
import org.elpatronstudio.easybuild.core.model.PasteMode;
import org.elpatronstudio.easybuild.core.model.SchematicRef;
import org.elpatronstudio.easybuild.core.network.packet.ClientboundBuildAccepted;
import org.elpatronstudio.easybuild.core.network.packet.ClientboundBuildCompleted;
import org.elpatronstudio.easybuild.core.network.packet.ClientboundBuildFailed;
import org.elpatronstudio.easybuild.core.network.packet.ClientboundMaterialCheckResponse;
import org.elpatronstudio.easybuild.core.network.packet.ClientboundMissingMaterials;
import org.elpatronstudio.easybuild.core.network.packet.ClientboundProgressUpdate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Central client-side state store used by the schematic builder GUI, input handlers, and network listeners.
 */
public final class EasyBuildClientState {

    private static final EasyBuildClientState INSTANCE = new EasyBuildClientState();
    private static final int MAX_JOB_HISTORY = 16;

    private final List<SchematicFileEntry> availableSchematics = new ArrayList<>();
    private final Map<String, SchematicFileEntry> schematicsById = new HashMap<>();
    private final Set<ChestRef> selectedChests = new LinkedHashSet<>();
    private final Map<String, MaterialStatus> materialStatuses = new HashMap<>();
    private final LinkedHashMap<String, ClientBuildJob> buildJobs = new LinkedHashMap<>();
    private final Map<String, PendingBuildRequest> pendingBuildRequests = new HashMap<>();

    private SchematicFileEntry selectedSchematic;
    private BuildMode buildMode = BuildMode.SELF;
    private boolean chestSelectionActive;
    private boolean reopenGuiAfterSelection;
    private double previewForwardOffset;

    private EasyBuildClientState() {
    }

    public static EasyBuildClientState get() {
        return INSTANCE;
    }

    public synchronized void reset() {
        availableSchematics.clear();
        schematicsById.clear();
        selectedSchematic = null;
        buildMode = BuildMode.SELF;
        chestSelectionActive = false;
        reopenGuiAfterSelection = false;
        previewForwardOffset = 0.0D;
        selectedChests.clear();
        materialStatuses.clear();
        buildJobs.clear();
        pendingBuildRequests.clear();
    }

    public synchronized void setAvailableSchematics(List<SchematicFileEntry> entries) {
        availableSchematics.clear();
        schematicsById.clear();
        for (SchematicFileEntry entry : entries) {
            availableSchematics.add(entry);
            schematicsById.put(normalizeId(entry.id()), entry);
            schematicsById.put(normalizeId(entry.ref().schematicId()), entry);
        }
        if (selectedSchematic != null && !availableSchematics.contains(selectedSchematic)) {
            selectedSchematic = availableSchematics.isEmpty() ? null : availableSchematics.get(0);
        }
    }

    public synchronized List<SchematicFileEntry> availableSchematics() {
        return Collections.unmodifiableList(new ArrayList<>(availableSchematics));
    }

    public synchronized Optional<SchematicFileEntry> findSchematic(SchematicRef ref) {
        if (ref == null) {
            return Optional.empty();
        }
        return findSchematic(ref.schematicId());
    }

    public synchronized Optional<SchematicFileEntry> findSchematic(String schematicId) {
        if (schematicId == null || schematicId.isBlank()) {
            return Optional.empty();
        }
        String normalized = normalizeId(schematicId);
        SchematicFileEntry direct = schematicsById.get(normalized);
        if (direct != null) {
            return Optional.of(direct);
        }
        // Fallback to linear search in case multiple entries share ids with different casing
        return availableSchematics.stream()
                .filter(entry -> normalizeId(entry.id()).equals(normalized)
                        || normalizeId(entry.ref().schematicId()).equals(normalized))
                .findFirst();
    }

    private static String normalizeId(String id) {
        return id == null ? "" : id.replace('\\', '/').toLowerCase(Locale.ROOT);
    }

    public synchronized Optional<SchematicFileEntry> selectedSchematic() {
        return Optional.ofNullable(selectedSchematic);
    }

    public synchronized void selectSchematic(SchematicFileEntry entry) {
        if (entry == null) {
            selectedSchematic = null;
            return;
        }
        if (!availableSchematics.contains(entry)) {
            throw new IllegalArgumentException("Schematic is not part of the current repository");
        }
        selectedSchematic = entry;
    }

    public synchronized BuildMode buildMode() {
        return buildMode;
    }

    public synchronized void setBuildMode(BuildMode mode) {
        buildMode = Objects.requireNonNull(mode, "mode");
    }

    public synchronized boolean isChestSelectionActive() {
        return chestSelectionActive;
    }

    public synchronized void setChestSelectionActive(boolean active) {
        chestSelectionActive = active;
    }

    public synchronized void requestReopenGuiAfterSelection() {
        reopenGuiAfterSelection = true;
    }

    public synchronized boolean consumeReopenGuiRequest() {
        boolean flag = reopenGuiAfterSelection;
        reopenGuiAfterSelection = false;
        return flag;
    }

    public synchronized Set<ChestRef> selectedChests() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(selectedChests));
    }

    public synchronized boolean toggleChest(ChestRef ref) {
        Objects.requireNonNull(ref, "ref");
        if (selectedChests.remove(ref)) {
            return false;
        }
        selectedChests.add(ref);
        return true;
    }

    public synchronized void setSelectedChests(Iterable<ChestRef> chests) {
        selectedChests.clear();
        if (chests == null) {
            return;
        }
        for (ChestRef ref : chests) {
            if (ref != null) {
                selectedChests.add(ref);
            }
        }
    }

    public synchronized void clearChestSelection() {
        selectedChests.clear();
    }

    public synchronized double previewForwardOffset() {
        return previewForwardOffset;
    }

    public synchronized void adjustPreviewForwardOffset(double delta) {
        previewForwardOffset = Mth.clamp(previewForwardOffset + delta, -256.0D, 256.0D);
    }

    public synchronized void resetPreviewOffset() {
        previewForwardOffset = 0.0D;
    }

    // Material check -----------------------------------------------------------------------------

    public synchronized void recordMaterialCheck(ClientboundMaterialCheckResponse response) {
        MaterialStatus status = new MaterialStatus(
                response.schematic(),
                response.ok(),
                response.missing(),
                response.reserved(),
                response.reservationExpiresAt(),
                List.of(),
                System.currentTimeMillis()
        );
        materialStatuses.put(response.schematic().schematicId(), status);
    }

    public synchronized void recordMissingMaterials(ClientboundMissingMaterials response) {
        MaterialStatus previous = materialStatuses.get(response.schematic().schematicId());
        boolean reserved = previous != null && previous.reserved();
        long reservationExpiresAt = previous != null ? previous.reservationExpiresAt() : 0L;
        MaterialStatus updated = new MaterialStatus(
                response.schematic(),
                false,
                response.missing(),
                reserved,
                reservationExpiresAt,
                response.suggestedSources(),
                System.currentTimeMillis()
        );
        materialStatuses.put(response.schematic().schematicId(), updated);
    }

    public synchronized Optional<MaterialStatus> materialStatus(SchematicRef schematic) {
        return Optional.ofNullable(materialStatuses.get(schematic.schematicId()));
    }

    public synchronized Optional<MaterialStatus> materialStatus(String schematicId) {
        return Optional.ofNullable(materialStatuses.get(schematicId));
    }

    public synchronized List<MaterialStatus> materialStatuses() {
        return List.copyOf(materialStatuses.values());
    }

    public synchronized void clearMaterialStatus(SchematicRef schematic) {
        materialStatuses.remove(schematic.schematicId());
    }

    // Build requests and jobs -------------------------------------------------------------------

    public synchronized void registerPendingBuildRequest(PendingBuildRequest request) {
        pendingBuildRequests.put(request.requestId(), request);
    }

    public synchronized void recordBuildAccepted(ClientboundBuildAccepted message) {
        PendingBuildRequest pending = pendingBuildRequests.remove(message.clientRequestId());
        BuildMode mode = BuildMode.INSTA;
        PasteMode pasteMode = message.mode();
        String displayName = message.schematic().schematicId();
        if (pending != null) {
            mode = pending.buildMode();
            pasteMode = pending.pasteMode();
            displayName = pending.displayName();
        }

        ClientBuildJob job = new ClientBuildJob(
                message.jobId(),
                message.schematic(),
                displayName,
                mode,
                pasteMode,
                message.clientRequestId(),
                true
        );
        job.setReservationToken(message.reservationToken());
        job.setEstimatedDurationTicks(message.estimatedDurationTicks());
        job.setPhase(JobPhase.QUEUED);
        job.setLastMessage("Queued");
        job.setLastUpdate(System.currentTimeMillis());

        buildJobs.put(message.jobId(), job);
        trimJobHistory();
    }

    public synchronized void recordProgressUpdate(ClientboundProgressUpdate message) {
        ClientBuildJob job = buildJobs.get(message.jobId());
        if (job == null) {
            job = new ClientBuildJob(
                    message.jobId(),
                    message.schematic(),
                    message.schematic().schematicId(),
                    BuildMode.INSTA,
                    PasteMode.ATOMIC,
                    message.jobId(),
                    true
            );
            buildJobs.put(message.jobId(), job);
            trimJobHistory();
        }

        job.updateProgress(message.placed(), message.total(), message.phase(), message.message());
        job.setLastUpdate(System.currentTimeMillis());
        reorderJob(job.jobId(), job);
    }

    public synchronized void recordBuildCompleted(ClientboundBuildCompleted message) {
        ClientBuildJob job = buildJobs.get(message.jobId());
        if (job == null) {
            job = new ClientBuildJob(
                    message.jobId(),
                    message.schematic(),
                    message.schematic().schematicId(),
                    BuildMode.INSTA,
                    PasteMode.ATOMIC,
                    message.jobId(),
                    true
            );
            buildJobs.put(message.jobId(), job);
            trimJobHistory();
        }
        job.complete(message.success(), message.consumed());
        job.setLastMessage(message.success() ? "Completed" : "Completed with issues");
        job.setLastUpdate(System.currentTimeMillis());
        reorderJob(job.jobId(), job);
    }

    public synchronized void recordBuildFailed(ClientboundBuildFailed message) {
        PendingBuildRequest pending = pendingBuildRequests.remove(message.clientRequestId());
        ClientBuildJob job = buildJobs.get(message.jobId());
        if (job == null) {
            BuildMode mode = pending != null ? pending.buildMode() : BuildMode.INSTA;
            PasteMode pasteMode = pending != null ? pending.pasteMode() : PasteMode.ATOMIC;
            String displayName = pending != null ? pending.displayName() : message.schematic().schematicId();
            job = new ClientBuildJob(message.jobId(), message.schematic(), displayName, mode, pasteMode, message.clientRequestId(), true);
            buildJobs.put(message.jobId(), job);
            trimJobHistory();
        }
        job.fail(message.reasonCode(), message.details(), message.rollbackPerformed());
        job.setLastUpdate(System.currentTimeMillis());
        reorderJob(job.jobId(), job);
    }

    public synchronized List<ClientBuildJob> jobs() {
        return List.copyOf(buildJobs.values());
    }

    public synchronized Optional<ClientBuildJob> job(String jobId) {
        return Optional.ofNullable(buildJobs.get(jobId));
    }

    public synchronized Optional<ClientBuildJob> latestJob() {
        if (buildJobs.isEmpty()) {
            return Optional.empty();
        }
        List<ClientBuildJob> ordered = new ArrayList<>(buildJobs.values());
        return Optional.of(ordered.get(ordered.size() - 1));
    }

    public synchronized List<ClientBuildJob> recentJobs(int limit) {
        if (limit <= 0 || buildJobs.isEmpty()) {
            return List.of();
        }
        List<ClientBuildJob> ordered = new ArrayList<>(buildJobs.values());
        Collections.reverse(ordered);
        if (ordered.size() > limit) {
            ordered = ordered.subList(0, limit);
        }
        return List.copyOf(ordered);
    }

    public synchronized Optional<ClientBuildJob> activeJob() {
        if (buildJobs.isEmpty()) {
            return Optional.empty();
        }
        long now = System.currentTimeMillis();
        for (ClientBuildJob job : recentJobs(buildJobs.size())) {
            if (!job.completed() && job.phase() != JobPhase.CANCELLED) {
                if (now - job.lastUpdate() <= 15000L) {
                    return Optional.of(job);
                }
            }
        }
        return Optional.empty();
    }

    private void reorderJob(String jobId, ClientBuildJob job) {
        buildJobs.remove(jobId);
        buildJobs.put(jobId, job);
    }

    private void trimJobHistory() {
        while (buildJobs.size() > MAX_JOB_HISTORY) {
            String oldestKey = buildJobs.keySet().iterator().next();
            buildJobs.remove(oldestKey);
        }
    }

    // Data classes ------------------------------------------------------------------------------

    public record MaterialStatus(SchematicRef schematic,
                                 boolean ok,
                                 List<MaterialStack> missing,
                                 boolean reserved,
                                 long reservationExpiresAt,
                                 List<ChestRef> suggestedSources,
                                 long updatedAtMillis) {

        public MaterialStatus {
            Objects.requireNonNull(schematic, "schematic");
            missing = List.copyOf(missing);
            suggestedSources = List.copyOf(suggestedSources);
        }

        public boolean ready() {
            return ok && missing.isEmpty();
        }
    }

    public static final class ClientBuildJob {

        private final String jobId;
        private final SchematicRef schematic;
        private final String displayName;
        private final BuildMode buildMode;
        private final PasteMode pasteMode;
        private final String clientRequestId;
        private final boolean serverSide;

        private UUID reservationToken;
        private long estimatedDurationTicks;
        private int placed;
        private int total;
        private JobPhase phase = JobPhase.QUEUED;
        private boolean completed;
        private boolean success;
        private boolean rollback;
        private String failureReason = "";
        private String lastMessage = "";
        private List<MaterialStack> consumedMaterials = List.of();
        private long lastUpdate;

        private ClientBuildJob(String jobId, SchematicRef schematic, String displayName, BuildMode buildMode,
                               PasteMode pasteMode, String clientRequestId, boolean serverSide) {
            this.jobId = jobId;
            this.schematic = schematic;
            this.displayName = displayName;
            this.buildMode = buildMode;
            this.pasteMode = pasteMode;
            this.clientRequestId = clientRequestId;
            this.serverSide = serverSide;
        }

        public String jobId() {
            return jobId;
        }

        public SchematicRef schematic() {
            return schematic;
        }

        public String displayName() {
            return displayName;
        }

        public BuildMode buildMode() {
            return buildMode;
        }

        public PasteMode pasteMode() {
            return pasteMode;
        }

        public String clientRequestId() {
            return clientRequestId;
        }

        public boolean serverSide() {
            return serverSide;
        }

        public Optional<UUID> reservationToken() {
            return Optional.ofNullable(reservationToken);
        }

        public long estimatedDurationTicks() {
            return estimatedDurationTicks;
        }

        public int placed() {
            return placed;
        }

        public int total() {
            return total;
        }

        public JobPhase phase() {
            return phase;
        }

        public boolean completed() {
            return completed;
        }

        public boolean success() {
            return success;
        }

        public boolean rollbackPerformed() {
            return rollback;
        }

        public String failureReason() {
            return failureReason;
        }

        public String lastMessage() {
            return lastMessage;
        }

        public List<MaterialStack> consumedMaterials() {
            return consumedMaterials;
        }

        public long lastUpdate() {
            return lastUpdate;
        }

        private void setReservationToken(UUID reservationToken) {
            this.reservationToken = reservationToken;
        }

        private void setEstimatedDurationTicks(long estimatedDurationTicks) {
            this.estimatedDurationTicks = estimatedDurationTicks;
        }

        private void setPhase(JobPhase phase) {
            this.phase = phase;
        }

        private void updateProgress(int placed, int total, JobPhase phase, String message) {
            this.placed = placed;
            this.total = total;
            this.phase = phase;
            this.lastMessage = message == null ? "" : message;
            if (phase == JobPhase.COMPLETED) {
                this.completed = true;
                this.success = true;
            }
        }

        private void complete(boolean success, List<MaterialStack> consumed) {
            this.completed = true;
            this.success = success;
            this.phase = JobPhase.COMPLETED;
            this.consumedMaterials = List.copyOf(consumed);
        }

        private void fail(String reasonCode, String details, boolean rollback) {
            this.completed = true;
            this.success = false;
            this.phase = JobPhase.CANCELLED;
            this.rollback = rollback;
            this.failureReason = reasonCode;
            this.lastMessage = details == null ? reasonCode : details;
        }

        private void setLastMessage(String message) {
            this.lastMessage = message;
        }

        private void setLastUpdate(long timestamp) {
            this.lastUpdate = timestamp;
        }
    }

    public record PendingBuildRequest(String requestId,
                                      SchematicRef schematic,
                                      String displayName,
                                      BuildMode buildMode,
                                      PasteMode pasteMode,
                                      long createdAt) {
        public PendingBuildRequest {
            Objects.requireNonNull(requestId, "requestId");
            Objects.requireNonNull(schematic, "schematic");
            Objects.requireNonNull(displayName, "displayName");
            Objects.requireNonNull(buildMode, "buildMode");
            Objects.requireNonNull(pasteMode, "pasteMode");
        }
    }
}
