package de.kuro.worldguard.testmod;

import com.mojang.authlib.GameProfile;
import com.sk89q.worldguard.WorldGuard;
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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.lang.reflect.Method;
import java.util.UUID;

/** Native creative-item interaction shared by the additional DQM terrain cases. */
final class DqmTerrainAction {
    private DqmTerrainAction() { }

    static Item item(String id) {
        Identifier key = Identifier.parse(id);
        Item item = BuiltInRegistries.ITEM.getValue(key);
        if (item == null || !key.equals(BuiltInRegistries.ITEM.getKey(item))) {
            throw new AssertionError("DQM_TERRAIN_INCONCLUSIVE: item is not registered: " + key);
        }
        return item;
    }

    static FakePlayer player(ServerLevel level, String id, String name) {
        FakePlayer player = FakePlayerFactory.get(level, new GameProfile(UUID.fromString(id), name));
        player.gameMode.changeGameModeForPlayer(GameType.CREATIVE);
        if (!player.isCreative() || player.isSpectator()) {
            throw new AssertionError("DQM_TERRAIN_INCONCLUSIVE: creative player was not established");
        }
        var local = WorldGuardNeoForge.player(player);
        if (WorldGuard.getInstance().getPlatform().getSessionManager().hasBypass(local, local.getWorld())) {
            throw new AssertionError("DQM_TERRAIN_INCONCLUSIVE: fixture player bypasses regions");
        }
        return player;
    }

    static void rangeMode(ItemStack stack, int mode) {
        try {
            Class<?> type = Class.forName("com.dqm.DqmvRangeBreakTool");
            Method setter = type.getMethod("setMode", ItemStack.class, int.class);
            setter.invoke(null, stack, mode);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("DQM range mode setup failed", failure);
        }
    }

    static InteractionResult use(FakePlayer player, ItemStack stack, BlockPos pos) {
        player.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        player.setYRot(0.0f); // South; the width-three range reaches the eastern neighbor.
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
        PlayerInteractEvent.RightClickBlock interact = new PlayerInteractEvent.RightClickBlock(
                player, InteractionHand.MAIN_HAND, pos, hit);
        NeoForge.EVENT_BUS.post(interact);
        if (interact.isCanceled()) return interact.getCancellationResult();
        return stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
    }
}
