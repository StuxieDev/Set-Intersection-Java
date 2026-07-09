package com.ridgwell.setintersection.keyset;

/**
 * Combines several field values into one key string for composite (multi-column) keys.
 *
 * <p>Just concatenating the parts would be ambiguous - {@code ("AB", "C")} and
 * {@code ("A", "BC")} both give {@code "ABC"}. So each part gets length-prefixed instead:
 * {@code ("AB", "C")} becomes {@code "2:AB1:C"}. That tells a reader exactly how many
 * characters belong to each part, so two different inputs can never collide - and there's
 * no separator character to escape if a field happens to contain one.
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
