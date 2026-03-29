package org.nightcat.accuratus.mixin.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import org.nightcat.accuratus.client.AccuratusClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {

    @Shadow
    public ClientPlayerEntity player;

    @Inject(method = "doAttack", at = @At("HEAD"), cancellable = true)
    private void accuratus$cancelAttackInFixedTargetMode(CallbackInfoReturnable<Boolean> cir) {
        if (!accuratus$shouldIgnoreAimingActions()) {
            return;
        }

        // Reserved for custom fixed-target behavior.
        cir.setReturnValue(false);
    }

    @Inject(method = "handleBlockBreaking", at = @At("HEAD"), cancellable = true)
    private void accuratus$cancelBlockBreakingInFixedTargetMode(boolean breaking, CallbackInfo ci) {
        if (!accuratus$shouldIgnoreAimingActions()) {
            return;
        }

        // Reserved for custom fixed-target behavior.
        ci.cancel();
    }

    private boolean accuratus$shouldIgnoreAimingActions() {
        return AccuratusClient.isFixedTargetEnabled() && player != null && isHoldingBowOrCrossbow(player);
    }

    private static boolean isHoldingBowOrCrossbow(ClientPlayerEntity player) {
        ItemStack mainHand = player.getMainHandStack();
        ItemStack offHand = player.getOffHandStack();
        return mainHand.isOf(Items.BOW)
                || mainHand.isOf(Items.CROSSBOW)
                || offHand.isOf(Items.BOW)
                || offHand.isOf(Items.CROSSBOW);
    }
}

