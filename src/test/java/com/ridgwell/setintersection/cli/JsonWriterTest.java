package com.ridgwell.setintersection.cli;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JsonWriterTest {

    @Test
    void backslashesAreEscapedSoWindowsPathsStayValidJson() {
        assertEquals("C:\\\\data\\\\a.csv", JsonWriter.escape("C:\\data\\a.csv"));
    }

    @Test
    void quotesAreEscaped() {
        assertEquals("say \\\"hi\\\"", JsonWriter.escape("say \"hi\""));
    }

    @Test
    void plainAsciiPathsAreUnchanged() {
        assertEquals("examples/A_f.csv", JsonWriter.escape("examples/A_f.csv"));
    }

    @Test
    void controlCharactersAreEscaped() {
        assertEquals("a\\nb\\tc", JsonWriter.escape("a\nb\tc"));
    }

    @Test
    void carriageReturnIsEscaped() {
        assertEquals("a\\rb", JsonWriter.escape("a\rb"));
    }

    /** `\n`, `\r`, `\t` have their own named escapes above; any other control character below 0x20 falls through to the generic `\\u00xx` form. */
    @Test
    void otherControlCharactersUseTheGenericUnicodeEscape() {
        String controlCharacter = String.valueOf((char) 1);
        assertEquals("a\\u0001b", JsonWriter.escape("a" + controlCharacter + "b"));
    }
}
