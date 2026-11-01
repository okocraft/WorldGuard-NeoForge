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

/** Direct DQM impact plus vanilla fire ticks, with and without a PvP-deny region. */
final class DqmMeraPvpTest {
    private static final String REGION_ID = "worldguard_test_dqm_mera_pvp";
    private static final BlockPos ATTACKER_POS = new BlockPos(4097, 250, 4108);
    private static final BlockPos CONTROL_POS = new BlockPos(4099, 250, 4108);
    private static final BlockPos PROTECTED_POS = new BlockPos(4107, 250, 4108);

    private final RegionManager regions;
    private final DqmTestPlayer control;
    private final DqmTestPlayer protectedTarget;
    private final int protectedFireAfterImpact;
    private int elapsedTicks;

    private DqmMeraPvpTest(RegionManager regions, DqmTestPlayer control,
                           DqmTestPlayer protectedTarget, int protectedFireAfterImpact) {
        this.regions = regions;
        this.control = control;
        this.protectedTarget = protectedTarget;
        this.protectedFireAfterImpact = protectedFireAfterImpact;
    }

    static DqmMeraPvpTest start(ServerLevel level) {
        RegionManager regions = WorldGuard.getInstance().getPlatform().getRegionContainer()
                .get(WorldGuardNeoForge.world(level));
        if (regions == null || regions.hasRegion(REGION_ID)) {
            throw new AssertionError("DQM Mera fixture region manager unavailable or ID already exists");
        }
        DqmTestPlayer caster = DqmTestPlayer.create(level, "wg_mera_caster",
                "c27bd88a-16eb-4358-aa58-7e67cab4b3f1", ATTACKER_POS);
        DqmTestPlayer control = DqmTestPlayer.create(level, "wg_mera_control",
                "f42b0635-8e6b-4df0-8719-cdb15ed2bc71", CONTROL_POS);
        DqmTestPlayer protectedTarget = DqmTestPlayer.create(level, "wg_mera_denied",
                "6ca34845-7254-4602-bc31-8134b584d014", PROTECTED_POS);
        for (DqmTestPlayer actor : new DqmTestPlayer[] {caster, control, protectedTarget}) {
            var local = WorldGuardNeoForge.player(actor);
            if (WorldGuard.getInstance().getPlatform().getSessionManager()
                    .hasBypass(local, local.getWorld())) {
                throw new AssertionError("DQM Mera fixture player unexpectedly bypasses regions");
            }
            actor.setHealth(20.0f);
            actor.setRemainingFireTicks(0);
        }

        DqmSpellImpact.apply(level, caster, control, "mera", 2);
        float controlHealth = control.getHealth();
        int controlFire = control.getRemainingFireTicks();
        System.out.println("DQM_MERA_CONTROL health=" + controlHealth + " fireTicks=" + controlFire);
        if (controlFire <= 0) {
            System.out.println("DQM_MERA_INCONCLUSIVE no_unprotected_ignition");
            throw new AssertionError("DQM_MERA_INCONCLUSIVE: unprotected Mera ignition was not confirmed");
        }

        ProtectedCuboidRegion claim = new ProtectedCuboidRegion(REGION_ID,
                BlockVector3.at(PROTECTED_POS.getX(), PROTECTED_POS.getY() - 1, PROTECTED_POS.getZ()),
                BlockVector3.at(PROTECTED_POS.getX(), PROTECTED_POS.getY() + 1, PROTECTED_POS.getZ()));
        claim.setFlag(Flags.PVP, StateFlag.State.DENY);
        regions.addRegion(claim);
        try {
            DqmSpellImpact.apply(level, caster, protectedTarget, "mera", 2);
            float deniedHealth = protectedTarget.getHealth();
            int deniedFire = protectedTarget.getRemainingFireTicks();
            System.out.println("DQM_MERA_PROTECTED_IMPACT health=" + deniedHealth + " fireTicks=" + deniedFire);
            return new DqmMeraPvpTest(regions, control, protectedTarget, deniedFire);
        } catch (RuntimeException | Error failure) {
            regions.removeRegion(REGION_ID);
            throw failure;
        }
    }

    /** Advance the two non-world-ticking fake players once per real server tick. */
    boolean tick() {
        try {
            control.baseTick();
            protectedTarget.baseTick();
            if (++elapsedTicks < 25) return false;
            float controlLater = control.getHealth();
            float protectedLater = protectedTarget.getHealth();
            int protectedFireLater = protectedTarget.getRemainingFireTicks();
            System.out.println("DQM_MERA_AFTER_25_TICKS controlHealth=" + controlLater
                    + " protectedHealth=" + protectedLater + " protectedFireTicks=" + protectedFireLater);
            if (control.getRemainingFireTicks() <= 0) {
                throw new AssertionError("DQM_MERA_INCONCLUSIVE: control ignition did not persist across server ticks");
            }
            if (protectedFireAfterImpact > 0 || protectedFireLater > 0) {
                throw new AssertionError("DQM_MERA_PROTECTION_FAILED: PvP-denied target ignited"
                        + " (fireAfterImpact=" + protectedFireAfterImpact
                        + ", fireAfter25Ticks=" + protectedFireLater + ")");
            }
            System.out.println("DQM_MERA_PROTECTION_PASSED");
            return true;
        } catch (RuntimeException | Error failure) {
            if (elapsedTicks < 25) regions.removeRegion(REGION_ID);
            throw failure;
        } finally {
            if (elapsedTicks >= 25) regions.removeRegion(REGION_ID);
        }
    }

}
