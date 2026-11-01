package de.kuro.worldguard.testmod;

import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;
import de.kuro.worldguard.api.WorldGuardNeoForge;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

/** A beneficial DQM cure remains usable inside a PvP-deny region. */
final class DqmKiariPvpTest {
    private static final String REGION_ID = "worldguard_test_dqm_kiari_pvp";
    private static final BlockPos CASTER_POS = new BlockPos(4097, 250, 4112);
    private static final BlockPos CONTROL_POS = new BlockPos(4099, 250, 4112);
    private static final BlockPos PROTECTED_POS = new BlockPos(4107, 250, 4112);

    private DqmKiariPvpTest() { }

    static void run(ServerLevel level) {
        RegionManager manager = WorldGuard.getInstance().getPlatform().getRegionContainer()
                .get(WorldGuardNeoForge.world(level));
        if (manager == null || manager.hasRegion(REGION_ID)) {
            throw new AssertionError("DQM Kiari fixture region manager unavailable or ID already exists");
        }
        DqmTestPlayer caster = DqmTestPlayer.create(level, "wg_kiari_caster",
                "a4444d12-9c23-4797-811b-36b01afc60e0", CASTER_POS);
        DqmTestPlayer control = DqmTestPlayer.create(level, "wg_kiari_control",
                "92a66c4e-19ca-4d4a-b8d1-df8bb3105dd4", CONTROL_POS);
        DqmTestPlayer protectedTarget = DqmTestPlayer.create(level, "wg_kiari_denied",
                "655036d4-fb4f-4e9a-bbce-1d2eaef201ad", PROTECTED_POS);
        for (DqmTestPlayer actor : new DqmTestPlayer[] {caster, control, protectedTarget}) {
            var local = WorldGuardNeoForge.player(actor);
            if (WorldGuard.getInstance().getPlatform().getSessionManager()
                    .hasBypass(local, local.getWorld())) {
                throw new AssertionError("DQM Kiari fixture player unexpectedly bypasses regions");
            }
        }

        control.addEffect(new MobEffectInstance(MobEffects.POISON, 400, 0));
        protectedTarget.addEffect(new MobEffectInstance(MobEffects.POISON, 400, 0));
        if (!control.hasEffect(MobEffects.POISON) || !protectedTarget.hasEffect(MobEffects.POISON)) {
            throw new AssertionError("DQM_KIARI_INCONCLUSIVE: poison fixture was not established");
        }
        DqmSpellImpact.apply(level, caster, control, "kiari", 0);
        System.out.println("DQM_KIARI_CONTROL poison=" + control.hasEffect(MobEffects.POISON));
        if (control.hasEffect(MobEffects.POISON)) {
            throw new AssertionError("DQM_KIARI_INCONCLUSIVE: unprotected Kiari did not cure poison");
        }

        ProtectedCuboidRegion claim = new ProtectedCuboidRegion(REGION_ID,
                BlockVector3.at(PROTECTED_POS.getX(), PROTECTED_POS.getY() - 1, PROTECTED_POS.getZ()),
                BlockVector3.at(PROTECTED_POS.getX(), PROTECTED_POS.getY() + 1, PROTECTED_POS.getZ()));
        claim.setFlag(Flags.PVP, StateFlag.State.DENY);
        manager.addRegion(claim);
        try {
            DqmSpellImpact.apply(level, caster, protectedTarget, "kiari", 0);
            boolean stillPoisoned = protectedTarget.hasEffect(MobEffects.POISON);
            System.out.println("DQM_KIARI_PROTECTED poison=" + stillPoisoned);
            if (stillPoisoned) {
                throw new AssertionError("DQM_KIARI_PROTECTION_FAILED: PvP-denied target could not receive a cure");
            }
            System.out.println("DQM_KIARI_PROTECTION_PASSED");
        } finally {
            manager.removeRegion(REGION_ID);
        }
    }
}
