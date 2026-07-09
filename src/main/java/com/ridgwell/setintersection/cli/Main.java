package com.ridgwell.setintersection.cli;

/** Command-line entry point: compares the keys in two CSV files and reports how much they overlap. */
public final class Main {

    private Main() {
    }

    /** Delegates to {@link Cli#run} and exits with whatever code it returns. */
    public static void main(String[] args) {
        System.exit(Cli.run(args, System.out, System.err));
    }
}
