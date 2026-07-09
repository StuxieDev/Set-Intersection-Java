package com.ridgwell.setintersection.keyset;

import com.ridgwell.setintersection.io.InputSource;
import com.ridgwell.setintersection.io.InputSourceFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Direct ports of keyset_test.go's TestCompare_* cases, plus an int-overflow regression test. */
class CompareTest {

    private static Path writeCsv(Path dir, String name, List<String> lines) throws IOException {
        Path path = dir.resolve(name);
        Files.writeString(path, String.join("\n", lines) + "\n");
        return path;
    }

    private static Counts load(Path path) throws IOException {
        InputSource source = InputSourceFactory.resolve(path.toString());
        return KeysetLoader.load(source, Options.singleColumn(false, "0", ','));
    }

    @Test
    void pdfWorkedExamplePinsDistinctAndTotalOverlap(@TempDir Path dir) throws IOException {
        // Dataset 1: A B C D D E F F
        // Dataset 2: A C C D F F F X Y
        // Distinct Overlap = A C D F = 4
        // Total Overlap = A C C D D F F F F F F = 11
        Path path1 = writeCsv(dir, "d1.csv", List.of("A", "B", "C", "D", "D", "E", "F", "F"));
        Path path2 = writeCsv(dir, "d2.csv", List.of("A", "C", "C", "D", "F", "F", "F", "X", "Y"));

        Counts c1 = load(path1);
        Counts c2 = load(path2);
        assertEquals(8, c1.total());
        assertEquals(9, c2.total());

        Overlap overlap = Compare.compare(c1, c2);
        assertEquals(4, overlap.distinct());
        assertEquals(11, overlap.total());
    }

    @Test
    void compareIsSymmetric(@TempDir Path dir) throws IOException {
        Path path1 = writeCsv(dir, "d1.csv", List.of("A", "B", "C", "D", "D", "E", "F", "F"));
        Path path2 = writeCsv(dir, "d2.csv", List.of("A", "C", "C", "D", "F", "F", "F", "X", "Y"));

        Counts c1 = load(path1);
        Counts c2 = load(path2);

        assertEquals(Compare.compare(c1, c2), Compare.compare(c2, c1));
    }

    @Test
    void noSharedKeysMeansZeroOverlap(@TempDir Path dir) throws IOException {
        Path path1 = writeCsv(dir, "d1.csv", List.of("A", "B"));
        Path path2 = writeCsv(dir, "d2.csv", List.of("C", "D"));

        Overlap overlap = Compare.compare(load(path1), load(path2));

        assertEquals(0, overlap.distinct());
        assertEquals(0, overlap.total());
    }

    @Test
    void totalOverlapUsesLongSoItDoesNotOverflowA32BitInt() {
        Counts a = new Counts("a");
        Counts b = new Counts("b");
        for (int i = 0; i < 60_000; i++) {
            a.recordKey("shared");
            b.recordKey("shared");
        }

        long expected = 60_000L * 60_000L;
        assertTrue(expected > Integer.MAX_VALUE, "test setup should exceed Integer.MAX_VALUE");

        Overlap overlap = Compare.compare(a, b);
        assertEquals(expected, overlap.total());
    }
}
