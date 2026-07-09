package com.ridgwell.setintersection.keyset;

/** A byte range within a file: {@code [start, end)}, end exclusive. */
record ChunkRange(long start, long end) {
}
