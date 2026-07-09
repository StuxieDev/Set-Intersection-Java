package com.ridgwell.setintersection.keyset;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Direct tests for {@code ChunkedCsvLoader.BoundedInputStream}. Wrapping it in a
 * {@code BufferedInputStream} the way {@code ChunkedCsvLoader} actually uses it only ever
 * exercises the bulk {@code read(byte[], int, int)} override (that's what
 * {@code BufferedInputStream} calls to refill), so the single-byte {@code read()} override
 * needs to be driven directly to get covered at all.
 */
class BoundedInputStreamTest {

    @Test
    void singleByteReadStopsExactlyAtTheLimit() throws IOException {
        try (var bounded = new ChunkedCsvLoader.BoundedInputStream(new ByteArrayInputStream(new byte[] {1, 2, 3, 4, 5}), 3)) {
            assertEquals(1, bounded.read());
            assertEquals(2, bounded.read());
            assertEquals(3, bounded.read());
            assertEquals(-1, bounded.read());
            assertEquals(-1, bounded.read());
        }
    }

    @Test
    void bulkReadClampsToWhateverIsLeftInTheRange() throws IOException {
        try (var bounded = new ChunkedCsvLoader.BoundedInputStream(new ByteArrayInputStream(new byte[] {1, 2, 3, 4, 5}), 3)) {
            byte[] buf = new byte[10];
            int n = bounded.read(buf, 0, buf.length);

            assertEquals(3, n, "should stop at the limit even though the buffer and delegate both have more");
            assertEquals(-1, bounded.read(buf, 0, buf.length), "nothing left once the limit is reached");
        }
    }

    @Test
    void bulkReadAcrossMultipleCallsAccumulatesCorrectly() throws IOException {
        try (var bounded = new ChunkedCsvLoader.BoundedInputStream(new ByteArrayInputStream(new byte[] {1, 2, 3, 4, 5}), 4)) {
            byte[] buf = new byte[2];
            assertEquals(2, bounded.read(buf, 0, 2));
            assertEquals(2, bounded.read(buf, 0, 2));
            assertEquals(-1, bounded.read(buf, 0, 2), "limit of 4 reached, byte 5 must not be visible");
        }
    }

    @Test
    void zeroLimitMeansImmediateEofForBothReadForms() throws IOException {
        try (var bounded = new ChunkedCsvLoader.BoundedInputStream(new ByteArrayInputStream(new byte[] {1, 2, 3}), 0)) {
            assertEquals(-1, bounded.read());
            assertEquals(-1, bounded.read(new byte[4], 0, 4));
        }
    }

    @Test
    void closeDelegatesToTheUnderlyingStream() throws IOException {
        boolean[] closed = {false};
        var delegate = new ByteArrayInputStream(new byte[0]) {
            @Override
            public void close() throws IOException {
                closed[0] = true;
                super.close();
            }
        };

        try (var bounded = new ChunkedCsvLoader.BoundedInputStream(delegate, 5)) {
            // closing happens via try-with-resources below
        }

        assertEquals(true, closed[0]);
    }
}
