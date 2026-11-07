package dev.marigold.handinhand.mixin;
import dev.marigold.handinhand.HandServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A current, consented lift is the only exemption; no flight permission is changed. */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerLiftMixin {
    @Shadow public ServerPlayer player;
    @Shadow private boolean clientIsFloating;
    @Shadow private int aboveGroundTickCount;
    @Inject(method="tick",at=@At("HEAD"),remap=false)
    private void handinhandLift(CallbackInfo ci){
        if(HandServer.isLifted(player)){clientIsFloating=false;aboveGroundTickCount=0;player.resetFallDistance();}
    }
}
