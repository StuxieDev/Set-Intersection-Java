package com.ridgwell.setintersection.cli;

import com.ridgwell.setintersection.io.InputSource;
import com.ridgwell.setintersection.io.InputSourceFactory;
import com.ridgwell.setintersection.keyset.Compare;
import com.ridgwell.setintersection.keyset.Counts;
import com.ridgwell.setintersection.keyset.Options;
import com.ridgwell.setintersection.keyset.Overlap;
import com.ridgwell.setintersection.keyset.TwoFileLoader;

import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses arguments, loads both files, compares them, and writes the report. Returns an
 * exit code and writes to injected streams rather than {@code System.exit}/{@code System.out}
 * directly, so it can be driven end-to-end from a test with captured output.
 */
public final class Cli {

    private static final String PROGRAM_NAME = "set-intersection";

    private Cli() {
    }

    public static int run(String[] args, PrintStream out, PrintStream err) {
        ArgParser parser = buildParser();
        try {
            parser.parse(args);
        } catch (CliUsageException e) {
            return usageError(err, parser, e);
        }

        if (parser.getBoolean("help") || parser.getBoolean("h")) {
            out.print(parser.usage(PROGRAM_NAME));
            return 0;
        }

        try {
            runWithOptions(toCliOptions(parser), out);
            return 0;
        } catch (CliUsageException e) {
            return usageError(err, parser, e);
        } catch (IOException e) {
            err.println("error: " + e.getMessage());
            return 1;
        }
    }

    private static int usageError(PrintStream err, ArgParser parser, CliUsageException e) {
        err.println("error: " + e.getMessage());
        err.print(parser.usage(PROGRAM_NAME));
        return 1;
    }

    private static void runWithOptions(CliOptions cli, PrintStream out) throws CliUsageException, IOException {
        if (isBlank(cli.file1()) || isBlank(cli.file2())) {
            throw new CliUsageException("-file1 and -file2 are required");
        }
        if (cli.file1().equals("-") && cli.file2().equals("-")) {
            throw new CliUsageException("only one of -file1/-file2 may be \"-\" (stdin)");
        }

        char delimiter1 = DelimiterParser.parse(orDefault(cli.delimiter1(), cli.delimiter()));
        char delimiter2 = DelimiterParser.parse(orDefault(cli.delimiter2(), cli.delimiter()));

        List<String> columns1 = splitColumns(orDefault(cli.column1(), cli.column()));
        List<String> columns2 = splitColumns(orDefault(cli.column2(), cli.column()));

        Options options1 = new Options(cli.header(), columns1, delimiter1);
        Options options2 = new Options(cli.header(), columns2, delimiter2);

        InputSource source1 = InputSourceFactory.resolve(cli.file1());
        InputSource source2 = InputSourceFactory.resolve(cli.file2());

        Counts[] counts = TwoFileLoader.loadBoth(source1, options1, source2, options2);
        Overlap overlap = Compare.compare(counts[0], counts[1]);

        if (cli.json()) {
            JsonWriter.write(out, counts[0], counts[1], overlap);
        } else {
            TableWriter.write(out, counts[0], counts[1], overlap);
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isEmpty();
    }

    private static String orDefault(String override, String fallback) {
        return isBlank(override) ? fallback : override;
    }

    private static List<String> splitColumns(String spec) throws CliUsageException {
        String[] parts = spec.split(",");
        List<String> columns = new ArrayList<>(parts.length);
        for (String part : parts) {
            if (part.isEmpty()) {
                throw new CliUsageException("column spec '" + spec + "' has an empty entry");
            }
            columns.add(part);
        }
        return columns;
    }

    private static ArgParser buildParser() {
        return new ArgParser()
                .stringFlag("file1", "", "path to the first CSV file (required; \"-\" for stdin)")
                .stringFlag("file2", "", "path to the second CSV file (required; \"-\" for stdin)")
                .boolFlag("header", false, "set if the CSV files have a header row")
                .stringFlag("column", "0", "key column: index, header name, or comma-separated list for a composite key")
                .stringFlag("column1", "", "override -column for file1")
                .stringFlag("column2", "", "override -column for file2")
                .stringFlag("delimiter", ",", "field delimiter, e.g. ';' or '\\t' for TSV")
                .stringFlag("delimiter1", "", "override -delimiter for file1")
                .stringFlag("delimiter2", "", "override -delimiter for file2")
                .boolFlag("json", false, "print the result as JSON instead of a table")
                .boolFlag("help", false, "print usage and exit")
                .boolFlag("h", false, "alias for -help");
    }

    private static CliOptions toCliOptions(ArgParser parser) {
        return new CliOptions(
                parser.getString("file1"),
                parser.getString("file2"),
                parser.getBoolean("header"),
                parser.getString("column"),
                parser.getString("column1"),
                parser.getString("column2"),
                parser.getString("delimiter"),
                parser.getString("delimiter1"),
                parser.getString("delimiter2"),
                parser.getBoolean("json"),
                parser.getBoolean("help") || parser.getBoolean("h")
        );
    }
}
