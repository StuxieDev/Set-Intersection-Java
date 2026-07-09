package com.ridgwell.setintersection.collections;

/** Callback for {@link StringIntOpenHashMap#forEach}, avoiding a boxed {@code Map.Entry<String, Integer>} per visited slot. */
@FunctionalInterface
public interface EntryConsumer {
    void accept(String key, int value);
}
