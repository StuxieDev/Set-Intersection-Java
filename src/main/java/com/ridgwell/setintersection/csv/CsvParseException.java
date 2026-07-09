package com.ridgwell.setintersection.csv;

import java.io.IOException;

/** Signals malformed CSV input: an unterminated quoted field, or a bare quote outside one. */
public final class CsvParseException extends IOException {

    private final String sourceLabel;
    private final long recordNumber;

    public CsvParseException(String sourceLabel, long recordNumber, String detail) {
        super(sourceLabel + ": record " + recordNumber + ": " + detail);
        this.sourceLabel = sourceLabel;
        this.recordNumber = recordNumber;
    }

    public String sourceLabel() {
        return sourceLabel;
    }

    public long recordNumber() {
        return recordNumber;
    }
}
