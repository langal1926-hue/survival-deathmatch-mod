package com.zombieapocalypse.network;

import com.zombieapocalypse.ZombieApocalypseMod;
import com.zombieapocalypse.client.ClientGunInput;
import com.zombieapocalypse.client.ClientGunHud;
import com.zombieapocalypse.item.G17PistolItem;
import com.zombieapocalypse.item.GunAimState;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Client requests only an action or ADS state. The server validates the actual
 * gun, cooldown, ammo, and movement when creating each shot.
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

    public record AimUpdate(boolean aiming) implements CustomPacketPayload {
        public static final Type<AimUpdate> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath(ZombieApocalypseMod.MOD_ID, "aim_update"));
        public static final StreamCodec<ByteBuf, AimUpdate> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, AimUpdate::aiming, AimUpdate::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Only returned for a real, successfully spawned bullet. */
    public record ShotFeedback(boolean aimed) implements CustomPacketPayload {
        public static final Type<ShotFeedback> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath(ZombieApocalypseMod.MOD_ID, "shot_feedback"));
        public static final StreamCodec<ByteBuf, ShotFeedback> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, ShotFeedback::aimed, ShotFeedback::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Sent only when the server actually consumed ammunition and completed reload. */
    public record ReloadFeedback(boolean confirmed) implements CustomPacketPayload {
        public static final Type<ReloadFeedback> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath(ZombieApocalypseMod.MOD_ID, "reload_feedback"));
        public static final StreamCodec<ByteBuf, ReloadFeedback> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, ReloadFeedback::confirmed, ReloadFeedback::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static void sendReloadFeedback(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new ReloadFeedback(true));
    }

    public static void sendShotFeedback(ServerPlayer player, boolean aimed) {
        PacketDistributor.sendToPlayer(player, new ShotFeedback(aimed));
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("2");
        registrar.playToServer(GunAction.TYPE, GunAction.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                G17PistolItem.handleInput(player, payload.reload());
            }
        });
        registrar.playToServer(AimUpdate.TYPE, AimUpdate.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                GunAimState.update(player, payload.aiming());
            }
        });
        // This callback is invoked only on the logical client. Keep Minecraft
        // client code in ClientGunInput instead of accessing it from this class.
        registrar.playToClient(ShotFeedback.TYPE, ShotFeedback.STREAM_CODEC,
                (payload, context) -> ClientGunInput.applyRecoil(payload.aimed()));
        registrar.playToClient(ReloadFeedback.TYPE, ReloadFeedback.STREAM_CODEC,
                (payload, context) -> {
                    if (payload.confirmed()) {
                        ClientGunHud.reloadConfirmed();
                    }
                });
    }
}
