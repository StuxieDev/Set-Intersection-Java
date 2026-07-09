package com.ridgwell.setintersection.cli;

import com.ridgwell.setintersection.keyset.Counts;
import com.ridgwell.setintersection.keyset.Overlap;

import java.io.PrintStream;

/** Writes the human-readable report: per-file counts followed by the two overlap figures. */
final class TableWriter {

    private TableWriter() {
    }

    static void write(PrintStream out, Counts c1, Counts c2, Overlap overlap) {
        out.printf("%-40s %12s %12s%n", "File", "Keys", "Distinct");
        out.printf("%-40s %12d %12d%n", c1.path(), c1.total(), c1.distinct());
        out.printf("%-40s %12d %12d%n", c2.path(), c2.total(), c2.distinct());
        out.println();
        out.printf("%-40s %12d%n", "Distinct overlap", overlap.distinct());
        out.printf("%-40s %12d%n", "Total overlap", overlap.total());
    }
}
