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
}
