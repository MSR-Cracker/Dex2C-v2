package com.dex2c.cli;

import java.nio.file.Path;

final class CliOptions {
    final Path inputPath;
    final Path outputPath;
    final Path filterPath;
    final Path reportPath;
    final boolean listOnly;
    final boolean help;
    final boolean autoSetup;

    private CliOptions(Path inputPath, Path outputPath, Path filterPath, Path reportPath,
                       boolean listOnly, boolean help, boolean autoSetup) {
        this.inputPath = inputPath;
        this.outputPath = outputPath;
        this.filterPath = filterPath;
        this.reportPath = reportPath;
        this.listOnly = listOnly;
        this.help = help;
        this.autoSetup = autoSetup;
    }

    static CliOptions parse(String[] args) {
        Path input = null;
        Path output = null;
        Path filter = null;
        Path report = null;
        boolean listOnly = false;
        boolean help = false;
        boolean autoSetup = false;

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            switch (arg) {
                case "-a":
                case "--input":
                    input = Path.of(requireValue(args, ++i, arg));
                    break;
                case "-o":
                case "--out":
                case "--output":
                    output = Path.of(requireValue(args, ++i, arg));
                    break;
                case "--filter":
                    filter = Path.of(requireValue(args, ++i, arg));
                    break;
                case "--report":
                    report = Path.of(requireValue(args, ++i, arg));
                    break;
                case "--list-only":
                    listOnly = true;
                    break;
                case "-auto":
                case "--auto":
                    autoSetup = true;
                    break;
                case "-h":
                case "--help":
                    help = true;
                    break;
                default:
                    throw new IllegalArgumentException("Unknown argument: " + arg);
            }
        }

        if (!help && !autoSetup && input == null) {
            throw new IllegalArgumentException("Missing required -a/--input APK or DEX path");
        }

        if (filter == null && java.nio.file.Files.exists(Path.of("filter.txt"))) {
            filter = Path.of("filter.txt");
        }

        return new CliOptions(input, output, filter, report, listOnly, help, autoSetup);
    }

    static void printHelp() {
        System.out.println("Usage: java -jar dex2cxx.jar -a input.apk [-o output.apk] [--filter filter.txt] [--report methods.txt] [--list-only]");
        System.out.println("       java -jar dex2cxx.jar -auto");
    }

    private static String requireValue(String[] args, int index, String option) {
        if (index >= args.length) {
            throw new IllegalArgumentException("Missing value for " + option);
        }
        return args[index];
    }
}
