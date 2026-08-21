package com.dex2c.pipeline;

import com.dex2c.model.MethodInfo;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class SmaliPatcher {

    private static final String STRING_CLASS =
            "Lcom/dex2c/NativeStrings;";

    private SmaliPatcher() {
    }

    static List<MethodInfo> patch(
            Path decompiledDir,
            List<MethodInfo> methods,
            Map<Integer, String> stringsOut)
            throws IOException {

        List<MethodInfo> patched = new ArrayList<>();

        for (MethodInfo method :
                methods.stream()
                        .sorted(Comparator.comparing(MethodInfo::toDexSignature))
                        .toList()) {

            Path smali = findSmali(decompiledDir, method);

            if (smali == null) {
                continue;
            }

            if (patchMethod(smali, method, stringsOut)) {
                patched.add(method);
            }
        }

        if (!stringsOut.isEmpty()) {
            writeNativeStringsSmali(decompiledDir);
        }

        return patched;
    }

    private static Path findSmali(
            Path decompiledDir,
            MethodInfo method)
            throws IOException {

        String relative =
                method.className()
                        .substring(
                                1,
                                method.className().length() - 1)
                        + ".smali";

        try (var stream = Files.walk(decompiledDir)) {
            return stream
                    .filter(path ->
                            path.toString()
                                    .replace('\\', '/')
                                    .endsWith(relative))
                    .findFirst()
                    .orElse(null);
        }
    }

    private static boolean patchMethod(
            Path smali,
            MethodInfo method,
            Map<Integer, String> stringsOut)
            throws IOException {

        List<String> lines =
                Files.readAllLines(
                        smali,
                        StandardCharsets.UTF_8);

        String suffix =
                method.methodName()
                        + method.descriptor();

        for (int i = 0; i < lines.size(); i++) {

            String trimmed =
                    lines.get(i).trim();

            if (!trimmed.startsWith(".method ")
                    || !trimmed.endsWith(suffix)) {
                continue;
            }

            int end = findEnd(lines, i + 1);

            if (end < 0
                    || trimmed.contains(" native ")
                    || trimmed.contains(" abstract ")) {
                return false;
            }

            List<String> body =
                    new ArrayList<>(
                            lines.subList(i + 1, end));

            patchStrings(body, stringsOut);

            List<String> replacement =
                    new ArrayList<>();

            replacement.add(
                    makeNativeMethodLine(
                            lines.get(i),
                            method));

            replacement.add(".end method");
            replacement.add("");

            replacement.add(
                    makeRenamedMethodLine(
                            lines.get(i),
                            method));

            replacement.addAll(body);
            replacement.add(".end method");

            lines.subList(i, end + 1).clear();
            lines.addAll(i, replacement);

            ensureLoadLibrary(lines);

            Files.write(
                    smali,
                    lines,
                    StandardCharsets.UTF_8);

            return true;
        }

        return false;
    }

    private static void patchStrings(
            List<String> body,
            Map<Integer, String> stringsOut) {

        if (body.isEmpty()) {
            return;
        }

        /*
         * We deliberately use a LOCAL register.
         *
         * Do not modify .registers directly because
         * that can change p0/p1/... parameter mapping.
         */
        int tempRegister =
                findSafeLocalRegister(body);

        boolean changed = false;

        for (int i = 0; i < body.size(); i++) {

            String line = body.get(i);
            String trimmed = line.trim();

            if (!trimmed.startsWith("const-string ")
                    && !trimmed.startsWith("const-string/jumbo ")) {
                continue;
            }

            String[] parts =
                    trimmed.split("\\s+", 3);

            if (parts.length != 3) {
                continue;
            }

            String targetRegister =
                    parts[1].replace(",", "");

            String literal =
                    parts[2];

            if (!literal.startsWith("\"")
                    || !literal.endsWith("\"")) {
                continue;
            }

            String value =
                    decodeSmaliString(literal);

            int id =
                    findOrCreateId(
                            stringsOut,
                            value);

            String indent =
                    getIndent(line);

            String temp =
                    "v" + tempRegister;

            List<String> replacement =
                    new ArrayList<>();

            replacement.add(
                    indent
                            + "const "
                            + temp
                            + ", "
                            + id);

            replacement.add(
                    indent
                            + "invoke-static {"
                            + temp
                            + "}, "
                            + STRING_CLASS
                            + "->get(I)Ljava/lang/String;");

            replacement.add(
                    indent
                            + "move-result-object "
                            + targetRegister);

            body.remove(i);
            body.addAll(i, replacement);

            changed = true;

            /*
             * Move to another unused register for the
             * next string in the same method.
             */
            tempRegister =
                    findSafeLocalRegister(body);

            i += replacement.size() - 1;
        }

        if (changed) {
            ensureLocalRegister(body, tempRegister);
        }
    }

    /*
     * Pick a register after the currently existing locals.
     *
     * This avoids overwriting an existing v-register.
     */
    private static int findSafeLocalRegister(
            List<String> body) {

        int locals = getLocals(body);

        Set<Integer> used =
                collectUsedVRegisters(body);

        int candidate = Math.max(locals, 0);

        while (used.contains(candidate)) {
            candidate++;
        }

        return candidate;
    }

    private static Set<Integer> collectUsedVRegisters(
            List<String> body) {

        Set<Integer> used =
                new HashSet<>();

        for (String line : body) {

            String trimmed =
                    line.trim();

            if (trimmed.startsWith(".")
                    || trimmed.startsWith(":")) {
                continue;
            }

            String[] parts =
                    trimmed.split("[\\s{},]+");

            for (String part : parts) {

                if (!part.startsWith("v")
                        || part.length() <= 1) {
                    continue;
                }

                try {

                    int number =
                            Integer.parseInt(
                                    part.substring(1));

                    if (number >= 0) {
                        used.add(number);
                    }

                } catch (NumberFormatException ignored) {
                }
            }
        }

        return used;
    }

    private static int getLocals(
            List<String> body) {

        for (String line : body) {

            String trimmed =
                    line.trim();

            if (trimmed.startsWith(".locals ")) {

                try {
                    return Integer.parseInt(
                            trimmed.substring(
                                    ".locals ".length())
                                    .trim());
                } catch (NumberFormatException ignored) {
                    return 0;
                }
            }

            if (trimmed.startsWith(".registers ")) {
                /*
                 * For .registers methods we don't reuse
                 * parameter registers. We convert the
                 * register layout to .locals while
                 * preserving the original parameter count.
                 */
                return 0;
            }
        }

        return 0;
    }

    private static void ensureLocalRegister(
            List<String> body,
            int register) {

        for (int i = 0; i < body.size(); i++) {

            String trimmed =
                    body.get(i).trim();

            if (trimmed.startsWith(".locals ")) {

                int current =
                        parseDirective(
                                trimmed,
                                ".locals");

                if (register >= current) {

                    String indent =
                            getIndent(body.get(i));

                    body.set(
                            i,
                            indent
                                    + ".locals "
                                    + (register + 1));
                }

                return;
            }

            if (trimmed.startsWith(".registers ")) {

                int registers =
                        parseDirective(
                                trimmed,
                                ".registers");

                /*
                 * Convert .registers N to:
                 *
                 * .locals N
                 *
                 * is NOT safe because parameters occupy
                 * the end of the register file.
                 *
                 * Instead keep the total register count
                 * and only expand it by one.
                 */
                String indent =
                        getIndent(body.get(i));

                if (register >= registers) {

                    body.set(
                            i,
                            indent
                                    + ".registers "
                                    + (register + 1));
                }

                return;
            }
        }
    }

    private static int parseDirective(
            String line,
            String directive) {

        try {
            return Integer.parseInt(
                    line.substring(
                            directive.length())
                            .trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static int findOrCreateId(
            Map<Integer, String> strings,
            String value) {

        for (Map.Entry<Integer, String> entry :
                strings.entrySet()) {

            if (entry.getValue().equals(value)) {
                return entry.getKey();
            }
        }

        int id = strings.size();

        while (strings.containsKey(id)) {
            id++;
        }

        strings.put(id, value);

        return id;
    }

    private static String decodeSmaliString(
            String literal) {

        if (literal.length() < 2) {
            return literal;
        }

        String value =
                literal.substring(
                        1,
                        literal.length() - 1);

        StringBuilder out =
                new StringBuilder();

        boolean escaped = false;

        for (int i = 0; i < value.length(); i++) {

            char c = value.charAt(i);

            if (!escaped) {

                if (c == '\\') {
                    escaped = true;
                } else {
                    out.append(c);
                }

                continue;
            }

            switch (c) {

                case 'n':
                    out.append('\n');
                    break;

                case 'r':
                    out.append('\r');
                    break;

                case 't':
                    out.append('\t');
                    break;

                case 'b':
                    out.append('\b');
                    break;

                case 'f':
                    out.append('\f');
                    break;

                case '"':
                    out.append('"');
                    break;

                case '\\':
                    out.append('\\');
                    break;

                default:
                    out.append(c);
                    break;
            }

            escaped = false;
        }

        if (escaped) {
            out.append('\\');
        }

        return out.toString();
    }

    private static void writeNativeStringsSmali(
            Path decompiledDir)
            throws IOException {

        /*
         * Put NativeStrings in the primary dex.
         */
        Path file =
                decompiledDir
                        .resolve("smali")
                        .resolve(
                                "com/dex2c/NativeStrings.smali");

        Files.createDirectories(
                file.getParent());

        String content = """
                .class public final Lcom/dex2c/NativeStrings;
                .super Ljava/lang/Object;

                .method static constructor <clinit>()V
                    .locals 1
                    const-string v0, "msr"
                    invoke-static {v0}, Ljava/lang/System;->loadLibrary(Ljava/lang/String;)V
                    return-void
                .end method

                .method public static native get(I)Ljava/lang/String;
                .end method
                """;

        Files.writeString(
                file,
                content,
                StandardCharsets.UTF_8);
    }

    private static int findEnd(
            List<String> lines,
            int start) {

        for (int i = start; i < lines.size(); i++) {

            if (lines.get(i)
                    .trim()
                    .equals(".end method")) {
                return i;
            }
        }

        return -1;
    }

    private static String makeNativeMethodLine(
            String line,
            MethodInfo method) {

        String indent =
                getIndent(line);

        String body =
                line.trim();

        Set<String> tokens =
                new HashSet<>(
                        List.of(
                                body.split("\\s+")));

        String signature =
                method.methodName()
                        + method.descriptor();

        String prefix =
                body.substring(
                        0,
                        body.length()
                                - signature.length())
                        .trim();

        if (!tokens.contains("native")) {
            prefix += " native";
        }

        return indent
                + prefix
                + " "
                + signature;
    }

    private static String makeRenamedMethodLine(
            String line,
            MethodInfo method) {

        String original =
                method.methodName()
                        + method.descriptor();

        String renamed =
                JniNames.renamedMethod(method)
                        + method.descriptor();

        return line.substring(
                0,
                line.length() - original.length())
                + renamed;
    }

    private static void ensureLoadLibrary(
            List<String> lines) {

        for (String line : lines) {

            if (line.contains(
                    "loadLibrary(Ljava/lang/String;)V")
                    && line.contains(
                    NativeNames.MODULE)) {
                return;
            }
        }

        for (int i = 0; i < lines.size(); i++) {

            String trimmed =
                    lines.get(i).trim();

            if (!trimmed.startsWith(".method ")
                    || !trimmed.endsWith("<clinit>()V")) {
                continue;
            }

            int directive =
                    findDirective(
                            lines,
                            i + 1,
                            ".locals",
                            ".registers");

            if (directive >= 0) {

                String indent =
                        getIndent(
                                lines.get(directive));

                lines.set(
                        directive,
                        bumpLocalDirective(
                                lines.get(directive)));

                lines.add(
                        directive + 1,
                        indent
                                + "const-string v0, \""
                                + NativeNames.MODULE
                                + "\"");

                lines.add(
                        directive + 2,
                        indent
                                + "invoke-static {v0}, Ljava/lang/System;->loadLibrary(Ljava/lang/String;)V");

                return;
            }
        }

        int insertAt = lines.size();

        lines.add(insertAt, "");

        lines.add(
                insertAt + 1,
                ".method static constructor <clinit>()V");

        lines.add(
                insertAt + 2,
                "    .locals 1");

        lines.add(
                insertAt + 3,
                "    const-string v0, \""
                        + NativeNames.MODULE
                        + "\"");

        lines.add(
                insertAt + 4,
                "    invoke-static {v0}, Ljava/lang/System;->loadLibrary(Ljava/lang/String;)V");

        lines.add(
                insertAt + 5,
                "    return-void");

        lines.add(
                insertAt + 6,
                ".end method");
    }

    private static int findDirective(
            List<String> lines,
            int start,
            String... directives) {

        for (int i = start; i < lines.size(); i++) {

            String trimmed =
                    lines.get(i).trim();

            for (String directive :
                    directives) {

                if (trimmed.startsWith(
                        directive + " ")) {
                    return i;
                }
            }

            if (trimmed.equals(
                    ".end method")) {
                return -1;
            }
        }

        return -1;
    }

    private static String bumpLocalDirective(
            String line) {

        String trimmed =
                line.trim();

        String directive =
                trimmed.startsWith(".registers")
                        ? ".registers"
                        : ".locals";

        String indent =
                getIndent(line);

        String[] parts =
                trimmed.split("\\s+");

        int count =
                Integer.parseInt(parts[1]);

        return indent
                + directive
                + " "
                + Math.max(count, 1);
    }

    private static String getIndent(
            String line) {

        int i = 0;

        while (i < line.length()
                && Character.isWhitespace(
                line.charAt(i))) {
            i++;
        }

        return line.substring(0, i);
    }
}
