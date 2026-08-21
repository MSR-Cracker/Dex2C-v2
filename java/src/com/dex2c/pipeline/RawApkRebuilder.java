package com.dex2c.pipeline;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

final class RawApkRebuilder {
    private RawApkRebuilder() {
    }

    static void rebuild(Path inputApk, Path outputApk, Path patchedDexDir, Path nativeLibDir) throws IOException {
        Set<String> replaced = new TreeSet<>();
        try (ZipFile in = new ZipFile(inputApk.toFile());
             ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(outputApk))) {
            var entries = in.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                if (shouldSkipOriginal(name)) {
                    continue;
                }
                ZipEntry copy = new ZipEntry(name);
                copy.setTime(entry.getTime());
                if (entry.getMethod() == ZipEntry.STORED) {
                    copy.setMethod(ZipEntry.STORED);
                    copy.setSize(entry.getSize());
                    copy.setCompressedSize(entry.getCompressedSize());
                    copy.setCrc(entry.getCrc());
                }
                out.putNextEntry(copy);
                if (!entry.isDirectory()) {
                    try (InputStream is = in.getInputStream(entry)) {
                        is.transferTo(out);
                    }
                }
                out.closeEntry();
            }

            for (Path dex : listFiles(patchedDexDir)) {
                String name = dex.getFileName().toString();
                if (name.matches("classes(\\d*)\\.dex")) {
                    putDeflated(out, name, dex);
                    replaced.add(name);
                }
            }
            for (Path lib : listFiles(nativeLibDir)) {
                String relative = nativeLibDir.relativize(lib).toString().replace('\\', '/');
                putStored(out, "lib/" + relative, lib);
            }
        }
        if (replaced.isEmpty()) {
            throw new IOException("No patched dex files were written");
        }
    }

    private static boolean shouldSkipOriginal(String name) {
        if (name.matches("classes(\\d*)\\.dex")) {
            return true;
        }
        if (name.matches("META-INF/[^/]+\\.(RSA|DSA|EC|SF)") || name.equals("META-INF/MANIFEST.MF")) {
            return true;
        }
        return name.matches("lib/[^/]+/" + NativeNames.LIB_FILE.replace(".", "\\."));
    }

    private static List<Path> listFiles(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return List.of();
        }
        try (var stream = Files.walk(dir)) {
            return stream.filter(Files::isRegularFile).toList();
        }
    }

    private static void putDeflated(ZipOutputStream out, String name, Path file) throws IOException {
        ZipEntry entry = new ZipEntry(name);
        entry.setTime(Files.getLastModifiedTime(file).toMillis());
        out.putNextEntry(entry);
        Files.copy(file, out);
        out.closeEntry();
    }

    private static void putStored(ZipOutputStream out, String name, Path file) throws IOException {
        byte[] data = Files.readAllBytes(file);
        CRC32 crc = new CRC32();
        crc.update(data);
        ZipEntry entry = new ZipEntry(name);
        entry.setMethod(ZipEntry.STORED);
        entry.setSize(data.length);
        entry.setCompressedSize(data.length);
        entry.setCrc(crc.getValue());
        entry.setTime(Files.getLastModifiedTime(file).toMillis());
        out.putNextEntry(entry);
        out.write(data);
        out.closeEntry();
    }
}
