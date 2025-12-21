package org.mcsebi.whereismyshulker.client;

import net.minecraft.block.Block;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.util.WorldSavePath;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ShulkerBoxTracker {
    private static ShulkerBoxTracker instance;
    private final List<ShulkerBoxData> shulkerBoxes = new ArrayList<>();
    private Path csvFilePath;

    private ShulkerBoxTracker() {
    }

    public static ShulkerBoxTracker getInstance() {
        if (instance == null) {
            instance = new ShulkerBoxTracker();
        }
        return instance;
    }

    public void onWorldLoad() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) {
            return;
        }

        csvFilePath = getCsvPath(client);
        loadFromCsv();
    }
    
    public void onWorldUnload() {
        shulkerBoxes.clear();
        csvFilePath = null;
    }

    /**
     * Get the CSV file path based on whether in singleplayer or multiplayer.
     *
     * @param client Minecraft client instance
     * @return Path to the CSV file
     */
    private Path getCsvPath(MinecraftClient client) {
        ClientPlayNetworkHandler networkHandler = client.getNetworkHandler();
        String serverAddress = client.getCurrentServerEntry() != null ?
                client.getCurrentServerEntry().address : "unknown";

        if (!serverAddress.equals("unknown")) {
            // Multiplayer - store in .minecraft/.whereismyshulker/ip_port/

            // Replace colons and other invalid characters
            serverAddress = serverAddress.replace(":", "_").replaceAll("[^a-zA-Z0-9._-]", "_");

            Path minecraftDir = client.runDirectory.toPath();
            Path whereismyshulkerDir = minecraftDir.resolve(".whereismyshulker").resolve(serverAddress);

            try {
                Files.createDirectories(whereismyshulkerDir);
            } catch (IOException e) {
                e.printStackTrace();
            }

            return whereismyshulkerDir.resolve("shulker_boxes.csv");
        } else {
            // Singleplayer - store in world/data/
            ClientWorld world = client.world;
            if (world != null) {
                // Get the save directory for this world
                Path worldDir = client.runDirectory.toPath().resolve("saves");
                if (client.getServer() != null && client.getServer().getSavePath(WorldSavePath.ROOT) != null) {
                    worldDir = client.getServer().getSavePath(WorldSavePath.ROOT);
                }

                Path dataDir = worldDir.resolve("data");
                try {
                    Files.createDirectories(dataDir);
                } catch (IOException e) {
                    e.printStackTrace();
                }

                return dataDir.resolve("shulker_boxes.csv");
            }
        }

        // Fallback to .minecraft/.whereismyshulker/default/
        Path minecraftDir = client.runDirectory.toPath();
        Path whereismyshulkerDir = minecraftDir.resolve(".whereismyshulker").resolve("default");
        try {
            Files.createDirectories(whereismyshulkerDir);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return whereismyshulkerDir.resolve("shulker_boxes.csv");
    }

    private void loadFromCsv() {
        shulkerBoxes.clear();

        if (csvFilePath == null || !Files.exists(csvFilePath)) {
            return;
        }

        try (BufferedReader reader = Files.newBufferedReader(csvFilePath)) {
            String line;
            while ((line = reader.readLine()) != null) {
                ShulkerBoxData data = ShulkerBoxData.fromCsv(line);
                if (data != null) {
                    shulkerBoxes.add(data);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void saveToCsv() {
        if (csvFilePath == null) {
            return;
        }

        try {
            // Ensure parent directory exists
            Files.createDirectories(csvFilePath.getParent());

            // Write all shulker boxes to CSV
            try (BufferedWriter writer = Files.newBufferedWriter(csvFilePath,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                for (ShulkerBoxData data : shulkerBoxes) {
                    writer.write(data.toCsv());
                    writer.newLine();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Reset shulker box data.
     *
     * @param resetAll If true, remove all shulker boxes. If false, only remove undyed shulker boxes.
     */
    public void resetShulkerBoxes(boolean resetAll) {
        if(resetAll) {
            shulkerBoxes.clear();
        } else {
            // only remove undyed shulker boxes
            shulkerBoxes.removeIf(data -> data.getColor().isEmpty());
        }
        saveToCsv();
    }

    /**
     * Called when a shulker box is placed in the world.
     *
     * @param pos Position of the placed shulker box
     * @param block The block that was placed
     * @param world The world where the block was placed
     * @param customName Custom name of the shulker box, if any
     */
    public void onShulkerBoxPlaced(BlockPos pos, Block block, World world, String customName) {
        if (!(block instanceof ShulkerBoxBlock)) {
            return;
        }

        // Get shulker box color
        String color = getShulkerBoxColor(block);

        // Get dimension name
        String dimension = world.getRegistryKey().getValue().toString();

        // Add to list
        ShulkerBoxData data = new ShulkerBoxData(pos, dimension, color, System.currentTimeMillis(), customName);
        shulkerBoxes.add(data);

        // Save to CSV
        saveToCsv();
    }

    public void onShulkerBoxBroken(BlockPos pos) {
        // Remove any shulker box at this position
        shulkerBoxes.removeIf(data -> data.getPosition().equals(pos));

        // Save to CSV
        saveToCsv();
    }

    /**
     * Get the color of the shulker box from the block.
     *
     * @param block The shulker box block
     * @return Color name as a string
     */
    private String getShulkerBoxColor(Block block) {
        String blockName = block.getTranslationKey();
        // Extract color from translation key like "block.minecraft.red_shulker_box"
        if (blockName.contains("shulker_box")) {
            String[] parts = blockName.split("\\.");
            if (parts.length > 0) {
                String lastPart = parts[parts.length - 1];
                if (lastPart.equals("shulker_box")) {
                    return ""; // undyed shulker box
                } else {
                    // Remove "_shulker_box" and capitalize
                    String colorPart = lastPart.replace("_shulker_box", "");
                    return capitalize(colorPart.replace("_", " "));
                }
            }
        }
        return "Unknown";
    }

    /**
     * Capitalize the first letter of each word in the string.
     *
     * @param str Input string
     * @return
     */
    private String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        String[] words = str.split(" ");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (!word.isEmpty()) {
                result.append(Character.toUpperCase(word.charAt(0)));
                if (word.length() > 1) {
                    result.append(word.substring(1).toLowerCase());
                }
                result.append(" ");
            }
        }
        return result.toString().trim();
    }

    public List<ShulkerBoxData> getShulkerBoxes() {
        // Return in reverse order (most recent first)
        List<ShulkerBoxData> reversed = new ArrayList<>(shulkerBoxes);
        Collections.reverse(reversed);
        return reversed;
    }

    public int getShulkerBoxCount() {
        return shulkerBoxes.size();
    }
}
