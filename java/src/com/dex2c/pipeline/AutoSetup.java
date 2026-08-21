package com.dex2c.pipeline;

import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Comparator;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class AutoSetup {
    private static final String REPOSITORY = "https://dl.google.com/android/repository/repository2-1.xml";
    private static final String REPOSITORY_BASE = "https://dl.google.com/android/repository/";

    private AutoSetup() {
    }

    public static void ensure(Path root) throws Exception {
        Path ndk = ensureNdk(root.toAbsolutePath().normalize());
        System.out.println("NDK ready: " + ndk);
        System.out.println("Config updated: " + configPath(root.toAbsolutePath().normalize()));
    }

    static Path ensureNdk(Path root) throws IOException {
        root = root.toAbsolutePath().normalize();
        Path cfg = configPath(root);
        String json = readOrCreateConfig(cfg);
        Path configured = Path.of(stringValue(json, "ndk_dir", root.resolve("android-ndk").toString()));
        if (Files.exists(ndkBuild(configured))) {
            return configured;
        }

        Path localRoot = root.resolve("android-ndk");
        Path existing = findExistingNdk(localRoot);
        if (existing != null) {
            updateConfig(cfg, existing);
            return existing;
        }

        try {
            System.out.println("NDK not found. Downloading to: " + localRoot);
            NdkArchive archive = findLatestArchive();
            Files.createDirectories(localRoot);
            Path zip = localRoot.resolve(archive.fileName());
            download(archive.url(), zip);
            unzip(zip, localRoot);
            Files.deleteIfExists(zip);

            Path installed = findExistingNdk(localRoot);
            if (installed == null) {
                throw new IOException("NDK extracted but ndk-build was not found under " + localRoot);
            }
            updateConfig(cfg, installed);
            return installed;
        } catch (Exception e) {
            if (e instanceof IOException io) {
                throw io;
            }
            throw new IOException("Auto NDK setup failed: " + e.getMessage(), e);
        }
    }

    private static Path configPath(Path root) {
        return Files.exists(root.resolve("dxx.cfg")) ? root.resolve("dxx.cfg") : root.resolve("dxx.cfg");
    }

    private static String readOrCreateConfig(Path cfg) throws IOException {
        if (Files.exists(cfg)) {
            return Files.readString(cfg, StandardCharsets.UTF_8);
        }
        String json = """
                {
                    "ndk_dir": "%s",
                    "signature": {
                        "keystore_path": "keystore/debug.keystore",
                        "alias": "androiddebugkey",
                        "keystore_pass": "android",
                        "store_pass": "android",
                        "v1_enabled": true,
                        "v2_enabled": true,
                        "v3_enabled": true
                    }
                }
                """.formatted(cfg.getParent().resolve("android-ndk").toString().replace("\\", "\\\\"));
        Files.writeString(cfg, json, StandardCharsets.UTF_8);
        return json;
    }

    private static Path findExistingNdk(Path root) throws IOException {
        if (!Files.exists(root)) {
            return null;
        }
        try (var stream = Files.walk(root, 4)) {
            return stream
                    .filter(path -> path.getFileName().toString().equals(ToolConfig.isWindows() ? "ndk-build.cmd" : "ndk-build"))
                    .map(Path::getParent)
                    .sorted(Comparator.reverseOrder())
                    .findFirst()
                    .orElse(null);
        }
    }

    private static Path ndkBuild(Path ndkDir) {
        return ndkDir.resolve(ToolConfig.isWindows() ? "ndk-build.cmd" : "ndk-build");
    }

    private static void updateConfig(Path cfg, Path ndkDir) throws IOException {
        String json = readOrCreateConfig(cfg);
        String escaped = ndkDir.toAbsolutePath().normalize().toString().replace("\\", "\\\\");
        if (json.matches("(?s).*\"ndk_dir\"\\s*:\\s*\"[^\"]*\".*")) {
            json = json.replaceFirst("\"ndk_dir\"\\s*:\\s*\"[^\"]*\"", "\"ndk_dir\": \"" + escaped.replace("$", "\\$") + "\"");
        } else {
            json = json.replaceFirst("\\{", "{\n    \"ndk_dir\": \"" + escaped.replace("$", "\\$") + "\",");
        }
        Files.writeString(cfg, json, StandardCharsets.UTF_8);
    }

    private static NdkArchive findLatestArchive() throws Exception {
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();
        HttpRequest request = HttpRequest.newBuilder(URI.create(REPOSITORY)).timeout(Duration.ofMinutes(2)).build();
        try (InputStream in = client.send(request, HttpResponse.BodyHandlers.ofInputStream()).body()) {
            var doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(in);
            NodeList packages = doc.getElementsByTagName("remotePackage");
            NdkArchive best = null;
            for (int i = 0; i < packages.getLength(); i++) {
                Element pkg = (Element) packages.item(i);
                String path = pkg.getAttribute("path");
                if (!path.startsWith("ndk;")) {
                    continue;
                }
                String revision = text(pkg, "revision");
                NodeList archives = pkg.getElementsByTagName("archive");
                for (int a = 0; a < archives.getLength(); a++) {
                    Element archive = (Element) archives.item(a);
                    String hostOs = text(archive, "host-os");
                    if (!hostMatches(hostOs)) {
                        continue;
                    }
                    String url = text(archive, "url");
                    NdkArchive candidate = new NdkArchive(REPOSITORY_BASE + url, url, revision);
                    if (best == null || candidate.compareTo(best) > 0) {
                        best = candidate;
                    }
                }
            }
            if (best == null) {
                throw new IOException("No matching NDK archive found in Android repository metadata");
            }
            return best;
        }
    }

    private static boolean hostMatches(String hostOs) {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            return hostOs.equals("windows");
        }
        if (os.contains("mac")) {
            return hostOs.equals("macosx");
        }
        return hostOs.equals("linux");
    }

    private static String text(Element root, String tag) {
        NodeList nodes = root.getElementsByTagName(tag);
        if (nodes.getLength() == 0) {
            return "";
        }
        return nodes.item(0).getTextContent().trim();
    }

    private static void download(String url, Path out) throws Exception {
        System.out.println("Downloading: " + url);
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofHours(1)).build();
        HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() / 100 != 2) {
            throw new IOException("Download failed HTTP " + response.statusCode());
        }
        try (InputStream in = response.body()) {
            Files.copy(in, out, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void unzip(Path zip, Path outDir) throws IOException {
        System.out.println("Extracting: " + zip);
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zip))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Path out = outDir.resolve(entry.getName()).normalize();
                if (!out.startsWith(outDir.normalize())) {
                    throw new IOException("Blocked unsafe zip entry: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(out);
                } else {
                    Files.createDirectories(out.getParent());
                    Files.copy(zis, out, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private static String stringValue(String json, String key, String fallback) {
        var matcher = java.util.regex.Pattern.compile("\"" + java.util.regex.Pattern.quote(key) + "\"\\s*:\\s*\"([^\"]*)\"").matcher(json);
        return matcher.find() ? matcher.group(1).replace("\\\\", "\\") : fallback;
    }

    private record NdkArchive(String url, String fileName, String revision) implements Comparable<NdkArchive> {
        @Override
        public int compareTo(NdkArchive other) {
            return compareRevision(revision, other.revision);
        }

        private static int compareRevision(String left, String right) {
            String[] a = left.split("\\.");
            String[] b = right.split("\\.");
            for (int i = 0; i < Math.max(a.length, b.length); i++) {
                int ai = i < a.length ? parse(a[i]) : 0;
                int bi = i < b.length ? parse(b[i]) : 0;
                if (ai != bi) {
                    return Integer.compare(ai, bi);
                }
            }
            return 0;
        }

        private static int parse(String value) {
            try {
                return Integer.parseInt(value.replaceAll("\\D.*", ""));
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
    }
}
