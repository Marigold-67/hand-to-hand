package dev.marigold.handinhand.mixin;
import dev.marigold.handinhand.client.PairClient;
import net.minecraft.client.player.RemotePlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(net.minecraft.world.entity.LivingEntity.class)
public abstract class RemotePairMixin {
 @Inject(method="lerpTo",at=@At("HEAD"),cancellable=true,remap=false)
 private void handinhandPair(double x,double y,double z,float yaw,float pitch,int steps,CallbackInfo ci){
  if(!((Object)this instanceof RemotePlayer p))return;
  if(PairClient.hasFresh(p.getUUID())){p.setYRot(yaw);p.setXRot(pitch);ci.cancel();}
 }
}
