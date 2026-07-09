package com.ridgwell.setintersection.keyset;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class CompositeKeyEncoderTest {

    @Test
    void singleColumnPassesTheValueThroughUnchanged() {
        assertEquals("AB1C", CompositeKeyEncoder.encode(new String[] {"AB1C"}));
    }

    @Test
    void differentSplitsOfTheSameConcatenationDoNotCollide() {
        String key1 = CompositeKeyEncoder.encode(new String[] {"AB", "C"});
        String key2 = CompositeKeyEncoder.encode(new String[] {"A", "BC"});
        assertNotEquals(key1, key2);
    }

    @Test
    void emptyStringPartsAreHandled() {
        assertEquals("0:1:A", CompositeKeyEncoder.encode(new String[] {"", "A"}));
    }

    @Test
    void partsContainingDigitsAndColonsStillEncodeInjectively() {
        String key1 = CompositeKeyEncoder.encode(new String[] {"1:2", "3"});
        String key2 = CompositeKeyEncoder.encode(new String[] {"1", "2:3"});
        assertNotEquals(key1, key2);
    }

    @Test
    void encodingIsDeterministicForTheSameInput() {
        String key1 = CompositeKeyEncoder.encode(new String[] {"udprn123", "postcode-AB1"});
        String key2 = CompositeKeyEncoder.encode(new String[] {"udprn123", "postcode-AB1"});
        assertEquals(key1, key2);
    }
}
