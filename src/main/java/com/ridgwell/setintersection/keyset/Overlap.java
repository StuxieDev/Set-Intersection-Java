package com.ridgwell.setintersection.keyset;

/**
 * The result of comparing two {@link Counts}.
 *
 * @param distinct the number of distinct keys present in both files
 * @param total    the "maximum possible overlap": for each shared key, every occurrence
 *                  in file a could pair with every occurrence in file b, so the key
 *                  contributes {@code count(a) * count(b)}. A {@code long}, not an
 *                  {@code int} - two files each containing on the order of 50,000
 *                  occurrences of one key already produces a product that overflows a
 *                  32-bit int.
 */
public record Overlap(int distinct, long total) {
}
