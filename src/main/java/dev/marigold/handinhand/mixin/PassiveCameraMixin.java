package dev.marigold.handinhand.mixin;
import dev.marigold.handinhand.*;
import dev.marigold.handinhand.client.*;
import net.minecraft.client.Camera;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.*;
import net.minecraft.world.phys.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(Camera.class)
public abstract class PassiveCameraMixin {
 @Shadow public abstract Vec3 getPosition();
 @Shadow protected abstract void setPosition(Vec3 position);
 @Unique private final CameraDamping handinhandEye=new CameraDamping();
 @Unique private final CameraDamping handinhandEyeX=new CameraDamping(.035),handinhandEyeZ=new CameraDamping(.035);
 @Unique private long handinhandTime,handinhandSession=Long.MIN_VALUE;
 @Unique private Object handinhandLevel;
 @Inject(method="setup",at=@At("TAIL"),remap=false)
 private void handinhandPassiveEye(BlockGetter level,Entity entity,boolean detached,boolean mirrored,float partial,CallbackInfo ci){
  if(detached||!(entity instanceof LocalPlayer p)||Boolean.getBoolean("handinhand.legacyCamera")||!PairClient.passiveView(p)||p.isCrouching()||p.isPassenger()){
   handinhandEye.clear();handinhandEyeX.clear();handinhandEyeZ.clear();handinhandTime=0;handinhandLevel=null;return;
  }
  var link=HandClient.LINKS.get(p.getUUID());if(link==null)return;
  var original=getPosition();long now=System.nanoTime();double dt=handinhandTime==0?0:(now-handinhandTime)/1e9;
  double scale=ScaleSupport.modelScale(p,partial)/.9375,limit=.24*scale;
  if(!Double.isFinite(scale)||scale<=0){handinhandEye.clear();handinhandEyeX.clear();handinhandEyeZ.clear();handinhandTime=0;return;}
  boolean reset=handinhandTime==0||handinhandLevel!=level||handinhandSession!=link.started();
  double feet=p.getPosition(partial).y;
  double y=handinhandEye.update(original.y,dt,Math.max(original.y-limit,feet+.05*scale),Math.min(original.y+limit,feet+p.getBbHeight()-.05*scale),reset);
  var body=p.getPosition(partial);double side=Math.max(0,Math.min(.2*scale,p.getBbWidth()/2-.04*scale));
  double x=handinhandEyeX.update(original.x,dt,Math.max(original.x-side,body.x-side),Math.min(original.x+side,body.x+side),reset);
  double z=handinhandEyeZ.update(original.z,dt,Math.max(original.z-side,body.z-side),Math.min(original.z+side,body.z+side),reset);
  var target=new Vec3(x,y,z);
  if(level.clip(new ClipContext(original,target,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p)).getType()==HitResult.Type.BLOCK){handinhandEye.clear();handinhandEyeX.clear();handinhandEyeZ.clear();target=original;}
  setPosition(target);handinhandTime=now;handinhandLevel=level;handinhandSession=link.started();
 }
}
