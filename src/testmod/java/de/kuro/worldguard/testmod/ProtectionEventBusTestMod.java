package de.kuro.worldguard.testmod;

import com.mojang.authlib.GameProfile;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;
import de.kuro.worldguard.api.WorldGuardNeoForge;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Dedicated-server event-bus integration fixture. Packaged only in the smoke-test mod. */
@Mod("worldguard_test")
public final class ProtectionEventBusTestMod {
    private static final String CLAIM_ID = "worldguard_test_claim";
    private static final String DENY_ID = "worldguard_test_explicit_deny";
    private static final BlockPos CLAIM_POS = new BlockPos(4096, 250, 4096);
    private static final BlockPos DENY_POS = new BlockPos(4112, 250, 4096);
    private static final BlockPos OUTSIDE_POS = new BlockPos(4140, 250, 4096);
    private static final UUID OWNER_ID = UUID.fromString("fbb0a322-6fd9-4d3c-bf03-586f4e11d431");
    private static final UUID MEMBER_ID = UUID.fromString("8f1ae534-3c36-4c8a-84c4-26672219686b");
    private static final UUID OUTSIDER_ID = UUID.fromString("1542519b-b0bf-4639-986c-96ef17907e18");

    private boolean ran;
    private int readyTicks;
    private DqmMeraPvpTest pendingMera;
    private final List<Throwable> dqmFailures = new ArrayList<>();

    public ProtectionEventBusTestMod() {
        NeoForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        if (pendingMera != null) {
            try {
                if (pendingMera.tick()) {
                    pendingMera = null;
                    if (!dqmFailures.isEmpty()) {
                        IllegalStateException aggregate = new IllegalStateException("DQM protection tests failed");
                        dqmFailures.forEach(aggregate::addSuppressed);
                        throw aggregate;
                    }
                    System.out.println("WG_PROTECTION_TESTS_PASSED");
                }
            } catch (Throwable failure) {
                if (failure.getSuppressed().length == 0) {
                    attachFailures(failure);
                }
                fail(event.getServer(), failure);
            }
            return;
        }
        if (ran) return;
        MinecraftServer server = event.getServer();
        if (!WorldGuardNeoForge.isReady() || WorldGuard.getInstance().getPlatform().getRegionContainer()
                .get(WorldGuardNeoForge.world(server.overworld())) == null) {
            if (++readyTicks < 200) return;
            ran = true;
            fail(server, new IllegalStateException("WorldGuard overworld regions did not load within 200 server ticks"));
            return;
        }
        ran = true;
        try {
            exercise(server.overworld());
            if (ModList.get().isLoaded("luckperms")) {
                LuckPermsProtectionTest.run(server.overworld());
            }
            if (ModList.get().isLoaded("dqmvii")) {
                try {
                    DqmRangeBreakTest.run(server.overworld());
                } catch (Throwable failure) {
                    recordDqmFailure("RANGE", failure);
                }
                try {
                    DqmRangeFluidTest.run(server.overworld());
                } catch (Throwable failure) {
                    recordDqmFailure("FLUID", failure);
                }
                try {
                    DqmMagicBuildTest.run(server.overworld());
                } catch (Throwable failure) {
                    recordDqmFailure("BUILD", failure);
                }
                try {
                    DqmBomiePvpTest.run(server.overworld());
                } catch (Throwable failure) {
                    recordDqmFailure("BOMIE", failure);
                }
                try {
                    DqmKiariPvpTest.run(server.overworld());
                } catch (Throwable failure) {
                    recordDqmFailure("KIARI", failure);
                }
                pendingMera = DqmMeraPvpTest.start(server.overworld());
                return;
            }
            System.out.println("WG_PROTECTION_TESTS_PASSED");
        } catch (Throwable failure) {
            attachFailures(failure);
            fail(server, failure);
        }
    }

    private void recordDqmFailure(String caseName, Throwable failure) {
        dqmFailures.add(failure);
        System.err.println("DQM_" + caseName + "_RESULT " + failure);
    }

    private void attachFailures(Throwable target) {
        for (Throwable failure : dqmFailures) {
            if (failure != target) target.addSuppressed(failure);
        }
    }

