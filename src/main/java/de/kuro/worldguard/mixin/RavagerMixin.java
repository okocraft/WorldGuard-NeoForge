package de.kuro.worldguard.mixin;

import com.sk89q.worldguard.protection.flags.Flags;
import de.kuro.worldguard.protection.EnvironmentalHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Ravager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Ravager.class)
public abstract class RavagerMixin {
    @Redirect(method = "aiStep", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;destroyBlock(Lnet/minecraft/core/BlockPos;ZLnet/minecraft/world/entity/Entity;)Z"))
    private boolean worldguard$ravage(ServerLevel level, BlockPos pos, boolean drop, Entity source) {
        if (!EnvironmentalHooks.mobBlock(level,
                (Ravager) (Object) this, pos, Flags.RAVAGER_RAVAGE, false)) return false;
        return level.destroyBlock(pos, drop, source);
    }
}
