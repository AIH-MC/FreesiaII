package com.nguyendevs.freesia.backend.misc;

import com.nguyendevs.freesia.backend.FreesiaBackend;
import com.nguyendevs.freesia.backend.Utils;
import com.nguyendevs.freesia.backend.utils.FriendlyByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public class VirtualPlayerManager {
    public static final String CHANNEL_NAME = "freesia:virtual_player_management";
    public static final ResourceLocation CHANNEL_RL = new ResourceLocation("freesia", "virtual_player_management");

    private final AtomicInteger eventIdGenerator = new AtomicInteger(0);
    private final Map<Integer, Consumer<Boolean>> pendingCallbacks = new ConcurrentHashMap<>();

    @SubscribeEvent
    public void handlePacket(NetworkEvent.ServerCustomPayloadEvent event) {
        event.getSource().get().enqueueWork(() -> {
            final FriendlyByteBuf packetBuffer = new FriendlyByteBuf(event.getPayload());
            try {
                final byte packetId = packetBuffer.readByte();
                if (packetId == 2) {
                    final int eventId = packetBuffer.readVarInt();
                    final boolean result = packetBuffer.readBoolean();
                    final Consumer<Boolean> removedCallback = this.pendingCallbacks.remove(eventId);
                    if (removedCallback != null) {
                        removedCallback.accept(result);
                    } else {
                        FreesiaBackend.LOGGER.warn("Received unknown callback for virtual player operations {}", eventId);
                    }
                }
            } finally {
                packetBuffer.release();
            }
        });
        event.getSource().get().setPacketHandled(true);
    }

    public CompletableFuture<Boolean> setVirtualPlayerData(UUID playerUUID, byte[] data) {
        final FriendlyByteBuf packetBuffer = new FriendlyByteBuf(Unpooled.buffer());
        final int generatedEventId = this.eventIdGenerator.getAndIncrement();

        packetBuffer.writeByte(4);
        packetBuffer.writeVarInt(generatedEventId);
        packetBuffer.writeUUID(playerUUID);
        packetBuffer.writeBytes(data);

        final ServerPlayer payload = Utils.randomPlayerIfNotFound(null);

        if (payload == null) {
            packetBuffer.release();
            return CompletableFuture.failedFuture(new IllegalStateException("Could not find a available payload"));
        }

        final CompletableFuture<Boolean> future = new CompletableFuture<>();
        this.pendingCallbacks.put(generatedEventId, future::complete);

        payload.connection.send(new ClientboundCustomPayloadPacket(CHANNEL_RL, new net.minecraft.network.FriendlyByteBuf(packetBuffer)));

        future.whenComplete((res, ex) -> this.pendingCallbacks.remove(generatedEventId));
        return future.orTimeout(30, TimeUnit.SECONDS);
    }

    public CompletableFuture<Boolean> removeVirtualPlayer(UUID playerUUID) {
        final FriendlyByteBuf packetBuffer = new FriendlyByteBuf(Unpooled.buffer());
        final int generatedEventId = this.eventIdGenerator.getAndIncrement();

        packetBuffer.writeByte(1);
        packetBuffer.writeVarInt(generatedEventId);
        packetBuffer.writeUUID(playerUUID);

        final ServerPlayer payload = Utils.randomPlayerIfNotFound(null);

        if (payload == null) {
            packetBuffer.release();
            return CompletableFuture.failedFuture(new IllegalStateException("Could not find a available payload"));
        }

        final CompletableFuture<Boolean> future = new CompletableFuture<>();
        this.pendingCallbacks.put(generatedEventId, future::complete);

        payload.connection.send(new ClientboundCustomPayloadPacket(CHANNEL_RL, new net.minecraft.network.FriendlyByteBuf(packetBuffer)));

        future.whenComplete((res, ex) -> this.pendingCallbacks.remove(generatedEventId));
        return future.orTimeout(30, TimeUnit.SECONDS);
    }

    public CompletableFuture<Boolean> addVirtualPlayer(UUID playerUUID, int entityId) {
        final FriendlyByteBuf packetBuffer = new FriendlyByteBuf(Unpooled.buffer());
        final int generatedEventId = this.eventIdGenerator.getAndIncrement();

        packetBuffer.writeByte(0);
        packetBuffer.writeVarInt(generatedEventId);
        packetBuffer.writeVarInt(entityId);
        packetBuffer.writeUUID(playerUUID);

        final ServerPlayer payload = Utils.randomPlayerIfNotFound(null);

        if (payload == null) {
            packetBuffer.release();
            return CompletableFuture.failedFuture(new IllegalStateException("Could not find a available payload"));
        }

        final CompletableFuture<Boolean> future = new CompletableFuture<>();
        this.pendingCallbacks.put(generatedEventId, future::complete);

        payload.connection.send(new ClientboundCustomPayloadPacket(CHANNEL_RL, new net.minecraft.network.FriendlyByteBuf(packetBuffer)));

        future.whenComplete((res, ex) -> this.pendingCallbacks.remove(generatedEventId));
        return future.orTimeout(30, TimeUnit.SECONDS);
    }
}
