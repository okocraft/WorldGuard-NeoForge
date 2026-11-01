package de.kuro.worldguard.testmod;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.util.FakePlayer;

import java.util.UUID;

/** FakePlayer with ordinary PvP eligibility for impact tests. */
final class DqmTestPlayer extends FakePlayer {
    private DqmTestPlayer(ServerLevel level, GameProfile profile) {
        super(level, profile);
    }

    static DqmTestPlayer create(ServerLevel level, String name, String id, BlockPos pos) {
        DqmTestPlayer player = new DqmTestPlayer(level,
                new GameProfile(UUID.fromString(id), name));
        player.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        return player;
    }

    @Override
    public boolean canHarmPlayer(Player other) {
        return true;
    }
}
