package dev.marigold.handinhand;

import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Only the server may send relationship state. Zero UUID is an explicit release. */
public record LinkPayload(UUID first, UUID second, long started,float horizontalA,float horizontalB,float verticalA,float verticalB,boolean verticalTether,boolean firstRight,boolean secondRight) implements CustomPacketPayload {
    public LinkPayload(UUID first,UUID second,long started,float horizontalA,float horizontalB,float verticalA,float verticalB,boolean verticalTether){this(first,second,started,horizontalA,horizontalB,verticalA,verticalB,verticalTether,true,false);}
    public LinkPayload(UUID first,UUID second,long started,float horizontalA,float horizontalB,float verticalA,float verticalB){this(first,second,started,horizontalA,horizontalB,verticalA,verticalB,false);}
    public LinkPayload(UUID first,UUID second,long started){this(first,second,started,1,1,1,1);}
    public static final UUID NONE=new UUID(0,0);
    public static final Type<LinkPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(HandInHand.ID,"link"));
    public static Consumer<LinkPayload> receiver=p -> {};
    public static final StreamCodec<RegistryFriendlyByteBuf,LinkPayload> CODEC=StreamCodec.of(
        (b,p)-> {b.writeUUID(p.first); b.writeUUID(p.second); b.writeLong(p.started);b.writeFloat(p.horizontalA);b.writeFloat(p.horizontalB);b.writeFloat(p.verticalA);b.writeFloat(p.verticalB);b.writeBoolean(p.verticalTether);b.writeBoolean(p.firstRight);b.writeBoolean(p.secondRight);},
        b->new LinkPayload(b.readUUID(),b.readUUID(),b.readLong(),b.readFloat(),b.readFloat(),b.readFloat(),b.readFloat(),b.readBoolean(),b.readBoolean(),b.readBoolean()));
    public Type<? extends CustomPacketPayload> type() {return TYPE;}
    public static void register(RegisterPayloadHandlersEvent e) {
        var registrar=e.registrar("10");
        registrar.playToClient(TYPE,CODEC,(p,c)->receiver.accept(p));
        registrar.playToClient(PairFrame.TYPE,PairFrame.CODEC,(p,c)->PairFrame.receiver.accept(p));
        registrar.playBidirectional(ClockPayload.TYPE,ClockPayload.CODEC,(p,c)->{if(c.player() instanceof net.minecraft.server.level.ServerPlayer actor){if(p.tick()==-1)net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(actor,new ClockPayload(p.stamp(),actor.server.overworld().getGameTime()));}else ClockPayload.receiver.accept(p);});
        registrar.playToClient(FacingPayload.TYPE,FacingPayload.CODEC,(p,c)->FacingPayload.receiver.accept(p));
        registrar.playToClient(TetherMotion.TYPE,TetherMotion.CODEC,(p,c)->TetherMotion.receiver.accept(p));
        registrar.playToServer(TetherIntent.TYPE,TetherIntent.CODEC,(p,c)->{if(c.player() instanceof net.minecraft.server.level.ServerPlayer player)HandServer.intent(player,p);});
    }
}
