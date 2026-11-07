package dev.marigold.handinhand;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
/** Body-facing metadata only. This is not the planned authoritative physical pair snapshot. */
public record FacingPayload(UUID first,UUID second,UUID follower,long started,long frameTick,double heading,boolean grab,boolean assisted) implements CustomPacketPayload {
    public static final Type<FacingPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(HandInHand.ID,"facing"));
    public static Consumer<FacingPayload> receiver=p->{};
    public static final StreamCodec<RegistryFriendlyByteBuf,FacingPayload> CODEC=StreamCodec.of(
        (b,p)->{b.writeUUID(p.first);b.writeUUID(p.second);b.writeUUID(p.follower);b.writeLong(p.started);b.writeLong(p.frameTick);b.writeDouble(p.heading);b.writeBoolean(p.grab);b.writeBoolean(p.assisted);},
        b->new FacingPayload(b.readUUID(),b.readUUID(),b.readUUID(),b.readLong(),b.readLong(),b.readDouble(),b.readBoolean(),b.readBoolean()));
    public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
