package com.ridgwell.setintersection.collections;

import java.util.Objects;

/**
 * A hand-rolled {@code String -> int} map used to count key frequencies, in place of
 * {@code java.util.HashMap<String, Integer>}.
 *
 * <p>Implementation: open addressing with linear probing over three parallel arrays
 * (keys, values, per-slot state) rather than a bucket/node structure, so lookups and
 * increments never allocate and never box the count into an {@code Integer}. Slot state
 * is one of {@link #EMPTY}, {@link #OCCUPIED}, or {@link #TOMBSTONE} (a deleted slot that
 * must stay "not empty" for probing correctness but doesn't hold a live entry).
 *
 * <p>Capacity is always a power of two so probing can use a bitmask instead of a modulo.
 * {@link String#hashCode()} is run through a Murmur3-style finalizer before masking,
 * because {@code String.hashCode()} alone spreads its low bits poorly for structured
 * inputs (e.g. runs of digits, as in the UDPRN-style keys this tool was written for) -
 * masked directly, that weakness would cluster entries instead of spreading them evenly.
 *
 * <p>Not thread-safe: each concurrent CSV chunk in this tool builds its own instance,
 * which are merged afterwards, so no instance is ever written from more than one thread.
 */
public final class StringIntOpenHashMap {

    private static final int DEFAULT_INITIAL_CAPACITY = 16;
    private static final double MAX_LOAD_FACTOR = 0.7;

    private static final byte EMPTY = 0;
    private static final byte OCCUPIED = 1;
    private static final byte TOMBSTONE = 2;

    private String[] keys;
    private int[] values;
    private byte[] states;
    private int capacity;
    private int size;
    private int tombstones;

    public StringIntOpenHashMap() {
        this(DEFAULT_INITIAL_CAPACITY);
    }

    public StringIntOpenHashMap(int initialCapacity) {
        this.capacity = nextPowerOfTwo(Math.max(initialCapacity, 4));
        this.keys = new String[capacity];
        this.values = new int[capacity];
        this.states = new byte[capacity];
    }

    /** Number of live (non-deleted) entries. */
    public int size() {
        return size;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    /** Current table size in slots. Exposed for tests that assert on resize behaviour. */
    public int capacity() {
        return capacity;
    }

    public boolean containsKey(String key) {
        return locate(key) >= 0;
    }

    public int getOrDefault(String key, int defaultValue) {
        int index = locate(key);
        return index >= 0 ? values[index] : defaultValue;
    }

    /** Equivalent to {@code merge(key, 1)} - the hot path used while counting CSV rows. */
    public void increment(String key) {
        merge(key, 1);
    }

    /** Adds {@code delta} to the count for {@code key}, inserting it at {@code delta} if absent. */
    public void merge(String key, int delta) {
        Objects.requireNonNull(key, "key");
        growIfNeeded();

        int mask = capacity - 1;
        int index = spread(key.hashCode()) & mask;
        int firstTombstone = -1;

        while (true) {
            byte state = states[index];
            if (state == EMPTY) {
                int slot = firstTombstone != -1 ? firstTombstone : index;
                if (states[slot] == TOMBSTONE) {
                    tombstones--;
                }
                keys[slot] = key;
                values[slot] = delta;
                states[slot] = OCCUPIED;
                size++;
                return;
            }
            if (state == OCCUPIED && keys[index].equals(key)) {
                values[index] += delta;
                return;
            }
            if (state == TOMBSTONE && firstTombstone == -1) {
                firstTombstone = index;
            }
            index = (index + 1) & mask;
        }
    }

    /** Removes {@code key} if present. Not used on the CSV-loading hot path; exercised by tests. */
    public boolean remove(String key) {
        int index = locate(key);
        if (index < 0) {
            return false;
        }
        states[index] = TOMBSTONE;
        keys[index] = null;
        values[index] = 0;
        size--;
        tombstones++;
        return true;
    }

    /** Visits every live entry with no per-entry allocation and no boxed {@code Integer}. */
    public void forEach(EntryConsumer consumer) {
        for (int i = 0; i < capacity; i++) {
            if (states[i] == OCCUPIED) {
                consumer.accept(keys[i], values[i]);
            }
        }
    }

    /** Returns the slot index of {@code key} if present (occupied and equal), else -1. */
    private int locate(String key) {
        int mask = capacity - 1;
        int index = spread(key.hashCode()) & mask;
        while (true) {
            byte state = states[index];
            if (state == EMPTY) {
                return -1;
            }
            if (state == OCCUPIED && keys[index].equals(key)) {
                return index;
            }
            index = (index + 1) & mask;
        }
    }

    private void growIfNeeded() {
        if (size + tombstones + 1 > capacity * MAX_LOAD_FACTOR) {
            resize(capacity * 2);
        }
    }

    /**
     * Rebuilds the table at {@code newCapacity}, re-inserting only live entries so
     * tombstones accumulated since the last resize are dropped for free. Rehashing cost
     * is O(size), not O(capacity), regardless of how many tombstones existed before.
     */
    private void resize(int newCapacity) {
        String[] oldKeys = keys;
        int[] oldValues = values;
        byte[] oldStates = states;
        int oldCapacity = capacity;

        capacity = newCapacity;
        keys = new String[capacity];
        values = new int[capacity];
        states = new byte[capacity];
        size = 0;
        tombstones = 0;

        for (int i = 0; i < oldCapacity; i++) {
            if (oldStates[i] == OCCUPIED) {
                insertFreshDuringResize(oldKeys[i], oldValues[i]);
            }
        }
    }

    /** Inserts a key known to be unique into a freshly-sized, tombstone-free table. */
    private void insertFreshDuringResize(String key, int value) {
        int mask = capacity - 1;
        int index = spread(key.hashCode()) & mask;
        while (states[index] != EMPTY) {
            index = (index + 1) & mask;
        }
        keys[index] = key;
        values[index] = value;
        states[index] = OCCUPIED;
        size++;
    }

    private static int nextPowerOfTwo(int n) {
        int p = 1;
        while (p < n) {
            p <<= 1;
        }
        return p;
    }

    /** Murmur3 fmix32 finalizer, used to spread {@link String#hashCode()} before masking. */
    private static int spread(int h) {
        h ^= (h >>> 16);
        h *= 0x85EBCA6B;
        h ^= (h >>> 13);
        h *= 0xC2B2AE35;
        h ^= (h >>> 16);
        return h;
    }
}
