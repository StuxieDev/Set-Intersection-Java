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
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Direct ports of keyset_test.go's TestLoad_* cases. */
class KeysetLoaderTest {

    private static Path writeCsv(Path dir, String name, List<String> lines) throws IOException {
        Path path = dir.resolve(name);
        Files.writeString(path, String.join("\n", lines) + "\n");
        return path;
    }

    private static Counts load(Path path, Options opts) throws IOException {
        InputSource source = InputSourceFactory.resolve(path.toString());
        return KeysetLoader.load(source, opts);
    }

    @Test
    void noHeaderCountsEveryRowAsAKey(@TempDir Path dir) throws IOException {
        Path path = writeCsv(dir, "data.csv", List.of("A", "B", "C", "D", "D", "E", "F", "F"));

        Counts c = load(path, Options.singleColumn(false, "0", ','));

        assertEquals(8, c.total());
        assertEquals(6, c.distinct());
        assertEquals(2, c.frequencyOf("D"));
        assertEquals(2, c.frequencyOf("F"));
    }

    @Test
    void headerByNameResolvesTheColumn(@TempDir Path dir) throws IOException {
        Path path = writeCsv(dir, "data.csv", List.of(
                "id,udprn,name",
                "1,30433784,Alice",
                "2,08034283,Bob",
                "3,30433784,Carol"));

        Counts c = load(path, Options.singleColumn(true, "udprn", ','));

        assertEquals(3, c.total());
        assertEquals(2, c.distinct());
        assertEquals(2, c.frequencyOf("30433784"));
    }

    @Test
    void headerByIndexPreservesLeadingZeros(@TempDir Path dir) throws IOException {
        Path path = writeCsv(dir, "data.csv", List.of(
                "id,udprn",
                "1,00012345",
                "2,00067890"));

        Counts c = load(path, Options.singleColumn(true, "1", ','));

        assertEquals(2, c.distinct());
        assertEquals(1, c.frequencyOf("00012345"));
    }

    @Test
    void unknownHeaderColumnNameIsAnError(@TempDir Path dir) throws IOException {
        Path path = writeCsv(dir, "data.csv", List.of("id,udprn", "1,00012345"));

        assertThrows(IOException.class,
                () -> load(path, Options.singleColumn(true, "does-not-exist", ',')));
    }

    @Test
    void outOfRangeColumnIndexIsAnError(@TempDir Path dir) throws IOException {
        Path path = writeCsv(dir, "data.csv", List.of("A", "B"));

        assertThrows(IOException.class,
                () -> load(path, Options.singleColumn(false, "5", ',')));
    }

    @Test
    void missingFileIsAnError(@TempDir Path dir) {
        Path missing = dir.resolve("nope.csv");

        assertThrows(IOException.class,
                () -> load(missing, Options.singleColumn(false, "0", ',')));
    }
}
