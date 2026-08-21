package com.dex2c.pipeline;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ToolConfig {
    final Path apktool;
    final Path ndkBuild;
    final Path apksigner;
    final Path keystore;
    final String alias;
    final String keystorePass;
    final String storePass;

    private ToolConfig(Path apktool, Path ndkBuild, Path apksigner, Path keystore,
                       String alias, String keystorePass, String storePass) {
        this.apktool = apktool;
        this.ndkBuild = ndkBuild;
        this.apksigner = apksigner;
        this.keystore = keystore;
        this.alias = alias;
        this.keystorePass = keystorePass;
        this.storePass = storePass;
    }

    static ToolConfig load(Path root, Path tmp) throws IOException {
        Path configPath = Files.exists(root.resolve("dxx.cfg")) ? root.resolve("dxx.cfg") : root.resolve("dcc.cfg");
        String json = Files.readString(configPath, StandardCharsets.UTF_8);
        Path apktool = null;
        Path ndkDir = AutoSetup.ensureNdk(root);
        Path ndkBuild = ndkDir.resolve(isWindows() ? "ndk-build.cmd" : "ndk-build");
        Path apksigner = resolveTool(root, tmp, "tools/apksigner.jar", "apksigner.jar");
        Path keystore = root.resolve(stringValue(json, "keystore_path", "keystore/debug.keystore"));
        String alias = stringValue(json, "alias", "androiddebugkey");
        String keyPass = stringValue(json, "keystore_pass", "android");
        String storePass = stringValue(json, "store_pass", "android");
        return new ToolConfig(apktool, ndkBuild, apksigner, keystore, alias, keyPass, storePass);
    }

    private static Path resolveTool(Path root, Path tmp, String configuredPath, String resourceName) throws IOException {
        Path local = root.resolve(configuredPath);
        if (Files.exists(local)) {
            return local;
        }

        Path extracted = tmp.resolve("embedded-tools").resolve(resourceName);
        Files.createDirectories(extracted.getParent());
        try (InputStream in = ToolConfig.class.getResourceAsStream("/dex2c-tools/" + resourceName)) {
            if (in == null) {
                throw new IOException("Missing embedded tool: " + resourceName);
            }
            Files.copy(in, extracted, StandardCopyOption.REPLACE_EXISTING);
        }
        return extracted;
    }

    private static String stringValue(String json, String key, String fallback) {
        Matcher matcher = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"([^\"]*)\"").matcher(json);
        return matcher.find() ? matcher.group(1).replace("\\\\", "\\") : fallback;
    }

    static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }
}
