package com.dex2c.resource;

/**
 * Represents a string resource selected for protection.
 */
public final class ResourceEntry {

    private final String name;
    private final int resourceId;
    private final String value;

    public ResourceEntry(
            String name,
            int resourceId,
            String value
    ) {
        this.name = name;
        this.resourceId = resourceId;
        this.value = value;
    }

    public String getName() {
        return name;
    }

    public int getResourceId() {
        return resourceId;
    }

    public String getValue() {
        return value;
    }

    @Override
    public String toString() {
        return "ResourceEntry{" +
                "name='" + name + '\'' +
                ", resourceId=0x" +
                Integer.toHexString(resourceId) +
                ", value='" + value + '\'' +
                '}';
    }
}
