package dev.marigold.handinhand.client;

import dev.marigold.handinhand.*;
import com.zigythebird.playeranim.api.PlayerAnimationFactory;
import com.zigythebird.playeranimcore.animation.AnimationData;
import com.zigythebird.playeranimcore.animation.layered.IAnimation;
import com.zigythebird.playeranimcore.bones.PlayerAnimBone;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;
import java.util.*;

@EventBusSubscriber(modid=HandInHand.ID,value=Dist.CLIENT)
public final class HandClient {
    public record Link(UUID first,UUID second,long started,float horizontalA,float horizontalB,float verticalA,float verticalB,boolean verticalTether,boolean firstRight,boolean secondRight) {
        UUID other(UUID id){return id.equals(first)?second:first;}
        boolean right(UUID id){return id.equals(first)?firstRight:secondRight;}
    }
    public static final Map<UUID,Link> LINKS=new HashMap<>();
    private static TetherMotion tetherTarget;
    private static int tetherTargetTick;
    private static int intentTick=-1;
    
    public static final KeyMapping HOLD=new KeyMapping("key.handinhand.hold",GLFW.GLFW_KEY_H,"key.categories.handinhand");
    public static final KeyMapping GRAB=new KeyMapping("key.handinhand.grab",GLFW.GLFW_KEY_G,"key.categories.handinhand");
    public static final KeyMapping RELEASE=new KeyMapping("key.handinhand.release",GLFW.GLFW_KEY_UNKNOWN,"key.categories.handinhand");
    public static void receive(LinkPayload p) {
        if(p.second().equals(LinkPayload.NONE)){Link old=LINKS.remove(p.first());if(old!=null) {PairClient.remove(old.first(),old.second());LINKS.remove(old.first());LINKS.remove(old.second());if(tetherTarget!=null&&(tetherTarget.partner().equals(old.first())||tetherTarget.partner().equals(old.second()))){tetherTarget=null;}}}
        else {Link link=new Link(p.first(),p.second(),p.started(),p.horizontalA(),p.horizontalB(),p.verticalA(),p.verticalB(),p.verticalTether(),p.firstRight(),p.secondRight());LINKS.put(p.first(),link);LINKS.put(p.second(),link);}
    }
    public static float flightFactor(UUID player,boolean vertical){
        var link=LINKS.get(player);if(link==null)return 1;
        float f=player.equals(link.first())?(vertical?link.verticalA():link.horizontalA()):(vertical?link.verticalB():link.horizontalB());
        return Float.isFinite(f)?Mth.clamp(f,0,1):1;
    }
    public static void receiveTether(TetherMotion p) {
        var mc=Minecraft.getInstance();if(mc.player==null)return;
        var link=LINKS.get(mc.player.getUUID());
        if(link==null||link.started()!=p.started()||!link.other(mc.player.getUUID()).equals(p.partner())||!HandInHand.MOVEMENT_TETHER.get())return;
        var offset=new Vec3(p.x(),p.y(),p.z());
        var pace=new Vec3(p.vx(),p.vy(),p.vz());
        if(!Double.isFinite(offset.lengthSqr()+p.maximum()+pace.lengthSqr())||offset.lengthSqr()>64||p.maximum()<0||p.maximum()>.80001||pace.lengthSqr()>.64001)return;
        if(!Double.isFinite(p.ax()+p.ay()+p.az())||Math.abs(p.ax())>3.2e7||Math.abs(p.ay())>3.2e7||Math.abs(p.az())>3.2e7)return;
        if(tetherTarget!=null&&tetherTarget.started()==p.started()&&tetherTarget.partner().equals(p.partner())&&p.frameTick()<tetherTarget.frameTick())return;
        tetherTarget=p;tetherTargetTick=mc.player.tickCount;
        
    }
    public static Vec3 tetherMovement(net.minecraft.client.player.LocalPlayer player,Vec3 movement){return PairClient.movement(player,movement);}
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut e){LINKS.clear();PairClient.clear();tetherTarget=null;intentTick=-1;}
    private static void command(String text){var mc=Minecraft.getInstance();if(mc.player!=null&&mc.player.connection!=null)mc.player.connection.sendCommand(text);}
    @SubscribeEvent public static void tick(ClientTickEvent.Post e) {
        var mc=Minecraft.getInstance();if(mc.player==null||mc.level==null){LINKS.clear();PairClient.clear();tetherTarget=null;return;}
        PairClient.tick();
        if(LINKS.containsKey(mc.player.getUUID())&&!HandInHand.ALLOW_SPRINTING.get())mc.player.setSprinting(false);
        while(GRAB.consumeClick()){
            if(LINKS.containsKey(mc.player.getUUID()))command("handholding release");
            else if(mc.hitResult instanceof EntityHitResult hit && hit.getEntity() instanceof Player target)
                command("handholding grab "+target.getGameProfile().getName());
            else mc.player.displayClientMessage(net.minecraft.network.chat.Component.literal("Look at a nearby player to grab their hand."),true);
        }
        while(RELEASE.consumeClick())command("handholding release");
        while(HOLD.consumeClick()) {
            if(LINKS.containsKey(mc.player.getUUID()))command("handholding release");
            else if(mc.hitResult instanceof EntityHitResult hit && hit.getEntity() instanceof Player target)
                command("handholding ask "+target.getGameProfile().getName());
            else mc.player.displayClientMessage(net.minecraft.network.chat.Component.literal("Look at a nearby player to offer your hand."),true);
        }
    }
    @SubscribeEvent public static void movement(MovementInputUpdateEvent e) {
        if(LINKS.containsKey(e.getEntity().getUUID())&&!HandInHand.ALLOW_SPRINTING.get()) {
            e.getEntity().setSprinting(false);
        }
    }
    @EventBusSubscriber(modid=HandInHand.ID,value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD)
    public static final class Setup {
        @SubscribeEvent public static void keys(RegisterKeyMappingsEvent e){e.register(HOLD);e.register(RELEASE);e.register(GRAB);}
        @SubscribeEvent public static void setup(FMLClientSetupEvent e){e.enqueueWork(()->{
            LinkPayload.receiver=HandClient::receive;
            TetherMotion.receiver=HandClient::receiveTether;
            PairFrame.receiver=PairClient::receive;
            ClockPayload.receiver=PairClient::clock;
            PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(ResourceLocation.fromNamespaceAndPath(HandInHand.ID,"hands"),1200,HandPose::new);
        });}
    }
    @SubscribeEvent public static void facing(net.neoforged.neoforge.client.event.RenderPlayerEvent.Pre e){PairClient.facing(e.getEntity());}
    /** PAL procedural animation layer. Changes only the held arm and its sleeve; the body stays free. */
    public static final class HandPose implements IAnimation {
        private final AbstractClientPlayer player;
        private float previousRight,right,previousLeft,left;
        private float partial;
        private Vector3f rightAngles=new Vector3f(),leftAngles=new Vector3f();
        private boolean targetRight,targetLeft;
        private int lastSolvedTick=-1000;
        private boolean lastSolvedRight;
        public HandPose(AbstractClientPlayer p){player=p;}
        @Override public void tick(AnimationData d) {
            previousRight=right;previousLeft=left;
            right=Mth.clamp(right+(targetRight?0.2f:-0.2f),0,1);
            left=Mth.clamp(left+(targetLeft?0.2f:-0.2f),0,1);
        }
        @Override public void setupAnim(AnimationData d) {
            partial=d.getPartialTick();targetRight=false;targetLeft=false;
            var link=LINKS.get(player.getUUID());if(link==null||player.level()==null)return;
            var partner=player.level().getPlayerByUUID(link.other(player.getUUID()));
            if(!(partner instanceof AbstractClientPlayer other))return;
            if(!Boolean.getBoolean("handinhand.legacyPose"))PairClient.facing(other);
            Vec3 a=player.getPosition(partial),b=other.getPosition(partial);
            float yawA=Mth.rotLerp(partial,player.yBodyRotO,player.yBodyRot),yawB=Mth.rotLerp(partial,other.yBodyRotO,other.yBodyRot);
            boolean ar=link.right(player.getUUID()),br=link.right(other.getUUID());
            double as=ScaleSupport.modelScale(player,partial),bs=ScaleSupport.modelScale(other,partial);
            boolean slimA=player.getSkin().model()==net.minecraft.client.resources.PlayerSkin.Model.SLIM;
            boolean slimB=other.getSkin().model()==net.minecraft.client.resources.PlayerSkin.Model.SLIM;
            if(player.isCrouching())a=a.add(0,-(.125/.9375+.2)*as,0);
            if(other.isCrouching())b=b.add(0,-(.125/.9375+.2)*bs,0);
            HandGeometry.V shoulderA=shoulder(a,yawA,ar,as,slimA),shoulderB=shoulder(b,yawB,br,bs,slimB);
            double ax=slimA?0.5:1.0,bx=slimB?0.5:1.0;
            double al=Math.sqrt(100+ax*ax)/16*as,bl=Math.sqrt(100+bx*bx)/16*bs;
            HandGeometry.V contact=HandGeometry.contact(shoulderA,al,shoulderB,bl);
            if(!Boolean.getBoolean("handinhand.legacyPose")){
                boolean first=player.getUUID().equals(link.first());
                contact=HandContacts.contact(link,player.level(),(double)player.level().getGameTime()+partial,first?shoulderA:shoulderB,first?al:bl,first?shoulderB:shoulderA,first?bl:al);
            }
            if(contact==null){
                if(HandInHand.MOVEMENT_TETHER.get()&&Double.isFinite(al+bl)&&shoulderB.sub(shoulderA).length()>1e-6)
                    contact=shoulderA.add(shoulderB.sub(shoulderA).unit().mul(al));
                // Keep reaching through a brief interpolation gap instead of dropping the arm immediately.
                else if(player.tickCount-lastSolvedTick<=HandInHand.REACH_GRACE.get()){
                    targetRight=lastSolvedRight;targetLeft=!lastSolvedRight;
                }
                if(contact==null)return;
            }
            lastSolvedTick=player.tickCount;lastSolvedRight=ar;
            HandGeometry.V delta=contact.sub(shoulderA);
            double yaw=Math.toRadians(yawA);
            Vector3f model=new Vector3f((float)((delta.x()*Math.cos(yaw)+delta.z()*Math.sin(yaw))/as),
                (float)(-delta.y()/as),(float)((delta.x()*Math.sin(yaw)-delta.z()*Math.cos(yaw))/as));
            Vector3f rest=new Vector3f((float)((ar?-ax:ax)/16),10f/16,0);
            Vector3f angles=new Quaternionf().rotationTo(rest.normalize(),model.normalize()).getEulerAnglesZYX(new Vector3f());
            if(ar){rightAngles=angles;targetRight=true;}else{leftAngles=angles;targetLeft=true;}
        }
        @Override public boolean isActive(){return LINKS.containsKey(player.getUUID())||right>0||left>0||previousRight>0||previousLeft>0;}
        @Override public PlayerAnimBone get3DTransform(PlayerAnimBone bone) {
            boolean isRight=bone.getName().equals("right_arm"),isLeft=bone.getName().equals("left_arm");
            if(!isRight&&!isLeft)return bone;
            float amount=Mth.lerp(partial,isRight?previousRight:previousLeft,isRight?right:left);
            if(amount<=0)return bone;
            amount=amount*amount*(3-2*amount);
            Vector3f angles=isRight?rightAngles:leftAngles;
            bone.rotX=angleLerp(amount,bone.rotX,angles.x);
            bone.rotY=angleLerp(amount,bone.rotY,angles.y);
            bone.rotZ=angleLerp(amount,bone.rotZ,angles.z);
            bone.bend*=1-amount;
            bone.positionX*=1-amount;bone.positionY*=1-amount;bone.positionZ*=1-amount;
            return bone;
        }
        void applyAfterCustomAnimation(Map<?,?> parts) {
            if(!LINKS.containsKey(player.getUUID()))return;
            applyCustomPart(parts.get("right_arm"),true);
            applyCustomPart(parts.get("right_sleeve"),true);
            applyCustomPart(parts.get("left_arm"),false);
            applyCustomPart(parts.get("left_sleeve"),false);
        }
        private void applyCustomPart(Object value,boolean isRight) {
            if(!(value instanceof net.minecraft.client.model.geom.ModelPart part))return;
            float amount=Mth.lerp(partial,isRight?previousRight:previousLeft,isRight?right:left);
            if(amount<=0)return;
            var bone=com.zigythebird.playeranim.util.RenderUtil.copyVanillaPart(part,new PlayerAnimBone(isRight?"right_arm":"left_arm"));
            get3DTransform(bone);
            EmfCompatibility.restoreChildren(part);
            com.zigythebird.playeranim.util.RenderUtil.translatePartToBone(part,bone,part.getInitialPose());
        }
        private static float angleLerp(float t,float a,float b){return a+(float)Math.atan2(Math.sin(b-a),Math.cos(b-a))*t;}
    }
    public static boolean rightSide(Vec3 a,Vec3 b,float yaw) {
        double r=Math.toRadians(yaw);return -(b.x-a.x)*Math.cos(r)-(b.z-a.z)*Math.sin(r)>=0;
    }
    public static HandGeometry.V shoulder(Vec3 p,float yaw,boolean right,double scale,boolean slim) {
        double r=Math.toRadians(yaw),x=(right?-5:5)/16.0*scale;
        return new HandGeometry.V(p.x+x*Math.cos(r),p.y+(1.501-(slim?2.5:2)/16.0)*scale,p.z+x*Math.sin(r));
    }
}
