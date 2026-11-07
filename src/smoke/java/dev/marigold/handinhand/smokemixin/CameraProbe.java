package dev.marigold.handinhand.smokemixin;
import dev.marigold.handinhand.client.SmoothingNetwork;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(Camera.class)
public abstract class CameraProbe {
 @Shadow public abstract net.minecraft.world.phys.Vec3 getPosition();
 @Inject(method="setup",at=@At(value="INVOKE",target="Lnet/minecraft/client/Camera;setPosition(DDD)V",ordinal=0,shift=At.Shift.AFTER),remap=false)
 private void handinhandRawView(net.minecraft.world.level.BlockGetter level,net.minecraft.world.entity.Entity entity,boolean detached,boolean mirrored,float partial,CallbackInfo ci){
  if(Boolean.getBoolean("handinhand.smoothingCheck"))SmoothingNetwork.rawCamera=getPosition();
 }
}
