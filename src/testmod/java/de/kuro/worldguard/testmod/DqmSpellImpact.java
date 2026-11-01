package de.kuro.worldguard.testmod;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Optional;

/** Calls DQM's real projectile-impact entry point without a DQM compile dependency. */
final class DqmSpellImpact {
    private static final DqmSpellImpact INSTANCE = load();

    private final Constructor<?> projectile;
    private final Method spell;
    private final Method apply;

    private DqmSpellImpact(Constructor<?> projectile, Method spell, Method apply) {
        this.projectile = projectile;
        this.spell = spell;
        this.apply = apply;
    }

    static void apply(ServerLevel level, LivingEntity caster, LivingEntity target,
                      String spellId, int fallbackDamage) {
        try {
            Object spec = ((Optional<?>) INSTANCE.spell.invoke(null, spellId)).orElseThrow(() ->
                    new AssertionError("DQM spell is absent: " + spellId));
            Object instance = INSTANCE.projectile.newInstance(level, caster,
                    new ItemStack(Items.BLAZE_ROD), spellId, fallbackDamage);
            INSTANCE.apply.invoke(null, level, instance, spec, target);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("DQM spell impact invocation failed: " + spellId, failure);
        }
    }

    private static DqmSpellImpact load() {
        try {
            Class<?> projectileType = Class.forName("com.dqm.entity.DqmvMagicProjectile");
            Class<?> spellsType = Class.forName("com.dqm.DqmvMagicSpells");
            Class<?> specType = Class.forName("com.dqm.DqmvMagicSpells$SpellSpec");
            Constructor<?> projectile = projectileType.getConstructor(Level.class, LivingEntity.class,
                    ItemStack.class, String.class, int.class);
            Method spell = spellsType.getMethod("spell", String.class);
            Method apply = spellsType.getMethod("applyProjectileImpact", ServerLevel.class,
                    projectileType, specType, LivingEntity.class);
            return new DqmSpellImpact(projectile, spell, apply);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Cannot reach DQM projectile-impact entry point", failure);
        }
    }
}
