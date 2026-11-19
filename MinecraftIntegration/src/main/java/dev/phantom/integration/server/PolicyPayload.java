package dev.phantom.integration.server;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.nio.charset.StandardCharsets;

/**
 * {@code phantom:policy}: server -> client. The body is raw UTF-8 JSON (no length prefix), which is
 * exactly what Bukkit/Paper's {@code Player#sendPluginMessage} and Velocity/BungeeCord send.
 *
 * <p>Decoding never throws: an oversized body is skipped and reported as {@code null}, so a broken
 * or hostile payload cannot disconnect the player.</p>
 */
public record PolicyPayload(String json) implements CustomPayload {
    public static final Id<PolicyPayload> ID = new Id<>(Identifier.of("phantom", "policy"));
    public static final int MAX_BYTES = 16_384;

    public static final PacketCodec<RegistryByteBuf, PolicyPayload> CODEC = PacketCodec.of(
            (payload, buf) -> write(payload, buf),
            PolicyPayload::read);

    private static void write(PolicyPayload payload, ByteBuf buf) {
        buf.writeBytes((payload.json == null ? "" : payload.json).getBytes(StandardCharsets.UTF_8));
    }

    private static PolicyPayload read(ByteBuf buf) {
        int n = buf.readableBytes();
        if (n > MAX_BYTES) {
            buf.skipBytes(n);
            return new PolicyPayload(null);
        }
        byte[] bytes = new byte[n];
        buf.readBytes(bytes);
        return new PolicyPayload(new String(bytes, StandardCharsets.UTF_8));
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
