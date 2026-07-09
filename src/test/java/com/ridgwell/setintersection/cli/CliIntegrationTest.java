package com.ridgwell.setintersection.cli;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** End-to-end equivalent of Go's cmd/main_test.go, driven through {@link Cli#run} with captured streams. */
class CliIntegrationTest {

    private InputStream originalStdin;

    @BeforeEach
    void captureStdin() {
        originalStdin = System.in;
    }

    @AfterEach
    void restoreStdin() {
        System.setIn(originalStdin);
    }

    private record Result(int exitCode, String stdout, String stderr) {
    }

    private static Result run(String... args) {
        ByteArrayOutputStream outBuf = new ByteArrayOutputStream();
        ByteArrayOutputStream errBuf = new ByteArrayOutputStream();
        int code = Cli.run(args,
                new PrintStream(outBuf, true, StandardCharsets.UTF_8),
                new PrintStream(errBuf, true, StandardCharsets.UTF_8));
        return new Result(code, outBuf.toString(StandardCharsets.UTF_8), errBuf.toString(StandardCharsets.UTF_8));
    }

    private static Path writeCsv(Path dir, String name, List<String> lines) throws IOException {
        Path path = dir.resolve(name);
        Files.writeString(path, String.join("\n", lines) + "\n");
        return path;
    }

    private static Path[] pdfExampleFiles(Path dir) throws IOException {
        Path file1 = writeCsv(dir, "d1.csv", List.of("A", "B", "C", "D", "D", "E", "F", "F"));
        Path file2 = writeCsv(dir, "d2.csv", List.of("A", "C", "C", "D", "F", "F", "F", "X", "Y"));
        return new Path[] {file1, file2};
    }

    @Test
    void pdfExampleTableOutput(@TempDir Path dir) throws IOException {
        Path[] files = pdfExampleFiles(dir);

        Result result = run("-file1", files[0].toString(), "-file2", files[1].toString());

        assertEquals(0, result.exitCode(), result.stderr());
        for (String expected : List.of("Distinct overlap", "4", "Total overlap", "11")) {
            assertTrue(result.stdout().contains(expected), "missing '" + expected + "' in:\n" + result.stdout());
        }
    }

    @Test
    void pdfExampleJsonOutputMatchesExactly(@TempDir Path dir) throws IOException {
        Path[] files = pdfExampleFiles(dir);

        Result result = run("-file1", files[0].toString(), "-file2", files[1].toString(), "-json");

        assertEquals(0, result.exitCode(), result.stderr());
        String expected = "{\n"
                + "  \"files\": [\n"
                + "    {\n"
                + "      \"path\": \"" + JsonWriter.escape(files[0].toString()) + "\",\n"
                + "      \"keys\": 8,\n"
                + "      \"distinct\": 6\n"
                + "    },\n"
                + "    {\n"
                + "      \"path\": \"" + JsonWriter.escape(files[1].toString()) + "\",\n"
                + "      \"keys\": 9,\n"
                + "      \"distinct\": 6\n"
                + "    }\n"
                + "  ],\n"
                + "  \"distinct_overlap\": 4,\n"
                + "  \"total_overlap\": 11\n"
                + "}\n";
        assertEquals(expected, result.stdout());
    }

    @Test
    void customTsvDelimiterWithHeader(@TempDir Path dir) throws IOException {
        Path file1 = writeCsv(dir, "d1.tsv", List.of("id\tudprn", "1\t00012345", "2\t00067890"));
        Path file2 = writeCsv(dir, "d2.tsv", List.of("id\tudprn", "1\t00012345", "2\t00099999"));

        Result result = run("-file1", file1.toString(), "-file2", file2.toString(),
                "-header", "-column", "udprn", "-delimiter", "\\t");

        assertEquals(0, result.exitCode(), result.stderr());
        assertTrue(result.stdout().contains("Distinct overlap"));
    }

    @Test
    void missingRequiredArgsIsAnError(@TempDir Path dir) throws IOException {
        Path[] files = pdfExampleFiles(dir);

        Result result = run("-file1", files[0].toString());

        assertEquals(1, result.exitCode());
        assertTrue(result.stderr().contains("error:"));
    }

    @Test
    void compositeColumnOverridePerFile(@TempDir Path dir) throws IOException {
        Path file1 = writeCsv(dir, "d1.csv", List.of("a,b,c", "X,Y,ignored", "X,Y,ignored2"));
        Path file2 = writeCsv(dir, "d2.csv", List.of("a,b,c", "X,Y,other"));

        Result result = run("-file1", file1.toString(), "-file2", file2.toString(),
                "-header", "-column1", "a,b", "-column2", "a,b", "-json");

        assertEquals(0, result.exitCode(), result.stderr());
        assertTrue(result.stdout().contains("\"distinct_overlap\": 1"));
        assertTrue(result.stdout().contains("\"total_overlap\": 2"));
    }