    private static void exercise(ServerLevel level) {
        RegionManager manager = WorldGuard.getInstance().getPlatform().getRegionContainer()
                .get(WorldGuardNeoForge.world(level));
        if (manager == null) throw new AssertionError("Overworld region manager was not loaded");
        if (manager.hasRegion(CLAIM_ID) || manager.hasRegion(DENY_ID)) {
            throw new AssertionError("Test region IDs already exist in the isolated world");
        }

        FakePlayer owner = fake(level, OWNER_ID, "wg_test_owner");
        FakePlayer member = fake(level, MEMBER_ID, "wg_test_member");
        FakePlayer outsider = fake(level, OUTSIDER_ID, "wg_test_outsider");
        var sessions = WorldGuard.getInstance().getPlatform().getSessionManager();
        for (FakePlayer player : new FakePlayer[] {owner, member, outsider}) {
            var local = WorldGuardNeoForge.player(player);
            if (sessions.hasBypass(local, local.getWorld())) {
                throw new AssertionError(player.getName().getString() + " unexpectedly bypasses regions");
            }
        }

        ProtectedCuboidRegion claim = cuboid(CLAIM_ID, CLAIM_POS);
        claim.getOwners().addPlayer(OWNER_ID);
        claim.getMembers().addPlayer(MEMBER_ID);
        ProtectedCuboidRegion explicitDeny = cuboid(DENY_ID, DENY_POS);
        explicitDeny.getOwners().addPlayer(OWNER_ID);
        explicitDeny.setFlag(Flags.BLOCK_BREAK, StateFlag.State.DENY);

        BlockState previous = level.getBlockState(CLAIM_POS);
        if (!previous.isAir()) throw new AssertionError("Test block position is not empty in the isolated world");
        try {
            manager.addRegion(claim);
            manager.addRegion(explicitDeny);
            level.setBlockAndUpdate(CLAIM_POS, Blocks.CHEST.defaultBlockState());
            if (!level.getBlockState(CLAIM_POS).is(Blocks.CHEST)) {
                throw new AssertionError("Could not place isolated chest fixture");
            }

            expect("outsider break inside claim", breakBlock(level, CLAIM_POS, outsider), true);
            expect("owner break inside claim", breakBlock(level, CLAIM_POS, owner), false);
            expect("member break inside claim", breakBlock(level, CLAIM_POS, member), false);
            expect("owner break under explicit deny", breakBlock(level, DENY_POS, owner), true);
            expect("outsider break outside claim", breakBlock(level, OUTSIDE_POS, outsider), false);

            expect("outsider place inside claim", placeBlock(level, CLAIM_POS, outsider), true);
            expect("owner place inside claim", placeBlock(level, CLAIM_POS, owner), false);
            expect("member place inside claim", placeBlock(level, CLAIM_POS, member), false);
            expect("outsider place outside claim", placeBlock(level, OUTSIDE_POS, outsider), false);

            expect("outsider chest use inside claim", useBlock(CLAIM_POS, outsider), true);
            expect("owner chest use inside claim", useBlock(CLAIM_POS, owner), false);
            expect("member chest use inside claim", useBlock(CLAIM_POS, member), false);
        } finally {
            level.setBlockAndUpdate(CLAIM_POS, previous);
            manager.removeRegion(CLAIM_ID);
            manager.removeRegion(DENY_ID);
        }
    }

    private static ProtectedCuboidRegion cuboid(String id, BlockPos center) {
        return new ProtectedCuboidRegion(id,
                BlockVector3.at(center.getX() - 2, center.getY() - 2, center.getZ() - 2),
                BlockVector3.at(center.getX() + 2, center.getY() + 2, center.getZ() + 2));
    }

    private static FakePlayer fake(ServerLevel level, UUID id, String name) {
        FakePlayer player = FakePlayerFactory.get(level, new GameProfile(id, name));
        player.setPos(CLAIM_POS.getX() + 0.5, CLAIM_POS.getY(), CLAIM_POS.getZ() + 0.5);
        return player;
    }

    private static boolean breakBlock(ServerLevel level, BlockPos pos, FakePlayer player) {
        BreakBlockEvent event = new BreakBlockEvent(level, pos, Blocks.STONE.defaultBlockState(), player);
        NeoForge.EVENT_BUS.post(event);
        return event.isCanceled();
    }

    private static boolean placeBlock(ServerLevel level, BlockPos pos, FakePlayer player) {
        BlockSnapshot snapshot = BlockSnapshot.create(level.dimension(), level, pos);
        BlockEvent.EntityPlaceEvent event = new BlockEvent.EntityPlaceEvent(
                snapshot, Blocks.STONE.defaultBlockState(), player);
        NeoForge.EVENT_BUS.post(event);
        return event.isCanceled();
    }

    private static boolean useBlock(BlockPos pos, FakePlayer player) {
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
        PlayerInteractEvent.RightClickBlock event = new PlayerInteractEvent.RightClickBlock(
                player, InteractionHand.MAIN_HAND, pos, hit);
        NeoForge.EVENT_BUS.post(event);
        return event.isCanceled();
    }

    private static void expect(String caseName, boolean actual, boolean expected) {
        if (actual != expected) {
            throw new AssertionError(caseName + ": expected canceled=" + expected + ", actual=" + actual);
        }
    }

    private static void fail(MinecraftServer server, Throwable failure) {
        System.err.println("WG_PROTECTION_TESTS_FAILED: " + failure);
        failure.printStackTrace(System.err);
        server.halt(false);
        throw new IllegalStateException("WG_PROTECTION_TESTS_FAILED", failure);
    }
}
