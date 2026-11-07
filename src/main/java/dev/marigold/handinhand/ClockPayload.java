package dev.marigold.handinhand;
import java.util.function.Consumer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
/** Echoed monotonic client timestamp; -1 is a request, all other ticks are server replies. */
public record ClockPayload(long stamp,long tick) implements CustomPacketPayload {
 public static final Type<ClockPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(HandInHand.ID,"clock"));
 public static Consumer<ClockPayload> receiver=p->{};
 public static final StreamCodec<RegistryFriendlyByteBuf,ClockPayload> CODEC=StreamCodec.of((b,p)->{b.writeLong(p.stamp);b.writeLong(p.tick);},b->new ClockPayload(b.readLong(),b.readLong()));
 public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
