package com.ridgwell.setintersection.cli;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A small hand-rolled flag parser - the standard library has nothing built in, and using
 * a library like picocli/JCommander would violate the "no frameworks" constraint.
 *
 * <p>Supports {@code -flag value}, {@code -flag=value}, {@code --flag}, {@code --flag=value},
 * and bare boolean flags that don't consume a following token - the same conventions Go's
 * {@code flag} package uses, so adjacent positional-looking arguments parse the same way.
 * Unknown flags and missing values for string flags are reported as {@link CliUsageException}s;
 * a repeated flag simply has its last occurrence win.
 */
final class ArgParser {

    private final Map<String, FlagSpec> specs = new LinkedHashMap<>();
    private final Map<String, String> stringValues = new HashMap<>();
    private final Map<String, Boolean> boolValues = new HashMap<>();

    /** Registers a string-valued flag; returns {@code this} so registrations can be chained. */
    ArgParser stringFlag(String name, String defaultValue, String help) {
        specs.put(name, new FlagSpec(name, FlagType.STRING, defaultValue, help));
        stringValues.put(name, defaultValue);
        return this;
    }

    /** Registers a boolean flag. */
    ArgParser boolFlag(String name, boolean defaultValue, String help) {
        specs.put(name, new FlagSpec(name, FlagType.BOOLEAN, String.valueOf(defaultValue), help));
        boolValues.put(name, defaultValue);
        return this;
    }

    /** Walks {@code args} left to right, filling in {@code stringValues}/{@code boolValues} as it goes. */
    void parse(String[] args) throws CliUsageException {
        int i = 0;
        while (i < args.length) {
            String token = args[i++];
            String body = stripLeadingDashes(token);

            String name;
            String inlineValue = null;
            int eq = body.indexOf('=');
            if (eq >= 0) {
                name = body.substring(0, eq);
                inlineValue = body.substring(eq + 1);
            } else {
                name = body;
            }

            FlagSpec spec = specs.get(name);
            if (spec == null) {
                throw new CliUsageException("unknown flag: " + token);
            }

            if (spec.type() == FlagType.BOOLEAN) {
                // A bare "-header" means true; it never eats the next token as its value,
                // which matters so it doesn't accidentally swallow a following file path.
                boolValues.put(name, inlineValue == null || Boolean.parseBoolean(inlineValue));
                continue;
            }

            String value = inlineValue;
            if (value == null) {
                if (i >= args.length) {
                    throw new CliUsageException("-" + name + " requires a value");
                }
                value = args[i++];
            }
            stringValues.put(name, value);
        }
    }

    /** Strips a leading {@code -}/{@code --} off a token, or complains if there isn't one to strip. */
    private static String stripLeadingDashes(String token) throws CliUsageException {
        if (token.startsWith("--")) {
            return token.substring(2);
        }
        if (token.startsWith("-") && token.length() > 1) {
            return token.substring(1);
        }
        throw new CliUsageException("unexpected argument: " + token);
    }

    String getString(String name) {
        return stringValues.get(name);
    }

    boolean getBoolean(String name) {
        return boolValues.get(name);
    }

    /** Builds the usage text shown on `-help` and on a parse/validation error. */
    String usage(String programName) {
        StringBuilder sb = new StringBuilder();
        sb.append("Usage: ").append(programName)
                .append(" -file1 <path> -file2 <path> [-header] [-column <spec>] [-delimiter <char>] [-json]\n");
        for (FlagSpec spec : specs.values()) {
            String placeholder = spec.type() == FlagType.BOOLEAN ? "" : " <value>";
            sb.append(String.format("  -%s%-14s %s (default %s)\n", spec.name(), placeholder, spec.help(), quote(spec)));
        }
        return sb.toString();
    }

    /** Quotes a string flag's default for display; a boolean's default (true/false) needs no quoting. */
    private static String quote(FlagSpec spec) {
        return spec.type() == FlagType.STRING ? "\"" + spec.defaultValue() + "\"" : spec.defaultValue();
    }
}
