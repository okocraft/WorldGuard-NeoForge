package de.kuro.worldguard.testmod;

import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;
import de.kuro.worldguard.api.WorldGuardNeoForge;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.util.FakePlayer;

import java.util.ArrayList;
import java.util.List;

/** Real MODE_FLUID range action against unprotected and region-protected water. */
final class DqmRangeFluidTest {
    private static final String REGION_ID = "worldguard_test_dqm_range_fluid";
    private static final BlockPos CONTROL = new BlockPos(4099, 250, 4114);
    private static final BlockPos PROTECTED = new BlockPos(4107, 250, 4114);

    private DqmRangeFluidTest() { }

    static void run(ServerLevel level) {
        Item tool = DqmTerrainAction.item("dqmvii:legacy_item_kc_magic_tool");
        FakePlayer player = DqmTerrainAction.player(level,
                "7e1ea76f-b0d1-4e2e-9ae0-783925925110", "wg_dqm_fluid");
        RegionManager manager = WorldGuard.getInstance().getPlatform().getRegionContainer()
                .get(WorldGuardNeoForge.world(level));
        if (manager == null || manager.hasRegion(REGION_ID)) {
            throw new AssertionError("DQM fluid fixture region manager unavailable or ID already exists");
        }
        List<BlockPos> controlCells = cells(CONTROL);
        List<BlockPos> protectedCells = cells(PROTECTED);
        for (BlockPos pos : controlCells) requireEmpty(level, pos);
        for (BlockPos pos : protectedCells) requireEmpty(level, pos);
        ProtectedCuboidRegion claim = new ProtectedCuboidRegion(REGION_ID,
                BlockVector3.at(PROTECTED.getX() + 1, PROTECTED.getY(), PROTECTED.getZ()),
                BlockVector3.at(PROTECTED.getX() + 1, PROTECTED.getY() + 2, PROTECTED.getZ() + 2));
        boolean added = false;
        try {
            fillWater(level, controlCells);
            fillWater(level, protectedCells);
            var controlStack = new ItemStack(tool);
            DqmTerrainAction.rangeMode(controlStack, 1);
            var controlResult = DqmTerrainAction.use(player, controlStack, CONTROL);
            boolean controlEastDrained = level.getFluidState(CONTROL.east()).isEmpty();
            System.out.println("DQM_FLUID_CONTROL result=" + controlResult
                    + " eastDrained=" + controlEastDrained);
            if (!controlEastDrained) {
                throw new AssertionError("DQM_FLUID_INCONCLUSIVE: unprotected MODE_FLUID did not drain water");
            }

            manager.addRegion(claim);
            added = true;
            var protectedStack = new ItemStack(tool);
            DqmTerrainAction.rangeMode(protectedStack, 1);
            var protectedResult = DqmTerrainAction.use(player, protectedStack, PROTECTED);
            boolean protectedEastDrained = level.getFluidState(PROTECTED.east()).isEmpty();
            System.out.println("DQM_FLUID_PROTECTED result=" + protectedResult
                    + " eastDrained=" + protectedEastDrained);
            if (protectedEastDrained) {
                throw new AssertionError("DQM_FLUID_PROTECTION_FAILED: MODE_FLUID drained protected water");
            }
            System.out.println("DQM_FLUID_PROTECTION_PASSED");
        } finally {
            if (added) manager.removeRegion(REGION_ID);
            clear(level, controlCells);
            clear(level, protectedCells);
        }
    }

    private static List<BlockPos> cells(BlockPos origin) {
        List<BlockPos> positions = new ArrayList<>(27);
        for (int sideways = -1; sideways <= 1; sideways++) {
            for (int up = 0; up < 3; up++) {
                for (int forward = 0; forward < 3; forward++) {
                    positions.add(origin.offset(sideways, up, forward));
                }
            }
        }
        return positions;
    }

    private static void requireEmpty(ServerLevel level, BlockPos pos) {
        if (!level.getBlockState(pos).isAir()) {
            throw new AssertionError("DQM fluid fixture is not empty at " + pos);
        }
    }

    private static void fillWater(ServerLevel level, List<BlockPos> positions) {
        for (BlockPos pos : positions) level.setBlockAndUpdate(pos, Blocks.WATER.defaultBlockState());
    }

    private static void clear(ServerLevel level, List<BlockPos> positions) {
        for (BlockPos pos : positions) level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
    }
}
