package com.genealogy.platform.services.research.application.persistence;

/**
 * Shared helper for research repositories. Each repository
 * binds an aggregate id to a tenant via the
 * {@code ResearchRlsTxInterceptor} and uses the same
 * {@code etagFor} helper so the wire format stays consistent.
 */
public final class RepositorySupport {

    private RepositorySupport() {
        // utility
    }

    /**
     * Returns the canonical wire-level ETag for a given aggregate
     * version. The format is {@code "v<N>"} so the value is
     * distinguishable from the random KSUID/UUID proposal id and
     * can be sent back as {@code If-Match} by the caller.
     */
    public static String etagFor(long version) {
        return "\"v" + version + "\"";
    }

    /** Round-trip parse for the ETag. Accepts both quoted and bare forms. */
    public static long parseEtag(String ifMatch) {
        if (ifMatch == null || ifMatch.isBlank() || "\"\"".equals(ifMatch)) {
            throw new OptimisticConcurrencyException(
                    "If-Match header is required for mutations");
        }
        String trimmed = ifMatch.trim();
        if (trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            trimmed = trimmed.substring(1, trimmed.length() - 1);
        }
        if (trimmed.startsWith("v")) {
            trimmed = trimmed.substring(1);
        }
        try {
            return Long.parseLong(trimmed);
        } catch (NumberFormatException nfe) {
            throw new OptimisticConcurrencyException(
                    "If-Match header is not a recognised version: " + ifMatch);
        }
    }

    public static class OptimisticConcurrencyException extends RuntimeException {
        public OptimisticConcurrencyException(String message) {
            super(message);
        }
    }
}
