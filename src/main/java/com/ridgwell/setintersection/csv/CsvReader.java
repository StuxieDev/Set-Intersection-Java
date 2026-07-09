package com.ridgwell.setintersection.csv;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * A hand-rolled, streaming, RFC4180-style CSV reader. The standard library has no CSV
 * parser, so this replaces Go's {@code encoding/csv}.
 *
 * <p>Byte-oriented rather than {@code Reader}-based: fields are accumulated as raw bytes
 * and decoded to UTF-8 exactly once, when the field is complete. This is safe because the
 * only bytes this reader ever inspects as control characters - the delimiter, {@code "},
 * {@code \r}, {@code \n} - are all single-byte ASCII values below 0x80, and UTF-8
 * continuation/lead bytes for any multi-byte character are always 0x80 or above, so a
 * multi-byte character can never be mistaken for a control byte mid-scan. This also lets
 * the same reader work unmodified over a bounded byte range of a larger file (see
 * {@code io.ChunkedCsvLoader}), since it never needs to look beyond what its
 * {@code InputStream} makes visible.
 *
 * <p>Quoting follows Go's non-lazy (strict) default: a field is quoted only if it starts
 * with {@code "}; inside a quoted field a doubled {@code ""} is a literal quote; a quote
 * appearing anywhere in an unquoted field, or any character between a quoted field's
 * closing quote and the next delimiter/newline, is a parse error. Rows may have a
 * different number of fields from each other (there is no fixed-arity check), matching
 * Go's {@code FieldsPerRecord = -1}. Delimiters are restricted to a single ASCII byte,
 * which the byte-level scan above depends on.
 */
public final class CsvReader implements Closeable {

    private enum Terminator { DELIMITER, NEWLINE, EOF }

    private static final int NO_BYTE_BUFFERED = Integer.MIN_VALUE;

    private final InputStream in;
    private final int delimiterByte;
    private final String sourceLabel;

    private int pending = NO_BYTE_BUFFERED;
    private byte[] scratch = new byte[64];
    private int scratchLen;

    private String[] fields = new String[8];
    private int fieldCount;

    private long recordNumber;

    public CsvReader(InputStream in, char delimiter, String sourceLabel) {
        if (delimiter > 0x7F) {
            throw new IllegalArgumentException("delimiter must be a single ASCII character, got '" + delimiter + "'");
        }
        this.in = in;
        this.delimiterByte = delimiter;
        this.sourceLabel = sourceLabel;
    }

    /**
     * Advances to the next record, skipping any blank lines first (a line with no
     * characters before its terminator, matching Go's {@code encoding/csv} behaviour).
     * Returns {@code false} once the stream is exhausted with no further record.
     */
    public boolean nextRecord() throws IOException {
        if (!skipBlankLines()) {
            return false;
        }

        fieldCount = 0;
        while (true) {
            Terminator terminator = readField();
            addField(materializeField());
            if (terminator != Terminator.DELIMITER) {
                break;
            }
        }
        recordNumber++;
        return true;
    }

    public int fieldCount() {
        return fieldCount;
    }

    public String field(int index) {
        if (index < 0 || index >= fieldCount) {
            throw new IndexOutOfBoundsException("field " + index + " (record has " + fieldCount + ")");
        }
        return fields[index];
    }

    /** 1-based count of records returned by {@link #nextRecord()} so far. */
    public long recordNumber() {
        return recordNumber;
    }

    @Override
    public void close() throws IOException {
        in.close();
    }

    private boolean skipBlankLines() throws IOException {
        while (true) {
            int b = peekByte();
            if (b == -1) {
                return false;
            }
            if (b == '\n') {
                consumeByte();
                continue;
            }
            if (b == '\r') {
                consumeByte();
                if (peekByte() == '\n') {
                    consumeByte();
                }
                continue;
            }
            return true;
        }
    }

    private Terminator readField() throws IOException {
        scratchLen = 0;
        if (peekByte() == '"') {
            consumeByte();
            return readQuotedField();
        }
        return readUnquotedField();
    }

    private Terminator readUnquotedField() throws IOException {
        while (true) {
            int b = peekByte();
            if (b == -1) {
                return Terminator.EOF;
            }
            if (b == delimiterByte) {
                consumeByte();
                return Terminator.DELIMITER;
            }
            if (b == '\n') {
                consumeByte();
                return Terminator.NEWLINE;
            }
            if (b == '\r') {
                consumeByte();
                if (peekByte() == '\n') {
                    consumeByte();
                }
                return Terminator.NEWLINE;
            }
            if (b == '"') {
                throw parseError("bare \" in non-quoted field");
            }
            appendScratch((byte) consumeByte());
        }
    }

    private Terminator readQuotedField() throws IOException {
        while (true) {
            int b = consumeByte();
            if (b == -1) {
                throw parseError("unterminated quoted field");
            }
            if (b == '"') {
                if (peekByte() == '"') {
                    consumeByte();
                    appendScratch((byte) '"');
                    continue;
                }
                return afterClosingQuote();
            }
            appendScratch((byte) b);
        }
    }

    private Terminator afterClosingQuote() throws IOException {
        int b = peekByte();
        if (b == -1) {
            return Terminator.EOF;
        }
        if (b == delimiterByte) {
            consumeByte();
            return Terminator.DELIMITER;
        }
        if (b == '\n') {
            consumeByte();
            return Terminator.NEWLINE;
        }
        if (b == '\r') {
            consumeByte();
            if (peekByte() == '\n') {
                consumeByte();
            }
            return Terminator.NEWLINE;
        }
        throw parseError("extraneous characters after closing quote");
    }

    private CsvParseException parseError(String detail) {
        return new CsvParseException(sourceLabel, recordNumber + 1, detail);
    }

    private int peekByte() throws IOException {
        if (pending == NO_BYTE_BUFFERED) {
            pending = in.read();
        }
        return pending;
    }

    private int consumeByte() throws IOException {
        int b = peekByte();
        pending = NO_BYTE_BUFFERED;
        return b;
    }

    private void appendScratch(byte b) {
        if (scratchLen == scratch.length) {
            byte[] grown = new byte[scratch.length * 2];
            System.arraycopy(scratch, 0, grown, 0, scratchLen);
            scratch = grown;
        }
        scratch[scratchLen++] = b;
    }

    private String materializeField() {
        return new String(scratch, 0, scratchLen, StandardCharsets.UTF_8);
    }

    private void addField(String value) {
        if (fieldCount == fields.length) {
            String[] grown = new String[fields.length * 2];
            System.arraycopy(fields, 0, grown, 0, fieldCount);
            fields = grown;
        }
        fields[fieldCount++] = value;
    }
}
