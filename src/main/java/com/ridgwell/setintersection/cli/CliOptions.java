package com.ridgwell.setintersection.cli;

/** Raw parsed flag values, before validation or resolution into {@code keyset.Options}. */
record CliOptions(
        String file1,
        String file2,
        boolean header,
        String column,
        String column1,
        String column2,
        String delimiter,
        String delimiter1,
        String delimiter2,
        boolean json,
        boolean help
) {
}
