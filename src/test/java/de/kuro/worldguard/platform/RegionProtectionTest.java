package de.kuro.worldguard.platform;

import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.util.Location;
import com.sk89q.worldedit.world.NullWorld;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.domains.Association;
import com.sk89q.worldguard.protection.association.RegionAssociable;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.managers.index.HashMapIndex;
import com.sk89q.worldguard.protection.managers.storage.file.YamlRegionFile;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionQuery.QueryOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RegionProtectionTest {
    private static final RegionAssociable OUTSIDER = regions -> Association.NON_MEMBER;
    private static final RegionAssociable MEMBER = regions -> Association.MEMBER;
    @TempDir Path directory;

    @BeforeAll
    static void registerFlags() {
        Flags.registerAll();
    }

    private RegionManager manager(String name) {
        return new RegionManager(new YamlRegionFile(name, directory.resolve(name + ".yml").toFile()),
                new HashMapIndex.Factory(), WorldGuard.getInstance().getFlagRegistry());
    }

    private static ProtectedCuboidRegion region(String id, int min, int max) {
        return new ProtectedCuboidRegion(id, BlockVector3.at(min, 60, min), BlockVector3.at(max, 80, max));
    }

    @Test
    void onlyMembersCanBuildInsideClaimAndBoundaryIsInclusive() {
        RegionManager manager = manager("claims");
        manager.addRegion(region("temple", -2, 2));
        assertFalse(manager.getApplicableRegions(BlockVector3.at(2, 64, 2)).testState(OUTSIDER, Flags.BUILD));
        assertTrue(manager.getApplicableRegions(BlockVector3.at(2, 64, 2)).testState(MEMBER, Flags.BUILD));
        assertTrue(manager.getApplicableRegions(BlockVector3.at(3, 64, 2)).testState(OUTSIDER, Flags.BUILD));
    }

    @Test
    void explicitBreakDenyAlsoBlocksMembers() {
        RegionManager manager = manager("deny");
        ProtectedRegion claim = region("temple", -2, 2);
        claim.setFlag(Flags.BLOCK_BREAK, StateFlag.State.DENY);
        manager.addRegion(claim);
        assertFalse(manager.getApplicableRegions(BlockVector3.at(0, 64, 0))
                .testState(MEMBER, Flags.BUILD, Flags.BLOCK_BREAK));
    }

    @Test
    void higherPriorityOverridesLowerButDenyWinsAtEqualPriority() {
        RegionManager manager = manager("priority");
        ProtectedRegion outer = region("outer", -10, 10);
        ProtectedRegion inner = region("inner", -2, 2);
        outer.setFlag(Flags.PVP, StateFlag.State.DENY);
        inner.setFlag(Flags.PVP, StateFlag.State.ALLOW);
        inner.setPriority(1);
        manager.addRegion(outer);
        manager.addRegion(inner);
        assertTrue(manager.getApplicableRegions(BlockVector3.at(0, 64, 0)).testState(OUTSIDER, Flags.PVP));
        assertFalse(manager.getApplicableRegions(BlockVector3.at(5, 64, 5)).testState(OUTSIDER, Flags.PVP));
        inner.setPriority(0);
        assertFalse(manager.getApplicableRegions(BlockVector3.at(0, 64, 0)).testState(OUTSIDER, Flags.PVP));
    }

    @Test
    void currentQueryReflectsRegionChangesAtTheSamePosition() {
        RegionManager manager = manager("refresh");
        CurrentRegionQueryCache cache = new CurrentRegionQueryCache();
        Location position = new Location(NullWorld.getInstance(), 1, 64, 1);
        assertTrue(cache.queryContains(manager, position, QueryOption.COMPUTE_PARENTS)
                .testState(OUTSIDER, Flags.BUILD));
        manager.addRegion(region("temple", 0, 2));
        assertFalse(cache.queryContains(manager, position, QueryOption.COMPUTE_PARENTS)
                .testState(OUTSIDER, Flags.BUILD));
        manager.removeRegion("temple");
        assertTrue(cache.queryContains(manager, position, QueryOption.COMPUTE_PARENTS)
                .testState(OUTSIDER, Flags.BUILD));
    }

    @Test
    void readsExistingYamlAndPreservesOwnerGroupFlagsAndParentOnSave() throws Exception {
        Path file = directory.resolve("persist.yml");
        Files.writeString(file, """
                regions:
                  temple:
                    type: cuboid
                    min: {x: -10, y: 60, z: -10}
                    max: {x: 10, y: 80, z: 10}
                    priority: 5
                    flags: {pvp: deny}
                    owners:
                      unique-ids: [b2677dd7-14d0-47b7-a646-015ed165a494]
                    members:
                      groups: [builders]
                  courtyard:
                    type: cuboid
                    min: {x: -2, y: 60, z: -2}
                    max: {x: 2, y: 80, z: 2}
                    priority: 6
                    parent: temple
                    flags: {}
                    owners: {}
                    members: {}
                """);
        RegionManager first = manager("persist");
        first.load();
        assertPersistedMeaning(first);
        first.save();
        RegionManager reopened = manager("persist");
        reopened.load();
        assertPersistedMeaning(reopened);
    }

    private static void assertPersistedMeaning(RegionManager manager) {
        ProtectedRegion temple = manager.getRegion("temple");
        assertNotNull(temple);
        assertEquals(BlockVector3.at(-10, 60, -10), temple.getMinimumPoint());
        assertEquals(BlockVector3.at(10, 80, 10), temple.getMaximumPoint());
        assertEquals(5, temple.getPriority());
        assertEquals(StateFlag.State.DENY, temple.getFlag(Flags.PVP));
        assertTrue(temple.getOwners().contains(UUID.fromString("b2677dd7-14d0-47b7-a646-015ed165a494")));
        assertTrue(temple.getMembers().getGroups().contains("builders"));
        assertEquals("temple", manager.getRegion("courtyard").getParent().getId());
        assertFalse(manager.getApplicableRegions(BlockVector3.at(0, 64, 0)).testState(OUTSIDER, Flags.PVP));
    }
}
