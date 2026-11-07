package dev.marigold.handinhand;

import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Reports only the authenticated sender's own movement controls, never a position or force. */
public record TetherIntent(UUID partner,long started,boolean moving,float bodyYaw) implements CustomPacketPayload {
    public TetherIntent(UUID partner,long started,boolean moving){this(partner,started,moving,0);}
    public static final Type<TetherIntent> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(HandInHand.ID,"tether_intent"));
    public static final StreamCodec<RegistryFriendlyByteBuf,TetherIntent> CODEC=StreamCodec.of(
        (b,p)->{b.writeUUID(p.partner);b.writeLong(p.started);b.writeBoolean(p.moving);b.writeFloat(p.bodyYaw);},
        b->new TetherIntent(b.readUUID(),b.readLong(),b.readBoolean(),b.readFloat()));
    public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
