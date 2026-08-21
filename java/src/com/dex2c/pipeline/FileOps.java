package com.dex2c.pipeline;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;

final class FileOps {
    private FileOps() {
    }

    static void copyTree(Path source, Path target) throws IOException {
        Files.walk(source).forEach(path -> {
            try {
                Path out = target.resolve(source.relativize(path));
                if (Files.isDirectory(path)) {
                    Files.createDirectories(out);
                } else {
                    Files.createDirectories(out.getParent());
                    Files.copy(path, out, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException e) {
                throw new UncheckedIo(e);
            }
        });
    }

    static void deleteTree(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        try (var stream = Files.walk(path)) {
            for (Path p : stream.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(p);
            }
        }
    }

    static final class UncheckedIo extends RuntimeException {
        UncheckedIo(IOException cause) {
            super(cause);
        }
    }
}
