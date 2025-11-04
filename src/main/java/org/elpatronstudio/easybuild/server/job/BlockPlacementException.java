package org.elpatronstudio.easybuild.server.job;

/**
 * Exception thrown when preparing or executing a block placement plan fails.
 */
public final class BlockPlacementException extends Exception {

    private final String reasonCode;

    public BlockPlacementException(String reasonCode, String message) {
        super(message);
        this.reasonCode = reasonCode;
    }

    public String reasonCode() {
        return reasonCode;
    }
}
