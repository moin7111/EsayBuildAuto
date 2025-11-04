package org.elpatronstudio.easybuild.server.job;

import java.util.UUID;

/**
 * Utility for generating unique job identifiers.
 */
public final class JobIdGenerator {

    private JobIdGenerator() {
    }

    public static String nextId() {
        return UUID.randomUUID().toString().replace("-", "").toUpperCase();
    }
}
