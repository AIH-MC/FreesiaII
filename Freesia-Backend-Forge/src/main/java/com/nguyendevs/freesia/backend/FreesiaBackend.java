package com.nguyendevs.freesia.backend;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.nguyendevs.freesia.backend.misc.VirtualPlayerManager;
import com.nguyendevs.freesia.backend.tracker.TrackerProcessor;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.event.EventNetworkChannel;
import net.minecraftforge.server.permission.PermissionAPI;
import net.minecraftforge.server.permission.nodes.PermissionNode;
import net.minecraftforge.server.permission.nodes.PermissionTypes;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Field;

@Mod(FreesiaBackend.MODID)
public final class FreesiaBackend {
    public static final String MODID = "freesia_backend";
    public static final Logger LOGGER = LogManager.getLogger();
    public static FreesiaBackend INSTANCE;

    public static final PermissionNode<Boolean> RELOAD_PERMISSION = new PermissionNode<>(
            MODID,
            "admin.reload",
            PermissionTypes.BOOLEAN,
            (player, uuid, contexts) -> player != null && player.hasPermissions(2)
    );

    private static final String PROTOCOL_VERSION = "1";
    
    public static final EventNetworkChannel TRACKER_CHANNEL = NetworkRegistry.ChannelBuilder
            .named(new ResourceLocation("freesia:tracker_sync"))
            .networkProtocolVersion(() -> PROTOCOL_VERSION)
            .clientAcceptedVersions(v -> true)
            .serverAcceptedVersions(v -> true)
            .eventNetworkChannel();

    public static final EventNetworkChannel VIRTUAL_PLAYER_CHANNEL = NetworkRegistry.ChannelBuilder
            .named(new ResourceLocation("freesia:virtual_player_management"))
            .networkProtocolVersion(() -> PROTOCOL_VERSION)
            .clientAcceptedVersions(v -> true)
            .serverAcceptedVersions(v -> true)
            .eventNetworkChannel();

    private volatile Player payloadPlayer;

    private final TrackerProcessor trackerProcessor = new TrackerProcessor();
    private final VirtualPlayerManager virtualPlayerManager = new VirtualPlayerManager();

    private static Field connectionChannelField;

    static {
        try {
            // 在 1.20.1 中，Connection 类的 channel 字段通常名为 'channel' (m_129479_)
            for (Field field : net.minecraft.network.Connection.class.getDeclaredFields()) {
                if (field.getType() == Channel.class) {
                    connectionChannelField = field;
                    connectionChannelField.setAccessible(true);
                    break;
                }
            }
        } catch (Exception e) {
            LOGGER.error("Failed to find Connection.channel field!", e);
        }
    }

    public FreesiaBackend() {
        INSTANCE = this;
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC);

        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.register(this.trackerProcessor);
        MinecraftForge.EVENT_BUS.register(this.virtualPlayerManager);

        TRACKER_CHANNEL.registerObject(this.trackerProcessor);
        VIRTUAL_PLAYER_CHANNEL.registerObject(this.virtualPlayerManager);

        LOGGER.info("Freesia Backend (Forge) initialized!");
    }

    @SubscribeEvent
    public void onPermissionRegister(net.minecraftforge.server.permission.events.PermissionGatherEvent.Nodes event) {
        event.addNodes(RELOAD_PERMISSION);
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("freesiabackend-forge")
                .then(Commands.literal("reload")
                        .requires(src -> {
                            if (src.getPlayer() != null) {
                                return PermissionAPI.getPermission(src.getPlayer(), RELOAD_PERMISSION);
                            }
                            return src.hasPermission(2);
                        })
                        .executes(ctx -> {
                            ctx.getSource().sendSuccess(() -> Component.literal("§5[§dFreesia-Backend§5] §aConfiguration reloaded!"), true);
                            return 1;
                        })
                );

        dispatcher.register(root);
        dispatcher.register(Commands.literal("fbf").redirect(dispatcher.getRoot().getChild("freesiabackend-forge")));
    }

    public Player getPayloadPlayer() {
        return this.payloadPlayer;
    }

    @SubscribeEvent
    public void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (this.payloadPlayer == null) {
            this.payloadPlayer = event.getEntity();
        }

        if (event.getEntity() instanceof ServerPlayer sp) {
            injectNetworkHandler(sp);
        }
    }

    @SubscribeEvent
    public void onPlayerQuit(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity().equals(this.payloadPlayer)) {
            this.payloadPlayer = event.getEntity().getServer().getPlayerList().getPlayers().stream()
                    .filter(p -> !p.equals(event.getEntity()))
                    .findAny()
                    .orElse(null);
        }
    }

    private void injectNetworkHandler(ServerPlayer player) {
        try {
            if (connectionChannelField == null) return;
            Channel channel = (Channel) connectionChannelField.get(player.connection.connection);
            if (channel == null) return;

            channel.pipeline().addBefore("packet_handler", "freesia_packet_fixer", new ChannelOutboundHandlerAdapter() {
                @Override
                public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
                    super.write(ctx, msg, promise);
                }
            });
        } catch (Exception e) {
            LOGGER.error("Failed to inject network handler for player {}", player.getName().getString(), e);
        }
    }
}
