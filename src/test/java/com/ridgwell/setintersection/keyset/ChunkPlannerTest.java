package com.ridgwell.setintersection.keyset;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
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

    /**
     * A file with no newline anywhere has nowhere for any interior boundary to land, so
     * every target the scan can't satisfy before EOF has to fall back to {@code fileSize} -
     * collapsing what was asked for as several chunks into one. This is the safe
     * degradation the class docs describe, exercised directly rather than as a side effect
     * of some other scenario.
     */
    @Test
    void ranOutOfFileBeforeFindingEveryTargetFallsBackToFileSize(@TempDir Path dir) throws IOException {
        Path path = dir.resolve("no-newlines.csv");
        Files.writeString(path, "abcdefghij");
        long size = Files.size(path);

        List<ChunkRange> ranges = ChunkPlanner.plan(path, 0, size, 3);

        assertRangesReconstructWholeFileWithNoGapsOrOverlaps(ranges, 0, size);
        assertEquals(1, ranges.size(), "with no newlines anywhere, the whole file must collapse into a single range");
    }

    @Test
    void skipFullyFallsBackToSingleByteReadsWhenSkipReturnsZero() throws IOException {
        byte[] data = "abcdefghij".getBytes(StandardCharsets.UTF_8);
        InputStream zeroSkipStream = new InputStream() {
            private int pos = 0;

            @Override
            public int read() {
                return pos < data.length ? data[pos++] : -1;
            }

            @Override
            public long skip(long n) {
                return 0; // pretend skip never makes progress, forcing the fallback path
            }
        };

        ChunkPlanner.skipFully(zeroSkipStream, 5);

        assertEquals('f', zeroSkipStream.read(), "the next unread byte after skipping 5 of 'abcdefghij' should be 'f'");
    }

    @Test
    void skipFullyStopsCleanlyAtEofEvenIfMoreWasRequestedThanTheStreamHas() throws IOException {
        InputStream shortStream = new ByteArrayInputStream("abc".getBytes(StandardCharsets.UTF_8)) {
            @Override
            public long skip(long n) {
                return 0; // force the single-byte fallback path here too
            }
        };

        ChunkPlanner.skipFully(shortStream, 100);

        assertEquals(-1, shortStream.read(), "should have stopped at EOF rather than hanging or throwing");
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
