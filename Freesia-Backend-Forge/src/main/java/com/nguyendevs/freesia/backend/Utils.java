package com.nguyendevs.freesia.backend;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.UUID;

public class Utils {
    public static ServerPlayer randomPlayerIfNotFound(UUID uuid) {
        if (uuid != null) {
            ServerPlayer expected = ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayer(uuid);
            if (expected != null) {
                return expected;
            }
        }

        ServerPlayer cached = (ServerPlayer) FreesiaBackend.INSTANCE.getPayloadPlayer();
        if (cached != null) {
            return cached;
        }

        return ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayers().stream().findAny().orElse(null);
    }
}
