package com.dex2c.pipeline;

import com.dex2c.model.MethodInfo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipFile;

public final class DccPipeline {
    private final Path root;

    public DccPipeline(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    public void run(
            Path inputApk,
            Path outputApk,
            List<MethodInfo> methods) throws Exception {

        if (!inputApk.getFileName().toString().endsWith(".apk")) {
            Files.copy(
                    inputApk,
                    outputApk,
                    StandardCopyOption.REPLACE_EXISTING);
            return;
        }

        if (methods.isEmpty()) {
            Files.copy(
                    inputApk,
                    outputApk,
                    StandardCopyOption.REPLACE_EXISTING);
            return;
        }

        Path tmp =
                root.resolve(".tmp-java");

        FileOps.deleteTree(tmp);
        Files.createDirectories(tmp);

        ToolConfig config =
                ToolConfig.load(root, tmp);

        Path decompiled =
                tmp.resolve("smali-work");

        Path dexDir =
                tmp.resolve("dex");

        Path patchedDexDir =
                tmp.resolve("patched-dex");

        Path nativeProject =
                tmp.resolve("project");

        /*
         * ID -> original string
         */
        Map<Integer, String> nativeStrings =
                new LinkedHashMap<>();

        try {

            disassembleDexes(
                    inputApk.toAbsolutePath(),
                    dexDir,
                    decompiled);

            List<MethodInfo> patched =
                    SmaliPatcher.patch(
                            decompiled,
                            methods,
                            nativeStrings);

            if (patched.isEmpty()) {

                Files.copy(
                        inputApk,
                        outputApk,
                        StandardCopyOption.REPLACE_EXISTING);

                return;
            }

            assembleDexes(
                    decompiled,
                    patchedDexDir);

            FileOps.copyTree(
                    root.resolve("project"),
                    nativeProject);

            writeMinimalNativeMakefiles(
                    nativeProject,
                    allAbis());

            NativeWrapperWriter.write(
                    nativeProject
                            .resolve(
                                    "jni/nc/compiled_methods.cpp"),
                    patched,
                    nativeStrings);

            buildNative(
                    config,
                    nativeProject);

            Path unsignedApk =
                    tmp.resolve("unsigned.apk");

            Path alignedApk =
                    tmp.resolve("aligned.apk");

            RawApkRebuilder.rebuild(
                    inputApk.toAbsolutePath(),
                    unsignedApk,
                    patchedDexDir,
                    nativeProject.resolve("libs"));

            zipalign(
                    unsignedApk,
                    alignedApk);

            sign(
                    config,
                    alignedApk,
                    outputApk.toAbsolutePath());

        } finally {

            FileOps.deleteTree(tmp);
        }
    }

    private void buildNative(
            ToolConfig config,
            Path nativeProject)
            throws IOException, InterruptedException {

        if (!Files.exists(config.ndkBuild)) {
            throw new IOException(
                    "ndk-build not found: "
                            + config.ndkBuild);
        }

        ProcessRunner.run(
                nativeProject,
                List.of(
                        config.ndkBuild.toString(),
                        "-j"
                                + Runtime.getRuntime()
                                .availableProcessors()));
    }

    private void disassembleDexes(
            Path inputApk,
            Path dexDir,
            Path smaliRoot)
            throws IOException, InterruptedException {

        Files.createDirectories(dexDir);
        Files.createDirectories(smaliRoot);

        try (ZipFile zip =
                     new ZipFile(inputApk.toFile())) {

            var entries =
                    zip.entries();

            while (entries.hasMoreElements()) {

                var entry =
                        entries.nextElement();

                String name =
                        entry.getName();

                if (!name.matches(
                        "classes(\\d*)\\.dex")) {
                    continue;
                }

                Path dex =
                        dexDir.resolve(name);

                try (var in =
                             zip.getInputStream(entry)) {

                    Files.copy(
                            in,
                            dex,
                            StandardCopyOption.REPLACE_EXISTING);
                }

                SmaliTools.baksmali(
                        root,
                        dex,
                        smaliRoot.resolve(
                                smaliDirName(name)));
            }
        }
    }

    private void assembleDexes(
            Path smaliRoot,
            Path patchedDexDir)
            throws IOException, InterruptedException {

        Files.createDirectories(
                patchedDexDir);

        try (var stream =
                     Files.list(smaliRoot)) {

            for (Path smaliDir :
                    stream
                            .filter(Files::isDirectory)
                            .toList()) {

                String dexName =
                        dexNameFromSmaliDir(
                                smaliDir
                                        .getFileName()
                                        .toString());

                SmaliTools.smali(
                        root,
                        smaliDir,
                        patchedDexDir.resolve(
                                dexName));
            }
        }
    }

    private String smaliDirName(
            String dexName) {

        if (dexName.equals(
                "classes.dex")) {
            return "smali";
        }

        return "smali_classes"
                + dexName
                .replace("classes", "")
                .replace(".dex", "");
    }

    private String dexNameFromSmaliDir(
            String smaliDir) {

        if (smaliDir.equals("smali")) {
            return "classes.dex";
        }

        return "classes"
                + smaliDir.substring(
                        "smali_classes".length())
                + ".dex";
    }

    private void writeMinimalNativeMakefiles(
            Path nativeProject,
            List<String> abis)
            throws IOException {

        Files.writeString(
                nativeProject.resolve(
                        "jni/Android.mk"),
                """
                LOCAL_PATH:= $(call my-dir)

                include $(CLEAR_VARS)
                LOCAL_MODULE    := %s
                LOCAL_LDLIBS    := -llog
                LOCAL_LDFLAGS += "-Wl,-z,max-page-size=16384"
                LOCAL_SRC_FILES := nc/compiled_methods.cpp

                include $(BUILD_SHARED_LIBRARY)
                """.formatted(
                        NativeNames.MODULE));

        Files.writeString(
                nativeProject.resolve(
                        "jni/Application.mk"),
                """
                APP_STL := c++_static
                APP_CPPFLAGS += -fvisibility=hidden
                APP_PLATFORM := android-21
                APP_ABI := %s
                """.formatted(
                        String.join(" ", abis)));
    }

    private List<String> allAbis() {
        return List.of(
                "armeabi-v7a",
                "arm64-v8a",
                "x86",
                "x86_64");
    }

    private void zipalign(
            Path input,
            Path output)
            throws IOException, InterruptedException {

        Path zipalign =
                findZipalign();

        if (zipalign == null) {

            Files.copy(
                    input,
                    output,
                    StandardCopyOption.REPLACE_EXISTING);

            return;
        }

        ProcessRunner.run(
                root,
                List.of(
                        zipalign.toString(),
                        "-p",
                        "-f",
                        "4",
                        input.toString(),
                        output.toString()));
    }

    private Path findZipalign()
            throws IOException {

        List<Path> candidates =
                new ArrayList<>();

        String androidHome =
                System.getenv("ANDROID_HOME");

        if (androidHome != null) {

            candidates.add(
                    Path.of(
                            androidHome,
                            "build-tools"));
        }

        candidates.add(
                Path.of(
                        "D:/software/Sdk/build-tools"));

        for (Path base : candidates) {

            if (!Files.exists(base)) {
                continue;
            }

            try (var stream =
                         Files.walk(base, 2)) {

                Path found =
                        stream
                                .filter(path ->
                                        path.getFileName()
                                                .toString()
                                                .equals(
                                                        ToolConfig.isWindows()
                                                                ? "zipalign.exe"
                                                                : "zipalign"))
                                .findFirst()
                                .orElse(null);

                if (found != null) {
                    return found;
                }
            }
        }

        return null;
    }

    private void sign(
            ToolConfig config,
            Path unsignedApk,
            Path outputApk)
            throws IOException, InterruptedException {

        ProcessRunner.run(
                root,
                List.of(
                        "java",
                        "-jar",
                        config.apksigner.toString(),
                        "sign",
                        "--ks",
                        config.keystore.toString(),
                        "--ks-key-alias",
                        config.alias,
                        "--ks-pass",
                        "pass:" + config.storePass,
                        "--key-pass",
                        "pass:" + config.keystorePass,
                        "--out",
                        outputApk.toString(),
                        unsignedApk.toString()));
    }
}
