package com.ridgwell.setintersection.cli;

/** Turns a {@code -delimiter}/{@code -delimiter1}/{@code -delimiter2} flag value into a single delimiter character. */
final class DelimiterParser {

    private DelimiterParser() {
    }

    /**
     * Accepts a literal one-character string (e.g. {@code ";"}), plus the shell-friendly
     * two-character escape {@code \t} as an alias for an actual tab, since typing a real
     * tab character on a command line is awkward.
     */
    static char parse(String value) throws CliUsageException {
        if (value.isEmpty() || value.equals(",")) {
            return ',';
        }
        if (value.equals("\\t") || value.equals("\t")) {
            return '\t';
        }
        if (value.length() != 1) {
            throw new CliUsageException("-delimiter must be a single character, got '" + value + "'");
        }
        char c = value.charAt(0);
        if (c > 0x7F) {
            throw new CliUsageException("-delimiter must be an ASCII character, got '" + value + "'");
        }
        return c;
    }
}
