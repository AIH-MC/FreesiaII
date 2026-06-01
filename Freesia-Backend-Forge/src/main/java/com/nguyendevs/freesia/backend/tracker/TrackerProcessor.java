package com.nguyendevs.freesia.backend.tracker;

import com.nguyendevs.freesia.backend.Config;
import com.nguyendevs.freesia.backend.FreesiaBackend;
import com.nguyendevs.freesia.backend.Utils;
import com.nguyendevs.freesia.backend.event.CyanidinRealPlayerTrackerUpdateEvent;
import com.nguyendevs.freesia.backend.event.CyanidinTrackerScanEvent;
import com.nguyendevs.freesia.backend.utils.FriendlyByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class TrackerProcessor {
    public static final String CHANNEL_NAME = "freesia:tracker_sync";
    public static final ResourceLocation CHANNEL_RL = new ResourceLocation("freesia:tracker_sync");

    private final Map<UUID, Set<UUID>> entityViewers = new ConcurrentHashMap<>();
    private int tickCounter = 0;

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (++tickCounter % Config.REFRESH_INTERVAL.get() != 0) return;

        for (Map.Entry<UUID, Set<UUID>> entry : this.entityViewers.entrySet()) {
            UUID beWatched = entry.getKey();
            for (UUID watcher : entry.getValue()) {
                this.notifyTrackerUpdate(watcher, beWatched);
            }
        }
    }

    @SubscribeEvent
    public void handlePacket(NetworkEvent.ServerCustomPayloadEvent event) {
        event.getSource().get().enqueueWork(() -> {
            final FriendlyByteBuf packetData = new FriendlyByteBuf(event.getPayload());
            try {
                if (packetData.readVarInt() == 1) {
                    final int callbackId = packetData.readVarInt();
                    final UUID requestedPlayerUUID = packetData.readUUID();

                    final ServerPlayer sender = event.getSource().get().getSender();
                    if (sender == null) return;

                    final Set<UUID> cachedViewers = this.entityViewers.get(requestedPlayerUUID);
                    final Set<UUID> result = cachedViewers != null ? new HashSet<>(cachedViewers) : new HashSet<>();

                    final CyanidinTrackerScanEvent trackerScanEvent = new CyanidinTrackerScanEvent(result, requestedPlayerUUID);
                    MinecraftForge.EVENT_BUS.post(trackerScanEvent);

                    final FriendlyByteBuf reply = new FriendlyByteBuf(Unpooled.buffer());
                    reply.writeVarInt(0);
                    reply.writeVarInt(callbackId);
                    reply.writeVarInt(result.size());

                    for (UUID uuid : result) {
                        reply.writeUUID(uuid);
                    }

                    sender.connection.send(new ClientboundCustomPayloadPacket(CHANNEL_RL, new net.minecraft.network.FriendlyByteBuf(reply)));
                }
            } finally {
                packetData.release();
            }
        });
        event.getSource().get().setPacketHandled(true);
    }

    @SubscribeEvent
    public void onPlayerTrackEntity(PlayerEvent.StartTracking event) {
        final Player watcher = event.getEntity();
        final Entity beingWatched = event.getTarget();

        this.entityViewers
                .computeIfAbsent(beingWatched.getUUID(), k -> ConcurrentHashMap.newKeySet())
                .add(watcher.getUUID());

        if (beingWatched instanceof Player beingWatchedPlayer) {
            this.playerTrackedPlayer(beingWatchedPlayer, watcher);
        }
    }

    @SubscribeEvent
    public void onPlayerUntrackEntity(PlayerEvent.StopTracking event) {
        final Player watcher = event.getEntity();
        final Entity beingWatched = event.getTarget();

        Set<UUID> viewers = this.entityViewers.get(beingWatched.getUUID());
        if (viewers != null) {
            viewers.remove(watcher.getUUID());
        }
    }

    @SubscribeEvent
    public void onPlayerQuit(PlayerEvent.PlayerLoggedOutEvent event) {
        final Player player = event.getEntity();
        this.entityViewers.remove(player.getUUID());

        for (Set<UUID> viewers : this.entityViewers.values()) {
            viewers.remove(player.getUUID());
        }
    }

    @SubscribeEvent
    public void onPlayerAddedToWorld(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof Player player) {
            this.playerTrackedPlayer(player, player);
        }
    }

    private void playerTrackedPlayer(Player beSeen, Player seeing) {
        CyanidinRealPlayerTrackerUpdateEvent event = new CyanidinRealPlayerTrackerUpdateEvent(seeing, beSeen);
        MinecraftForge.EVENT_BUS.post(event);
        if (event.isCanceled()) {
            return;
        }
        this.notifyTrackerUpdate(seeing.getUUID(), beSeen.getUUID());
    }

    public void notifyTrackerUpdate(UUID watcher, UUID beWatched) {
        final FriendlyByteBuf wrappedUpdatePacket = new FriendlyByteBuf(Unpooled.buffer());

        wrappedUpdatePacket.writeVarInt(2);
        wrappedUpdatePacket.writeUUID(beWatched);
        wrappedUpdatePacket.writeUUID(watcher);

        final ServerPlayer payload = Utils.randomPlayerIfNotFound(watcher);

        if (payload == null) {
            wrappedUpdatePacket.release();
            return;
        }

        payload.connection.send(new ClientboundCustomPayloadPacket(CHANNEL_RL, new net.minecraft.network.FriendlyByteBuf(wrappedUpdatePacket)));
    }
}
