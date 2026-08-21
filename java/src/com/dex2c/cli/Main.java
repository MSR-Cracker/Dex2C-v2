package com.dex2c.cli;

import com.dex2c.dex.DexScanner;
import com.dex2c.filter.MethodFilter;
import com.dex2c.model.MethodInfo;
import com.dex2c.pipeline.AutoSetup;
import com.dex2c.pipeline.DccPipeline;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

public final class Main {
    private Main() {
    }

    public static void main(String[] args) throws Exception {
        System.out.println("dev - @msr_cracker");
        CliOptions options = CliOptions.parse(args);
        if (options.help) {
            CliOptions.printHelp();
            return;
        }
        if (options.autoSetup) {
            AutoSetup.ensure(Path.of("."));
            return;
        }

        MethodFilter filter = options.filterPath == null
                ? MethodFilter.matchAll()
                : MethodFilter.fromFile(options.filterPath);

        DexScanner scanner = new DexScanner(filter);
        List<MethodInfo> methods = scanner.scan(options.inputPath);

        if (options.listOnly) {
            for (MethodInfo method : methods) {
                System.out.println(method.toDexSignature());
            }
        }

        if (options.reportPath != null) {
            writeReport(options.reportPath, methods);
        }

        List<MethodInfo> wrappable = methods.stream()
                .filter(MethodInfo::canWrapNative)
                .collect(Collectors.toList());

        if (options.outputPath != null) {
            new DccPipeline(Path.of(".")).run(options.inputPath, options.outputPath, wrappable);
            System.out.printf("Wrote protected APK/DEX: %s%n", options.outputPath);
        }

        System.out.printf("dexlib2 scan complete: %d method(s) matched, %d method(s) wrappable%n",
                methods.size(), wrappable.size());
    }

    private static void writeReport(Path reportPath, List<MethodInfo> methods) throws IOException {
        Path parent = reportPath.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        StringBuilder out = new StringBuilder();
        for (MethodInfo method : methods) {
            out.append(method.dexEntry())
                    .append('\t')
                    .append(method.accessFlags())
                    .append('\t')
                    .append(method.toDexSignature())
                    .append(System.lineSeparator());
        }
        Files.writeString(reportPath, out.toString(), StandardCharsets.UTF_8);
    }
}
