package de.kuro.worldguard.platform;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.luckperms.api.query.QueryOptions;
import net.luckperms.api.util.Tristate;
import net.minecraft.server.level.ServerPlayer;

final class LuckPermsRegionPermissionResolver implements RegionPermissionResolver {
    private final LuckPerms luckPerms = LuckPermsProvider.get();
    private final NeoForgeWorldGuardPlatform platform;

    LuckPermsRegionPermissionResolver(NeoForgeWorldGuardPlatform platform) {
        this.platform = platform;
    }

    private QueryOptions options(User user) {
        return luckPerms.getContextManager().getQueryOptions(user)
                .orElseGet(() -> luckPerms.getContextManager().getStaticQueryOptions());
    }

    @Override
    public boolean hasPermission(ServerPlayer player, String node) {
        User user = luckPerms.getUserManager().getUser(player.getUUID());
        if (user == null) return false;
        Tristate permission = user.getCachedData().getPermissionData(options(user)).checkPermission(node);
        return permission == Tristate.UNDEFINED
                ? RegionPermissionResolver.defaultPermission(platform, player)
                : permission.asBoolean();
    }

    @Override
    public String[] getGroups(ServerPlayer player) {
        User user = luckPerms.getUserManager().getUser(player.getUUID());
        if (user == null) return new String[0];
        return user.getInheritedGroups(options(user)).stream()
                .map(group -> group.getName()).toArray(String[]::new);
    }
}
