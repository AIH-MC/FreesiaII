package com.nguyendevs.freesia.common.communicating.message.w2m;

import io.netty.buffer.ByteBuf;
import com.nguyendevs.freesia.common.communicating.handler.NettyServerChannelHandlerLayer;
import com.nguyendevs.freesia.common.communicating.message.IMessage;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class W2MWorkerInfoMessage implements IMessage<NettyServerChannelHandlerLayer> {
    private static final int CAPABILITY_MARKER = 0x46574350;

    private UUID workerUUID;
    private String workerName;
    private boolean allowModelUpload;

    public W2MWorkerInfoMessage() {
    }

    public W2MWorkerInfoMessage(UUID workerUUID, String workerName, boolean allowModelUpload) {
        this.workerUUID = workerUUID;
        this.workerName = workerName;
        this.allowModelUpload = allowModelUpload;
    }

    @Override
    public void writeMessageData(@NotNull ByteBuf buffer) {
        buffer.writeLong(workerUUID.getMostSignificantBits());
        buffer.writeLong(workerUUID.getLeastSignificantBits());
        byte[] workerNameBytes = workerName.getBytes(StandardCharsets.UTF_8);
        buffer.writeInt(workerNameBytes.length);
        buffer.writeBytes(workerNameBytes);
        buffer.writeInt(CAPABILITY_MARKER);
        buffer.writeBoolean(allowModelUpload);
    }

    @Override
    public void readMessageData(@NotNull ByteBuf buffer) {
        workerUUID = new UUID(buffer.readLong(), buffer.readLong());
        if (buffer.readableBytes() >= Integer.BYTES) {
            int readerIndex = buffer.readerIndex();
            int nameLength = buffer.readInt();
            if (nameLength >= 0 && nameLength <= buffer.readableBytes()) {
                byte[] bytes = new byte[nameLength];
                buffer.readBytes(bytes);
                workerName = new String(bytes, StandardCharsets.UTF_8);
                if (buffer.readableBytes() >= Integer.BYTES + 1 && buffer.readInt() == CAPABILITY_MARKER) {
                    allowModelUpload = buffer.readBoolean();
                }
                return;
            }
            buffer.readerIndex(readerIndex);
        }

        byte[] bytes = new byte[buffer.readableBytes()];
        buffer.readBytes(bytes);
        workerName = new String(bytes, StandardCharsets.UTF_8);
        allowModelUpload = false;
    }

    @Override
    public void process(@NotNull NettyServerChannelHandlerLayer handler) {
        handler.updateWorkerInfo(this.workerUUID, this.workerName, this.allowModelUpload);
    }
}

