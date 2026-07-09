package com.ridgwell.setintersection.keyset;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChunkPlannerTest {

    @Test
    void boundariesLandExactlyOnRecordStartsForAPlainFile(@TempDir Path dir) throws IOException {
        Path path = dir.resolve("plain.csv");
        StringBuilder content = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            content.append("row").append(i).append('\n');
        }
        Files.writeString(path, content.toString());
        long size = Files.size(path);

        List<ChunkRange> ranges = ChunkPlanner.plan(path, 0, size, 4);

        assertRangesReconstructWholeFileWithNoGapsOrOverlaps(ranges, 0, size);
        assertEveryRangeStartsRightAfterANewline(ranges, path);
    }

    @Test
    void multiLineQuotedFieldNeverGetsTornAtAnEmbeddedNewline(@TempDir Path dir) throws IOException {
        String quotedField = "line1\nline2\nline3\nline4\nline5";
        String firstRecord = "a,\"" + quotedField + "\"\n";
        String content = firstRecord + "b,c\nd,e\n";

        Path path = dir.resolve("multiline.csv");
        Files.writeString(path, content);
        long size = Files.size(path);
        long firstRecordEnd = firstRecord.getBytes(StandardCharsets.UTF_8).length;

        List<ChunkRange> ranges = ChunkPlanner.plan(path, 0, size, 4);

        assertRangesReconstructWholeFileWithNoGapsOrOverlaps(ranges, 0, size);
        for (ChunkRange range : ranges) {
            if (range.start() > 0) {
                assertTrue(range.start() >= firstRecordEnd,
                        "boundary at " + range.start() + " fell inside the quoted field (ends at " + firstRecordEnd + ")");
            }
        }
        assertEveryRangeStartsRightAfterANewline(ranges, path);
    }

    @Test
    void doubledQuotesDoNotFalselyToggleQuoteState(@TempDir Path dir) throws IOException {
        Path path = dir.resolve("escaped.csv");
        Files.writeString(path, "a,\"say \"\"hi\"\" now\"\nb,c\nd,e\n");
        long size = Files.size(path);

        List<ChunkRange> ranges = ChunkPlanner.plan(path, 0, size, 3);

        assertRangesReconstructWholeFileWithNoGapsOrOverlaps(ranges, 0, size);
        assertEveryRangeStartsRightAfterANewline(ranges, path);
    }

    @Test
    void crlfLineEndingsPlanCleanly(@TempDir Path dir) throws IOException {
        Path path = dir.resolve("crlf.csv");
        StringBuilder content = new StringBuilder();
        for (int i = 0; i < 50; i++) {
            content.append("row").append(i).append("\r\n");
        }
        Files.writeString(path, content.toString());
        long size = Files.size(path);

        List<ChunkRange> ranges = ChunkPlanner.plan(path, 0, size, 4);

        assertRangesReconstructWholeFileWithNoGapsOrOverlaps(ranges, 0, size);
    }

    @Test
    void headerOffsetIsRespectedAsTheStartOfTheFirstRange(@TempDir Path dir) throws IOException {
        Path path = dir.resolve("headered.csv");
        Files.writeString(path, "id,name\n1,a\n2,b\n3,c\n");
        long size = Files.size(path);
        long headerEnd = "id,name\n".getBytes(StandardCharsets.UTF_8).length;

        List<ChunkRange> ranges = ChunkPlanner.plan(path, headerEnd, size, 2);

        assertRangesReconstructWholeFileWithNoGapsOrOverlaps(ranges, headerEnd, size);
    }

    @Test
    void targetChunkCountOfOneReturnsTheWholeRangeUnsplit(@TempDir Path dir) throws IOException {
        Path path = dir.resolve("small.csv");
        Files.writeString(path, "a,b\nc,d\n");
        long size = Files.size(path);

        List<ChunkRange> ranges = ChunkPlanner.plan(path, 0, size, 1);

        assertEquals(List.of(new ChunkRange(0, size)), ranges);
    }

    private static void assertRangesReconstructWholeFileWithNoGapsOrOverlaps(List<ChunkRange> ranges, long start, long end) {
        assertFalse(ranges.isEmpty());
        long cursor = start;
        for (ChunkRange range : ranges) {
            assertEquals(cursor, range.start(), "gap or overlap detected");
            assertTrue(range.end() > range.start(), "empty range");
            cursor = range.end();
        }
        assertEquals(end, cursor, "ranges must cover up to fileSize exactly");
    }

    private static void assertEveryRangeStartsRightAfterANewline(List<ChunkRange> ranges, Path path) throws IOException {
        byte[] bytes = Files.readAllBytes(path);
        for (ChunkRange range : ranges) {
            if (range.start() > 0) {
                assertEquals('\n', (char) bytes[(int) range.start() - 1],
                        "byte immediately before chunk start " + range.start() + " must be a newline");
            }
        }
    }
}
