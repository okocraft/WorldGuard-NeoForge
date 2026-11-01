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

/** Normal magic_tool8 use erases matching blocks in its 5x5 area. */
final class DqmMagicBuildTest {
    private static final String REGION_ID = "worldguard_test_dqm_magic_build";
    private static final BlockPos CONTROL = new BlockPos(4098, 250, 4122);
    private static final BlockPos PROTECTED = new BlockPos(4107, 250, 4122);

    private DqmMagicBuildTest() { }

    static void run(ServerLevel level) {
        Item tool = DqmTerrainAction.item("dqmvii:legacy_item_kc_magic_tool8");
        FakePlayer player = DqmTerrainAction.player(level,
                "039d40b2-168b-42f9-a77c-f7e7ef568215", "wg_dqm_builder");
        RegionManager manager = WorldGuard.getInstance().getPlatform().getRegionContainer()
                .get(WorldGuardNeoForge.world(level));
        if (manager == null || manager.hasRegion(REGION_ID)) {
            throw new AssertionError("DQM magic-build fixture region manager unavailable or ID already exists");
        }
        List<BlockPos> controlCells = cells(CONTROL);
        List<BlockPos> protectedCells = cells(PROTECTED);
        for (BlockPos pos : controlCells) requireEmpty(level, pos);
        for (BlockPos pos : protectedCells) requireEmpty(level, pos);
        ProtectedCuboidRegion claim = new ProtectedCuboidRegion(REGION_ID,
                BlockVector3.at(PROTECTED.getX() + 3, PROTECTED.getY(), PROTECTED.getZ()),
                BlockVector3.at(PROTECTED.getX() + 4, PROTECTED.getY(), PROTECTED.getZ() + 4));
        boolean added = false;
        try {
            fillStone(level, controlCells);
            fillStone(level, protectedCells);
            var controlResult = DqmTerrainAction.use(player, new ItemStack(tool), CONTROL);
            boolean controlClickedErased = level.getBlockState(CONTROL).isAir();
            boolean controlFarErased = level.getBlockState(CONTROL.offset(4, 0, 4)).isAir();
            System.out.println("DQM_BUILD_CONTROL result=" + controlResult + " clickedErased="
                    + controlClickedErased + " farErased=" + controlFarErased);
            if (!controlClickedErased || !controlFarErased) {
                throw new AssertionError("DQM_BUILD_INCONCLUSIVE: unprotected magic_tool8 did not erase its 5x5 area");
            }

            manager.addRegion(claim);
            added = true;
            var protectedResult = DqmTerrainAction.use(player,
                    new ItemStack(tool), PROTECTED);
            boolean protectedFarErased = level.getBlockState(PROTECTED.offset(4, 0, 4)).isAir();
            System.out.println("DQM_BUILD_PROTECTED result=" + protectedResult
                    + " farErased=" + protectedFarErased);
            if (protectedFarErased) {
                throw new AssertionError("DQM_BUILD_PROTECTION_FAILED: magic_tool8 erased protected stone");
            }
            System.out.println("DQM_BUILD_PROTECTION_PASSED");
        } finally {
            if (added) manager.removeRegion(REGION_ID);
            clear(level, controlCells);
            clear(level, protectedCells);
        }
    }

    private static List<BlockPos> cells(BlockPos origin) {
        List<BlockPos> positions = new ArrayList<>(25);
        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 5; z++) positions.add(origin.offset(x, 0, z));
        }
        return positions;
    }

    private static void requireEmpty(ServerLevel level, BlockPos pos) {
        if (!level.getBlockState(pos).isAir()) {
            throw new AssertionError("DQM magic-build fixture is not empty at " + pos);
        }
    }

    private static void fillStone(ServerLevel level, List<BlockPos> positions) {
        for (BlockPos pos : positions) level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
    }

    private static void clear(ServerLevel level, List<BlockPos> positions) {
        for (BlockPos pos : positions) level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
    }
}
