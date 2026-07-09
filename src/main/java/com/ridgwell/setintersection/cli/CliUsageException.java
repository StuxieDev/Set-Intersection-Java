package com.ridgwell.setintersection.cli;

/** A user-facing CLI error: bad flag syntax, an unknown flag, or a failed validation check. */
final class CliUsageException extends Exception {
    CliUsageException(String message) {
        super(message);
    }
}
