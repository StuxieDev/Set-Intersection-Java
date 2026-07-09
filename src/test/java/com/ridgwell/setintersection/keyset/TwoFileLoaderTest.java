package com.ridgwell.setintersection.keyset;

import com.ridgwell.setintersection.io.RegularFileSource;
import com.ridgwell.setintersection.io.StdinSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TwoFileLoaderTest {

    private InputStream originalStdin;

    @BeforeEach
    void captureStdin() {
        originalStdin = System.in;
    }

    @AfterEach
    void restoreStdin() {
        System.setIn(originalStdin);
    }

    @Test
    void loadBothRunsBothFilesConcurrentlyAndCombinesTheirCounts(@TempDir Path dir) throws IOException {
        Path file1 = dir.resolve("d1.csv");
        Files.writeString(file1, "A\nB\nB\n");
        Path file2 = dir.resolve("d2.csv");
        Files.writeString(file2, "A\nA\nC\n");

        Counts[] counts = TwoFileLoader.loadBoth(
                new RegularFileSource(file1), Options.singleColumn(false, "0", ','),
                new RegularFileSource(file2), Options.singleColumn(false, "0", ','));

        assertEquals(3, counts[0].total());
        assertEquals(3, counts[1].total());
        assertEquals(4, counts[0].frequencyOf("B") + counts[1].frequencyOf("A"));
    }

    @Test
    void loadBothSurfacesFile1sErrorFirstWhenBothFail(@TempDir Path dir) {
        Path missing1 = dir.resolve("nope1.csv");
        Path missing2 = dir.resolve("nope2.csv");

        IOException ex = org.junit.jupiter.api.Assertions.assertThrows(IOException.class, () ->
                TwoFileLoader.loadBoth(
                        new RegularFileSource(missing1), Options.singleColumn(false, "0", ','),
                        new RegularFileSource(missing2), Options.singleColumn(false, "0", ',')));

        assertTrue(ex.getMessage().contains("nope1.csv"), "expected file1's error to surface first, got: " + ex.getMessage());
    }

    /**
     * Interrupts the thread that's blocked inside {@code loadBoth} and checks two things:
     * the interruption comes out as an {@link IOException} rather than a raw
     * {@link InterruptedException}, and the thread's interrupted flag ends up set again
     * afterward (easy to lose, since catching {@code InterruptedException} clears it).
     *
     * <p>To get a thread reliably blocked inside {@code loadBoth} without sleeps or races,
     * file2 reads from stdin, and stdin is swapped for a stream whose {@code read()} signals
     * a latch before parking forever - so the test knows exactly when to interrupt.
     */
    @Test
    void interruptingTheCallingThreadIsPropagatedAsIOExceptionWithTheInterruptFlagRestored(@TempDir Path dir) throws Exception {
        Path quick = dir.resolve("quick.csv");
        Files.writeString(quick, "A\nB\n");

        CountDownLatch blockedNow = new CountDownLatch(1);
        System.setIn(new InputStream() {
            @Override
            public int read() throws IOException {
                blockedNow.countDown();
                try {
                    Thread.sleep(Long.MAX_VALUE);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("interrupted", e);
                }
                return -1;
            }
        });

        AtomicReference<Throwable> thrown = new AtomicReference<>();
        AtomicBoolean interruptedAfter = new AtomicBoolean();
        Thread worker = new Thread(() -> {
            try {
                TwoFileLoader.loadBoth(
                        new StdinSource(), Options.singleColumn(false, "0", ','),
                        new RegularFileSource(quick), Options.singleColumn(false, "0", ','));
            } catch (Throwable t) {
                thrown.set(t);
            } finally {
                interruptedAfter.set(Thread.currentThread().isInterrupted());
            }
        });

        worker.start();
        assertTrue(blockedNow.await(5, TimeUnit.SECONDS), "worker never reached the blocking read");
        worker.interrupt();
        worker.join(TimeUnit.SECONDS.toMillis(5));

        assertInstanceOf(IOException.class, thrown.get());
        assertTrue(interruptedAfter.get(), "interrupted flag should be restored before loadBoth returns");
    }
}
