package de.kuro.worldguard.compat.dqm.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;

@Pseudo
@Mixin(targets = "com.dqm.DqmvMagicSpells$SpellSpec", remap = false)
public interface DqmSpellSpecAccessor {
    @Accessor(value = "id", remap = false)
    String worldguard$spellId();
}
