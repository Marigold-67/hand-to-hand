package dev.marigold.handinhand.client;
import dev.marigold.handinhand.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import java.util.*;
/** Buffered shared history, gentle physical reconciliation, and cooperative native steering. */
public final class PairClient {
 private static final boolean TIMELINE=!Boolean.getBoolean("handinhand.legacyTimeline"),PRESENTATION=!Boolean.getBoolean("handinhand.legacyPresentation");
 private static final Map<UUID,Track> tracks=new HashMap<>();
 private static final double STEP_NANOS=50_000_000d;
 private static double clockOffset,oneWayTicks;private static boolean clockReady;
 private static long lastProbe;private static int intentTick=-1;
 private static final class Track {
  final ArrayDeque<PairFrame> frames=new ArrayDeque<>();long received;
  double playhead=Double.NEGATIVE_INFINITY;
  int sampleTick=Integer.MIN_VALUE;
  boolean voluntary;double passiveFall;
  final JumpWindow jump=new JumpWindow();
  Vec3 alignment=Vec3.ZERO;
  final float[] yaw={Float.NaN,Float.NaN},previousYaw={Float.NaN,Float.NaN};
 }
 private static double localTime(){return System.nanoTime()/STEP_NANOS;}
 public static void clock(ClockPayload p){long now=System.nanoTime(),rtt=now-p.stamp();if(rtt<0||rtt>2_000_000_000L)return;
  double measured=rtt/(2*STEP_NANOS);oneWayTicks=clockReady?oneWayTicks*.8+measured*.2:measured;
  double offset=p.tick()+measured-localTime();
  if(!clockReady){clockOffset=offset;clockReady=true;}else clockOffset+=Math.max(-.25,Math.min(.25,offset-clockOffset));
 }
 public static void receive(PairFrame p){
  var link=HandClient.LINKS.get(p.first());if(link==null||link.started()!=p.session()||!link.second().equals(p.second())||p.first().equals(p.second())||p.leader()<-1||p.leader()>1)return;
  if(!valid(p.a())||!valid(p.b())||link.firstRight()!=p.rightA()||link.secondRight()!=p.rightB())return;
  var track=tracks.computeIfAbsent(p.first(),k->new Track());var last=track.frames.peekLast();
  if(last!=null&&last.session()!=p.session()){track.frames.clear();track.playhead=Double.NEGATIVE_INFINITY;track.sampleTick=Integer.MIN_VALUE;track.jump.clear();Arrays.fill(track.yaw,Float.NaN);}
  last=track.frames.peekLast();if(last!=null&&p.tick()<=last.tick())return;
  long received=System.nanoTime();if(TIMELINE&&track.received!=0&&received-track.received>500_000_000L){track.playhead=Double.NEGATIVE_INFINITY;track.sampleTick=Integer.MIN_VALUE;}
  track.frames.addLast(p);while(track.frames.size()>20)track.frames.removeFirst();track.received=received;
  if(!clockReady)clockOffset=p.tick()-localTime();
 }
 private static boolean valid(PairFrame.Body b){return Double.isFinite(b.x()+b.y()+b.z()+b.dx()+b.dy()+b.dz()+b.bodyYaw())&&Math.abs(b.x())<3.2e7&&Math.abs(b.y())<3.2e7&&Math.abs(b.z())<3.2e7&&new Vec3(b.dx(),b.dy(),b.dz()).length()<8;}
 private static Track track(UUID id){var link=HandClient.LINKS.get(id);if(link==null||!HandInHand.MOVEMENT_TETHER.get())return null;var t=tracks.get(link.first());return t!=null&&System.nanoTime()-t.received<500_000_000L?t:null;}
 public static boolean hasFresh(UUID id){return track(id)!=null;}
 private static PairFrame latest(UUID id){var t=track(id);return t==null?null:t.frames.peekLast();}
 private static PairFrame.Body body(PairFrame f,UUID id){return id.equals(f.first())?f.a():f.b();}
 public static boolean controlled(UUID id){var f=latest(id);return f!=null&&body(f,id).controlled();}
 private static PairFrame.Body mix(PairFrame.Body a,PairFrame.Body b,double t){
  return new PairFrame.Body(a.x()+(b.x()-a.x())*t,a.y()+(b.y()-a.y())*t,a.z()+(b.z()-a.z())*t,
   b.dx(),b.dy(),b.dz(),(float)(a.bodyYaw()+Math.toDegrees(Formation.difference(Math.toRadians(b.bodyYaw()),Math.toRadians(a.bodyYaw())))*t),b.controlled(),b.grounded(),b.flying());
 }
 private static SampleMotion.State motion(PairFrame.Body b){return new SampleMotion.State(HandTether.vector(position(b)),new HandGeometry.V(b.dx(),b.dy(),b.dz()),b.grounded(),b.flying());}
 private static PairFrame.Body evaluated(SampleMotion.State s,float yaw,boolean controlled){return new PairFrame.Body(s.position().x(),s.position().y(),s.position().z(),s.velocity().x(),s.velocity().y(),s.velocity().z(),yaw,controlled,s.grounded(),s.flying());}
 /** Average resolved motion across four server intervals, instead of copying burst/zero packets. */
 private static Vec3 pace(Track t,UUID id){
  return paceAt(t,id,t.frames.peekLast().tick());
 }
 private static Vec3 paceAt(Track t,UUID id,double time){
  var end=t.frames.peekFirst();for(var frame:t.frames)if(frame.tick()<=time)end=frame;else break;
  var begin=end;
  for(var frame:t.frames)if(frame.tick()<=end.tick()&&end.tick()-frame.tick()<=4){begin=frame;break;}
  double elapsed=end.tick()-begin.tick();
  if(elapsed<1){var b=body(end,id);return new Vec3(b.dx(),b.dy(),b.dz());}
  return position(body(end,id)).subtract(position(body(begin,id))).scale(1/elapsed);
 }
 private static PairFrame.Body sample(Track t,UUID id){
  // Absorb an ordinary arrival gap before it becomes a stop/start camera correction.
  double delay=Math.max(1.5,Math.min(5,oneWayTicks+1));
  double wall=localTime(),desired=Math.min(t.frames.peekLast().tick()+1.5,wall+clockOffset-delay);
  var observer=Minecraft.getInstance().player;int step=observer==null?Integer.MAX_VALUE:observer.tickCount;
  if(!TIMELINE||t.sampleTick!=step){t.playhead=Math.max(t.playhead,desired);t.sampleTick=step;}
  double time=t.playhead;PairFrame before=null,after=null;
  for(var frame:t.frames){if(frame.tick()<=time)before=frame;else{after=frame;break;}}
  if(before==null)return body(t.frames.peekFirst(),id);
  var result=body(before,id);
  if(after!=null){var next=body(after,id);double fraction=Math.max(0,Math.min(1,(time-before.tick())/(after.tick()-before.tick())));if(TIMELINE){var s=SampleMotion.interpolate(motion(result),motion(next),fraction,after.tick()-before.tick());var horizontal=paceAt(t,id,time);s=new SampleMotion.State(s.position(),new HandGeometry.V(horizontal.x,s.velocity().y(),horizontal.z),s.grounded(),s.flying());return evaluated(s,mix(result,next,fraction).bodyYaw(),result.controlled());}return mix(result,next,fraction);}
  double ahead=Math.max(0,Math.min(1.5,time-before.tick()));var velocity=pace(t,id);
  if(TIMELINE)return evaluated(SampleMotion.predict(motion(result),HandTether.vector(velocity.multiply(1,0,1)),ahead),result.bodyYaw(),result.controlled());
  return new PairFrame.Body(result.x()+velocity.x*ahead,result.y()+(result.flying()?velocity.y*ahead:0),result.z()+velocity.z*ahead,result.dx(),result.dy(),result.dz(),result.bodyYaw(),result.controlled(),result.grounded(),result.flying());
 }
 private static Vec3 position(PairFrame.Body b){return new Vec3(b.x(),b.y(),b.z());}
 private static Vec3 nativeSteering(LocalPlayer p,Vec3 own,Track t,PairFrame f,boolean carryJumpPace){
  UUID leader=f.leader()==0?f.first():f.leader()==1?f.second():null;
  if(leader==null||p.getUUID().equals(leader)||p.getAbilities().flying)return own;
  var partner=Minecraft.getInstance().level.getPlayerByUUID(leader);if(partner==null)return own;
  var lead=sample(t,leader);var self=sample(t,p.getUUID());
  double size=HandTether.scale(p,partner);if(!Double.isFinite(size)||size<=0)return own;
  double sign=(f.rightA()?-1:1)*(f.leader()==0?1:-1),angle=Math.toRadians(lead.bodyYaw());
  var slot=new Vec3(Math.cos(angle)*sign*HandInHand.TETHER_LENGTH.get()*size,0,Math.sin(angle)*sign*HandInHand.TETHER_LENGTH.get()*size);
  var error=position(lead).add(slot).subtract(position(self)).multiply(1,0,1);
  double motion=Math.max(.001,ScaleSupport.motionScale(p));var world=own.multiply(motion,0,motion);
  var result=FollowMotion.cooperate(HandTether.vector(world),HandTether.vector(pace(t,leader).multiply(1,0,1)),HandTether.vector(error),.04*size,carryJumpPace);
  return new Vec3(result.x()/motion,own.y,result.z()/motion);
 }
 public static Vec3 movement(LocalPlayer player,Vec3 own){
  var link=HandClient.LINKS.get(player.getUUID());if(link==null||!HandInHand.MOVEMENT_TETHER.get())return own;
  var t=track(player.getUUID());var f=t==null?null:t.frames.peekLast();
  boolean directional=Math.abs(player.input.forwardImpulse)+Math.abs(player.input.leftImpulse)>.01;
  boolean jumping=t!=null&&t.jump.update(player.tickCount,player.onGround(),player.input.jumping,own.y>.05,!f.grab()&&!player.getAbilities().flying);
  if(t!=null)t.voluntary=jumping;
  boolean active=directional||player.input.jumping||jumping||player.getAbilities().flying&&player.input.shiftKeyDown;
  if(intentTick!=player.tickCount){intentTick=player.tickCount;net.neoforged.neoforge.network.PacketDistributor.sendToServer(new TetherIntent(link.other(player.getUUID()),link.started(),active,player.yBodyRot));}
  if(t==null)return safety(player,own);var state=body(f,player.getUUID());
  if(active&&!f.grab())return safety(player,nativeSteering(player,own,t,f,jumping&&!directional));
  if(!state.controlled())return safety(player,own);
  var sampled=sample(t,player.getUUID());var horizontal=pace(t,player.getUUID());Vec3 error=position(sampled).subtract(player.position()),velocity=TIMELINE?new Vec3(horizontal.x,sampled.dy(),horizontal.z):horizontal;
  double motion=Math.max(.001,ScaleSupport.motionScale(player));
  boolean settled=TIMELINE?sampled.grounded()&&!sampled.flying():state.grounded()&&!state.flying();
  if(settled){error=error.multiply(1,0,1);velocity=velocity.multiply(1,0,1);}
  double maximum=Math.min(.65*motion,Math.max(.14*motion,velocity.length()*1.4+.03*motion));
  var step=HandTether.vector(FollowMotion.follow(HandTether.vector(error),HandTether.vector(velocity),maximum,.08*motion));
  if(TIMELINE&&!settled){t.passiveFall=sampled.dy();step=new Vec3(step.x,Math.max(-.65*motion,Math.min(.65*motion,error.y)),step.z);}
  if(settled){double fall=-.08*motion;if(TIMELINE){boolean supported=player.onGround()||!player.level().noCollision(player,player.getBoundingBox().move(0,-.04*motion,0).deflate(.001));if(supported)t.passiveFall=0;t.passiveFall=Math.max(-3.92*motion,(t.passiveFall-.08*motion)*.98);fall=t.passiveFall;}step=new Vec3(step.x,fall,step.z);}
  player.setDeltaMovement(Vec3.ZERO);if(state.flying())player.resetFallDistance();
  return safety(player,step.scale(1/motion));
 }
 private static Vec3 safety(LocalPlayer p,Vec3 own){
  var link=HandClient.LINKS.get(p.getUUID());if(link==null)return own;var t=tracks.get(link.first());if(t==null||t.frames.isEmpty()||System.nanoTime()-t.received>2_000_000_000L)return own;
  var mc=Minecraft.getInstance();var partner=mc.level.getPlayerByUUID(link.other(p.getUUID()));if(partner==null)return own;
  double size=HandTether.scale(p,partner),motion=Math.max(.001,ScaleSupport.motionScale(p));if(!Double.isFinite(size)||size<=0)return own;
  var f=t.frames.peekLast();var ownBody=body(f,p.getUUID());var otherBody=body(f,partner.getUUID());
  double age=Math.max(0,Math.min(8,localTime()+clockOffset-f.tick()));
  var predictedSelf=position(ownBody).add(pace(t,p.getUUID()).multiply(1,0,1).scale(age));
  var predictedOther=position(otherBody).add(pace(t,partner.getUUID()).multiply(1,0,1).scale(age));
  double gap=position(ownBody).subtract(position(otherBody)).horizontalDistance();
  boolean fresh=System.nanoTime()-t.received<500_000_000L;
  if(fresh){var offset=p.position().subtract(predictedSelf).multiply(1,0,1);double cap=gap>1.3*size?.2*size:2*size;t.alignment=offset.length()>cap?offset.normalize().scale(cap):offset;}
  var anchor=predictedOther.add(t.alignment);
  var world=own.scale(motion);var result=HandTether.vector(PairReach.move(HandTether.vector(p.position()),HandTether.vector(anchor),HandTether.vector(world),1.25*size,1.8*size,true));
  return result.scale(1/motion);
 }
 public static void tick(){
  var mc=Minecraft.getInstance();if(mc.player==null||mc.level==null){clear();return;}long now=System.nanoTime();
  if(!HandClient.LINKS.isEmpty()&&now-lastProbe>500_000_000L){lastProbe=now;net.neoforged.neoforge.network.PacketDistributor.sendToServer(new ClockPayload(now,-1));}
  for(var t:tracks.values()){
   if(t.frames.isEmpty()||now-t.received>500_000_000L)continue;
   var f=t.frames.peekLast();Vec3 prediction=Vec3.ZERO;
   if(mc.player.getUUID().equals(f.first())||mc.player.getUUID().equals(f.second())){
    prediction=mc.player.position().subtract(position(sample(t,mc.player.getUUID()))).multiply(1,0,1);
    double cap=.8*ScaleSupport.modelScale(mc.player,1)/.9375;if(prediction.length()>cap)prediction=prediction.normalize().scale(cap);
    if(PRESENTATION){var other=mc.level.getPlayerByUUID(mc.player.getUUID().equals(f.first())?f.second():f.first());if(other!=null){double size=HandTether.scale(mc.player,other);prediction=prediction.scale(SampleMotion.presentationWeight(position(f.a()).subtract(position(f.b())).horizontalDistance(),HandInHand.TETHER_LENGTH.get()*size,size));}}
    else if(position(f.a()).distanceTo(position(f.b()))>HandInHand.TETHER_LENGTH.get()*HandTether.scale(mc.player,mc.player)+.3)prediction=Vec3.ZERO;
   }
   int index=0;
   for(UUID id:List.of(f.first(),f.second())){
    var entity=mc.level.getPlayerByUUID(id);if(entity==null){index++;continue;}
    var sampled=sample(t,id);float yaw=sampled.bodyYaw();
    if(Float.isNaN(t.yaw[index]))t.yaw[index]=yaw;
    t.previousYaw[index]=t.yaw[index];t.yaw[index]+=Math.max(-9,Math.min(9,net.minecraft.util.Mth.wrapDegrees(yaw-t.yaw[index])));index++;
    if(entity==mc.player)continue;
    Vec3 target=position(sampled).add(prediction),d=target.subtract(entity.position());
    if(mc.level.noCollision(entity,entity.getBoundingBox().move(d)))entity.setPos(target);entity.setOnGround(sampled.grounded());
   }
  }
 }
 public static void facing(Player player){
  var t=track(player.getUUID());if(t==null)return;var f=t.frames.peekLast();if(!body(f,player.getUUID()).controlled())return;
  var mc=Minecraft.getInstance();if(player==mc.player&&!f.grab()&&(Math.abs(mc.player.input.forwardImpulse)+Math.abs(mc.player.input.leftImpulse)>.01||mc.player.input.jumping))return;
  int i=player.getUUID().equals(f.first())?0:1;
  if(Float.isFinite(t.yaw[i])&&Float.isFinite(t.previousYaw[i])){player.yBodyRotO=t.previousYaw[i];player.yBodyRot=t.yaw[i];}
 }
 public static boolean ownsGait(Player player){return hasFresh(player.getUUID())&&(player!=Minecraft.getInstance().player||controlled(player.getUUID()));}
 public static boolean passiveView(LocalPlayer p){var t=track(p.getUUID());if(t==null)return false;var f=t.frames.peekLast();if(!body(f,p.getUUID()).controlled())return false;return f.grab()||!t.voluntary&&!p.input.jumping&&Math.abs(p.input.forwardImpulse)+Math.abs(p.input.leftImpulse)<.01;}
 public static float gaitResponse(){return Boolean.getBoolean("handinhand.legacyGait")?.4f:.3f;}
 public static float gait(UUID id){var t=track(id);if(t==null)return 0;var b=Boolean.getBoolean("handinhand.legacyGait")?body(t.frames.peekLast(),id):sample(t,id);var v=pace(t,id);return b.flying()?0:(float)Math.min(1,Math.hypot(v.x,v.z)*4)*(b.grounded()||Boolean.getBoolean("handinhand.legacyGait")?1:.8f);}
 public static void remove(UUID a,UUID b){tracks.remove(a);tracks.remove(b);HandContacts.remove(a);HandContacts.remove(b);}
 public static void clear(){tracks.clear();HandContacts.clear();clockReady=false;oneWayTicks=0;lastProbe=0;intentTick=-1;}
}
