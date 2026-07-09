package com.ridgwell.setintersection.keyset;

import com.ridgwell.setintersection.csv.CsvReader;

/**
 * The shared row-processing core used by both the serial and the chunked loading paths,
 * so "extract the key column(s), validate them, apply composite encoding if needed,
 * record the key" exists exactly once regardless of which path a file takes.
 */
final class RowCounter {

    // Resolved column index for each key spec, in order - one entry for a plain key,
    // more than one for a composite key.
    private final int[] keyColumnIndices;
    // Used only to label error messages with something identifying the file being read.
    private final String sourceLabel;

    /** {@code keyColumnIndices} is already resolved - by this point columns are indices, not names or specs. */
    RowCounter(int[] keyColumnIndices, String sourceLabel) {
        this.keyColumnIndices = keyColumnIndices;
        this.sourceLabel = sourceLabel;
    }

    /** Extracts the key from the reader's current record (applying composite encoding if needed) and records it in {@code counts}. */
    void consumeRow(CsvReader reader, Counts counts) throws KeysetException {
        int fieldCount = reader.fieldCount();
        String key;
        if (keyColumnIndices.length == 1) {
            // Ordinary single-column key - use the field value directly, with no composite encoding.
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

    /** Returns field {@code index} of the current record, or throws if the row doesn't have that many fields. */
    private String fieldAt(CsvReader reader, int index, int fieldCount) throws KeysetException {
        if (index < 0 || index >= fieldCount) {
            throw new KeysetException(sourceLabel + ": record " + reader.recordNumber()
                    + ": no column " + index + " (row has " + fieldCount + ")");
        }
        return reader.field(index);
    }
}
