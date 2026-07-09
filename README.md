# set-intersection

> Originally written in Go by Leo Ridgwell as a technical test submission for InfoSum.
> Rebuilt in Java for a Senior Java Engineer application. JDK standard library only, no
> application frameworks - Maven for the build, JUnit 5 for tests.

Compares the keys in two CSV files and reports:

- the count of keys in each file
- the count of distinct keys in each file
- the distinct overlap (how many distinct keys appear in both files)
- the total overlap (see [How overlap is defined](#how-overlap-is-defined) below)

`examples/A_f.csv` and `examples/B_f.csv` are the sample datasets from the original
InfoSum task - single-column CSVs of UDPRN keys with a header row.

## Layout

```
src/main/java/com/ridgwell/setintersection/
  cli/          argument parsing, output formatting, the process entry point
  keyset/       CSV-to-key-counts loading, comparison, chunked/concurrent loading
  csv/          the hand-rolled CSV parser
  collections/  the hand-rolled hash map used for key counting
  io/           file/gzip/stdin input sources
src/test/java/  JUnit 5 tests, same package layout as main
examples/       sample UDPRN datasets from the original task
```

`keyset` doesn't know `cli` exists, so a different front end (an HTTP service wrapping the
same package, say) could sit next to it without touching anything. `io` and `csv` don't
depend on anything else in the project either - they're the two building blocks `keyset`
is made of.

## Build & run

Needs JDK 21+ and Maven. No runtime dependencies, so `mvn package` gives you a jar you can
just run:

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

Give `-file1`/`-file2` a path ending in `.gz`/`.gzip` and it's decompressed on the fly. Only
one of the two can be `-` (stdin) though, since stdin can't be read twice.

### Example

```
$ java -jar target/set-intersection.jar -file1 examples/A_f.csv -file2 examples/B_f.csv -header -column udprn
File                                             Keys     Distinct
examples/A_f.csv                                95000        72799
examples/B_f.csv                                80000        72815

Distinct overlap                                58222
Total overlap                                60627882
```

Those numbers match what the Go version produced on the same files, which was a nice
sanity check to have - two independent implementations agreeing beats unit tests agreeing
with themselves.

## How overlap is defined

The task PDF gives this example:

```
Dataset 1: A B C D D E F F
Dataset 2: A C C D F F F X Y
Distinct Overlap = A C D F = 4
Total Overlap = A C C D D F F F F F F = 11
```

Distinct overlap is just the set intersection size. Total overlap took me a second look
the first time around (back in the Go version): a plain multiset intersection gives
`1+1+1+2 = 5` here, not 11. Working backwards from 11 instead, each shared key contributes
`count(a) * count(b)`, not `min(count(a), count(b))` - basically the row count you'd get
from a SQL join on the key with no dedup. `Compare.compare` implements it that way, and
`CompareTest` pins the exact numbers from the PDF's example.

Keys are strings, not numbers, so `08034283` keeps its leading zero.

## What's actually different from a straight port

I didn't just translate the Go line by line - once the port worked I pushed on three
things a straight port wouldn't have:

**A hash map I wrote myself.** Key frequency counting doesn't touch `java.util.HashMap`
anywhere. `StringIntOpenHashMap` is open addressing with linear probing over three plain
arrays (keys, values, a tombstone-aware state byte per slot), with a Murmur3-style
finalizer on top of `String.hashCode()` because the raw hash spreads badly for keys that
are mostly digits, which is exactly what UDPRN values look like. No `Integer` boxing, no
`Map.Entry` objects - iteration just calls back with a `String` and an `int` directly.

**Concurrency at two levels.** Go's version loads both files at once on two goroutines.
`TwoFileLoader` does the same with virtual threads, then goes further: a single large file
gets split into byte ranges and parsed in parallel too (`ChunkPlanner` +
`ChunkedCsvLoader`). The one thing that took real thought here was finding chunk
boundaries safely - you can't just pick a byte offset and scan forward, because whether
that offset sits inside a quoted field depends on everything read since the last known
record start. So it's one sequential quote-aware scan from a known-good starting point,
not N independent lookups.

**More than the Go version could do.** Composite (multi-column) keys, gzip input, stdin,
and per-file column/delimiter overrides - basically the Go README's own "if I had more
time" list, done for real this time. Composite keys are length-prefixed
(`"2:AB1:C"` for `("AB", "C")`) instead of joined with a separator character, so there's
no escaping to get wrong and no way for two different inputs to collide.

Worth flagging two smaller things too:

- `Overlap.total` is a `long`. Go's `int` is 64-bit in practice so `count(a) * count(b)`
  summed across keys never overflowed there, but Java's `int` is always 32-bit - two files
  with ~50,000 occurrences of the same key already overflows it. Not a style choice,
  actually necessary. There's a regression test for it in `CompareTest`.
- The CSV parser reads raw bytes rather than characters. That only works because every
  control byte it looks for - the delimiter, `"`, `\r`, `\n` - is plain ASCII below `0x80`,
  and UTF-8 never uses those byte values for anything else in a multi-byte character. It's
  what lets the same reader run unmodified over a slice of a bigger file, which the
  chunked path relies on.

## Design notes

- CSV files are streamed row by row, so memory for a normal (non-chunked) load tracks the
  number of distinct keys, not the row count.
- `Compare` walks whichever file has fewer distinct keys and looks each one up in the
  other side, so it's `O(min(distinct1, distinct2))` rather than a full cross product.
- `InputSource` is a sealed interface with three implementations (file, gzip, stdin), and
  `KeysetLoader.load` dispatches over it with a Java 21 pattern-matching switch - add a
  fourth kind later and the compiler will point at every switch that needs updating.
- Package dependencies only point one way: `cli` → `keyset` → `io`/`csv`. Nothing in `io`,
  `csv`, or `collections` depends on anything else here.

## Testing

```sh
mvn test
```

Every test from the original Go suite has a direct equivalent here - the PDF worked
example, header/no-header parsing, column by name or index, leading zeros, the
missing-file/unknown-column/out-of-range error cases. On top of that there's coverage for
everything that's Java-only: the hash map (collisions, resizing, a stress test checked
against a real `java.util.HashMap`), the CSV parser's quoting edge cases, chunk boundaries
around quoted multi-line fields, a chunked-vs-serial parse of the same file coming out
byte-identical, gzip, stdin, composite keys, and the per-file overrides.

## Scaling

A normal load costs one `StringIntOpenHashMap` per file, sized to distinct keys rather
than row count - same memory profile the Go version had with its `map[string]int`. Past
32 MiB, a single plain file gets split into chunks and parsed in parallel instead, trading
a bit of that memory-boundedness for wall-clock time.

UDPRN has its own natural ceiling anyway - it's a Royal Mail identifier, and there are
only tens of millions of delivery points in the UK, so the key space stays bounded no
matter how big the input files get. For a key type that could grow much larger, the next
step would be an external sort-merge join or sharding by `hash(key) % N`, and
HyperLogLog/MinHash for approximate counts once exact ones stop being practical - with an
error bound reported alongside, per the task's own note about that.

## If I had more time

- Fuzz testing for the CSV parser, and a benchmark suite so a perf regression gets caught
  automatically instead of by hand.
- Composite keys can't currently handle a header name that itself contains a comma - the
  CLI's own comma-splitting for `-column`/`-column1`/`-column2` would misread it. Known
  limitation, not fixed.
- A Dockerfile or a GraalVM native image, so running this doesn't need a JDK installed.
