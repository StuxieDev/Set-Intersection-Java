/**
 * Turns a CSV source into key frequency counts ({@link com.ridgwell.setintersection.keyset.KeysetLoader}),
 * compares two of them ({@link com.ridgwell.setintersection.keyset.Compare}), and handles
 * the concurrency: two files at once ({@link com.ridgwell.setintersection.keyset.TwoFileLoader})
 * and, for a single large plain file, byte-range chunks parsed in parallel
 * ({@link com.ridgwell.setintersection.keyset.ChunkPlanner} and
 * {@link com.ridgwell.setintersection.keyset.ChunkedCsvLoader}).
 *
 * <p>Depends on {@code io} and {@code csv}; nothing here knows the {@code cli} package
 * exists, so a different front end could reuse this package without changes.
 */
package com.ridgwell.setintersection.keyset;
