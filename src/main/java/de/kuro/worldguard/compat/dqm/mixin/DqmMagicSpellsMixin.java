package de.kuro.worldguard.compat.dqm.mixin;

import de.kuro.worldguard.protection.EnvironmentalHooks;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.dqm.DqmvMagicSpells", remap = false)
public abstract class DqmMagicSpellsMixin {
    @Inject(method = "applyToTarget(Lnet/minecraft/server/level/ServerLevel;Lcom/dqm/entity/DqmvMagicProjectile;Lcom/dqm/DqmvMagicSpells$SpellSpec;Lnet/minecraft/world/entity/LivingEntity;Lcom/dqm/entity/DqmvMonsterEntity;)V",
            at = @At("HEAD"), cancellable = true, remap = false)
    private static void worldguard$status(ServerLevel level, @Coerce Projectile projectile,
                                          @Coerce Object spell, LivingEntity target,
                                          @Coerce Object reflectedBoss, CallbackInfo ci) {
        String id = ((DqmSpellSpecAccessor) spell).worldguard$spellId();
        if (!worldguard$harmfulStatus(id)) return;
        Entity caster = projectile.getOwner();
        if (caster != null && !EnvironmentalHooks.offensiveEffect(caster, target)) ci.cancel();
    }

    @Inject(method = "applyAfterEffect(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/LivingEntity;Lcom/dqm/DqmvMagicSpells$SpellSpec;Lnet/minecraft/world/entity/LivingEntity;)V",
            at = @At("HEAD"), cancellable = true, remap = false)
    private static void worldguard$after(ServerLevel level, LivingEntity target,
                                         @Coerce Object spell, LivingEntity caster, CallbackInfo ci) {
        if (caster != null && !EnvironmentalHooks.offensiveEffect(caster, target)) ci.cancel();
    }

    private static boolean worldguard$harmfulStatus(String id) {
        return switch (id) {
            case "bomie", "rariho", "manusa", "mahoton", "rukani", "medapani", "henatosu",
                 "divainsuperu", "mahotora" -> true;
            default -> false;
        };
    }
}
