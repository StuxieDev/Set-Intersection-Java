package com.ridgwell.setintersection.collections;

import java.util.Objects;

/**
 * A {@code String -> int} map for counting key frequencies, written by hand instead of
 * using {@code java.util.HashMap<String, Integer>}.
 *
 * <p>It's open addressing with linear probing over three parallel arrays (keys, values,
 * and a per-slot state byte) instead of a bucket/node structure, so counting never
 * allocates and never boxes anything into an {@code Integer}. A slot is EMPTY, OCCUPIED,
 * or TOMBSTONE - tombstones are deleted entries that still need to block probes from
 * stopping early, even though nothing lives there anymore.
 *
 * <p>Capacity is always a power of two so probing can mask instead of doing a modulo.
 * {@code String.hashCode()} gets run through a Murmur3-style finalizer first, because on
 * its own it spreads badly for keys that are mostly digits - which is exactly what the
 * UDPRN keys this was built for look like. Without that step they'd cluster instead of
 * spreading evenly across the table.
 *
 * <p>Not thread-safe, but doesn't need to be: each parallel CSV chunk builds its own
 * instance and they get merged afterwards, so nothing here is ever written from two
 * threads at once.
 */
public final class StringIntOpenHashMap {

    private static final int DEFAULT_INITIAL_CAPACITY = 16;
    // Once (live entries + tombstones) crosses this fraction of capacity, grow.
    private static final double MAX_LOAD_FACTOR = 0.7;

    private static final byte EMPTY = 0;
    private static final byte OCCUPIED = 1;
    private static final byte TOMBSTONE = 2;

    // keys[i]/values[i]/states[i] together are slot i.
    private String[] keys;
    private int[] values;
    private byte[] states;
    private int capacity;
    private int size;
    private int tombstones;

    /** Starts at the default capacity. */
    public StringIntOpenHashMap() {
        this(DEFAULT_INITIAL_CAPACITY);
    }

    /** Rounds {@code initialCapacity} up to a power of two so probing can use a bitmask. */
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

    /** Current table size in slots - mostly useful for tests asserting on resize behaviour. */
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

    /** Same as {@code merge(key, 1)} - the hot path for counting CSV rows. */
    public void increment(String key) {
        merge(key, 1);
    }

    /** Adds {@code delta} to {@code key}'s count, inserting it fresh if it isn't there yet. */
    public void merge(String key, int delta) {
        Objects.requireNonNull(key, "key");
        // Grow before probing so there's always room for an insert without resizing mid-scan.
        growIfNeeded();

        int mask = capacity - 1;
        int index = spread(key.hashCode()) & mask;
        // If we end up inserting rather than updating, prefer the first tombstone we
        // passed over the eventual empty slot - keeps the probe chain for this bucket short.
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

    /** Removes {@code key} if it's there. Not on the loading hot path, just here for completeness (and tests). */
    public boolean remove(String key) {
        int index = locate(key);
        if (index < 0) {
            return false;
        }
        // Has to become a TOMBSTONE, not EMPTY - an EMPTY slot would cut off probes for
        // other keys that hashed the same way and got pushed past this one.
        states[index] = TOMBSTONE;
        keys[index] = null;
        values[index] = 0;
        size--;
        tombstones++;
        return true;
    }

    /** Visits every live entry - no boxed {@code Integer}, no {@code Map.Entry} allocated per entry. */
    public void forEach(EntryConsumer consumer) {
        for (int i = 0; i < capacity; i++) {
            if (states[i] == OCCUPIED) {
                consumer.accept(keys[i], values[i]);
            }
        }
    }

    /** The linear probe itself - shared by every read (getOrDefault, containsKey, remove). */
    private int locate(String key) {
        int mask = capacity - 1;
        int index = spread(key.hashCode()) & mask;
        while (true) {
            byte state = states[index];
            if (state == EMPTY) {
                // If key were in the table, it (or a tombstone in its place) would have
                // shown up before we ever reached an empty slot.
                return -1;
            }
            if (state == OCCUPIED && keys[index].equals(key)) {
                return index;
            }
            index = (index + 1) & mask;
        }
    }

    /** Called before every insert - grows if adding one more entry would push past the load factor. */
    private void growIfNeeded() {
        if (size + tombstones + 1 > capacity * MAX_LOAD_FACTOR) {
            resize(capacity * 2);
        }
    }

    /**
     * Rebuilds at {@code newCapacity}, carrying over only the live entries - tombstones
     * just get dropped, which is how their cost gets reclaimed. Cost is O(size), not
     * O(capacity), no matter how many tombstones had piled up beforehand.
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

    /** Inserts a key already known to be unique into a fresh, tombstone-free table. */
    private void insertFreshDuringResize(String key, int value) {
        int mask = capacity - 1;
        int index = spread(key.hashCode()) & mask;
        // No need to check for a match - every key coming from the old table is already
        // unique, so the first empty slot we hit is the right one.
        while (states[index] != EMPTY) {
            index = (index + 1) & mask;
        }
        keys[index] = key;
        values[index] = value;
        states[index] = OCCUPIED;
        size++;
    }

    /** Smallest power of two that's >= n. */
    private static int nextPowerOfTwo(int n) {
        int p = 1;
        while (p < n) {
            p <<= 1;
        }
        return p;
    }

    /** Murmur3's fmix32 finalizer - spreads the hash before it gets masked down to a table index. */
    private static int spread(int h) {
        h ^= (h >>> 16);
        h *= 0x85EBCA6B;
        h ^= (h >>> 13);
        h *= 0xC2B2AE35;
        h ^= (h >>> 16);
        return h;
    }
}
