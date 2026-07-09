package com.ridgwell.setintersection.cli;

/** One registered flag's definition - what {@link ArgParser} matches against and what {@code usage()} prints. */
record FlagSpec(String name, FlagType type, String defaultValue, String help) {
}
