package org.mcsebi.whereismyshulker.client;

import com.mojang.brigadier.arguments.IntegerArgumentType;
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
import net.minecraft.world.World;
import net.minecraft.world.dimension.DimensionType;

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

        // Register the /shulker command with pagination
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
            dispatcher.register(ClientCommandManager.literal("shulker")
                .executes(context -> {
                    // Show page 1 by default
                    return showShulkerList(context.getSource(), "1");
                })
                .then(ClientCommandManager.argument("page", StringArgumentType.string())
                    .executes(context -> {
                        String arg = StringArgumentType.getString(context, "page");
                        return showShulkerList(context.getSource(), arg);
                    })
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
            if(arg.toLowerCase().startsWith("reset") || arg.toLowerCase().startsWith("prune") || arg.toLowerCase().startsWith("clear")) {
                if(arg.toLowerCase().endsWith("all")) {
                    tracker.resetShulkerBoxes(true);
                    source.sendFeedback(Text.literal("All shulker boxes have been reset.").formatted(Formatting.GREEN));
                } else {
                    tracker.resetShulkerBoxes(false);
                    source.sendFeedback(Text.literal("Default shulker boxes have been reset.").formatted(Formatting.GREEN));
                }
                return 1;
            }
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
                    .append(distanceInfo);


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
}
