package com.ridgwell.setintersection.keyset;

/**
 * Combines several field values into one key string for composite (multi-column) keys.
 *
 * <p>Naive concatenation is ambiguous - {@code ("AB", "C")} and {@code ("A", "BC")} would
 * both produce {@code "ABC"}. Instead each part is length-prefixed: {@code ("AB", "C")}
 * becomes {@code "2:AB1:C"}. A reader always knows exactly how many characters to consume
 * next, so this is provably collision-free for any input - no separator character needs
 * to be reserved or escaped.
 */
final class CompositeKeyEncoder {

    private CompositeKeyEncoder() {
    }

    /** A single-part input is returned untouched, so ordinary single-column keys are byte-identical to the raw field value. */
    static String encode(String[] parts) {
        if (parts.length == 1) {
            return parts[0];
        }
        StringBuilder key = new StringBuilder();
        for (String part : parts) {
            key.append(part.length()).append(':').append(part);
        }
        return key.toString();
    }
}
