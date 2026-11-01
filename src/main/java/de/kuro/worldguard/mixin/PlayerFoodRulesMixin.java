package de.kuro.worldguard.mixin;

import de.kuro.worldguard.protection.SessionPlayerFlagsListener;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.food.FoodData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FoodData.class)
public abstract class PlayerFoodRulesMixin {
    @Shadow private int foodLevel;
    @Shadow private float saturationLevel;
    @Shadow private float exhaustionLevel;
    @Unique private boolean worldguard$preserveFood;
    @Unique private int worldguard$foodBefore;
    @Unique private float worldguard$saturationBefore;
    @Unique private float worldguard$exhaustionBefore;

    @Inject(method = "tick", at = @At("HEAD"))
    private void worldguard$beforeFoodTick(ServerPlayer player, CallbackInfo ci) {
        worldguard$preserveFood = !SessionPlayerFlagsListener.naturalHunger(player);
        if (worldguard$preserveFood) {
            worldguard$foodBefore = foodLevel;
            worldguard$saturationBefore = saturationLevel;
            worldguard$exhaustionBefore = exhaustionLevel;
        }
    }

    @Redirect(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerPlayer;heal(F)V"))
    private void worldguard$naturalRegeneration(ServerPlayer player, float amount) {
        if (SessionPlayerFlagsListener.naturalHealth(player)) player.heal(amount);
    }

    @Redirect(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/food/FoodData;addExhaustion(F)V"))
    private void worldguard$naturalRegenExhaustion(FoodData food, float amount, ServerPlayer player) {
        if (SessionPlayerFlagsListener.naturalHealth(player)) food.addExhaustion(amount);
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void worldguard$afterFoodTick(ServerPlayer player, CallbackInfo ci) {
        if (worldguard$preserveFood) {
            foodLevel = Math.max(foodLevel, worldguard$foodBefore);
            saturationLevel = Math.max(saturationLevel, worldguard$saturationBefore);
            exhaustionLevel = worldguard$exhaustionBefore;
            worldguard$preserveFood = false;
        }
    }
}
