package com.dex2c.resource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Reads resource names from resource-filter.txt.
 *
 * Format:
 *
 * app_name
 * api_url
 * secret_message
 *
 * Empty lines and lines beginning with # are ignored.
 */
public final class ResourceFilter {

    private final Set<String> names;

    private ResourceFilter(Set<String> names) {
        this.names = names;
    }

    public static ResourceFilter load(Path path)
            throws IOException {

        if (path == null) {
            return new ResourceFilter(
                    Collections.emptySet()
            );
        }

        if (!Files.exists(path)) {
            throw new IOException(
                    "Resource filter not found: " + path
            );
        }

        List<String> lines = Files.readAllLines(
                path,
                StandardCharsets.UTF_8
        );

        Set<String> names = new LinkedHashSet<>();

        for (String line : lines) {

            String value = line.trim();

            if (value.isEmpty()) {
                continue;
            }

            if (value.startsWith("#")) {
                continue;
            }

            names.add(value);
        }

        return new ResourceFilter(names);
    }

    public boolean contains(String resourceName) {
        return names.contains(resourceName);
    }

    public Set<String> getNames() {
        return Collections.unmodifiableSet(names);
    }

    public boolean isEmpty() {
        return names.isEmpty();
    }

    public int size() {
        return names.size();
    }

    @Override
    public String toString() {
        return "ResourceFilter{" +
                "names=" + names +
                '}';
    }
}
