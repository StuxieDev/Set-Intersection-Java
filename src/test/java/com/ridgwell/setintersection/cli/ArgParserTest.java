package com.ridgwell.setintersection.cli;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArgParserTest {

    private static ArgParser parser() {
        return new ArgParser()
                .stringFlag("file1", "", "")
                .stringFlag("column", "0", "")
                .boolFlag("header", false, "")
                .boolFlag("json", false, "");
    }

    @Test
    void parsesAFlagValueGivenAsSeparateTokens() throws CliUsageException {
        ArgParser p = parser();
        p.parse(new String[] {"-file1", "a.csv"});
        assertEquals("a.csv", p.getString("file1"));
    }

    @Test
    void parsesEqualsSyntaxForBothSingleAndDoubleDash() throws CliUsageException {
        ArgParser p = parser();
        p.parse(new String[] {"-file1=a.csv", "--column=udprn"});
        assertEquals("a.csv", p.getString("file1"));
        assertEquals("udprn", p.getString("column"));
    }

    @Test
    void bareBooleanFlagDoesNotConsumeTheNextToken() throws CliUsageException {
        ArgParser p = parser();
        p.parse(new String[] {"-header", "-file1", "a.csv"});
        assertTrue(p.getBoolean("header"));
        assertEquals("a.csv", p.getString("file1"));
    }

    @Test
    void longFormDoubleDashIsAccepted() throws CliUsageException {
        ArgParser p = parser();
        p.parse(new String[] {"--header"});
        assertTrue(p.getBoolean("header"));
    }

    @Test
    void unknownFlagIsAnError() {
        ArgParser p = parser();
        assertThrows(CliUsageException.class, () -> p.parse(new String[] {"-bogus"}));
    }

    @Test
    void missingValueForAStringFlagIsAnError() {
        ArgParser p = parser();
        assertThrows(CliUsageException.class, () -> p.parse(new String[] {"-file1"}));
    }

    @Test
    void repeatedFlagLastOccurrenceWins() throws CliUsageException {
        ArgParser p = parser();
        p.parse(new String[] {"-file1", "first.csv", "-file1", "second.csv"});
        assertEquals("second.csv", p.getString("file1"));
    }

    @Test
    void backslashTEscapeIsAcceptedAsALiteralStringValue() throws CliUsageException {
        ArgParser p = new ArgParser().stringFlag("delimiter", ",", "");
        p.parse(new String[] {"-delimiter", "\\t"});
        assertEquals("\\t", p.getString("delimiter"));
    }

    @Test
    void bareDashWithNoFlagNameIsAnError() {
        ArgParser p = parser();
        assertThrows(CliUsageException.class, () -> p.parse(new String[] {"-"}));
    }
}
