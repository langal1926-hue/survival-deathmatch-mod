package com.zombieapocalypse.network;

import com.zombieapocalypse.ZombieApocalypseMod;
import com.zombieapocalypse.item.G17PistolItem;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * A tiny client->server request. The server checks the player's actual held
 * item, current cooldown, inventory and world before taking any action.
 */
public final class GunNetwork {
    private GunNetwork() {}

    public record GunAction(boolean reload) implements CustomPacketPayload {
        public static final Type<GunAction> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath(ZombieApocalypseMod.MOD_ID, "gun_action"));

        public static final StreamCodec<ByteBuf, GunAction> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, GunAction::reload, GunAction::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(GunAction.TYPE, GunAction.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                G17PistolItem.handleInput(serverPlayer, payload.reload());
            }
        });
    }
}
