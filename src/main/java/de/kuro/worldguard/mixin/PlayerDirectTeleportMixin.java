package de.kuro.worldguard.mixin;

import de.kuro.worldguard.protection.SessionMovement;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.phys.Vec3;
import java.util.Set;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayer.class)
public abstract class PlayerDirectTeleportMixin {
    @Inject(method = "teleportTo(Lnet/minecraft/server/level/ServerLevel;DDDLjava/util/Set;FFZ)Z",
            at = @At("HEAD"), cancellable = true)
    private void worldguard$beforeDirectTeleport(ServerLevel level, double x, double y, double z,
                                                  Set<Relative> relatives, float yaw, float pitch,
                                                  boolean resetCamera, CallbackInfoReturnable<Boolean> cir) {
        ServerPlayer player = (ServerPlayer) (Object) this;
        PositionMoveRotation destination = PositionMoveRotation.calculateAbsolute(
                PositionMoveRotation.of(player), new PositionMoveRotation(new Vec3(x, y, z),
                        Vec3.ZERO, yaw, pitch), relatives);
        SessionMovement movement = SessionMovement.installed();
        if (movement != null && !movement.dimensionTeleport(player, level,
                destination.position().x, destination.position().y, destination.position().z,
                destination.yRot(), destination.xRot())) cir.setReturnValue(false);
    }
}
