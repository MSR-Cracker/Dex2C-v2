package com.dex2c.pipeline;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

final class ProcessRunner {
    private ProcessRunner() {
    }

    static void run(Path cwd, List<String> command) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(cwd.toFile());
        pb.inheritIO();
        Process process = pb.start();
        int code = process.waitFor();
        if (code != 0) {
            throw new IOException("Command failed (" + code + "): " + String.join(" ", command));
        }
    }
}
