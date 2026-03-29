package org.nightcat.accuratus.mixin.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.hit.HitResult.Type;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import org.nightcat.accuratus.client.AccuratusClient;
import org.nightcat.accuratus.client.PreciseTrajectoryCalculator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {

    @Shadow
    public ClientPlayerEntity player;

    @Unique
    private int lastAutoAimTick = Integer.MIN_VALUE;

    @Unique
    @Inject(method = "doAttack", at = @At("HEAD"), cancellable = true)
    private void cancelAttackInFixedTargetMode(CallbackInfoReturnable<Boolean> cir) {
        if (shouldHandleFixedTargetAimingActions()) {
            tryAutoAimAtCurrentLookTarget();

            // Reserved for custom fixed-target behavior after aiming is applied.
            cir.setReturnValue(false);
        }
    }

    @Unique
    @Inject(method = "handleBlockBreaking", at = @At("HEAD"), cancellable = true)
    private void cancelBlockBreakingInFixedTargetMode(boolean breaking, CallbackInfo ci) {
        if (shouldHandleFixedTargetAimingActions()) {
            if (breaking) {
                tryAutoAimAtCurrentLookTarget();
            }

            // Reserved for custom fixed-target behavior after aiming is applied.
            ci.cancel();
        }
    }

    @Unique
    private boolean shouldHandleFixedTargetAimingActions() {
        return AccuratusClient.isFixedTargetEnabled() && player != null && isHoldingBowOrCrossbow(player);
    }

    @Unique
    private static boolean isHoldingBowOrCrossbow(ClientPlayerEntity player) {
        ItemStack mainHand = player.getMainHandStack();
        ItemStack offHand = player.getOffHandStack();
        return mainHand.isOf(Items.BOW)
                || mainHand.isOf(Items.CROSSBOW)
                || offHand.isOf(Items.BOW)
                || offHand.isOf(Items.CROSSBOW);
    }

    @Unique
    private void aimAtTarget(HitResult target) {
        Vec3d targetPos = getTargetPosition(target);
        if (targetPos == null) {
            return;
        }

        double startY = player.getY() + (player.isSneaking() ? 1.17 : 1.52);
        double speed = getInitialArrowSpeed(player);
        double[] result = PreciseTrajectoryCalculator.calculate(
                player.getX(),
                startY,
                player.getZ(),
                targetPos.x,
                targetPos.y,
                targetPos.z,
                speed
        );

        if (result[2] < 0) {
            player.sendMessage(Text.literal("Fixed target: impossible to hit this target."), false);
            return;
        }

        float yaw = (float) result[0];
        float pitch = (float) result[1];
        player.setYaw(yaw);
        player.setPitch(pitch);
        player.setHeadYaw(yaw);
        player.setBodyYaw(yaw);

        double ticks = result[2];
        double seconds = ticks / 20.0;
        player.sendMessage(Text.literal(String.format(
                "Fixed target: ETA %.2f ticks (%.2f s).",
                ticks,
                seconds
        )), false);
    }

    @Unique
    private void tryAutoAimAtCurrentLookTarget() {
        if (player.age == lastAutoAimTick) {
            return;
        }
        lastAutoAimTick = player.age;

        HitResult target = findLongRangeLookTarget();
        if (target.getType() == Type.MISS) {
            player.sendMessage(Text.literal("Fixed target: no target in sight."), false);
            return;
        }

        aimAtTarget(target);
    }

    @Unique
    private HitResult findLongRangeLookTarget() {
        final double range = 256.0;
        Vec3d start = player.getCameraPosVec(1.0F);
        Vec3d direction = player.getRotationVec(1.0F);
        Vec3d end = start.add(direction.multiply(range));

        BlockHitResult blockHit = player.getEntityWorld().raycast(new RaycastContext(
                start,
                end,
                RaycastContext.ShapeType.OUTLINE,
                RaycastContext.FluidHandling.NONE,
                player
        ));

        double maxDistanceSq = range * range;
        if (blockHit.getType() != Type.MISS) {
            maxDistanceSq = start.squaredDistanceTo(blockHit.getPos());
        }

        Box searchBox = player.getBoundingBox().stretch(direction.multiply(range)).expand(1.0);
        EntityHitResult entityHit = ProjectileUtil.raycast(
                player,
                start,
                end,
                searchBox,
                entity -> !entity.isSpectator() && entity.canHit(),
                maxDistanceSq
        );

        if (entityHit != null) {
            return entityHit;
        }
        return blockHit;
    }

    @Unique
    private static Vec3d getTargetPosition(HitResult target) {
        if (target instanceof BlockHitResult blockHit) {
            return blockHit.getPos();
        }
        if (target.getType() == HitResult.Type.ENTITY) {
            Entity entity = ((EntityHitResult) target).getEntity();
            return entity.getBoundingBox().getCenter();
        }
        return null;
    }

    @Unique
    private static double getInitialArrowSpeed(ClientPlayerEntity player) {
        ItemStack mainHand = player.getMainHandStack();
        ItemStack offHand = player.getOffHandStack();
        if (mainHand.isOf(Items.CROSSBOW) || offHand.isOf(Items.CROSSBOW)) {
            return 3.15;
        }
        return 3.0;
    }
}

