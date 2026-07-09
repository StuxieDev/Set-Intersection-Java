package com.ridgwell.setintersection.keyset;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Direct tests for {@code ChunkedCsvLoader.await}, covering both its catch branches without needing a real chunk to fail in exactly the right way. */
class ChunkedCsvLoaderAwaitTest {

    @Test
    void returnsTheResultWhenTheFutureCompletesNormally() throws IOException {
        Counts counts = new Counts("test");
        Future<Counts> future = CompletableFuture.completedFuture(counts);

        assertSame(counts, ChunkedCsvLoader.await(future));
    }

    @Test
    void anIOExceptionCauseIsRethrownAsIs() {
        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            Future<Counts> future = executor.submit(() -> {
                throw new KeysetException("boom");
            });

            IOException ex = assertThrows(IOException.class, () -> ChunkedCsvLoader.await(future));
            assertInstanceOf(KeysetException.class, ex);
            assertEquals("boom", ex.getMessage());
        }
    }

    @Test
    void aNonIOExceptionCauseIsWrappedInANewIOException() {
        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            Future<Counts> future = executor.submit(() -> {
                throw new IllegalStateException("unexpected");
            });

            IOException ex = assertThrows(IOException.class, () -> ChunkedCsvLoader.await(future));
            assertInstanceOf(IllegalStateException.class, ex.getCause());
        }
    }

    /**
     * Same shape as {@code TwoFileLoaderTest}'s interruption test, but targeting
     * {@code await} directly. There's no observable signal for "the calling thread is now
     * parked inside {@code Future.get()}" the way there is for a blocking stream read, so
     * this waits a short, generous moment before interrupting - safe even if that moment
     * turns out to be too short, since {@code Thread.interrupt()} just sets a flag that
     * persists until consumed, so an interrupt arriving fractionally before the thread
     * reaches {@code get()} still gets picked up the instant it does.
     *
     * <p>Deliberately not try-with-resources for the executor: its task is designed to
     * never finish on its own (that's the point - {@code await} has to be the thing that
     * gives up on it via interruption, not the task completing). {@code ExecutorService.close()}'s
     * default implementation only escalates to {@code shutdownNow()} if the thread calling
     * {@code close()} itself gets interrupted while waiting - which the JUnit thread here
     * never is - so relying on it would hang this test indefinitely. Explicit
     * {@code shutdownNow()} in a {@code finally} sidesteps that entirely.
     */
    @Test
    void interruptionIsPropagatedAsIOExceptionWithTheInterruptFlagRestored() throws Exception {
        CountDownLatch taskStarted = new CountDownLatch(1);
        CountDownLatch neverReleased = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();

        try {
            Future<Counts> future = executor.submit(() -> {
                taskStarted.countDown();
                neverReleased.await();
                return new Counts("unused");
            });
            assertTrue(taskStarted.await(5, TimeUnit.SECONDS));

            AtomicReference<Throwable> thrown = new AtomicReference<>();
            AtomicBoolean interruptedAfter = new AtomicBoolean();
            Thread worker = new Thread(() -> {
                try {
                    ChunkedCsvLoader.await(future);
                } catch (Throwable t) {
                    thrown.set(t);
                } finally {
                    interruptedAfter.set(Thread.currentThread().isInterrupted());
                }
            });

            worker.start();
            Thread.sleep(100);
            worker.interrupt();
            worker.join(TimeUnit.SECONDS.toMillis(5));

            assertInstanceOf(IOException.class, thrown.get());
            assertTrue(interruptedAfter.get());
        } finally {
            executor.shutdownNow();
        }
    }
}
