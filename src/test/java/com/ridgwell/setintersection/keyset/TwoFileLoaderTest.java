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
import static org.junit.jupiter.api.Assertions.assertThrows;
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

        IOException ex = assertThrows(IOException.class, () ->
                TwoFileLoader.loadBoth(
                        new RegularFileSource(missing1), Options.singleColumn(false, "0", ','),
                        new RegularFileSource(missing2), Options.singleColumn(false, "0", ',')));

        assertTrue(ex.getMessage().contains("nope1.csv"), "expected file1's error to surface first, got: " + ex.getMessage());
    }

    /** The mirror of the test above with the roles reversed - file1 succeeds, so it's file2's error that has to surface. */
    @Test
    void loadBothSurfacesFile2sErrorWhenOnlyFile2Fails(@TempDir Path dir) throws IOException {
        Path file1 = dir.resolve("d1.csv");
        Files.writeString(file1, "A\nB\n");
        Path missing2 = dir.resolve("nope2.csv");

        IOException ex = assertThrows(IOException.class, () ->
                TwoFileLoader.loadBoth(
                        new RegularFileSource(file1), Options.singleColumn(false, "0", ','),
                        new RegularFileSource(missing2), Options.singleColumn(false, "0", ',')));

        assertTrue(ex.getMessage().contains("nope2.csv"), "expected file2's error, got: " + ex.getMessage());
    }

    /**
     * {@code KeysetLoader.load} pattern-matches on the source, which throws a plain
     * {@link NullPointerException} - not an {@link IOException} - if the source is
     * {@code null}. That's a real (if unusual) way for a chunk's task to fail with a
     * non-{@code IOException} cause, so it's a more honest way to reach that branch than
     * fabricating one.
     */
    @Test
    void loadBothWrapsANonIOExceptionCauseInAnIOException(@TempDir Path dir) throws IOException {
        Path file2 = dir.resolve("d2.csv");
        Files.writeString(file2, "A\nB\n");

        IOException ex = assertThrows(IOException.class, () ->
                TwoFileLoader.loadBoth(
                        null, Options.singleColumn(false, "0", ','),
                        new RegularFileSource(file2), Options.singleColumn(false, "0", ',')));

        assertInstanceOf(NullPointerException.class, ex.getCause());
    }

    /**
     * Interrupts the thread that's blocked inside {@code loadBoth} and checks two things:
     * the interruption comes out as an {@link IOException} rather than a raw
     * {@link InterruptedException}, and the thread's interrupted flag ends up set again
     * afterward (easy to lose, since catching {@code InterruptedException} clears it).
     *
     * <p>To get a thread reliably blocked inside {@code loadBoth} without sleeps or races,
     * file1 reads from stdin, and stdin is swapped for a stream whose {@code read()} signals
     * a latch before parking forever - so the test knows exactly when to interrupt. Since
     * {@code loadBoth} waits on {@code future1} before {@code future2}, this exercises
     * file1's catch block specifically; see the next test for file2's.
     */
    @Test
    void interruptingWhileWaitingOnFile1IsPropagatedAsIOExceptionWithTheInterruptFlagRestored(@TempDir Path dir) throws Exception {
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
        assertTrue(thrown.get().getMessage().contains("<stdin>"),
                "expected file1's (the blocking one here, since file1 = StdinSource) path in the message, got: "
                        + thrown.get().getMessage());
        assertTrue(interruptedAfter.get(), "interrupted flag should be restored before loadBoth returns");
    }

    /**
     * Same idea as the test above, but with file1/file2 swapped: file1 is a quick real
     * file, so once it's done the thread ends up blocked in {@code future2.get()} instead -
     * the branch the previous test can't reach. There's no ordering guarantee between which
     * of the two submitted virtual-thread tasks the scheduler runs first, so "the stdin task
     * reached its blocking read" alone doesn't prove file1's future has already resolved -
     * only that its own task has started. The extra short sleep after the latch gives
     * file1's two-line read (already OS page-cache warm) a generous window to finish first;
     * the message assertion below is what actually catches it if that assumption ever
     * turns out to be wrong, rather than silently accepting whichever branch happened to fire.
     */
    @Test
    void interruptingWhileWaitingOnFile2IsPropagatedAsIOExceptionWithTheInterruptFlagRestored(@TempDir Path dir) throws Exception {
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
                        new RegularFileSource(quick), Options.singleColumn(false, "0", ','),
                        new StdinSource(), Options.singleColumn(false, "0", ','));
            } catch (Throwable t) {
                thrown.set(t);
            } finally {
                interruptedAfter.set(Thread.currentThread().isInterrupted());
            }
        });

        worker.start();
        assertTrue(blockedNow.await(5, TimeUnit.SECONDS), "worker never reached the blocking read");
        Thread.sleep(200);
        worker.interrupt();
        worker.join(TimeUnit.SECONDS.toMillis(5));

        assertInstanceOf(IOException.class, thrown.get());
        assertTrue(thrown.get().getMessage().contains("<stdin>"),
                "expected file2's (the blocking one here) path in the message, got: " + thrown.get().getMessage());
        assertTrue(interruptedAfter.get(), "interrupted flag should be restored before loadBoth returns");
    }
}
