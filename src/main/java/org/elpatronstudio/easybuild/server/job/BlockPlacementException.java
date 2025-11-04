package org.elpatronstudio.easybuild.server.job;

/**
 * Exception thrown when preparing or executing a block placement plan fails.
 */
final class BlockPlacementException extends Exception {

    private final String reasonCode;

    BlockPlacementException(String reasonCode, String message) {
        super(message);
        this.reasonCode = reasonCode;
    }

    String reasonCode() {
        return reasonCode;
    }
}
