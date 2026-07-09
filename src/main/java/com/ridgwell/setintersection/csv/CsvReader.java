package com.ridgwell.setintersection.csv;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * A streaming RFC4180-style CSV reader, written by hand since the standard library
 * doesn't ship one (this replaces Go's {@code encoding/csv}).
 *
 * <p>It reads raw bytes rather than characters, decoding each field to UTF-8 only once
 * it's complete. That works because the only bytes it ever treats as meaningful - the
 * delimiter, {@code "}, {@code \r}, {@code \n} - are plain ASCII below 0x80, and no
 * multi-byte UTF-8 character ever produces a byte in that range. It's also what lets the
 * same reader run over a byte-range slice of a bigger file unmodified, which the chunked
 * loading path depends on.
 *
 * <p>Quoting follows Go's strict (non-lazy) default: a field only counts as quoted if it
 * starts with {@code "}; a doubled {@code ""} inside one is a literal quote; a bare quote
 * anywhere else is a parse error. Rows can have different numbers of fields from each
 * other - there's no fixed-arity check, same as Go's {@code FieldsPerRecord = -1}.
 * Delimiters have to be a single ASCII byte, since that's what the byte-level scan here
 * assumes.
 */
public final class CsvReader implements Closeable {

    // What ended the field just read.
    private enum Terminator { DELIMITER, NEWLINE, EOF }

    // Distinct from every real value InputStream.read() can return (0-255, or -1 at EOF),
    // so it's safe to use as "nothing buffered yet".
    private static final int NO_BYTE_BUFFERED = Integer.MIN_VALUE;

    private final InputStream in;
    private final int delimiterByte;
    // Just for error messages - identifies where this reader's bytes are coming from.
    private final String sourceLabel;

    // One-byte lookahead, since a few decisions (is this "\r" part of "\r\n"?) need a peek
    // without actually consuming the byte.
    private int pending = NO_BYTE_BUFFERED;
    // Reused across fields instead of reallocated - only grows if a field outgrows it.
    private byte[] scratch = new byte[64];
    private int scratchLen;

    // Reused across records the same way.
    private String[] fields = new String[8];
    private int fieldCount;

    private long recordNumber;

    /**
     * @param in          the byte stream to read CSV records from
     * @param delimiter   the field separator; must be a single ASCII character (see class docs)
     * @param sourceLabel a label identifying this stream's origin, used only in error messages
     */
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
        // A record is one or more fields; DELIMITER means another field follows in this
        // same record, NEWLINE/EOF means the record (and the loop) is complete.
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

    /** Number of fields in the record most recently returned by {@link #nextRecord()}. */
    public int fieldCount() {
        return fieldCount;
    }

    /** The field at {@code index} (0-based) in the current record. */
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

    /** Closes the underlying stream. */
    @Override
    public void close() throws IOException {
        in.close();
    }

    /** Consumes leading blank lines (bare {@code \n} or {@code \r\n} with nothing before them); returns false only at true EOF. */
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
            // Real content ahead - stop skipping.
            return true;
        }
    }

    /** Reads one field (quoted or not) into {@code scratch} and reports how it ended. */
    private Terminator readField() throws IOException {
        scratchLen = 0;
        // A field is "quoted" only if its very first byte is a quote (strict-mode rule) -
        // any other quote placement is handled as an error by readUnquotedField/afterClosingQuote.
        if (peekByte() == '"') {
            consumeByte();
            return readQuotedField();
        }
        return readUnquotedField();
    }

    /** Reads an unquoted field's bytes up to (and consuming) its terminator. */
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
                // Strict mode: a quote is only legal as the very first byte of a field.
                throw parseError("bare \" in non-quoted field");
            }
            appendScratch((byte) consumeByte());
        }
    }

    /** Reads a quoted field's bytes (handling doubled-quote escaping) up to its closing quote. */
    private Terminator readQuotedField() throws IOException {
        while (true) {
            int b = consumeByte();
            if (b == -1) {
                throw parseError("unterminated quoted field");
            }
            if (b == '"') {
                if (peekByte() == '"') {
                    // Doubled quote: a literal '"' inside the field, not the closing quote.
                    consumeByte();
                    appendScratch((byte) '"');
                    continue;
                }
                // A lone quote (not doubled) closes the field.
                return afterClosingQuote();
            }
            appendScratch((byte) b);
        }
    }

    /** After a quoted field's closing quote, expects a delimiter/newline/EOF - anything else is a parse error (strict mode). */
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

    /** Builds a {@link CsvParseException} labelled with this reader's source and the record currently being read. */
    private CsvParseException parseError(String detail) {
        return new CsvParseException(sourceLabel, recordNumber + 1, detail);
    }

    /** Returns the next byte without consuming it, buffering it in {@code pending} until {@link #consumeByte()} is called. */
    private int peekByte() throws IOException {
        if (pending == NO_BYTE_BUFFERED) {
            pending = in.read();
        }
        return pending;
    }

    /** Returns and consumes the next byte (reading one first if none is buffered). */
    private int consumeByte() throws IOException {
        int b = peekByte();
        pending = NO_BYTE_BUFFERED;
        return b;
    }

    /** Appends one raw byte to the current field's scratch buffer, growing it (doubling) if full. */
    private void appendScratch(byte b) {
        if (scratchLen == scratch.length) {
            byte[] grown = new byte[scratch.length * 2];
            System.arraycopy(scratch, 0, grown, 0, scratchLen);
            scratch = grown;
        }
        scratch[scratchLen++] = b;
    }

    /** Decodes the current field's accumulated bytes to a UTF-8 string - the only point a field's bytes become a String. */
    private String materializeField() {
        return new String(scratch, 0, scratchLen, StandardCharsets.UTF_8);
    }

    /** Appends a decoded field to the current record's field list, growing it (doubling) if full. */
    private void addField(String value) {
        if (fieldCount == fields.length) {
            String[] grown = new String[fields.length * 2];
            System.arraycopy(fields, 0, grown, 0, fieldCount);
            fields = grown;
        }
        fields[fieldCount++] = value;
    }
}
