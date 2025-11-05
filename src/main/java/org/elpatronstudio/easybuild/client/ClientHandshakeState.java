package org.elpatronstudio.easybuild.client;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Maintains the last known EasyBuild handshake state on the client.
 */
public final class ClientHandshakeState {

    private static final ClientHandshakeState INSTANCE = new ClientHandshakeState();

    private volatile HandshakeSnapshot snapshot;
    private volatile FailureSnapshot failure;

    private ClientHandshakeState() {
    }

    public static ClientHandshakeState get() {
        return INSTANCE;
    }

    public synchronized void recordSuccess(String protocolVersion, String serverVersion, List<String> capabilities,
                                           String configHash, long nonce, long serverTime) {
        Objects.requireNonNull(protocolVersion, "protocolVersion");
        Objects.requireNonNull(serverVersion, "serverVersion");
        List<String> safeCapabilities = capabilities == null ? List.of() : List.copyOf(capabilities);
        this.snapshot = new HandshakeSnapshot(protocolVersion, serverVersion, safeCapabilities, configHash == null ? "" : configHash,
                nonce, serverTime, Instant.now());
        this.failure = null;
    }

    public synchronized void recordFailure(String reason, String requiredProtocol) {
        String safeReason = reason == null ? "" : reason;
        String safeRequired = requiredProtocol == null ? "" : requiredProtocol;
        this.snapshot = null;
        this.failure = new FailureSnapshot(safeReason, safeRequired, Instant.now());
    }

    public Optional<HandshakeSnapshot> snapshot() {
        return Optional.ofNullable(snapshot);
    }

    public Optional<FailureSnapshot> failure() {
        return Optional.ofNullable(failure);
    }

    public synchronized void clear() {
        this.snapshot = null;
        this.failure = null;
    }

    public record HandshakeSnapshot(String protocolVersion,
                                    String serverVersion,
                                    List<String> capabilities,
                                    String configHash,
                                    long nonce,
                                    long serverTime,
                                    Instant recordedAt) {
        public HandshakeSnapshot {
            capabilities = Collections.unmodifiableList(new ArrayList<>(capabilities));
        }
    }

    public record FailureSnapshot(String reason, String requiredProtocol, Instant recordedAt) {
    }
}