    @Test
    void gzipInputIsReadTransparently(@TempDir Path dir) throws IOException {
        Path file1 = writeCsv(dir, "d1.csv", List.of("A", "B", "B"));
        Path file2gz = dir.resolve("d2.csv.gz");
        try (OutputStream out = new GZIPOutputStream(Files.newOutputStream(file2gz))) {
            out.write("A\nA\nC\n".getBytes(StandardCharsets.UTF_8));
        }

        Result result = run("-file1", file1.toString(), "-file2", file2gz.toString(), "-json");

        assertEquals(0, result.exitCode(), result.stderr());
        assertTrue(result.stdout().contains("\"distinct_overlap\": 1"));
        assertTrue(result.stdout().contains("\"total_overlap\": 2"));
    }

    @Test
    void stdinIsAcceptedForOneFile(@TempDir Path dir) throws IOException {
        Path file1 = writeCsv(dir, "d1.csv", List.of("A", "B", "B"));
        System.setIn(new ByteArrayInputStream("A\nA\nC\n".getBytes(StandardCharsets.UTF_8)));

        Result result = run("-file1", file1.toString(), "-file2", "-", "-json");

        assertEquals(0, result.exitCode(), result.stderr());
        assertTrue(result.stdout().contains("\"distinct_overlap\": 1"));
    }

    @Test
    void bothFilesAsStdinIsRejected() {
        Result result = run("-file1", "-", "-file2", "-");

        assertEquals(1, result.exitCode());
        assertTrue(result.stderr().contains("stdin"));
    }

    @Test
    void helpFlagPrintsUsageAndExitsZero() {
        Result result = run("-help");

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("Usage:"));
    }

    /** {@code -h} is a separate registered flag from {@code -help} - this exercises that alias on its own, not alongside {@code -help}. */
    @Test
    void hShortFlagAloneAlsoPrintsUsageAndExitsZero() {
        Result result = run("-h");

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("Usage:"));
    }

    /**
     * {@code missingRequiredArgsIsAnError} exercises a {@code CliUsageException} thrown
     * from inside {@code runWithOptions} (a missing required flag). This one instead makes
     * {@code ArgParser.parse} itself fail - an unrecognized flag - which is caught at a
     * different point in {@code Cli.run}, before {@code runWithOptions} is ever reached.
     */
    @Test
    void unrecognizedFlagIsAnErrorBeforeRunWithOptionsIsReached(@TempDir Path dir) throws IOException {
        Path[] files = pdfExampleFiles(dir);

        Result result = run("-file1", files[0].toString(), "-file2", files[1].toString(), "-not-a-real-flag");

        assertEquals(1, result.exitCode());
        assertTrue(result.stderr().contains("error:"));
        assertTrue(result.stderr().contains("Usage:"));
    }

    /**
     * Every other error-path test here fails validation before any file is touched
     * (missing flags, bad flag syntax, both sides as stdin). This one gets all the way to
     * actually trying to load a file that doesn't exist, so it's a genuine {@link IOException}
     * from {@code TwoFileLoader.loadBoth} propagating out through {@code Cli.run}'s other
     * catch clause, not a {@code CliUsageException}.
     */
    @Test
    void missingInputFileProducesAFriendlyErrorRatherThanACrash(@TempDir Path dir) throws IOException {
        Path file1 = writeCsv(dir, "d1.csv", List.of("A", "B"));
        Path missing = dir.resolve("does-not-exist.csv");

        Result result = run("-file1", file1.toString(), "-file2", missing.toString());

        assertEquals(1, result.exitCode());
        assertTrue(result.stderr().contains("error:"));
        assertTrue(result.stderr().contains("does-not-exist.csv"), "expected the missing path in the error, got: " + result.stderr());
    }

    /** An empty entry in a composite spec (a stray comma) is caught by {@code Cli.splitColumns}, not by column resolution further down. */
    @Test
    void emptyEntryInACompositeColumnSpecIsAnError(@TempDir Path dir) throws IOException {
        Path[] files = pdfExampleFiles(dir);

        Result result = run("-file1", files[0].toString(), "-file2", files[1].toString(), "-column", "a,,b");

        assertEquals(1, result.exitCode());
        assertTrue(result.stderr().contains("error:"));
    }
}
