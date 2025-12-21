package org.mcsebi.whereismyshulker.client.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.mcsebi.whereismyshulker.client.ShulkerBoxTracker;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientPlayerInteractionManager.class)
public abstract class ClientPlayerInteractionManagerMixin {

    @Final
    @Shadow
    private MinecraftClient client;

    @Unique
    private static final ThreadLocal<Boolean> whereismyshulker$breakingShulker =
            ThreadLocal.withInitial(() -> false);

    @Inject(method = "breakBlock", at = @At("HEAD"))
    private void whereismyshulker$capture(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if(client.world != null) {
            BlockState state = client.world.getBlockState(pos);
            whereismyshulker$breakingShulker.set(state.getBlock() instanceof ShulkerBoxBlock);
        } else {
            whereismyshulker$breakingShulker.set(false);
        }
    }

    @Inject(method = "breakBlock", at = @At("RETURN"))
    private void whereismyshulker$after(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        try {
            if (!cir.getReturnValue()) return;

            if (Boolean.TRUE.equals(whereismyshulker$breakingShulker.get())) {
                ShulkerBoxTracker.getInstance().onShulkerBoxBroken(pos);
            }
        } finally {
            whereismyshulker$breakingShulker.remove();
        }
    }
}
