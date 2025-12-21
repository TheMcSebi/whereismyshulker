package org.mcsebi.whereismyshulker.client;

import net.minecraft.util.math.BlockPos;

public class ShulkerBoxData {
    private final BlockPos position;
    private final String dimension;
    private final String color;
    private final String customName;
    private final long timestamp;

    public ShulkerBoxData(BlockPos position, String dimension, String color, long timestamp, String customName) {
        this.position = position;
        this.dimension = dimension;
        this.color = color;
        this.timestamp = timestamp;
        this.customName = customName;
    }

    public BlockPos getPosition() {
        return position;
    }

    public String getDimension() {
        return dimension;
    }

    public String getColor() {
        return color;
    }

    public long getTimestamp() { return timestamp; }

    public String getCustomName() { return customName; }

    public boolean hasCustomName() { return customName != null && !customName.isEmpty(); }

    // Convert to CSV format
    public String toCsv() {
        return String.format("%d,%d,%d,%s,%s,%d,%s",
                position.getX(),
                position.getY(),
                position.getZ(),
                dimension,
                color,
                timestamp,
                customName);
    }

    // Parse from CSV format
    public static ShulkerBoxData fromCsv(String csvLine) {
        String[] parts = csvLine.split(",");
        if (parts.length != 7 && parts.length != 6) {
            return null;
        }
        try {
            BlockPos pos = new BlockPos(
                    Integer.parseInt(parts[0]),
                    Integer.parseInt(parts[1]),
                    Integer.parseInt(parts[2])
            );
            String customName = parts.length == 7 ? parts[6] : "";
            return new ShulkerBoxData(pos, parts[3], parts[4], Long.parseLong(parts[5]), customName);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public String toString() {
        return String.format("[%s] %s Shulker Box at %d, %d, %d%s",
                dimension,
                color,
                position.getX(),
                position.getY(),
                position.getZ(),
                customName.isEmpty() ? "" : " named '" + customName + "'");
    }
}
