package org.mcsebi.whereismyshulker.client;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

import java.net.URI;
import java.util.HashMap;
import java.util.List;

public class WhereismyshulkerClient implements ClientModInitializer {

    private static final int ITEMS_PER_PAGE = 8; // maybe a maximum of 9 with nav would be possible on one screen, but 8 can be calculated more easily

    @Override
    public void onInitializeClient() {
        // Initialize tracker when world loads
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
            // Register for world load/unload events
        });

        // Register world join event
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            ShulkerBoxTracker.getInstance().onWorldLoad();
        });

        // Register world leave event
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            ShulkerBoxTracker.getInstance().onWorldUnload();
        });

        // Register the /shulker command and its subcommands
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(ClientCommandManager.literal("shulker")
                        // /shulker  (default page = 1)
                        .executes(context -> showShulkerList(context.getSource(), "1"))

                        // /shulker <page>
                        .then(ClientCommandManager.argument("page", StringArgumentType.string())
                                .executes(context -> {
                                    String page = StringArgumentType.getString(context, "page");
                                    return showShulkerList(context.getSource(), page);
                                })
                        )

                        // /shulker clear
                        .then(ClientCommandManager.literal("clear")
                                .executes(context -> clearShulkerList(context.getSource(), false))
                        )

                        // /shulker clearAll
                        .then(ClientCommandManager.literal("clear all")
                                .executes(context -> clearShulkerList(context.getSource(), true))
                        )

                        // /shulker config [arg1 [arg2]]
                        .then(ClientCommandManager.literal("config")
                                // /shulker config
                                .executes(context -> printProperties(context.getSource()))

                                // /shulker config <arg1>
                                .then(ClientCommandManager.argument("arg1", StringArgumentType.string())
                                        .executes(context -> {
                                            String arg1 = StringArgumentType.getString(context, "arg1");
                                            return printProperty(context.getSource(), arg1);
                                        })

                                        // /shulker config <arg1> <arg2>
                                        .then(ClientCommandManager.argument("arg2", StringArgumentType.string())
                                                .executes(context -> {
                                                    String arg1 = StringArgumentType.getString(context, "arg1");
                                                    String arg2 = StringArgumentType.getString(context, "arg2");
                                                    return setProperty(context.getSource(), arg1, arg2);
                                                })
                                        )
                                )
                        )
                )
        );
    }

    /**
     * Display the shulker box list for the given page.
     *
     * @param source Command sender source
     * @param arg Command argument as string
     * @return Command result status
     */
    private int showShulkerList(FabricClientCommandSource source, String arg) {
        ShulkerBoxTracker tracker = ShulkerBoxTracker.getInstance();
        List<ShulkerBoxData> shulkerBoxes = tracker.getShulkerBoxes();

        if (shulkerBoxes.isEmpty()) {
            source.sendFeedback(Text.literal("No shulker boxes tracked yet!").formatted(Formatting.YELLOW));
            return 1;
        }

        // Check if arg is the parge or a reset command
        int page;
        try {
            page = Integer.parseInt(arg);
        } catch (NumberFormatException e) {

            source.sendError(Text.literal("Invalid page number! Please enter a valid integer or type 'reset' to reset your shulker history."));
            return 0;
        }

        int totalPages = (int) Math.ceil((double) shulkerBoxes.size() / ITEMS_PER_PAGE);

        if (page < 1 || page > totalPages) {
            if(totalPages == 1) {
                source.sendError(Text.literal("Invalid page number! There is only one page."));
            } else {
                source.sendError(Text.literal("Invalid page number! Valid pages: 1-" + totalPages));
            }
            return 0;
        }

        int startIndex = (page - 1) * ITEMS_PER_PAGE;
        int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, shulkerBoxes.size());

        // Header
        source.sendFeedback(Text.literal("=== Shulker Box Tracker ===").formatted(Formatting.GOLD, Formatting.BOLD));
        source.sendFeedback(Text.literal("Page " + page + " of " + totalPages + " (" + shulkerBoxes.size() + " total)")
                .formatted(Formatting.GRAY));

        BlockPos playerPos = source.getPlayer().getBlockPos();
        String playerDim = source.getWorld().getRegistryKey().getValue().toString(); // returns e.g. minecraft:overworld

        // List shulker boxes for this page
        for (int i = startIndex; i < endIndex; i++) {
            ShulkerBoxData data = shulkerBoxes.get(i);
            int boxNumber = i + 1;

            String shulkerName = data.getColor() + " Shulker Box";
            if(data.hasCustomName()) {
                shulkerName = data.getCustomName();
            }
            shulkerName = shulkerName.trim();

            // generate info about dimension, distance and direction
            Text dimensionInfo;
            Text distanceInfo;
            if(data.getDimension().equals(playerDim)) {
                // same dimension - show distance and direction
                dimensionInfo = Text.literal("");

                double horizontalDistance = getHorizontalDistance(playerPos, data.getPosition());
                String direction = getDirection(playerPos, data.getPosition());
                int verticalDistance = Math.abs(playerPos.getY() - data.getPosition().getY());
                String belowOrAbove = data.getPosition().getY() < playerPos.getY() ? "v" : "^";

                distanceInfo = Text.literal(String.format(" [%d %s, %d %s]", (int)horizontalDistance, direction, verticalDistance, belowOrAbove))
                        .formatted(Formatting.DARK_GRAY);

            } else {
                // different dimension - show dimension only
                dimensionInfo = Text.literal("(").formatted(Formatting.GRAY)
                        .append(Text.literal(formatDimension(data.getDimension()).formatted(Formatting.WHITE))
                                .append(Text.literal(")").formatted(Formatting.GRAY)));

                distanceInfo = Text.literal("").formatted(Formatting.DARK_GRAY);
            }

            MutableText message = Text.literal(boxNumber + ". ").formatted(Formatting.WHITE)
                    .append(Text.literal(shulkerName).formatted(getColorFormatting(data.getColor())))
                    .append(Text.literal(" (").formatted(Formatting.GRAY))
                    .append(createClickableCoords(data))
                    .append(Text.literal(") ").formatted(Formatting.GRAY))
                    .append(dimensionInfo)
                    .append(distanceInfo)
                    .append(Text.literal(" [").formatted(Formatting.DARK_GRAY))
                    .append(createBlueMapLink(data))
                    .append(Text.literal("]").formatted(Formatting.DARK_GRAY));


            source.sendFeedback(message);
        }

        // Footer with navigation (if necessary)
        MutableText navigation = Text.literal("");
        boolean hasNav = false;

        if (page > 1) {
            int prev = page - 1;
            navigation.append(Text.literal("[← Prev]")
                    .formatted(Formatting.YELLOW)
                    .styled(style -> style
                            .withClickEvent(new ClickEvent.RunCommand("/shulker " + prev))
                            .withHoverEvent(new HoverEvent.ShowText(Text.literal("Go to page " + prev)))
                    ));
            navigation.append(Text.literal(" "));
            hasNav = true;
        }

        if (page < totalPages) {
            int next = page + 1;
            navigation.append(Text.literal("[Next →]")
                    .formatted(Formatting.YELLOW)
                    .styled(style -> style
                            .withClickEvent(new ClickEvent.RunCommand("/shulker " + next))
                            .withHoverEvent(new HoverEvent.ShowText(Text.literal("Go to page " + next)))
                    ));
            hasNav = true;
        }

        if (hasNav) {
            source.sendFeedback(Text.literal("")); // Empty line before navigation
            source.sendFeedback(navigation);
        }

        return 1;
    }

    /**
     * Create clickable coordinates text component.
     *
     * @param data Shulker box data
     * @return Clickable text component with coordinates
     */
    private MutableText createClickableCoords(ShulkerBoxData data) {
        BlockPos pos = data.getPosition();

        String coords = pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
        String cmd = "/tp @s " + pos.getX() + " " + pos.getY() + " " + pos.getZ();

        return Text.literal(coords)
                .formatted(Formatting.GREEN)
                .styled(style -> style
                        .withClickEvent(new ClickEvent.SuggestCommand(cmd))
                        .withHoverEvent(new HoverEvent.ShowText(Text.literal("Click to teleport")))
                );
    }
    /**
     * Create clickable BlueMap Link.
     *  url: <baseUrl>/#world:<x>:<y>:<z>:5:1.04:1.08:0:0:perspective
     * @param data Shulker box data
     * @return Clickable text component with coordinates
     */
    private MutableText createBlueMapLink(ShulkerBoxData data) {
        BlockPos pos = data.getPosition();
        ShulkerBoxTracker tracker = ShulkerBoxTracker.getInstance();
        String baseUrl = tracker.getProperty("blueMapBaseUrl", "http://localhost:8100");

        String url = String.format("%s/#%s:%d:%d:%d:5:1.04:1.08:0:0:perspective",
                baseUrl,
                getBlueMapWorldName(data.getDimension()),
                pos.getX(), pos.getY()+2, pos.getZ()); // y-pos +2 to center on shulker box, bluemap want's it this way

        URI uri = URI.create(url);

        return Text.literal("BlueMap")
                .formatted(Formatting.BLUE)
                .styled(style -> style
                        .withClickEvent(new ClickEvent.OpenUrl(uri))
                        .withHoverEvent(new HoverEvent.ShowText(Text.literal("Show on BlueMap")))
                );
    }

    /**
     * Get the Minecraft Formatting color for the given color name.
     *
     * @param color Color name
     * @return Formatting color
     */
    private Formatting getColorFormatting(String color) {
        return switch (color.toLowerCase()) {
            case "white" -> Formatting.WHITE;
            case "orange" -> Formatting.GOLD;
            case "magenta" -> Formatting.LIGHT_PURPLE;
            case "light blue" -> Formatting.AQUA;
            case "yellow" -> Formatting.YELLOW;
            case "lime" -> Formatting.GREEN;
            case "pink" -> Formatting.LIGHT_PURPLE;
            case "gray" -> Formatting.DARK_GRAY;
            case "light gray" -> Formatting.GRAY;
            case "cyan" -> Formatting.DARK_AQUA;
            case "purple" -> Formatting.DARK_PURPLE;
            case "blue" -> Formatting.BLUE;
            case "brown" -> Formatting.GOLD;
            case "green" -> Formatting.DARK_GREEN;
            case "red" -> Formatting.RED;
            case "black" -> Formatting.BLACK;
            default -> Formatting.LIGHT_PURPLE;
        };
    }

    /**
     * Get cardinal direction from one position to another.
     *
     * @param from First position
     * @param to Second position
     * @return Direction as string (N, NE, E, SE, S, SW, W, NW)
     */
    private String getDirection(BlockPos from, BlockPos to) {
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        double angle = Math.toDegrees(Math.atan2(-dx, dz));
        if (angle < 0) angle += 360;

        if (angle >= 337.5 || angle < 22.5) return "S";
        if (angle >= 22.5 && angle < 67.5) return "SW";
        if (angle >= 67.5 && angle < 112.5) return "W";
        if (angle >= 112.5 && angle < 157.5) return "NW";
        if (angle >= 157.5 && angle < 202.5) return "N";
        if (angle >= 202.5 && angle < 247.5) return "NE";
        if (angle >= 247.5 && angle < 292.5) return "E";
        if (angle >= 292.5 && angle < 337.5) return "SE";

        return "Unknown";
    }

    /**
     * Calculate horizontal distance between two BlockPos.
     *
     * @param from First position
     * @param to Second position
     * @return Horizontal distance
     */
    private double getHorizontalDistance(BlockPos from, BlockPos to) {
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    /**
     * Format dimension string to a more user-friendly name.
     *
     * @param dimension Dimension identifier
     * @return Formatted dimension name
     */
    private String formatDimension(String dimension) {
        if (dimension.contains("overworld")) {
            return "Overworld";
        } else if (dimension.contains("the_nether")) {
            return "Nether";
        } else if (dimension.contains("the_end")) {
            return "End";
        }
        return dimension;
    }

    /**
     * Get BlueMap world name based on dimension.
     *
     * @param dimension Dimension identifier
     * @return BlueMap world name
     */
    private String getBlueMapWorldName(String dimension) {
        if (dimension.contains("overworld")) {
            return "world";
        } else if (dimension.contains("the_nether")) {
            return "world_nether";
        } else if (dimension.contains("the_end")) {
            return "world_the_end";
        }
        return "world";
    }

    /**
     * Clear shulker list - wrapper for /shulker clearAll command.
     *
     * @param source Command sender source
     * @param clearAll If true, clears all shulker boxes. If false, clears default shulker boxes.
     * @return Command result status
     */
    private int clearShulkerList(FabricClientCommandSource source, boolean clearAll) {
        ShulkerBoxTracker tracker = ShulkerBoxTracker.getInstance();
        tracker.resetShulkerBoxes(clearAll);

        if (clearAll) {
            source.sendFeedback(Text.literal("All shulker boxes have been reset.").formatted(Formatting.GREEN));
        } else {
            source.sendFeedback(Text.literal("Default shulker boxes have been reset.").formatted(Formatting.GREEN));
        }
        return 1;
    }

    /**
     * Print all properties - handler for /shulker config.
     *
     * @param source Command sender source
     * @return Command result status
     */
    private int printProperties(FabricClientCommandSource source) {
        ShulkerBoxTracker tracker = ShulkerBoxTracker.getInstance();
        HashMap<String, String> props = tracker.getProperties();

        if (props == null || props.isEmpty()) {
            source.sendFeedback(Text.literal("No configuration properties found.").formatted(Formatting.YELLOW));
            return 1;
        }

        source.sendFeedback(Text.literal("=== Configuration Properties ===").formatted(Formatting.GOLD, Formatting.BOLD));
        for (String key : props.keySet()) {
            source.sendFeedback(Text.literal(key + " = " + props.get(key)).formatted(Formatting.WHITE));
        }
        return 1;
    }

    /**
     * Print a specific property - handler for /shulker config <arg1>.
     *
     * @param source Command sender source
     * @param key Property key
     * @return Command result status
     */
    private int printProperty(FabricClientCommandSource source, String key) {
        ShulkerBoxTracker tracker = ShulkerBoxTracker.getInstance();
        String value = tracker.getProperty(key, null);

        if (value == null) {
            source.sendError(Text.literal("Property '" + key + "' not found."));
            return 0;
        }

        source.sendFeedback(Text.literal(key + " = " + value).formatted(Formatting.WHITE));
        return 1;
    }

    /**
     * Set a property - handler for /shulker config <arg1> <arg2>.
     *
     * @param source Command sender source
     * @param key Property key
     * @param value Property value
     * @return Command result status
     */
    private int setProperty(FabricClientCommandSource source, String key, String value) {
        ShulkerBoxTracker tracker = ShulkerBoxTracker.getInstance();

        if (tracker.setProperty(key, value)) {
            source.sendFeedback(Text.literal("Property '" + key + "' set to '" + value + "'.").formatted(Formatting.GREEN));
            return 1;
        } else {
            source.sendError(Text.literal("Failed to set property '" + key + "'."));
            return 0;
        }
    }
}
