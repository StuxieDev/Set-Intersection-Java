package com.ridgwell.setintersection.collections;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StringIntOpenHashMapTest {

    @Test
    void incrementAccumulatesCounts() {
        StringIntOpenHashMap map = new StringIntOpenHashMap();
        map.increment("a");
        map.increment("a");
        map.increment("b");

        assertEquals(2, map.getOrDefault("a", 0));
        assertEquals(1, map.getOrDefault("b", 0));
        assertEquals(0, map.getOrDefault("c", 0));
        assertEquals(2, map.size());
    }

    @Test
    void isEmptyReflectsWhetherAnyLiveEntriesExist() {
        StringIntOpenHashMap map = new StringIntOpenHashMap();
        assertTrue(map.isEmpty());

        map.increment("a");
        assertFalse(map.isEmpty());

        map.remove("a");
        assertTrue(map.isEmpty());
    }

    @Test
    void mergeAddsDelta() {
        StringIntOpenHashMap map = new StringIntOpenHashMap();
        map.merge("a", 5);
        map.merge("a", 3);
        assertEquals(8, map.getOrDefault("a", 0));
    }

    @Test
    void growsAndStaysCorrectAcrossManyResizes() {
        StringIntOpenHashMap map = new StringIntOpenHashMap();
        Map<String, Integer> reference = new HashMap<>();
        Random random = new Random(42);

        for (int i = 0; i < 20_000; i++) {
            String key = "key-" + random.nextInt(2_000);
            map.increment(key);
            reference.merge(key, 1, Integer::sum);
        }

        assertEquals(reference.size(), map.size());
        for (Map.Entry<String, Integer> entry : reference.entrySet()) {
            assertEquals(entry.getValue(), map.getOrDefault(entry.getKey(), -1));
        }
        assertTrue(map.capacity() > 16, "table should have resized past its initial capacity");
    }

    @Test
    void degenerateAllSameKeyStaysAtOneEntry() {
        StringIntOpenHashMap map = new StringIntOpenHashMap();
        for (int i = 0; i < 10_000; i++) {
            map.increment("only-key");
        }
        assertEquals(1, map.size());
        assertEquals(10_000, map.getOrDefault("only-key", 0));
    }

    @Test
    void emptyStringKeyIsDistinctFromAbsentKey() {
        StringIntOpenHashMap map = new StringIntOpenHashMap();
        assertFalse(map.containsKey(""));

        map.increment("");
        assertTrue(map.containsKey(""));
        assertEquals(1, map.getOrDefault("", -1));
    }

    @Test
    void removeReclaimsSlotForReuseViaTombstone() {
        StringIntOpenHashMap map = new StringIntOpenHashMap(4);
        map.increment("a");
        map.increment("b");

        assertTrue(map.remove("a"));
        assertFalse(map.containsKey("a"));
        assertEquals(1, map.size());

        map.increment("a");
        assertEquals(1, map.getOrDefault("a", 0));
        assertEquals(2, map.size());
    }

    @Test
    void removingAMissingKeyReturnsFalse() {
        StringIntOpenHashMap map = new StringIntOpenHashMap();
        map.increment("a");
        assertFalse(map.remove("nope"));
    }

    @Test
    void forEachVisitsEveryLiveEntryExactlyOnce() {
        StringIntOpenHashMap map = new StringIntOpenHashMap();
        map.increment("a");
        map.increment("b");
        map.increment("b");

        Map<String, Integer> seen = new HashMap<>();
        map.forEach((key, value) -> seen.put(key, value));

        assertEquals(Map.of("a", 1, "b", 2), seen);
    }

    @Test
    void collisionsResolveCorrectlyEvenWithASmallInitialCapacity() {
        StringIntOpenHashMap map = new StringIntOpenHashMap(4);
        for (int i = 0; i < 50; i++) {
            map.increment("k" + i);
        }
        for (int i = 0; i < 50; i++) {
            assertEquals(1, map.getOrDefault("k" + i, 0), "key k" + i);
        }
        assertEquals(50, map.size());
    }
}
