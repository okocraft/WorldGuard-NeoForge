package de.kuro.worldguard.platform;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;

public interface RegionPermissionResolver {
    boolean hasPermission(ServerPlayer player, String node);

    default boolean hasGroup(ServerPlayer player, String group) {
        for (String candidate : getGroups(player)) {
            if (candidate.equalsIgnoreCase(group)) return true;
        }
        return false;
    }

    default String[] getGroups(ServerPlayer player) {
        return new String[0];
    }

    static RegionPermissionResolver defaultResolver(NeoForgeWorldGuardPlatform platform) {
        if (ModList.get().isLoaded("luckperms")) {
            return new LuckPermsRegionPermissionResolver(platform);
        }
        return (player, node) -> defaultPermission(platform, player);
    }

    static boolean defaultPermission(NeoForgeWorldGuardPlatform platform, ServerPlayer player) {
        return platform.getGlobalStateManager().get(platform.adaptWorld(player.level())).opPermissions
                && player.level().getServer().getPlayerList().isOp(player.nameAndId());
    }

}
