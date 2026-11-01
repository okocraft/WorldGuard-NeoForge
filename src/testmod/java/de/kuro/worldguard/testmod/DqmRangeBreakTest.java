package de.kuro.worldguard.testmod;

import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;
import de.kuro.worldguard.api.WorldGuardNeoForge;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.util.FakePlayer;

import java.util.ArrayList;
import java.util.List;

/** Real DQM magic-tool action against paired unprotected and region-protected fixtures. */
final class DqmRangeBreakTest {
    private static final String REGION_ID = "worldguard_test_dqm_range";
    private static final BlockPos CONTROL = new BlockPos(4099, 250, 4104);
    private static final BlockPos PROTECTED = new BlockPos(4107, 250, 4104);

    private DqmRangeBreakTest() { }

    static void run(ServerLevel level) {
        Item tool = DqmTerrainAction.item("dqmvii:legacy_item_kc_magic_tool");
        RegionManager manager = WorldGuard.getInstance().getPlatform().getRegionContainer()
                .get(WorldGuardNeoForge.world(level));
        if (manager == null || manager.hasRegion(REGION_ID)) {
            throw new AssertionError("DQM range fixture region manager unavailable or ID already exists");
        }
        FakePlayer player = DqmTerrainAction.player(level,
                "8a56fb83-1448-46fa-a843-536e3d3b74e3", "wg_dqm_range");

        List<BlockPos> controlCells = cells(CONTROL);
        List<BlockPos> protectedCells = cells(PROTECTED);
        for (BlockPos pos : controlCells) requireEmpty(level, pos);
        for (BlockPos pos : protectedCells) requireEmpty(level, pos);
        ProtectedCuboidRegion claim = new ProtectedCuboidRegion(REGION_ID,
                BlockVector3.at(PROTECTED.getX() + 1, PROTECTED.getY(), PROTECTED.getZ()),
                BlockVector3.at(PROTECTED.getX() + 1, PROTECTED.getY() + 2, PROTECTED.getZ() + 2));
        boolean added = false;
        try {
            fill(level, controlCells);
            fill(level, protectedCells);
            InteractionResult controlResult = DqmTerrainAction.use(player, new ItemStack(tool), CONTROL);
            int controlRemoved = removed(level, controlCells);
            boolean controlNeighborRemoved = level.getBlockState(CONTROL.east()).isAir();
            System.out.println("DQM_RANGE_CONTROL result=" + controlResult
                    + " removed=" + controlRemoved + " eastRemoved=" + controlNeighborRemoved);
            if (controlRemoved < 2 || !controlNeighborRemoved) {
                System.out.println("DQM_RANGE_INCONCLUSIVE no_confirmed_range_break");
                throw new AssertionError("DQM_RANGE_INCONCLUSIVE: unprotected magic_tool had no range effect");
            }

            manager.addRegion(claim);
            added = true;
            InteractionResult protectedResult = DqmTerrainAction.use(player, new ItemStack(tool), PROTECTED);
            int protectedRemoved = removed(level, protectedCells);
            boolean protectedNeighborRemoved = level.getBlockState(PROTECTED.east()).isAir();
            System.out.println("DQM_RANGE_PROTECTED result=" + protectedResult
                    + " removed=" + protectedRemoved + " eastRemoved=" + protectedNeighborRemoved);
            if (protectedNeighborRemoved) {
                throw new AssertionError("DQM_RANGE_PROTECTION_FAILED: magic_tool removed an outsider-protected block");
            }
            System.out.println("DQM_RANGE_PROTECTION_PASSED");
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
            throw new AssertionError("DQM range fixture is not empty at " + pos);
        }
    }

    private static void fill(ServerLevel level, List<BlockPos> positions) {
        for (BlockPos pos : positions) level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
    }

    private static void clear(ServerLevel level, List<BlockPos> positions) {
        for (BlockPos pos : positions) level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
    }

    private static int removed(ServerLevel level, List<BlockPos> positions) {
        int count = 0;
        for (BlockPos pos : positions) if (level.getBlockState(pos).isAir()) count++;
        return count;
    }

}
