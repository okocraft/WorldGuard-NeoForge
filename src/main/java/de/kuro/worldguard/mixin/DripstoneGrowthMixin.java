package de.kuro.worldguard.mixin;

import com.sk89q.worldguard.protection.flags.Flags;
import de.kuro.worldguard.protection.EnvironmentalHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.SpeleothemBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SpeleothemThickness;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SpeleothemBlock.class)
public abstract class DripstoneGrowthMixin {
    @Inject(method = "createSpeleothem", at = @At("HEAD"), cancellable = true)
    private void worldguard$rock(LevelAccessor level, BlockPos pos, Direction direction,
                                 SpeleothemThickness thickness, CallbackInfo ci) {
        var service = EnvironmentalHooks.service();
        if (level instanceof ServerLevel server && service != null && service.active()
                && !EnvironmentalHooks.natural(server, pos, Flags.ROCK_GROWTH,
                service.config(server).disableRockGrowth)) ci.cancel();
    }

    @Inject(method = "createMergedTips", at = @At("HEAD"), cancellable = true)
    private void worldguard$merge(BlockState state, LevelAccessor level, BlockPos pos, CallbackInfo ci) {
        var service = EnvironmentalHooks.service();
        if (!(level instanceof ServerLevel server) || service == null || !service.active()) return;
        BlockPos other = state.getValue(SpeleothemBlock.TIP_DIRECTION) == Direction.UP
                ? pos.above() : pos.below();
        boolean disabled = service.config(server).disableRockGrowth;
        if (!EnvironmentalHooks.natural(server, pos, Flags.ROCK_GROWTH, disabled)
                || !EnvironmentalHooks.natural(server, other, Flags.ROCK_GROWTH, disabled)) ci.cancel();
    }
}
