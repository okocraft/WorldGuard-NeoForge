package de.kuro.worldguard.testmod;

import com.mojang.authlib.GameProfile;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;
import de.kuro.worldguard.api.WorldGuardNeoForge;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.data.DataType;
import net.luckperms.api.node.types.InheritanceNode;
import net.luckperms.api.node.types.PermissionNode;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;

import java.util.Arrays;
import java.util.UUID;

/** Loaded only when the optional LuckPerms mod is installed in the isolated server. */
final class LuckPermsProtectionTest {
    private static final String REGION_ID = "worldguard_test_luckperms_group";
    private static final String ROOT_GROUP = "wg_test_root";
    private static final String CHILD_GROUP = "wg_test_child";
    private static final BlockPos REGION_POS = new BlockPos(4160, 250, 4096);
    private static final UUID MEMBER_ID = UUID.fromString("5b6c7724-2f74-46da-8d47-292978f59257");
    private static final UUID OUTSIDER_ID = UUID.fromString("d253b248-118d-4cfa-a1d3-c9910c1e3bb8");

    private LuckPermsProtectionTest() { }

    static void run(ServerLevel level) {
        var luckPerms = LuckPermsProvider.get();
        var groups = luckPerms.getGroupManager();
        var users = luckPerms.getUserManager();
        RegionManager regions = WorldGuard.getInstance().getPlatform().getRegionContainer()
                .get(WorldGuardNeoForge.world(level));
        if (regions == null || regions.hasRegion(REGION_ID)) {
            throw new AssertionError("LuckPerms test region manager is unavailable or region ID already exists");
        }

        var root = groups.createAndLoadGroup(ROOT_GROUP).join();
        var child = groups.createAndLoadGroup(CHILD_GROUP).join();
        var member = users.loadUser(MEMBER_ID, "wg_lp_member").join();
        var outsider = users.loadUser(OUTSIDER_ID, "wg_lp_outsider").join();
        PermissionNode allow = PermissionNode.builder("worldguard.test.allowed").value(true).build();
        PermissionNode deny = PermissionNode.builder("worldguard.test.denied").value(false).build();
        PermissionNode groupAllowDenied = PermissionNode.builder("worldguard.test.denied").value(true).build();
        PermissionNode inherited = PermissionNode.builder("worldguard.test.inherited").value(true).build();
        InheritanceNode parentGroup = InheritanceNode.builder(ROOT_GROUP).build();
        InheritanceNode childGroup = InheritanceNode.builder(CHILD_GROUP).build();

        try {
            root.getData(DataType.NORMAL).add(inherited);
            root.getData(DataType.NORMAL).add(groupAllowDenied);
            child.getData(DataType.NORMAL).add(parentGroup);
            groups.saveGroup(root).join();
            groups.saveGroup(child).join();
            member.getData(DataType.NORMAL).add(allow);
            member.getData(DataType.NORMAL).add(deny);
            member.getData(DataType.NORMAL).add(childGroup);
            users.saveUser(member).join();
            root.getCachedData().invalidate();
            child.getCachedData().invalidate();
            member.getCachedData().invalidate();

            FakePlayer memberPlayer = fake(level, MEMBER_ID, "wg_lp_member");
            FakePlayer outsiderPlayer = fake(level, OUTSIDER_ID, "wg_lp_outsider");
            var local = WorldGuardNeoForge.player(memberPlayer);
            var outsiderLocal = WorldGuardNeoForge.player(outsiderPlayer);
            expect("unset non-OP permission", local.hasPermission("worldguard.test.unset"), false);
            expect("explicit permission allow", local.hasPermission("worldguard.test.allowed"), true);
            expect("explicit permission deny", local.hasPermission("worldguard.test.denied"), false);
            expect("inherited group permission", local.hasPermission("worldguard.test.inherited"), true);
            expect("direct group membership", local.hasGroup(CHILD_GROUP), true);
            expect("inherited group membership", local.hasGroup(ROOT_GROUP), true);
            expect("inherited group in getGroups", Arrays.asList(local.getGroups()).contains(ROOT_GROUP), true);
            expect("outsider has no test group", outsiderLocal.hasGroup(ROOT_GROUP), false);

            ProtectedCuboidRegion groupClaim = new ProtectedCuboidRegion(REGION_ID,
                    BlockVector3.at(REGION_POS.getX() - 2, REGION_POS.getY() - 2, REGION_POS.getZ() - 2),
                    BlockVector3.at(REGION_POS.getX() + 2, REGION_POS.getY() + 2, REGION_POS.getZ() + 2));
            groupClaim.getMembers().addGroup(ROOT_GROUP);
            regions.addRegion(groupClaim);
            expect("group member break", breakBlock(level, memberPlayer), false);
            expect("outsider break in group claim", breakBlock(level, outsiderPlayer), true);
        } finally {
            regions.removeRegion(REGION_ID);
            member.getData(DataType.NORMAL).remove(allow);
            member.getData(DataType.NORMAL).remove(deny);
            member.getData(DataType.NORMAL).remove(childGroup);
            child.getData(DataType.NORMAL).remove(parentGroup);
            root.getData(DataType.NORMAL).remove(inherited);
            root.getData(DataType.NORMAL).remove(groupAllowDenied);
            users.saveUser(member).join();
            groups.deleteGroup(child).join();
            groups.deleteGroup(root).join();
            users.cleanupUser(member);
            users.cleanupUser(outsider);
            users.deletePlayerData(MEMBER_ID).join();
            users.deletePlayerData(OUTSIDER_ID).join();
        }
        System.out.println("WG_LUCKPERMS_TESTS_PASSED");
    }

    private static FakePlayer fake(ServerLevel level, UUID id, String name) {
        FakePlayer player = FakePlayerFactory.get(level, new GameProfile(id, name));
        player.setPos(REGION_POS.getX() + 0.5, REGION_POS.getY(), REGION_POS.getZ() + 0.5);
        return player;
    }

    private static boolean breakBlock(ServerLevel level, FakePlayer player) {
        BreakBlockEvent event = new BreakBlockEvent(level, REGION_POS,
                Blocks.STONE.defaultBlockState(), player);
        NeoForge.EVENT_BUS.post(event);
        return event.isCanceled();
    }

    private static void expect(String caseName, boolean actual, boolean expected) {
        if (actual != expected) {
            throw new AssertionError(caseName + ": expected=" + expected + ", actual=" + actual);
        }
    }
}
