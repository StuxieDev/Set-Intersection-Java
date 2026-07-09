package com.ridgwell.setintersection.keyset;

import com.ridgwell.setintersection.io.GzipFileSource;
import com.ridgwell.setintersection.io.RegularFileSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GzipLoadTest {

    @Test
    void gzipFileProducesTheSameCountsAsItsUncompressedEquivalent(@TempDir Path dir) throws IOException {
        String content = "a\nb\nb\nc\nc\nc\n";

        Path plain = dir.resolve("data.csv");
        Files.writeString(plain, content);

        Path gzipped = dir.resolve("data.csv.gz");
        try (OutputStream out = new GZIPOutputStream(Files.newOutputStream(gzipped))) {
            out.write(content.getBytes(StandardCharsets.UTF_8));
        }

        Options opts = Options.singleColumn(false, "0", ',');
        Counts plainCounts = KeysetLoader.load(new RegularFileSource(plain), opts);
        Counts gzipCounts = KeysetLoader.load(new GzipFileSource(gzipped), opts);

        assertEquals(plainCounts.total(), gzipCounts.total());
        assertEquals(plainCounts.distinct(), gzipCounts.distinct());
        assertEquals(3, gzipCounts.frequencyOf("c"));
    }

    @Test
    void gzipSourceIsCorrectEvenAboveTheRegularChunkingSizeThreshold(@TempDir Path dir) throws IOException {
        Path gzipped = dir.resolve("big.csv.gz");
        StringBuilder content = new StringBuilder();
        for (int i = 0; i < 5000; i++) {
            content.append("key-").append(i % 100).append('\n');
        }
        try (OutputStream out = new GZIPOutputStream(Files.newOutputStream(gzipped))) {
            out.write(content.toString().getBytes(StandardCharsets.UTF_8));
        }

        // KeysetLoader.load must take the serial path for gzip regardless of size - if it
        // tried to chunk a compressed file's on-disk byte offsets, this would produce wrong
        // counts (or throw). A correct result here is itself the behavioural proof that
        // GzipFileSource is never routed through the chunked loader.
        Counts counts = KeysetLoader.load(new GzipFileSource(gzipped), Options.singleColumn(false, "0", ','));

        assertEquals(5000, counts.total());
        assertEquals(100, counts.distinct());
    }
}
