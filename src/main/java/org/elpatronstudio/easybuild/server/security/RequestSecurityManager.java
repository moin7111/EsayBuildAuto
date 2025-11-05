package org.elpatronstudio.easybuild.server.security;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Provides lightweight rate limiting and nonce replay protection for EasyBuild requests.
 */
public final class RequestSecurityManager {

    private static final int MAX_TRACKED_NONCES = 64;

    private static final RequestSecurityManager INSTANCE = new RequestSecurityManager();

    private final Map<UUID, PlayerSecurityState> states = new ConcurrentHashMap<>();

    private RequestSecurityManager() {
    }

    public static RequestSecurityManager get() {
        return INSTANCE;
    }

    public RateLimitResult checkRateLimit(UUID playerUuid, RequestType type, long now) {
        Objects.requireNonNull(playerUuid, "playerUuid");
        Objects.requireNonNull(type, "type");
        PlayerSecurityState state = states.computeIfAbsent(playerUuid, PlayerSecurityState::new);
        synchronized (state) {
            SlidingWindow window = state.rateLimits.computeIfAbsent(type, key -> new SlidingWindow(type.windowMs, type.maxRequests));
            return window.tryAcquire(now);
        }
    }

    public NonceResult verifyNonce(UUID playerUuid, RequestType type, long nonce) {
        Objects.requireNonNull(playerUuid, "playerUuid");
        Objects.requireNonNull(type, "type");
        if (nonce == 0L) {
            return NonceResult.invalid("Nonce fehlt oder ist null");
        }
        PlayerSecurityState state = states.computeIfAbsent(playerUuid, PlayerSecurityState::new);
        synchronized (state) {
            NonceTracker tracker = state.nonceTrackers.computeIfAbsent(type, key -> new NonceTracker());
            return tracker.accept(nonce) ? NonceResult.ok() : NonceResult.replayed();
        }
    }

    public void clear(UUID playerUuid) {
        if (playerUuid == null) {
            return;
        }
        states.remove(playerUuid);
    }

    private static final class PlayerSecurityState {
        private final UUID playerUuid;
        private final EnumMap<RequestType, SlidingWindow> rateLimits = new EnumMap<>(RequestType.class);
        private final EnumMap<RequestType, NonceTracker> nonceTrackers = new EnumMap<>(RequestType.class);

        private PlayerSecurityState(UUID playerUuid) {
            this.playerUuid = playerUuid;
        }
    }

    private static final class SlidingWindow {
        private final long windowMs;
        private final int maxRequests;
        private final ArrayDeque<Long> timestamps = new ArrayDeque<>();

        private SlidingWindow(long windowMs, int maxRequests) {
            this.windowMs = windowMs;
            this.maxRequests = maxRequests;
        }

        private RateLimitResult tryAcquire(long now) {
            while (!timestamps.isEmpty() && now - timestamps.peekFirst() > windowMs) {
                timestamps.pollFirst();
            }
            if (timestamps.size() >= maxRequests) {
                long retryAfter = windowMs - Math.max(0L, now - timestamps.peekFirst());
                return RateLimitResult.rejected(retryAfter);
            }
            timestamps.addLast(now);
            return RateLimitResult.ok();
        }
    }

    private static final class NonceTracker {
        private final ArrayDeque<Long> recent = new ArrayDeque<>();
        private final Set<Long> seen = new HashSet<>();

        private boolean accept(long nonce) {
            if (seen.contains(nonce)) {
                return false;
            }
            recent.addLast(nonce);
            seen.add(nonce);
            if (recent.size() > MAX_TRACKED_NONCES) {
                Long removed = recent.removeFirst();
                seen.remove(removed);
            }
            return true;
        }
    }

    public enum RequestType {
        HANDSHAKE(2, Duration.ofSeconds(10).toMillis()),
        MATERIAL_CHECK(4, Duration.ofSeconds(8).toMillis()),
        BUILD_REQUEST(3, Duration.ofSeconds(15).toMillis()),
        CANCEL_REQUEST(6, Duration.ofSeconds(10).toMillis()),
        STATUS_ACK(8, Duration.ofSeconds(10).toMillis());

        private final int maxRequests;
        private final long windowMs;

        RequestType(int maxRequests, long windowMs) {
            this.maxRequests = maxRequests;
            this.windowMs = windowMs;
        }
    }

    public record RateLimitResult(boolean allowed, long retryAfterMs) {

        private static RateLimitResult ok() {
            return new RateLimitResult(true, 0L);
        }

        private static RateLimitResult rejected(long retryAfterMs) {
            return new RateLimitResult(false, Math.max(0L, retryAfterMs));
        }
    }

    public record NonceResult(boolean valid, String reason) {

        private static NonceResult ok() {
            return new NonceResult(true, "");
        }

        private static NonceResult invalid(String reason) {
            return new NonceResult(false, reason);
        }

        private static NonceResult replayed() {
            return new NonceResult(false, "Nonce wurde erneut verwendet");
        }
    }
}
