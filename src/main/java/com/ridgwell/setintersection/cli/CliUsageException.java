package com.ridgwell.setintersection.cli;

/** A user-facing CLI error: bad flag syntax, an unknown flag, or a failed validation check. */
final class CliUsageException extends Exception {

    // Never actually serialized (this is a CLI, not a distributed app) - just here to
    // satisfy the Serializable contract Exception carries and quiet the compiler warning.
    private static final long serialVersionUID = 1L;

    CliUsageException(String message) {
        super(message);
    }
}
