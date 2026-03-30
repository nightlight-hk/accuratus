package org.nightcat.accuratus.mixin.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.GameOptions;
import net.minecraft.entity.Entity;
import net.minecraft.entity.boss.dragon.EnderDragonPart;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.util.Formatting;
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
import org.nightcat.accuratus.client.InterceptAimCalculator;
import org.nightcat.accuratus.client.PreciseTrajectoryCalculator;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {

    @Unique
    private static final int TRACK_HISTORY_SIZE = 20;

    @Unique
    private static final int TRACK_PREDICTION_STEP_TICKS = 1;

    @Unique
    private static final int TRACK_AIM_PRECALC_TICKS = 1;

    @Unique
    private static final double TRACKING_RANGE = 256.0;

    @Shadow
    public ClientPlayerEntity player;

    @Final
    @Shadow
    public GameOptions options;

    @Unique
    private boolean handledCurrentAttackClick;

    @Unique
    private boolean trackingActive;

    @Unique
    private int trackedEntityId = Integer.MIN_VALUE;

    @Unique
    private final double[] trackedX = new double[TRACK_HISTORY_SIZE];

    @Unique
    private final double[] trackedY = new double[TRACK_HISTORY_SIZE];

    @Unique
    private final double[] trackedZ = new double[TRACK_HISTORY_SIZE];

    @Unique
    private final double[] orderedTrackedX = new double[TRACK_HISTORY_SIZE];

    @Unique
    private final double[] orderedTrackedY = new double[TRACK_HISTORY_SIZE];

    @Unique
    private final double[] orderedTrackedZ = new double[TRACK_HISTORY_SIZE];

    @Unique
    private int trackedSampleCount;

    @Unique
    private int trackedWriteIndex;

    @Unique
    private boolean trackingReadyNotified;

    @Unique
    private int predictionTickCounter;

    @Unique
    private boolean delayedAimPending;

    @Unique
    private int delayedAimTicksRemaining;

    @Unique
    private float delayedAimYaw;

    @Unique
    private float delayedAimPitch;

    @Unique
    @Inject(method = "tick", at = @At("HEAD"))
    private void resetAutoAimClickLatch(CallbackInfo ci) {
        if (player == null) {
            handledCurrentAttackClick = false;
            return;
        }

        if (!options.attackKey.isPressed()) {
            handledCurrentAttackClick = false;
        }

        updateTrackingState();
    }

    @Unique
    @Inject(method = "doAttack", at = @At("HEAD"), cancellable = true)
    private void cancelAttackInFixedTargetMode(CallbackInfoReturnable<Boolean> cir) {
        if (shouldHandlePredictionModeActions()) {
            handlePredictionClick();
            cir.setReturnValue(false);
            return;
        }

        if (shouldHandleFixedTargetAimingActions()) {
            tryAutoAimAtCurrentClick();

            // Reserved for custom fixed-target behavior after aiming is applied.
            cir.setReturnValue(false);
        }
    }

    @Unique
    @Inject(method = "handleBlockBreaking", at = @At("HEAD"), cancellable = true)
    private void cancelBlockBreakingInFixedTargetMode(boolean breaking, CallbackInfo ci) {
        if (shouldHandlePredictionModeActions()) {
            ci.cancel();
            return;
        }

        if (shouldHandleFixedTargetAimingActions()) {
            if (breaking) {
                tryAutoAimAtCurrentClick();
            }

            // Reserved for custom fixed-target behavior after aiming is applied.
            ci.cancel();
        }
    }

    @Unique
    private boolean shouldHandlePredictionModeActions() {
        return AccuratusClient.isTrackTargetEnabled() && player != null && isHoldingBowOrCrossbow(player);
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

        String targetName = getTargetName(target);
        String coordinateText = formatCoordinates(targetPos);

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
            player.sendMessage(Text.literal(
                    "Fixed target: impossible to hit " + targetName + " at " + coordinateText + "."
            ).formatted(Formatting.RED), false);
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
                "Fixed target: %s at %s, ETA %.2f ticks (%.2f s).",
                targetName,
                coordinateText,
                ticks,
                seconds
        )), false);
    }

    @Unique
    private void tryAutoAimAtCurrentClick() {
        if (handledCurrentAttackClick) {
            return;
        }
        handledCurrentAttackClick = true;

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
        if (target instanceof EntityHitResult entityHit) {
            return entityHit.getPos();
        }
        return null;
    }

    @Unique
    private String getTargetName(HitResult target) {
        if (target instanceof BlockHitResult blockHit) {
            return player.getEntityWorld().getBlockState(blockHit.getBlockPos()).getBlock().getName().getString();
        }
        if (target instanceof EntityHitResult entityHit) {
            Entity entity = entityHit.getEntity();
            return entity.getName().getString();
        }
        return "target";
    }

    @Unique
    private static String formatCoordinates(Vec3d pos) {
        return String.format("(%.2f, %.2f, %.2f)", pos.x, pos.y, pos.z);
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

    @Unique
    private void handlePredictionClick() {
        if (handledCurrentAttackClick) {
            return;
        }
        handledCurrentAttackClick = true;

        if (trackingActive) {
            stopTracking("Track target: tracking stopped.");
            return;
        }

        HitResult target = findLongRangeLookTarget();
        if (target instanceof EntityHitResult entityHit) {
            startTracking(entityHit.getEntity());
            return;
        }

        player.sendMessage(Text.literal("Track target: left click an entity to start tracking."), false);
    }

    @Unique
    private void startTracking(Entity entity) {
        Entity trackEntity = resolveTrackEntity(entity);
        if (trackEntity == null) {
            player.sendMessage(Text.literal("Track target: failed to resolve target."), false);
            return;
        }

        trackingActive = true;
        trackedEntityId = trackEntity.getId();
        trackedSampleCount = 0;
        trackedWriteIndex = 0;
        trackingReadyNotified = false;
        predictionTickCounter = 0;
        delayedAimPending = false;
        delayedAimTicksRemaining = 0;

        Vec3d pos = getEntityHeadPosition(trackEntity);
        player.sendMessage(Text.literal(String.format(
                "Track target: tracking %s at %s.",
                trackEntity.getName().getString(),
                formatCoordinates(pos)
        )), false);
    }

    @Unique
    private void updateTrackingState() {
        if (!trackingActive) {
            return;
        }

        if (player == null) {
            trackingActive = false;
            return;
        }

        if (!AccuratusClient.isTrackTargetEnabled()) {
            stopTracking("Track target: prediction mode disabled.");
            return;
        }

        Entity trackedEntity = resolveTrackEntity(player.getEntityWorld().getEntityById(trackedEntityId));
        if (trackedEntity == null || !trackedEntity.isAlive()) {
            stopTracking("Track target: target is gone.");
            return;
        }

        // If the resolved target differs (e.g. multipart hit -> owner), keep id synced.
        trackedEntityId = trackedEntity.getId();

        if (player.squaredDistanceTo(trackedEntity) > TRACKING_RANGE * TRACKING_RANGE) {
            stopTracking("Track target: target out of range.");
            return;
        }

        Vec3d headPos = getEntityHeadPosition(trackedEntity);
        appendTrackingSample(headPos.x, headPos.y, headPos.z);

        if (trackedSampleCount < TRACK_HISTORY_SIZE) {
            return;
        }

        if (!trackingReadyNotified) {
            trackingReadyNotified = true;
            player.sendMessage(Text.literal("Track target: 20 ticks collected, predicting trajectory."), false);
        }

        if (delayedAimPending) {
            delayedAimTicksRemaining--;
            if (delayedAimTicksRemaining <= 0) {
                applyAim(delayedAimYaw, delayedAimPitch);
                delayedAimPending = false;
            }
        }

        predictionTickCounter++;
        if (predictionTickCounter < TRACK_PREDICTION_STEP_TICKS) {
            return;
        }
        predictionTickCounter = 0;

        double startY = player.getY() + (player.isSneaking() ? 1.17 : 1.52);
        InterceptAimCalculator.AimSolution solution = InterceptAimCalculator.findEarliestAimSolution(
                orderedTrackedX,
                orderedTrackedY,
                orderedTrackedZ,
                player.getX(),
                startY,
                player.getZ(),
                getInitialArrowSpeed(player),
                TRACK_AIM_PRECALC_TICKS
        );

        if (solution.found) {
            delayedAimYaw = (float) solution.yaw;
            delayedAimPitch = (float) solution.pitch;
            delayedAimTicksRemaining = TRACK_AIM_PRECALC_TICKS;
            delayedAimPending = true;
        }
    }

    @Unique
    private void appendTrackingSample(double x, double y, double z) {
        if (trackedSampleCount < TRACK_HISTORY_SIZE) {
            trackedX[trackedWriteIndex] = x;
            trackedY[trackedWriteIndex] = y;
            trackedZ[trackedWriteIndex] = z;
            trackedWriteIndex = (trackedWriteIndex + 1) % TRACK_HISTORY_SIZE;
            trackedSampleCount++;
            if (trackedSampleCount == TRACK_HISTORY_SIZE) {
                rebuildOrderedHistory();
            }
            return;
        }

        trackedX[trackedWriteIndex] = x;
        trackedY[trackedWriteIndex] = y;
        trackedZ[trackedWriteIndex] = z;
        trackedWriteIndex = (trackedWriteIndex + 1) % TRACK_HISTORY_SIZE;

        rebuildOrderedHistory();
    }

    @Unique
    private void rebuildOrderedHistory() {
        if (trackedSampleCount < TRACK_HISTORY_SIZE) {
            return;
        }

        for (int i = 0; i < TRACK_HISTORY_SIZE; i++) {
            int src = (trackedWriteIndex + i) % TRACK_HISTORY_SIZE;
            orderedTrackedX[i] = trackedX[src];
            orderedTrackedY[i] = trackedY[src];
            orderedTrackedZ[i] = trackedZ[src];
        }
    }

    @Unique
    private void applyAim(float yaw, float pitch) {
        player.setYaw(yaw);
        player.setPitch(pitch);
        player.setHeadYaw(yaw);
        player.setBodyYaw(yaw);
    }

    @Unique
    private static Vec3d getEntityHeadPosition(Entity entity) {
        return new Vec3d(entity.getX(), entity.getEyeY(), entity.getZ());
    }

    @Unique
    private static Entity resolveTrackEntity(Entity entity) {
        if (entity instanceof EnderDragonPart dragonPart && dragonPart.owner != null) {
            return dragonPart.owner;
        }
        return entity;
    }

    @Unique
    private void stopTracking(String reason) {
        trackingActive = false;
        trackedEntityId = Integer.MIN_VALUE;
        trackedSampleCount = 0;
        trackedWriteIndex = 0;
        trackingReadyNotified = false;
        predictionTickCounter = 0;
        delayedAimPending = false;
        delayedAimTicksRemaining = 0;
        if (player != null) {
            player.sendMessage(Text.literal(reason), false);
        }
    }
}

