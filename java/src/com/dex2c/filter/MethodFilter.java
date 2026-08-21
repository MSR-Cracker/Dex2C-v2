package com.dex2c.filter;

import com.dex2c.model.MethodInfo;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public final class MethodFilter {
    private final List<Rule> whitelist;
    private final List<Rule> blacklist;

    private MethodFilter(List<Rule> whitelist, List<Rule> blacklist) {
        this.whitelist = whitelist;
        this.blacklist = blacklist;
    }

    public static MethodFilter matchAll() {
        return new MethodFilter(List.of(new Rule(Pattern.compile(".*"))), List.of());
    }

    public static MethodFilter fromFile(Path path) throws IOException {
        List<Rule> whitelist = new ArrayList<>();
        List<Rule> blacklist = new ArrayList<>();

        for (String rawLine : Files.readAllLines(path, StandardCharsets.UTF_8)) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }

            boolean black = line.startsWith("!");
            if (black) {
                line = line.substring(1);
            }

            line = normalizeRule(line);
            Rule rule = new Rule(Pattern.compile(line));
            if (black) {
                blacklist.add(rule);
            } else {
                whitelist.add(rule);
            }
        }

        if (whitelist.isEmpty()) {
            whitelist.add(new Rule(Pattern.compile(".*")));
        }
        return new MethodFilter(List.copyOf(whitelist), List.copyOf(blacklist));
    }

    private static String normalizeRule(String line) {
        if (line.indexOf(';') >= 0) {
            return line;
        }

        boolean simpleClassName = line.matches("[A-Za-z_$][A-Za-z0-9_$.]*");
        if (simpleClassName) {
            return line.replace('.', '/') + ";.*";
        }
        return line;
    }

    public boolean matches(MethodInfo method) {
        String signature = method.toFilterSignature();
        for (Rule rule : blacklist) {
            if (rule.matches(signature)) {
                return false;
            }
        }
        for (Rule rule : whitelist) {
            if (rule.matches(signature)) {
                return true;
            }
        }
        return false;
    }

    private record Rule(Pattern pattern) {
        boolean matches(String signature) {
            return pattern.matcher(signature).matches();
        }
    }
}
