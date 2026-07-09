package com.ridgwell.setintersection.cli;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DelimiterParserTest {

    @Test
    void emptyStringDefaultsToComma() throws CliUsageException {
        assertEquals(',', DelimiterParser.parse(""));
    }

    @Test
    void literalCommaIsComma() throws CliUsageException {
        assertEquals(',', DelimiterParser.parse(","));
    }

    @Test
    void backslashTEscapeMeansTab() throws CliUsageException {
        assertEquals('\t', DelimiterParser.parse("\\t"));
    }

    @Test
    void anActualTabCharacterAlsoMeansTab() throws CliUsageException {
        assertEquals('\t', DelimiterParser.parse("\t"));
    }

    @Test
    void aSingleOrdinaryCharacterIsUsedAsIs() throws CliUsageException {
        assertEquals(';', DelimiterParser.parse(";"));
        assertEquals('|', DelimiterParser.parse("|"));
    }

    @Test
    void aMultiCharacterValueIsAnError() {
        CliUsageException ex = assertThrows(CliUsageException.class, () -> DelimiterParser.parse("::"));
        assertEquals("-delimiter must be a single character, got '::'", ex.getMessage());
    }

    @Test
    void aNonAsciiCharacterIsAnError() {
        assertThrows(CliUsageException.class, () -> DelimiterParser.parse("é"));
    }
}
