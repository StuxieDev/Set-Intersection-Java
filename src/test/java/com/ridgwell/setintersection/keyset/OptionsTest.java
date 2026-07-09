package com.ridgwell.setintersection.keyset;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OptionsTest {

    @Test
    void emptyColumnsListIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new Options(false, List.of(), ','));
    }

    @Test
    void nullColumnsListIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new Options(false, null, ','));
    }

    @Test
    void singleColumnWrapsTheGivenColumnInAOneEntryList() {
        Options opts = Options.singleColumn(true, "udprn", ';');
        assertEquals(List.of("udprn"), opts.columns());
        assertEquals(';', opts.delimiter());
    }

    @Test
    void columnsListIsDefensivelyCopied() {
        List<String> mutable = new ArrayList<>(List.of("a", "b"));
        Options opts = new Options(true, mutable, ',');

        mutable.add("c");

        assertEquals(List.of("a", "b"), opts.columns(), "Options must not be affected by later changes to the caller's list");
    }
}
