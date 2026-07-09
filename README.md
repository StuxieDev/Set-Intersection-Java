# set-intersection

> Written by Leo Ridgwell as a technical test submission for InfoSum, then reworked in
> Java as a Senior Java Engineer portfolio piece. Standard library only - no application
> frameworks - built with Maven, tested with JUnit 5.

Compares the keys in two CSV files and reports:

- the count of keys in each file
- the count of distinct keys in each file
- the distinct overlap (how many distinct keys appear in both files)
- the total overlap (see [How overlap is defined](#how-overlap-is-defined))

`examples/A_f.csv` and `examples/B_f.csv` are the sample datasets from the original
InfoSum task - single-column CSVs of UDPRN keys with a header row.

## Layout

```
src/main/java/com/ridgwell/setintersection/
  cli/          argument parsing, output formatting, the process entry point
  keyset/       CSV-to-key-counts loading, comparison, chunked/concurrent loading
  csv/          the hand-rolled CSV parser
  collections/  the hand-rolled open-addressing map used for key counting
  io/           file/gzip/stdin input sources
src/test/java/  JUnit 5 tests, mirroring the same package layout
examples/       sample UDPRN datasets from the original task
```

`keyset` has no dependency on `cli`, so another entry point (an HTTP service wrapping the
same package, say) could sit alongside the CLI without reshuffling anything. `io` and
`csv` have no dependency on anything else in this project - they're pure, independently
testable building blocks that `keyset` composes.

## Build & run

Requires JDK 21+ and Maven. No runtime dependencies - `mvn package` produces a
self-contained runnable jar.

```sh
mvn package
java -jar target/set-intersection.jar -file1 examples/A_f.csv -file2 examples/B_f.csv -header -column udprn
```

### Flags

| Flag          | Default      | Description                                                              |
|---------------|--------------|---------------------------------------------------------------------------|
| `-file1`      | *(required)* | Path to the first CSV file, or `-` to read it from stdin                 |
| `-file2`      | *(required)* | Path to the second CSV file, or `-` to read it from stdin                |
| `-header`     | `false`      | Set if the CSV files have a header row (shared by both files)            |
| `-column`     | `0`          | Key column(s): a zero-based index, a header name, or a comma-separated list of either for a composite key |
| `-column1`/`-column2` | *(falls back to `-column`)* | Per-file override of `-column`                          |
| `-delimiter`  | `,`          | Field delimiter, e.g. `;` or `\t` for TSV                                 |
| `-delimiter1`/`-delimiter2` | *(falls back to `-delimiter`)* | Per-file override of `-delimiter`                |
| `-json`       | `false`      | Print the result as JSON instead of a table                              |
| `-help`/`-h`  | `false`      | Print usage and exit                                                     |

`-file1`/`-file2` ending in `.gz`/`.gzip` are transparently gzip-decompressed. At most one
of the two may be `-` (stdin) - stdin can't be read twice.

### Example

```
$ java -jar target/set-intersection.jar -file1 examples/A_f.csv -file2 examples/B_f.csv -header -column udprn
File                                             Keys     Distinct
examples/A_f.csv                                95000        72799
examples/B_f.csv                                80000        72815

Distinct overlap                                58222
Total overlap                                60627882
```

These are the exact figures the original Go version recorded for the same files - a
real cross-check between the two implementations, not just unit tests agreeing with
themselves.

## How overlap is defined

The task PDF gives this example:

```
Dataset 1: A B C D D E F F
Dataset 2: A C C D F F F X Y
Distinct Overlap = A C D F = 4
Total Overlap = A C C D D F F F F F F = 11
```

Distinct overlap is just the size of the set intersection. Total overlap is the "maximum
possible overlap": for each key present in both files, every occurrence in file A could
pair with every occurrence in file B, so each shared key contributes
`count(a) * count(b)`, not `min(count(a), count(b))`. That's the same number a SQL inner
join on the key would produce with no deduplication. `Compare` in `keyset/Compare.java`
implements it that way, and `CompareTest.pdfWorkedExamplePinsDistinctAndTotalOverlap`
pins the numbers down against the PDF's own example.

Keys are treated as strings, not numbers, so `08034283` keeps its leading zero.

## What's more advanced than a straight port

This started as a like-for-like Go-to-Java port, then went further in three directions
rather than staying feature-equivalent:

- **A hand-rolled hash map.** Key frequency counting never uses `java.util.HashMap`.
  `collections/StringIntOpenHashMap.java` is an open-addressing map with linear probing
  over parallel arrays (`String[]` keys, `int[]` values, a tombstone-aware state array),
  a Murmur3-style finalizer over `String.hashCode()` to avoid clustering on structured
  keys (long runs of digits, as UDPRN keys are), and allocation-free iteration - no boxed
  `Integer`, no `Map.Entry`.
- **Concurrency at two levels, both via virtual threads.** The original Go version loads
  its two files concurrently on two goroutines. `keyset/TwoFileLoader.java` does the same
  with `Executors.newVirtualThreadPerTaskExecutor()`, and goes a level further: a single
  large plain file is split into byte-range chunks (`ChunkPlanner`) and parsed in parallel
  on their own virtual threads (`ChunkedCsvLoader`), with the per-chunk results merged
  afterwards. Chunk boundaries are found by a single quote-aware sequential scan rather
  than trying to resolve each boundary independently, which is unsound for CSV: whether a
  given byte offset sits inside a quoted field depends on everything read since the last
  known record start.
- **A broader feature set.** Composite (multi-column) keys, gzip input, stdin input, and
  per-file `-column`/`-delimiter` overrides - the Go README's own "if I had more time"
  list, actually implemented. `keyset/CompositeKeyEncoder.java` combines multi-column keys
  with length-prefixing (`"2:AB1:C"` for `("AB", "C")`) rather than a separator character,
  so it's collision-free by construction with no escaping needed.

Two smaller correctness points worth calling out:

- `Overlap.total` is a `long`, not an `int`. Go's `int` is 64-bit on every real platform,
  so `count(a) * count(b)` summed across many keys never overflows there in practice.
  Java's `int` is always 32-bit - two files each containing on the order of 50,000
  occurrences of one key already produces a product that overflows a 32-bit int, so this
  isn't a style choice, it's required for correctness. `CompareTest` has a regression test
  for exactly this.
- The CSV parser (`csv/CsvReader.java`) is byte-oriented rather than `Reader`-based:
  fields are accumulated as raw bytes and decoded to UTF-8 once, when complete. That's
  safe because the only bytes it treats as control characters - the delimiter, `"`, `\r`,
  `\n` - are all single-byte ASCII values below `0x80`, and UTF-8 continuation/lead bytes
  for any multi-byte character are always `0x80` or above, so a multi-byte character can
  never be mistaken for a control byte mid-scan. That also lets the same reader work
  unmodified over a bounded byte range of a larger file, which the chunked loading path
  depends on.

## Design notes

- CSV files are streamed row by row rather than loaded whole, so memory for a serial load
  is driven by the number of distinct keys, not the number of rows in the file.
- `Compare` walks whichever file has fewer distinct keys and looks each one up in the
  other, so it's `O(min(distinct1, distinct2))`.
- `io.InputSource` is a sealed interface (`RegularFileSource`/`GzipFileSource`/
  `StdinSource`), and `KeysetLoader.load` dispatches over it with Java 21 pattern matching
  for switch - the compiler enforces that every kind of source is handled, and a fourth
  kind would be a compile error at every switch until it's handled too.
- Package dependencies are one-directional: `cli` depends on `keyset`, `keyset` depends on
  `io` and `csv`, and `io`/`csv`/`collections` depend on nothing else in this project.

## Testing

```sh
mvn test
```

Direct ports of every test from the original Go suite (the PDF's worked example,
header/no-header parsing, selecting the key column by name or index, leading zeros, and
the file-not-found/unknown-column/out-of-range-column error cases) plus new coverage for
everything Java-only: the hash map (collisions, resizing, a degenerate all-same-key
stress test cross-checked against `java.util.HashMap`), the CSV parser's quoting/escaping
edge cases, chunk-boundary planning around quoted multi-line fields, a chunked-vs-serial
parse of the same file producing byte-identical results, gzip input, stdin input,
composite keys, and per-file overrides.

## Scaling

Memory for a serial load is one `StringIntOpenHashMap` per file, sized to the number of
distinct keys, not the row count - the same profile as the original Go version's
`map[string]int`. For a single large plain file, `ChunkedCsvLoader` trades some of that
memory-boundedness for wall-clock time above a 32 MiB threshold, splitting it into
byte-range chunks parsed in parallel and merging the per-chunk maps back together.

UDPRN specifically has a natural ceiling regardless: it's a Royal Mail identifier for UK
delivery points, and there are only tens of millions of those in total, so the distinct
key space (and this tool's memory use) is bounded regardless of row count. For a key type
that were much larger or unbounded, the next steps would be an external sort-merge join or
sharding by `hash(key) % N` to get past the memory limit, and HyperLogLog/MinHash for
approximate counts if exact ones stopped being feasible - reporting an error bound
alongside the estimate, per the task's own note on that.

## If I had more time

- Fuzz testing the CSV parsing path, and a benchmark suite so performance regressions get
  caught automatically rather than by hand.
- Extending composite keys to escape a literal comma in a header name (currently a known,
  documented limitation of the CLI's own comma-splitting for `-column`/`-column1`/
  `-column2`).
- Packaging (a Dockerfile, a native image via GraalVM) so it doesn't need a JDK installed
  to run.
