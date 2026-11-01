package de.kuro.worldguard.testmod;

import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;
import de.kuro.worldguard.api.WorldGuardNeoForge;
import net.minecraft.core.BlockPos;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.server.level.ServerLevel;

/** DQM status spell must not apply Slowness across a PvP-deny boundary. */
final class DqmBomiePvpTest {
    private static final String REGION_ID = "worldguard_test_dqm_bomie_pvp";
    private static final BlockPos CASTER_POS = new BlockPos(4097, 250, 4110);
    private static final BlockPos CONTROL_POS = new BlockPos(4099, 250, 4110);
    private static final BlockPos PROTECTED_POS = new BlockPos(4107, 250, 4110);

    private DqmBomiePvpTest() { }

    static void run(ServerLevel level) {
        RegionManager manager = WorldGuard.getInstance().getPlatform().getRegionContainer()
                .get(WorldGuardNeoForge.world(level));
        if (manager == null || manager.hasRegion(REGION_ID)) {
            throw new AssertionError("DQM Bomie fixture region manager unavailable or ID already exists");
        }
        DqmTestPlayer caster = DqmTestPlayer.create(level, "wg_bomie_caster",
                "470e59d5-9f50-4161-900a-280956628a68", CASTER_POS);
        DqmTestPlayer control = DqmTestPlayer.create(level, "wg_bomie_control",
                "ba591286-366f-40dc-b8bb-9b626d3f68e6", CONTROL_POS);
        DqmTestPlayer protectedTarget = DqmTestPlayer.create(level, "wg_bomie_denied",
                "15c67750-6432-4339-b0d3-e9fe60031e80", PROTECTED_POS);
        for (DqmTestPlayer actor : new DqmTestPlayer[] {caster, control, protectedTarget}) {
            var local = WorldGuardNeoForge.player(actor);
            if (WorldGuard.getInstance().getPlatform().getSessionManager()
                    .hasBypass(local, local.getWorld())) {
                throw new AssertionError("DQM Bomie fixture player unexpectedly bypasses regions");
            }
            actor.setHealth(20.0f);
            actor.removeEffect(MobEffects.SLOWNESS);
        }

        DqmSpellImpact.apply(level, caster, control, "bomie", 0);
        MobEffectInstance controlEffect = control.getEffect(MobEffects.SLOWNESS);
        System.out.println("DQM_BOMIE_CONTROL slowness=" + describe(controlEffect)
                + " health=" + control.getHealth());
        if (controlEffect == null || controlEffect.getAmplifier() != 1) {
            System.out.println("DQM_BOMIE_INCONCLUSIVE no_unprotected_slowness");
            throw new AssertionError("DQM_BOMIE_INCONCLUSIVE: unprotected spell effect was not confirmed");
        }

        ProtectedCuboidRegion claim = new ProtectedCuboidRegion(REGION_ID,
                BlockVector3.at(PROTECTED_POS.getX(), PROTECTED_POS.getY() - 1, PROTECTED_POS.getZ()),
                BlockVector3.at(PROTECTED_POS.getX(), PROTECTED_POS.getY() + 1, PROTECTED_POS.getZ()));
        claim.setFlag(Flags.PVP, StateFlag.State.DENY);
        manager.addRegion(claim);
        try {
            DqmSpellImpact.apply(level, caster, protectedTarget, "bomie", 0);
            MobEffectInstance deniedEffect = protectedTarget.getEffect(MobEffects.SLOWNESS);
            System.out.println("DQM_BOMIE_PROTECTED slowness=" + describe(deniedEffect)
                    + " health=" + protectedTarget.getHealth());
            if (deniedEffect != null) {
                throw new AssertionError("DQM_BOMIE_PROTECTION_FAILED: PvP-denied target received Slowness");
            }
            System.out.println("DQM_BOMIE_PROTECTION_PASSED");
        } finally {
            manager.removeRegion(REGION_ID);
        }
    }

    private static String describe(MobEffectInstance effect) {
        return effect == null ? "none"
                : "amplifier=" + effect.getAmplifier() + ",duration=" + effect.getDuration();
    }
}
