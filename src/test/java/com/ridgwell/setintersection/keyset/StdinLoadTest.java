package com.ridgwell.setintersection.keyset;

import com.ridgwell.setintersection.io.StdinSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StdinLoadTest {

    private InputStream originalStdin;

    @BeforeEach
    void captureStdin() {
        originalStdin = System.in;
    }

    @AfterEach
    void restoreStdin() {
        System.setIn(originalStdin);
    }

    @Test
    void stdinSourceIsReadableThroughKeysetLoader() throws IOException {
        System.setIn(new ByteArrayInputStream("a\nb\nb\nc\n".getBytes(StandardCharsets.UTF_8)));

        Counts counts = KeysetLoader.load(new StdinSource(), Options.singleColumn(false, "0", ','));

        assertEquals(4, counts.total());
        assertEquals(3, counts.distinct());
        assertEquals(2, counts.frequencyOf("b"));
    }

    @Test
    void stdinCanOnlyBeOpenedOncePerSource() {
        System.setIn(new ByteArrayInputStream("a\n".getBytes(StandardCharsets.UTF_8)));

        StdinSource source = new StdinSource();
        assertDoesNotThrow(source::openStream);
        assertThrows(IllegalStateException.class, source::openStream);
    }
}
