package dev.marigold.handinhand;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Replaces a following plan. Offset and this tick's pace never enter entity velocity. */
public record TetherMotion(UUID partner,long started,long frameTick,boolean following,double x,double y,double z,double maximum,boolean lift,double vx,double vy,double vz,double ax,double ay,double az) implements CustomPacketPayload {
    public TetherMotion(UUID partner,long started,long frameTick,boolean following,double x,double y,double z,double maximum,boolean lift,double vx,double vy,double vz){this(partner,started,frameTick,following,x,y,z,maximum,lift,vx,vy,vz,0,0,0);}
    public TetherMotion(UUID partner,long started,long frameTick,boolean following,double x,double y,double z,double maximum){this(partner,started,frameTick,following,x,y,z,maximum,false,0,0,0);}
    public TetherMotion(UUID partner,long started,double x,double y,double z){this(partner,started,0,true,x,y,z,.2);}
    public static final Type<TetherMotion> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(HandInHand.ID,"tether_motion"));
    public static Consumer<TetherMotion> receiver=p->{};
    public static final StreamCodec<RegistryFriendlyByteBuf,TetherMotion> CODEC=StreamCodec.of(
        (b,p)->{b.writeUUID(p.partner);b.writeLong(p.started);b.writeLong(p.frameTick);b.writeBoolean(p.following);b.writeDouble(p.x);b.writeDouble(p.y);b.writeDouble(p.z);b.writeDouble(p.maximum);b.writeBoolean(p.lift);b.writeDouble(p.vx);b.writeDouble(p.vy);b.writeDouble(p.vz);b.writeDouble(p.ax);b.writeDouble(p.ay);b.writeDouble(p.az);},
        b->new TetherMotion(b.readUUID(),b.readLong(),b.readLong(),b.readBoolean(),b.readDouble(),b.readDouble(),b.readDouble(),b.readDouble(),b.readBoolean(),b.readDouble(),b.readDouble(),b.readDouble(),b.readDouble(),b.readDouble(),b.readDouble()));
    public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
