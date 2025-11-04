package org.elpatronstudio.easybuild.core.model;

/**
 * Status codes a client can report back to the server when acknowledging updates.
 */
public enum AcknowledgeStatusCode {
    MATERIALS_VIEWED,
    REQUEST_CONFIRMED,
    ABORT_CONFIRMED;
}
