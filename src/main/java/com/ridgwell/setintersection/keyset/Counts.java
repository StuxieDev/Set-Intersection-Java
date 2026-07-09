package com.ridgwell.setintersection.keyset;

import com.ridgwell.setintersection.collections.EntryConsumer;
import com.ridgwell.setintersection.collections.StringIntOpenHashMap;

/** Per-file key statistics: how many keys were read in total, and how often each distinct key occurred. */
public final class Counts {

    private final String path;
    private final StringIntOpenHashMap freq = new StringIntOpenHashMap();
    private long total;

    Counts(String path) {
        this.path = path;
    }

    public String path() {
        return path;
    }

    /** Number of keys read, including duplicates. A {@code long}: with enough rows this can exceed {@code Integer.MAX_VALUE}. */
    public long total() {
        return total;
    }

    public int distinct() {
        return freq.size();
    }

    /** The number of times {@code key} occurred, or 0 if it never did. */
    public int frequencyOf(String key) {
        return freq.getOrDefault(key, 0);
    }

    /** Visits every distinct key and its frequency, with no allocation per entry. */
    public void forEachKey(EntryConsumer consumer) {
        freq.forEach(consumer);
    }

    /** Records one more occurrence of {@code key}. Called once per data row while loading. */
    void recordKey(String key) {
        freq.increment(key);
        total++;
    }

    /** Folds another partial {@code Counts} into this one - used only to merge parallel chunk results back together. */
    void mergeFrom(Counts other) {
        this.total += other.total;
        other.freq.forEach((key, count) -> this.freq.merge(key, count));
    }
}
