package de.kuro.worldguard.mixin;

import de.kuro.worldguard.protection.SessionMovement;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.level.portal.TeleportTransition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayer.class)
public abstract class PlayerDimensionMovementMixin {
    @Inject(method = "teleport(Lnet/minecraft/world/level/portal/TeleportTransition;)Lnet/minecraft/server/level/ServerPlayer;",
            at = @At("HEAD"), cancellable = true)
    private void worldguard$beforeDimensionChange(TeleportTransition transition,
                                                   CallbackInfoReturnable<ServerPlayer> result) {
        ServerPlayer player = (ServerPlayer) (Object) this;
        PositionMoveRotation destination = PositionMoveRotation.calculateAbsolute(
                PositionMoveRotation.of(player), PositionMoveRotation.of(transition), transition.relatives());
        SessionMovement movement = SessionMovement.installed();
        if (movement != null && !movement.dimensionTeleport(player, transition.newLevel(),
                destination.position().x, destination.position().y, destination.position().z,
                destination.yRot(), destination.xRot())) {
            result.setReturnValue(player);
        }
    }
}
