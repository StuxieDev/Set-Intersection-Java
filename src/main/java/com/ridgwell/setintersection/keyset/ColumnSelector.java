package com.ridgwell.setintersection.keyset;

import java.util.Arrays;
import java.util.List;

/** Resolves column specs (numeric index or header name) to concrete zero-based indices. */
final class ColumnSelector {

    private ColumnSelector() {
    }

    /**
     * @param headerRow the parsed header row, required (non-null) when {@code hasHeader}
     *                   is true; ignored otherwise
     */
    static int[] resolve(List<String> specs, boolean hasHeader, String[] headerRow) throws KeysetException {
        int[] indices = new int[specs.size()];
        for (int i = 0; i < specs.size(); i++) {
            indices[i] = resolveOne(specs.get(i), hasHeader, headerRow);
        }
        return indices;
    }

    private static int resolveOne(String spec, boolean hasHeader, String[] headerRow) throws KeysetException {
        try {
            return Integer.parseInt(spec);
        } catch (NumberFormatException notNumeric) {
            if (!hasHeader) {
                throw new KeysetException(
                        "column '" + spec + "' must be a numeric index when -header is not set");
            }
            int index = indexOf(headerRow, spec);
            if (index == -1) {
                throw new KeysetException(
                        "column '" + spec + "' not found in header " + Arrays.toString(headerRow));
            }
            return index;
        }
    }

    private static int indexOf(String[] header, String name) {
        for (int i = 0; i < header.length; i++) {
            if (header[i].equals(name)) {
                return i;
            }
        }
        return -1;
    }
}
