package com.dex2c.pipeline;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

final class SmaliTools {
    private SmaliTools() {
    }

    static void baksmali(Path root, Path dexFile, Path outDir) throws IOException, InterruptedException {
        ProcessRunner.run(root, List.of(
                "java", "-cp", currentJar(),
                "org.jf.baksmali.Main", "d",
                "-o", outDir.toString(),
                dexFile.toString()
        ));
    }

    static void smali(Path root, Path smaliDir, Path outDex) throws IOException, InterruptedException {
        ProcessRunner.run(root, List.of(
                "java", "-cp", currentJar(),
                "org.jf.smali.Main", "a",
                "-o", outDex.toString(),
                smaliDir.toString()
        ));
    }

    private static String currentJar() {
        try {
            return Path.of(SmaliTools.class.getProtectionDomain()
                    .getCodeSource()
                    .getLocation()
                    .toURI()).toString();
        } catch (Exception e) {
            return "dex2cxx.jar";
        }
    }
}
