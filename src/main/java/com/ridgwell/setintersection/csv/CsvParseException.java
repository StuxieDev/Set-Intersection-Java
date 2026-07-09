package com.ridgwell.setintersection.csv;

import java.io.IOException;

/** Signals malformed CSV input: an unterminated quoted field, or a bare quote outside one. */
public final class CsvParseException extends IOException {

    private static final long serialVersionUID = 1L;

    private final String sourceLabel;
    private final long recordNumber;

    /**
     * @param sourceLabel  identifies which stream this error came from (e.g. a file path)
     * @param recordNumber the 1-based record number being read when the error occurred
     * @param detail       what specifically was wrong with the input
     */
    public CsvParseException(String sourceLabel, long recordNumber, String detail) {
        super(sourceLabel + ": record " + recordNumber + ": " + detail);
        this.sourceLabel = sourceLabel;
        this.recordNumber = recordNumber;
    }

    /** The source label passed to the constructor. */
    public String sourceLabel() {
        return sourceLabel;
    }

    /** The 1-based record number that was being read when parsing failed. */
    public long recordNumber() {
        return recordNumber;
    }
}
