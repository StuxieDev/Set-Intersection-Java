package com.ridgwell.setintersection.keyset;

import com.ridgwell.setintersection.csv.CsvReader;

/**
 * The shared row-processing core used by both the serial and the chunked loading paths,
 * so "extract the key column(s), validate them, apply composite encoding if needed,
 * record the key" exists exactly once regardless of which path a file takes.
 */
final class RowCounter {

    private final int[] keyColumnIndices;
    private final String sourceLabel;

    RowCounter(int[] keyColumnIndices, String sourceLabel) {
        this.keyColumnIndices = keyColumnIndices;
        this.sourceLabel = sourceLabel;
    }

    void consumeRow(CsvReader reader, Counts counts) throws KeysetException {
        int fieldCount = reader.fieldCount();
        String key;
        if (keyColumnIndices.length == 1) {
            key = fieldAt(reader, keyColumnIndices[0], fieldCount);
        } else {
            String[] parts = new String[keyColumnIndices.length];
            for (int i = 0; i < keyColumnIndices.length; i++) {
                parts[i] = fieldAt(reader, keyColumnIndices[i], fieldCount);
            }
            key = CompositeKeyEncoder.encode(parts);
        }
        counts.recordKey(key);
    }

    private String fieldAt(CsvReader reader, int index, int fieldCount) throws KeysetException {
        if (index < 0 || index >= fieldCount) {
            throw new KeysetException(sourceLabel + ": record " + reader.recordNumber()
                    + ": no column " + index + " (row has " + fieldCount + ")");
        }
        return reader.field(index);
    }
}
