package com.ridgwell.setintersection.cli;

import com.ridgwell.setintersection.keyset.Counts;
import com.ridgwell.setintersection.keyset.Overlap;

import java.io.PrintStream;

/**
 * Writes the same report as {@link TableWriter}, but as JSON - hand-rolled and scoped
 * only to this tool's one fixed output shape, not a general-purpose JSON library, since
 * pulling one in would violate the "no frameworks" constraint.
 */
final class JsonWriter {

    private JsonWriter() {
    }

    static void write(PrintStream out, Counts c1, Counts c2, Overlap overlap) {
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"files\": [\n");
        appendFile(json, c1, true);
        appendFile(json, c2, false);
        json.append("  ],\n");
        json.append("  \"distinct_overlap\": ").append(overlap.distinct()).append(",\n");
        json.append("  \"total_overlap\": ").append(overlap.total()).append('\n');
        json.append('}');
        out.println(json);
    }

    private static void appendFile(StringBuilder json, Counts c, boolean hasMore) {
        json.append("    {\n");
        json.append("      \"path\": \"").append(escape(c.path())).append("\",\n");
        json.append("      \"keys\": ").append(c.total()).append(",\n");
        json.append("      \"distinct\": ").append(c.distinct()).append('\n');
        json.append("    }").append(hasMore ? ",\n" : "\n");
    }

    /** Windows paths contain backslashes, and paths could in principle contain quotes - both must be escaped to stay valid JSON. */
    static String escape(String value) {
        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\\' -> out.append("\\\\");
                case '"' -> out.append("\\\"");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.toString();
    }
}
