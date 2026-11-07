package dev.marigold.handinhand;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
/** Both collision-resolved actors, at one server step. Never a request to chase another client. */
public record PairFrame(UUID first,UUID second,long session,long tick,int leader,boolean grab,boolean rightA,boolean rightB,Body a,Body b) implements CustomPacketPayload {
 public record Body(double x,double y,double z,double dx,double dy,double dz,float bodyYaw,boolean controlled,boolean grounded,boolean flying){
  static void write(RegistryFriendlyByteBuf b,Body p){b.writeDouble(p.x);b.writeDouble(p.y);b.writeDouble(p.z);b.writeDouble(p.dx);b.writeDouble(p.dy);b.writeDouble(p.dz);b.writeFloat(p.bodyYaw);b.writeBoolean(p.controlled);b.writeBoolean(p.grounded);b.writeBoolean(p.flying);}
  static Body read(RegistryFriendlyByteBuf b){return new Body(b.readDouble(),b.readDouble(),b.readDouble(),b.readDouble(),b.readDouble(),b.readDouble(),b.readFloat(),b.readBoolean(),b.readBoolean(),b.readBoolean());}
 }
 public static final Type<PairFrame> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(HandInHand.ID,"pair_frame"));
 public static Consumer<PairFrame> receiver=p->{};
 public static final StreamCodec<RegistryFriendlyByteBuf,PairFrame> CODEC=StreamCodec.of((b,p)->{
  b.writeUUID(p.first);b.writeUUID(p.second);b.writeLong(p.session);b.writeLong(p.tick);b.writeInt(p.leader);b.writeBoolean(p.grab);b.writeBoolean(p.rightA);b.writeBoolean(p.rightB);Body.write(b,p.a);Body.write(b,p.b);
 },b->new PairFrame(b.readUUID(),b.readUUID(),b.readLong(),b.readLong(),b.readInt(),b.readBoolean(),b.readBoolean(),b.readBoolean(),Body.read(b),Body.read(b)));
 public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
