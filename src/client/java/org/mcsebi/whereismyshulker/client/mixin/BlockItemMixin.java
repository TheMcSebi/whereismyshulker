package org.mcsebi.whereismyshulker.client.mixin;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.mcsebi.whereismyshulker.client.ShulkerBoxTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockItem.class)
public abstract class BlockItemMixin {

    @Unique
    private static final ThreadLocal<String> whereismyshulker$pendingCustomName = new ThreadLocal<>();

    @Inject(method = "place", at = @At("HEAD"))
    private void whereismyshulker$captureName(ItemPlacementContext context,
                                              CallbackInfoReturnable<ActionResult> cir) {
        World world = context.getWorld();
        if (!world.isClient()) return;

        // We only care about shulker boxes
        BlockItem self = (BlockItem) (Object) this;
        Block block = self.getBlock();
        if (!(block instanceof ShulkerBoxBlock)) return;

        String customName = "";

        // Capture BEFORE decrement happens
        if(context.getStack().getCustomName() != null) {
            customName = context.getStack().getCustomName().getString();
        }
        whereismyshulker$pendingCustomName.set(customName);
    }

    @Inject(method = "place", at = @At("RETURN"))
    private void whereismyshulker$onPlace(ItemPlacementContext context,
                                          CallbackInfoReturnable<ActionResult> cir) {
        try {
            if (!cir.getReturnValue().isAccepted()) return;

            World world = context.getWorld();
            if (!world.isClient()) return;

            // discard everything besides shulker boxes
            BlockItem self = (BlockItem) (Object) this;
            Block block = self.getBlock();
            if (!(block instanceof ShulkerBoxBlock)) return;

            BlockPos pos = context.getBlockPos();
            BlockState state = world.getBlockState(pos);
            if (!(state.getBlock() instanceof ShulkerBoxBlock)) return;

            String customName = whereismyshulker$pendingCustomName.get();

            ShulkerBoxTracker.getInstance()
                    .onShulkerBoxPlaced(pos, state.getBlock(), world, customName);

        } finally {
            // Always clear to avoid leaks / wrong names on later placements
            whereismyshulker$pendingCustomName.remove();
        }
    }


}