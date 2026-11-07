package dev.marigold.handinhand.mixin;
import dev.marigold.handinhand.HandServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerFollowMixin {
 @Shadow public ServerPlayer player;
 @Redirect(method="handleMovePlayer",at=@At(value="INVOKE",target="Lnet/minecraft/network/protocol/game/ServerboundMovePlayerPacket;getX(D)D",ordinal=1),remap=false)
 private double handinhandX(ServerboundMovePlayerPacket p,double fallback){return HandServer.isControlled(player)?player.getX():HandServer.constrain(player,new net.minecraft.world.phys.Vec3(p.getX(player.getX()),p.getY(player.getY()),p.getZ(player.getZ()))).x;}
 @Redirect(method="handleMovePlayer",at=@At(value="INVOKE",target="Lnet/minecraft/network/protocol/game/ServerboundMovePlayerPacket;getY(D)D",ordinal=1),remap=false)
 private double handinhandY(ServerboundMovePlayerPacket p,double fallback){return HandServer.isControlled(player)?player.getY():p.getY(fallback);}
 @Redirect(method="handleMovePlayer",at=@At(value="INVOKE",target="Lnet/minecraft/network/protocol/game/ServerboundMovePlayerPacket;getZ(D)D",ordinal=1),remap=false)
 private double handinhandZ(ServerboundMovePlayerPacket p,double fallback){return HandServer.isControlled(player)?player.getZ():HandServer.constrain(player,new net.minecraft.world.phys.Vec3(p.getX(player.getX()),p.getY(player.getY()),p.getZ(player.getZ()))).z;}
}
