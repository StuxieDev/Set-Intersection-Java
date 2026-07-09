package com.ridgwell.setintersection.keyset;

import com.ridgwell.setintersection.io.InputSource;

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Loads two files at once, each on its own virtual thread, so wall-clock time is roughly
 * {@code max(load1, load2)} rather than the sum. Each file makes its own call on whether
 * to chunk (see {@link KeysetLoader}) with no coordination needed here - a chunked load
 * just spins up more virtual threads underneath, which is cheap enough not to matter.
 */
public final class TwoFileLoader {

    private TwoFileLoader() {
    }

    /** Both loads always run to completion; if both fail, file1's error is surfaced first. */
    public static Counts[] loadBoth(InputSource source1, Options options1, InputSource source2, Options options2) throws IOException {
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<Counts> future1 = executor.submit(() -> KeysetLoader.load(source1, options1));
            Future<Counts> future2 = executor.submit(() -> KeysetLoader.load(source2, options2));

            Counts counts1 = null;
            Counts counts2 = null;
            IOException error1 = null;
            IOException error2 = null;

            try {
                counts1 = future1.get();
            } catch (ExecutionException e) {
                error1 = unwrap(e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("interrupted while loading " + source1.displayPath(), e);
            }

            try {
                counts2 = future2.get();
            } catch (ExecutionException e) {
                error2 = unwrap(e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("interrupted while loading " + source2.displayPath(), e);
            }

            if (error1 != null) {
                throw error1;
            }
            if (error2 != null) {
                throw error2;
            }
            return new Counts[] {counts1, counts2};
        }
    }

    /** A task's real failure ends up wrapped in ExecutionException - this pulls the original IOException back out. */
    private static IOException unwrap(ExecutionException e) {
        Throwable cause = e.getCause();
        return cause instanceof IOException io ? io : new IOException(cause);
    }
}
