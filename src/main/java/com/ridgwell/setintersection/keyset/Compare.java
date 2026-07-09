package com.ridgwell.setintersection.keyset;

/** Computes the distinct and total overlap between two {@link Counts}. */
public final class Compare {

    private Compare() {
    }

    /**
     * Walks whichever of {@code a}/{@code b} has fewer distinct keys and looks each one
     * up in the other, so the cost is {@code O(min(distinct(a), distinct(b)))} rather
     * than a full nested loop over both key sets.
     */
    public static Overlap compare(Counts a, Counts b) {
        Counts small = a;
        Counts big = b;
        if (b.distinct() < a.distinct()) {
            small = b;
            big = a;
        }

        Accumulator accumulator = new Accumulator();
        Counts bigRef = big;
        small.forEachKey((key, count) -> {
            int otherCount = bigRef.frequencyOf(key);
            if (otherCount > 0) {
                accumulator.distinct++;
                accumulator.total += (long) count * (long) otherCount;
            }
        });
        return new Overlap(accumulator.distinct, accumulator.total);
    }

    private static final class Accumulator {
        int distinct;
        long total;
    }
}
