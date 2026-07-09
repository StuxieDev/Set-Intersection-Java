package com.ridgwell.setintersection.csv;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CsvReaderTest {

    private static CsvReader readerFor(String content, char delimiter) {
        return new CsvReader(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)), delimiter, "test");
    }

    private static List<List<String>> readAll(CsvReader reader) throws IOException {
        List<List<String>> records = new ArrayList<>();
        while (reader.nextRecord()) {
            List<String> fields = new ArrayList<>();
            for (int i = 0; i < reader.fieldCount(); i++) {
                fields.add(reader.field(i));
            }
            records.add(fields);
        }
        return records;
    }

    @Test
    void plainCommaSeparatedRows() throws IOException {
        CsvReader reader = readerFor("a,b,c\n1,2,3\n", ',');
        assertEquals(List.of(List.of("a", "b", "c"), List.of("1", "2", "3")), readAll(reader));
    }

    @Test
    void noTrailingNewlineStillReadsTheLastRow() throws IOException {
        CsvReader reader = readerFor("a,b", ',');
        assertEquals(List.of(List.of("a", "b")), readAll(reader));
    }

    @Test
    void emptyFileHasNoRecords() throws IOException {
        CsvReader reader = readerFor("", ',');
        assertEquals(List.of(), readAll(reader));
    }

    @Test
    void quotedFieldWithEmbeddedDelimiterAndNewline() throws IOException {
        CsvReader reader = readerFor("\"a,b\nc\",d\n", ',');
        assertEquals(List.of(List.of("a,b\nc", "d")), readAll(reader));
    }

    @Test
    void doubledQuoteIsALiteralQuote() throws IOException {
        CsvReader reader = readerFor("\"say \"\"hi\"\"\"\n", ',');
        assertEquals(List.of(List.of("say \"hi\"")), readAll(reader));
    }

    @Test
    void bareQuoteInAnUnquotedFieldIsAnError() {
        CsvReader reader = readerFor("a\"b,c\n", ',');
        assertThrows(CsvParseException.class, () -> readAll(reader));
    }

    @Test
    void unterminatedQuotedFieldIsAnError() {
        CsvReader reader = readerFor("\"unterminated", ',');
        assertThrows(CsvParseException.class, () -> readAll(reader));
    }

    @Test
    void extraneousCharactersAfterAClosingQuoteAreAnError() {
        CsvReader reader = readerFor("\"a\"b,c\n", ',');
        assertThrows(CsvParseException.class, () -> readAll(reader));
    }

    @Test
    void exceptionCarriesTheOneBasedRecordNumberOfTheBadRow() throws IOException {
        CsvReader reader = readerFor("ok,fine\nbad\"field,x\n", ',');
        assertTrue(reader.nextRecord());

        CsvParseException ex = assertThrows(CsvParseException.class, reader::nextRecord);
        assertEquals(2, ex.recordNumber());
        assertEquals("test", ex.sourceLabel());
    }

    @Test
    void variableFieldCountsPerRowAreAllowed() throws IOException {
        CsvReader reader = readerFor("a\nb,c\nd,e,f\n", ',');
        List<List<String>> records = readAll(reader);
        assertEquals(List.of(1, 2, 3), records.stream().map(List::size).toList());
    }

    @Test
    void customDelimiterSemicolon() throws IOException {
        CsvReader reader = readerFor("a;b;c\n", ';');
        assertEquals(List.of(List.of("a", "b", "c")), readAll(reader));
    }

    @Test
    void tabDelimiter() throws IOException {
        CsvReader reader = readerFor("a\tb\tc\n", '\t');
        assertEquals(List.of(List.of("a", "b", "c")), readAll(reader));
    }

    @Test
    void crlfLineEndings() throws IOException {
        CsvReader reader = readerFor("a,b\r\nc,d\r\n", ',');
        assertEquals(List.of(List.of("a", "b"), List.of("c", "d")), readAll(reader));
    }

    @Test
    void blankLinesAreSkippedRatherThanReturnedAsEmptyRecords() throws IOException {
        CsvReader reader = readerFor("a,b\n\nc,d\n", ',');
        assertEquals(List.of(List.of("a", "b"), List.of("c", "d")), readAll(reader));
    }

    @Test
    void utf8MultiByteFieldsRoundTrip() throws IOException {
        CsvReader reader = readerFor("café,日本語\n", ',');
        assertEquals(List.of(List.of("café", "日本語")), readAll(reader));
    }

    @Test
    void leadingZerosArePreservedAsAString() throws IOException {
        CsvReader reader = readerFor("00012345\n", ',');
        assertEquals("00012345", readAll(reader).get(0).get(0));
    }

    @Test
    void nonAsciiDelimiterIsRejectedAtConstruction() {
        assertThrows(IllegalArgumentException.class,
                () -> new CsvReader(new ByteArrayInputStream(new byte[0]), 'é', "test"));
    }

    @Test
    void fieldWithAnOutOfRangeIndexThrows() throws IOException {
        CsvReader reader = readerFor("a,b\n", ',');
        assertTrue(reader.nextRecord());

        assertThrows(IndexOutOfBoundsException.class, () -> reader.field(2));
        assertThrows(IndexOutOfBoundsException.class, () -> reader.field(-1));
    }

    @Test
    void quotedFieldImmediatelyFollowedByEofWithNoTrailingNewline() throws IOException {
        CsvReader reader = readerFor("\"abc\"", ',');
        assertEquals(List.of(List.of("abc")), readAll(reader));
    }

    @Test
    void quotedFieldFollowedByCrlf() throws IOException {
        CsvReader reader = readerFor("\"abc\"\r\nd,e\r\n", ',');
        assertEquals(List.of(List.of("abc"), List.of("d", "e")), readAll(reader));
    }

    @Test
    void blankLineUsingCrlfIsAlsoSkipped() throws IOException {
        CsvReader reader = readerFor("a,b\r\n\r\nc,d\r\n", ',');
        assertEquals(List.of(List.of("a", "b"), List.of("c", "d")), readAll(reader));
    }

    @Test
    void recordWithMoreThanEightFieldsGrowsTheFieldArray() throws IOException {
        String row = String.join(",", "f0", "f1", "f2", "f3", "f4", "f5", "f6", "f7", "f8", "f9", "f10") + "\n";
        CsvReader reader = readerFor(row, ',');

        List<List<String>> records = readAll(reader);

        assertEquals(1, records.size());
        assertEquals(11, records.get(0).size());
        assertEquals(List.of("f0", "f1", "f2", "f3", "f4", "f5", "f6", "f7", "f8", "f9", "f10"), records.get(0));
    }
}
