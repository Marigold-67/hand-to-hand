package dev.marigold.handinhand;
import dev.marigold.handinhand.HandGeometry.V;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.UUID;

/** Side-by-side position following, with no accumulated tether velocity. */
public final class HandTether {
    private Vec3 previousA,previousB,side=Vec3.ZERO;
    private final UUID first;
    private final boolean firstRight,secondRight;
    private boolean movingA,movingB;
    private long inputA=Long.MIN_VALUE,inputB=Long.MIN_VALUE;
    private float yawA,yawB;
    private int leader=-1;
    private final Formation formation;
    private UUID lifted,controlled;private long liftTick,controlTick;
    private double fallVelocity;private boolean grabMode;
    private final TerrainFollower terrain=new TerrainFollower();
    public boolean controlling(ServerPlayer p,long now){return p.getUUID().equals(controlled)&&now-controlTick>=0&&now-controlTick<=2&&(grabMode||!active(p.getUUID(),now));}
    private boolean active(UUID id,long now){long tick=id.equals(first)?inputA:inputB;return now-tick>=0&&now-tick<=6&&(id.equals(first)?movingA:movingB);}
    public boolean lifting(ServerPlayer p,long now){return p.getUUID().equals(lifted)&&now-liftTick>=0&&now-liftTick<=2;}
    public boolean firstRight(){return firstRight;}
    public boolean secondRight(){return secondRight;}
    public boolean consumeGripChange(){return false;}
    public HandTether(ServerPlayer a,ServerPlayer b){
        first=a.getUUID();previousA=a.position();previousB=b.position();
        double heading=Math.toRadians(a.yBodyRot);var delta=b.position().subtract(a.position());
        formation=new Formation(heading,delta.x*Math.cos(heading)+delta.z*Math.sin(heading)>=0?1:-1);
        firstRight=formation.sign()<0;secondRight=!firstRight;
    }
    public void intent(UUID id,long tick,boolean moving,float bodyYaw){
        if(id.equals(first)){if(tick>inputA){inputA=tick;movingA=moving;yawA=bodyYaw;}}
        else if(tick>inputB){inputB=tick;movingB=moving;yawB=bodyYaw;}
    }
    public static boolean[] chooseHands(net.minecraft.world.entity.LivingEntity a,net.minecraft.world.entity.LivingEntity b){
        boolean ar=true,br=false;double best=Double.POSITIVE_INFINITY;
        for(boolean x:new boolean[]{true,false})for(boolean y:new boolean[]{false,true}){double d=ScaleSupport.shoulder(a,x).sub(ScaleSupport.shoulder(b,y)).length();if(d<best-1e-8){best=d;ar=x;br=y;}}
        return new boolean[]{ar,br};
    }
    public static double scale(net.minecraft.world.entity.Entity a,net.minecraft.world.entity.Entity b){return (ScaleSupport.modelScale(a,1)+ScaleSupport.modelScale(b,1))/(2*.9375);}
    public static V vector(Vec3 v){return new V(v.x,v.y,v.z);}
    public static Vec3 vector(V v){return new Vec3(v.x(),v.y(),v.z());}
    public static Vec3 shoulder(net.minecraft.world.entity.LivingEntity self,net.minecraft.world.entity.LivingEntity other){
        Boolean held=self instanceof ServerPlayer p?HandServer.heldSide(p):null;
        if(held==null){var d=other.position().subtract(self.position());double yaw=Math.toRadians(self.yBodyRot);held=-d.x*Math.cos(yaw)-d.z*Math.sin(yaw)>=0;}
        return vector(ScaleSupport.shoulder(self,held));
    }
    public boolean tick(ConsentLedger.Pair pair,ServerPlayer a,ServerPlayer b){
        Vec3 da=a.position().subtract(previousA),db=b.position().subtract(previousB);previousA=a.position();previousB=b.position();
        double size=scale(a,b);if(!Double.isFinite(size)||size<=0||da.lengthSqr()>64||db.lengthSqr()>64)return false;
        long now=a.server.overworld().getGameTime();grabMode=pair.grab();
        boolean activeA=now-inputA>=0&&now-inputA<=6&&movingA,activeB=now-inputB>=0&&now-inputB<=6&&movingB;
        int next=leader;
        if(leader==0&&!activeA&&activeB)next=1;else if(leader==1&&!activeB&&activeA)next=0;
        else if(leader<0){if(activeA)next=0;else if(activeB)next=1;}
        if(!activeA&&!activeB){if(a.getAbilities().flying&&!b.getAbilities().flying)next=0;else if(b.getAbilities().flying&&!a.getAbilities().flying)next=1;}
        if(activeA)a.yBodyRot=yawA;if(activeB)b.yBodyRot=yawB;
        if(pair.grab())next=0;
        lifted=null;controlled=null;
        if(next>=0){
            ServerPlayer lead=next==0?a:b,follow=next==0?b:a;Vec3 velocity=next==0?da:db;
            boolean vertical=lead.getAbilities().flying;
            double spacing=HandInHand.TETHER_LENGTH.get()*size;
            boolean changed=next!=leader;leader=next;
            if((next==0?activeA:activeB)&&velocity.horizontalDistance()>.008)
                formation.turnToward(Math.toRadians(lead.yBodyRot),.20);
            double sign=formation.sign()*(next==0?1:-1);
            side=new Vec3(Math.cos(formation.heading())*sign*spacing,0,Math.sin(formation.heading())*sign*spacing);
            boolean assist=pair.grab()||!(next==0?activeB:activeA);
            Vec3 before=follow.position();
            if(assist){
                double height=vertical?1.376*(ScaleSupport.modelScale(lead,1)-ScaleSupport.modelScale(follow,1)):0;
                var error=lead.position().add(side.x,height,side.z).subtract(before);
                double maximum=Math.min(.65,Math.max(.30*size,velocity.length()+.18*size));
                boolean hop=false;
                if(!vertical){var step=terrain.step(follow,lead.position().add(side.x,0,side.z),maximum);hop=step.hop();error=step.target().subtract(before).multiply(1,0,1);}
                if(error.length()>maximum)error=error.normalize().scale(maximum);
                if(vertical){fallVelocity=0;follow.resetFallDistance();lifted=follow.getUUID();liftTick=now;}
                else {boolean supported=follow.onGround()||!follow.level().noCollision(follow,follow.getBoundingBox().move(0,-.04,0).deflate(.001));if(changed||supported&&fallVelocity<=0)fallVelocity=0;if(hop&&supported&&fallVelocity<=0&&!follow.isCrouching()){follow.jumpFromGround();fallVelocity=Math.max(.42,follow.getDeltaMovement().y*ScaleSupport.motionScale(follow));}else fallVelocity=Math.max(-3.92,(fallVelocity-.08)*.98);error=new Vec3(error.x,fallVelocity,error.z);}
                error=vector(PairReach.move(vector(follow.position()),vector(lead.position()),vector(error),1.3*size,2*size,false));
                follow.setDeltaMovement(Vec3.ZERO);
                double motion=ScaleSupport.motionScale(follow);
                follow.move(net.minecraft.world.entity.MoverType.SELF,error.scale(1/Math.max(.001,motion)));
                follow.setDeltaMovement(Vec3.ZERO);follow.yBodyRot=(float)Math.toDegrees(formation.heading());
                follow.connection.resetPosition();
                controlled=follow.getUUID();controlTick=now;
            }
            var fa=next==0||!assist?da:a.position().subtract(before);
            var fb=next==1||!assist?db:b.position().subtract(before);
            publish(pair,a,b,now,next,assist,fa,fb);
        }else publish(pair,a,b,now,-1,false,da,db);
        previousA=a.position();previousB=b.position();
        return true;
    }
    private void publish(ConsentLedger.Pair pair,ServerPlayer a,ServerPlayer b,long now,int leader,boolean assist,Vec3 da,Vec3 db){
        var frame=new PairFrame(pair.first(),pair.second(),pair.started(),now,leader,pair.grab(),firstRight,secondRight,body(a,da,assist&&leader==1),body(b,db,assist&&leader==0));
        // Actual entity trackers, plus both actors; deduplicate viewers of both entities.
        var viewers=new java.util.HashSet<ServerPlayer>();viewers.add(a);viewers.add(b);
        viewers.addAll(a.serverLevel().getChunkSource().chunkMap.getPlayers(a.chunkPosition(),false));
        viewers.addAll(b.serverLevel().getChunkSource().chunkMap.getPlayers(b.chunkPosition(),false));
        for(var viewer:viewers)PacketDistributor.sendToPlayer(viewer,frame);
    }
    private PairFrame.Body body(ServerPlayer p,Vec3 d,boolean controlled){return new PairFrame.Body(p.getX(),p.getY(),p.getZ(),d.x,d.y,d.z,p.yBodyRot,controlled,p.onGround(),p.getAbilities().flying||p.getUUID().equals(lifted));}
}
