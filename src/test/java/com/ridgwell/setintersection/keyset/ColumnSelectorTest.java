package com.ridgwell.setintersection.keyset;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ColumnSelectorTest {

    @Test
    void numericSpecResolvesDirectlyWithoutAHeader() throws KeysetException {
        int[] indices = ColumnSelector.resolve(List.of("2"), false, null);
        assertArrayEquals(new int[] {2}, indices);
    }

    @Test
    void headerNameResolvesToItsIndex() throws KeysetException {
        int[] indices = ColumnSelector.resolve(List.of("udprn"), true, new String[] {"id", "udprn", "name"});
        assertArrayEquals(new int[] {1}, indices);
    }

    @Test
    void multipleCompositeSpecsResolveIndependently() throws KeysetException {
        int[] indices = ColumnSelector.resolve(List.of("udprn", "0"), true, new String[] {"id", "udprn", "name"});
        assertArrayEquals(new int[] {1, 0}, indices);
    }

    @Test
    void unknownHeaderNameIsAnError() {
        assertThrows(KeysetException.class,
                () -> ColumnSelector.resolve(List.of("nope"), true, new String[] {"id", "udprn"}));
    }

    @Test
    void nonNumericColumnWithoutAHeaderIsAnError() {
        assertThrows(KeysetException.class,
                () -> ColumnSelector.resolve(List.of("udprn"), false, null));
    }
}
