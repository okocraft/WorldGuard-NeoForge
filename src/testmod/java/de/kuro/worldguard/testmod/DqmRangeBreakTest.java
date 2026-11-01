package de.kuro.worldguard.testmod;

import com.mojang.authlib.GameProfile;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;
import de.kuro.worldguard.api.WorldGuardNeoForge;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Real DQM magic-tool action against paired unprotected and region-protected fixtures. */
final class DqmRangeBreakTest {
    private static final Identifier TOOL_ID = Identifier.parse("dqmvii:legacy_item_kc_magic_tool");
    private static final String REGION_ID = "worldguard_test_dqm_range";
    private static final BlockPos CONTROL = new BlockPos(4099, 250, 4104);
    private static final BlockPos PROTECTED = new BlockPos(4107, 250, 4104);
    private static final UUID PLAYER_ID = UUID.fromString("8a56fb83-1448-46fa-a843-536e3d3b74e3");

    private DqmRangeBreakTest() { }

    static void run(ServerLevel level) {
        Item tool = BuiltInRegistries.ITEM.getValue(TOOL_ID);
        if (tool == null || !TOOL_ID.equals(BuiltInRegistries.ITEM.getKey(tool))) {
            System.out.println("DQM_RANGE_INCONCLUSIVE item_not_registered=" + TOOL_ID);
            throw new AssertionError("DQM_RANGE_INCONCLUSIVE: item is not registered");
        }
        RegionManager manager = WorldGuard.getInstance().getPlatform().getRegionContainer()
                .get(WorldGuardNeoForge.world(level));
        if (manager == null || manager.hasRegion(REGION_ID)) {
            throw new AssertionError("DQM range fixture region manager unavailable or ID already exists");
        }
        FakePlayer player = FakePlayerFactory.get(level, new GameProfile(PLAYER_ID, "wg_dqm_range"));
        player.gameMode.changeGameModeForPlayer(GameType.CREATIVE);
        if (!player.isCreative() || player.isSpectator()) {
            throw new AssertionError("DQM magic-tool test player is not in creative mode");
        }
        var local = WorldGuardNeoForge.player(player);
        if (WorldGuard.getInstance().getPlatform().getSessionManager()
                .hasBypass(local, local.getWorld())) {
            throw new AssertionError("DQM magic-tool test player unexpectedly bypasses regions");
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
            fill(level, controlCells);
            fill(level, protectedCells);
            InteractionResult controlResult = use(level, player, tool, CONTROL);
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
            InteractionResult protectedResult = use(level, player, tool, PROTECTED);
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

    private static InteractionResult use(ServerLevel level, FakePlayer player, Item tool, BlockPos pos) {
        player.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        player.setYRot(0.0f); // south: the 3-wide tool reaches the eastern neighbor.
        ItemStack stack = new ItemStack(tool);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
        PlayerInteractEvent.RightClickBlock interact = new PlayerInteractEvent.RightClickBlock(
                player, InteractionHand.MAIN_HAND, pos, hit);
        NeoForge.EVENT_BUS.post(interact);
        if (interact.isCanceled()) {
            System.out.println("DQM_RANGE_CLICK_CANCELED at=" + pos);
            return interact.getCancellationResult();
        }
        return stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
    }
}
